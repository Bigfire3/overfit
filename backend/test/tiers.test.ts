import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { Client, baseEnv, fund, rawFetch } from './helpers';
import { costMicro } from '../src/money';
import { currentPrices, tierForModel, type Tier } from '../src/providers';
import { ROUTING } from '../src/routing';
const TIERS = ROUTING.presets.claude.tiers;
import type { Env } from '../src/types';

const MARKUP = 2800;
const price = (tier: Tier) => currentPrices()[TIERS[tier].model];
const USAGE = { input_tokens: 1000, output_tokens: 500 };
const at = (tier: Tier, usage: object = USAGE) => costMicro(usage as never, price(tier), MARKUP);

const HAIKU = 'claude-haiku-5-5';
const SONNET = 'claude-sonnet-5-5';
const OPUS = 'claude-opus-5-5';

/** Modell, das der Worker beim Anbieter angefragt hat. */
const sentModel = (init: any) => JSON.parse(init.body).model as string;
const anthropicReply = (model = SONNET, usage: object | null = USAGE) =>
  Response.json({
    id: 'msg_1', type: 'message', role: 'assistant', model, content: [{ type: 'text', text: `antwort von ${model}` }], stop_reason: 'end_turn',
    ...(usage ? { usage } : {}),
  });
const overloaded = () => Response.json({ type: 'error', error: { type: 'overloaded_error', message: 'overloaded' } }, { status: 529 });

const IMG = { type: 'image', source: { type: 'base64', media_type: 'image/jpeg', data: 'QUJD' } };
const body = (model: string, extra: object = {}) => ({
  model,
  max_tokens: 1000,
  system: [
    { type: 'text', text: 'Regeln', cache_control: { type: 'ephemeral' } },
    { type: 'text', text: 'Profil' },
  ],
  messages: [{ role: 'user', content: 'Hallo' }],
  output_config: { format: { type: 'json_schema', schema: { type: 'object', properties: { ok: { type: 'boolean' } } } } },
  ...extra,
});

let anthropic: any;
let ai: { gateway: any };
beforeEach(() => {
  // Jede Antwort frisch erzeugen (ein Response-Body lässt sich nur einmal lesen; Ausweichen fragt denselben Anbieter zweimal).
  anthropic = vi.fn(async (_url: string, init: any) => anthropicReply(sentModel(init)));
  ai = { gateway: vi.fn((id: string) => ({ getUrl: async (p: string) => `https://gateway.test/${id}/${p}` })) };
  const real = globalThis.fetch;
  vi.stubGlobal('fetch', (input: any, init?: any) => {
    const url = String(input instanceof Request ? input.url : input);
    if (url.startsWith('https://api.anthropic.com') || url.startsWith('https://gateway.test/overfit/anthropic')) return anthropic(url, init);
    if (url.startsWith('https://generativelanguage.googleapis.com')) throw new Error('Gemini darf nicht mehr angefragt werden');
    return real(input, init);
  });
});
afterEach(() => vi.unstubAllGlobals());

/** Nur die genannten Modelle fallen aus (mit `fail`), die anderen antworten normal. */
const failFor = (models: string[], fail: () => Promise<Response>) =>
  anthropic.mockImplementation(async (_url: string, init: any) => (models.includes(sentModel(init)) ? fail() : anthropicReply(sentModel(init))));
const sentModels = () => anthropic.mock.calls.map((x: any[]) => sentModel(x[1]));

const env = (extra: Partial<Env> = {}) => baseEnv({ AI: ai, ...extra } as Partial<Env>);
const balance = async (c: Client) => (await c.call('GET', '/v1/account')).body.balanceMicro as number;
const lastUsage = async (c: Client) => (await c.call('GET', '/v1/account/ledger')).body.entries[0];
const usageEntries = async (c: Client) => (await c.call('GET', '/v1/account/ledger')).body.entries.filter((e: any) => e.type === 'usage');

