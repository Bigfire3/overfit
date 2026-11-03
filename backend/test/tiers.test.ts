import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { Client, baseEnv, fund, rawFetch } from './helpers';
import { costMicro } from '../src/money';
import { currentPrices, TIERS } from '../src/providers';
import type { Env } from '../src/types';

const MARKUP = 2800;
const price = (tier: keyof typeof TIERS) => currentPrices()[TIERS[tier].model];

const geminiReply = (meta: object = { promptTokenCount: 1000, candidatesTokenCount: 300, thoughtsTokenCount: 200 }, finishReason = 'STOP', text = '{"ok":true}') =>
  Response.json({
    candidates: [{ content: { role: 'model', parts: [{ text: 'denke…', thought: true }, { text }] }, finishReason }],
    usageMetadata: meta,
    responseId: 'g1',
  });
const anthropicReply = (usage: object = { input_tokens: 1000, output_tokens: 500 }) =>
  Response.json({ id: 'msg_1', type: 'message', role: 'assistant', model: 'claude-sonnet-5-5', content: [{ type: 'text', text: 'pro' }], stop_reason: 'end_turn', usage });

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

let gemini: any;
let anthropic: any;
let ai: { run: any; gateway: any };
beforeEach(() => {
  gemini = vi.fn(async () => geminiReply());
  anthropic = vi.fn(async () => anthropicReply());
  ai = {
    run: vi.fn(async () => ({ response: '{"ok":true}', usage: { prompt_tokens: 400, completion_tokens: 100 } })),
    gateway: vi.fn((id: string) => ({ getUrl: async (p: string) => `https://gateway.test/${id}/${p}` })),
  };
  const real = globalThis.fetch;
  vi.stubGlobal('fetch', (input: any, init?: any) => {
    const url = String(input instanceof Request ? input.url : input);
    if (url.startsWith('https://generativelanguage.googleapis.com') || url.startsWith('https://gateway.test/overfit/google-ai-studio')) return gemini(url, init);
    if (url.startsWith('https://api.anthropic.com') || url.startsWith('https://gateway.test/overfit/anthropic')) return anthropic(url, init);
    return real(input, init);
  });
});
afterEach(() => vi.unstubAllGlobals());

const env = (extra: Partial<Env> = {}) => baseEnv({ AI: ai, ...extra } as Partial<Env>);
const balance = async (c: Client) => (await c.call('GET', '/v1/account')).body.balanceMicro as number;
const lastUsage = async (c: Client) => (await c.call('GET', '/v1/account/ledger')).body.entries[0];

describe('Stufe standard (Gemini)', () => {
  it('bildet Anthropic-Format auf Gemini ab und zurück, rechnet nach Gemini-Preis ab', async () => {
    const c = await Client.create(env());
    const f = await fund(c);
    const r = await c.call('POST', '/v1/messages', body('standard', { messages: [{ role: 'user', content: [IMG, { type: 'text', text: 'Bild' }] }] }));
    expect(r.status).toBe(200);
    expect(r.body).toMatchObject({ type: 'message', role: 'assistant', model: 'gemini-3.6-flash', stop_reason: 'end_turn', content: [{ type: 'text', text: '{"ok":true}' }] });
    expect(r.body.usage).toMatchObject({ input_tokens: 1000, output_tokens: 500 });
    const expected = costMicro({ input_tokens: 1000, output_tokens: 500 }, price('standard'), MARKUP);
    expect(r.body.overfit).toMatchObject({ costMicro: expected, tier: 'standard', servedTier: 'standard', model: 'gemini-3.6-flash', fallback: false });
    expect(await balance(c)).toBe(f.balanceMicro - expected);

    const [url, init] = gemini.mock.calls[0];
    expect(url).toBe('https://generativelanguage.googleapis.com/v1beta/models/gemini-3.6-flash:generateContent');
    expect(init.headers['x-goog-api-key']).toBe('gemini-key');
    const sent = JSON.parse(init.body);
    expect(sent.systemInstruction.parts[0].text).toBe('Regeln\n\nProfil');
    expect(sent.contents[0]).toEqual({ role: 'user', parts: [{ inlineData: { mimeType: 'image/jpeg', data: 'QUJD' } }, { text: 'Bild' }] });
    expect(sent.generationConfig).toMatchObject({ maxOutputTokens: 4096, responseMimeType: 'application/json', responseJsonSchema: { type: 'object' } });
    expect(anthropic).not.toHaveBeenCalled();
    expect(await lastUsage(c)).toMatchObject({ type: 'usage', amountMicro: -expected, model: 'gemini-3.6-flash', tier: 'standard', inputTokens: 1000, outputTokens: 500 });
  });

  it('Cache-Treffer, Abbruch wegen Länge und Ablehnung werden übersetzt', async () => {
    const c = await Client.create(env());
    await fund(c);
    gemini.mockResolvedValueOnce(geminiReply({ promptTokenCount: 5000, cachedContentTokenCount: 4000, candidatesTokenCount: 10 }, 'MAX_TOKENS'));
    const r = await c.call('POST', '/v1/messages', body('standard'));
    expect(r.body.stop_reason).toBe('max_tokens');
    expect(r.body.usage).toMatchObject({ input_tokens: 1000, cache_read_input_tokens: 4000, output_tokens: 10 });
    expect(r.body.overfit.costMicro).toBe(costMicro({ input_tokens: 1000, cache_read_input_tokens: 4000, output_tokens: 10 }, price('standard'), MARKUP));
    gemini.mockResolvedValueOnce(Response.json({ promptFeedback: { blockReason: 'SAFETY' }, usageMetadata: { promptTokenCount: 10 } }));
    const r2 = await c.call('POST', '/v1/messages', body('standard'));
    expect(r2.body.stop_reason).toBe('refusal');
    expect(r2.body.content).toEqual([]);
  });
});

