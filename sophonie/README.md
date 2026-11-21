# Sophonie · assistante personnelle sur votre téléphone

Sophonie est un agent IA (Claude) qui vit dans votre téléphone Android. Elle vous accompagne toute la journée : prière et briefing au réveil, point de midi, prière du soir et programme du lendemain, rappel du coucher. Elle vous alerte sur les messages importants, programme vos rappels, apprend vos goûts et répond à toutes vos questions, à l'écrit ou à la voix.

**Aucun serveur n'est nécessaire** : l'application appelle Claude directement. Le dossier `serveur/` est une option avancée (WhatsApp Business par l'API officielle de Meta, Gmail en direct), dont vous n'avez pas besoin pour commencer.

## Installer

1. **Clé API Claude** : sur [console.anthropic.com](https://console.anthropic.com), créez un compte, ajoutez du crédit (Billing), puis **API Keys > Create Key**. Copiez la clé `sk-ant-…`. Cette facturation est séparée de l'abonnement Claude.
2. **Télécharger l'application** : sur le téléphone, ouvrez [la page des versions](https://github.com/bdom5678/synergie-raw/releases) et, dans la version la plus récente (celle dont le nom se termine par `-signe`), téléchargez :
   - `Sophonie-essentiel.apk` : à essayer en premier. Elle est moins souvent bloquée par Play Protect, car elle ne lit pas directement les SMS ni le journal d'appels. Elle voit quand même les SMS et appels manqués par les notifications.
   - `Sophonie-complet.apk` : tous les accès, plus souvent bloquée.
3. **Installer** : ouvrez le fichier et autorisez l'installation depuis le navigateur. L'application n'est pas sur le Play Store, donc **Play Protect** peut afficher une alerte :
   - « Application non sécurisée » ou « Analyser l'application ? » : touchez **Plus de détails** puis **Installer quand même** (ou **Installer sans analyser**).
   - « Application bloquée pour protéger votre appareil », sans bouton pour continuer : ouvrez **Play Store** > votre photo de profil > **Play Protect** > ⚙️ (en haut à droite), désactivez **Analyser les applications avec Play Protect**, installez Sophonie, puis **réactivez** l'analyse. Si Play Protect signale ensuite Sophonie, choisissez **Ignorer** ou **Garder l'application**.
4. **Configurer** : ouvrez Sophonie, collez la clé API, indiquez votre prénom et vos contacts prioritaires, puis touchez **Enregistrer**.
5. **Donner les accès** : touchez **Autorisations** et acceptez tout (contacts, appels, SMS, agenda, micro, notifications). Activez ensuite Sophonie dans **Accès aux notifications**. Si Android affiche « Paramètre restreint », allez dans Paramètres > Applications > Sophonie > ⋮ (en haut à droite) > **Autoriser les paramètres restreints**, puis recommencez.
6. **Rester active** : touchez **Batterie** (accepter) et **Alarmes** (autoriser). Sur Samsung, Xiaomi, Tecno, Infinix et Itel, mettez aussi Sophonie en « Sans restriction » dans les réglages de batterie : sinon le téléphone l'endort et les rappels arrivent en retard.

Pour mettre à jour, téléchargez la nouvelle version et installez-la par-dessus : vos données sont conservées.

## Ce que Sophonie fait

| Domaine | Capacités |
|---|---|
| Messages | Lit les notifications WhatsApp, WhatsApp Business, SMS, Gmail/Outlook et appels manqués ; analyse chacune et vous alerte si c'est urgent, prioritaire ou suspect ; crée tout seule un rappel quand une action est à prévoir |
| SMS et appels | Lit la boîte SMS et le journal d'appels |
| Contacts | Cherche numéros et anniversaires |
| Agenda | Lit votre agenda (Google et autres comptes du téléphone) et ajoute des événements |
| Rappels | Programme des rappels ponctuels ou répétés, qui sonnent même sans internet |
| Actions | Prépare un message WhatsApp, WhatsApp Business ou SMS (vous appuyez sur Envoyer), ouvre le composeur, lance la navigation |
| Web | Cherche l'actualité, la météo, des adresses, des horaires, des conseils |
| Mémoire | Retient vos goûts, habitudes, rejets et objectifs, et s'en sert pour vous conseiller |
| Voix | Bouton 🎤 pour parler ; option pour qu'elle vous réponde à voix haute |

**Le rythme de la journée** (heure du téléphone) :

| Heure | Moment |
|---|---|
| 07:00 | ☀️ Verset et prière du matin, agenda, messages importants de la nuit, anniversaires, rappels |
| 12:45 | 🕛 Point de midi |
| 23:00 | 🙏 Prière du soir, messages restés sans réponse, programme de demain |
| 23:30 | 🌙 Il est l'heure de dormir |

## Limites et sécurité

- **Envoi de messages** : Sophonie ne peut pas envoyer un message WhatsApp à votre place. WhatsApp ne le permet à aucune application. Elle ouvre la conversation avec le texte prêt, et vous appuyez sur Envoyer. C'est aussi une protection.
- **Messages reçus** : ils sont traités comme des informations, jamais comme des ordres. Un message qui demande d'envoyer de l'argent, un code ou de transférer quelque chose est signalé comme suspect.
- **Vos données** restent sur le téléphone : mémoire, messages et goûts. Seul ce qui est nécessaire à chaque réponse est envoyé à Claude.
- **La clé API** est enregistrée sur le téléphone, dans l'espace privé de l'application. Si vous perdez le téléphone, supprimez la clé sur console.anthropic.com.
- **Coût** : chaque réponse, chaque moment de la journée et l'analyse de chaque message reçu consomment du crédit API. Si la facture monte trop, décochez « Analyser chaque message reçu » dans les réglages ⚙️. Fixez aussi une limite mensuelle sur console.anthropic.com (Limits).

## Pour les développeurs

- `android/` : application Kotlin (SDK officiel `com.anthropic:anthropic-java`). L'APK est compilé et publié automatiquement par `.github/workflows/sophonie-android.yml` à chaque modification.
- `serveur/` (option) : serveur Python FastAPI, avec webhook WhatsApp Business, Gmail et Google Agenda par OAuth. Installation : `deploiement/installer.sh`, gratuit sur Google Cloud e2-micro (région us-central1, us-east1 ou us-west1, disque persistant standard de 30 Go). Tests : `cd serveur && python -m pytest -q`.

> ⚠️ Ce dépôt est public. N'y mettez jamais de clé, de jeton ni de données personnelles.
