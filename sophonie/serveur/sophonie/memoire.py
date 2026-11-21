"""Mémoire de Sophonie (SQLite) : messages reçus, goûts appris, rappels, alertes, conversation, contacts."""

from __future__ import annotations

import json
import sqlite3
import threading
from datetime import datetime, timezone
from typing import Any

SCHEMA = """
CREATE TABLE IF NOT EXISTS messages_recus (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    source TEXT NOT NULL,          -- whatsapp, whatsapp_business, sms, mail, appel, autre
    expediteur TEXT,
    texte TEXT,
    recu_le TEXT NOT NULL,         -- ISO 8601 UTC
    importance TEXT DEFAULT 'inconnue',
    traite INTEGER DEFAULT 0
);
CREATE TABLE IF NOT EXISTS gouts (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    categorie TEXT NOT NULL,       -- cuisine, musique, lecture, sport, travail, relations, habitudes...
    description TEXT NOT NULL,
    sentiment TEXT DEFAULT 'aime', -- aime, n_aime_pas, habitude, objectif
    source TEXT,
    appris_le TEXT NOT NULL,
    UNIQUE(categorie, description)
);
CREATE TABLE IF NOT EXISTS rappels (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    texte TEXT NOT NULL,
    echeance TEXT NOT NULL,        -- ISO 8601 avec fuseau
    recurrence TEXT DEFAULT '',    -- '', quotidien, hebdomadaire
    fait INTEGER DEFAULT 0,
    cree_le TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS alertes (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    titre TEXT NOT NULL,
    corps TEXT NOT NULL,
    priorite TEXT DEFAULT 'normale', -- basse, normale, haute
    creee_le TEXT NOT NULL,
    vue INTEGER DEFAULT 0
);
CREATE TABLE IF NOT EXISTS conversation (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    role TEXT NOT NULL,
    contenu TEXT NOT NULL,
    le TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS contacts (
    nom TEXT PRIMARY KEY,
    telephones TEXT DEFAULT '[]',
    emails TEXT DEFAULT '[]',
    anniversaire TEXT DEFAULT '',
    note TEXT DEFAULT ''
);
"""


def maintenant_utc() -> str:
    return datetime.now(timezone.utc).isoformat(timespec="seconds")


