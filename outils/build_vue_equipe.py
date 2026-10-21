#!/usr/bin/env python3
"""Vue équipe Synergie Corporate — copie en lecture seule de l'outil de suivi, chiffrée par mot de passe.

Usage :
  python3 build_vue_equipe.py --tool index.html --db DOSSIER_DB --password "MOT-DE-PASSE" --out site/index.html

  --tool : page de l'outil (Artifact read, path "index.html")
  --db   : dossier contenant referrals/*.json, cbos/*.json, config/settings.json (ArtifactData list avec out_dir)
Le script échoue (code 1) si la structure de l'outil a changé au point que la vue ne peut plus être construite.
"""
import argparse, base64, datetime, glob, hashlib, json, os, re, secrets, sys
from zoneinfo import ZoneInfo
from cryptography.hazmat.primitives.ciphers.aead import AESGCM

TEAM_TABS = ['dash', 'refs', 'zones', 'cbo', 'sim']
ITER = 600_000
REF_KEEP = ['dateRef', 'client', 'secteur', 'produit', 'cboId', 'cbo', 'agence', 'zone', 'statut', 'primeEstimee',
            'dateCotation', 'primeSignee', 'primeEncaissee', 'fraisGestion', 'cotisation', 'periodicite', 'createdAt']
CBO_DROP = {'updatedBy', 'notes', 'createdBy'}


def fail(msg):
    print('ERREUR :', msg, file=sys.stderr)
    sys.exit(1)


def load_dir(path):
    out = []
    for f in sorted(glob.glob(os.path.join(path, '*.json'))):
        d = json.load(open(f, encoding='utf-8'))
        d['_id'] = os.path.splitext(os.path.basename(f))[0]
        out.append(d)
    return out


def build_inner(tool, data):
    h = tool
    n0 = len(h)
    # 1. onglets réservés aux administrateurs
    h, n = re.subn(r'\s*<button[^>]*data-tab="(?:param|import|team)"[^>]*>.*?</button>', '', h, flags=re.S)
    if n < 3: fail(f'onglets à masquer introuvables ({n}/3)')
    # 2. bibliothèque d'import inutile
    h = re.sub(r'<script src="[^"]*xlsx[^"]*"></script>', '', h)
    # 3. titre
    h, n = re.subn(r'<title>.*?</title>', '<title>Synergie Corporate · Vue équipe</title>', h, count=1, flags=re.S)
    if not n: fail('balise <title> introuvable')
    # 4. script principal : bornes
    m = re.search(r'<script>(?!window\.__VUE_DATA)', h)
    if not m: fail('script principal introuvable')
    s_start = m.end()
    s_end = h.rfind('</script>')
    script = h[s_start:s_end]
    # 5. fusion des paramètres telle que l'outil la fait
    mm = re.search(r"db\.doc\('config/settings'\)\.onSnapshot\(s=>\{const d=s\.exists\?s\.data\(\):\{\};(.*?);render\(\)", script, flags=re.S)
    if not mm: fail('lecture des paramètres de l’outil introuvable')
    merge = mm.group(1)
    # 6. démarrage remplacé par les données figées
    k = script.find('/* ---------- démarrage ---------- */')
    if k < 0: fail('bloc de démarrage introuvable')
    boot = ("/* ---------- vue équipe : données figées, lecture seule ---------- */\n"
            "(function(){const D=window.__VUE_DATA;const d=D.settings||{};" + merge + ";\n"
            "cbos=D.cbos;refs=D.refs;meInfo=null;applyRole();renderSim(true);\n"
            "const b=$('banner');b.hidden=false;b.innerHTML='<b>Vue équipe en lecture seule</b> · données arrêtées au '+esc(D.stamp)+'. Mise à jour chaque jour ouvré vers 7 h.';\n"
            "})();\n")
    script = script[:k] + boot
    # 7. onglets autorisés
    script, n = re.subn(r'function showTab\(t\)\{', "function showTab(t){if(!TEAM_TABS.includes(t))t='dash';", script, count=1)
    if not n: fail('fonction showTab introuvable')
    script = f"const TEAM_TABS={json.dumps(TEAM_TABS)};\n" + script
    # 8. textes qui renvoient à des onglets ou des droits absents
    script = script.replace(" Seuls les administrateurs ajoutent, réaffectent ou retirent un CBO.", "")
    h = h[:s_start] + script + h[s_end:]
    h = h.replace("de l'onglet Paramètres", "de la campagne").replace("de l’onglet Paramètres", "de la campagne")
    # 9. données + styles de lecture seule
    payload = json.dumps(data, ensure_ascii=False, separators=(',', ':')).replace('</', '<\\/')
    css = ("<style>#btn-export,#btn-new,#c-new,[data-edit],[data-cbo],[data-admin]{display:none!important}"
           "#c-moves th:last-child,#c-moves td:last-child{display:none}</style>")
    h = h.replace('<script>', css + f'<script>window.__VUE_DATA={payload};</script><script>', 1)
    if len(h) < n0 * 0.5: fail('page produite anormalement courte')
    return h