describe('Stufe pro (Anthropic)', () => {
  it('schickt den Body mit Modell und effort weiter und rechnet nach Sonnet ab', async () => {
    const c = await Client.create(env());
    await fund(c);
    const r = await c.call('POST', '/v1/messages', body('pro'));
    expect(r.status).toBe(200);
    expect(r.body.content[0].text).toBe('pro');
    expect(r.body.overfit).toMatchObject({ tier: 'pro', model: 'claude-sonnet-5-5', costMicro: 8244 });
    const sent = JSON.parse(anthropic.mock.calls[0][1].body);
    expect(sent.model).toBe('claude-sonnet-5-5');
    expect(sent.output_config).toMatchObject({ effort: 'medium', format: { type: 'json_schema' } });
    expect(sent.system[0].cache_control).toEqual({ type: 'ephemeral' });
    expect(await lastUsage(c)).toMatchObject({ tier: 'pro', model: 'claude-sonnet-5-5' });
  });
});

describe('Stufe lite (Workers AI)', () => {
  it('nutzt das AI-Binding mit JSON-Modus und rechnet nach Neurons-Preis ab', async () => {
    const c = await Client.create(env());
    await fund(c);
    const r = await c.call('POST', '/v1/messages', body('lite'));
    expect(r.status).toBe(200);
    expect(r.body).toMatchObject({ model: '@cf/meta/llama-3.1-8b-instruct', content: [{ type: 'text', text: '{"ok":true}' }], stop_reason: 'end_turn' });
    const expected = costMicro({ input_tokens: 400, output_tokens: 100 }, price('lite'), MARKUP);
    expect(r.body.overfit).toMatchObject({ tier: 'lite', costMicro: expected });
    const [model, inputs] = ai.run.mock.calls[0];
    expect(model).toBe('@cf/meta/llama-3.1-8b-instruct');
    expect(inputs.messages).toEqual([{ role: 'system', content: 'Regeln\n\nProfil' }, { role: 'user', content: 'Hallo' }]);
    expect(inputs.response_format).toEqual({ type: 'json_schema', json_schema: { type: 'object', properties: { ok: { type: 'boolean' } } } });
    expect(inputs.max_tokens).toBe(1000);
    expect(await lastUsage(c)).toMatchObject({ tier: 'lite', model: '@cf/meta/llama-3.1-8b-instruct' });
  });

  it('JSON als Objekt wird zu Text; ohne usage wird der reservierte Betrag berechnet', async () => {
    const c = await Client.create(env());
    const f = await fund(c);
    ai.run.mockResolvedValueOnce({ response: { ok: true } });
    const r = await c.call('POST', '/v1/messages', body('lite'));
    expect(r.body.content[0].text).toBe('{"ok":true}');
    expect(r.body.overfit.costMicro).toBeGreaterThan(0);
    expect(await balance(c)).toBe(f.balanceMicro - r.body.overfit.costMicro);
  });

  it('weist Bilder ab, ohne zu reservieren oder anzufragen', async () => {
    const c = await Client.create(env());
    const f = await fund(c);
    const r = await c.call('POST', '/v1/messages', body('lite', { messages: [{ role: 'user', content: [IMG, { type: 'text', text: 'x' }] }] }));
    expect(r.status).toBe(400);
    expect(r.body.error.code).toBe('images_not_supported');
    expect(ai.run).not.toHaveBeenCalled();
    expect(await balance(c)).toBe(f.balanceMicro);
  });

  it('ohne AI-Binding nicht verfügbar', async () => {
    const c = await Client.create(baseEnv());
    await fund(c);
    const r = await c.call('POST', '/v1/messages', body('lite'));
    expect(r.status).toBe(503);
    expect(r.body.error).toMatchObject({ code: 'tier_unavailable', details: { tier: 'lite', available: ['standard', 'pro'] } });
  });
});

