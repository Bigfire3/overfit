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
