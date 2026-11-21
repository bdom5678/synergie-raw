"""Connexions externes : Gmail, Google Agenda (OAuth, lecture seule) et WhatsApp Business (Cloud API officielle de Meta).

Chaque intégration est optionnelle : si elle n'est pas configurée, elle renvoie un message clair
au lieu de planter, et Sophonie le signale.
"""

from __future__ import annotations

import base64
from datetime import datetime, timedelta
from pathlib import Path
from typing import Any
from zoneinfo import ZoneInfo

import httpx

SCOPES_GOOGLE = [
    "https://www.googleapis.com/auth/gmail.readonly",
    "https://www.googleapis.com/auth/calendar.readonly",
]

GRAPH_API = "https://graph.facebook.com/v21.0"


class NonConfigure(Exception):
    pass


class Google:
    """Lit Gmail et Google Agenda avec un jeton OAuth créé par `python -m sophonie.connexion_google`."""

    def __init__(self, fichier_jeton: str):
        self.fichier_jeton = fichier_jeton
        self._gmail = None
        self._agenda = None

    def _services(self):
        if self._gmail is not None:
            return self._gmail, self._agenda
        if not Path(self.fichier_jeton).exists():
            raise NonConfigure("Google n'est pas connecté (lancez : python -m sophonie.connexion_google).")
        from google.auth.transport.requests import Request
        from google.oauth2.credentials import Credentials
        from googleapiclient.discovery import build

        creds = Credentials.from_authorized_user_file(self.fichier_jeton, SCOPES_GOOGLE)
        if creds.expired and creds.refresh_token:
            creds.refresh(Request())
            Path(self.fichier_jeton).write_text(creds.to_json(), encoding="utf-8")
        self._gmail = build("gmail", "v1", credentials=creds, cache_discovery=False)
        self._agenda = build("calendar", "v3", credentials=creds, cache_discovery=False)
        return self._gmail, self._agenda

    def mails(self, requete: str = "is:unread newer_than:1d -category:promotions -category:social",
              limite: int = 10) -> list[dict[str, Any]]:
        gmail, _ = self._services()
        res = gmail.users().messages().list(userId="me", q=requete, maxResults=limite).execute()
        sortie = []
        for m in res.get("messages", []):
            detail = gmail.users().messages().get(
                userId="me", id=m["id"], format="metadata",
                metadataHeaders=["From", "Subject", "Date"]).execute()
            entetes = {h["name"]: h["value"] for h in detail["payload"].get("headers", [])}
            sortie.append({
                "id": m["id"],
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
                    "agenda": cal.get("summary", ""),
                })
        return sorted(sortie, key=lambda e: e["debut"])

    def evenements_du_jour(self, jour: datetime, tz: ZoneInfo) -> list[dict[str, Any]]:
        debut = datetime(jour.year, jour.month, jour.day, tzinfo=tz)
        return self.evenements(debut, debut + timedelta(days=1))


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
