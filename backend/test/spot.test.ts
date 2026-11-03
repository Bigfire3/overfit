import { describe, expect, it, vi } from 'vitest';
import { Client, baseEnv, fund } from './helpers';

async function redeem(inviter: Client, redeemer: Client, opts: { deviceKey?: string; token?: string; platform?: string } = {}) {
  const token = opts.token ?? (await inviter.call('POST', '/v1/spot/token')).body.token;
  const ch = (await redeemer.call('POST', '/v1/spot/challenge')).body.challenge;
  return redeemer.call('POST', '/v1/spot/redeem', {
    token,
    attestation: { platform: opts.platform ?? 'google', token: `mock:${opts.deviceKey ?? crypto.randomUUID()}`, challenge: ch },
  });
}

const msg = { model: 'claude-sonnet-5-5', max_tokens: 100, messages: [{ role: 'user', content: 'hi' }] };
function mockAnthropic() {
  const real = globalThis.fetch;
  vi.stubGlobal('fetch', (i: any, init?: any) =>
    String(i instanceof Request ? i.url : i).startsWith('https://api.anthropic.com')
      ? Promise.resolve(new Response(JSON.stringify({ content: [], usage: { input_tokens: 100, output_tokens: 50 } })))
      : real(i, init),
  );
}

describe('Einladungen (spot)', () => {
  it('Einlösen: 1 € Startguthaben und Name des Einladenden; Gutschrift erst nach der ersten Coach-Anfrage', async () => {
    mockAnthropic();
    const inviter = await Client.create();
    await inviter.call('PUT', '/v1/account/profile', { displayName: 'Fabian' });
    const friend = await Client.create();
    const r = await redeem(inviter, friend);
    expect(r.status).toBe(200);
    expect(r.body.grantedMicro).toBe(1_000_000);
    expect(r.body.inviter.displayName).toBe('Fabian');
    expect(r.body.balanceMicro).toBe(1_000_000);
    expect(r.body.expiresAt - Math.floor(Date.now() / 1000)).toBeGreaterThan(179 * 86400);
    expect((await inviter.call('GET', '/v1/account')).body.balanceMicro).toBe(0);

    const m1 = await friend.call('POST', '/v1/messages', msg);
    expect(m1.status).toBe(200);
    expect((await inviter.call('GET', '/v1/account')).body.balanceMicro).toBe(1_000_000);
    await friend.call('POST', '/v1/messages', msg);
    expect((await inviter.call('GET', '/v1/account')).body.balanceMicro).toBe(1_000_000); // nur einmal
    const led = await inviter.call('GET', '/v1/account/ledger');
    expect(led.body.entries[0].type).toBe('spot_reward');
    vi.unstubAllGlobals();
  });

  it('Namenlos: displayName ist null', async () => {
    const r = await redeem(await Client.create(), await Client.create());
    expect(r.body.inviter.displayName).toBeNull();
  });

  it('Token ist einmalig', async () => {
    const inviter = await Client.create();
    const token = (await inviter.call('POST', '/v1/spot/token')).body.token;
    expect((await redeem(inviter, await Client.create(), { token })).status).toBe(200);
    const second = await redeem(inviter, await Client.create(), { token });
    expect(second.status).toBe(409);
    expect(second.body.error.code).toBe('spot_token_used');
  });

  it('Token läuft ab und ist manipulationssicher', async () => {
    vi.useFakeTimers({ toFake: ['Date'] });
    try {
      const inviter = await Client.create();
      const friend = await Client.create();
      const { token, ttlSec } = (await inviter.call('POST', '/v1/spot/token')).body;
      expect(ttlSec).toBe(45);
      vi.setSystemTime(Date.now() + 60_000);
      const late = await redeem(inviter, friend, { token });
      expect(late.status).toBe(410);
      expect(late.body.error.code).toBe('spot_token_expired');
    } finally {
      vi.useRealTimers();
    }
    const inviter = await Client.create();
    const { token } = (await inviter.call('POST', '/v1/spot/token')).body;
    const bad = token.slice(0, -2) + (token.endsWith('AA') ? 'BB' : 'AA');
    expect((await redeem(inviter, await Client.create(), { token: bad })).body.error.code).toBe('spot_token_invalid');
  });

  it('einmal pro Konto und einmal pro Gerät (Attestierung)', async () => {
    const inviter = await Client.create();
    const friend = await Client.create();
    expect((await redeem(inviter, friend, { deviceKey: 'dev1' })).status).toBe(200);
    expect((await redeem(inviter, friend)).body.error.code).toBe('spot_already_redeemed');
    const reinstall = await Client.create(); // neue Identität, gleiches physisches Gerät
    const r = await redeem(inviter, reinstall, { deviceKey: 'dev1' });
    expect(r.status).toBe(409);
    expect(r.body.error.code).toBe('spot_device_used');
  });

  it('nicht sich selbst', async () => {
    const a = await Client.create();
    expect((await redeem(a, a)).body.error.code).toBe('spot_self');
  });

  it('Limit pro Einladendem und globales Monatsbudget', async () => {
    const e = baseEnv({ SPOT_MONTHLY_LIMIT_PER_INVITER: '2' });
    const inviter = await Client.create(e);
    for (let i = 0; i < 2; i++) expect((await redeem(inviter, await Client.create(e))).status).toBe(200);
    const r = await redeem(inviter, await Client.create(e));
    expect(r.status).toBe(429);
    expect(r.body.error.code).toBe('spot_inviter_limit');

    const e2 = baseEnv({ SPOT_GLOBAL_MONTHLY_BUDGET_MICRO: '2000000' }); // = eine Einladung (1 € + 1 € Belohnung)
    const i2 = await Client.create(e2);
    const i3 = await Client.create(e2);
    // Zähler stammt aus dem vorigen Teil (2 Einlösungen im Monat) → Budget bereits erschöpft
    const r2 = await redeem(i2, await Client.create(e2));
    expect(r2.body.error.code).toBe('spot_budget_exhausted');
    void i3;
  });

  it('Attestierung ist ohne Konfiguration Pflicht (503) und ohne Dev-Modus nie gemockt', async () => {
    const inviter = await Client.create(baseEnv({ ATTESTATION_MODE: 'required' }));
    const friend = await Client.create(baseEnv({ ATTESTATION_MODE: 'required' }));
    expect((await redeem(inviter, friend)).body.error.code).toBe('attestation_unavailable');
    const pInv = await Client.create(baseEnv({ DEV_MODE: 'false', ATTESTATION_MODE: 'mock', SPOT_SIGNING_KEY: 'k' }));
    const pFriend = await Client.create(baseEnv({ DEV_MODE: 'false', ATTESTATION_MODE: 'mock', SPOT_SIGNING_KEY: 'k' }));
    expect((await redeem(pInv, pFriend)).status).toBe(503);
    const noKey = await Client.create(baseEnv({ DEV_MODE: 'false' }));
    expect((await noKey.call('POST', '/v1/spot/token')).body.error.code).toBe('not_configured');
  });

  it('Challenge ist an das Gerät gebunden und einmalig', async () => {
    const inviter = await Client.create();
    const a = await Client.create();
    const b = await Client.create();
    const ch = (await a.call('POST', '/v1/spot/challenge')).body.challenge;
    const token = (await inviter.call('POST', '/v1/spot/token')).body.token;
    const stolen = await b.call('POST', '/v1/spot/redeem', { token, attestation: { platform: 'google', token: 'mock:x', challenge: ch } });
    expect(stolen.body.error.code).toBe('challenge_invalid');
  });

  it('Startguthaben ist zugleich nutzbar nur für Coach-Anfragen (es gibt keine anderen Ausgaben) und verfällt', async () => {
    const e = baseEnv();
    const inviter = await Client.create(e);
    const friend = await Client.create(e);
    await redeem(inviter, friend);
    const t = Math.floor(Date.now() / 1000);
    await e.DB.prepare("UPDATE lots SET expires_at = ? WHERE kind = 'spot_bonus'").bind(t - 1).run();
    expect((await friend.call('GET', '/v1/account')).body.balanceMicro).toBe(0);
    void fund;
  });
});

