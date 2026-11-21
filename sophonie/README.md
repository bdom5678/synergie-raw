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

Il doit tourner en permanence et être joignable par votre téléphone en HTTPS : un petit VPS, un Raspberry Pi derrière un tunnel (Cloudflare Tunnel, Tailscale Funnel), etc.

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

**Gmail et Agenda** : créez un identifiant OAuth « Application de bureau » dans Google Cloud Console (APIs Gmail et Calendar activées), enregistrez-le sous `serveur/google_client_secret.json`, puis lancez `python -m sophonie.connexion_google`.

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
