#!/usr/bin/env node
/* Présentation personnalisée pour un client référencé par un CBO (campagne Synergie Corporate).
   Usage : node presentation_client.js entree.json sortie.pptx
   entree.json : { client, contact, fonction, secteur, effectif, produits: ["flotte", ...], besoin, assureurActuel,
                   echeance, cboNom, cboMail, cboAgence, date, offre?, renouvellement? }   (dates au format JJ/MM/AAAA ;
                   offre = date d'envoi de l'offre écrite si elle existe déjà, renouvellement = true pour un renouvellement)
   Contenu des produits : livret de référencement Synergie Corporate, édition octobre 2026. */
const fs = require('fs');
const path = require('path');
const pptxgen = require('pptxgenjs');
const { applyTheme } = require(path.join(__dirname, 'apply_theme.js'));

const THEME = {
  name: 'RAWSUR Synergie',
  headFontFace: 'Arial',
  bodyFontFace: 'Calibri',
  colors: {
    dk1: '1B2433', lt1: 'FFFFFF', dk2: '0E3A75', lt2: 'EEF3FA',
    accent1: '0E3A75', accent2: '2F6FBF', accent3: 'E3B23C', accent4: '5B6B82', accent5: 'D6E2F3', accent6: '1F7A5A',
    hlink: '2F6FBF', folHlink: '5B6B82'
  }
};