describe('Alle Stufen sind Claude über Anthropic', () => {
  it.each([
    ['lite', HAIKU, 'Claude Haiku 5.5'],
    ['standard', SONNET, 'Claude Sonnet 5.5'],
    ['pro', OPUS, 'Claude Opus 5.5'],
  ] as [Tier, string, string][])('%s → %s, Abrechnung nach dessen Preis', async (tier, model, name) => {
    const c = await Client.create(env());
    const f = await fund(c);
    const r = await c.call('POST', '/v1/messages', body(tier));
    expect(r.status).toBe(200);
    expect(r.body.content[0].text).toBe(`antwort von ${model}`);
    const expected = at(tier);
    expect(r.body.overfit).toMatchObject({
      costMicro: expected, tier, servedTier: tier, model, modelName: name, vendor: 'Anthropic', requestedModel: model, fallback: false,
    });
    expect(await balance(c)).toBe(f.balanceMicro - expected);
    expect(await lastUsage(c)).toMatchObject({ type: 'usage', amountMicro: -expected, model, tier, inputTokens: 1000, outputTokens: 500 });

    expect(anthropic).toHaveBeenCalledTimes(1);
    const [url, init] = anthropic.mock.calls[0];
    expect(url).toBe('https://api.anthropic.com/v1/messages');
    expect(init.headers['x-api-key']).toBe('test-key');
    expect(init.headers['anthropic-version']).toBe('2023-06-01');
    const sent = JSON.parse(init.body);
    expect(sent.model).toBe(model);
    expect(sent.max_tokens).toBe(1000);
    expect(sent.output_config).toMatchObject({ effort: 'medium', format: { type: 'json_schema' } });
    expect(sent.system[0].cache_control).toEqual({ type: 'ephemeral' });
  });

  it('Preise: Haiku < Sonnet < Opus; Sonnet wie bisher 8244 µ€ für 1000/500 Token', () => {
    expect(at('lite')).toBeLessThan(at('standard'));
    expect(at('standard')).toBeLessThan(at('pro'));
    expect(at('standard')).toBe(8244);
  });

  it('effort der App bleibt erhalten', async () => {
    const c = await Client.create(env());
    await fund(c);
    await c.call('POST', '/v1/messages', body('pro', { output_config: { effort: 'high' } }));
    expect(JSON.parse(anthropic.mock.calls[0][1].body).output_config).toEqual({ effort: 'high' });
  });

  it('Cache-Token werden zum Preis der Stufe abgerechnet', async () => {
    const u = { input_tokens: 100, cache_read_input_tokens: 4000, cache_creation_input_tokens: 2000, output_tokens: 10 };
    anthropic.mockImplementation(async (_u: string, init: any) => anthropicReply(sentModel(init), u));
    const c = await Client.create(env());
    await fund(c);
    const r = await c.call('POST', '/v1/messages', body('lite'));
    expect(r.body.overfit.costMicro).toBe(at('lite', u));
    expect(await lastUsage(c)).toMatchObject({ inputTokens: 100, cacheReadTokens: 4000, cacheWriteTokens: 2000, outputTokens: 10 });
  });

  it('ohne usage in der Antwort wird der reservierte Höchstbetrag berechnet', async () => {
    anthropic.mockImplementation(async (_u: string, init: any) => anthropicReply(sentModel(init), null));
    const c = await Client.create(env());
    const f = await fund(c);
    const r = await c.call('POST', '/v1/messages', body('lite'));
    expect(r.status).toBe(200);
    // Mindestens die angefragten Ausgabe-Token zum Haiku-Preis
    expect(r.body.overfit.costMicro).toBeGreaterThanOrEqual(at('lite', { input_tokens: 0, output_tokens: 1000 }));
    expect(await balance(c)).toBe(f.balanceMicro - r.body.overfit.costMicro);
  });

  it.each(['lite', 'standard', 'pro'] as Tier[])('Bilder gehen auf Stufe %s unverändert an Claude', async (tier) => {
    const c = await Client.create(env());
    await fund(c);
    const content = [IMG, { type: 'text', text: 'Bild' }];
    const r = await c.call('POST', '/v1/messages', body(tier, { messages: [{ role: 'user', content }] }));
    expect(r.status).toBe(200);
    expect(r.body.overfit).toMatchObject({ tier, servedTier: tier, costMicro: at(tier) });
    expect(JSON.parse(anthropic.mock.calls[0][1].body).messages).toEqual([{ role: 'user', content }]);
  });

  it('ohne jeden KI-Schlüssel ist nichts verfügbar', async () => {
    const c = await Client.create(env({ ANTHROPIC_API_KEY: undefined } as Partial<Env>));
    const f = await fund(c);
    for (const t of ['lite', 'standard', 'pro']) {
      const r = await c.call('POST', '/v1/messages', body(t));
      expect(r.status).toBe(503);
      expect(r.body.error.code).toBe('not_configured');
    }
    expect(anthropic).not.toHaveBeenCalled();
    expect(await balance(c)).toBe(f.balanceMicro);
  });
});

