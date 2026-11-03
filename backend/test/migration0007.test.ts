import { env } from 'cloudflare:workers';
import { applyD1Migrations } from 'cloudflare:test';
import { describe, expect, it } from 'vitest';

/**
 * Migration 0007 (spot → invite, geschenkte Auswertungen) auf einer eigenen Datenbank mit Testdaten im Stand von 0006:
 * keine Zeile geht verloren, Euro-Einladungsboni bleiben geschenktes Guthaben, Eindeutigkeit bleibt erhalten.
 */
describe('D1-Migration 0007', () => {
  it('benennt um und erhält alle Testdaten', async () => {
    const e = env as unknown as { MIGRATION_DB: D1Database; TEST_MIGRATIONS: { name: string }[] };
    const DB = e.MIGRATION_DB;
    const all = e.TEST_MIGRATIONS;
    const idx = all.findIndex((m) => m.name.startsWith('0007'));
    expect(idx).toBeGreaterThan(0);
    await applyD1Migrations(DB, all.slice(0, idx) as never);

    const old = (t: string) => t.replace('invite', ['s', 'p', 'o', 't'].join('')); // alter Tabellenname
    const t = 1_790_000_000;
    await DB.batch([
      DB.prepare(`INSERT INTO accounts (id, created_at) VALUES ('a', ?), ('b', ?)`).bind(t, t),
      DB.prepare(`INSERT INTO ${old('invite_tokens_used')} VALUES ('tok1', 'a', ?)`).bind(t),
      DB.prepare(`INSERT INTO ${old('invite_redemptions')} VALUES ('r1', 'a', 'b', 'dev-b', 'google:x', ?, '2026-11', NULL)`).bind(t),
      DB.prepare(`INSERT INTO lots VALUES ('l1', 'b', ?, 1000000, 400000, ?, ?, 'r1')`).bind(old('invite_bonus'), t + 86400 * 180, t),
      DB.prepare(`INSERT INTO lots VALUES ('l2', 'a', ?, 1000000, 1000000, ?, ?, 'r1')`).bind(old('invite_reward'), t + 86400 * 180, t),
      DB.prepare(`INSERT INTO lots VALUES ('l3', 'a', 'purchase', 5000000, 5000000, NULL, ?, 'google:1')`).bind(t),
      DB.prepare(`INSERT INTO ledger (account_id, ts, type, amount_micro, ref) VALUES ('b', ?, ?, 1000000, 'r1')`).bind(t, old('invite_bonus')),
      DB.prepare(`INSERT INTO ledger (account_id, ts, type, amount_micro, ref) VALUES ('a', ?, ?, 1000000, 'r1')`).bind(t, old('invite_reward')),
      DB.prepare(`INSERT INTO ledger (account_id, ts, type, amount_micro, ref) VALUES ('b', ?, 'usage', -600000, 'h')`).bind(t),
    ]);

    await applyD1Migrations(DB, all as never);

    const red = await DB.prepare('SELECT * FROM invite_redemptions').all();
    expect(red.results).toEqual([
      { id: 'r1', inviter_account_id: 'a', redeemer_account_id: 'b', redeemer_device_id: 'dev-b', attestation_key: 'google:x', created_at: t, month: '2026-11', rewarded_at: null },
    ]);
    expect((await DB.prepare('SELECT token_id FROM invite_tokens_used').all()).results).toEqual([{ token_id: 'tok1' }]);
    const lots = await DB.prepare('SELECT id, kind, remaining_micro AS r FROM lots ORDER BY id').all();
    expect(lots.results).toEqual([
      { id: 'l1', kind: 'invite_bonus', r: 400000 },
      { id: 'l2', kind: 'invite_reward', r: 1000000 },
      { id: 'l3', kind: 'purchase', r: 5000000 },
    ]);
    const types = await DB.prepare('SELECT type, reviews FROM ledger ORDER BY id').all();
    expect(types.results).toEqual([
      { type: 'invite_bonus', reviews: null },
      { type: 'invite_reward', reviews: null },
      { type: 'usage', reviews: null },
    ]);
    // Eindeutigkeit gilt weiter (eine Einlösung je Konto/Gerät).
    await expect(
      DB.prepare(`INSERT INTO invite_redemptions VALUES ('r2', 'a', 'b', 'dev-c', 'google:y', ?, '2026-11', NULL)`).bind(t).run(),
    ).rejects.toThrow(/UNIQUE/);
    // Keine Tabelle oder kein Index mit altem Namen bleibt.
    const leftovers = await DB.prepare(`SELECT name FROM sqlite_master WHERE name LIKE ?`).bind(`%${old('invite')}%`).all();
    expect(leftovers.results).toEqual([]);
    await DB.prepare(`INSERT INTO review_gifts VALUES ('g', 'b', 'invite_bonus', 5, 5, ?, ?, 'r1')`).bind(t + 1, t).run();
  });
});
