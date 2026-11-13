# Espace CBO Synergie (13/11/2026)

Page publique pour les CBO sans compte Claude : https://claude.ai/artifact/MGsf4qTZXKzMuXDoEp8iL5
À partager par Malachie depuis le menu Partager : « toute personne disposant du lien ».

## Contenu
- Mes affaires : zone + nom (liste déroulante, 77 CBO actifs, retirés exclus) + code personnel à 6 caractères. Affiche commission acquise (prime nette encaissée × taux), commission en jeu (offres cotées), affaires (client, produit, statut, montants), place dans la zone.
- Simulateur ouvert à tous : lignes produit + prime nette visée, taux de config/settings.rates.
- Podium de zone : seuils actuels (montants, sans noms) et bonus 1er/2e/3e (1 500/900/600 Kinshasa et Sud ; 650/400/250 Centre et Est), affichés comme « indicatifs, sous réserve de validation ». Classement = logique de l'outil (prime totale encaissée, au moins une affaire encaissée).

## Sécurité
- Données de chaque CBO chiffrées (AES-256-GCM, PBKDF2-SHA256 150 000 itérations, sel = code|id du CBO). Seuls la liste des noms, les taux et les seuils du podium sont en clair.
- Codes dérivés par HMAC-SHA256 d'un secret : stables d'un jour à l'autre, un nouveau CBO reçoit automatiquement son code.
- Secret de génération des codes : conservé par Malachie (non écrit ici). Sans lui, une régénération produit de nouveaux codes.
- Liste des codes : Codes_Espace_CBO_Synergie.xlsx (envoyé le 13/11) ; régénérable avec --codes.

## Mise à jour
- Générateur : claude/espace-cbo-generateur.py ; gabarit : claude/espace-cbo-gabarit.html (copies aussi dans outils/ du clone local, non poussées : push refusé le 13/11).
- Commande : python3 build_espace_cbo.py --db <dossier avec referrals/, cbos/, config/settings.json> --template espace_cbo_template.html --secret "<secret>" --out page.html [--codes codes.csv], puis Artifact publish avec url ci-dessus.
- Données publiées le 13/11/2026 à 13:24. Actualisation quotidienne pas encore branchée sur une tâche planifiée.
