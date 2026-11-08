import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { Client, baseEnv, fund } from './helpers';
import { HOLD_TTL_SEC, now } from '../src/ledger';
import { MAX_UPSTREAM_BUDGET_MS, getConfig } from '../src/config';
import type { Env } from '../src/types';

// Reservierungen (Holds) müssen in jedem Fehlerpfad sofort enden, und eine Anfrage muss auch dann sauber abrechnen, wenn die
// App die Verbindung trennt (8.11.2026: drei Opus-Anfragen „No connection“, je ~0,20 € blieben 15 min reserviert).

const reply = (usage: object = { input_tokens: 1000, output_tokens: 500 }) =>
  new Response(JSON.stringify({ id: 'msg_1', type: 'message', role: 'assistant', content: [{ type: 'text', text: 'ok' }], usage }), {
    status: 200,
    headers: { 'content-type': 'application/json' },
  });
const body = { model: 'pro', max_tokens: 4000, messages: [{ role: 'user', content: 'Hallo' }], overfit: { purpose: 'chat' } };

let upstream: any;
beforeEach(() => {
  upstream = vi.fn(async () => reply());
  const real = globalThis.fetch;
  vi.stubGlobal('fetch', (input: any, init?: any) => {
    const url = String(input instanceof Request ? input.url : input);
    if (url.startsWith('https://api.anthropic.com')) return upstream(input, init);
    return real(input, init);
  });
});
afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

async function account(c: Client) {
  return (await c.call('GET', '/v1/account')).body as { balanceMicro: number; reservedMicro: number };
}

/** Wartet auf das Abort-Signal der Anfrage (Zeitüberschreitung beim Anbieter). */
const hang = (_: unknown, init: { signal?: AbortSignal }) =>
  new Promise<Response>((_, reject) => init.signal?.addEventListener('abort', () => reject(init.signal!.reason)));

