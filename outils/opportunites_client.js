#!/usr/bin/env node
/* Document « Opportunités » : les produits RAWSUR que le client n'a pas demandés mais qui correspondent à son activité,
   avec, toujours, l'assurance vie de ses collaborateurs (Group Life, CAREC, IFC).
   Usage : node opportunites_client.js entree.json sortie.pptx   (même JSON que presentation_client.js)
   Module : require('./opportunites_client.js').opportunites(d) → { profil, nonVie: [...], vie: [...] } (réutilisé par mail_client.js).
   Repères chiffrés : kit d'argumentaire Synergie Corporate du 21/10/2026 (offres réelles anonymisées), présentés comme indicatifs. */
const fs = require('fs');
const path = require('path');
const pptxgen = require('pptxgenjs');
const { applyTheme } = require(path.join(__dirname, 'apply_theme.js'));
const { P, THEME } = require(path.join(__dirname, 'presentation_client.js'));

const sansAccent = s => String(s || '').normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase();

// Profils d'activité : mots-clés (sur secteur, besoin et raison sociale), produits non-vie dans l'ordre de pertinence,
// et la raison « pour vous » propre au métier. Le premier profil qui correspond l'emporte.
const PROFILS = [
  { id: 'ong', nom: 'ONG et organisations humanitaires', re: /\bong\b|humanitaire|asbl|association|solidarit|ngo\b|fondation/,
    produits: ['sante', 'transport', 'flotte', 'multirisque'], pourquoi: {
      sante: "Vos équipes terrain travaillent loin des grands centres : le tiers payant leur ouvre les soins sans avance de frais, partout dans le réseau national.",
      transport: "Vos intrants et équipements arrivent par route, fleuve ou air : une cargaison perdue, c'est une mission retardée et un bailleur à rassurer.",
      flotte: "Vos véhicules sont l'outil de vos missions : un seul contrat pour tout le parc, avec assistance 24 h/24.",
      multirisque: "Entrepôts, bureaux, stocks de matériel : un incendie ou un dégât des eaux ne doit pas stopper vos programmes." } },
  { id: 'medical', nom: 'santé et établissements de soins', re: /medic|hopita|clinique|centre de sante|pharma|laboratoire|cabinet/,
    produits: ['sante', 'multirisque', 'flotte'], pourquoi: {
      sante: "Vos soignants prennent soin des autres : offrez-leur la même sécurité, avec une prime connue au lieu de prises en charge au cas par cas.",
      multirisque: "Vos équipements médicaux sont coûteux et difficiles à remplacer : un sinistre ne doit pas fermer vos services.",
      flotte: "Ambulances et véhicules de service assurés ensemble, avec assistance et aide au constat." } },
  { id: 'hotel', nom: 'hôtellerie et restauration', re: /hotel|hotell|restaura|lodge|auberge|tourism/,
    produits: ['multirisque', 'sante', 'flotte'], pourquoi: {
      multirisque: "Cuisines, groupes électrogènes, chambres équipées : l'incendie est le premier risque d'un hôtel, et la responsabilité envers vos clients suit.",
      sante: "Un personnel en bonne santé, c'est un service constant : la santé collective fidélise vos équipes.",
      flotte: "Navettes et véhicules de service réunis dans une seule police." } },
  { id: 'petrole', nom: 'distribution de produits pétroliers', re: /petrol|carburant|station[- ]service|lubrifiant|gasoil|essence/,
    produits: ['transport', 'flotte', 'multirisque'], pourquoi: {
      transport: "Chaque citerne acheminée vers votre dépôt est du capital roulant : une perte en route ne doit pas tomber sur votre trésorerie.",
      flotte: "Camions-citernes et véhicules de livraison sous un seul contrat, avec assistance en cas d'accident.",
      multirisque: "Un dépôt de carburant concentre un risque d'incendie majeur : protégez les installations et votre responsabilité envers les voisins." } },
  { id: 'vehicules', nom: 'vente de véhicules et de motos', re: /moto|vehicule|automobile|\bauto\b|garage|pieces detachees|concession/,
    produits: ['transport', 'multirisque', 'flotte'], pourquoi: {
      transport: "Vos motos et véhicules arrivent d'importation : de l'embarquement à votre magasin, la marchandise est couverte.",
      multirisque: "Votre stock en exposition vaut souvent plus que le local lui-même : protégez-le contre l'incendie et les dégâts.",
      flotte: "Véhicules de livraison et de démonstration assurés ensemble." } },
  { id: 'agri', nom: 'agro-industrie et commerce de produits agricoles', re: /cacao|cafe|agri|agro|food|vivrier|farine|riz|mais|huile|elevage|plantation/,
    produits: ['transport', 'multirisque', 'flotte'], pourquoi: {
      transport: "Vos sacs de cacao ou de café voyagent loin avant d'être payés : la marchandise est couverte jusqu'à la livraison.",
      multirisque: "Entrepôts et stocks de récolte : un incendie efface une saison entière, l'assurance la rembourse.",
      flotte: "Camions de collecte et de livraison sous un seul contrat." } },
  { id: 'btp', nom: 'construction et travaux', re: /btp|construction|chantier|genie civil|batiment|travaux|immobili/,
    produits: ['trc', 'flotte', 'multirisque'], pourquoi: {
      trc: "Tant que l'ouvrage n'est pas livré, il est à votre charge : le TRC protège le chantier, les engins et votre responsabilité.",
      flotte: "Camions, engins et véhicules de chantier réunis dans une seule police.",
      multirisque: "Base vie, dépôt de matériaux, bureaux : protégez ce qui reste après chaque chantier." } },
  { id: 'mines', nom: 'mines et industrie', re: /mine|minier|extraction|carriere|industri|usine/,
    produits: ['trc', 'transport', 'flotte', 'corps'], pourquoi: {
      trc: "Vos installations en construction ou en extension sont couvertes jusqu'à la réception.",
      transport: "Équipements importés et minerais acheminés : la marchandise est protégée sur tout le trajet.",
      flotte: "Véhicules légers et camions de site sous un seul contrat.",
      corps: "Avions, hélicoptères ou barges au service du site : l'actif le plus cher mérite une couverture dédiée." } },
  { id: 'logistique', nom: 'transport et logistique', re: /transport|logisti|fluvial|barge|aviation|aerien|fret|transit|messagerie(?! financ)/,
    produits: ['corps', 'transport', 'flotte'], pourquoi: {
      corps: "Barges, pousseurs ou appareils : une seule perte ne doit pas effacer votre outil de travail.",
      transport: "Les marchandises que vous transportez pour vos clients sont couvertes de bout en bout.",
      flotte: "Tout votre parc routier dans une seule police, avec assistance 24 h/24." } },
  { id: 'commerce', nom: 'commerce et distribution', re: /commerce|vente|revend|distribut|import|quincaill|ciment|materiaux|chambre froide|boutique|magasin|depot/,
    produits: ['multirisque', 'transport', 'flotte'], pourquoi: {
      multirisque: "Votre stock est votre trésorerie : un incendie ou un dégât des eaux ne doit pas l'effacer.",
      transport: "Vos marchandises importées sont couvertes du fournisseur jusqu'à votre dépôt.",
      flotte: "Véhicules de livraison réunis dans une seule police, avec assistance en cas d'accident." } },
  { id: 'services', nom: 'services, finance et télécoms', re: /financ|banque|microfinance|assurance|telecom|informatique|conseil|cabinet|service|maintenance/,
    produits: ['multirisque', 'flotte', 'sante'], pourquoi: {
      multirisque: "Agences, équipements informatiques et fonds de caisse : un sinistre arrête votre service, l'assurance vous remet en route.",
      flotte: "Véhicules de service et de convoyage assurés ensemble.",
      sante: "Dans une entreprise de services, vos équipes sont votre outil de production : la santé collective les protège et les fidélise." } }
];
const DEFAUT = { id: 'general', nom: 'votre activité', produits: ['multirisque', 'flotte', 'sante'], pourquoi: {} };

