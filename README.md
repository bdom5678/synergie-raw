# Synergie Corporate · vue équipe

Copie en lecture seule de l'outil de suivi de la campagne Synergie Corporate (Rawbank × Rawsur), publiée sur GitHub Pages.

- `index.html` : la page. Son contenu est chiffré (AES-256-GCM, clé dérivée du mot de passe par PBKDF2-SHA256, 600 000 itérations) ; sans le mot de passe, seul l'écran de connexion est lisible.
- `outils/build_vue_equipe.py` : le générateur. Il relit l'outil et sa base, retire les onglets d'administration et les notes internes, puis chiffre la page. Le mot de passe n'est jamais stocké dans ce dépôt.

Mise à jour automatique chaque jour ouvré vers 7 h (heure de Kinshasa).
