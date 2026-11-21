"""Le rythme de la journée : briefing, prières, point de midi, coucher, rappels et anniversaires."""

from __future__ import annotations

import logging
from datetime import datetime, timedelta

from apscheduler.schedulers.background import BackgroundScheduler

from .cerveau import Sophonie

log = logging.getLogger("sophonie")


def _hm(heure: str) -> tuple[int, int]:
    h, m = heure.split(":")
    return int(h), int(m)


def _moins(heure: str, minutes: int) -> tuple[int, int]:
    h, m = _hm(heure)
    t = datetime(2000, 1, 1, h, m) - timedelta(minutes=minutes)
    return t.hour, t.minute


def verifier_rappels(sophonie: Sophonie) -> int:
    """Transforme les rappels arrivés à échéance en alertes, et replanifie les récurrents."""
    maintenant = sophonie.maintenant()
    dus = sophonie.memoire.rappels_dus(maintenant.isoformat())
    for r in dus:
        sophonie.memoire.creer_alerte("⏰ Rappel", r["texte"], "haute")
        if r["recurrence"] in ("quotidien", "hebdomadaire"):
            pas = timedelta(days=1 if r["recurrence"] == "quotidien" else 7)
            suivante = datetime.fromisoformat(r["echeance"])
            while suivante <= maintenant:
                suivante += pas
            sophonie.memoire.reporter_rappel(r["id"], suivante.isoformat())
        else:
            sophonie.memoire.terminer_rappel(r["id"])
    return len(dus)


def _publier(sophonie: Sophonie, titre: str, production, priorite: str = "normale") -> None:
    try:
        texte = production()
        if texte:
            sophonie.memoire.creer_alerte(titre, texte, priorite)
    except Exception:  # une panne ne doit jamais arrêter le planificateur
        log.exception("Échec de « %s »", titre)


def demarrer(sophonie: Sophonie) -> BackgroundScheduler:
    p = sophonie.profil
    planif = BackgroundScheduler(timezone=p.tz)

    h, m = _hm(p.briefing_matin)
    planif.add_job(_publier, "cron", hour=h, minute=m, id="briefing",
                   args=[sophonie, "☀️ Bonjour", sophonie.briefing_matin])

    h, m = _hm(p.point_midi)
    planif.add_job(_publier, "cron", hour=h, minute=m, id="midi",
                   args=[sophonie, "🕛 Point de midi", sophonie.point_midi])

    for i, priere in enumerate(p.prieres):
        h, m = _hm(priere.heure)
        planif.add_job(_publier, "cron", hour=h, minute=m, id=f"priere-{i}",
                       args=[sophonie, f"🙏 {priere.nom}", lambda nom=priere.nom: sophonie.priere(nom), "haute"])

    h, m = _moins(p.heure_coucher, 30)
    planif.add_job(_publier, "cron", hour=h, minute=m, id="coucher",
                   args=[sophonie, "🌙 Bientôt l'heure de dormir", sophonie.coucher])

    def anniversaires():
        for nom in sophonie.anniversaires_du_jour():
            sophonie.memoire.creer_alerte("🎂 Anniversaire", f"C'est l'anniversaire de {nom} aujourd'hui.", "haute")

    h, m = _hm(p.heure_lever)
    planif.add_job(anniversaires, "cron", hour=h, minute=m, id="anniversaires")
    planif.add_job(verifier_rappels, "interval", minutes=1, id="rappels", args=[sophonie])

    planif.start()
    log.info("Planificateur démarré (%s) : %s", p.fuseau, [j.id for j in planif.get_jobs()])
    return planif
