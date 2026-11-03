import { afterEach, describe, expect, it, vi } from 'vitest';
import { Client, baseEnv, fund } from './helpers';

async function redeem(inviter: Client, redeemer: Client, opts: { deviceKey?: string; token?: string; platform?: string } = {}) {
  const token = opts.token ?? (await inviter.call('POST', '/v1/invite/token')).body.token;
  const ch = (await redeemer.call('POST', '/v1/invite/challenge')).body.challenge;
  return redeemer.call('POST', '/v1/invite/redeem', {
    token,
    attestation: { platform: opts.platform ?? 'google', token: `mock:${opts.deviceKey ?? crypto.randomUUID()}`, challenge: ch },
  });
}

/** Coach-Auswertung über die Stufe standard (Gemini Flash), wie die App sie schickt. */
const review = (extra: object = {}) => ({
  model: 'standard', max_tokens: 100, overfit: { purpose: 'review' }, messages: [{ role: 'user', content: 'Workout' }], ...extra,
});
const chat = { model: 'standard', max_tokens: 100, overfit: { purpose: 'chat' }, messages: [{ role: 'user', content: 'hi' }] };
let sentBodies: any[] = [];
function mockAi() {
  sentBodies = [];
  const real = globalThis.fetch;
  vi.stubGlobal('fetch', (i: any, init?: any) => {
    const url = String(i instanceof Request ? i.url : i);
    if (url.startsWith('https://generativelanguage.googleapis.com')) {
      sentBodies.push(JSON.parse(String(init?.body ?? '{}')));
      return Promise.resolve(
        Response.json({
          candidates: [{ content: { role: 'model', parts: [{ text: '{"ok":true}' }] }, finishReason: 'STOP' }],
          usageMetadata: { promptTokenCount: 8000, candidatesTokenCount: 1400, thoughtsTokenCount: 1000 },
        }),
      );
    }
    if (url.startsWith('https://api.anthropic.com')) {
      sentBodies.push(JSON.parse(String(init?.body ?? '{}')));
      return Promise.resolve(Response.json({ content: [], usage: { input_tokens: 100, output_tokens: 50 } }));
    }
    return real(i, init);
  });
}
const account = async (c: Client) => (await c.call('GET', '/v1/account')).body;

