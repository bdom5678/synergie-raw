"""Configuration de Sophonie : profil personnel (fichier local) et secrets (variables d'environnement)."""

from __future__ import annotations

import json
import os
from dataclasses import dataclass, field
from pathlib import Path
from zoneinfo import ZoneInfo

RACINE = Path(__file__).resolve().parent.parent


@dataclass
class Priere:
    nom: str
    heure: str  # "HH:MM"


@dataclass
class Profil:
    prenom: str = "vous"
    fuseau: str = "Africa/Douala"
    langue: str = "français"
    vouvoiement: bool = True
    heure_lever: str = "07:00"
    heure_coucher: str = "23:30"
    foi: str = "chrétienne"
    prieres: list[Priere] = field(default_factory=list)
    briefing_matin: str = "07:05"
    point_midi: str = "12:45"
    contacts_prioritaires: list[str] = field(default_factory=list)
    applications_suivies: list[str] = field(default_factory=list)

    @property
    def tz(self) -> ZoneInfo:
        return ZoneInfo(self.fuseau)

    @classmethod
    def charger(cls, chemin: Path | None = None) -> "Profil":
        chemin = chemin or Path(os.environ.get("SOPHONIE_PROFIL", RACINE / "profil.json"))
        if not chemin.exists():
            chemin = RACINE / "profil.exemple.json"
        donnees = json.loads(chemin.read_text(encoding="utf-8"))
        donnees["prieres"] = [Priere(**p) for p in donnees.get("prieres", [])]
        connus = set(cls.__dataclass_fields__)
        return cls(**{k: v for k, v in donnees.items() if k in connus})


@dataclass
class Secrets:
    """Lus uniquement depuis l'environnement (ou un fichier .env non versionné)."""

    jeton_api: str = ""  # jeton partagé avec l'application Android
    whatsapp_token: str = ""
    whatsapp_phone_id: str = ""
    whatsapp_verify_token: str = ""
    whatsapp_app_secret: str = ""  # sert à vérifier la signature des webhooks Meta
    google_token_file: str = ""
    base_de_donnees: str = ""
    modele: str = "claude-opus-5-5"

    @classmethod
    def depuis_env(cls) -> "Secrets":
        _charger_dotenv(RACINE / ".env")
        return cls(
            jeton_api=os.environ.get("SOPHONIE_JETON", ""),
            whatsapp_token=os.environ.get("WHATSAPP_TOKEN", ""),
            whatsapp_phone_id=os.environ.get("WHATSAPP_PHONE_ID", ""),
            whatsapp_verify_token=os.environ.get("WHATSAPP_VERIFY_TOKEN", ""),
            whatsapp_app_secret=os.environ.get("WHATSAPP_APP_SECRET", ""),
            google_token_file=os.environ.get("GOOGLE_TOKEN_FILE", str(RACINE / "google_token.json")),
            base_de_donnees=os.environ.get("SOPHONIE_DB", str(RACINE / "sophonie.db")),
            modele=os.environ.get("SOPHONIE_MODELE", "claude-opus-5-5"),
        )


def _charger_dotenv(chemin: Path) -> None:
    if not chemin.exists():
        return
    for ligne in chemin.read_text(encoding="utf-8").splitlines():
        ligne = ligne.strip()
        if not ligne or ligne.startswith("#") or "=" not in ligne:
            continue
        cle, valeur = ligne.split("=", 1)
        os.environ.setdefault(cle.strip(), valeur.strip().strip('"').strip("'"))