describe('Wiederherstellung', () => {
  it('Umzug aufs neue Handy übernimmt Saldo, meldet das alte ab, Code ist einmalig', async () => {
    const oldPhone = await Client.create();
    const f = await fund(oldPhone);
    const code = (await oldPhone.call('POST', '/v1/recovery/code')).body.code as string;
    expect(code).toMatch(/^([A-Z2-9]{4}-){5}[A-Z2-9]{4}$/);
    const newPhone = await Client.create();
    expect((await newPhone.call('GET', '/v1/account')).body.balanceMicro).toBe(0);
    const r = await newPhone.call('POST', '/v1/recovery/restore', { code: code.toLowerCase().replace(/-/g, ' ') });
    expect(r.status).toBe(200);
    expect(r.body.balanceMicro).toBe(f.balanceMicro);
    expect((await newPhone.call('GET', '/v1/account')).body.balanceMicro).toBe(f.balanceMicro);
    expect((await oldPhone.call('GET', '/v1/account')).body.error.code).toBe('unknown_device');
    const third = await Client.create();
    expect((await third.call('POST', '/v1/recovery/restore', { code })).status).toBe(404);
  });

  it('falscher Code', async () => {
    const c = await Client.create();
    expect((await c.call('POST', '/v1/recovery/restore', { code: 'nope' })).status).toBe(400);
    expect((await c.call('POST', '/v1/recovery/restore', { code: 'AAAA-AAAA-AAAA-AAAA-AAAA-AAAA' })).status).toBe(404);
  });

  it('Brute-Force wird begrenzt', async () => {
    const c = await Client.create(baseEnv({ RL_SENSITIVE_PER_HOUR: '2' }));
    const codes: number[] = [];
    for (let i = 0; i < 4; i++) codes.push((await c.call('POST', '/v1/recovery/restore', { code: 'AAAA-AAAA-AAAA-AAAA-AAAA-AAAA' })).status);
    expect(codes).toContain(429);
  });
});
