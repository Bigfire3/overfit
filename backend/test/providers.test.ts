import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { Client, baseEnv, fund, rawFetch } from './helpers';
import { costMicro } from '../src/money';
import {
  currentPrices, geminiRequest, geminiToAnthropic, openAiRequest, openAiToAnthropic, upstreamErrorFor, workersAiRequest, workersAiToAnthropic,
} from '../src/providers';
import type { Env } from '../src/types';

const MARKUP = 2800;
const IMG = { type: 'image', source: { type: 'base64', media_type: 'image/png', data: 'QUJD' } };
const SCHEMA = { type: 'object', properties: { ok: { type: 'boolean' } } };
const body = (model: string, extra: object = {}) => ({
  model,
  max_tokens: 1000,
  system: [{ type: 'text', text: 'Regeln', cache_control: { type: 'ephemeral' } }, { type: 'text', text: 'Profil' }],
  messages: [{ role: 'user', content: 'Hallo' }],
  output_config: { format: { type: 'json_schema', schema: SCHEMA } },
  ...extra,
});
const cost = (model: string, usage: object) => costMicro(usage as never, currentPrices()[model], MARKUP);

describe('Adapter: Übersetzung Anthropic ↔ Anbieter', () => {
  it('OpenAI: developer-Prompt, Bild als Data-URL, JSON-Schema, max_completion_tokens, effort', () => {
    const req = openAiRequest(
      body('x', { messages: [{ role: 'user', content: [{ type: 'text', text: 'Foto:' }, IMG] }, { role: 'assistant', content: 'ok' }], temperature: 0.3 }),
      'gpt-5', 4096, 'openai',
    );
    expect(req).toMatchObject({
      model: 'gpt-5',
      max_completion_tokens: 4096,
      reasoning_effort: 'medium',
      response_format: { type: 'json_schema', json_schema: { name: 'overfit', schema: SCHEMA, strict: false } },
    });
    expect(req.temperature).toBeUndefined();
    expect(req.messages).toEqual([
      { role: 'developer', content: 'Regeln\n\nProfil' },
      { role: 'user', content: [{ type: 'text', text: 'Foto:' }, { type: 'image_url', image_url: { url: 'data:image/png;base64,QUJD' } }] },
      { role: 'assistant', content: 'ok' },
    ]);
  });

  it('OpenRouter: system-Rolle, max_tokens, Temperatur bleibt', () => {
    const req = openAiRequest(body('x', { temperature: 0.2 }), 'anthropic/claude-sonnet-5.5', 1000, 'openrouter');
    expect(req).toMatchObject({ max_tokens: 1000, temperature: 0.2, messages: [{ role: 'system', content: 'Regeln\n\nProfil' }, { role: 'user', content: 'Hallo' }] });
    expect(req.max_completion_tokens).toBeUndefined();
  });

  it('OpenAI-Antwort → Anthropic: Text, Stop-Grund, Token mit Cache', () => {
    const n = openAiToAnthropic({
      id: 'chatcmpl-1',
      choices: [{ message: { content: '{"ok":true}' }, finish_reason: 'length' }],
      usage: { prompt_tokens: 1000, completion_tokens: 300, prompt_tokens_details: { cached_tokens: 400 } },
    }, 'gpt-5');
    expect(n.usage).toEqual({ input_tokens: 600, cache_read_input_tokens: 400, cache_creation_input_tokens: 0, output_tokens: 300 });
    expect(n.body).toMatchObject({ type: 'message', role: 'assistant', model: 'gpt-5', content: [{ type: 'text', text: '{"ok":true}' }], stop_reason: 'max_tokens' });
    const refused = openAiToAnthropic({ choices: [{ message: { content: null, refusal: 'Nein' }, finish_reason: 'stop' }] }, 'gpt-5');
    expect(refused.body.stop_reason).toBe('refusal');
    expect(refused.usage).toBeNull();
  });

  it('Gemini: Rollen, Bilder, Schema; Antwort ohne Denk-Text, Denk-Token als Ausgabe', () => {
    const req = geminiRequest(body('x', { messages: [{ role: 'user', content: [IMG, { type: 'text', text: 'Wie?' }] }] }), 2000) as any;
    expect(req.systemInstruction).toEqual({ parts: [{ text: 'Regeln\n\nProfil' }] });
    expect(req.contents[0]).toEqual({ role: 'user', parts: [{ inlineData: { mimeType: 'image/png', data: 'QUJD' } }, { text: 'Wie?' }] });
    expect(req.generationConfig).toMatchObject({ maxOutputTokens: 2000, responseMimeType: 'application/json', responseJsonSchema: SCHEMA });
    const n = geminiToAnthropic({
      responseId: 'r1',
      candidates: [{ content: { parts: [{ text: 'denke…', thought: true }, { text: '{"ok":true}' }] }, finishReason: 'STOP' }],
      usageMetadata: { promptTokenCount: 900, cachedContentTokenCount: 100, candidatesTokenCount: 50, thoughtsTokenCount: 200 },
    }, 'gemini-3.6-flash');
    expect(n.body.content).toEqual([{ type: 'text', text: '{"ok":true}' }]);
    expect(n.usage).toEqual({ input_tokens: 800, cache_read_input_tokens: 100, cache_creation_input_tokens: 0, output_tokens: 250 });
    expect(geminiToAnthropic({ promptFeedback: { blockReason: 'SAFETY' } }, 'g').body.stop_reason).toBe('refusal');
  });

  it('Workers AI: nur Text, JSON-Modus; Antwort alt ({response}) und neu ({choices})', () => {
    const req = workersAiRequest(body('x', { messages: [{ role: 'user', content: [{ type: 'text', text: 'A' }, IMG] }] }), 500) as any;
    expect(req.messages).toEqual([{ role: 'system', content: 'Regeln\n\nProfil' }, { role: 'user', content: 'A' }]);
    expect(req.response_format).toEqual({ type: 'json_schema', json_schema: SCHEMA });
    expect(workersAiToAnthropic({ response: { ok: true }, usage: { prompt_tokens: 10, completion_tokens: 5 } }, 'm', 500).body.content).toEqual([{ type: 'text', text: '{"ok":true}' }]);
    const n = workersAiToAnthropic({ choices: [{ message: { content: 'hi' } }], usage: { prompt_tokens: 10, completion_tokens: 500 } }, 'm', 500);
    expect(n.body).toMatchObject({ content: [{ type: 'text', text: 'hi' }], stop_reason: 'max_tokens' });
    expect(n.usage).toEqual({ input_tokens: 10, output_tokens: 500 });
  });

  it('Fehler einheitlich: Ausfall (5xx, 429, 408, 401, 403) vs. Ablehnung (4xx), Meldung aus allen Formaten', () => {
    for (const s of [500, 503, 529, 429, 408, 401, 402, 403, 404]) expect(upstreamErrorFor(s, null).unavailable, String(s)).toBe(true);
    for (const s of [400, 413, 422]) expect(upstreamErrorFor(s, null).unavailable, String(s)).toBe(false);
    expect(upstreamErrorFor(400, { error: { message: 'kaputt' } }).message).toBe('kaputt');
    expect(upstreamErrorFor(401, { error: [{ code: 1, message: 'gw' }] }).message).toBe('gw');
    expect(upstreamErrorFor(402, { error: 'kein Guthaben' }).message).toBe('kein Guthaben');
  });
});

