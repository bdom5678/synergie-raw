#!/usr/bin/env python3
"""Espace CBO Synergie Corporate : page publique (sans compte Claude) pour les CBO.

Chaque CBO choisit sa zone et son nom, saisit son code personnel et voit ses affaires,
ses commissions et sa place dans sa zone. Le simulateur de gains est ouvert à tous.

Les données propres à chaque CBO sont chiffrées (AES-256-GCM, clé PBKDF2-SHA256 dérivée
du code personnel). Les codes sont dérivés d'un secret (HMAC) : ils restent stables d'un jour
à l'autre et un nouveau CBO reçoit automatiquement le sien. Le secret n'est jamais écrit
dans la page ni dans le dépôt.

Usage :
  python3 build_espace_cbo.py --db DOSSIER_DB --template espace_cbo_template.html \
      --secret "SECRET" --out page.html [--codes codes.csv]
  --codes-doc codes.json : document à écrire dans la base de l'outil (collection codes, doc cbo), lu par l'onglet CBO participants
  --db : dossier contenant referrals/*.json, cbos/*.json, config/settings.json
"""
import argparse, base64, csv, datetime, glob, hashlib, hmac, json, os, secrets, sys
from zoneinfo import ZoneInfo
from cryptography.hazmat.primitives.ciphers.aead import AESGCM

ITER = 150_000
ALPHABET = 'ABCDEFGHJKMNPQRSTUVWXYZ23456789'  # sans I, L, O, 0, 1
ZONES = ['Kinshasa', 'Sud', 'Centre', 'Est']
TIER_HAUT = ['Kinshasa', 'Sud']
PRODUCTS = [
    ('multirisque', 'Incendie / MRP', 4), ('trc', 'Tous risques chantier', 4), ('flotte', 'Flotte automobile', 4),
    ('transport', 'Transport de marchandises', 4), ('corps', 'Assurance des Corps', 4), ('sante', 'Santé VITASUR', 3),
    ('grouplife', 'Group Life', 15), ('retraite', 'CAREC · retraite complémentaire', 1), ('ifc', 'IFC · fin de carrière', 1)]
EPARGNE = {'retraite', 'ifc'}


def fail(msg):
    print('ERREUR :', msg, file=sys.stderr)
    sys.exit(1)


def load_dir(path):
    out = []
    for f in sorted(glob.glob(os.path.join(path, '*.json'))):
        d = json.load(open(f, encoding='utf-8'))
        d = d.get('data', d) if isinstance(d.get('data'), dict) and 'id' in d else d
        d['_id'] = os.path.splitext(os.path.basename(f))[0]
        out.append(d)
    return out


def num(v):
    try:
        return float(v or 0)
    except (TypeError, ValueError):
        return 0.0


def code_for(secret, cbo_id):
    mac = hmac.new(secret.encode(), ('cbo:' + cbo_id).encode(), hashlib.sha256).digest()
    n = int.from_bytes(mac[:8], 'big')
    s = ''
    for _ in range(6):
        n, r = divmod(n, len(ALPHABET))
        s += ALPHABET[r]
    return s


