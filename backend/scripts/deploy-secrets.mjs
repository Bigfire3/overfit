#!/usr/bin/env node
// Hilfsskript für .github/workflows/backend-deploy.yml: baut aus den GitHub-Secrets (Umgebungsvariablen) und den
// bereits im Worker vorhandenen Secrets die Datei für `wrangler secret bulk`.
//
//   node scripts/deploy-secrets.mjs <test|production> <vorhandene-secrets.json> <ausgabe.json> <zusammenfassung.md>
//
// - KI-Anbieter (llm-routing.jsonc): Schlüssel je Anbieter (ANTHROPIC_API_KEY, OPENAI_API_KEY, GEMINI_API_KEY, OPENROUTER_API_KEY),
//   aus GitHub oder schon im Worker; Workers AI braucht keinen. Die Voreinstellung kommt aus der Auswahlliste des Workflows
//   (LLM_PRESET_INPUT, wird im Worker als LLM_PRESET gespeichert), sonst aus der Repo-Variable LLM_PRESET, sonst bleibt die im
//   Worker gespeicherte, sonst `default` der Datei. Fehlt ein Schlüssel der gewählten Voreinstellung: Warnung, der Worker nimmt
//   die nächste vollständige aus `fallbackOrder`. Ist keine vollständig: Exit 1 mit Anleitung.
// - SYNC_ENCRYPTION_KEY: GitHub-Secret, sonst bereits im Worker (bleibt unangetastet), sonst wird einmalig
//   ein Zufallswert erzeugt. SYNC_ENCRYPTION_KEY wird nie überschrieben, wenn er schon im Worker steht und kein GitHub-Secret gesetzt ist.
//   Einladungen gibt es nicht mehr (7.11.2026): INVITE_SIGNING_KEY wird nicht mehr gebraucht.
// - ADMIN_SECRET (nur Testbetrieb): wird bei jedem Lauf neu erzeugt, außer ein GitHub-Secret ist gesetzt; der Workflow nutzt ihn sofort.
// - Web-Kauf (Stripe): STRIPE_SECRET_KEY (sk_test_… im Testbetrieb, sk_live_… in Produktion), STRIPE_WEBHOOK_SECRET (whsec_…).
//   In Produktion haben STRIPE_LIVE_SECRET_KEY / STRIPE_LIVE_WEBHOOK_SECRET Vorrang (Test- und Live-Werte parallel in GitHub).
//   Optional; fehlt einer (weder in GitHub noch im Worker) oder passt der Schlüssel nicht zum Modus, bleibt der Web-Kauf aus,
//   mit Hinweis in der Zusammenfassung (kein Abbruch).
// - AI_GATEWAY_TOKEN (optional): Token für das authentifizierte AI Gateway; ohne gehen Anfragen direkt an Anthropic.
// - Optional (Play/Apple-Prüfer): nur gesetzt, wenn vorhanden; sonst Hinweis in der Zusammenfassung.
import { randomBytes } from 'node:crypto';
import { readFileSync, writeFileSync, appendFileSync } from 'node:fs';

import ROUTING_JSON from '../src/routing.gen.json' with { type: 'json' };

/** Routing-Tabelle (erzeugt aus llm-routing.jsonc). */
export const ROUTING = ROUTING_JSON;
export const PROVIDER_SECRETS = { anthropic: 'ANTHROPIC_API_KEY', openai: 'OPENAI_API_KEY', gemini: 'GEMINI_API_KEY', openrouter: 'OPENROUTER_API_KEY', 'workers-ai': null };
const TIERS = ['lite', 'standard', 'pro'];

/** Fehlende Secrets der ersten Modelle einer Voreinstellung (gleiche Regel wie src/routing.ts `presetComplete`). */
export function missingFor(preset, ready, routing = ROUTING) {
  const p = routing.presets[preset];
  if (!p) return null;
  const out = new Set();
  for (const t of TIERS) {
    const s = PROVIDER_SECRETS[routing.models[p.tiers[t].model].provider];
    if (s && !ready.has(s)) out.add(s);
  }
  return [...out];
}