class Memoire:
    def __init__(self, chemin: str = ":memory:"):
        self._verrou = threading.Lock()
        self.db = sqlite3.connect(chemin, check_same_thread=False)
        self.db.row_factory = sqlite3.Row
        self.db.executescript(SCHEMA)
        self.db.commit()

    def _exec(self, sql: str, params: tuple = ()) -> sqlite3.Cursor:
        with self._verrou:
            cur = self.db.execute(sql, params)
            self.db.commit()
            return cur

    def _lignes(self, sql: str, params: tuple = ()) -> list[dict[str, Any]]:
        with self._verrou:
            return [dict(r) for r in self.db.execute(sql, params).fetchall()]

    # --- Messages reçus (notifications Android, webhook WhatsApp Business, mails) ---
    def ajouter_message(self, source: str, expediteur: str, texte: str, recu_le: str | None = None) -> int:
        cur = self._exec(
            "INSERT INTO messages_recus (source, expediteur, texte, recu_le) VALUES (?,?,?,?)",
            (source, expediteur, texte, recu_le or maintenant_utc()),
        )
        return int(cur.lastrowid)

    def marquer_importance(self, message_id: int, importance: str) -> None:
        self._exec("UPDATE messages_recus SET importance=?, traite=1 WHERE id=?", (importance, message_id))

    def messages(self, depuis: str | None = None, source: str | None = None, recherche: str | None = None,
                 limite: int = 30) -> list[dict[str, Any]]:
        sql, params = "SELECT * FROM messages_recus WHERE 1=1", []
        if depuis:
            sql += " AND recu_le >= ?"
            params.append(depuis)
        if source:
            sql += " AND source = ?"
            params.append(source)
        if recherche:
            sql += " AND (texte LIKE ? OR expediteur LIKE ?)"
            params += [f"%{recherche}%", f"%{recherche}%"]
        sql += " ORDER BY recu_le DESC LIMIT ?"
        params.append(limite)
        return self._lignes(sql, tuple(params))

    # --- Goûts et personnalité ---
    def apprendre_gout(self, categorie: str, description: str, sentiment: str = "aime", source: str = "") -> None:
        self._exec(
            "INSERT INTO gouts (categorie, description, sentiment, source, appris_le) VALUES (?,?,?,?,?) "
            "ON CONFLICT(categorie, description) DO UPDATE SET sentiment=excluded.sentiment, "
            "appris_le=excluded.appris_le",
            (categorie.lower().strip(), description.strip(), sentiment, source, maintenant_utc()),
        )

    def oublier_gout(self, gout_id: int) -> None:
        self._exec("DELETE FROM gouts WHERE id=?", (gout_id,))

    def gouts(self, categorie: str | None = None) -> list[dict[str, Any]]:
        if categorie:
            return self._lignes("SELECT * FROM gouts WHERE categorie=? ORDER BY appris_le DESC",
                                (categorie.lower().strip(),))
        return self._lignes("SELECT * FROM gouts ORDER BY categorie, appris_le DESC")

    # --- Rappels ---
    def ajouter_rappel(self, texte: str, echeance: str, recurrence: str = "") -> int:
        cur = self._exec(
            "INSERT INTO rappels (texte, echeance, recurrence, cree_le) VALUES (?,?,?,?)",
            (texte, echeance, recurrence, maintenant_utc()),
        )
        return int(cur.lastrowid)

    def rappels_a_venir(self, inclure_faits: bool = False) -> list[dict[str, Any]]:
        sql = "SELECT * FROM rappels" + ("" if inclure_faits else " WHERE fait=0") + " ORDER BY echeance"
        return self._lignes(sql)

    def rappels_dus(self, avant_iso: str) -> list[dict[str, Any]]:
        return [r for r in self.rappels_a_venir()
                if datetime.fromisoformat(r["echeance"]) <= datetime.fromisoformat(avant_iso)]

    def terminer_rappel(self, rappel_id: int) -> None:
        self._exec("UPDATE rappels SET fait=1 WHERE id=?", (rappel_id,))

    def reporter_rappel(self, rappel_id: int, nouvelle_echeance: str) -> None:
        self._exec("UPDATE rappels SET echeance=? WHERE id=?", (nouvelle_echeance, rappel_id))

    # --- Alertes destinées au téléphone ---
    def creer_alerte(self, titre: str, corps: str, priorite: str = "normale") -> int:
        cur = self._exec(
            "INSERT INTO alertes (titre, corps, priorite, creee_le) VALUES (?,?,?,?)",
            (titre, corps, priorite, maintenant_utc()),
        )
        return int(cur.lastrowid)

    def alertes_non_vues(self) -> list[dict[str, Any]]:
        return self._lignes("SELECT * FROM alertes WHERE vue=0 ORDER BY id")

    def marquer_alerte_vue(self, alerte_id: int) -> None:
        self._exec("UPDATE alertes SET vue=1 WHERE id=?", (alerte_id,))

    # --- Conversation (historique du chat) ---
    def ajouter_echange(self, role: str, contenu: str) -> None:
        self._exec("INSERT INTO conversation (role, contenu, le) VALUES (?,?,?)", (role, contenu, maintenant_utc()))

    def historique(self, limite: int = 20) -> list[dict[str, str]]:
        lignes = self._lignes("SELECT role, contenu FROM conversation ORDER BY id DESC LIMIT ?", (limite,))
        lignes.reverse()
        # L'API exige que la conversation commence par un message utilisateur.
        while lignes and lignes[0]["role"] != "user":
            lignes.pop(0)
        return [{"role": l["role"], "content": l["contenu"]} for l in lignes]

    # --- Contacts (synchronisés depuis le téléphone) ---
    def enregistrer_contacts(self, contacts: list[dict[str, Any]]) -> int:
        for c in contacts:
            self._exec(
                "INSERT INTO contacts (nom, telephones, emails, anniversaire) VALUES (?,?,?,?) "
                "ON CONFLICT(nom) DO UPDATE SET telephones=excluded.telephones, emails=excluded.emails, "
                "anniversaire=CASE WHEN excluded.anniversaire != '' THEN excluded.anniversaire ELSE anniversaire END",
                (c["nom"], json.dumps(c.get("telephones", [])), json.dumps(c.get("emails", [])),
                 c.get("anniversaire", "")),
            )
        return len(contacts)

    def annoter_contact(self, nom: str, note: str) -> bool:
        return self._exec("UPDATE contacts SET note=? WHERE nom=?", (note, nom)).rowcount > 0

    def chercher_contacts(self, recherche: str) -> list[dict[str, Any]]:
        lignes = self._lignes("SELECT * FROM contacts WHERE nom LIKE ? OR telephones LIKE ? LIMIT 10",
                              (f"%{recherche}%", f"%{recherche}%"))
        for l in lignes:
            l["telephones"] = json.loads(l["telephones"])
            l["emails"] = json.loads(l["emails"])
        return lignes

    def anniversaires(self, mois_jour: str) -> list[dict[str, Any]]:
        """mois_jour au format 'MM-JJ' ; l'anniversaire est stocké 'AAAA-MM-JJ' ou '--MM-JJ'."""
        return self._lignes("SELECT nom, anniversaire FROM contacts WHERE anniversaire LIKE ?", (f"%{mois_jour}",))
