"""Connexions externes : Gmail, Google Agenda (OAuth, lecture seule) et WhatsApp Business (Cloud API officielle de Meta).

Chaque intégration est optionnelle : si elle n'est pas configurée, elle renvoie un message clair
au lieu de planter, et Sophonie le signale.
"""

from __future__ import annotations

import base64
from datetime import datetime
from pathlib import Path
from typing import Any

import httpx

SCOPES_GOOGLE = [
    "https://www.googleapis.com/auth/gmail.readonly",
    "https://www.googleapis.com/auth/calendar.readonly",
]

GRAPH_API = "https://graph.facebook.com/v21.0"


class NonConfigure(Exception):
    pass


class CompteGoogle:
    """Un compte Google (Gmail + Agenda) lu avec un jeton OAuth créé par `python -m sophonie.connexion_google`."""

    def __init__(self, fichier_jeton: str, nom: str = ""):
        self.fichier_jeton = fichier_jeton
        self.nom = nom or nom_du_compte(fichier_jeton)
        self._gmail = None
        self._agenda = None

    def _services(self):
        if self._gmail is not None:
            return self._gmail, self._agenda
        if not Path(self.fichier_jeton).exists():
            raise NonConfigure(f"Le compte Google « {self.nom} » n'est pas connecté.")
        from google.auth.exceptions import RefreshError
        from google.auth.transport.requests import Request
        from google.oauth2.credentials import Credentials
        from googleapiclient.discovery import build

        creds = Credentials.from_authorized_user_file(self.fichier_jeton, SCOPES_GOOGLE)
        if creds.expired and creds.refresh_token:
            try:
                creds.refresh(Request())
            except RefreshError as e:
                raise NonConfigure(f"L'accès au compte Google « {self.nom} » a expiré : "
                                   "relancez python -m sophonie.connexion_google.") from e
            Path(self.fichier_jeton).write_text(creds.to_json(), encoding="utf-8")
        self._gmail = build("gmail", "v1", credentials=creds, cache_discovery=False)
        self._agenda = build("calendar", "v3", credentials=creds, cache_discovery=False)
        return self._gmail, self._agenda

    def mails(self, requete: str, limite: int = 10) -> list[dict[str, Any]]:
        gmail, _ = self._services()
        res = gmail.users().messages().list(userId="me", q=requete, maxResults=limite).execute()
        sortie = []
        for m in res.get("messages", []):
            detail = gmail.users().messages().get(
                userId="me", id=m["id"], format="metadata",
                metadataHeaders=["From", "Subject", "Date"]).execute()
            entetes = {h["name"]: h["value"] for h in detail["payload"].get("headers", [])}
            sortie.append({
                "id": f"{self.nom}:{m['id']}",
                "compte": self.nom,
                "de": entetes.get("From", ""),
                "sujet": entetes.get("Subject", ""),
                "date": entetes.get("Date", ""),
                "extrait": detail.get("snippet", ""),
            })
        return sortie

    def lire_mail(self, mail_id: str) -> str:
        gmail, _ = self._services()
        detail = gmail.users().messages().get(userId="me", id=mail_id, format="full").execute()
        return _texte_du_mail(detail["payload"])[:8000] or detail.get("snippet", "")

    def evenements(self, debut: datetime, fin: datetime) -> list[dict[str, Any]]:
        _, agenda = self._services()
        sortie = []
        for cal in agenda.calendarList().list().execute().get("items", []):
            if not cal.get("selected", True):
                continue
            res = agenda.events().list(
                calendarId=cal["id"], timeMin=debut.isoformat(), timeMax=fin.isoformat(),
                singleEvents=True, orderBy="startTime").execute()
            for e in res.get("items", []):
                sortie.append({
                    "titre": e.get("summary", "(sans titre)"),
                    "debut": e["start"].get("dateTime", e["start"].get("date")),
                    "fin": e["end"].get("dateTime", e["end"].get("date")),
                    "lieu": e.get("location", ""),
                    "agenda": f"{self.nom} / {cal.get('summary', '')}",
                })
        return sortie


def nom_du_compte(fichier: str) -> str:
    """google_token_pro.json -> pro ; google_token.json -> principal."""
    base = Path(fichier).stem
    return base.removeprefix("google_token").lstrip("_-") or "principal"