// ---- Ende zu Ende über /v1/messages ----------------------------------------------------------------------------

let calls: { url: string; init: any }[];
let replies: Record<string, (init: any) => Promise<Response> | Response>;
let ai: { run: any; gateway: any };
const OPENAI_USAGE = { prompt_tokens: 1000, completion_tokens: 500, prompt_tokens_details: { cached_tokens: 0 } };
const GEMINI_USAGE = { promptTokenCount: 1000, candidatesTokenCount: 300, thoughtsTokenCount: 200 };
const okOpenAi = (text = 'von openai') => Response.json({ id: 'c1', choices: [{ message: { content: text }, finish_reason: 'stop' }], usage: OPENAI_USAGE });
const okGemini = () => Response.json({ responseId: 'g1', candidates: [{ content: { parts: [{ text: 'von gemini' }] }, finishReason: 'STOP' }], usageMetadata: GEMINI_USAGE });
const okAnthropic = (init: any) => Response.json({
  id: 'msg_1', type: 'message', role: 'assistant', model: JSON.parse(init.body).model, content: [{ type: 'text', text: 'von anthropic' }],
  stop_reason: 'end_turn', usage: { input_tokens: 1000, output_tokens: 500 },
});
const down = (status = 503) => Response.json({ error: { message: 'down' } }, { status });

