"""Connexion unique à Google (Gmail + Agenda, lecture seule).

1. Créez un identifiant OAuth « Application de bureau » dans Google Cloud Console
   (APIs Gmail et Google Calendar activées) et téléchargez-le sous `serveur/google_client_secret.json`.
2. Lancez : python -m sophonie.connexion_google
3. Autorisez l'accès dans le navigateur : le jeton est enregistré dans `serveur/google_token.json`
   (fichier ignoré par git).

Pour plusieurs comptes (pro et perso), lancez la commande une fois par compte avec
GOOGLE_TOKEN_FILE=google_token_pro.json, etc.
"""

from __future__ import annotations

from pathlib import Path

from .config import RACINE, Secrets
from .integrations import SCOPES_GOOGLE


def main() -> None:
    from google_auth_oauthlib.flow import InstalledAppFlow

    secret = RACINE / "google_client_secret.json"
    if not secret.exists():
        raise SystemExit(f"Fichier manquant : {secret}")
    flow = InstalledAppFlow.from_client_secrets_file(str(secret), SCOPES_GOOGLE)
    creds = flow.run_local_server(port=0)
    cible = Path(Secrets.depuis_env().google_token_file)
    cible.write_text(creds.to_json(), encoding="utf-8")
    print(f"Google connecté. Jeton enregistré dans {cible}")


if __name__ == "__main__":
    main()