describe('Ausweichen bei Ausfall', () => {
  it('standard → pro bei 5xx; berechnet höchstens den Standard-Preis', async () => {
    gemini.mockResolvedValue(Response.json({ error: { message: 'overloaded' } }, { status: 503 }));
    const c = await Client.create(env());
    const f = await fund(c);
    const r = await c.call('POST', '/v1/messages', body('standard'));
    expect(r.status).toBe(200);
    expect(r.body.content[0].text).toBe('pro');
    const usage = { input_tokens: 1000, output_tokens: 500 };
    const atStandard = costMicro(usage, price('standard'), MARKUP);
    expect(atStandard).toBeLessThan(8244);
    expect(r.body.overfit).toMatchObject({ tier: 'standard', servedTier: 'pro', model: 'claude-sonnet-5-5', fallback: true, costMicro: atStandard });
    expect(await balance(c)).toBe(f.balanceMicro - atStandard);
    expect(await lastUsage(c)).toMatchObject({ tier: 'standard', model: 'claude-sonnet-5-5', amountMicro: -atStandard });
  });

  it('pro → standard bei Zeitüberschreitung; berechnet den günstigeren Preis', async () => {
    anthropic.mockRejectedValue(new DOMException('timeout', 'TimeoutError'));
    const c = await Client.create(env());
    await fund(c);
    const r = await c.call('POST', '/v1/messages', body('pro'));
    expect(r.status).toBe(200);
    expect(r.body.overfit).toMatchObject({ tier: 'pro', servedTier: 'standard', fallback: true });
    expect(r.body.overfit.costMicro).toBe(costMicro({ input_tokens: 1000, output_tokens: 500 }, price('standard'), MARKUP));
  });

  it('lite → standard, wenn Workers AI ausfällt', async () => {
    ai.run.mockRejectedValue(new Error('3040: Capacity temporarily exceeded'));
    const c = await Client.create(env());
    await fund(c);
    const r = await c.call('POST', '/v1/messages', body('lite'));
    expect(r.status).toBe(200);
    expect(r.body.overfit).toMatchObject({ tier: 'lite', servedTier: 'standard', fallback: true });
    // Nie mehr als der Lite-Preis für dieselben Token
    expect(r.body.overfit.costMicro).toBe(costMicro({ input_tokens: 1000, output_tokens: 500 }, price('lite'), MARKUP));
  });

  it('beide Anbieter fallen aus → 502, Reservierung vollständig zurück', async () => {
    gemini.mockResolvedValue(new Response('kaputt', { status: 500 }));
    anthropic.mockResolvedValue(Response.json({ error: { message: 'overloaded' } }, { status: 529 }));
    const c = await Client.create(env());
    const f = await fund(c);
    const r = await c.call('POST', '/v1/messages', body('standard'));
    expect(r.status).toBe(502);
    expect(r.body.error.code).toBe('upstream_unavailable');
    expect(await balance(c)).toBe(f.balanceMicro);
    expect((await c.call('GET', '/v1/account/ledger')).body.entries.filter((e: any) => e.type === 'usage')).toEqual([]);
  });

  it('Abbruch (Netzwerk) bei beiden → 504, nichts abgebucht', async () => {
    gemini.mockRejectedValue(new TypeError('network'));
    anthropic.mockRejectedValue(new TypeError('network'));
    const c = await Client.create(env());
    const f = await fund(c);
    const r = await c.call('POST', '/v1/messages', body('standard'));
    expect(r.status).toBe(504);
    expect(await balance(c)).toBe(f.balanceMicro);
  });

  it('Ablehnung (400) weicht nicht aus und bucht nichts ab', async () => {
    gemini.mockResolvedValue(Response.json({ error: { message: 'schema unsupported' } }, { status: 400 }));
    const c = await Client.create(env());
    const f = await fund(c);
    const r = await c.call('POST', '/v1/messages', body('standard'));
    expect(r.status).toBe(400);
    expect(r.body.error).toMatchObject({ code: 'upstream_rejected', message: 'schema unsupported' });
    expect(anthropic).not.toHaveBeenCalled();
    expect(await balance(c)).toBe(f.balanceMicro);
  });

  it('ohne Anthropic-Key kein Ausweichen', async () => {
    gemini.mockResolvedValue(new Response('x', { status: 503 }));
    const c = await Client.create(env({ ANTHROPIC_API_KEY: undefined }));
    await fund(c);
    expect((await c.call('POST', '/v1/messages', body('standard'))).status).toBe(502);
    expect(anthropic).not.toHaveBeenCalled();
  });
});