def encrypt(code, cbo_id, obj):
    salt = secrets.token_bytes(16)
    key = hashlib.pbkdf2_hmac('sha256', (code + '|' + cbo_id).encode(), salt, ITER, 32)
    iv = secrets.token_bytes(12)
    ct = AESGCM(key).encrypt(iv, json.dumps(obj, ensure_ascii=False).encode(), None)
    b = lambda x: base64.b64encode(x).decode()
    return {'s': b(salt), 'i': b(iv), 'c': b(ct)}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--db', required=True)
    ap.add_argument('--template', required=True)
    ap.add_argument('--secret', required=True)
    ap.add_argument('--out', required=True)
    ap.add_argument('--codes', help='écrit la liste des codes (CSV) à transmettre aux CBO')
    ap.add_argument('--codes-doc', help="écrit le document JSON {codes, maj} à enregistrer dans la base de l'outil (codes/cbo)")
    a = ap.parse_args()
    if len(a.secret) < 16:
        fail('secret trop court')

    refs = load_dir(os.path.join(a.db, 'referrals'))
    cbos = load_dir(os.path.join(a.db, 'cbos'))
    sp = os.path.join(a.db, 'config', 'settings.json')
    if not refs or not cbos or not os.path.exists(sp):
        fail('base incomplète (referrals, cbos ou config/settings.json manquant)')
    settings = json.load(open(sp, encoding='utf-8'))
    settings = settings.get('data', settings) if 'rates' not in settings else settings
    rates = {p: num((settings.get('rates') or {}).get(p, r)) for p, r, in [(x[0], x[2]) for x in PRODUCTS]}
    rw = settings.get('rewards') or {}
    rewards_on = settings.get('recompenses') is True
    tiers = {'haut': [num(x) for x in (rw.get('haut') or [1500, 900, 600])],
             'bas': [num(x) for x in (rw.get('bas') or [650, 400, 250])]}

    znorm = lambda z: 'Kinshasa' if z == 'Ouest' else (z or '—')
    by_id = {c['_id']: c for c in cbos}
    key = lambda s: ''.join(ch for ch in (s or '').lower() if ch.isalnum())
    by_name = {key(f"{c.get('prenom','')}{c.get('nom','')}"): c for c in cbos}

    def cbo_of(r):
        return by_id.get(r.get('cboId')) or by_name.get(key(r.get('cbo')))

    rate = lambda p: rates.get(p, 0)
    enc_net = lambda r: num(r.get('primeEncaissee')) if r.get('statut') == 'Encaissé' else 0
    enc_tot = lambda r: num(r.get('primeTotaleEncaissee')) if r.get('statut') == 'Encaissé' else 0
    cash = lambda r: enc_tot(r) or enc_net(r)

    stats = {}
    for c in cbos:
        stats[c['_id']] = {'c': c, 'refs': []}
    for r in refs:
        c = cbo_of(r)
        if c:
            stats[c['_id']]['refs'].append(r)
    for s in stats.values():
        c = s['c']
        s['zone'] = znorm(c.get('zone'))
        s['retire'] = c.get('statut') == 'retire'
        s['nCash'] = sum(1 for r in s['refs'] if r.get('statut') == 'Encaissé')
        s['ca'] = sum(cash(r) for r in s['refs'])
        s['gen'] = sum(enc_net(r) * rate(r.get('produit')) / 100 for r in s['refs'])
        s['esp'] = sum(num(r.get('primeTotaleEstimee')) or num(r.get('primeEstimee')) for r in s['refs'])
        s['nom'] = f"{c.get('prenom','')} {c.get('nom','')}".strip()
        s['eligible'] = s['nCash'] > 0 and not s['retire']

    zone_info = {}
    for z in ZONES:
        lst = [s for s in stats.values() if s['zone'] == z and (s['refs'] or not s['retire'])]
        lst.sort(key=lambda s: (-s['ca'], -s['gen'], -s['nCash'], -s['esp'], -len(s['refs']), s['nom']))
        place = 0
        for s in lst:
            s['place'] = None
            if s['eligible'] and place < 3:
                place += 1
                s['place'] = place
        podium = [round(s['ca'], 2) for s in lst if s['place']]
        zone_info[z] = {'podium': podium, 'tier': 'haut' if z in TIER_HAUT else 'bas',
                        'nEligibles': sum(1 for s in lst if s['eligible']),
                        'nCbo': sum(1 for s in lst if not s['retire'])}
        for i, s in enumerate(lst):
            s['rang'] = i + 1

    public_cbos, payloads, code_rows = [], {}, []
    for cid, s in sorted(stats.items(), key=lambda kv: (kv[1]['zone'], kv[1]['nom'])):
        if s['retire'] or s['zone'] not in ZONES:
            continue
        c = s['c']
        aff = []
        for r in sorted(s['refs'], key=lambda r: r.get('dateRef') or '', reverse=True):
            p = r.get('produit')
            st = r.get('statut') or 'Référencé'
            net_esp = num(r.get('primeEstimee'))
            net_enc = enc_net(r)
            aff.append({'d': r.get('dateRef') or '', 'cl': r.get('client') or '', 'p': p, 'st': st,
                        'ne': round(net_esp, 2), 'te': round(num(r.get('primeTotaleEstimee')), 2),
                        'nn': round(net_enc, 2), 'tn': round(enc_tot(r), 2),
                        'dc': r.get('dateCotation') or ''})
        payload = {'id': cid, 'nom': s['nom'], 'agence': c.get('agence') or '', 'zone': s['zone'],
                   'ca': round(s['ca'], 2), 'place': s.get('place'), 'rang': s.get('rang'), 'eligible': s['eligible'],
                   'affaires': aff}
        code = code_for(a.secret, cid)
        payloads[cid] = encrypt(code, cid, payload)
        public_cbos.append({'id': cid, 'nom': s['nom'], 'zone': s['zone'], 'agence': c.get('agence') or ''})
        code_rows.append({'zone': s['zone'], 'agence': c.get('agence') or '', 'cbo': s['nom'],
                          'email': c.get('email') or '', 'code': code})

    now = datetime.datetime.now(ZoneInfo('Africa/Kinshasa'))
    data = {
        'maj': now.strftime('%d/%m/%Y à %H:%M'),
        'fin': settings.get('fin') or '2026-12-31',
        'debut': settings.get('debut') or '2026-10-19',
        'iter': ITER,
        'produits': [{'id': p, 'label': l, 'taux': rate(p), 'epargne': p in EPARGNE} for p, l, _ in PRODUCTS],
        'recompenses': rewards_on,
        'tiers': tiers,
        'zones': zone_info,
        'cbos': public_cbos,
        'coffres': payloads,
    }
    tpl = open(a.template, encoding='utf-8').read()
    if '/*__DATA__*/null' not in tpl:
        fail('gabarit sans marqueur de données')
    html = tpl.replace('/*__DATA__*/null', json.dumps(data, ensure_ascii=False, separators=(',', ':')).replace('</', '<\\/'))
    open(a.out, 'w', encoding='utf-8').write(html)
    if a.codes:
        with open(a.codes, 'w', encoding='utf-8-sig', newline='') as f:
            w = csv.DictWriter(f, fieldnames=['zone', 'agence', 'cbo', 'email', 'code'], delimiter=';')
            w.writeheader()
            w.writerows(code_rows)
    if a.codes_doc:
        json.dump({'codes': {r_id: code_for(a.secret, r_id) for r_id in [c['id'] for c in public_cbos]},
                   'maj': now.isoformat(timespec='minutes'), 'source': 'build_espace_cbo.py'},
                  open(a.codes_doc, 'w', encoding='utf-8'), ensure_ascii=False)
    print(json.dumps({'ok': True, 'cbo': len(public_cbos), 'referencements': len(refs),
                      'rattaches': sum(len(s['refs']) for s in stats.values()), 'maj': data['maj']}))


if __name__ == '__main__':
    main()
