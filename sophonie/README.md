# Sophonie · assistante personnelle

Sophonie vous accompagne toute la journée : briefing du réveil, prières du matin et du soir, point de midi, rappel du coucher, alertes sur les messages importants, rappels « à ne pas oublier » et mémoire de vos goûts.

Elle tient en deux parties :

| Partie | Rôle |
|---|---|
| `serveur/` | Le cerveau (Python, Claude). Il garde la mémoire (SQLite), lit Gmail et Google Agenda, reçoit WhatsApp Business, trie les messages, prépare les briefings et les prières, et produit les alertes. |
| `android/` | L'application du téléphone (Kotlin). Elle lit les notifications WhatsApp, WhatsApp Business, SMS et mails, les appels manqués et les contacts, affiche les alertes, déclenche les rappels de prière et vous laisse discuter avec Sophonie. |

> ⚠️ Ce dépôt est publié sur GitHub Pages. Ne versionnez **jamais** `profil.json`, `.env`, `sophonie.db` ni les fichiers `google_*.json` : le `.gitignore` les exclut déjà.

## Ce que Sophonie peut et ne peut pas faire

- **WhatsApp personnel** : pas d'API officielle. Sophonie lit les messages **à travers les notifications Android**. Elle ne répond pas à votre place sur WhatsApp personnel.
- **WhatsApp Business** : lecture par notification et, si vous le configurez, réception et envoi par l'**API Cloud officielle de Meta**. Elle n'envoie un message **qu'après votre accord explicite** sur le texte et le destinataire.
- **Mails** : Gmail en lecture seule (pro et perso, un jeton par compte), plus les notifications des autres applications de mail.
- **Agenda** : Google Agenda en lecture seule.
- **Sécurité** : les messages reçus sont traités comme des données, jamais comme des ordres. Un message qui demande d'envoyer, payer ou transmettre un code est signalé comme suspect.

## 1. Lancer le serveur

Il doit tourner en permanence et être joignable par votre téléphone en HTTPS. Un petit VPS (1 à 2 vCPU, 2 Go de RAM, Ubuntu 24.04) suffit largement : le gros du travail est fait par Claude, pas par le serveur. Un ordinateur ou un Raspberry Pi à la maison est déconseillé : à chaque coupure de courant ou d'internet, Sophonie s'arrête.

### Serveur gratuit : Google Cloud e2-micro

Google Cloud offre une petite machine permanente gratuite (une seule par compte). C'est le même compte que pour la clé Gmail.