const VIE = {
  grouplife: { court: 'Group Life · décès, invalidité', accroche: "Un drame dans l'équipe ne doit pas ruiner une famille.",
    args: ["Le capital va à la famille, sans collecte improvisée ni décision prise dans l'urgence.",
           "Une prime connue, calculée sur la masse salariale, et des équipes qui savent leurs proches à l'abri."],
    repere: "Repère : ≈ 0,8 % de la masse salariale pour un capital de 100 % du salaire en décès et 60 % en invalidité." },
  retraite: { court: 'CAREC · retraite complémentaire', accroche: 'Retenez vos talents sans augmenter les salaires.',
    args: ['Une épargne retraite que vos collaborateurs voient grandir année après année.',
           "Vous fixez la cotisation, les salariés complètent s'ils le veulent ; capital ou rente au départ."],
    repere: "Repère : une cotisation employeur de 5 % du salaire suffit pour démarrer, sans engagement de rendement." },
  ifc: { court: 'IFC · fin de carrière', accroche: 'Votre dette sociale grossit chaque année, en silence.',
    args: ["Chaque année d'ancienneté augmente l'indemnité due au départ : nous l'évaluons pour vous.",
           'Financée à votre rythme, elle ne tombe plus d’un coup sur votre trésorerie.'],
    repere: 'Repère : pour 71 salariés, une dette de ≈ 455 500 $ financée ≈ 76 700 $ par an sur 10 ans, au lieu de 486 700 $ en une fois.' }
};
const REPERE_NONVIE = { sante: 'Repère : à partir de ≈ 311 $ par personne et par an en formule Basique.' };

