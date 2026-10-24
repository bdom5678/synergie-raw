#!/usr/bin/env node
/* Projet de mail personnalisé (HTML + texte) pour un client référencé, à partir du même JSON que presentation_client.js.
   Usage : node mail_client.js entree.json sortie.json
   entree.json : champs de presentation_client.js + contactMail (facultatif).
   sortie.json : { to, cc, subject, htmlBody, body, destinataire: "client" | "cbo" }
   Si l'e-mail du client est connu, le mail lui est adressé (copie CBO et desk) ; sinon il est adressé au CBO,
   avec le message prêt à transmettre au client. Aucun montant n'est repris : seule l'offre écrite engage. */
const fs = require('fs');
const path = require('path');
const { P } = require(path.join(__dirname, 'presentation_client.js'));
const DESK = 'ref-bancassurance@rawbank.cd';
const esc = s => String(s || '').replace(/[&<>"]/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));
const titre = s => String(s || '').toLowerCase().replace(/(^|[\s'-])\p{L}/gu, m => m.toUpperCase());
const EMAIL = /^[\w.+'-]+@[\w-]+(\.[\w-]+)+$/;

function main() {
  const [inp, out] = process.argv.slice(2);
  const d = JSON.parse(fs.readFileSync(inp, 'utf8'));
  const prods = (d.produits || []).filter(p => P[p]);
  if (!d.client || !prods.length) { console.error('Il faut au moins le client et un produit connu.'); process.exit(1); }
  const cbo = d.cboNom ? titre(d.cboNom) : 'votre conseiller Rawbank';
  const cboMail = (d.cboMail || '').toLowerCase();
  const clientMail = EMAIL.test(d.contactMail || '') ? d.contactMail.trim() : '';
  const salut = d.contact ? `Bonjour ${titre(d.contact)},` : 'Bonjour,';
  const intro = d.renouvellement
    ? `Suite à votre échange avec ${cbo}, votre conseiller Rawbank, nous vous remercions de votre confiance pour le renouvellement de votre couverture auprès de RAWSUR.`
    : `Suite à votre échange avec ${cbo}, votre conseiller Rawbank, nous avons le plaisir de vous présenter les solutions RAWSUR qui répondent au besoin de ${d.client}.`;
  const etape = d.offre
    ? `Notre offre écrite a été établie le ${d.offre} et remise à votre conseiller. Nous restons à votre disposition pour en parcourir les garanties avec vous ; la couverture prend effet à la date de paiement de la prime.`
    : `Pour établir votre offre écrite, il nous suffit des éléments listés ci-dessous. Dès réception d’un dossier complet, vous recevez notre offre sous 24 h.`;

  const blocsH = prods.map(p => {
    const q = P[p];
    const pieces = d.offre ? '' : `<p style="margin:6px 0 2px;font-weight:bold;color:#0E3A75">Pour établir votre offre</p><ul style="margin:0 0 0 18px;padding:0">${q.pieces.map(x => `<li>${esc(x)}</li>`).join('')}</ul>`;
    return `<div style="border:1px solid #D5DEEC;border-radius:6px;padding:12px 14px;margin:10px 0;background:#F6F8FC">
<p style="margin:0;font-size:15px;font-weight:bold;color:#0E3A75">${esc(q.label)}</p>
<p style="margin:2px 0 8px;font-size:12px;color:#5B6B82">${esc(q.cie)}</p>
<p style="margin:0 0 6px">${esc(q.benefice)}</p>
<p style="margin:6px 0 2px;font-weight:bold;color:#0E3A75">Ce qui est couvert</p><ul style="margin:0 0 0 18px;padding:0">${q.couvert.map(x => `<li>${esc(x)}</li>`).join('')}</ul>${pieces}</div>`;
  }).join('\n');
  const blocsT = prods.map(p => {
    const q = P[p];
    return `${q.label} (${q.cie})\n${q.benefice}\nCe qui est couvert :\n${q.couvert.map(x => '- ' + x).join('\n')}` + (d.offre ? '' : `\nPour établir votre offre :\n${q.pieces.map(x => '- ' + x).join('\n')}`);
  }).join('\n\n');
  const contacts = `Votre conseiller Rawbank : ${cbo}${cboMail ? ' (' + cboMail + ')' : ''}\nDesk Bancassurance & Partnerships : ${DESK}`;
  const clause = 'Ce message présente nos solutions ; seule l’offre écrite de RAWSUR engage sur les tarifs et les garanties.';

  const coreH = `<p>${esc(salut)}</p><p>${esc(intro)}</p><p>${esc(etape)}</p>${blocsH}
<p><b>Pourquoi RAWSUR</b> : premier groupe privé d’assurances en RDC, n°1 de la non-vie (35 % du marché en 2024) et de la vie (62 %), avec une équipe dédiée qui travaille aux côtés de votre conseiller Rawbank.</p>
<p>${esc(contacts).replace('\n', '<br>')}</p><p style="font-size:12px;color:#5B6B82;font-style:italic">${esc(clause)}</p><p>Bien cordialement,</p>`;
  const coreT = `${salut}\n\n${intro}\n\n${etape}\n\n${blocsT}\n\nPourquoi RAWSUR : premier groupe privé d’assurances en RDC, n°1 de la non-vie (35 % du marché en 2024) et de la vie (62 %), avec une équipe dédiée qui travaille aux côtés de votre conseiller Rawbank.\n\n${contacts}\n\n${clause}\n\nBien cordialement,`;

  const objetClient = `${d.client} · ${d.renouvellement ? 'votre renouvellement' : 'nos solutions'} ${prods.map(p => P[p].label.split(' · ')[0]).join(' et ')} avec RAWSUR`;
  let res;
  if (clientMail) {
    res = { destinataire: 'client', to: [clientMail], cc: [cboMail, DESK].filter(Boolean), subject: objetClient,
      htmlBody: `<div style="font-family:Calibri,Arial,sans-serif;font-size:14px;color:#1F2937;max-width:640px">${coreH}</div>`, body: coreT };
  } else {
    const noteH = `<p>Bonjour ${esc(cbo.split(' ')[0])},</p><p>Votre client <b>${esc(d.client)}</b> a accepté d’être contacté. Voici le message préparé à son intention : merci de le lui transmettre, ou de nous communiquer son adresse e-mail pour que nous le lui adressions directement, et de nous dire quand il est disponible pour un échange.</p><hr style="border:none;border-top:1px solid #D5DEEC;margin:14px 0">`;
    const noteT = `Bonjour ${cbo.split(' ')[0]},\n\nVotre client ${d.client} a accepté d’être contacté. Voici le message préparé à son intention : merci de le lui transmettre, ou de nous communiquer son adresse e-mail pour que nous le lui adressions directement, et de nous dire quand il est disponible pour un échange.\n\n----------\n\n`;
    res = { destinataire: 'cbo', to: [cboMail].filter(Boolean), cc: [DESK], subject: `Synergie Corporate · ${d.client} : proposition à transmettre à votre client`,
      htmlBody: `<div style="font-family:Calibri,Arial,sans-serif;font-size:14px;color:#1F2937;max-width:640px">${noteH}${coreH}</div>`, body: noteT + coreT };
  }
  fs.writeFileSync(out, JSON.stringify(res, null, 1));
  console.log(JSON.stringify({ ok: true, out, destinataire: res.destinataire, to: res.to, octetsHtml: res.htmlBody.length }));
}
if (require.main === module) main();