/** Welche Voreinstellung der Worker nutzen wird (wie src/routing.ts `choosePreset`). */
export function choosePreset(wanted, ready, routing = ROUTING) {
  const requested = routing.presets[wanted] ? wanted : routing.default;
  const missing = missingFor(requested, ready, routing);
  if (missing.length === 0) return { name: requested, requested, missing };
  const alt = routing.fallbackOrder.find((n) => missingFor(n, ready, routing).length === 0);
  return { name: alt ?? null, requested, missing };
}

export const STRIPE = ['STRIPE_SECRET_KEY', 'STRIPE_WEBHOOK_SECRET'];

export const OPTIONAL = [
  'GOOGLE_PLAY_SERVICE_ACCOUNT', 'GOOGLE_PLAY_PACKAGE_NAME',
  'APPLE_IAP_KEY', 'APPLE_IAP_KEY_ID', 'APPLE_IAP_ISSUER_ID', 'APPLE_BUNDLE_ID',
];

const b64u = (b) => b.toString('base64').replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
export const generators = {
  SYNC_ENCRYPTION_KEY: () => b64u(randomBytes(32)),
  ADMIN_SECRET: () => randomBytes(32).toString('hex'),
};

/** Reine Funktion: Umgebung + vorhandene Secret-Namen → { secrets, notes, errors, generated }. */
export function plan(mode, env, existing) {
  const have = new Set(existing);
  const secrets = {};
  const notes = [];
  const errors = [];
  const warnings = [];
  const generated = [];
  let llm = null;
  const given = (n) => (env[n] ?? '').trim();

  // KI: Schlüssel je Anbieter übertragen (nur wenn in GitHub gesetzt); vorhandene im Worker bleiben.
  const ready = new Set();
  for (const n of Object.values(PROVIDER_SECRETS).filter(Boolean)) {
    if (given(n)) { secrets[n] = given(n); ready.add(n); }
    else if (have.has(n)) ready.add(n);
  }
  // Voreinstellung: Auswahlliste (wird im Worker gespeichert) → Repo-Variable → im Worker gespeichert → default.
  const input = given('LLM_PRESET_INPUT');
  const fromInput = input && input !== 'wie-bisher';
  const variable = given('LLM_PRESET_VAR');
  let wanted = ROUTING.default;
  let source = 'Voreinstellung der Datei';
  if (fromInput) { wanted = input; source = 'Auswahl beim Start'; }
  else if (variable) { wanted = variable; source = 'Repo-Variable `LLM_PRESET`'; }
  else if (have.has('LLM_PRESET')) { wanted = null; source = 'im Worker gespeichert'; }
  if (wanted !== null) {
    if (!ROUTING.presets[wanted]) {
      errors.push('LLM_PRESET');
      notes.push(`Voreinstellung \`${wanted}\` gibt es nicht (vorhanden: ${Object.keys(ROUTING.presets).map((n) => `\`${n}\``).join(', ')}).`);
    } else {
      secrets.LLM_PRESET = wanted;
      const c = choosePreset(wanted, ready);
      llm = { ...c, source };
      if (!c.name) errors.push('AI_KEY');
      else if (c.name !== wanted) {
        warnings.push(`KI-Anbieter: „${wanted}“ (${source}) ist unvollständig, es fehlt ${c.missing.map((n) => `\`${n}\``).join(', ')}. ` +
          `Der Server nutzt stattdessen **„${c.name}“**, bis der Schlüssel als GitHub-Secret eingetragen und neu deployt ist.`);
      }
    }
  } else {
    // Gespeicherte Wahl ist hier unbekannt (Secrets sind nicht lesbar): mindestens eine Voreinstellung muss vollständig sein.
    const any = Object.keys(ROUTING.presets).find((n) => missingFor(n, ready).length === 0);
    if (!any) errors.push('AI_KEY');
    llm = { name: null, requested: null, missing: [], source };
  }
  for (const n of ['SYNC_ENCRYPTION_KEY']) {
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
  // AI Gateway (Protokolle, Kosten): neue Gateways verlangen immer ein Token. Ohne Token gehen Anfragen direkt an Anthropic.
  if (given('AI_GATEWAY_TOKEN')) secrets.AI_GATEWAY_TOKEN = given('AI_GATEWAY_TOKEN');
  else if (have.has('AI_GATEWAY_TOKEN')) notes.push('`AI_GATEWAY_TOKEN`: bleibt wie im Worker gesetzt.');
  else notes.push('`AI_GATEWAY_TOKEN` fehlt (optional): Anfragen gehen direkt an den KI-Anbieter, nicht über das AI Gateway ' +
    '(keine Gateway-Protokolle für diese Stufen, Auswertungen funktionieren). Anleitung: backend/README.md → „AI Gateway“.');
  const skipped = [];
  for (const n of OPTIONAL) {
    if (given(n)) secrets[n] = given(n);
    else if (!have.has(n)) skipped.push(n);
  }
  if (skipped.length) {
    notes.push(`Optional übersprungen (kein GitHub-Secret gesetzt): ${skipped.map((n) => `\`${n}\``).join(', ')}. ` +
      'Das sind die Store-Prüfer für echte Käufe; sie brauchen Play-/Apple-Konten. Im Testbetrieb nicht nötig.');
  }
  return { secrets, notes, errors, warnings, generated, webPurchase, llm };
}

const MISSING_HELP = {
  AI_KEY: 'Kein KI-Anbieter vollständig: Für die gewählte Voreinstellung fehlt ein Schlüssel, und keine andere ist vollständig. ' +
    'Je Anbieter: **`ANTHROPIC_API_KEY`** (console.anthropic.com → API Keys, `sk-ant-…`), **`OPENAI_API_KEY`** (platform.openai.com → API keys), ' +
    '**`GEMINI_API_KEY`** (aistudio.google.com → Get API key), **`OPENROUTER_API_KEY`** (openrouter.ai → Keys). Workers AI (Voreinstellung `gratis`) ' +
    'braucht keinen. Am besten mit Ausgabenlimit beim Anbieter. Anleitung: docs/anbieter-umstellen.md.',
  LLM_PRESET: 'Unbekannte KI-Voreinstellung (Repo-Variable `LLM_PRESET` oder Auswahl): erlaubt sind die Namen unter `presets` in backend/llm-routing.jsonc.',
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
  for (const w of r.warnings) console.log(`::warning::${w.replace(/[`*]/g, '')}`);
  for (const [k, v] of Object.entries(r.secrets)) if (k !== 'LLM_PRESET') for (const line of String(v).split('\n')) if (line.trim().length > 6) console.log(`::add-mask::${line.trim()}`);
  writeFileSync(outFile, JSON.stringify(r.secrets));
  const llmLines = ['### KI-Anbieter', '',
    ...r.warnings.map((w) => `> ⚠️ ${w}`), ...(r.warnings.length ? [''] : []),
    r.llm?.name ? `- Voreinstellung: **${r.llm.name}** (${r.llm.source}${r.llm.name !== r.llm.requested ? `, gewünscht war „${r.llm.requested}“` : ''}) – ${ROUTING.presets[r.llm.name].description}`
      : `- Voreinstellung: ${r.llm?.source ?? 'unbekannt'} (Details unten unter „Gesundheitsprüfung“)`,
    ''];
  appendFileSync(summaryFile, llmLines.join('\n') + '\n');
  appendFileSync(summaryFile, ['### Secrets', '', ...r.notes.map((n) => `- ${n}`), `- Übertragen: ${Object.keys(r.secrets).map((n) => `\`${n}\``).join(', ') || '–'}`, ''].join('\n') + '\n');
  if (r.secrets.ADMIN_SECRET) writeFileSync(`${outFile}.admin`, r.secrets.ADMIN_SECRET);
}
