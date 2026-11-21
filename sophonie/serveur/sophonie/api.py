"""API HTTP de Sophonie, appelée par l'application Android et par le webhook WhatsApp Business."""

from __future__ import annotations

import hashlib
import hmac
import logging
import secrets as _secrets
from typing import Any

from fastapi import BackgroundTasks, Depends, FastAPI, Header, HTTPException, Query, Request
from fastapi.responses import PlainTextResponse
from pydantic import BaseModel, Field

from .cerveau import Sophonie
from .integrations import WhatsAppBusiness

log = logging.getLogger("sophonie")

SOURCES = {"whatsapp", "whatsapp_business", "sms", "mail", "appel", "autre"}


class Question(BaseModel):
    texte: str = Field(min_length=1, max_length=8000)


class Evenement(BaseModel):
    source: str
    expediteur: str = ""
    texte: str = Field(default="", max_length=20000)
    recu_le: str | None = None


class Contact(BaseModel):
    nom: str
    telephones: list[str] = []
    emails: list[str] = []
    anniversaire: str = ""


class Contacts(BaseModel):
    contacts: list[Contact]


def creer_app(sophonie: Sophonie, demarrer_planificateur: bool = True) -> FastAPI:
    app = FastAPI(title="Sophonie", version="1.0")
    jeton = sophonie.secrets.jeton_api

    def authentifier(authorization: str = Header(default="")) -> None:
        if not jeton:
            raise HTTPException(503, "SOPHONIE_JETON n'est pas défini sur le serveur.")
        attendu = f"Bearer {jeton}"
        if not _secrets.compare_digest(authorization.encode(), attendu.encode()):
            raise HTTPException(401, "Jeton invalide.")

    if demarrer_planificateur:
        from .planificateur import demarrer

        @app.on_event("startup")
        def _demarrer() -> None:
            app.state.planificateur = demarrer(sophonie)

    @app.get("/sante")
    def sante() -> dict[str, Any]:
        return {"ok": True, "heure_locale": sophonie.maintenant().isoformat(timespec="minutes")}

    @app.post("/chat", dependencies=[Depends(authentifier)])
    def chat(q: Question) -> dict[str, str]:
        return {"reponse": sophonie.discuter(q.texte)}

    @app.post("/evenements", dependencies=[Depends(authentifier)])
    def evenement(e: Evenement, taches: BackgroundTasks) -> dict[str, Any]:
        source = e.source if e.source in SOURCES else "autre"
        mid = sophonie.memoire.ajouter_message(source, e.expediteur, e.texte, e.recu_le)
        if e.texte.strip():
            taches.add_task(_trier, sophonie, mid, source, e.expediteur, e.texte)
        return {"id": mid}

    @app.get("/alertes", dependencies=[Depends(authentifier)])
    def alertes() -> list[dict[str, Any]]:
        return sophonie.memoire.alertes_non_vues()

    @app.post("/alertes/{alerte_id}/vue", dependencies=[Depends(authentifier)])
    def alerte_vue(alerte_id: int) -> dict[str, bool]:
        sophonie.memoire.marquer_alerte_vue(alerte_id)
        return {"ok": True}

    @app.post("/contacts", dependencies=[Depends(authentifier)])
    def contacts(c: Contacts) -> dict[str, int]:
        return {"enregistres": sophonie.memoire.enregistrer_contacts([x.model_dump() for x in c.contacts])}

    @app.get("/rappels", dependencies=[Depends(authentifier)])
    def rappels() -> list[dict[str, Any]]:
        return sophonie.memoire.rappels_a_venir()

    @app.get("/gouts", dependencies=[Depends(authentifier)])
    def gouts() -> list[dict[str, Any]]:
        return sophonie.memoire.gouts()

    @app.delete("/gouts/{gout_id}", dependencies=[Depends(authentifier)])
    def oublier(gout_id: int) -> dict[str, bool]:
        sophonie.memoire.oublier_gout(gout_id)
        return {"ok": True}

    @app.post("/moments/{nom}", dependencies=[Depends(authentifier)])
    def moment(nom: str) -> dict[str, str]:
        productions = {"briefing": sophonie.briefing_matin, "midi": sophonie.point_midi,
                       "coucher": sophonie.coucher}
        if nom not in productions:
            raise HTTPException(404, f"Moments disponibles : {', '.join(productions)}")
        return {"texte": productions[nom]()}

    # --- Webhook WhatsApp Business (Meta Cloud API) ---
    @app.get("/webhook/whatsapp", response_class=PlainTextResponse)
    def verification_meta(mode: str = Query("", alias="hub.mode"),
                          verify_token: str = Query("", alias="hub.verify_token"),
                          challenge: str = Query("", alias="hub.challenge")) -> str:
        attendu = sophonie.secrets.whatsapp_verify_token
        if mode == "subscribe" and attendu and _secrets.compare_digest(verify_token, attendu):
            return challenge
        raise HTTPException(403, "Vérification refusée.")

    @app.post("/webhook/whatsapp")
    async def reception_meta(requete: Request, taches: BackgroundTasks,
                             signature: str = Header(default="", alias="X-Hub-Signature-256")) -> dict[str, int]:
        corps = await requete.body()
        secret = sophonie.secrets.whatsapp_app_secret
        if not secret:
            raise HTTPException(503, "WHATSAPP_APP_SECRET n'est pas défini.")
        attendu = "sha256=" + hmac.new(secret.encode(), corps, hashlib.sha256).hexdigest()
        if not hmac.compare_digest(signature, attendu):
            raise HTTPException(401, "Signature invalide.")
        recus = WhatsAppBusiness.extraire_messages(await requete.json())
        for m in recus:
            expediteur = f"{m['nom']} ({m['expediteur']})" if m["nom"] else m["expediteur"]
            mid = sophonie.memoire.ajouter_message("whatsapp_business", expediteur, m["texte"])
            taches.add_task(_trier, sophonie, mid, "whatsapp_business", expediteur, m["texte"])
        return {"recus": len(recus)}

    return app


def _trier(sophonie: Sophonie, mid: int, source: str, expediteur: str, texte: str) -> None:
    try:
        sophonie.trier_message(mid, source, expediteur, texte)
    except Exception:
        log.exception("Échec du tri du message %s", mid)