describe('Alte Modell-IDs (ältere App-Versionen)', () => {
  it('werden auf Stufen abgebildet', () => {
    expect(tierForModel('gemini-3.6-flash')).toBe('standard');
    expect(tierForModel('@cf/meta/llama-3.3-70b-instruct-fp8-fast')).toBe('lite');
    expect(tierForModel('@cf/meta/llama-3.1-8b-instruct')).toBe('lite');
    expect(tierForModel(HAIKU)).toBe('lite');
    expect(tierForModel(SONNET)).toBe('standard');
    expect(tierForModel(OPUS)).toBe('pro');
    expect(tierForModel('pro')).toBe('pro');
    expect(tierForModel('gpt-x')).toBeNull();
  });

  it.each([
    ['gemini-3.6-flash', 'standard', SONNET],
    ['@cf/meta/llama-3.3-70b-instruct-fp8-fast', 'lite', HAIKU],
    ['@cf/meta/llama-3.1-8b-instruct', 'lite', HAIKU],
  ] as [string, Tier, string][])('%s → Stufe %s (%s), Abrechnung nach Claude-Preis', async (legacy, tier, model) => {
    const c = await Client.create(env());
    await fund(c);
    const r = await c.call('POST', '/v1/messages', body(legacy));
    expect(r.status).toBe(200);
    expect(r.body.overfit).toMatchObject({ tier, model, costMicro: at(tier) });
    expect(sentModels()).toEqual([model]);
  });
});

