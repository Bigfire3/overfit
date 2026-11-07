import { env } from 'cloudflare:workers';
import { applyD1Migrations } from 'cloudflare:test';
import { describe, expect, it } from 'vitest';

/** Migration 0009 (Guthaben läuft nie ab) auf einer eigenen Datenbank: Ablaufdaten fallen weg, Beträge bleiben. */
describe('D1-Migration 0009', () => {
  it('entfernt jedes Ablaufdatum, ohne Beträge zu ändern', async () => {
    const e = env as unknown as { MIGRATION_DB: D1Database; TEST_MIGRATIONS: { name: string }[] };
    const DB = e.MIGRATION_DB;
    const all = e.TEST_MIGRATIONS;
    const idx = all.findIndex((m) => m.name.startsWith('0009'));
    expect(idx).toBeGreaterThan(0);
    await applyD1Migrations(DB, all.slice(0, idx) as never);
    const t = 1_790_000_000;
    await DB.batch([
      DB.prepare(`INSERT INTO accounts (id, created_at) VALUES ('a', ?)`).bind(t),
      DB.prepare(`INSERT INTO lots VALUES ('l1', 'a', 'test_credit', 1000000, 400000, ?, ?, 'x')`).bind(t + 86400, t),
      DB.prepare(`INSERT INTO lots VALUES ('l2', 'a', 'invite_bonus', 1000000, 1000000, ?, ?, 'x')`).bind(t - 86400, t - 2 * 86400),
      DB.prepare(`INSERT INTO lots VALUES ('l3', 'a', 'purchase', 5000000, 5000000, NULL, ?, 'x')`).bind(t),
    ]);
    await applyD1Migrations(DB, all.slice(0, idx + 1) as never);
    const rows = await DB.prepare('SELECT id, remaining_micro AS r, expires_at AS e FROM lots ORDER BY id').all();
    expect(rows.results).toEqual([
      { id: 'l1', r: 400000, e: null },
      { id: 'l2', r: 1000000, e: null },
      { id: 'l3', r: 5000000, e: null },
    ]);
  });
});
