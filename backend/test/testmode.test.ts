import { describe, expect, it } from 'vitest';
import { Client, baseEnv, rawFetch } from './helpers';

const ADMIN = 'admin-secret-0123456789';
// Echter Testbetrieb: kein Dev-Modus, keine Mocks, nur DEPLOY_MODE=test.
const testEnv = (extra = {}) =>
  baseEnv({ DEV_MODE: 'false', PURCHASE_MODE: 'required', DEPLOY_MODE: 'test', ADMIN_SECRET: ADMIN, ...extra });

const credit = (e: ReturnType<typeof baseEnv>, body: unknown, secret: string | null = ADMIN) =>
  rawFetch('/v1/admin/credit', e, {
    method: 'POST',
    headers: { 'content-type': 'application/json', ...(secret ? { authorization: `Bearer ${secret}` } : {}) },
    body: JSON.stringify(body),
  });

describe('Testbetrieb (DEPLOY_MODE=test)', () => {
  it('/v1/config markiert den Modus; Produktion sagt production', async () => {
    expect((await rawFetch('/v1/config', testEnv())).body.mode).toBe('test');
    expect((await rawFetch('/v1/config', baseEnv({ DEV_MODE: 'false' }))).body.mode).toBe('production');
    // Nur der exakte Wert „test“ zählt
    expect((await rawFetch('/v1/config', baseEnv({ DEV_MODE: 'false', DEPLOY_MODE: 'TEST' }))).body.mode).toBe('production');
  });

  it('Kein Startguthaben: neue Konten starten mit 0 €, auch im Testbetrieb (Fabian, 7.11.2026)', async () => {
    const c = await Client.create(testEnv());
    const acc = (await c.call('GET', '/v1/account')).body;
    expect(acc.balanceMicro).toBe(0);
    expect(acc.giftReviews).toBeUndefined();
    expect(acc.invite).toBeUndefined();
    expect((await c.call('GET', '/v1/account/ledger')).body.entries).toEqual([]);
    const p = await Client.create(baseEnv({ DEV_MODE: 'false' }));
    expect((await p.call('GET', '/v1/account')).body.balanceMicro).toBe(0);
  });

  it('Test-Gutschrift läuft nie ab', async () => {
    const e = testEnv();
    const a = await Client.create(e);
    expect((await credit(e, { amountMicro: 3_000_000, all: true })).status).toBe(200);
    const acc = (await a.call('GET', '/v1/account')).body;
    expect(acc.balanceMicro).toBe(3_000_000);
    expect(acc.expiring).toEqual([]);
    const lots = await e.DB.prepare('SELECT expires_at FROM lots').all<{ expires_at: number | null }>();
    expect(lots.results.map((l) => l.expires_at)).toEqual([null]);
  });

  it('Admin-Gutschrift: nur mit Geheimnis, nur Testbetrieb, begrenzt', async () => {
    const e = testEnv();
    const a = await Client.create(e);
    const b = await Client.create(e);
    expect((await credit(e, { amountMicro: 1, all: true }, null)).status).toBe(401);
    expect((await credit(e, { amountMicro: 1, all: true }, 'falsch-falsch-falsch')).status).toBe(401);
    expect((await credit(e, { amountMicro: 0, all: true })).status).toBe(400);
    expect((await credit(e, { amountMicro: 99_000_000, all: true })).status).toBe(400);
    expect((await credit(e, { amountMicro: 5_000_000 })).status).toBe(400);
    const r = await credit(e, { amountMicro: 5_000_000, all: true });
    expect(r.status).toBe(200);
    expect(r.body).toMatchObject({ accounts: 2, credited: 2, totalMicro: 10_000_000 });
    expect((await a.call('GET', '/v1/account')).body.balanceMicro).toBe(5_000_000);
    const accountId = (await b.call('GET', '/v1/account')).body.accountId;
    expect((await credit(e, { amountMicro: 2_000_000, accountId })).body.totalMicro).toBe(2_000_000);
    expect((await b.call('GET', '/v1/account')).body.balanceMicro).toBe(7_000_000);
    // Obergrenze für alles Test-Guthaben (20 €): 10 + 2 = 12 vergeben, mehr als 8 geht nicht
    const rest = await credit(e, { amountMicro: 10_000_000, all: true });
    expect(rest.body.totalMicro).toBe(8_000_000);
    expect((await credit(e, { amountMicro: 1_000_000, all: true })).body.totalMicro).toBe(0);
  });

  it('Ohne Testbetrieb oder ohne Geheimnis gibt es den Endpunkt nicht', async () => {
    expect((await credit(baseEnv({ DEV_MODE: 'false', ADMIN_SECRET: ADMIN }), { amountMicro: 1, all: true })).status).toBe(404);
    expect((await credit(testEnv({ ADMIN_SECRET: undefined }), { amountMicro: 1, all: true })).status).toBe(404);
    expect((await credit(testEnv({ ADMIN_SECRET: 'kurz' }), { amountMicro: 1, all: true }, 'kurz')).status).toBe(404);
  });

  it('Einladungen gibt es nicht mehr: alle /v1/invite-Pfade antworten 404, auch im Testbetrieb', async () => {
    for (const e of [testEnv(), baseEnv({ DEV_MODE: 'false' }), baseEnv()]) {
      const c = await Client.create(e);
      for (const path of ['/v1/invite/token', '/v1/invite/challenge', '/v1/invite/redeem']) {
        const r = await c.call('POST', path, path.endsWith('redeem') ? { token: 'x', attestation: { platform: 'google', token: `test:${c.deviceId}`, challenge: 'y' } } : undefined);
        expect(r.status).toBe(404);
      }
    }
  });
});