1. Sur [console.cloud.google.com](https://console.cloud.google.com), dans le projet `Sophonie` : **Facturation** > associez un compte de facturation. Une carte est demandée pour vérifier votre identité, mais l'offre gratuite n'est pas débitée.
2. **Protégez-vous** : Facturation > **Budgets et alertes** > créez un budget de **1 $** avec alerte par e-mail. Vous serez prévenu à la moindre dépense.
3. **Compute Engine** > Instances de VM > **Créer une instance** :
   - Région : **us-central1** (Iowa), **us-east1** ou **us-west1**. Seules ces régions sont gratuites.
   - Série **E2**, type **e2-micro**.
   - Disque de démarrage : **Ubuntu 24.04 LTS**, type **Disque persistant standard**, **30 Go** au maximum (pas « équilibré »).
   - Pare-feu : cochez **Autoriser le trafic HTTP** et **Autoriser le trafic HTTPS**.
4. Une fois l'instance créée, cliquez sur **SSH** dans la liste : un terminal s'ouvre dans le navigateur. Lancez-y les deux commandes ci-dessous.

### Installation automatique sur un VPS (recommandé)

Connecté en SSH au serveur :

```bash
curl -fsSL https://raw.githubusercontent.com/bdom5678/synergie-raw/ccr-2019a868-qtmewc/sophonie/deploiement/installer.sh -o installer.sh
sudo bash installer.sh
```

Le script installe tout : utilisateur dédié, service qui redémarre tout seul, HTTPS automatique (adresse gratuite `https://<ip>.sslip.io`, ou votre domaine : `sudo bash installer.sh mon.domaine.com`) et pare-feu. Il demande votre clé API Claude, puis affiche l'**adresse** et le **jeton** à saisir dans l'application. Pour mettre Sophonie à jour, relancez le même script.

Ensuite, adaptez `/opt/sophonie/sophonie/serveur/profil.json` et faites `sudo systemctl restart sophonie`.

### Installation manuelle

```bash
cd sophonie/serveur
python3 -m venv .venv && . .venv/bin/activate
pip install -r requirements.txt
cp profil.exemple.json profil.json      # puis adaptez prénom, heures, prières
cat > .env <<'EOF'
ANTHROPIC_API_KEY=...                   # clé API Claude (console.anthropic.com)
SOPHONIE_JETON=...                      # long secret aléatoire, à recopier dans l'app Android
# Optionnel : WhatsApp Business (developers.facebook.com > WhatsApp > Cloud API)
WHATSAPP_TOKEN=...
WHATSAPP_PHONE_ID=...
WHATSAPP_VERIFY_TOKEN=...               # secret choisi par vous pour la vérification du webhook
WHATSAPP_APP_SECRET=...                 # « Clé secrète de l'application » Meta
EOF
python -m sophonie                      # écoute sur le port 8765
```

Générer un jeton : `python3 -c "import secrets; print(secrets.token_urlsafe(32))"`.

### Clé Google (Gmail et Agenda)

À faire une seule fois, depuis un ordinateur, sur [console.cloud.google.com](https://console.cloud.google.com) :

1. **Projet** : en haut, « Sélectionner un projet » > **Nouveau projet** > nom `Sophonie` > Créer.
2. **APIs** : menu « API et services » > **Bibliothèque**. Activez **Gmail API**, puis **Google Calendar API**.
3. **Écran de consentement** : « API et services » > **Écran de consentement OAuth** (ou « Google Auth Platform »). Type **Externe**, nom de l'application `Sophonie`, votre adresse e-mail en contact. Inutile d'ajouter des champs d'application : le script les demande lui-même.
4. **Utilisateurs** : dans « Audience », ajoutez comme utilisateurs test toutes les adresses que Sophonie doit lire (pro et perso).
5. **Publier** : toujours dans « Audience », cliquez sur **Publier l'application** (passer « En production »). Sans cela, Google coupe l'accès tous les 7 jours. Inutile de demander une validation à Google : pour votre usage personnel, vous verrez simplement l'avertissement « Google n'a pas validé cette application ». Cliquez sur « Paramètres avancés » puis « Accéder à Sophonie ».
6. **Identifiant** : « API et services » > **Identifiants** > Créer des identifiants > **ID client OAuth** > type **Application de bureau** > Créer, puis **Télécharger le JSON**.
7. **Envoyer le fichier sur le serveur** (depuis votre ordinateur) :
   ```bash
   scp client_secret_*.json root@ADRESSE_DU_SERVEUR:/opt/sophonie/sophonie/serveur/google_client_secret.json
   ssh root@ADRESSE_DU_SERVEUR "chown sophonie: /opt/sophonie/sophonie/serveur/google_client_secret.json && chmod 600 /opt/sophonie/sophonie/serveur/google_client_secret.json"
   ```
8. **Connecter chaque compte**, un par un. Le tunnel SSH permet à votre navigateur de terminer la connexion :
   ```bash
   ssh -L 8085:localhost:8085 root@ADRESSE_DU_SERVEUR
   sudo -u sophonie bash -c 'cd /opt/sophonie/sophonie/serveur && .venv/bin/python -m sophonie.connexion_google perso'
   ```
   Ouvrez le lien affiché, choisissez le compte, acceptez, puis recommencez avec `pro`. Terminez par `sudo systemctl restart sophonie`.

Sophonie lit alors tous les comptes connectés, en lecture seule, et fusionne mails et agendas.

Si votre messagerie professionnelle n'est pas sur Google (Outlook/Microsoft 365, par exemple), elle passe par les notifications Android de l'application de mail, sans connexion directe.

**Webhook WhatsApp Business** : dans Meta, déclarez l'URL `https://votre-serveur/webhook/whatsapp` avec votre `WHATSAPP_VERIFY_TOKEN`, et abonnez-vous au champ `messages`.

Tests : `python -m pytest -q`.

## 2. Installer l'application Android

1. Ouvrez le dossier `android/` dans Android Studio, laissez Gradle se synchroniser, puis **Run** sur votre téléphone (débogage USB activé).
2. Dans l'app, saisissez l'adresse du serveur et le jeton, puis touchez **Enregistrer**.
3. Touchez **Autorisations** : acceptez contacts, journal d'appels et notifications, puis activez Sophonie dans **Accès aux notifications**.
4. Touchez **Contacts** pour transmettre vos contacts et leurs anniversaires.
5. Pour des rappels de prière à la minute près : Paramètres > Applications > Sophonie > **Alarmes et rappels** > autoriser. Excluez aussi Sophonie de l'**optimisation de batterie**.

## 3. Le rythme de la journée (fuseau Africa/Douala)

| Heure | Moment |
|---|---|
| 07:00 | 🙏 Prière du matin, avec verset et prière |
| 07:00 | 🎂 Anniversaires du jour |
| 07:05 | ☀️ Briefing : agenda, mails importants, messages sans réponse, rappels |
| 12:45 | 🕛 Point de midi |
| 23:00 | 🌙 Préparation au coucher et programme de demain, puis 🙏 prière du soir |
| 23:30 | 🌙 Il est l'heure de dormir (rappel sur le téléphone) |
| En continu | ⚠️ Alertes sur les messages urgents, ⏰ rappels à l'échéance |

Tout se règle dans `profil.json` côté serveur (et dans les heures de l'application pour les rappels locaux).

## API du serveur

Toutes les routes, sauf `/sante` et le webhook, exigent `Authorization: Bearer <SOPHONIE_JETON>`.

| Route | Usage |
|---|---|
| `POST /chat` `{texte}` | Discuter avec Sophonie |
| `POST /evenements` `{source, expediteur, texte}` | Message reçu sur le téléphone (trié en arrière-plan) |
| `GET /alertes` · `POST /alertes/{id}/vue` | Alertes à afficher |
| `POST /contacts` | Synchroniser les contacts |
| `GET /rappels` · `GET /gouts` · `DELETE /gouts/{id}` | Consulter les rappels, consulter ou effacer ce que Sophonie sait de vous |
| `POST /moments/{briefing,midi,coucher}` | Produire un moment à la demande |
| `GET/POST /webhook/whatsapp` | Webhook Meta (signature vérifiée) |