describe('Reservierungen', () => {
  it('Zeitüberschreitung beim Anbieter (auch beim Ausweich-Modell) → 504, Reservierung sofort frei', async () => {
    upstream.mockImplementation(hang);
    const c = await Client.create(baseEnv({ UPSTREAM_TIMEOUT_MS: '1000', UPSTREAM_BUDGET_MS: '1500' }));
    const f = await fund(c);
    const r = await c.call('POST', '/v1/messages', body);
    expect(r.status).toBe(504);
    expect(r.body.error.code).toBe('upstream_unavailable');
    expect(await account(c)).toMatchObject({ balanceMicro: f.balanceMicro, reservedMicro: 0 });
    const log = (await c.call('GET', '/v1/account/requests')).body.entries[0];
    expect(log).toMatchObject({ status: 'failed', errorCode: 'upstream_unavailable', chargedMicro: 0, fallbackReason: 'Zeitüberschreitung' });
  });

  it('unerwarteter Fehler beim Abbuchen → 500, Reservierung frei, nichts abgebucht', async () => {
    let failBatch = false;
    const db = baseEnv().DB;
    const DB = new Proxy(db, {
      get(target, prop) {
        if (prop === 'batch') return (stmts: D1PreparedStatement[]) => (failBatch ? Promise.reject(new Error('D1 kaputt')) : target.batch(stmts));
        const v = (target as any)[prop];
        return typeof v === 'function' ? v.bind(target) : v;
      },
    });
    const c = await Client.create(baseEnv({ DB } as Partial<Env>));
    const f = await fund(c);
    failBatch = true;
    const r = await c.call('POST', '/v1/messages', body);
    failBatch = false;
    expect(r.status).toBe(500);
    expect(await account(c)).toMatchObject({ balanceMicro: f.balanceMicro, reservedMicro: 0 });
  });

  it('App trennt während der Anbieter rechnet: Arbeit läuft über waitUntil weiter und rechnet echte Kosten ab', async () => {
    let answer!: () => void;
    upstream.mockImplementation(() => new Promise<Response>((resolve) => (answer = () => resolve(reply()))));
    vi.spyOn(Math, 'random').mockReturnValue(0.5); // keine zufällige Aufräum-Arbeit über waitUntil
    const c = await Client.create();
    const f = await fund(c);
    const pending: Promise<unknown>[] = [];
    const ctx = { waitUntil: (p: Promise<unknown>) => pending.push(p), passThroughOnException() {} } as unknown as ExecutionContext;
    const client = await Client.create();
    Object.assign(client, { keys: c.keys, publicRaw: c.publicRaw, deviceId: c.deviceId, ctx });
    // Die App wartet nicht auf die Antwort (Verbindung weg); die Anfrage selbst läuft weiter.
    const request = client.call('POST', '/v1/messages', body);
    await vi.waitFor(() => expect(upstream).toHaveBeenCalledTimes(1));
    expect(pending.length).toBeGreaterThan(0);
    const during = await account(c);
    expect(during.reservedMicro).toBeGreaterThan(0);
    expect(during.balanceMicro + during.reservedMicro).toBe(f.balanceMicro);
    answer();
    await Promise.all(pending);
    await request;
    const after = await account(c);
    expect(after.reservedMicro).toBe(0);
    const led = (await c.call('GET', '/v1/account/ledger')).body;
    const usage = led.entries.find((e: any) => e.type === 'usage');
    expect(usage).toMatchObject({ model: 'claude-opus-5-5', inputTokens: 1000, outputTokens: 500 });
    expect(after.balanceMicro).toBe(f.balanceMicro + usage.amountMicro);
    expect((await c.call('GET', '/v1/account/requests')).body.entries[0]).toMatchObject({ status: 'ok', chargedMicro: -usage.amountMicro });
  });

  it('Buchungsliste geht auf: Summe der Buchungen = Kontostand + Reservierungen', async () => {
    const c = await Client.create();
    await fund(c);
    await c.call('POST', '/v1/messages', body);
    const acc = (await c.call('GET', '/v1/account')).body;
    await c.env.DB.prepare('INSERT INTO holds (id, account_id, amount_micro, created_at) VALUES (?, ?, ?, ?)').bind('h-run', acc.accountId, 123_456, now()).run();
    const led = (await c.call('GET', '/v1/account/ledger')).body;
    const sum = led.entries.reduce((a: number, e: any) => a + e.amountMicro, 0);
    expect(led.reservedMicro).toBe(123_456);
    expect(sum).toBe(led.balanceMicro + led.reservedMicro);
    expect((await account(c)).reservedMicro).toBe(123_456);
  });

  it('verwaiste Reservierungen zählen nach HOLD_TTL_SEC nicht mehr', async () => {
    const c = await Client.create();
    const f = await fund(c);
    const id = (await c.call('GET', '/v1/account')).body.accountId;
    const ins = (hid: string, age: number) =>
      c.env.DB.prepare('INSERT INTO holds (id, account_id, amount_micro, created_at) VALUES (?, ?, ?, ?)').bind(hid, id, 200_000, now() - age).run();
    await ins('old', HOLD_TTL_SEC + 1);
    expect(await account(c)).toMatchObject({ balanceMicro: f.balanceMicro, reservedMicro: 0 });
    await ins('young', HOLD_TTL_SEC - 30);
    expect(await account(c)).toMatchObject({ balanceMicro: f.balanceMicro - 200_000, reservedMicro: 200_000 });
  });

  it('Zeitbudget bleibt unter App-Timeout (240 s) und Lebensdauer einer Reservierung', () => {
    const cfg = getConfig(baseEnv());
    expect(cfg.upstreamTimeoutMs).toBeLessThanOrEqual(cfg.upstreamBudgetMs);
    expect(cfg.upstreamBudgetMs).toBeLessThan(240_000);
    expect(MAX_UPSTREAM_BUDGET_MS).toBeLessThan(HOLD_TTL_SEC * 1000);
    const big = getConfig(baseEnv({ UPSTREAM_TIMEOUT_MS: '900000', UPSTREAM_BUDGET_MS: '900000' }));
    expect(big.upstreamBudgetMs).toBe(MAX_UPSTREAM_BUDGET_MS);
    expect(big.upstreamTimeoutMs).toBe(MAX_UPSTREAM_BUDGET_MS);
  });
});