const P = {
  multirisque: { label: 'Incendie & Multirisque professionnelle', cie: 'RAWSUR SA · non-vie',
    desc: "Protège les locaux, le matériel et les stocks de l'entreprise, et sa responsabilité, contre les événements qui peuvent arrêter l'activité du jour au lendemain.",
    benefice: "Un incendie ou un dégât des eaux peut arrêter votre activité du jour au lendemain. La MRP protège vos locaux, votre matériel et vos stocks.",
    couvert: ['Incendie, implosions, explosions et chute de la foudre', 'Dégâts des eaux', 'Recours des voisins et des tiers', 'Tempêtes, ouragans, cyclones', "Chute d'un aéronef", 'Choc de véhicule terrestre identifié', 'Frais de déblais et de démolition', 'Autres frais sur justificatif'],
    pieces: ['Formulaire ou document de cotation rempli', "Informations légales de l'entreprise", "Photos du site, de l'objet ou du bâtiment à assurer"] },
  trc: { label: 'Tous risques chantier', cie: 'RAWSUR SA · non-vie',
    desc: "Couvre l'ouvrage en construction, les matériaux, le matériel et les engins de chantier, ainsi que la responsabilité civile envers les tiers, jusqu'à la fin de la période d'entretien.",
    benefice: "Tant que l'ouvrage n'est pas livré, un effondrement ou un incendie peut compromettre tout le projet. Le TRC sécurise votre chantier.",
    couvert: ["Dommages à l'ouvrage pendant les travaux", "Période d'entretien après la réception", 'Responsabilité civile envers les tiers', "Matériaux et équipements incorporés à l'ouvrage", 'Matériel et engins de chantier'],
    pieces: ['Contrat de marché ou devis, avec le montant ventilé des travaux', "Description de l'ouvrage et plans", "Planning : début, fin et durée de la période d'entretien", 'Localisation du chantier et nature du sol', "Intervenants : maître d'ouvrage, entrepreneur, sous-traitants", 'Matériel et engins à assurer, avec leur valeur'] },
  flotte: { label: 'Flotte automobile', cie: 'RAWSUR SA · non-vie',
    desc: "Assure en un seul contrat tous les véhicules de l'entreprise : la responsabilité civile obligatoire et, au choix, les dommages, le vol, l'incendie et les personnes transportées.",
    benefice: 'Tout votre parc assuré au même endroit, en une seule police, avec un seul interlocuteur.',
    couvert: ['Responsabilité civile obligatoire', 'Recours des tiers incendie', 'Dommages au véhicule', 'Incendie et explosion', 'Vol du véhicule', 'Bris de glaces', 'Défense et recours', 'Individuelle personnes transportées', 'Assistance 24 h/24, 7 j/7', 'Aide au constat en cas d’accident'],
    pieces: ['Liste du parc : marque, modèle, année, immatriculation, n° de châssis', 'Usage de chaque véhicule et nombre de places', 'Valeur à neuf ou valeur vénale de chaque véhicule', 'Copies de la carte rose et du volet jaune', 'Garanties souhaitées par véhicule (RC seule ou tous risques)', 'Relevé de sinistres des 3 dernières années', "Police en cours : assureur et date d'échéance"] },
  transport: { label: 'Transport de marchandises', cie: 'RAWSUR SA · non-vie',
    desc: "Couvre les marchandises importées ou exportées pendant leur acheminement, par mer, fleuve, lac, route ou air, du départ jusqu'à la livraison.",
    benefice: "Si votre marchandise arrive endommagée, c'est l'assurance qui rembourse, pas votre trésorerie.",
    couvert: ['Dommages matériels pendant le transport', 'Pertes de poids ou de quantité', 'Transport maritime, fluvial et lacustre', 'Transport routier et aérien'],
    pieces: ['Nature des marchandises et conditionnement', 'Valeur : facture proforma ou commerciale', 'Titre de transport : BL, Airway bill…', 'Trajet : départ, arrivée, transbordements', 'Mode de transport et, si connu, transporteur', 'Incoterm de la vente (FOB, CIF, CFR…)', 'Volume annuel prévu, pour une police à l’abonnement', 'Sinistres passés sur les expéditions'] },
  corps: { label: 'Assurance des Corps', cie: 'RAWSUR SA · non-vie',
    desc: 'Assure les appareils navigants eux-mêmes (avions, hélicoptères, bateaux, barges, pousseurs) contre les dommages et la perte, avec la responsabilité civile liée à leur exploitation.',
    benefice: 'Votre appareil est souvent votre actif le plus cher : une seule perte ne doit pas effacer cet actif.',
    couvert: ["Dommages au corps de l'appareil", 'Perte totale', 'Responsabilité civile envers les tiers', "Passagers, selon l'usage déclaré"],
    pieces: ["Identification de l'appareil : type, modèle, immatriculation, n° de série, année", 'Valeur agréée à assurer', 'Usage : commercial, privé, fret, travail aérien ou fluvial', "Zone d'exploitation ou de navigation", 'Certificats de navigabilité et, en aviation, expérience des pilotes', 'Sinistres passés et assureur actuel'] },
  sante: { label: 'Santé collective VITASUR', cie: 'RAWSUR SA · non-vie',
    desc: "Offre au personnel un accès aux soins sans avance de frais dans un réseau médical national, tout en donnant à l'employeur la maîtrise de son budget santé.",
    benefice: 'Vos agents gagnent une vraie couverture santé, et vous remplacez des factures imprévisibles par une prime connue et pilotée.',
    couvert: ['Carte personnalisée par assuré', 'Tiers payant, sans avance de frais', 'Réseau médical national', 'Hospitalisation et consultations', 'Pharmacie, maternité, optique, dentaire', 'Évacuation sanitaire (formule Platinum)', "Reporting des dépenses à l'employeur", 'Cinq formules : plafonds de 5 000 à 30 000 $'],
    pieces: ['Liste des bénéficiaires : nom, date de naissance, sexe, catégorie', 'Ayants droit à couvrir : conjoints, enfants', 'Formule souhaitée (Basique à Platinum)', 'Localisation des sites et des agents', 'Historique de consommation médicale, si contrat existant', "Assureur actuel et date d'échéance"] },
  grouplife: { label: 'Group Life', cie: 'RAWSUR Life SA · vie',
    desc: "Verse un capital à la famille d'un employé en cas de décès, de perte totale d'autonomie ou d'invalidité, calculé sur son salaire.",
    benefice: "Pour une prime calculée sur la masse salariale, chaque famille est protégée et l'entreprise n'a plus à improviser une aide en cas de drame.",
    couvert: ['Décès toutes causes', "Perte totale et irréversible d'autonomie", 'Invalidité permanente partielle', 'Capital en % ou multiple du salaire annuel'],
    pieces: ['Liste du personnel : nom, date de naissance, sexe, catégorie', 'Salaire annuel brut par employé (ou masse salariale par catégorie)', 'Capitaux souhaités (ex. 100 % du salaire en décès)', 'Garanties optionnelles souhaitées', 'Assureur actuel, échéance et sinistres passés'] },
  retraite: { label: 'CAREC · Retraite complémentaire', cie: 'RAWSUR Life SA · épargne',
    desc: "Constitue une épargne retraite collective pour les salariés, alimentée par l'employeur et, s'il le souhaite, par les salariés, versée au départ à la retraite.",
    benefice: "Un avantage social qui fidélise vos meilleurs collaborateurs, financé à votre rythme et géré par le leader de l'assurance vie en RDC.",
    couvert: ['Épargne collective gérée par RAWSUR Life', 'Cotisations employeur, et salariés en option', 'Capital ou rente au départ à la retraite', 'Suivi des comptes par salarié'],
    pieces: ['Population cible : catégories de personnel concernées', "Liste du personnel : naissance, embauche, salaire", 'Niveau de cotisation envisagé (part employeur et salarié)', 'Âge de départ à la retraite', 'Forme de sortie souhaitée : capital ou rente'] },
  ifc: { label: 'IFC · Indemnités de fin de carrière', cie: 'RAWSUR Life SA · épargne',
    desc: "Finance à l'avance les indemnités de départ à la retraite que l'entreprise devra verser, pour éviter un choc de trésorerie le jour où elles tombent.",
    benefice: "Vos départs en retraite sont une dette certaine : financez-la aujourd'hui, à votre rythme, au lieu de la payer d'un coup demain.",
    couvert: ['Évaluation actuarielle de la dette acquise', 'Charge annuelle des nouveaux droits', 'Financement immédiat, sur 5 ans ou 10 ans', 'Versement des indemnités au départ'],
    pieces: ['Fichier du personnel : naissance, embauche, sexe', 'Salaire de base ou brut de chaque employé', "Règles d'indemnité : convention collective ou règlement", 'Âge de départ à la retraite pratiqué', 'Provisions ou fonds déjà constitués'] }
};