describe('AI Gateway', () => {
  it('leitet alle Anbieter über das Gateway; bei Fotos nur Metadaten, keine Inhalte', async () => {
    const c = await Client.create(env({ AI_GATEWAY_ID: 'overfit' }));
    await fund(c);
    await c.call('POST', '/v1/messages', body('standard', { messages: [{ role: 'user', content: [IMG] }] }));
    const [gUrl, gInit] = gemini.mock.calls[0];
    expect(gUrl).toBe('https://gateway.test/overfit/google-ai-studio/v1beta/models/gemini-3.6-flash:generateContent');
    // Nicht „collect-log: false“ (das würde auch Kosten und Tokens unterdrücken), sondern nur die Inhalte abschalten.
    expect(gInit.headers['cf-aig-collect-log']).toBeUndefined();
    expect(gInit.headers['cf-aig-collect-log-payload']).toBe('false');
    expect(JSON.parse(gInit.headers['cf-aig-metadata'])).toEqual({ tier: 'standard', photo: true, fallback: false });
    await c.call('POST', '/v1/messages', body('pro'));
    const [aUrl, aInit] = anthropic.mock.calls[0];
    expect(aUrl).toBe('https://gateway.test/overfit/anthropic/v1/messages');
    expect(aInit.headers['cf-aig-collect-log']).toBeUndefined();
    expect(aInit.headers['cf-aig-collect-log-payload']).toBeUndefined(); // ohne Foto: normales Logging
    expect(JSON.parse(aInit.headers['cf-aig-metadata'])).toEqual({ tier: 'pro', photo: false, fallback: false });
    await c.call('POST', '/v1/messages', body('lite'));
    expect(ai.run.mock.calls[0][2]).toEqual({ gateway: { id: 'overfit', metadata: { tier: 'lite', photo: false, fallback: false } } });
  });
});

describe('/v1/config', () => {
  it('meldet Stufen und Verfügbarkeit', async () => {
    const all = await rawFetch('/v1/config', env());
    expect(all.body.defaultTier).toBe('standard');
    expect(all.body.tiers.standard).toMatchObject({ available: true, model: 'gemini-3.6-flash', images: true, fallback: 'pro' });
    expect(all.body.tiers.pro).toMatchObject({ available: true, model: 'claude-sonnet-5-5' });
    expect(all.body.tiers.lite).toMatchObject({ available: true, images: false });
    expect(all.body.tiers.standard.price.out).toBe(price('standard').out);
    const noPro = await rawFetch('/v1/config', baseEnv({ ANTHROPIC_API_KEY: undefined }));
    expect(noPro.body.tiers.pro.available).toBe(false);
    expect(noPro.body.tiers.lite.available).toBe(false);
  });

  it('unbekannte Stufe wird abgelehnt', async () => {
    const c = await Client.create(env());
    await fund(c);
    const r = await c.call('POST', '/v1/messages', body('ultra'));
    expect(r.body.error).toMatchObject({ code: 'model_not_allowed', details: { allowed: ['standard', 'pro', 'lite'] } });
  });
});

describe('Preistabelle', () => {
  it('nimmt den zum Zeitpunkt gültigen Preis (Gemini ab 2027 teurer) und rechnet Neurons um', () => {
    expect(currentPrices(Date.parse('2026-12-31T23:00:00Z'))['gemini-3.6-flash']).toEqual({ in: 690_000, cacheRead: 69_000, cacheWrite: 690_000, out: 3_450_000 });
    expect(currentPrices(Date.parse('2027-01-01T00:00:00Z'))['gemini-3.6-flash']).toEqual({ in: 1_380_000, cacheRead: 138_000, cacheWrite: 1_380_000, out: 6_900_000 });
    expect(currentPrices()['claude-sonnet-5-5']).toEqual({ in: 1_840_000, cacheRead: 184_000, cacheWrite: 2_300_000, out: 9_200_000 });
    // 25 608 / 75 147 Neurons je MTok × 0,011 $/1000 Neurons × 0,92 €/$
    expect(currentPrices()['@cf/meta/llama-3.1-8b-instruct']).toMatchObject({ in: 259_153, out: 760_488 });
  });
});
