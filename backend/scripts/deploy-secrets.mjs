#!/usr/bin/env node
// Hilfsskript für .github/workflows/backend-deploy.yml: baut aus den GitHub-Secrets (Umgebungsvariablen) und den
// bereits im Worker vorhandenen Secrets die Datei für `wrangler secret bulk`.
//
//   node scripts/deploy-secrets.mjs <test|production> <vorhandene-secrets.json> <ausgabe.json> <zusammenfassung.md>
//
// - KI-Schlüssel: GEMINI_API_KEY (Stufe standard) und ANTHROPIC_API_KEY (Stufe pro), je aus GitHub oder schon im Worker.
//   Mindestens einer ist Pflicht, sonst Exit 1 mit Anleitung. Fehlt einer, ist die Stufe aus /v1/config nicht verfügbar.
// - SPOT_SIGNING_KEY, SYNC_ENCRYPTION_KEY: GitHub-Secret, sonst bereits im Worker (bleibt unangetastet), sonst wird einmalig
//   ein Zufallswert erzeugt. SYNC_ENCRYPTION_KEY wird nie überschrieben, wenn er schon im Worker steht und kein GitHub-Secret gesetzt ist.
// - ADMIN_SECRET (nur Testbetrieb): wird bei jedem Lauf neu erzeugt, außer ein GitHub-Secret ist gesetzt; der Workflow nutzt ihn sofort.
// - Web-Kauf (Paddle): PADDLE_API_KEY, PADDLE_WEBHOOK_SECRET, PADDLE_CLIENT_TOKEN. Optional; fehlt einer (weder in GitHub
//   noch im Worker), bleibt der Web-Kauf aus, mit Hinweis in der Zusammenfassung (kein Abbruch).
// - Optional (Play/Apple-Prüfer): nur gesetzt, wenn vorhanden; sonst Hinweis in der Zusammenfassung.
import { randomBytes } from 'node:crypto';
import { readFileSync, writeFileSync, appendFileSync } from 'node:fs';

export const PADDLE = ['PADDLE_API_KEY', 'PADDLE_WEBHOOK_SECRET', 'PADDLE_CLIENT_TOKEN'];

export const OPTIONAL = [
  'GOOGLE_PLAY_SERVICE_ACCOUNT', 'GOOGLE_PLAY_PACKAGE_NAME', 'PLAY_INTEGRITY_SERVICE_ACCOUNT',
  'APPLE_IAP_KEY', 'APPLE_IAP_KEY_ID', 'APPLE_IAP_ISSUER_ID', 'APPLE_BUNDLE_ID', 'APPLE_APP_ATTEST_TEAM_ID',
];

const b64u = (b) => b.toString('base64').replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
export const generators = {
  SPOT_SIGNING_KEY: () => randomBytes(48).toString('base64'),
  SYNC_ENCRYPTION_KEY: () => b64u(randomBytes(32)),
  ADMIN_SECRET: () => randomBytes(32).toString('hex'),
};

