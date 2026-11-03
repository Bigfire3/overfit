#!/usr/bin/env node
// Inhaltsfreie Nutzung und Marge als Tabelle: ADMIN_SECRET=… node scripts/usage-report.mjs <worker-url> [von] [bis]
// Liest GET /v1/admin/usage (nur Zahlen aus dem Ledger, nie Prompts, Antworten oder Fotos).
const [base, from, to] = process.argv.slice(2);
const secret = process.env.ADMIN_SECRET;
if (!base || !secret) {
  console.error('Aufruf: ADMIN_SECRET=… node scripts/usage-report.mjs https://<worker-domain> [2026-11-01] [2026-12-01]');
  process.exit(2);
}
const qs = new URLSearchParams({ ...(from ? { from } : {}), ...(to ? { to } : {}) }).toString();
const res = await fetch(`${base.replace(/\/+$/, '')}/v1/admin/usage${qs ? `?${qs}` : ''}`, { headers: { authorization: `Bearer ${secret}` } });
if (!res.ok) {
  console.error(`HTTP ${res.status}: ${await res.text()}`);
  process.exit(1);
}
const eur = (micro) => (micro == null ? '–' : (micro / 1e6).toFixed(4).replace('.', ',') + ' €');
const r = await res.json();
console.log(`Zeitraum ${new Date(r.from * 1000).toISOString().slice(0, 10)} bis ${new Date(r.to * 1000).toISOString().slice(0, 10)}`);
console.table(
  r.groups.map((g) => ({
    Modell: g.model,
    Anfragen: g.requests,
    Foto: g.photo ? 'ja' : '',
    Ausweichung: g.fallback ? 'ja' : '',
    'Token ein': g.inputTokens + g.cacheReadTokens + g.cacheWriteTokens,
    'Token aus': g.outputTokens,
    Berechnet: eur(g.chargedMicro),
    Anbieterkosten: eur(g.providerCostMicro),
    Marge: eur(g.marginMicro),
  })),
);
const t = r.totals;
console.log(`Gesamt: ${t.requests} Anfragen (${t.photoRequests} mit Foto, ${t.fallbackRequests} mit Ausweichung), berechnet ${eur(t.chargedMicro)}, Anbieterkosten ${eur(t.providerCostMicro)}, Marge ${eur(t.marginMicro)}`);

// Finanzen in echten Euro: Einnahmen je Kanal (brutto, Gebühren, netto), offenes Guthaben, KI-Kosten, Rohgewinn.
const f = r.finance;
if (f) {
  const e2 = (micro) => (micro / 1e6).toLocaleString('de-DE', { minimumFractionDigits: 2, maximumFractionDigits: 2 }) + ' €';
  console.log(`\nEinnahmen im Zeitraum (echte Euro, ${f.environment === 'live' ? 'echte Zahlungen' : 'Testmodus'}):`);
  console.table(
    f.channels.map((c) => ({ Kanal: c.label, Käufe: c.orders, Brutto: e2(c.grossMicro), 'Gebühren/USt (geschätzt)': e2(c.feesMicro), Netto: e2(c.netMicro), 'Guthaben dafür': e2(c.creditMicro) })),
  );
  console.log(`Summe: brutto ${e2(f.revenue.grossMicro)}, netto ${e2(f.revenue.netMicro)}`);
  console.log(`Offenes Guthaben (alle Konten): gekauft ${e2(f.openCredit.purchasedMicro)}, geschenkt ${e2(f.openCredit.giftedMicro)}, geschenkte Auswertungen ${f.openCredit.giftReviews}`);
  console.log(`KI-Kosten: ${e2(f.aiCost.totalMicro)} (aus Guthaben ${e2(f.aiCost.creditMicro)}, geschenkte Auswertungen ${e2(f.aiCost.giftReviewsMicro)} in ${f.aiCost.giftReviewRequests} Anfragen)` +
    (f.aiCost.unknownCostRequests ? `, ${f.aiCost.unknownCostRequests} ältere Anfragen ohne bekannte Kosten` : ''));
  console.log(`Rohgewinn (netto − KI-Kosten): ${e2(f.grossProfitMicro)}`);
  console.log(f.note);
}

// Web-Umsatz je Jahr und Rechnungsland (Kleinunternehmer, EU-Lieferschwelle). Werkzeug, keine Steuerberatung.
const w = r.webRevenue;
if (w) {
  const e2 = (micro) => (micro / 1e6).toLocaleString('de-DE', { minimumFractionDigits: 2, maximumFractionDigits: 2 }) + ' €';
  console.log(`\nWeb-Umsatz (Stripe, ${w.environment === 'live' ? 'echte Zahlungen' : 'Testmodus'}), bezahlt minus erstattet:`);
  if (!w.years.length) console.log('  noch keine Web-Verkäufe');
  for (const y of w.years) {
    console.log(`  ${y.year}: gesamt ${e2(y.totalMicro)} in ${y.orders} Käufen, davon andere EU-Länder ${e2(y.euCrossBorderMicro)}, Land unbekannt ${e2(y.unknownCountryMicro)}`);
    console.table(y.byCountry.map((c) => ({ Land: c.country, Käufe: c.orders, Umsatz: e2(c.totalMicro) })));
  }
  console.log(`Schwellen: EU-Lieferschwelle ${e2(w.thresholds.euCrossBorderMicro)} (andere EU-Länder, Vorjahr oder laufendes Jahr), ` +
    `Kleinunternehmer ${e2(w.thresholds.smallBusinessPrevYearMicro)} Vorjahr / ${e2(w.thresholds.smallBusinessCurrentYearMicro)} laufendes Jahr. Warnung ab 80 %.`);
  for (const x of w.warnings) console.log(`⚠️  ${x.message}`);
  console.log(w.note);
}
