import { describe, expect, it } from 'vitest';
import { Client, baseEnv, rawFetch } from './helpers';

const ADMIN = 'admin-secret-0123456789';
// Echter Testbetrieb: kein Dev-Modus, keine Mocks, nur DEPLOY_MODE=test.
const testEnv = (extra = {}) =>
  baseEnv({ DEV_MODE: 'false', ATTESTATION_MODE: 'required', PURCHASE_MODE: 'required', DEPLOY_MODE: 'test', ADMIN_SECRET: ADMIN, SPOT_SIGNING_KEY: 'k'.repeat(40), ...extra });

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

  it('Neues Konto bekommt Test-Startguthaben, Produktion nicht', async () => {
    const c = await Client.create(testEnv());
    expect((await c.call('GET', '/v1/account')).body.balanceMicro).toBe(1_000_000);
    expect((await c.call('GET', '/v1/account/ledger')).body.entries[0].type).toBe('test_credit');
    const p = await Client.create(baseEnv({ DEV_MODE: 'false' }));
    expect((await p.call('GET', '/v1/account')).body.balanceMicro).toBe(0);
  });

  it('Admin-Gutschrift: nur mit Geheimnis, nur Testbetrieb, begrenzt', async () => {
    const e = testEnv({ TEST_STARTER_MICRO: '0' });
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

  it('Einladung geht ohne Geräteprüfung mit Test-Token, genau einmal je Gerät', async () => {
    const e = testEnv({ TEST_STARTER_MICRO: '0' });
    const inviter = await Client.create(e);
    const friend = await Client.create(e);
    const go = async (who: Client, token?: string) => {
      const t = (await inviter.call('POST', '/v1/spot/token')).body.token;
      const ch = (await who.call('POST', '/v1/spot/challenge')).body.challenge;
      return who.call('POST', '/v1/spot/redeem', {
        token: t,
        attestation: { platform: 'google', token: token ?? `test:${who.deviceId}`, challenge: ch },
      });
    };
    const ok = await go(friend);
    expect(ok.status).toBe(200);
    expect(ok.body.grantedMicro).toBe(1_000_000);
    expect((await go(await Client.create(e), `test:${friend.deviceId}`)).body.error.code).toBe('spot_device_used');
    expect((await go(await Client.create(e), 'mock:x')).body.error.code).toBe('attestation_failed');
  });

  it('Produktion akzeptiert keine Test-Token', async () => {
    const e = baseEnv({ DEV_MODE: 'false', SPOT_SIGNING_KEY: 'k'.repeat(40) });
    const inviter = await Client.create(e);
    const friend = await Client.create(e);
    const t = (await inviter.call('POST', '/v1/spot/token')).body.token;
    const ch = (await friend.call('POST', '/v1/spot/challenge')).body.challenge;
    const r = await friend.call('POST', '/v1/spot/redeem', { token: t, attestation: { platform: 'google', token: `test:${friend.deviceId}`, challenge: ch } });
    expect(r.body.error.code).toBe('attestation_unavailable');
  });
});
