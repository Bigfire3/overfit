import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { Client, baseEnv, fund } from './helpers';

const reply = (usage: object = { input_tokens: 1000, output_tokens: 500 }) =>
  new Response(JSON.stringify({ id: 'msg_1', type: 'message', role: 'assistant', content: [{ type: 'text', text: 'ok' }], usage }), {
    status: 200,
    headers: { 'content-type': 'application/json' },
  });

// Alte App-Versionen schicken die Modell-ID; sie entspricht der Stufe `standard` (Claude Sonnet 5.5).
const body = (extra: object = {}) => ({
  model: 'claude-sonnet-5-5',
  max_tokens: 1000,
  messages: [{ role: 'user', content: 'Hallo' }],
  ...extra,
});

let upstream: any;
beforeEach(() => {
  upstream = vi.fn(async () => reply());
  const real = globalThis.fetch;
  vi.stubGlobal('fetch', (input: any, init?: any) => {
    const url = String(input instanceof Request ? input.url : input);
    // Alle Stufen laufen über Anthropic; die Ausweich-Stufe (lite) landet also beim selben Mock. Stufen-Tests: tiers.test.ts
    if (url.startsWith('https://api.anthropic.com')) return upstream(input, init);
    return real(input, init);
  });
});
afterEach(() => vi.unstubAllGlobals());