beforeEach(() => {
  calls = [];
  replies = { anthropic: okAnthropic, openai: () => okOpenAi(), gemini: () => okGemini(), openrouter: () => okOpenAi('von openrouter') };
  ai = {
    run: vi.fn(async () => ({ response: 'von llama', usage: { prompt_tokens: 1000, completion_tokens: 500 } })),
    gateway: vi.fn(() => ({ getUrl: async (p: string) => `https://gateway.test/${p}` })),
  };
  const real = globalThis.fetch;
  vi.stubGlobal('fetch', (input: any, init?: any) => {
    const url = String(input instanceof Request ? input.url : input);
    const hit = ([
      ['https://api.anthropic.com', 'anthropic'], ['https://api.openai.com', 'openai'],
      ['https://generativelanguage.googleapis.com', 'gemini'], ['https://openrouter.ai', 'openrouter'],
    ] as const).find(([p]) => url.startsWith(p));
    if (hit) {
      calls.push({ url, init });
      return Promise.resolve(replies[hit[1]](init));
    }
    return real(input, init);
  });
});
afterEach(() => vi.unstubAllGlobals());

const keys = { ANTHROPIC_API_KEY: 'sk-ant', OPENAI_API_KEY: 'sk-openai', GEMINI_API_KEY: 'g-key', OPENROUTER_API_KEY: 'or-key' };
const env = (preset: string, extra: Partial<Env> = {}) => baseEnv({ ...keys, LLM_PRESET: preset, AI: ai, ...extra } as Partial<Env>);
const lastRequest = async (c: Client) => (await c.call('GET', '/v1/account/requests')).body.entries[0];
const balance = async (c: Client) => (await c.call('GET', '/v1/account')).body.balanceMicro as number;

describe('Voreinstellungen Ende zu Ende', () => {
  it('openai: GPT-5 beantwortet standard, Abrechnung nach echten Token und GPT-5-Preis, Protokoll mit Anbieter', async () => {
    const c = await Client.create(env('openai'));
    const f = await fund(c);
    const r = await c.call('POST', '/v1/messages', body('standard'));
    expect(r.status).toBe(200);
    expect(r.body.content).toEqual([{ type: 'text', text: 'von openai' }]);
    const expected = cost('gpt-5', { input_tokens: 1000, output_tokens: 500 });
    expect(r.body.overfit).toMatchObject({ costMicro: expected, model: 'gpt-5', modelName: 'GPT-5', vendor: 'OpenAI', provider: 'openai', fallback: false });
    expect(calls[0].url).toBe('https://api.openai.com/v1/chat/completions');
    expect(calls[0].init.headers.authorization).toBe('Bearer sk-openai');
    // GPT-5 denkt: doppelter Ausgabe-Raum
    expect(JSON.parse(calls[0].init.body).max_completion_tokens).toBe(4096);
    expect(await balance(c)).toBe(f.balanceMicro - expected);
    expect(await lastRequest(c)).toMatchObject({ model: 'gpt-5', provider: 'openai', requestedProvider: 'openai', chargedMicro: expected });
  });

  it('gemini: Gemini über den Google-Schlüssel, Denk-Token zählen als Ausgabe', async () => {
    const c = await Client.create(env('gemini'));
    await fund(c);
    const r = await c.call('POST', '/v1/messages', body('lite'));
    expect(r.status).toBe(200);
    expect(calls[0].url).toBe('https://generativelanguage.googleapis.com/v1beta/models/gemini-3.6-flash:generateContent');
    expect(calls[0].init.headers['x-goog-api-key']).toBe('g-key');
    expect(r.body.overfit).toMatchObject({ costMicro: cost('gemini-3.6-flash', { input_tokens: 1000, output_tokens: 500 }), provider: 'gemini', vendor: 'Google' });
  });

  it('openrouter: Claude über OpenRouter', async () => {
    const c = await Client.create(env('openrouter'));
    await fund(c);
    const r = await c.call('POST', '/v1/messages', body('pro'));
    expect(r.status).toBe(200);
    expect(calls[0].url).toBe('https://openrouter.ai/api/v1/chat/completions');
    expect(JSON.parse(calls[0].init.body).model).toBe('anthropic/claude-opus-5.5');
    expect(r.body.overfit).toMatchObject({ provider: 'openrouter', model: 'anthropic/claude-opus-5.5' });
  });

  it('gratis: Workers AI über das Binding; Foto-Anfrage geht gleich an das Ausweich-Modell (kann Bilder)', async () => {
    const c = await Client.create(env('gratis'));
    await fund(c);
    const r = await c.call('POST', '/v1/messages', body('standard'));
    expect(r.status).toBe(200);
    expect(ai.run).toHaveBeenCalledWith('@cf/meta/llama-4-scout-17b-16e-instruct', expect.anything(), {});
    expect(r.body.overfit).toMatchObject({ provider: 'workers-ai', vendor: 'Cloudflare', costMicro: cost('@cf/meta/llama-4-scout-17b-16e-instruct', { input_tokens: 1000, output_tokens: 500 }) });
    expect(calls).toHaveLength(0);

    const photo = await c.call('POST', '/v1/messages', body('standard', { messages: [{ role: 'user', content: [IMG, { type: 'text', text: 'Form?' }] }] }));
    expect(photo.status).toBe(200);
    expect(photo.body.overfit).toMatchObject({ model: 'claude-haiku-5-5', provider: 'anthropic', fallback: true, fallbackReason: 'keine Bilder' });
    // Bezahlt wird das günstigere: Llama-Preis, nicht Haiku.
    const usage = { input_tokens: 1000, output_tokens: 500 };
    expect(photo.body.overfit.costMicro).toBe(Math.min(cost('claude-haiku-5-5', usage), cost('@cf/meta/llama-4-scout-17b-16e-instruct', usage)));
  });

  it('Foto ohne bildfähiges Modell in der Stufe → images_not_supported, nichts abgebucht', async () => {
    const c = await Client.create(env('gratis', { ANTHROPIC_API_KEY: undefined }));
    const f = await fund(c);
    const r = await c.call('POST', '/v1/messages', body('lite', { messages: [{ role: 'user', content: [IMG] }] }));
    expect(r.status).toBe(400);
    expect(r.body.error.code).toBe('images_not_supported');
    expect(await balance(c)).toBe(f.balanceMicro);
  });
});

