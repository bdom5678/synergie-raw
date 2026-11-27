"""Connexion d'un compte Google (Gmail + Agenda, lecture seule). Une fois par compte.

    python -m sophonie.connexion_google perso
    python -m sophonie.connexion_google pro

Le jeton est enregistré dans serveur/google_token_<compte>.json (ignoré par git).

Sur un serveur sans écran : depuis votre ordinateur, ouvrez d'abord un tunnel
    ssh -L 8085:localhost:8085 sophonie@ADRESSE_DU_SERVEUR
lancez la commande dans cette session SSH, puis ouvrez dans votre navigateur le lien affiché.
"""

from __future__ import annotations

import os
import re
import sys

from .config import RACINE
from .integrations import SCOPES_GOOGLE

PORT = 8085


def main() -> None:
    from google_auth_oauthlib.flow import InstalledAppFlow

    compte = sys.argv[1] if len(sys.argv) > 1 else "principal"
    if not re.fullmatch(r"[a-z0-9_-]{1,30}", compte):
        raise SystemExit("Nom de compte invalide : lettres minuscules, chiffres, - ou _ (ex. pro, perso).")
    secret = RACINE / "google_client_secret.json"
    if not secret.exists():
        raise SystemExit(f"Fichier manquant : {secret}\nVoir le README, section « Clé Google ».")

    flow = InstalledAppFlow.from_client_secrets_file(str(secret), SCOPES_GOOGLE)
    avec_ecran = bool(os.environ.get("DISPLAY") or sys.platform in ("darwin", "win32"))
    print(f"Connexion du compte « {compte} ». Choisissez le bon compte Google dans le navigateur.")
    creds = flow.run_local_server(
        host="localhost", port=PORT, open_browser=avec_ecran,
        authorization_prompt_message="Ouvrez ce lien dans votre navigateur : {url}",
        success_message="C'est fait : Sophonie peut lire ce compte. Vous pouvez fermer cette page.",
        access_type="offline", prompt="consent",
    )
    cible = RACINE / ("google_token.json" if compte == "principal" else f"google_token_{compte}.json")
    cible.write_text(creds.to_json(), encoding="utf-8")
    os.chmod(cible, 0o600)
    print(f"Compte « {compte} » connecté. Redémarrez Sophonie : sudo systemctl restart sophonie")


if __name__ == "__main__":
    main()
