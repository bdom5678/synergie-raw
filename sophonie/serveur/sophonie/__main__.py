"""Lancement : python -m sophonie  (écoute sur 0.0.0.0:8765 par défaut, variable SOPHONIE_PORT)."""

import logging
import os

import uvicorn

from .api import creer_app
from .cerveau import Sophonie
from .config import Profil, Secrets
from .memoire import Memoire


def main() -> None:
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
    secrets = Secrets.depuis_env()
    sophonie = Sophonie(Profil.charger(), secrets, Memoire(secrets.base_de_donnees))
    uvicorn.run(creer_app(sophonie), host=os.environ.get("SOPHONIE_HOTE", "0.0.0.0"),
                port=int(os.environ.get("SOPHONIE_PORT", "8765")))


if __name__ == "__main__":
    main()