describe('Ausweichen über Anbietergrenzen', () => {
  it('gemischt: Sonnet fällt aus (529) → Gemini antwortet; bezahlt wird das günstigere Modell, Protokoll nennt beide Anbieter', async () => {
    replies.anthropic = () => down(529);
    const c = await Client.create(env('gemischt'));
    const f = await fund(c);
    const r = await c.call('POST', '/v1/messages', body('standard'));
    expect(r.status).toBe(200);
    expect(r.body.content).toEqual([{ type: 'text', text: 'von gemini' }]);
    const usage = { input_tokens: 1000, output_tokens: 500 };
    const expected = Math.min(cost('gemini-3.6-flash', usage), cost('claude-sonnet-5-5', usage));
    expect(r.body.overfit).toMatchObject({
      tier: 'standard', model: 'gemini-3.6-flash', provider: 'gemini', requestedModel: 'claude-sonnet-5-5', requestedProvider: 'anthropic',
      fallback: true, fallbackReason: 'HTTP 529', costMicro: expected,
    });
    expect(await balance(c)).toBe(f.balanceMicro - expected);
    expect(await lastRequest(c)).toMatchObject({ requestedModel: 'claude-sonnet-5-5', requestedProvider: 'anthropic', model: 'gemini-3.6-flash', provider: 'gemini', fallbackFromStatus: 529 });
    const ledger = (await c.call('GET', '/v1/account/ledger')).body.entries[0];
    expect(ledger).toMatchObject({ type: 'usage', model: 'gemini-3.6-flash', requestedModel: 'claude-sonnet-5-5', fallback: true, amountMicro: -expected });
  });

  it('gemischt: Gemini (lite) fällt aus → Haiku antwortet, berechnet höchstens den Gemini-Preis', async () => {
    replies.gemini = () => down(500);
    const c = await Client.create(env('gemischt'));
    await fund(c);
    const r = await c.call('POST', '/v1/messages', body('lite'));
    expect(r.status).toBe(200);
    const usage = { input_tokens: 1000, output_tokens: 500 };
    expect(r.body.overfit).toMatchObject({ model: 'claude-haiku-5-5', provider: 'anthropic', fallback: true });
    expect(r.body.overfit.costMicro).toBe(Math.min(cost('claude-haiku-5-5', usage), cost('gemini-3.6-flash', usage)));
  });

  it('gratis: Workers AI wirft (Tageskontingent) → Claude Haiku', async () => {
    ai.run.mockRejectedValueOnce(new Error('4006: daily free allocation exceeded'));
    const c = await Client.create(env('gratis'));
    await fund(c);
    const r = await c.call('POST', '/v1/messages', body('lite'));
    expect(r.status).toBe(200);
    expect(r.body.overfit).toMatchObject({ model: 'claude-haiku-5-5', fallback: true, fallbackReason: 'HTTP 502' });
  });

  it('Ablehnung (400) beim ersten Anbieter: kein Ausweichen, nichts abgebucht', async () => {
    replies.openai = () => down(400);
    const c = await Client.create(env('openai'));
    const f = await fund(c);
    const r = await c.call('POST', '/v1/messages', body('standard'));
    expect(r.body.error.code).toBe('upstream_rejected');
    expect(calls).toHaveLength(1);
    expect(await balance(c)).toBe(f.balanceMicro);
  });

  it('beide fallen aus → 502, nichts abgebucht', async () => {
    replies.openai = () => down(503);
    const c = await Client.create(env('openai'));
    const f = await fund(c);
    const r = await c.call('POST', '/v1/messages', body('lite'));
    expect(r.status).toBe(502);
    expect(calls.map((x) => JSON.parse(x.init.body).model)).toEqual(['gpt-5-mini', 'gpt-5']);
    expect(await balance(c)).toBe(f.balanceMicro);
  });
});