describe('Einladungen', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('Einlösen: 5 geschenkte Auswertungen und Name des Einladenden; Belohnung (5) erst nach der ersten Auswertung', async () => {
    mockAi();
    const inviter = await Client.create();
    await inviter.call('PUT', '/v1/account/profile', { displayName: 'Fabian' });
    const friend = await Client.create();
    const r = await redeem(inviter, friend);
    expect(r.status).toBe(200);
    expect(r.body.grantedReviews).toBe(5);
    expect(r.body.inviter.displayName).toBe('Fabian');
    expect(r.body.balanceMicro).toBe(0); // kein Euro-Guthaben
    expect(r.body.giftReviews.remaining).toBe(5);
    expect(r.body.expiresAt - Math.floor(Date.now() / 1000)).toBeGreaterThan(179 * 86400);
    expect((await account(inviter)).giftReviews.remaining).toBe(0);

    const m1 = await friend.call('POST', '/v1/messages', review());
    expect(m1.status).toBe(200);
    expect(m1.body.overfit.giftReview).toBe(true);
    expect(m1.body.overfit.costMicro).toBe(0);
    expect(sentBodies[0].overfit).toBeUndefined(); // nie an den Anbieter
    const fa = await account(friend);
    expect(fa.giftReviews.remaining).toBe(4);
    expect(fa.balanceMicro).toBe(0);
    expect((await account(inviter)).giftReviews.remaining).toBe(5);
    await friend.call('POST', '/v1/messages', review());
    expect((await account(inviter)).giftReviews.remaining).toBe(5); // nur einmal
    expect((await account(inviter)).balanceMicro).toBe(0);
    const led = await inviter.call('GET', '/v1/account/ledger');
    expect(led.body.entries[0]).toMatchObject({ type: 'invite_reward', amountMicro: 0, reviews: 5, category: 'gifted' });
    const fl = await friend.call('GET', '/v1/account/ledger');
    expect(fl.body.entries[0]).toMatchObject({ type: 'usage', amountMicro: 0, reviews: -1, category: 'used' });
  });

  it('Geschenkte Auswertungen gelten nur für Coach-Auswertungen mit Gemini Flash; Chat und Claude zahlen aus dem Guthaben', async () => {
    mockAi();
    const inviter = await Client.create();
    const friend = await Client.create();
    await redeem(inviter, friend);
    expect((await friend.call('POST', '/v1/messages', chat)).status).toBe(402);
    expect((await friend.call('POST', '/v1/messages', { ...chat, overfit: undefined })).status).toBe(402);
    expect((await friend.call('POST', '/v1/messages', review({ model: 'pro' }))).status).toBe(402);
    expect((await account(friend)).giftReviews.remaining).toBe(5);
    // Mit Guthaben: Chat kostet Euro, die Auswertung bleibt geschenkt.
    await fund(friend);
    const before = (await account(friend)).balanceMicro;
    const c = await friend.call('POST', '/v1/messages', chat);
    expect(c.body.overfit.giftReview).toBe(false);
    expect(c.body.overfit.costMicro).toBeGreaterThan(0);
    const rv = await friend.call('POST', '/v1/messages', review());
    expect(rv.body.overfit.giftReview).toBe(true);
    const after = await account(friend);
    expect(after.balanceMicro).toBe(before - c.body.overfit.costMicro);
    expect(after.giftReviews.remaining).toBe(4);
  });

  it('Fehlgeschlagene Auswertung gibt die geschenkte Auswertung zurück', async () => {
    const inviter = await Client.create();
    const friend = await Client.create();
    await redeem(inviter, friend);
    const real = globalThis.fetch;
    vi.stubGlobal('fetch', (i: any, init?: any) =>
      String(i instanceof Request ? i.url : i).startsWith('https://generativelanguage.googleapis.com')
        ? Promise.resolve(new Response('kaputt', { status: 400 }))
        : real(i, init),
    );
    expect((await friend.call('POST', '/v1/messages', review())).status).toBe(400);
    expect((await account(friend)).giftReviews.remaining).toBe(5);
  });

  it('Monatsgrenze der echten KI-Kosten für Geschenktes: darüber zahlt das Guthaben', async () => {
    mockAi();
    const e = baseEnv({ INVITE_GIFT_MONTHLY_COST_CAP_MICRO: '1' });
    const inviter = await Client.create(e);
    const friend = await Client.create(e);
    await redeem(inviter, friend);
    expect((await friend.call('POST', '/v1/messages', review())).status).toBe(402);
    expect((await account(friend)).giftReviews.remaining).toBe(5);
  });

  it('Geschenkte Auswertungen verfallen', async () => {
    mockAi();
    const e = baseEnv();
    const inviter = await Client.create(e);
    const friend = await Client.create(e);
    await redeem(inviter, friend);
    await e.DB.prepare('UPDATE review_gifts SET expires_at = ?').bind(Math.floor(Date.now() / 1000) - 1).run();
    expect((await account(friend)).giftReviews.remaining).toBe(0);
    expect((await friend.call('POST', '/v1/messages', review())).status).toBe(402);
  });

  it('Namenlos: displayName ist null', async () => {
    const r = await redeem(await Client.create(), await Client.create());
    expect(r.body.inviter.displayName).toBeNull();
  });

  it('Token ist einmalig', async () => {
    const inviter = await Client.create();
    const token = (await inviter.call('POST', '/v1/invite/token')).body.token;
    expect((await redeem(inviter, await Client.create(), { token })).status).toBe(200);
    const second = await redeem(inviter, await Client.create(), { token });
    expect(second.status).toBe(409);
    expect(second.body.error.code).toBe('invite_token_used');
  });

  it('Token läuft ab und ist manipulationssicher', async () => {
    vi.useFakeTimers({ toFake: ['Date'] });
    try {
      const inviter = await Client.create();
      const friend = await Client.create();
      const { token, ttlSec } = (await inviter.call('POST', '/v1/invite/token')).body;
      expect(ttlSec).toBe(45);
      vi.setSystemTime(Date.now() + 60_000);
      const late = await redeem(inviter, friend, { token });
      expect(late.status).toBe(410);
      expect(late.body.error.code).toBe('invite_token_expired');
    } finally {
      vi.useRealTimers();
    }
    const inviter = await Client.create();
    const { token } = (await inviter.call('POST', '/v1/invite/token')).body;
    const bad = token.slice(0, -2) + (token.endsWith('AA') ? 'BB' : 'AA');
    expect((await redeem(inviter, await Client.create(), { token: bad })).body.error.code).toBe('invite_token_invalid');
  });

  it('einmal pro Konto und einmal pro Gerät (Attestierung)', async () => {
    const inviter = await Client.create();
    const friend = await Client.create();
    expect((await redeem(inviter, friend, { deviceKey: 'dev1' })).status).toBe(200);
    expect((await redeem(inviter, friend)).body.error.code).toBe('invite_already_redeemed');
    const reinstall = await Client.create(); // neue Identität, gleiches physisches Gerät
    const r = await redeem(inviter, reinstall, { deviceKey: 'dev1' });
    expect(r.status).toBe(409);
    expect(r.body.error.code).toBe('invite_device_used');
  });

  it('nicht sich selbst', async () => {
    const a = await Client.create();
    expect((await redeem(a, a)).body.error.code).toBe('invite_self');
  });

  it('Limit pro Einladendem und globales Monatsbudget', async () => {
    const e = baseEnv({ INVITE_MONTHLY_LIMIT_PER_INVITER: '2' });
    const inviter = await Client.create(e);
    for (let i = 0; i < 2; i++) expect((await redeem(inviter, await Client.create(e))).status).toBe(200);
    const r = await redeem(inviter, await Client.create(e));
    expect(r.status).toBe(429);
    expect(r.body.error.code).toBe('invite_inviter_limit');

    const e2 = baseEnv({ INVITE_GLOBAL_MONTHLY_BUDGET_REVIEWS: '20' }); // = zwei Einladungen (je 5 + 5 Auswertungen)
    const i2 = await Client.create(e2);
    const i3 = await Client.create(e2);
    // Zähler stammt aus dem vorigen Teil (2 Einlösungen im Monat) → Budget bereits erschöpft
    const r2 = await redeem(i2, await Client.create(e2));
    expect(r2.body.error.code).toBe('invite_budget_exhausted');
    void i3;
  });

  it('Attestierung ist ohne Konfiguration Pflicht (503) und ohne Dev-Modus nie gemockt', async () => {
    const inviter = await Client.create(baseEnv({ ATTESTATION_MODE: 'required' }));
    const friend = await Client.create(baseEnv({ ATTESTATION_MODE: 'required' }));
    expect((await redeem(inviter, friend)).body.error.code).toBe('attestation_unavailable');
    const pInv = await Client.create(baseEnv({ DEV_MODE: 'false', ATTESTATION_MODE: 'mock', INVITE_SIGNING_KEY: 'k' }));
    const pFriend = await Client.create(baseEnv({ DEV_MODE: 'false', ATTESTATION_MODE: 'mock', INVITE_SIGNING_KEY: 'k' }));
    expect((await redeem(pInv, pFriend)).status).toBe(503);
    const noKey = await Client.create(baseEnv({ DEV_MODE: 'false' }));
    expect((await noKey.call('POST', '/v1/invite/token')).body.error.code).toBe('not_configured');
  });

  it('Challenge ist an das Gerät gebunden und einmalig', async () => {
    const inviter = await Client.create();
    const a = await Client.create();
    const b = await Client.create();
    const ch = (await a.call('POST', '/v1/invite/challenge')).body.challenge;
    const token = (await inviter.call('POST', '/v1/invite/token')).body.token;
    const stolen = await b.call('POST', '/v1/invite/redeem', { token, attestation: { platform: 'google', token: 'mock:x', challenge: ch } });
    expect(stolen.body.error.code).toBe('challenge_invalid');
  });

  it('Alte Euro-Einladungsboni bleiben geschenktes Guthaben und werden vor Gekauftem verbraucht', async () => {
    mockAi();
    const e = baseEnv();
    const c = await Client.create(e);
    await fund(c);
    const acc = (await account(c)).accountId;
    const t = Math.floor(Date.now() / 1000);
    await e.DB.prepare("INSERT INTO lots VALUES ('old-bonus', ?, 'invite_bonus', 1000000, 1000000, ?, ?, 'x')").bind(acc, t + 1000, t).run();
    const a = await account(c);
    expect(a.giftedMicro).toBe(1_000_000);
    expect(a.purchasedMicro).toBe(a.balanceMicro - 1_000_000);
    const r = await c.call('POST', '/v1/messages', chat);
    const b = await account(c);
    expect(b.giftedMicro).toBe(1_000_000 - r.body.overfit.costMicro);
    expect(b.purchasedMicro).toBe(a.purchasedMicro);
  });
});