function main() {
  const [inp, out] = process.argv.slice(2);
  if (!inp || !out) { console.error('Usage : node presentation_client.js entree.json sortie.pptx'); process.exit(1); }
  const d = JSON.parse(fs.readFileSync(inp, 'utf8'));
  const prods = (d.produits || []).filter(p => P[p]);
  if (!d.client || !prods.length) { console.error('Il faut au moins le client et un produit connu.'); process.exit(1); }
  const titre = s => String(s || '').toLowerCase().replace(/(^|[\s'-])\p{L}/gu, m => m.toUpperCase());
  const contact = d.contact ? `${titre(d.contact)}${d.fonction ? ', ' + titre(d.fonction) : ''}` : '';

  const pres = new pptxgen();
  pres.layout = 'LAYOUT_16x9';
  pres.title = `Proposition RAWSUR · ${d.client}`;
  pres.author = 'RAWSUR · Bancassurance & Partnerships';
  pres.company = 'RAWSUR SA';
  pres.theme = { headFontFace: THEME.headFontFace, bodyFontFace: THEME.bodyFontFace };
  const C = pres.SchemeColor;

  pres.defineSlideMaster({ title: 'COUVERTURE', background: { color: C.text2 }, objects: [
    { placeholder: { options: { name: 'title', type: 'title', x: 0.6, y: 1.7, w: 8.8, h: 1.3, fontFace: THEME.headFontFace, fontSize: 36, bold: true, color: C.background1, valign: 'top', align: 'left', margin: 0 }, text: '' } }
  ] });
  pres.defineSlideMaster({ title: 'CONTENU', background: { color: C.background1 }, margin: [0.5, 0.5, 0.6, 0.5], slideNumber: { x: 9.1, y: 5.22, w: 0.4, h: 0.25, fontSize: 9, color: C.accent4, align: 'right' }, objects: [
    { placeholder: { options: { name: 'title', type: 'title', x: 0.5, y: 0.35, w: 9.0, h: 0.6, fontFace: THEME.headFontFace, fontSize: 26, bold: true, color: C.text2, valign: 'middle', align: 'left', margin: 0 }, text: '' } },
    { text: { text: `RAWSUR × Rawbank · ${d.client}`, options: { x: 0.5, y: 5.22, w: 7.5, h: 0.25, fontSize: 9, color: C.accent4, margin: 0 } } }
  ] });

  // 1. Couverture
  pres.addSection({ title: 'Ouverture' });
  let s = pres.addSlide({ masterName: 'COUVERTURE', sectionTitle: 'Ouverture' });
  s.addText('RAWSUR × RAWBANK · SYNERGIE CORPORATE', { x: 0.6, y: 0.75, w: 8.8, h: 0.35, fontSize: 12, bold: true, color: C.accent3, charSpacing: 3, margin: 0, isTextBox: true, objectName: 'Surtitre' });
  s.addText(d.renouvellement ? 'Proposition de renouvellement' : 'Proposition d’accompagnement', { x: 0.6, y: 1.15, w: 8.8, h: 0.45, fontSize: 20, color: C.accent5, margin: 0, isTextBox: true, objectName: 'Objet' });
  s.addText(d.client, { placeholder: 'title' });
  s.addText([
    ...(contact ? [{ text: `À l’attention de ${contact}`, options: { breakLine: true } }] : []),
    { text: `Préparée avec ${d.cboNom || 'votre conseiller Rawbank'}${d.cboAgence ? ', agence ' + titre(d.cboAgence) : ''}`, options: { breakLine: true } },
    { text: d.date || '' }
  ], { x: 0.6, y: 3.35, w: 8.8, h: 1.1, fontSize: 15, color: C.background1, margin: 0, valign: 'top', paraSpaceAfter: 4, isTextBox: true, objectName: 'Destinataire' });
  s.addText(prods.map(p => P[p].label).join('  ·  '), { x: 0.6, y: 4.75, w: 8.8, h: 0.4, fontSize: 12, color: C.accent3, margin: 0, isTextBox: true, objectName: 'Produits' });

  // 2. Vos besoins
  pres.addSection({ title: 'Vos besoins' });
  s = pres.addSlide({ masterName: 'CONTENU', sectionTitle: 'Vos besoins' });
  s.addText('Ce que nous retenons de votre demande', { placeholder: 'title' });
  const n = prods.length, cols = n <= 2 ? n : (n <= 4 ? 2 : 3), rows = Math.ceil(n / cols);
  const gx = 0.5, gy = 1.15, gw = 9.0, gap = 0.25, contexte = [d.secteur && `Secteur : ${d.secteur}`, d.effectif && `Effectif : ${d.effectif}`, d.assureurActuel && `Assureur actuel : ${d.assureurActuel}`, d.echeance && `Échéance : ${d.echeance}`, d.besoin && `Besoin exprimé : ${d.besoin}`].filter(Boolean);
  const gh = contexte.length ? 3.3 : 3.75;
  const cw = (gw - gap * (cols - 1)) / cols, ch = (gh - gap * (rows - 1)) / rows;
  prods.forEach((p, i) => {
    const x = gx + (i % cols) * (cw + gap), y = gy + Math.floor(i / cols) * (ch + gap);
    s.addShape(pres.shapes.ROUNDED_RECTANGLE, { x, y, w: cw, h: ch, rectRadius: 0.08, fill: { color: C.background2 }, line: { color: C.accent5, width: 0.75 }, objectName: `Carte ${P[p].label}` });
    s.addShape(pres.shapes.OVAL, { x: x + 0.2, y: y + 0.2, w: 0.42, h: 0.42, fill: { color: C.text2 }, line: { color: C.text2 }, objectName: `Pastille ${i + 1}` });
    s.addText(String(i + 1), { x: x + 0.2, y: y + 0.2, w: 0.42, h: 0.42, fontSize: 14, bold: true, color: C.background1, align: 'center', valign: 'middle', margin: 0, isTextBox: true });
    s.addText([{ text: P[p].label, options: { bold: true, fontSize: 15, color: C.text2, breakLine: true } }, { text: P[p].cie, options: { fontSize: 10, color: C.accent4 } }], { x: x + 0.75, y: y + 0.14, w: cw - 0.95, h: 0.6, margin: 0, valign: 'middle', isTextBox: true });
    const cles = n <= 2 ? P[p].couvert.slice(0, 5) : [];
    s.addText([{ text: P[p].benefice, options: { fontSize: n > 2 ? 12 : 14, color: C.text1, breakLine: cles.length > 0, paraSpaceAfter: 8 } },
      ...(cles.length ? [{ text: 'Garanties clés', options: { fontSize: 12, bold: true, color: C.text2, breakLine: true } }] : []),
      ...cles.map((c, k) => ({ text: c, options: { fontSize: 12, color: C.text1, bullet: true, breakLine: k < cles.length - 1 } }))],
      { x: x + 0.25, y: y + 0.78, w: cw - 0.5, h: ch - 0.88, margin: 0, valign: 'top', paraSpaceAfter: 3, isTextBox: true, objectName: `Bénéfice ${P[p].label}` });
  });
  if (contexte.length) s.addText(contexte.join('   ·   '), { x: 0.5, y: 4.6, w: 9.0, h: 0.45, fontSize: 12, italic: true, color: C.accent4, margin: 0, valign: 'middle', isTextBox: true, objectName: 'Contexte' });

  // 3. Une page par produit
  pres.addSection({ title: 'Nos solutions' });
  for (const p of prods) {
    const q = P[p];
    s = pres.addSlide({ masterName: 'CONTENU', sectionTitle: 'Nos solutions' });
    s.addText(q.label, { placeholder: 'title' });
    s.addText(q.desc, { x: 0.5, y: 1.0, w: 5.5, h: 0.75, fontSize: 13, color: C.text1, margin: 0, valign: 'top', isTextBox: true, objectName: 'Description' });
    s.addText('Ce qui est couvert', { x: 0.5, y: 1.85, w: 5.5, h: 0.3, fontSize: 14, bold: true, color: C.text2, margin: 0, isTextBox: true });
    const cov = q.couvert, half = Math.ceil(cov.length / 2), rh = Math.min(0.5, 2.85 / half), w2 = 2.65;
    cov.forEach((c, i) => {
      const x = 0.5 + (i >= half ? w2 + 0.2 : 0), y = 2.25 + (i % half) * rh;
      s.addShape(pres.shapes.ROUNDED_RECTANGLE, { x, y, w: w2, h: rh - 0.08, rectRadius: 0.06, fill: { color: C.background2 }, line: { color: C.background2 }, objectName: `Garantie ${i + 1}` });
      s.addText(c, { x: x + 0.12, y, w: w2 - 0.2, h: rh - 0.08, fontSize: 11, color: C.text1, margin: 0, valign: 'middle', isTextBox: true });
    });
    s.addShape(pres.shapes.ROUNDED_RECTANGLE, { x: 6.3, y: 1.0, w: 3.2, h: 4.05, rectRadius: 0.08, fill: { color: C.text2 }, line: { color: C.text2 }, objectName: 'Pièces à fournir' });
    s.addText(d.offre ? 'Pièces du dossier' : 'Pour établir votre offre', { x: 6.5, y: 1.15, w: 2.8, h: 0.35, fontSize: 14, bold: true, color: C.accent3, margin: 0, isTextBox: true });
    s.addText(q.pieces.map((t, i) => ({ text: t, options: { bullet: true, breakLine: i < q.pieces.length - 1 } })), { x: 6.5, y: 1.55, w: 2.85, h: 3.35, fontSize: q.pieces.length > 6 ? 10.5 : 11.5, color: C.background1, margin: 0, valign: 'top', paraSpaceAfter: 5, isTextBox: true, objectName: 'Liste des pièces' });
    s.addNotes(`${q.label} : ${q.benefice}`);
  }

  // 4. Pourquoi RAWSUR
  pres.addSection({ title: 'Conclusion' });
  s = pres.addSlide({ masterName: 'CONTENU', sectionTitle: 'Conclusion' });
  s.addText('Pourquoi RAWSUR', { placeholder: 'title' });
  const stats = [['35 %', 'du marché non-vie en 2024', 'RAWSUR SA, n°1 de la non-vie en RDC'], ['62 %', 'du marché vie en 2024', 'RAWSUR Life, n°1 de la vie en RDC'], ['24 h', 'pour recevoir votre offre', 'à partir d’un dossier complet']];
  stats.forEach(([v, l, sub], i) => {
    const x = 0.5 + i * 3.08;
    s.addShape(pres.shapes.ROUNDED_RECTANGLE, { x, y: 1.2, w: 2.85, h: 2.6, rectRadius: 0.08, fill: { color: C.background2 }, line: { color: C.background2 }, objectName: `Chiffre ${i + 1}` });
    s.addText(v, { x: x + 0.2, y: 1.35, w: 2.45, h: 1.0, fontSize: 44, bold: true, color: C.text2, fontFace: THEME.headFontFace, margin: 0, valign: 'middle', isTextBox: true });
    s.addText([{ text: l, options: { bold: true, breakLine: true } }, { text: sub }], { x: x + 0.2, y: 2.45, w: 2.45, h: 1.2, fontSize: 13, color: C.text1, margin: 0, valign: 'top', isTextBox: true });
  });
  s.addText('Premier groupe privé d’assurances en RDC, avec une équipe dédiée Bancassurance & Partnerships qui travaille aux côtés de votre conseiller Rawbank.', { x: 0.5, y: 4.1, w: 9.0, h: 0.7, fontSize: 14, color: C.text1, margin: 0, valign: 'middle', isTextBox: true, objectName: 'Signature groupe' });

  // 5. Prochaines étapes
  s = pres.addSlide({ masterName: 'CONTENU', sectionTitle: 'Conclusion' });
  s.addText('Prochaines étapes', { placeholder: 'title' });
  const etapes = d.offre
    ? [['Vous examinez notre offre', `Offre écrite établie le ${d.offre} ; nous répondons à vos questions.`], ['Vous confirmez votre accord', 'Signature du contrat et règlement de la prime.'], ['Votre couverture prend effet', 'À la date de paiement de la prime.']]
    : [['Vous nous transmettez les pièces', 'Listées produit par produit dans les pages précédentes.'], ['Nous vous adressons une offre écrite', 'Sous 24 h à partir d’un dossier complet.'], ['Nous mettons en place la couverture', 'Dès votre accord et le paiement de la prime.']];
  etapes.forEach(([t, sub], i) => {
    const x = 0.5 + i * 3.08;
    s.addShape(pres.shapes.OVAL, { x, y: 1.25, w: 0.6, h: 0.6, fill: { color: C.text2 }, line: { color: C.text2 }, objectName: `Étape ${i + 1}` });
    s.addText(String(i + 1), { x, y: 1.25, w: 0.6, h: 0.6, fontSize: 20, bold: true, color: C.background1, align: 'center', valign: 'middle', margin: 0, isTextBox: true });
    s.addText([{ text: t, options: { bold: true, fontSize: 15, color: C.text2, breakLine: true } }, { text: sub, options: { fontSize: 13, color: C.text1 } }], { x, y: 2.0, w: 2.8, h: 1.2, margin: 0, valign: 'top', isTextBox: true });
  });
  s.addShape(pres.shapes.ROUNDED_RECTANGLE, { x: 0.5, y: 3.45, w: 9.0, h: 1.25, rectRadius: 0.08, fill: { color: C.background2 }, line: { color: C.background2 }, objectName: 'Contacts' });
  s.addText([
    { text: 'Vos contacts', options: { bold: true, fontSize: 14, color: C.text2, breakLine: true } },
    { text: `Votre conseiller Rawbank : ${d.cboNom || '—'}${d.cboMail ? ' · ' + d.cboMail.toLowerCase() : ''}`, options: { fontSize: 13, color: C.text1, breakLine: true } },
    { text: 'Desk Bancassurance & Partnerships : ref-bancassurance@rawbank.cd', options: { fontSize: 13, color: C.text1 } }
  ], { x: 0.75, y: 3.55, w: 8.5, h: 1.05, margin: 0, valign: 'middle', paraSpaceAfter: 3, isTextBox: true });
  s.addText('Document de présentation : seule l’offre écrite de RAWSUR engage sur les tarifs et les garanties.', { x: 0.5, y: 4.82, w: 9.0, h: 0.3, fontSize: 10, italic: true, color: C.accent4, margin: 0, isTextBox: true });

  pres.writeFile({ fileName: out }).then(async () => { await applyTheme(out, THEME); console.log(JSON.stringify({ ok: true, out, slides: 4 + prods.length })); })
    .catch(e => { console.error(e); process.exit(1); });
}
if (require.main === module) main();
module.exports = { P, THEME };