describe('/v1/config zeigt die wirksame Zuordnung', () => {
  it('openai: echte Namen, Preise, Anbieter, Ausweich-Modell, Namensliste', async () => {
    const r = await rawFetch('/v1/config', env('openai'));
    expect(r.body.preset).toBe('openai');
    expect(r.body.tiers.standard).toMatchObject({
      available: true, model: 'gpt-5', provider: 'openai', displayName: 'GPT-5', vendor: 'OpenAI', label: 'GPT-5 (OpenAI)',
      fallback: { model: 'gpt-5-mini', displayName: 'GPT-5 mini' }, price: currentPrices()['gpt-5'],
    });
    expect(r.body.modelNames['claude-sonnet-5-5']).toEqual({ displayName: 'Claude Sonnet 5.5', vendor: 'Anthropic' });
    expect(r.body.modelNames['@cf/meta/llama-3.1-8b-instruct']).toBeDefined();
  });

  it('Stufe ohne bereites Modell: price bleibt ein Objekt, provider ein String (ältere Apps parsen beides nicht nullable)', async () => {
    const r = await rawFetch('/v1/config', baseEnv({ ANTHROPIC_API_KEY: undefined } as Partial<Env>));
    expect(r.body.tiers.standard).toMatchObject({ available: false, provider: '', price: { in: 0, out: 0, cacheRead: 0, cacheWrite: 0 } });
  });

  it('fehlender Schlüssel: Rückfall auf die nächste vollständige Voreinstellung', async () => {
    const r = await rawFetch('/v1/config', env('openai', { OPENAI_API_KEY: undefined }));
    expect(r.body.preset).toBe('claude');
    expect(r.body.tiers.standard.model).toBe('claude-sonnet-5-5');
  });
});

describe('providerErrorCode: nur der Code, nie der Text', () => {
  it('liest Anthropic, OpenAI, Gemini und Cloudflare', async () => {
    const { providerErrorCode, upstreamErrorFor } = await import('../src/providers');
    expect(providerErrorCode({ type: 'error', error: { type: 'invalid_request_error', message: 'max_tokens: too large' } })).toBe('invalid_request_error');
    expect(providerErrorCode({ error: { message: 'x', type: 'invalid_request_error', code: 'model_not_found' } })).toBe('model_not_found');
    expect(providerErrorCode({ error: { code: 400, message: 'x', status: 'INVALID_ARGUMENT' } })).toBe('INVALID_ARGUMENT');
    expect(providerErrorCode({ error: [{ code: 2005, message: 'x' }] })).toBe('2005');
    expect(providerErrorCode({ error: { type: 'Bad input: secret prompt text' } })).toBeNull();
    expect(providerErrorCode(null)).toBeNull();
    const e = upstreamErrorFor(400, { type: 'error', error: { type: 'invalid_request_error', message: 'm' } });
    expect(e).toMatchObject({ unavailable: false, status: 400, code: 'invalid_request_error' });
  });
});