/** Reine Funktion: Umgebung + vorhandene Secret-Namen → { secrets, notes, errors, generated }. */
export function plan(mode, env, existing) {
  const have = new Set(existing);
  const secrets = {};
  const notes = [];
  const errors = [];
  const generated = [];
  const given = (n) => (env[n] ?? '').trim();

  const aiKeys = { GEMINI_API_KEY: 'standard (Gemini)', ANTHROPIC_API_KEY: 'pro (Claude)' };
  const missingAi = [];
  for (const [n, tier] of Object.entries(aiKeys)) {
    if (given(n)) secrets[n] = given(n);
    else if (have.has(n)) notes.push(`\`${n}\`: bleibt wie im Worker gesetzt.`);
    else missingAi.push([n, tier]);
  }
  if (missingAi.length === 2) errors.push('AI_KEY');
  else for (const [n, tier] of missingAi) notes.push(`\`${n}\` fehlt: Stufe ${tier} ist nicht verfügbar (die App blendet sie aus).`);

  for (const n of ['SPOT_SIGNING_KEY', 'SYNC_ENCRYPTION_KEY']) {
    if (given(n)) secrets[n] = given(n);
    else if (have.has(n)) notes.push(`\`${n}\`: bleibt wie im Worker gesetzt.`);
    else {
      secrets[n] = generators[n]();
      generated.push(n);
      notes.push(`\`${n}\`: neu erzeugt und im Worker gespeichert (musst du nicht kennen; bei \`SYNC_ENCRYPTION_KEY\` nie ändern).`);
    }
  }
  if (mode === 'test') {
    if (given('ADMIN_SECRET')) secrets.ADMIN_SECRET = given('ADMIN_SECRET');
    else {
      secrets.ADMIN_SECRET = generators.ADMIN_SECRET();
      generated.push('ADMIN_SECRET');
      notes.push('`ADMIN_SECRET`: für diesen Lauf neu erzeugt (nur Testbetrieb, für Test-Guthaben).');
    }
  }
  const paddleMissing = [];
  for (const n of PADDLE) {
    if (given(n)) secrets[n] = given(n);
    else if (!have.has(n)) paddleMissing.push(n);
  }
  const webPurchase = paddleMissing.length === 0;
  if (webPurchase) notes.push('Web-Kauf (Paddle): alle drei Secrets vorhanden.');
  else {
    notes.push(`Web-Kauf bleibt **aus**: es fehlt ${paddleMissing.map((n) => `\`${n}\``).join(', ')} ` +
      '(Paddle → Developer Tools → Authentication bzw. Notifications; Anleitung in backend/README.md → „Web-Kauf“).');
  }
  const skipped = [];
  for (const n of OPTIONAL) {
    if (given(n)) secrets[n] = given(n);
    else if (!have.has(n)) skipped.push(n);
  }
  if (skipped.length) {
    notes.push(`Optional übersprungen (kein GitHub-Secret gesetzt): ${skipped.map((n) => `\`${n}\``).join(', ')}. ` +
      'Das sind die Store-Prüfer für echte Käufe und Spot-Geräteprüfung; sie brauchen Play-/Apple-Konten. Im Testbetrieb nicht nötig.');
  }
  return { secrets, notes, errors, generated, webPurchase };
}

const MISSING_HELP = {
  AI_KEY: 'Mindestens ein KI-Schlüssel für Fuel: **`GEMINI_API_KEY`** (Stufe Standard, Google AI Studio: aistudio.google.com/apikey, ' +
    'beginnt mit `AIza`, Projekt mit Abrechnung) und/oder **`ANTHROPIC_API_KEY`** (Stufe Pro, console.anthropic.com → API Keys, beginnt mit `sk-ant-`). ' +
    'Am besten je mit Ausgabenlimit.',
};

if (process.argv[1] && process.argv[1].endsWith('deploy-secrets.mjs')) {
  const [mode, existingFile, outFile, summaryFile] = process.argv.slice(2);
  let existing = [];
  try {
    const raw = JSON.parse(readFileSync(existingFile, 'utf8'));
    existing = Array.isArray(raw) ? raw.map((s) => s.name) : [];
  } catch {
    /* Worker existiert noch nicht */
  }
  const r = plan(mode, process.env, existing);
  if (r.errors.length) {
    const msg = ['### ❌ Es fehlt ein Pflicht-Secret', '',
      ...r.errors.map((n) => `- ${MISSING_HELP[n]}`), '',
      'So trägst du es ein: GitHub → dieses Repo → **Settings → Secrets and variables → Actions → New repository secret** ' +
      '(Name exakt wie oben, Wert einfügen) → danach den Workflow erneut starten (Actions → „Backend deployen“ → Run workflow).', ''].join('\n');
    appendFileSync(summaryFile, msg);
    console.error(msg.replace(/[`*#]/g, ''));
    process.exit(1);
  }
  for (const n of r.generated) console.log(`::add-mask::${r.secrets[n]}`);
  for (const v of Object.values(r.secrets)) for (const line of String(v).split('\n')) if (line.trim().length > 6) console.log(`::add-mask::${line.trim()}`);
  writeFileSync(outFile, JSON.stringify(r.secrets));
  appendFileSync(summaryFile, ['### Secrets', '', ...r.notes.map((n) => `- ${n}`), `- Übertragen: ${Object.keys(r.secrets).map((n) => `\`${n}\``).join(', ') || '–'}`, ''].join('\n') + '\n');
  if (r.secrets.ADMIN_SECRET) writeFileSync(`${outFile}.admin`, r.secrets.ADMIN_SECRET);
}
