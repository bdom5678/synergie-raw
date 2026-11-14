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
- Générateur : claude/espace-cbo-generateur.py ; gabarit : claude/espace-cbo-gabarit.html (aussi dans outils/ du dépôt, branche main).
- Commande : python3 build_espace_cbo.py --db <dossier avec referrals/, cbos/, config/settings.json> --template espace_cbo_template.html --secret "<secret>" --out page.html [--codes codes.csv], puis Artifact publish avec url ci-dessus.
- Actualisation automatique (13/11/2026) : étape 5 de la tâche planifiée « Vue équipe Synergie » (jours ouvrés, 6 h 55 Kinshasa) ; elle régénère la page et la republie sur la même URL. Le secret des codes est dans cette tâche.

## Codes dans l'outil de suivi (13/11/2026)
- Onglet « CBO participants » : chaque CBO actif affiche « Code Espace CBO : XXXXXX » (tableau et fiche), visible des seuls administrateurs de l'outil. Document de base codes/cbo, règle d'accès read/write admin (vérifié : invisible au niveau « interact »).
- Nouveau CBO ajouté dans l'outil : la fiche indique « attribué à la prochaine mise à jour » ; à l'étape 5 de la tâche « Vue équipe Synergie » (jours ouvrés 6 h 55), le générateur (option --codes-doc) calcule son code, l'inscrit dans codes/cbo et l'ajoute à l'Espace CBO dans la même exécution. Les codes existants ne changent pas.

## Objectifs et documents (13/11/2026, révisé)
- Après connexion, le CBO voit trois encadrés :
  - « Mon objectif de référencements » : objectif individuel = réalisés + arrondi supérieur(k × (2 + racine(réalisés))). k répartit le reste à faire (objectifRefs − référencements enregistrés) en supposant que la moitié des CBO ayant déjà référé au moins une affaire n'atteindra pas son objectif : k = reste / (Σ poids des CBO sans référencement + ½ Σ poids des CBO ayant référé). Plus un CBO a référé, plus son objectif additionnel est élevé. Au 13/11 : 0 réalisé → 6 ; 1 → 10 ; 2 → 13 ; 5 → 18 ; 8 → 23 (total des objectifs ≈ 704 pour 500 visés). Recalculé à chaque génération. Rythme hebdomadaire jusqu'au 31/12.
  - « Primes pour être 1er de ma zone » : prime encaissée TTC du 1er de la zone − sa prime encaissée + 1 $ ; avance sur le 2e s'il est déjà 1er.
  - « Ma zone » : primes encaissées de la zone, affaires encaissées / référencées, rang de la zone sur 4, nombre de CBO.
- L'objectif global (500 référencements, volume réalisé de la campagne) n'apparaît nulle part dans la page publique : réservé aux administrateurs (outil de suivi, vue équipe). Les objectifs et statistiques de zone sont dans les données chiffrées de chaque CBO.
- Section « Documents de la campagne » : PDF publiés comme fichiers de l'artefact (dossier docs/) : livret, fiche de référencement, kit d'argumentaire FR, flyers REF 2026 (7 produits), présentations Group Life et Santé VITASUR. Les publications quotidiennes sans `files` conservent ces PDF ; pour remplacer un document, republier avec `files` sur le même chemin.
- Lecture des documents (14/11/2026) : la page publiée est affichée dans un cadre protégé où un lien vers un PDF ne s'ouvre pas. Un clic sur un document l'ouvre dans un lecteur intégré (pdf.js 3.11.174, fichiers lib/pdf.min.js et lib/pdf.worker.min.js publiés avec l'artefact). Le bouton « Télécharger » passe par la capacité `downloads` (déclarée sur l'artefact ; les publications sans `capabilities` la conservent) et n'apparaît que si le visiteur peut l'utiliser.