describe('Ausweichen bei Ausfall', () => {
  it('lite → standard bei 5xx; berechnet höchstens den Haiku-Preis', async () => {
    failFor([HAIKU], async () => overloaded());
    const c = await Client.create(env());
    const f = await fund(c);
    const r = await c.call('POST', '/v1/messages', body('lite'));
    expect(r.status).toBe(200);
    expect(r.body.content[0].text).toBe(`antwort von ${SONNET}`);
    expect(sentModels()).toEqual([HAIKU, SONNET]);
    expect(at('lite')).toBeLessThan(at('standard'));
    expect(r.body.overfit).toMatchObject({
      tier: 'lite', servedTier: 'standard', model: SONNET, modelName: 'Claude Sonnet 5.5', requestedModel: HAIKU, requestedModelName: 'Claude Haiku 5.5',
      fallback: true, costMicro: at('lite'),
    });
    expect(await balance(c)).toBe(f.balanceMicro - at('lite'));
    expect(await lastUsage(c)).toMatchObject({ tier: 'lite', model: SONNET, amountMicro: -at('lite') });
  });

  it('standard → lite bei 429; berechnet den günstigeren Haiku-Preis', async () => {
    failFor([SONNET], async () => Response.json({ error: { message: 'rate limited' } }, { status: 429 }));
    const c = await Client.create(env());
    await fund(c);
    const r = await c.call('POST', '/v1/messages', body('standard'));
    expect(r.status).toBe(200);
    expect(sentModels()).toEqual([SONNET, HAIKU]);
    expect(r.body.overfit).toMatchObject({ tier: 'standard', servedTier: 'lite', model: HAIKU, fallback: true, costMicro: at('lite') });
  });

  it('pro → standard bei Zeitüberschreitung; berechnet den Sonnet-Preis', async () => {
    failFor([OPUS], async () => {
      throw new DOMException('timeout', 'TimeoutError');
    });
    const c = await Client.create(env());
    await fund(c);
    const r = await c.call('POST', '/v1/messages', body('pro'));
    expect(r.status).toBe(200);
    expect(sentModels()).toEqual([OPUS, SONNET]);
    expect(r.body.overfit).toMatchObject({ tier: 'pro', servedTier: 'standard', model: SONNET, fallback: true, costMicro: at('standard') });
  });

  it('mit Foto: Ausweich-Stufe kann ebenfalls Bilder', async () => {
    failFor([HAIKU], async () => new Response('kaputt', { status: 500 }));
    const c = await Client.create(env());
    await fund(c);
    const r = await c.call('POST', '/v1/messages', body('lite', { messages: [{ role: 'user', content: [IMG] }] }));
    expect(r.status).toBe(200);
    expect(r.body.overfit).toMatchObject({ servedTier: 'standard', fallback: true });
    expect(JSON.parse(anthropic.mock.calls[1][1].body).messages[0].content).toEqual([IMG]);
  });

  it('beide Modelle fallen aus → 502, nur ein Ausweichversuch, Reservierung vollständig zurück', async () => {
    anthropic.mockImplementation(async () => overloaded());
    const c = await Client.create(env());
    const f = await fund(c);
    const r = await c.call('POST', '/v1/messages', body('pro'));
    expect(r.status).toBe(502);
    expect(r.body.error.code).toBe('upstream_unavailable');
    expect(sentModels()).toEqual([OPUS, SONNET]);
    expect(await balance(c)).toBe(f.balanceMicro);
    expect(await usageEntries(c)).toEqual([]);
  });

  it('Abbruch (Netzwerk) bei beiden → 504, nichts abgebucht', async () => {
    anthropic.mockRejectedValue(new TypeError('network'));
    const c = await Client.create(env());
    const f = await fund(c);
    const r = await c.call('POST', '/v1/messages', body('standard'));
    expect(r.status).toBe(504);
    expect(r.body.error.code).toBe('upstream_unavailable');
    expect(anthropic).toHaveBeenCalledTimes(2);
    expect(await balance(c)).toBe(f.balanceMicro);
  });

  it('Ablehnung (400) weicht nicht aus und bucht nichts ab', async () => {
    anthropic.mockImplementation(async () => Response.json({ type: 'error', error: { message: 'schema unsupported' } }, { status: 400 }));
    const c = await Client.create(env());
    const f = await fund(c);
    const r = await c.call('POST', '/v1/messages', body('standard'));
    expect(r.status).toBe(400);
    expect(r.body.error).toMatchObject({ code: 'upstream_rejected', details: { upstreamStatus: 400 } });
    // Fehlertext des Anbieters geht nicht an die App (kann Teile der Anfrage wiederholen), nur Status und Code.
    expect(r.body.error.message).not.toContain('schema unsupported');
    expect(anthropic).toHaveBeenCalledTimes(1);
    expect(await balance(c)).toBe(f.balanceMicro);
  });

  it('Zeitbudget aufgebraucht → kein Ausweichen', async () => {
    failFor([SONNET], async () => overloaded());
    const c = await Client.create(env({ UPSTREAM_BUDGET_MS: '1000' }));
    const f = await fund(c);
    expect((await c.call('POST', '/v1/messages', body('standard'))).status).toBe(502);
    expect(sentModels()).toEqual([SONNET]);
    expect(await balance(c)).toBe(f.balanceMicro);
  });
});

