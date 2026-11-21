import hashlib
import hmac
import json
from datetime import datetime, timedelta
from types import SimpleNamespace

import pytest
from fastapi.testclient import TestClient

from sophonie.api import creer_app
from sophonie.cerveau import Sophonie
from sophonie.config import Priere, Profil, Secrets
from sophonie.integrations import WhatsAppBusiness
from sophonie.memoire import Memoire
from sophonie.planificateur import verifier_rappels


def message(texte, stop="end_turn"):
    return SimpleNamespace(content=[SimpleNamespace(type="thinking", thinking=""),
                                    SimpleNamespace(type="text", text=texte)], stop_reason=stop)


class FauxClient:
    def __init__(self, reponse_chat="Bonjour !", reponse_triage=None):
        self.appels = []
        outer = self

        class Runner:
            def until_done(self_inner):
                return message(reponse_chat)

        class Messages:
            def tool_runner(self_inner, **kw):
                outer.appels.append(("runner", kw))
                return Runner()

            def create(self_inner, **kw):
                outer.appels.append(("create", kw))
                return message(json.dumps(reponse_triage))

        self.beta = SimpleNamespace(messages=Messages())


PROFIL = Profil(prenom="Test", fuseau="Africa/Douala",
                prieres=[Priere("Prière du matin", "07:00"), Priere("Prière du soir", "23:00")],
                contacts_prioritaires=["Maman"])


def faire(client=None, jeton="secret", app_secret="meta"):
    secrets = Secrets(jeton_api=jeton, whatsapp_verify_token="verif", whatsapp_app_secret=app_secret,
                      google_token_file="/inexistant.json")
    s = Sophonie(PROFIL, secrets, Memoire(), client=client or FauxClient())
    return s, TestClient(creer_app(s, demarrer_planificateur=False))


AUTH = {"Authorization": "Bearer secret"}


def test_memoire_gouts_et_contacts():
    m = Memoire()
    m.apprendre_gout("cuisine", "Aime le ndolé")
    m.apprendre_gout("cuisine", "Aime le ndolé", "aime")  # pas de doublon
    assert len(m.gouts("cuisine")) == 1
    m.enregistrer_contacts([{"nom": "Maman", "telephones": ["+237600000000"], "anniversaire": "1960-11-21"}])
    assert m.chercher_contacts("Maman")[0]["telephones"] == ["+237600000000"]
    assert m.anniversaires("11-21")[0]["nom"] == "Maman"


def test_historique_commence_par_user():
    m = Memoire()
    m.ajouter_echange("assistant", "orphelin")
    m.ajouter_echange("user", "salut")
    m.ajouter_echange("assistant", "bonjour")
    assert m.historique()[0]["role"] == "user"


def test_rappels_dus_et_recurrents():
    s, _ = faire()
    passe = (s.maintenant() - timedelta(minutes=5)).isoformat()
    futur = (s.maintenant() + timedelta(hours=2)).isoformat()
    s.memoire.ajouter_rappel("Appeler le médecin", passe)
    s.memoire.ajouter_rappel("Lire la Bible", passe, "quotidien")
    s.memoire.ajouter_rappel("Plus tard", futur)
    assert verifier_rappels(s) == 2
    restants = {r["texte"]: r for r in s.memoire.rappels_a_venir()}
    assert "Appeler le médecin" not in restants
    assert datetime.fromisoformat(restants["Lire la Bible"]["echeance"]) > s.maintenant()
    assert len(s.memoire.alertes_non_vues()) == 2


def test_outils_definis():
    s, _ = faire()
    noms = {t.name for t in s.outils()}
    assert {"apprendre_gout", "creer_rappel", "agenda", "mails", "envoyer_whatsapp_business"} <= noms
    assert "envoyer_whatsapp_business" not in {t.name for t in s.outils(peut_envoyer=False)}


def test_api_refuse_sans_jeton():
    _, c = faire()
    assert c.post("/chat", json={"texte": "salut"}).status_code == 401
    _, c2 = faire(jeton="")
    assert c2.post("/chat", json={"texte": "salut"}, headers=AUTH).status_code == 503


