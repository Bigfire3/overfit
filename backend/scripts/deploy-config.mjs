#!/usr/bin/env node
// Hilfsskript für .github/workflows/backend-deploy.yml (läuft auch lokal).
//
//   node scripts/deploy-config.mjs names <test|production>              → JSON mit Worker-, Datenbank-, Bucket- und Gateway-Namen
//   node scripts/deploy-config.mjs config <test|production> <db-id>     → schreibt wrangler.generated.jsonc (nicht committen)
//
// Test- und Produktionsbetrieb sind getrennte Instanzen (eigener Worker, eigene Datenbank, eigener Bucket):
// Testdaten und Test-Guthaben können so nie in die Produktion geraten. Die `database_id` steht nur in der
// erzeugten Datei, nie in wrangler.jsonc.
import { readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');

export const NAMES = {
  production: { worker: 'overfit-backend', db: 'overfit', bucket: 'overfit-chat-photos', gateway: 'overfit' },
  test: { worker: 'overfit-backend-test', db: 'overfit-test', bucket: 'overfit-chat-photos-test', gateway: 'overfit-test' },
};

/** Zusätzliche `vars` je Modus. Testbetrieb: niedrige Limits, kein Dev-Modus, kein Startguthaben (Test-Guthaben nur per `credit_eur`). */
export const MODE_VARS = {
  production: { DEPLOY_MODE: 'production' },
  test: {
    DEPLOY_MODE: 'test',
    MAX_TOKENS_CAP: '2048',
    RL_MESSAGES_PER_MIN: '5',
    RL_REGISTER_PER_HOUR_IP: '10',
    TEST_CREDIT_CAP_MICRO: '20000000',
  },
};

/** wrangler.jsonc hat nur ganze Kommentarzeilen; die werden entfernt. */
export function readJsonc(path) {
  const text = readFileSync(path, 'utf8')
    .split('\n')
    .filter((l) => !/^\s*\/\//.test(l))
    .join('\n');
  return JSON.parse(text);
}

/**
 * Web-Kauf aus Repo-Variablen: `WEB_PURCHASE_CHANNELS` (sideload,store) steuert, wo die App den Knopf zeigt;
 * `STRIPE_INVOICES=on` erzeugt zu jedem Kauf eine Stripe-Rechnung (kostet extra). Test- oder Live-Zahlungen ergeben sich
 * aus dem Stripe-Schlüssel (der Worker nimmt im Testbetrieb nur sk_test_…, in Produktion nur sk_live_…).
 */
export function webVars(mode, env) {
  const v = {};
  if ((env.STRIPE_INVOICES ?? '').trim() === 'on') v.STRIPE_INVOICES = 'on';
  const ch = (env.WEB_PURCHASE_CHANNELS ?? '').trim();
  if (/^(sideload|store)(,(sideload|store))*$/.test(ch)) v.WEB_PURCHASE_CHANNELS = ch;
  return v;
}

export function buildConfig(mode, databaseId, base = readJsonc(join(root, 'wrangler.jsonc')), env = process.env) {
  const n = NAMES[mode];
  if (!n) throw new Error(`Unbekannter Modus: ${mode} (test | production)`);
  if (!/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/.test(databaseId ?? '')) throw new Error(`Ungültige database_id: ${databaseId}`);
  const cfg = structuredClone(base);
  cfg.name = n.worker;
  cfg.main = join(root, 'src/index.ts');
  cfg.d1_databases = [{ ...base.d1_databases[0], database_name: n.db, database_id: databaseId, migrations_dir: join(root, 'migrations') }];
  cfg.r2_buckets = [{ ...base.r2_buckets[0], bucket_name: n.bucket }];
  // Workers AI (Voreinstellung „gratis“) und AI-Gateway-URLs; nur im Deploy, damit lokale Tests kein Cloudflare-Konto brauchen.
  cfg.ai = { binding: 'AI' };
  cfg.vars = { ...base.vars, ...MODE_VARS[mode], AI_GATEWAY_ID: n.gateway, ...webVars(mode, env) };
  return cfg;
}

const [cmd, mode, id] = process.argv.slice(2);
if (cmd === 'names') {
  if (!NAMES[mode]) throw new Error(`Unbekannter Modus: ${mode}`);
  console.log(JSON.stringify(NAMES[mode]));
} else if (cmd === 'config') {
  writeFileSync(join(root, 'wrangler.generated.jsonc'), JSON.stringify(buildConfig(mode, id), null, 2) + '\n');
  console.log(`wrangler.generated.jsonc für ${NAMES[mode].worker} geschrieben`);
} else if (process.argv[1] === fileURLToPath(import.meta.url)) {
  console.error('Aufruf: deploy-config.mjs names|config <test|production> [database-id]');
  process.exit(2);
}
