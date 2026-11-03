import { env } from 'cloudflare:workers';
import { applyD1Migrations } from 'cloudflare:test';
import { beforeEach } from 'vitest';

const e = env as unknown as { DB: D1Database; TEST_MIGRATIONS: never };
await applyD1Migrations(e.DB, e.TEST_MIGRATIONS);

const TABLES = ['accounts', 'devices', 'lots', 'holds', 'ledger', 'nonces', 'rate_limits', 'purchases', 'spot_tokens_used', 'spot_redemptions'];
beforeEach(async () => {
  await e.DB.batch([...TABLES].reverse().map((t) => e.DB.prepare(`DELETE FROM ${t}`)));
});