function opportunites(d) {
  const texte = sansAccent([d.secteur, d.besoin, d.client].join(' '));
  const profil = PROFILS.find(p => p.re.test(texte)) || DEFAUT;
  const deja = new Set(d.produits || []);
  const ordre = [...profil.produits, ...DEFAUT.produits].filter((p, i, a) => a.indexOf(p) === i);
  const nonVie = ordre.filter(p => !deja.has(p) && P[p] && !P[p].cie.includes('Life')).slice(0, 3).map(id => ({
    id, label: P[id].label, cie: P[id].cie, pourquoi: profil.pourquoi[id] || P[id].benefice,
    cles: P[id].couvert.slice(0, 3), repere: REPERE_NONVIE[id] || '' }));
  const vie = ['grouplife', 'retraite', 'ifc'].filter(p => !deja.has(p)).map(id => ({ id, label: P[id].label, cie: P[id].cie, ...VIE[id] }));
  return { profil: { id: profil.id, nom: profil.nom }, nonVie, vie };
}

function main() {
  const [inp, out] = process.argv.slice(2);
  if (!inp || !out) { console.error('Usage : node opportunites_client.js entree.json sortie.pptx'); process.exit(1); }
  const d = JSON.parse(fs.readFileSync(inp, 'utf8'));
  if (!d.client) { console.error('Il faut au moins le client.'); process.exit(1); }
  const o = opportunites(d);
  const titre = s => String(s || '').toLowerCase().replace(/(^|[\s'-])\p{L}/gu, m => m.toUpperCase());
  const contact = d.contact ? `${titre(d.contact)}${d.fonction ? ', ' + titre(d.fonction) : ''}` : '';
  const demandes = (d.produits || []).filter(p => P[p]).map(p => P[p].label);

  const pres = new pptxgen();
  pres.layout = 'LAYOUT_16x9';
  pres.title = `Opportunités RAWSUR · ${d.client}`;
  pres.author = 'RAWSUR · Bancassurance & Partnerships';
  pres.company = 'RAWSUR SA';
  pres.theme = { headFontFace: THEME.headFontFace, bodyFontFace: THEME.bodyFontFace };
  const C = pres.SchemeColor;
  pres.defineSlideMaster({ title: 'COUVERTURE', background: { color: C.text2 }, objects: [
    { placeholder: { options: { name: 'title', type: 'title', x: 0.6, y: 1.75, w: 8.8, h: 1.2, fontFace: THEME.headFontFace, fontSize: 34, bold: true, color: C.background1, valign: 'top', align: 'left', margin: 0 }, text: '' } }] });
  pres.defineSlideMaster({ title: 'CONTENU', background: { color: C.background1 }, slideNumber: { x: 9.1, y: 5.22, w: 0.4, h: 0.25, fontSize: 9, color: C.accent4, align: 'right' }, objects: [
    { placeholder: { options: { name: 'title', type: 'title', x: 0.5, y: 0.3, w: 9.0, h: 0.6, fontFace: THEME.headFontFace, fontSize: 24, bold: true, color: C.text2, valign: 'middle', align: 'left', margin: 0 }, text: '' } },
    { text: { text: `RAWSUR × Rawbank · ${d.client}`, options: { x: 0.5, y: 5.22, w: 7.5, h: 0.25, fontSize: 9, color: C.accent4, margin: 0 } } }] });
  pres.defineSlideMaster({ title: 'CONTENU SOMBRE', background: { color: C.text2 }, slideNumber: { x: 9.1, y: 5.22, w: 0.4, h: 0.25, fontSize: 9, color: C.accent5, align: 'right' }, objects: [
    { placeholder: { options: { name: 'title', type: 'title', x: 0.5, y: 0.3, w: 9.0, h: 0.6, fontFace: THEME.headFontFace, fontSize: 24, bold: true, color: C.background1, valign: 'middle', align: 'left', margin: 0 }, text: '' } },
    { text: { text: `RAWSUR × Rawbank · ${d.client}`, options: { x: 0.5, y: 5.22, w: 7.5, h: 0.25, fontSize: 9, color: C.accent5, margin: 0 } } }] });

  // 1. Couverture
  pres.addSection({ title: 'Ouverture' });
  let s = pres.addSlide({ masterName: 'COUVERTURE', sectionTitle: 'Ouverture' });
  s.addText('RAWSUR × RAWBANK · SYNERGIE CORPORATE', { x: 0.6, y: 0.75, w: 8.8, h: 0.35, fontSize: 12, bold: true, color: C.accent3, charSpacing: 3, margin: 0, isTextBox: true, objectName: 'Surtitre' });
  s.addText('Au-delà de votre demande', { x: 0.6, y: 1.15, w: 8.8, h: 0.45, fontSize: 20, color: C.accent5, margin: 0, isTextBox: true, objectName: 'Objet' });
  s.addText(d.client, { placeholder: 'title' });
  s.addText([
    { text: `Des protections pensées pour ${o.profil.id === 'general' ? 'votre activité' : 'le secteur ' + o.profil.nom}, et pour ceux qui la font vivre.`, options: { breakLine: true } },
    ...(contact ? [{ text: `À l’attention de ${contact}`, options: { breakLine: true } }] : []),
    { text: `Préparé avec ${d.cboNom || 'votre conseiller Rawbank'}${d.cboAgence ? ', agence ' + titre(d.cboAgence) : ''} · ${d.date || ''}` }
  ], { x: 0.6, y: 3.2, w: 8.8, h: 1.25, fontSize: 14, color: C.background1, margin: 0, valign: 'top', paraSpaceAfter: 5, isTextBox: true, objectName: 'Destinataire' });
  if (demandes.length) s.addText(`Déjà en cours avec nous : ${demandes.join(' · ')}`, { x: 0.6, y: 4.75, w: 8.8, h: 0.4, fontSize: 12, color: C.accent3, margin: 0, isTextBox: true, objectName: 'Déjà demandé' });

  // 2. Non-vie selon le métier
  pres.addSection({ title: 'Votre activité' });
  if (o.nonVie.length) {
    s = pres.addSlide({ masterName: 'CONTENU', sectionTitle: 'Votre activité' });
    s.addText('Ce qui pourrait aussi protéger votre activité', { placeholder: 'title' });
    const n = o.nonVie.length, gap = 0.25, cw = (9.0 - gap * (n - 1)) / n;
    o.nonVie.forEach((q, i) => {
      const x = 0.5 + i * (cw + gap), y = 1.1, h = 3.85;
      s.addShape(pres.shapes.ROUNDED_RECTANGLE, { x, y, w: cw, h, rectRadius: 0.08, fill: { color: C.background2 }, line: { color: C.accent5, width: 0.75 }, objectName: `Carte ${q.label}` });
      s.addShape(pres.shapes.RECTANGLE, { x: x + 0.25, y: y + 0.28, w: 0.5, h: 0.06, fill: { color: C.accent3 }, line: { color: C.accent3 }, objectName: `Filet ${i + 1}` });
      s.addText([{ text: q.label, options: { bold: true, fontSize: 15, color: C.text2, breakLine: true } }, { text: q.cie, options: { fontSize: 10, color: C.accent4 } }],
        { x: x + 0.25, y: y + 0.42, w: cw - 0.5, h: 0.7, margin: 0, valign: 'top', isTextBox: true });
      s.addText([{ text: 'Pour vous', options: { bold: true, fontSize: 11, color: C.text2, breakLine: true } }, { text: q.pourquoi, options: { fontSize: 12, color: C.text1 } }],
        { x: x + 0.25, y: y + 1.15, w: cw - 0.5, h: 1.45, margin: 0, valign: 'top', paraSpaceAfter: 2, isTextBox: true, objectName: `Pour vous ${i + 1}` });
      s.addText(q.cles.map((c, k) => ({ text: c, options: { bullet: { indent: 11 }, breakLine: k < q.cles.length - 1 } })),
        { x: x + 0.25, y: y + 2.62, w: cw - 0.5, h: q.repere ? 0.75 : 1.05, fontSize: 10.5, color: C.accent4, margin: 0, valign: 'top', paraSpaceAfter: 2, isTextBox: true, objectName: `Garanties ${i + 1}` });
      if (q.repere) s.addText(q.repere, { x: x + 0.25, y: y + 3.38, w: cw - 0.5, h: 0.4, fontSize: 9.5, italic: true, color: C.text2, margin: 0, valign: 'top', isTextBox: true, objectName: `Repère ${i + 1}` });
    });
  }

  // 3. La vie, toujours
  pres.addSection({ title: 'Vos collaborateurs' });
  s = pres.addSlide({ masterName: 'CONTENU SOMBRE', sectionTitle: 'Vos collaborateurs' });
  s.addText('Votre premier actif, ce sont vos collaborateurs', { placeholder: 'title' });
  s.addText('RAWSUR Life, n°1 de l’assurance vie en RDC : 62 % du marché en 2024.', { x: 0.5, y: 0.88, w: 9.0, h: 0.3, fontSize: 12, color: C.accent3, margin: 0, isTextBox: true, objectName: 'Signature vie' });
  const nv = o.vie.length, gv = 0.22, wv = (9.0 - gv * (nv - 1)) / nv;
  o.vie.forEach((q, i) => {
    const x = 0.5 + i * (wv + gv), y = 1.32, h = 3.72;
    s.addShape(pres.shapes.ROUNDED_RECTANGLE, { x, y, w: wv, h, rectRadius: 0.08, fill: { color: C.background1 }, line: { color: C.background1 }, objectName: `Carte ${q.label}` });
    s.addText(q.court || q.label, { x: x + 0.22, y: y + 0.15, w: wv - 0.44, h: 0.3, fontSize: 11.5, bold: true, color: C.accent4, margin: 0, isTextBox: true });
    s.addText(q.accroche, { x: x + 0.22, y: y + 0.47, w: wv - 0.44, h: 0.9, fontSize: 15, bold: true, color: C.text2, fontFace: THEME.headFontFace, margin: 0, valign: 'top', isTextBox: true, objectName: `Accroche ${i + 1}` });
    s.addText(q.args.map((t, k) => ({ text: t, options: { bullet: { indent: 11 }, breakLine: k < q.args.length - 1 } })),
      { x: x + 0.22, y: y + 1.42, w: wv - 0.44, h: 1.45, fontSize: 11, color: C.text1, margin: 0, valign: 'top', paraSpaceAfter: 4, isTextBox: true, objectName: `Arguments ${i + 1}` });
    s.addShape(pres.shapes.ROUNDED_RECTANGLE, { x: x + 0.15, y: y + 2.95, w: wv - 0.3, h: 0.65, rectRadius: 0.05, fill: { color: C.background2 }, line: { color: C.background2 }, objectName: `Fond repère ${i + 1}` });
    s.addText(q.repere, { x: x + 0.25, y: y + 2.97, w: wv - 0.5, h: 0.61, fontSize: 9, italic: true, color: C.text2, margin: 0, valign: 'middle', isTextBox: true, objectName: `Repère vie ${i + 1}` });
  });
  s.addNotes('Les repères chiffrés viennent d’offres réelles RAWSUR Life anonymisées (kit d’argumentaire Synergie Corporate) ; ils sont indicatifs.');

  // 4. Une seule offre
  pres.addSection({ title: 'Conclusion' });
  s = pres.addSlide({ masterName: 'CONTENU', sectionTitle: 'Conclusion' });
  s.addText('Un seul interlocuteur, une offre groupée', { placeholder: 'title' });
  const etapes = [['Dites-nous ce qui vous intéresse', 'Une réponse à votre conseiller Rawbank suffit.'], ['Nous chiffrons l’ensemble', 'Une offre écrite unique, sous 24 h à partir d’un dossier complet.'], ['Vous décidez produit par produit', 'Vous gardez ce qui vous convient, rien n’est imposé.']];
  etapes.forEach(([t, sub], i) => {
    const x = 0.5 + i * 3.08;
    s.addShape(pres.shapes.OVAL, { x, y: 1.2, w: 0.6, h: 0.6, fill: { color: C.text2 }, line: { color: C.text2 }, objectName: `Étape ${i + 1}` });
    s.addText(String(i + 1), { x, y: 1.2, w: 0.6, h: 0.6, fontSize: 20, bold: true, color: C.background1, align: 'center', valign: 'middle', margin: 0, isTextBox: true });
    s.addText([{ text: t, options: { bold: true, fontSize: 15, color: C.text2, breakLine: true } }, { text: sub, options: { fontSize: 13, color: C.text1 } }], { x, y: 1.95, w: 2.8, h: 1.2, margin: 0, valign: 'top', isTextBox: true });
  });
  s.addShape(pres.shapes.ROUNDED_RECTANGLE, { x: 0.5, y: 3.4, w: 9.0, h: 1.2, rectRadius: 0.08, fill: { color: C.background2 }, line: { color: C.background2 }, objectName: 'Contacts' });
  s.addText([
    { text: 'Vos contacts', options: { bold: true, fontSize: 14, color: C.text2, breakLine: true } },
    { text: `Votre conseiller Rawbank : ${d.cboNom || '—'}${d.cboMail ? ' · ' + d.cboMail.toLowerCase() : ''}`, options: { fontSize: 13, color: C.text1, breakLine: true } },
    { text: 'Desk Bancassurance & Partnerships : ref-bancassurance@rawbank.cd', options: { fontSize: 13, color: C.text1 } }
  ], { x: 0.75, y: 3.48, w: 8.5, h: 1.04, margin: 0, valign: 'middle', paraSpaceAfter: 3, isTextBox: true });
  s.addText('Repères indicatifs tirés d’offres réelles RAWSUR ; seule l’offre écrite de RAWSUR engage sur les tarifs et les garanties.', { x: 0.5, y: 4.75, w: 9.0, h: 0.3, fontSize: 10, italic: true, color: C.accent4, margin: 0, isTextBox: true });

  pres.writeFile({ fileName: out }).then(async () => { await applyTheme(out, THEME); console.log(JSON.stringify({ ok: true, out, profil: o.profil.id, nonVie: o.nonVie.map(x => x.id), vie: o.vie.map(x => x.id) })); })
    .catch(e => { console.error(e); process.exit(1); });
}
if (require.main === module) main();
module.exports = { opportunites };