describe('Missbrauchsschutz beim Kontoumzug', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('Neu installieren → eigene Einladung einlösen → per Code zurück: geschenkte Auswertungen verfallen, Gekauftes wandert mit', async () => {
    mockAi();
    const main = await Client.create();
    const code = (await main.call('POST', '/v1/recovery/code')).body.code as string;
    const reinstall = await Client.create(); // neue Identität auf demselben Handy
    expect((await redeem(main, reinstall)).status).toBe(200);
    await fund(reinstall);
    const bought = (await account(reinstall)).purchasedMicro;
    expect(bought).toBeGreaterThan(0);
    const r = await reinstall.call('POST', '/v1/recovery/restore', { code });
    expect(r.status).toBe(200);
    expect(r.body.giftReviews.remaining).toBe(0);
    expect(r.body.purchasedMicro).toBe(bought);
    // Auch die Belohnung für den Einladenden entfällt: Einladender und Eingeladener sind jetzt dasselbe Konto.
    await reinstall.call('POST', '/v1/messages', review());
    expect((await account(reinstall)).giftReviews.remaining).toBe(0);
    const led = await reinstall.call('GET', '/v1/account/ledger');
    expect(led.body.entries.some((x: any) => x.type === 'invite_expired' && x.reviews === -5 && x.category === 'expired')).toBe(true);
  });

  it('Eigene geschenkte Auswertungen des Zielkontos bleiben', async () => {
    const inviter = await Client.create();
    const main = await Client.create();
    await redeem(inviter, main);
    const code = (await main.call('POST', '/v1/recovery/code')).body.code as string;
    const newPhone = await Client.create();
    const r = await newPhone.call('POST', '/v1/recovery/restore', { code });
    expect(r.body.giftReviews.remaining).toBe(5);
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