GATE = r"""<!doctype html><html lang="fr"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover">
<meta name="robots" content="noindex,nofollow"><title>Synergie Corporate · Vue équipe</title>
<link rel="preconnect" href="https://fonts.googleapis.com"><link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Oswald:wght@500;600&family=Public+Sans:wght@400;600;700&display=swap">
<style>
:root{--bg:#F3F1EB;--surface:#FCFBF8;--ink:#14213D;--muted:#5A6477;--line:#DDD8CC;--navy:#14213D;--gold:#C9A227;--gold-ink:#7A600E;--bad:#B03A2E;color-scheme:light}
@media (prefers-color-scheme:dark){:root{--bg:#0F1626;--surface:#172036;--ink:#E9ECF3;--muted:#A3AEC2;--line:#2B3651;--navy:#0A1020;--gold:#D8B444;--gold-ink:#E3C463;--bad:#EE8577;color-scheme:dark}}
*{box-sizing:border-box}html,body{margin:0;height:100%}
body{background:var(--bg);color:var(--ink);font:15px/1.5 'Public Sans','Segoe UI',Arial,sans-serif;display:flex;flex-direction:column}
header{background:var(--navy);color:#F3F1EB;border-bottom:4px solid var(--gold);padding:22px max(16px,4vw)}
.eyebrow{font-size:11px;letter-spacing:.14em;text-transform:uppercase;color:var(--gold);font-weight:700}
h1{font-family:Oswald,'Arial Narrow',Arial,sans-serif;font-weight:600;text-transform:uppercase;letter-spacing:.02em;font-size:clamp(26px,5vw,36px);margin:4px 0 0}
main{flex:1;display:flex;align-items:flex-start;justify-content:center;padding:max(24px,6vh) 16px}
form{background:var(--surface);border:1px solid var(--line);border-top:4px solid var(--gold);border-radius:10px;padding:24px;width:min(420px,100%);display:flex;flex-direction:column;gap:14px}
h2{font-family:Oswald,'Arial Narrow',Arial,sans-serif;font-weight:500;margin:0;font-size:22px}
p{margin:0;color:var(--muted);font-size:14px}
label{display:flex;flex-direction:column;gap:6px;font-size:13px;font-weight:600;color:var(--muted)}
input[type=password]{font:inherit;font-size:16px;letter-spacing:.06em;color:var(--ink);background:var(--bg);border:1px solid var(--line);border-radius:6px;padding:10px 12px}
input:focus-visible,button:focus-visible{outline:2px solid var(--gold);outline-offset:2px}
.rem{flex-direction:row;align-items:center;gap:8px;font-weight:400}
button{font:inherit;font-weight:700;cursor:pointer;border:0;border-radius:6px;padding:11px 16px;background:var(--gold);color:#14213D}
button[disabled]{opacity:.6;cursor:wait}
.err{color:var(--bad);font-size:13px;min-height:1em}
footer{padding:16px max(16px,4vw);font-size:12px;color:var(--muted)}
</style></head><body>
<header><div class="eyebrow">Rawbank × Rawsur · Bancassurance &amp; Partnerships</div><h1>Synergie Corporate</h1></header>
<main><form id="f" autocomplete="off">
<h2>Vue équipe</h2>
<p>Suivi de la campagne en lecture seule, mis à jour chaque jour ouvré vers 7 h. Saisissez le mot de passe communiqué par le responsable de la campagne.</p>
<label>Mot de passe<input type="password" id="pw" required autocomplete="off" autocapitalize="characters" spellcheck="false" autofocus placeholder="XXXX-XXXX-XXXX-XXXX"></label>
<label class="rem"><input type="checkbox" id="show"> Afficher le mot de passe</label>
<label class="rem"><input type="checkbox" id="rem"> Se souvenir sur cet appareil</label>
<button id="go" type="submit">Ouvrir le suivi</button>
<div class="err" id="err" role="alert"></div>
</form></main>
<footer>Accès réservé aux équipes RAWSUR et Rawbank engagées dans la campagne. Données confidentielles : ne pas transférer.</footer>
<script>
const P="__PAYLOAD__",IT=__ITER__;
const b64=s=>Uint8Array.from(atob(s),c=>c.charCodeAt(0));
const cands=s=>{const t=String(s).normalize('NFKC').toUpperCase().replace(/[^A-Z0-9]/g,'');const out=[s,s.trim()];if(t.length===16)out.push(t.match(/.{4}/g).join('-'));out.push(t);return [...new Set(out.filter(Boolean))]};
async function openAny(s){for(const c of cands(s)){try{await open(c);return c}catch(_){}}throw new Error('bad')}
async function open(pw){
  const raw=b64(P),salt=raw.slice(0,16),iv=raw.slice(16,28),ct=raw.slice(28);
  const base=await crypto.subtle.importKey('raw',new TextEncoder().encode(pw),'PBKDF2',false,['deriveKey']);
  const key=await crypto.subtle.deriveKey({name:'PBKDF2',salt,iterations:IT,hash:'SHA-256'},base,{name:'AES-GCM',length:256},false,['decrypt']);
  const html=new TextDecoder().decode(await crypto.subtle.decrypt({name:'AES-GCM',iv},key,ct));
  document.open();document.write(html);document.close();
}
const K='synergie-vue-pw';
document.getElementById('show').addEventListener('change',e=>{document.getElementById('pw').type=e.target.checked?'text':'password'});
document.getElementById('f').addEventListener('submit',async e=>{
  e.preventDefault();const pw=document.getElementById('pw').value,btn=document.getElementById('go'),err=document.getElementById('err');
  if(!window.crypto||!crypto.subtle){err.textContent="Ce navigateur ne peut pas ouvrir la page. Utilisez une version récente de Chrome, Edge, Firefox ou Safari.";return}
  btn.disabled=true;btn.textContent='Ouverture…';err.textContent='';
  try{const r=document.getElementById('rem').checked;try{localStorage.removeItem(K)}catch(_){}const ok=await openAny(pw);if(r){try{localStorage.setItem(K,ok)}catch(_){}}}
  catch(_){btn.disabled=false;btn.textContent='Ouvrir le suivi';err.textContent='Mot de passe incorrect. Il compte 16 caractères (lettres et chiffres), en 4 groupes de 4.'}
});
(async()=>{let pw=null;try{pw=localStorage.getItem(K)}catch(_){}if(pw&&window.crypto&&crypto.subtle){try{await open(pw)}catch(_){try{localStorage.removeItem(K)}catch(__){}}}})();
</script></body></html>"""


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--tool', required=True)
    ap.add_argument('--db', required=True)
    ap.add_argument('--password', required=True)
    ap.add_argument('--out', required=True)
    a = ap.parse_args()

    tool = open(a.tool, encoding='utf-8').read()
    settings_path = os.path.join(a.db, 'config', 'settings.json')
    if not os.path.exists(settings_path): fail('config/settings.json manquant')
    settings = json.load(open(settings_path, encoding='utf-8'))
    refs = [{k: r.get(k) for k in REF_KEEP if k in r} | {'_id': r['_id']} for r in load_dir(os.path.join(a.db, 'referrals'))]
    cbos = []
    for c in load_dir(os.path.join(a.db, 'cbos')):
        c = {k: v for k, v in c.items() if k not in CBO_DROP}
        if isinstance(c.get('historique'), list):
            c['historique'] = [{k: v for k, v in h.items() if k != 'par'} for h in c['historique']]
        cbos.append(c)
    if not cbos: fail('aucun CBO lu')
    now = datetime.datetime.now(ZoneInfo('Africa/Kinshasa'))
    stamp = now.strftime('%d/%m/%Y à %H:%M')
    inner = build_inner(tool, {'settings': settings, 'cbos': cbos, 'refs': refs, 'stamp': stamp})

    salt, iv = secrets.token_bytes(16), secrets.token_bytes(12)
    key = hashlib.pbkdf2_hmac('sha256', a.password.encode('utf-8'), salt, ITER, 32)
    blob = base64.b64encode(salt + iv + AESGCM(key).encrypt(iv, inner.encode('utf-8'), None)).decode()
    os.makedirs(os.path.dirname(os.path.abspath(a.out)), exist_ok=True)
    open(a.out, 'w', encoding='utf-8').write(GATE.replace('__PAYLOAD__', blob).replace('__ITER__', str(ITER)))
    print(json.dumps({'ok': True, 'out': a.out, 'stamp': stamp, 'referencements': len(refs), 'cbo': len(cbos), 'taille_ko': round(os.path.getsize(a.out) / 1024)}))


if __name__ == '__main__':
    main()
