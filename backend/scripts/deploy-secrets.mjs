#!/usr/bin/env node
// Hilfsskript für .github/workflows/backend-deploy.yml: baut aus den GitHub-Secrets (Umgebungsvariablen) und den
// bereits im Worker vorhandenen Secrets die Datei für `wrangler secret bulk`.
//
//   node scripts/deploy-secrets.mjs <test|production> <vorhandene-secrets.json> <ausgabe.json> <zusammenfassung.md>
//
// - KI-Schlüssel: GEMINI_API_KEY (Stufe standard) und ANTHROPIC_API_KEY (Stufe pro), je aus GitHub oder schon im Worker.
//   Mindestens einer ist Pflicht, sonst Exit 1 mit Anleitung. Fehlt einer, ist die Stufe aus /v1/config nicht verfügbar.
// - INVITE_SIGNING_KEY, SYNC_ENCRYPTION_KEY: GitHub-Secret, sonst bereits im Worker (bleibt unangetastet), sonst wird einmalig
//   ein Zufallswert erzeugt. Fehlt INVITE_SIGNING_KEY, wird das alte GitHub-Secret (LEGACY_INVITE_SIGNING_KEY) übernommen. SYNC_ENCRYPTION_KEY wird nie überschrieben, wenn er schon im Worker steht und kein GitHub-Secret gesetzt ist.
// - ADMIN_SECRET (nur Testbetrieb): wird bei jedem Lauf neu erzeugt, außer ein GitHub-Secret ist gesetzt; der Workflow nutzt ihn sofort.
// - Web-Kauf (Stripe): STRIPE_SECRET_KEY (sk_test_… im Testbetrieb, sk_live_… in Produktion), STRIPE_WEBHOOK_SECRET (whsec_…).
//   In Produktion haben STRIPE_LIVE_SECRET_KEY / STRIPE_LIVE_WEBHOOK_SECRET Vorrang (Test- und Live-Werte parallel in GitHub).
//   Optional; fehlt einer (weder in GitHub noch im Worker) oder passt der Schlüssel nicht zum Modus, bleibt der Web-Kauf aus,
//   mit Hinweis in der Zusammenfassung (kein Abbruch).
// - AI_GATEWAY_TOKEN (optional): Token für das authentifizierte AI Gateway; ohne gehen Gemini/Anthropic direkt.
// - Optional (Play/Apple-Prüfer): nur gesetzt, wenn vorhanden; sonst Hinweis in der Zusammenfassung.
import { randomBytes } from 'node:crypto';
import { readFileSync, writeFileSync, appendFileSync } from 'node:fs';

export const STRIPE = ['STRIPE_SECRET_KEY', 'STRIPE_WEBHOOK_SECRET'];

export const OPTIONAL = [
  'GOOGLE_PLAY_SERVICE_ACCOUNT', 'GOOGLE_PLAY_PACKAGE_NAME', 'PLAY_INTEGRITY_SERVICE_ACCOUNT',
  'APPLE_IAP_KEY', 'APPLE_IAP_KEY_ID', 'APPLE_IAP_ISSUER_ID', 'APPLE_BUNDLE_ID', 'APPLE_APP_ATTEST_TEAM_ID',
];

const b64u = (b) => b.toString('base64').replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
export const generators = {
  INVITE_SIGNING_KEY: () => randomBytes(48).toString('base64'),
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

  for (const n of ['INVITE_SIGNING_KEY', 'SYNC_ENCRYPTION_KEY']) {
    if (given(n)) secrets[n] = given(n);
    // Früherer GitHub-Secret-Name (vor Session AD), vom Workflow als LEGACY_INVITE_SIGNING_KEY durchgereicht.
    else if (n === 'INVITE_SIGNING_KEY' && given('LEGACY_INVITE_SIGNING_KEY')) {
      secrets[n] = given('LEGACY_INVITE_SIGNING_KEY');
      notes.push('`INVITE_SIGNING_KEY`: aus dem alten GitHub-Secret übernommen (darf gelöscht werden, sobald `INVITE_SIGNING_KEY` gesetzt ist).');
    }
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
  const stripeMissing = [];
  for (const n of STRIPE) {
    // Produktion nimmt bevorzugt eigene Live-Secrets (STRIPE_LIVE_…), damit Test- und Live-Schlüssel nebeneinander in GitHub liegen können.
    const live = mode === 'production' ? given(n.replace('STRIPE_', 'STRIPE_LIVE_')) : '';
    if (live) secrets[n] = live;
    else if (given(n)) secrets[n] = given(n);
    else if (!have.has(n)) stripeMissing.push(n);
  }
  let webPurchase = stripeMissing.length === 0;
  const key = secrets.STRIPE_SECRET_KEY ?? '';
  const wantLive = mode === 'production';
  if (!webPurchase) {
    notes.push(`Web-Kauf bleibt **aus**: es fehlt ${stripeMissing.map((n) => `\`${n}\``).join(', ')} ` +
      '(Stripe → Entwickler → API-Schlüssel bzw. Webhooks; Anleitung in backend/README.md → „Web-Kauf“).');
  } else if (key && !(wantLive ? /^(sk|rk)_live_/ : /^(sk|rk)_test_/).test(key)) {
    webPurchase = false;
    notes.push(`Web-Kauf bleibt **aus**: \`STRIPE_SECRET_KEY\` muss im Modus ${mode} mit \`${wantLive ? 'sk_live_' : 'sk_test_'}\` beginnen ` +
      '(Testbetrieb nur Testmodus-Schlüssel, Produktion nur Live-Schlüssel).');
  } else notes.push(`Web-Kauf (Stripe${wantLive ? '' : ', Testmodus'}): beide Secrets vorhanden.`);
  // AI Gateway (Protokolle, Kosten): neue Gateways verlangen immer ein Token. Ohne Token gehen Gemini/Anthropic direkt.
  if (given('AI_GATEWAY_TOKEN')) secrets.AI_GATEWAY_TOKEN = given('AI_GATEWAY_TOKEN');
  else if (have.has('AI_GATEWAY_TOKEN')) notes.push('`AI_GATEWAY_TOKEN`: bleibt wie im Worker gesetzt.');
  else notes.push('`AI_GATEWAY_TOKEN` fehlt (optional): Gemini und Anthropic laufen direkt, nicht über das AI Gateway ' +
    '(keine Gateway-Protokolle für diese Stufen, Auswertungen funktionieren). Anleitung: backend/README.md → „AI Gateway“.');
  const skipped = [];
  for (const n of OPTIONAL) {
    if (given(n)) secrets[n] = given(n);
    else if (!have.has(n)) skipped.push(n);
  }
  if (skipped.length) {
    notes.push(`Optional übersprungen (kein GitHub-Secret gesetzt): ${skipped.map((n) => `\`${n}\``).join(', ')}. ` +
      'Das sind die Store-Prüfer für echte Käufe und Geräteprüfung für Einladungen; sie brauchen Play-/Apple-Konten. Im Testbetrieb nicht nötig.');
  }
  return { secrets, notes, errors, generated, webPurchase };
}

const MISSING_HELP = {
  AI_KEY: 'Mindestens ein KI-Schlüssel für Guthaben: **`GEMINI_API_KEY`** (Gemini 3.6 Flash, Google AI Studio: aistudio.google.com/apikey, ' +
    'beginnt mit `AIza`, Projekt mit Abrechnung) und/oder **`ANTHROPIC_API_KEY`** (Claude Sonnet 5.5, console.anthropic.com → API Keys, beginnt mit `sk-ant-`). ' +
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