def test_chat_utilise_le_modele_et_memorise():
    client = FauxClient(reponse_chat="Bonjour Test, voici votre journée.")
    s, c = faire(client)
    r = c.post("/chat", json={"texte": "Qu'ai-je aujourd'hui ?"}, headers=AUTH)
    assert r.json()["reponse"].startswith("Bonjour Test")
    _, kw = client.appels[0]
    assert kw["model"] == "claude-opus-5-5"
    assert kw["fallbacks"] == "default"
    assert kw["messages"][-1]["role"] == "system"
    assert [m["role"] for m in s.memoire.historique()] == ["user", "assistant"]


def test_evenement_trie_et_alerte():
    triage = {"importance": "urgente", "alerter": True, "titre_alerte": "Maman demande un appel",
              "corps_alerte": "Elle veut que vous l'appeliez avant 18h.", "rappel_suggere": "Appeler Maman",
              "echeance_rappel": "2026-11-21T17:30:00+01:00", "gout_observe": ""}
    s, c = faire(FauxClient(reponse_triage=triage))
    r = c.post("/evenements", json={"source": "whatsapp", "expediteur": "Maman",
                                     "texte": "Appelle-moi avant 18h stp"}, headers=AUTH)
    assert r.status_code == 200
    alertes = c.get("/alertes", headers=AUTH).json()
    assert alertes[0]["titre"] == "Maman demande un appel" and alertes[0]["priorite"] == "haute"
    assert s.memoire.rappels_a_venir()[0]["texte"] == "Appeler Maman"
    c.post(f"/alertes/{alertes[0]['id']}/vue", headers=AUTH)
    assert c.get("/alertes", headers=AUTH).json() == []


def test_webhook_whatsapp_verification_et_signature():
    triage = {"importance": "normale", "alerter": False, "titre_alerte": "", "corps_alerte": "",
              "rappel_suggere": "", "echeance_rappel": "", "gout_observe": ""}
    s, c = faire(FauxClient(reponse_triage=triage))
    r = c.get("/webhook/whatsapp", params={"hub.mode": "subscribe", "hub.verify_token": "verif",
                                           "hub.challenge": "42"})
    assert r.text == "42"
    assert c.get("/webhook/whatsapp", params={"hub.mode": "subscribe", "hub.verify_token": "faux",
                                              "hub.challenge": "42"}).status_code == 403
    charge = {"entry": [{"changes": [{"value": {
        "contacts": [{"wa_id": "237600000000", "profile": {"name": "Client A"}}],
        "messages": [{"from": "237600000000", "type": "text", "text": {"body": "Le devis est prêt ?"},
                      "timestamp": "1"}]}}]}]}
    corps = json.dumps(charge).encode()
    assert c.post("/webhook/whatsapp", content=corps,
                  headers={"X-Hub-Signature-256": "sha256=faux"}).status_code == 401
    sig = "sha256=" + hmac.new(b"meta", corps, hashlib.sha256).hexdigest()
    r = c.post("/webhook/whatsapp", content=corps,
               headers={"X-Hub-Signature-256": sig, "Content-Type": "application/json"})
    assert r.json() == {"recus": 1}
    assert s.memoire.messages()[0]["expediteur"] == "Client A (237600000000)"


def test_envoi_whatsapp_exige_accord():
    s, _ = faire()
    outil = next(t for t in s.outils() if t.name == "envoyer_whatsapp_business")
    assert "refusé" in outil.call({"destinataire": "+237600000000", "texte": "ok", "accord_explicite": False})


def test_extraction_webhook_media():
    charge = {"entry": [{"changes": [{"value": {"messages": [{"from": "1", "type": "image"}]}}]}]}
    assert WhatsAppBusiness.extraire_messages(charge)[0]["texte"] == "[image]"


def test_profil_exemple_se_charge():
    from sophonie.config import RACINE
    p = Profil.charger(RACINE / "profil.exemple.json")
    assert p.fuseau == "Africa/Douala" and len(p.prieres) == 2