describe('POST /v1/messages', () => {
  it('rechnet nach usage ab und liefert die Kosten', async () => {
    const c = await Client.create();
    const funded = await fund(c);
    const r = await c.call('POST', '/v1/messages', body());
    expect(r.status).toBe(200);
    expect(r.body.content[0].text).toBe('ok');
    expect(r.body.overfit.costMicro).toBe(8244);
    expect(r.headers.get('x-overfit-cost-micro')).toBe('8244');
    expect(r.body.overfit.balanceMicro).toBe(funded.balanceMicro - 8244);
    expect((await c.call('GET', '/v1/account')).body.balanceMicro).toBe(funded.balanceMicro - 8244);
    const [, init] = upstream.mock.calls[0];
    expect(init.headers['x-api-key']).toBe('test-key');
    const led = await c.call('GET', '/v1/account/ledger');
    expect(led.body.entries[0]).toMatchObject({ type: 'usage', amountMicro: -8244, inputTokens: 1000, outputTokens: 500 });
  });

  it('Cache-Tokens werden separat abgerechnet', async () => {
    upstream.mockResolvedValue(reply({ input_tokens: 0, cache_read_input_tokens: 20_000, cache_creation_input_tokens: 0, output_tokens: 0 }));
    const c = await Client.create();
    await fund(c);
    expect((await c.call('POST', '/v1/messages', body())).body.overfit.costMicro).toBe(Math.ceil((20_000 * 184_000 * 12800) / 1e10));
  });

  it('ohne Guthaben: 402 und kein Upstream-Aufruf', async () => {
    const c = await Client.create();
    const r = await c.call('POST', '/v1/messages', body());
    expect(r.status).toBe(402);
    expect(r.body.error.code).toBe('insufficient_funds');
    expect(r.body.error.details.requiredMicro).toBeGreaterThan(0);
    expect(upstream).not.toHaveBeenCalled();
  });

  it('Upstream-Fehler gibt die Reservierung vollständig frei', async () => {
    // Jede Antwort frisch (ein Response-Body lässt sich nur einmal lesen; standard weicht auf lite aus, das hier ebenfalls ausfällt).
    upstream.mockImplementation(async () => new Response(JSON.stringify({ error: { message: 'overloaded' } }), { status: 529 }));
    const c = await Client.create();
    const f = await fund(c);
    const r = await c.call('POST', '/v1/messages', body());
    expect(r.status).toBe(502);
    expect(r.body.error.code).toBe('upstream_unavailable');
    expect(upstream).toHaveBeenCalledTimes(2);
    expect((await c.call('GET', '/v1/account')).body.balanceMicro).toBe(f.balanceMicro);
    // Netzwerkfehler bei standard → Ausweichen auf lite, ebenfalls Netzwerkfehler → 504
    upstream.mockRejectedValue(new Error('network'));
    expect((await c.call('POST', '/v1/messages', body())).status).toBe(504);
    expect((await c.call('GET', '/v1/account')).body.balanceMicro).toBe(f.balanceMicro);
    upstream.mockImplementation(async () => new Response(JSON.stringify({ error: { message: 'bad' } }), { status: 400 }));
    const r3 = await c.call('POST', '/v1/messages', body());
    expect(r3.body.error.code).toBe('upstream_rejected');
    expect((await c.call('GET', '/v1/account')).body.balanceMicro).toBe(f.balanceMicro);
  });

  it('Whitelist, max_tokens, Streaming', async () => {
    const c = await Client.create();
    await fund(c);
    expect((await c.call('POST', '/v1/messages', body({ model: 'gpt-x' }))).body.error.code).toBe('model_not_allowed');
    expect((await c.call('POST', '/v1/messages', body({ max_tokens: 999999 }))).body.error.code).toBe('invalid_max_tokens');
    expect((await c.call('POST', '/v1/messages', body({ max_tokens: undefined }))).body.error.code).toBe('invalid_max_tokens');
    expect((await c.call('POST', '/v1/messages', body({ stream: true }))).body.error.code).toBe('streaming_not_supported');
    expect(upstream).not.toHaveBeenCalled();
  });

  it('parallele Anfragen können das Guthaben nicht überziehen', async () => {
    const c = await Client.create(baseEnv({ PACKAGES: JSON.stringify({ small: { priceMicro: 100_000 } }) }));
    await c.call('POST', '/v1/purchases/verify', { platform: 'google', productId: 'small', purchaseToken: 'mock:o1' });
    const bal = (await c.call('GET', '/v1/account')).body.balanceMicro;
    // Jede Anfrage reserviert ~ (1000 × 9,2 + Eingabe) × 1,28 ≈ 7 600 µ€ → bei ~79 000 µ€ passen höchstens 10
    const res = await Promise.all(Array.from({ length: 20 }, () => c.call('POST', '/v1/messages', body())));
    const ok = res.filter((r) => r.status === 200).length;
    const denied = res.filter((r) => r.status === 402).length;
    expect(ok + denied).toBe(20);
    expect(denied).toBeGreaterThan(0);
    const after = (await c.call('GET', '/v1/account')).body.balanceMicro;
    expect(after).toBeGreaterThanOrEqual(0);
    expect(bal - after).toBe(ok * 8244);
  });

  it('Bilder werden pauschal statt nach Base64-Länge reserviert', async () => {
    const c = await Client.create();
    await fund(c);
    const img = { type: 'image', source: { type: 'base64', media_type: 'image/jpeg', data: 'A'.repeat(3_000_000) } };
    const r = await c.call('POST', '/v1/messages', body({ messages: [{ role: 'user', content: [img, img, { type: 'text', text: 'x' }] }] }));
    expect(r.status).toBe(200);
  });

  it('nicht konfigurierter Schlüssel → 503', async () => {
    const c = await Client.create(baseEnv({ ANTHROPIC_API_KEY: undefined }));
    await fund(c);
    expect((await c.call('POST', '/v1/messages', body())).status).toBe(503);
  });

  it('verfallenes Startguthaben zählt nicht, verfallende Posten werden zuerst verbraucht', async () => {
    const e = baseEnv();
    const c = await Client.create(e);
    const acc = (await c.call('GET', '/v1/account')).body.accountId;
    const t = Math.floor(Date.now() / 1000);
    await e.DB.batch([
      e.DB.prepare("INSERT INTO lots VALUES ('old', ?, 'invite_bonus', 1000000, 1000000, ?, ?, 'x')").bind(acc, t - 10, t - 1000),
      e.DB.prepare("INSERT INTO lots VALUES ('bonus', ?, 'invite_bonus', 1000000, 1000000, ?, ?, 'x')").bind(acc, t + 1000, t),
      e.DB.prepare("INSERT INTO lots VALUES ('paid', ?, 'purchase', 1000000, 1000000, NULL, ?, 'x')").bind(acc, t),
    ]);
    expect((await c.call('GET', '/v1/account')).body.balanceMicro).toBe(2_000_000);
    await c.call('POST', '/v1/messages', body());
    const rows = await e.DB.prepare("SELECT id, remaining_micro AS r FROM lots ORDER BY id").all<{ id: string; r: number }>();
    const m = Object.fromEntries(rows.results.map((x) => [x.id, x.r]));
    expect(m.bonus).toBe(1_000_000 - 8244);
    expect(m.paid).toBe(1_000_000);
    expect(m.old).toBe(1_000_000);
  });
});