describe('AI Gateway', () => {
  it('mit Token: über das Gateway, mit Metadaten; bei Fotos keine Inhalte', async () => {
    const c = await Client.create(env({ AI_GATEWAY_ID: 'overfit', AI_GATEWAY_TOKEN: 'gw-token' }));
    await fund(c);
    expect((await c.call('POST', '/v1/messages', body('standard', { messages: [{ role: 'user', content: [IMG] }] }))).status).toBe(200);
    const [pUrl, pInit] = anthropic.mock.calls[0];
    expect(pUrl).toBe('https://gateway.test/overfit/anthropic/v1/messages');
    expect(pInit.headers['x-api-key']).toBe('test-key');
    expect(pInit.headers['cf-aig-authorization']).toBe('Bearer gw-token');
    // Nicht „collect-log: false“ (das würde auch Kosten und Tokens unterdrücken), sondern nur die Inhalte abschalten.
    expect(pInit.headers['cf-aig-collect-log']).toBeUndefined();
    expect(pInit.headers['cf-aig-collect-log-payload']).toBe('false');
    expect(JSON.parse(pInit.headers['cf-aig-metadata'])).toEqual({ tier: 'standard', photo: true, fallback: false });

    expect((await c.call('POST', '/v1/messages', body('lite'))).status).toBe(200);
    const [, init] = anthropic.mock.calls[1];
    expect(init.headers['cf-aig-collect-log-payload']).toBeUndefined(); // ohne Foto: normales Logging
    expect(JSON.parse(init.headers['cf-aig-metadata'])).toEqual({ tier: 'lite', photo: false, fallback: false });
    expect(ai.gateway).toHaveBeenCalledWith('overfit');
  });

  it('mit Token: Ausweich-Anfrage ist in den Metadaten markiert', async () => {
    failFor([OPUS], async () => overloaded());
    const c = await Client.create(env({ AI_GATEWAY_ID: 'overfit', AI_GATEWAY_TOKEN: 'gw-token' }));
    await fund(c);
    expect((await c.call('POST', '/v1/messages', body('pro'))).status).toBe(200);
    expect(anthropic.mock.calls.map((x: any[]) => JSON.parse(x[1].headers['cf-aig-metadata']))).toEqual([
      { tier: 'pro', photo: false, fallback: false },
      { tier: 'pro', photo: false, fallback: true },
    ]);
  });

  it('ohne Gateway-Token direkt an Anthropic (neue Gateways verlangen Authentifizierung)', async () => {
    const c = await Client.create(env({ AI_GATEWAY_ID: 'overfit' }));
    await fund(c);
    for (const t of ['lite', 'standard', 'pro']) expect((await c.call('POST', '/v1/messages', body(t))).status).toBe(200);
    for (const [url, init] of anthropic.mock.calls) {
      expect(url).toBe('https://api.anthropic.com/v1/messages');
      expect(init.headers['cf-aig-authorization']).toBeUndefined();
      expect(init.headers['cf-aig-metadata']).toBeUndefined();
    }
  });

  it('lehnt das Gateway ab (401, Cloudflare-Fehlerformat), geht dieselbe Anfrage direkt an den Anbieter', async () => {
    anthropic.mockImplementation(async (url: string, init: any) =>
      url.startsWith('https://gateway.test/')
        ? new Response(JSON.stringify({ success: false, result: [], messages: [], error: [{ code: 2009, message: 'Unauthorized' }] }), { status: 401 })
        : anthropicReply(sentModel(init)),
    );
    const c = await Client.create(env({ AI_GATEWAY_ID: 'overfit', AI_GATEWAY_TOKEN: 'falsch' }));
    await fund(c);
    const r = await c.call('POST', '/v1/messages', body('pro'));
    expect(r.status).toBe(200);
    expect(r.body.overfit).toMatchObject({ servedTier: 'pro', fallback: false, costMicro: at('pro') });
    expect(anthropic.mock.calls.map((x: any[]) => x[0])).toEqual(['https://gateway.test/overfit/anthropic/v1/messages', 'https://api.anthropic.com/v1/messages']);
    expect(sentModels()).toEqual([OPUS, OPUS]);
    expect(anthropic.mock.calls[1][1].headers['cf-aig-authorization']).toBeUndefined();
  });
});

describe('Schlüssel des Betreibers abgelehnt (401/403)', () => {
  it('gilt als Ausfall: weicht auf die Ausweich-Stufe aus und bucht nur dort ab', async () => {
    failFor([OPUS], async () => new Response(JSON.stringify({ type: 'error', error: { type: 'permission_error', message: 'no access to opus' } }), { status: 403 }));
    const c = await Client.create(env());
    const f = await fund(c);
    const r = await c.call('POST', '/v1/messages', body('pro'));
    expect(r.status).toBe(200);
    expect(r.body.overfit).toMatchObject({ tier: 'pro', servedTier: 'standard', fallback: true, costMicro: at('standard') });
    expect(await balance(c)).toBe(f.balanceMicro - at('standard'));
  });

  it('Schlüssel überall ungültig: 502 upstream_unavailable mit Fehlertext, nichts abgebucht', async () => {
    anthropic.mockImplementation(
      async () => new Response(JSON.stringify({ type: 'error', error: { type: 'authentication_error', message: 'invalid x-api-key' } }), { status: 401 }),
    );
    const c = await Client.create(env());
    const f = await fund(c);
    const r = await c.call('POST', '/v1/messages', body('lite'));
    expect(r.status).toBe(502);
    expect(r.body.error).toMatchObject({ code: 'upstream_unavailable', message: 'invalid x-api-key', details: { upstreamStatus: 401 } });
    expect(sentModels()).toEqual([HAIKU, SONNET]);
    expect(await balance(c)).toBe(f.balanceMicro);
  });
});

