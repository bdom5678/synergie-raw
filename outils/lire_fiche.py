#!/usr/bin/env python3
"""Lit les fiches de référencement Synergie Corporate jointes à un mail Gmail.

Usage : python3 -I lire_fiche.py <message_raw.json> <dossier_sortie>
  <message_raw.json> : résultat de mcp__Gmail__get_message avec messageFormat RAW (objet JSON avec le champ « raw »).
Écrit les pièces jointes dans <dossier_sortie> et affiche en JSON, pour chaque PDF, les champs du formulaire
et une version normalisée (produits cochés, accord du client, contact, etc.).
"""
import base64, email, json, os, re, sys
from email import policy

PRODUITS = {'p_mrp': 'multirisque', 'p_trc': 'trc', 'p_flotte': 'flotte', 'p_transport': 'transport', 'p_corps': 'corps',
            'p_sante': 'sante', 'p_grouplife': 'grouplife', 'p_carec': 'retraite', 'p_ifc': 'ifc'}
EMAIL_RE = re.compile(r'[\w.+\'-]+@[\w-]+(\.[\w-]+)+')


def coche(v):
    return str(v or '').strip().lower() in ('/yes', 'yes', '/on', 'on', 'true', '1', '/oui', 'oui')


def norm(f):
    g = lambda k: (f.get(k) or '').strip()
    mails = [m.group(0) for m in EMAIL_RE.finditer(' '.join([g('cli_mail'), g('cli_tel')]))]
    tels = [x for x in (g('cli_tel'), g('cli_mail')) if x and not EMAIL_RE.search(x) and re.search(r'\d{6,}', re.sub(r'\D', '', x))]
    return {
        'dateRef': g('date_ref'), 'cboNom': g('cbo_nom'), 'cboAgence': g('cbo_agence'), 'cboZone': g('cbo_zone'), 'cboMail': g('cbo_mail'),
        'client': g('cli_raison'), 'rccm': g('cli_rccm'), 'secteur': g('cli_secteur'), 'effectif': g('cli_effectif'),
        'contact': g('cli_contact'), 'fonction': g('cli_fonction'), 'contactMail': mails[0] if mails else '',
        'contactTel': tels[0] if tels else '', 'produits': [p for k, p in PRODUITS.items() if coche(f.get(k))],
        'besoin': g('besoin_signal'), 'assureurActuel': g('ass_actuel'), 'echeance': g('ass_echeance'),
        'primeEstimeeTexte': g('prime_est'), 'pieces': g('pieces'), 'accordContact': coche(f.get('accord_client')),
        'anomalies': ([] if mails or not g('cli_mail') else ['le champ e-mail du client ne contient pas d’adresse valide'])
                     + ([] if any(coche(f.get(k)) for k in PRODUITS) else ['aucun produit coché']),
    }


def main():
    src, outdir = sys.argv[1], sys.argv[2]
    d = json.load(open(src, encoding='utf-8'))
    raw = d['raw'] if isinstance(d, dict) else d
    raw += '=' * (-len(raw) % 4)
    msg = email.message_from_bytes(base64.urlsafe_b64decode(raw), policy=policy.default)
    os.makedirs(outdir, exist_ok=True)
    from pypdf import PdfReader
    res = []
    for part in msg.iter_attachments():
        fn = os.path.basename(part.get_filename() or 'piece-jointe')
        data = part.get_payload(decode=True) or b''
        p = os.path.join(outdir, fn)
        open(p, 'wb').write(data)
        item = {'fichier': fn, 'chemin': p, 'taille': len(data)}
        if fn.lower().endswith('.pdf'):
            try:
                r = PdfReader(p)
                f = r.get_fields() or {}
                champs = {k: (None if (v.get('/V') if isinstance(v, dict) else v) is None else str(v.get('/V') if isinstance(v, dict) else v)) for k, v in f.items()}
                item['estFiche'] = 'cli_raison' in champs
                item['champs'] = champs
                if item['estFiche']:
                    item['fiche'] = norm(champs)
                else:
                    item['texte'] = '\n'.join((pg.extract_text() or '') for pg in r.pages[:3])[:4000]
            except Exception as e:
                item['erreur'] = f'PDF illisible : {e}'
        res.append(item)
    print(json.dumps({'messageId': d.get('id') if isinstance(d, dict) else None, 'threadId': d.get('threadId') if isinstance(d, dict) else None,
                      'objet': msg.get('subject'), 'de': msg.get('from'), 'date': msg.get('date'), 'pieces': res}, ensure_ascii=False, indent=1))


if __name__ == '__main__':
    main()
