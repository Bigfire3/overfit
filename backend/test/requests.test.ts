import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { Client, baseEnv, fund, rawFetch } from './helpers';
import { costMicro } from '../src/money';
import { currentPrices, TIERS, type Tier } from '../src/providers';
import { purposeOf } from '../src/requests';

const HAIKU = 'claude-haiku-5-5';
const SONNET = 'claude-sonnet-5-5';
const OPUS = 'claude-opus-5-5';
const USAGE = { input_tokens: 1200, cache_read_input_tokens: 300, cache_creation_input_tokens: 200, output_tokens: 400 };
const at = (tier: Tier) => costMicro(USAGE, currentPrices()[TIERS[tier].model], 2800);

const SECRET_PROMPT = 'GEHEIMER-PROMPT-Kniebeuge-120kg';
const SECRET_ANSWER = 'GEHEIME-ANTWORT-mehr-Schlaf';
const sentModel = (init: any) => JSON.parse(init.body).model as string;
const reply = (model: string) =>
  Response.json({ id: 'msg_1', type: 'message', role: 'assistant', model, content: [{ type: 'text', text: SECRET_ANSWER }], stop_reason: 'end_turn', usage: USAGE });
const req = (model: string, purpose?: unknown, extra: object = {}) => ({
  model,
  max_tokens: 4000, // Reservierung deutlich über den Kosten (abgebucht wird höchstens der reservierte Betrag)
  messages: [{ role: 'user', content: SECRET_PROMPT }],
  ...(purpose === undefined ? {} : { overfit: { purpose } }),
  ...extra,
});

let anthropic: any;
beforeEach(() => {
  anthropic = vi.fn(async (_u: string, init: any) => reply(sentModel(init)));
  const real = globalThis.fetch;
  vi.stubGlobal('fetch', (input: any, init?: any) => {
    const url = String(input instanceof Request ? input.url : input);
    if (url.startsWith('https://api.anthropic.com')) return anthropic(url, init);
    return real(input, init);
  });
});
afterEach(() => vi.unstubAllGlobals());

const log = async (c: Client, qs = '') => {
  const r = await c.call('GET', '/v1/account/requests' + qs);
  expect(r.status).toBe(200);
  return r.body as { entries: any[]; next: number | null };
};
const balance = async (c: Client) => (await c.call('GET', '/v1/account')).body.balanceMicro as number;

async function redeem(inviter: Client, redeemer: Client) {
  const token = (await inviter.call('POST', '/v1/invite/token')).body.token;
  const ch = (await redeemer.call('POST', '/v1/invite/challenge')).body.challenge;
  const r = await redeemer.call('POST', '/v1/invite/redeem', { token, attestation: { platform: 'google', token: `mock:${crypto.randomUUID()}`, challenge: ch } });
  expect(r.status).toBe(200);
}

