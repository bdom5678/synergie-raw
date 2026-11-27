#!/usr/bin/env bash
# Installe Sophonie sur un serveur Ubuntu 24.04 (VPS) avec HTTPS automatique.
#
# Usage (en root, sur le serveur) :
#   curl -fsSL https://raw.githubusercontent.com/bdom5678/synergie-raw/BRANCHE/sophonie/deploiement/installer.sh -o installer.sh
#   sudo bash installer.sh                 # adresse https://<ip>.sslip.io (sans nom de domaine)
#   sudo bash installer.sh mon.domaine.com # avec votre propre nom de domaine
#
# Relancer le script met Sophonie à jour sans toucher à profil.json, .env ni à la base.
set -euo pipefail

DEPOT="${SOPHONIE_DEPOT:-https://github.com/bdom5678/synergie-raw.git}"
BRANCHE="${SOPHONIE_BRANCHE:-ccr-2019a868-qtmewc}"
DOSSIER=/opt/sophonie
SERVEUR="$DOSSIER/sophonie/serveur"

[ "$(id -u)" -eq 0 ] || { echo "Lancez ce script avec sudo."; exit 1; }

echo "==> Mémoire d'appoint (petites machines gratuites à 1 Go)"
if [ "$(awk '/MemTotal/ {print $2}' /proc/meminfo)" -lt 1600000 ] && ! swapon --show | grep -q .; then
  fallocate -l 2G /swapfile && chmod 600 /swapfile && mkswap -q /swapfile && swapon /swapfile
  grep -q '^/swapfile' /etc/fstab || echo '/swapfile none swap sw 0 0' >> /etc/fstab
fi

echo "==> Paquets système"
apt-get update -qq
apt-get install -y -qq git python3-venv python3-pip caddy ufw curl >/dev/null

echo "==> Utilisateur dédié"
id sophonie >/dev/null 2>&1 || useradd --system --create-home --home-dir /home/sophonie --shell /bin/bash sophonie

echo "==> Code"
if [ -d "$DOSSIER/.git" ]; then
  sudo -u sophonie git -C "$DOSSIER" fetch -q origin "$BRANCHE"
  sudo -u sophonie git -C "$DOSSIER" checkout -q -B "$BRANCHE" FETCH_HEAD
else
  mkdir -p "$DOSSIER" && chown sophonie: "$DOSSIER"
  sudo -u sophonie git clone -q --depth 20 --branch "$BRANCHE" "$DEPOT" "$DOSSIER"
fi

echo "==> Environnement Python"
sudo -u sophonie python3 -m venv "$SERVEUR/.venv"
sudo -u sophonie "$SERVEUR/.venv/bin/pip" install -q --upgrade pip
sudo -u sophonie "$SERVEUR/.venv/bin/pip" install -q -r "$SERVEUR/requirements.txt"

echo "==> Profil et secrets"
[ -f "$SERVEUR/profil.json" ] || sudo -u sophonie cp "$SERVEUR/profil.exemple.json" "$SERVEUR/profil.json"
if [ ! -f "$SERVEUR/.env" ]; then
  JETON=$(python3 -c "import secrets; print(secrets.token_urlsafe(32))")
  read -r -s -p "Clé API Claude (sk-ant-..., saisie masquée) : " CLE; echo
  install -o sophonie -m 600 /dev/null "$SERVEUR/.env"
  cat > "$SERVEUR/.env" <<ENV
ANTHROPIC_API_KEY=$CLE
SOPHONIE_JETON=$JETON
SOPHONIE_HOTE=127.0.0.1
# WhatsApp Business (optionnel) :
# WHATSAPP_TOKEN=
# WHATSAPP_PHONE_ID=
# WHATSAPP_VERIFY_TOKEN=
# WHATSAPP_APP_SECRET=
ENV
  NOUVEAU_JETON=1
fi
chmod 600 "$SERVEUR/.env"

echo "==> Service systemd"
cat > /etc/systemd/system/sophonie.service <<UNIT
[Unit]
Description=Sophonie, assistante personnelle
After=network-online.target
Wants=network-online.target

[Service]
User=sophonie
WorkingDirectory=$SERVEUR
Environment=SOPHONIE_HOTE=127.0.0.1
ExecStart=$SERVEUR/.venv/bin/python -m sophonie
Restart=always
RestartSec=5
NoNewPrivileges=true
ProtectSystem=strict
ReadWritePaths=$SERVEUR
PrivateTmp=true

[Install]
WantedBy=multi-user.target
UNIT
systemctl daemon-reload
systemctl enable -q sophonie
systemctl restart sophonie

echo "==> HTTPS (Caddy)"
DOMAINE="${1:-}"
if [ -z "$DOMAINE" ]; then
  IP=$(curl -fsS4 https://api.ipify.org)
  DOMAINE="${IP//./-}.sslip.io"
fi
cat > /etc/caddy/Caddyfile <<CADDY
$DOMAINE {
    encode gzip
    reverse_proxy 127.0.0.1:8765
}
CADDY
systemctl reload caddy || systemctl restart caddy

echo "==> Pare-feu"
ufw allow OpenSSH >/dev/null
ufw allow 80/tcp >/dev/null
ufw allow 443/tcp >/dev/null
ufw --force enable >/dev/null

sleep 3
echo
echo "Sophonie est installée."
echo "  Adresse à saisir dans l'application : https://$DOMAINE"
if [ "${NOUVEAU_JETON:-0}" = 1 ]; then
  echo "  Jeton à saisir dans l'application  : $JETON"
else
  echo "  Jeton : voir SOPHONIE_JETON dans $SERVEUR/.env"
fi
echo "  Vérification : curl https://$DOMAINE/sante"
echo "  Journal       : journalctl -u sophonie -f"