describe('/v1/config', () => {
  it('meldet Stufen, Claude-Modelle und Verfügbarkeit', async () => {
    const all = await rawFetch('/v1/config', env());
    expect(all.body.defaultTier).toBe('standard');
    expect(all.body.tiers.lite).toMatchObject({ available: true, model: HAIKU, vendor: 'Anthropic', images: true, fallback: { model: SONNET, provider: 'anthropic', displayName: 'Claude Sonnet 5.5' } });
    expect(all.body.tiers.standard).toMatchObject({ available: true, model: SONNET, vendor: 'Anthropic', images: true, fallback: { model: HAIKU } });
    expect(all.body.tiers.pro).toMatchObject({ available: true, model: OPUS, vendor: 'Anthropic', images: true, fallback: { model: SONNET, provider: 'anthropic', displayName: 'Claude Sonnet 5.5' } });
    for (const t of ['lite', 'standard', 'pro'] as Tier[]) expect(all.body.tiers[t].price).toEqual(price(t));
    // Keine Einladungen mehr (Fabian, 7.11.2026).
    expect(all.body.invite).toBeUndefined();
    const none = await rawFetch('/v1/config', baseEnv({ ANTHROPIC_API_KEY: undefined } as Partial<Env>));
    for (const t of ['lite', 'standard', 'pro']) expect(none.body.tiers[t].available).toBe(false);
  });

  it('unbekannte Stufe wird abgelehnt', async () => {
    const c = await Client.create(env());
    await fund(c);
    const r = await c.call('POST', '/v1/messages', body('ultra'));
    expect(r.body.error).toMatchObject({ code: 'model_not_allowed', details: { allowed: ['standard', 'pro', 'lite'] } });
    expect(anthropic).not.toHaveBeenCalled();
  });
});

describe('Preistabelle', () => {
  it('Claude-Listenpreise × 0,92 €/$; alte Modelle bleiben für alte Buchungen', () => {
    // Haiku 0,10 $ / 0,50 $, Cache-Lesen 0,01 $, Cache-Schreiben 1,25 × Eingabe
    expect(currentPrices()[HAIKU]).toEqual({ in: 92_000, cacheRead: 9_200, cacheWrite: 115_000, out: 460_000 });
    expect(currentPrices()[SONNET]).toEqual({ in: 1_840_000, cacheRead: 184_000, cacheWrite: 2_300_000, out: 9_200_000 });
    expect(currentPrices()[OPUS]).toEqual({ in: 3_680_000, cacheRead: 184_000, cacheWrite: 4_600_000, out: 18_400_000 });
    // Jede Stufe hat einen Preis für ihr Modell
    for (const t of Object.values(ROUTING.models)) expect(currentPrices()[t.id]).toBeDefined();
    // Gemini: zum Zeitpunkt gültiger Preis (ab 2027 teurer)
    expect(currentPrices(Date.parse('2026-12-31T23:00:00Z'))['gemini-3.6-flash']).toEqual({ in: 690_000, cacheRead: 69_000, cacheWrite: 690_000, out: 3_450_000 });
    expect(currentPrices(Date.parse('2027-01-01T00:00:00Z'))['gemini-3.6-flash']).toEqual({ in: 1_380_000, cacheRead: 138_000, cacheWrite: 1_380_000, out: 6_900_000 });
    // 0,293 $ / 2,253 $ je MTok × 0,92 €/$
    expect(currentPrices()['@cf/meta/llama-3.3-70b-instruct-fp8-fast']).toMatchObject({ in: 269_560, out: 2_072_760 });
    // 25 608 / 75 147 Neurons je MTok × 0,011 $/1000 Neurons × 0,92 €/$
    expect(currentPrices()['@cf/meta/llama-3.1-8b-instruct']).toMatchObject({ in: 259_153, out: 760_488 });
  });
});