describe('Anfrage-Protokoll GET /v1/account/requests', () => {
  it('Erfolg: Zeit, Dauer, Zweck, Stufe, Modell, Token und abgebuchter Betrag; requestId passt zum Eintrag', async () => {
    const c = await Client.create();
    const f = await fund(c);
    const t0 = Math.floor(Date.now() / 1000);
    const r = await c.call('POST', '/v1/messages', req('standard', 'chat'));
    expect(r.status).toBe(200);
    expect(r.body.overfit).toMatchObject({ purpose: 'chat', fallbackReason: null, costMicro: at('standard') });
    const { entries, next } = await log(c);
    expect(next).toBeNull();
    expect(entries).toHaveLength(1);
    const e = entries[0];
    expect(r.body.overfit.requestId).toBe(e.id);
    expect(e).toMatchObject({
      purpose: 'chat', tier: 'standard', status: 'ok', errorCode: null, requestedModel: SONNET, model: SONNET,
      fallbackFromStatus: null, fallbackReason: null,
      inputTokens: 1200, cacheReadTokens: 300, cacheWriteTokens: 200, outputTokens: 400,
      chargedMicro: at('standard'), gift: false, photo: false, fallback: false,
    });
    expect(e.ts).toBeGreaterThanOrEqual(t0);
    expect(e.ts).toBeLessThanOrEqual(Math.floor(Date.now() / 1000));
    expect(e.durationMs).toBeGreaterThanOrEqual(0);
    // Abgebucht wurde genau der protokollierte Betrag
    expect(await balance(c)).toBe(f.balanceMicro - e.chargedMicro);
  });

  it('enthält nur Metadaten, nie Prompt, Antwort oder Foto', async () => {
    const c = await Client.create();
    await fund(c);
    const img = { type: 'image', source: { type: 'base64', media_type: 'image/jpeg', data: 'R0VIRUlNRVNGT1RP' } };
    await c.call('POST', '/v1/messages', req('lite', 'review', { messages: [{ role: 'user', content: [img, { type: 'text', text: SECRET_PROMPT }] }] }));
    anthropic.mockImplementation(async () => Response.json({ error: { message: 'bad' } }, { status: 400 }));
    await c.call('POST', '/v1/messages', req('lite', 'chat'));
    const { entries } = await log(c);
    expect(entries).toHaveLength(2);
    expect(entries[1].photo).toBe(true);
    const text = JSON.stringify(entries);
    for (const s of [SECRET_PROMPT, SECRET_ANSWER, 'R0VIRUlNRVNGT1RP', 'Kniebeuge']) expect(text).not.toContain(s);
    expect(Object.keys(entries[0]).sort()).toEqual(
      ['id', 'ts', 'durationMs', 'purpose', 'tier', 'status', 'errorCode', 'requestedModel', 'model', 'fallbackFromStatus', 'fallbackReason',
        'inputTokens', 'cacheReadTokens', 'cacheWriteTokens', 'outputTokens', 'chargedMicro', 'gift', 'photo', 'fallback'].sort(),
    );
    // Auch direkt in der Tabelle keine Inhalte
    const rows = await c.env.DB.prepare('SELECT * FROM requests').all();
    const raw = JSON.stringify(rows.results);
    for (const s of [SECRET_PROMPT, SECRET_ANSWER, 'R0VIRUlNRVNGT1RP']) expect(raw).not.toContain(s);
  });

  it('geschenkte Auswertung: gift true, chargedMicro 0', async () => {
    const inviter = await Client.create();
    const friend = await Client.create();
    await redeem(inviter, friend);
    const r = await friend.call('POST', '/v1/messages', req('lite', 'review'));
    expect(r.status).toBe(200);
    expect(r.body.overfit).toMatchObject({ giftReview: true, costMicro: 0 });
    const [e] = (await log(friend)).entries;
    expect(e).toMatchObject({ id: r.body.overfit.requestId, status: 'ok', purpose: 'review', tier: 'lite', model: HAIKU, gift: true, chargedMicro: 0, inputTokens: 1200, outputTokens: 400 });
  });

  it('Ausweichen: fallback true, Wunsch- und tatsächliches Modell, Status und Grund des Ausfalls', async () => {
    anthropic.mockImplementation(async (_u: string, init: any) =>
      sentModel(init) === OPUS ? Response.json({ error: { message: 'Overloaded' } }, { status: 529 }) : reply(sentModel(init)),
    );
    const c = await Client.create();
    await fund(c);
    const r = await c.call('POST', '/v1/messages', req('pro', 'plan'));
    expect(r.status).toBe(200);
    expect(r.body.overfit).toMatchObject({ fallback: true, fallbackReason: 'HTTP 529: Overloaded', purpose: 'plan' });
    const [e] = (await log(c)).entries;
    expect(e).toMatchObject({
      id: r.body.overfit.requestId, status: 'ok', tier: 'pro', requestedModel: OPUS, model: SONNET, fallback: true,
      fallbackFromStatus: 529, fallbackReason: 'Overloaded', chargedMicro: at('standard'), purpose: 'plan',
    });
  });

  it('langer Ausfallgrund wird auf 200 Zeichen gekürzt', async () => {
    anthropic.mockImplementation(async (_u: string, init: any) =>
      sentModel(init) === HAIKU ? Response.json({ error: { message: 'x'.repeat(500) } }, { status: 503 }) : reply(sentModel(init)),
    );
    const c = await Client.create();
    await fund(c);
    await c.call('POST', '/v1/messages', req('lite'));
    expect((await log(c)).entries[0].fallbackReason).toHaveLength(200);
  });

  it('Fehlschlag: status failed, errorCode, kein Modell, nichts abgebucht; requestId in details', async () => {
    anthropic.mockImplementation(async () => Response.json({ error: { message: 'overloaded' } }, { status: 529 }));
    const c = await Client.create();
    const f = await fund(c);
    const r = await c.call('POST', '/v1/messages', req('standard', 'chat'));
    expect(r.status).toBe(502);
    anthropic.mockImplementation(async () => Response.json({ error: { message: 'schema unsupported' } }, { status: 400 }));
    const r2 = await c.call('POST', '/v1/messages', req('lite', 'autofill'));
    expect(r2.status).toBe(400);
    anthropic.mockRejectedValue(new TypeError('network'));
    const r3 = await c.call('POST', '/v1/messages', req('pro', 'review'));
    expect(r3.status).toBe(504);
    expect(await balance(c)).toBe(f.balanceMicro);

    const { entries } = await log(c);
    expect(entries.map((e) => e.id)).toEqual([r3.body.error.details.requestId, r2.body.error.details.requestId, r.body.error.details.requestId]);
    const [timeout, rejected, unavailable] = entries;
    expect(unavailable).toMatchObject({
      status: 'failed', errorCode: 'upstream_unavailable', tier: 'standard', requestedModel: SONNET, model: null, fallback: false,
      fallbackFromStatus: 529, chargedMicro: 0, inputTokens: 0, outputTokens: 0, gift: false, purpose: 'chat',
    });
    expect(rejected).toMatchObject({ status: 'failed', errorCode: 'upstream_rejected', tier: 'lite', model: null, fallbackFromStatus: null, chargedMicro: 0, purpose: 'autofill' });
    expect(timeout).toMatchObject({ status: 'failed', errorCode: 'upstream_unavailable', tier: 'pro', model: null, fallbackFromStatus: 0, chargedMicro: 0 });
  });

  it('insufficient_funds wird protokolliert (ohne Anfrage beim Anbieter)', async () => {
    const c = await Client.create();
    const r = await c.call('POST', '/v1/messages', req('pro', 'chat'));
    expect(r.status).toBe(402);
    expect(r.body.error.code).toBe('insufficient_funds');
    expect(anthropic).not.toHaveBeenCalled();
    const [e] = (await log(c)).entries;
    expect(e).toMatchObject({ id: r.body.error.details.requestId, status: 'failed', errorCode: 'insufficient_funds', tier: 'pro', requestedModel: OPUS, model: null, chargedMicro: 0, purpose: 'chat' });
  });

  it('ungültige Anfragen (vor der Prüfung abgelehnt) erscheinen nicht', async () => {
    const c = await Client.create();
    await fund(c);
    expect((await c.call('POST', '/v1/messages', req('gpt-x'))).status).toBe(400);
    expect((await c.call('POST', '/v1/messages', req('standard', 'chat', { max_tokens: 0 }))).status).toBe(400);
    expect((await log(c)).entries).toEqual([]);
  });

  it('Zweck-Whitelist: review | chat | autofill | plan | other, Unbekanntes → other, fehlt → null; geht nie an den Anbieter', async () => {
    expect(purposeOf(undefined)).toBeNull();
    expect(purposeOf(null)).toBeNull();
    expect(purposeOf('hack<script>')).toBe('other');
    expect(purposeOf(42)).toBe('other');
    const c = await Client.create();
    await fund(c);
    const sent: (string | null | undefined)[] = ['review', 'chat', 'autofill', 'plan', 'other', 'DROP TABLE', undefined];
    for (const p of sent) expect((await c.call('POST', '/v1/messages', req('lite', p))).status).toBe(200);
    const { entries } = await log(c);
    expect(entries.map((e) => e.purpose).reverse()).toEqual(['review', 'chat', 'autofill', 'plan', 'other', 'other', null]);
    for (const [, init] of anthropic.mock.calls) expect(JSON.parse(init.body).overfit).toBeUndefined();
  });

  it('Seiten: neueste zuerst, limit, before, next', async () => {
    const c = await Client.create();
    await fund(c);
    const ids: number[] = [];
    for (let i = 0; i < 5; i++) ids.push((await c.call('POST', '/v1/messages', req('lite'))).body.overfit.requestId);
    expect(new Set(ids).size).toBe(5);
    const desc = [...ids].sort((a, b) => b - a);

    const p1 = await log(c, '?limit=2');
    expect(p1.entries.map((e) => e.id)).toEqual(desc.slice(0, 2));
    expect(p1.next).toBe(desc[1]);
    const p2 = await log(c, `?limit=2&before=${p1.next}`);
    expect(p2.entries.map((e) => e.id)).toEqual(desc.slice(2, 4));
    const p3 = await log(c, `?limit=2&before=${p2.next}`);
    expect(p3.entries.map((e) => e.id)).toEqual(desc.slice(4));
    expect(p3.next).toBeNull();

    // Standard-Limit und ungültige Werte
    expect((await log(c)).entries).toHaveLength(5);
    expect((await log(c, '?limit=0')).entries).toHaveLength(5); // 0 → Standard (50)
    expect((await log(c, '?limit=-3')).entries).toHaveLength(1); // unter 1 → 1
    expect((await log(c, '?limit=quatsch&before=quatsch')).entries).toHaveLength(5);
  });

  it('nur das eigene Konto ist sichtbar', async () => {
    const a = await Client.create();
    const b = await Client.create();
    await fund(a);
    await fund(b);
    const ra = await a.call('POST', '/v1/messages', req('lite', 'chat'));
    await b.call('POST', '/v1/messages', req('pro', 'review'));
    await b.call('POST', '/v1/messages', req('pro', 'review'));
    const la = await log(a);
    expect(la.entries.map((e) => e.id)).toEqual([ra.body.overfit.requestId]);
    const lb = await log(b);
    expect(lb.entries).toHaveLength(2);
    expect(lb.entries.every((e) => e.tier === 'pro')).toBe(true);
    // Auch mit before über fremde IDs hinweg keine fremden Einträge
    expect((await log(a, `?before=${Number.MAX_SAFE_INTEGER}`)).entries).toHaveLength(1);
  });

  it('nur signiert und nur GET', async () => {
    expect((await rawFetch('/v1/account/requests', baseEnv())).status).toBe(401);
    const c = await Client.create();
    expect((await c.call('POST', '/v1/account/requests')).status).toBe(405);
  });

  it('Umzug per Wiederherstellungscode nimmt die Einträge mit', async () => {
    const oldPhone = await Client.create();
    await fund(oldPhone);
    const r1 = await oldPhone.call('POST', '/v1/messages', req('lite', 'chat'));
    const code = (await oldPhone.call('POST', '/v1/recovery/code')).body.code as string;
    const newPhone = await Client.create();
    await fund(newPhone);
    const r2 = await newPhone.call('POST', '/v1/messages', req('standard', 'review'));
    expect((await newPhone.call('POST', '/v1/recovery/restore', { code })).status).toBe(200);
    const { entries } = await log(newPhone);
    expect(entries.map((e) => e.id).sort()).toEqual([r1.body.overfit.requestId, r2.body.overfit.requestId].sort());
    // Keine verwaisten Einträge des aufgelösten Kontos
    const orphans = await newPhone.env.DB.prepare('SELECT COUNT(*) AS n FROM requests WHERE account_id NOT IN (SELECT id FROM accounts)').first<{ n: number }>();
    expect(orphans!.n).toBe(0);
  });
});