class Google:
    """Regroupe tous les comptes Google connectés (pro, perso...) : les résultats sont fusionnés."""

    def __init__(self, fichiers_jetons: list[str]):
        self.comptes = [CompteGoogle(f) for f in fichiers_jetons]

    def _actifs(self) -> list[CompteGoogle]:
        actifs = [c for c in self.comptes if Path(c.fichier_jeton).exists()]
        if not actifs:
            raise NonConfigure("Aucun compte Google n'est connecté (lancez : python -m sophonie.connexion_google).")
        return actifs

    def _collecter(self, action) -> tuple[list[dict[str, Any]], list[str]]:
        resultats, problemes = [], []
        for compte in self._actifs():
            try:
                resultats += action(compte)
            except NonConfigure as e:
                problemes.append(str(e))
        if not resultats and problemes:
            raise NonConfigure(" ".join(problemes))
        return resultats, problemes

    def mails(self, requete: str = "is:unread newer_than:1d -category:promotions -category:social",
              limite: int = 10) -> list[dict[str, Any]]:
        resultats, problemes = self._collecter(lambda c: c.mails(requete, limite))
        return resultats + [{"avertissement": p} for p in problemes]

    def lire_mail(self, mail_id: str) -> str:
        nom, _, identifiant = mail_id.partition(":")
        for compte in self._actifs():
            if compte.nom == nom:
                return compte.lire_mail(identifiant)
        raise NonConfigure(f"Compte « {nom} » inconnu.")

    def evenements(self, debut: datetime, fin: datetime) -> list[dict[str, Any]]:
        resultats, problemes = self._collecter(lambda c: c.evenements(debut, fin))
        return sorted(resultats, key=lambda e: e["debut"]) + [{"avertissement": p} for p in problemes]


def _texte_du_mail(partie: dict[str, Any]) -> str:
    if partie.get("mimeType") == "text/plain" and partie.get("body", {}).get("data"):
        return base64.urlsafe_b64decode(partie["body"]["data"]).decode("utf-8", errors="replace")
    for sous in partie.get("parts", []) or []:
        texte = _texte_du_mail(sous)
        if texte:
            return texte
    return ""


class WhatsAppBusiness:
    """Envoi et réception via la WhatsApp Cloud API (numéro WhatsApp Business enregistré chez Meta)."""

    def __init__(self, token: str, phone_id: str, verify_token: str, client: httpx.Client | None = None):
        self.token = token
        self.phone_id = phone_id
        self.verify_token = verify_token
        self.client = client or httpx.Client(timeout=20)

    @property
    def configure(self) -> bool:
        return bool(self.token and self.phone_id)

    def envoyer_texte(self, destinataire: str, texte: str) -> dict[str, Any]:
        if not self.configure:
            raise NonConfigure("WhatsApp Business n'est pas configuré (WHATSAPP_TOKEN, WHATSAPP_PHONE_ID).")
        numero = "".join(ch for ch in destinataire if ch.isdigit())
        r = self.client.post(
            f"{GRAPH_API}/{self.phone_id}/messages",
            headers={"Authorization": f"Bearer {self.token}"},
            json={"messaging_product": "whatsapp", "to": numero, "type": "text", "text": {"body": texte}},
        )
        r.raise_for_status()
        return r.json()

    @staticmethod
    def extraire_messages(charge: dict[str, Any]) -> list[dict[str, str]]:
        """Transforme le webhook Meta en liste {expediteur, nom, texte, horodatage}."""
        sortie = []
        for entree in charge.get("entry", []):
            for changement in entree.get("changes", []):
                valeur = changement.get("value", {})
                noms = {c.get("wa_id"): c.get("profile", {}).get("name", "") for c in valeur.get("contacts", [])}
                for m in valeur.get("messages", []):
                    if m.get("type") == "text":
                        texte = m["text"]["body"]
                    else:
                        texte = f"[{m.get('type', 'message')}]"
                    sortie.append({
                        "expediteur": m.get("from", ""),
                        "nom": noms.get(m.get("from"), ""),
                        "texte": texte,
                        "horodatage": m.get("timestamp", ""),
                    })
        return sortie
