import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { Client, baseEnv, fund, rawFetch } from './helpers';
import { costMicro } from '../src/money';
import { currentPrices, TIERS } from '../src/providers';
import type { Env } from '../src/types';

const ADMIN = 'admin-secret-0123456789';
const IMG = { type: 'image', source: { type: 'base64', media_type: 'image/jpeg', data: 'QUJD' } };
const anthropicOk = (model: string) =>
  Response.json({ id: 'm', type: 'message', role: 'assistant', model, content: [{ type: 'text', text: 'x' }], stop_reason: 'end_turn', usage: { input_tokens: 1000, output_tokens: 500 } });
const sentModel = (init: any) => JSON.parse(init.body).model as string;
const req = (model: string, content: unknown = 'Hallo') => ({ model, max_tokens: 1000, messages: [{ role: 'user', content }] });

let anthropic: any;
/** Lässt nur die genannten Modelle ausfallen (503). */
const failFor = (...models: string[]) =>
  anthropic.mockImplementation(async (_u: string, init: any) =>
    models.includes(sentModel(init)) ? Response.json({ error: { message: 'down' } }, { status: 503 }) : anthropicOk(sentModel(init)),
  );
beforeEach(() => {
  anthropic = vi.fn(async (_u: string, init: any) => anthropicOk(sentModel(init)));
  const real = globalThis.fetch;
  vi.stubGlobal('fetch', (input: any, init?: any) => {
    const url = String(input instanceof Request ? input.url : input);
    if (url.startsWith('https://api.anthropic.com')) return anthropic(url, init);
    return real(input, init);
  });
});
afterEach(() => vi.unstubAllGlobals());

const usage = (e: Env, qs = '', secret: string | null = ADMIN) =>
  rawFetch('/v1/admin/usage' + qs, e, { headers: secret ? { authorization: `Bearer ${secret}` } : {} });

describe('GET /v1/config: echte Modellnamen', () => {
  it('liefert Anzeigename, Anbieter, Preis und typische Token-Mengen', async () => {
    const r = await rawFetch('/v1/config', baseEnv());
    expect(r.body.tiers.standard).toMatchObject({ model: 'claude-sonnet-5-5', displayName: 'Claude Sonnet 5.5', vendor: 'Anthropic', label: 'Claude Sonnet 5.5 (Anthropic)', available: true });
    expect(r.body.tiers.pro).toMatchObject({ model: 'claude-opus-5-5', displayName: 'Claude Opus 5.5', vendor: 'Anthropic', label: 'Claude Opus 5.5 (Anthropic)' });
    expect(r.body.tiers.lite).toMatchObject({ model: 'claude-haiku-5-5', displayName: 'Claude Haiku 5.5', vendor: 'Anthropic', label: 'Claude Haiku 5.5 (Anthropic)' });
    expect(r.body.tiers.standard.price.in).toBe(currentPrices()['claude-sonnet-5-5'].in);
    expect(r.body.tiers.lite.price.out).toBe(currentPrices()['claude-haiku-5-5'].out);
    expect(r.body.usageProfiles.coach.inputTokens).toBeGreaterThan(0);
    expect(r.body.usageProfiles.autofill.outputTokens).toBeGreaterThan(0);
    expect(r.body.markupBp).toBe(2800);
    expect(r.body.storeFeeBp).toBeUndefined();
    // Store-Pakete: Store-Preis (inkl. Store-Gebühr und USt) offen über dem Guthaben, in /v1/config sichtbar.
    expect(r.body.packages.map((p: any) => [p.productId, p.priceMicro, p.creditMicro])).toEqual([
      ['overfit.fuel.5', 6_990_000, 5_000_000],
      ['overfit.fuel.10', 13_990_000, 10_000_000],
    ]);
  });
});

describe('Antwort nennt das tatsächlich antwortende Modell', () => {
  it('bei Ausweichung: Wunschmodell, tatsächliches Modell und Wunschpreis', async () => {
    failFor('claude-opus-5-5');
    const c = await Client.create(baseEnv());
    await fund(c);
    const r = await c.call('POST', '/v1/messages', req('pro'));
    const price = currentPrices()['claude-sonnet-5-5'];
    expect(r.body.overfit).toMatchObject({
      fallback: true,
      fallbackReason: 'HTTP 503: down',
      model: 'claude-sonnet-5-5',
      modelName: 'Claude Sonnet 5.5',
      vendor: 'Anthropic',
      requestedModel: 'claude-opus-5-5',
      requestedModelName: 'Claude Opus 5.5',
      requestedVendor: 'Anthropic',
      // min(Sonnet-Preis, Opus-Preis) = Sonnet-Preis
      costMicro: costMicro({ input_tokens: 1000, output_tokens: 500 }, price, 2800),
    });
    failFor();
    const ok = await c.call('POST', '/v1/messages', req('pro'));
    expect(ok.body.overfit).toMatchObject({ fallback: false, fallbackReason: null, model: 'claude-opus-5-5' });
  });
});

describe('GET /v1/admin/usage', () => {
  it('ohne Secret 404, mit falschem 401, nur GET', async () => {
    expect((await usage(baseEnv(), '', ADMIN)).status).toBe(404);
    const e = baseEnv({ ADMIN_SECRET: ADMIN });
    expect((await usage(e, '', 'falsch-falsch-falsch-falsch')).status).toBe(401);
    expect((await usage(e, '', null)).status).toBe(401);
    expect((await rawFetch('/v1/admin/usage', e, { method: 'POST', headers: { authorization: `Bearer ${ADMIN}` } })).status).toBe(405);
    expect((await usage(e, '?from=quatsch')).status).toBe(400);
  });

  it('fasst Modell, Tokens, Kosten, Marge, Ausweichung und Foto zusammen – ohne Inhalte', async () => {
    const e = baseEnv({ ADMIN_SECRET: ADMIN });
    const c = await Client.create(e);
    await fund(c);
    await c.call('POST', '/v1/messages', req('standard'));
    await c.call('POST', '/v1/messages', req('standard', [IMG, { type: 'text', text: 'geheimer Prompt' }]));
    await c.call('POST', '/v1/messages', req('pro'));
    // lite fällt aus → Sonnet antwortet, berechnet wird der Haiku-Preis
    failFor('claude-haiku-5-5');
    await c.call('POST', '/v1/messages', req('lite'));

    const r = await usage(e);
    expect(r.status).toBe(200);
    expect(r.body.totals).toMatchObject({ requests: 4, photoRequests: 1, fallbackRequests: 1, inputTokens: 4000, outputTokens: 2000 });
    const g = (m: string, photo: boolean, fb: boolean) => r.body.groups.find((x: any) => x.model === m && x.photo === photo && x.fallback === fb);
    const prices = currentPrices();
    const u = { input_tokens: 1000, output_tokens: 500 };
    const sonnetCost = costMicro(u, prices['claude-sonnet-5-5'], 0);
    expect(g('claude-sonnet-5-5', true, false)).toMatchObject({ requests: 1, tier: 'standard', providerCostMicro: sonnetCost });
    expect(g('claude-sonnet-5-5', false, false)).toMatchObject({ requests: 1, tier: 'standard', providerCostMicro: sonnetCost });
    expect(g('claude-opus-5-5', false, false)).toMatchObject({ requests: 1, tier: 'pro', providerCostMicro: costMicro(u, prices['claude-opus-5-5'], 0) });
    const fb = g('claude-sonnet-5-5', false, true);
    expect(fb).toMatchObject({ requests: 1, tier: 'lite', providerCostMicro: sonnetCost });
    // Ausweichung auf das teurere Modell: berechnet wird der Haiku-Preis, die Marge schrumpft (hier negativ)
    expect(fb.chargedMicro).toBe(costMicro(u, prices['claude-haiku-5-5'], 2800));
    expect(fb.marginMicro).toBe(fb.chargedMicro - sonnetCost);
    expect(fb.marginMicro).toBeLessThan(0);
    expect(g('claude-sonnet-5-5', false, false).marginMicro).toBeGreaterThan(0);
    expect(g('claude-opus-5-5', false, false).marginMicro).toBeGreaterThan(0);
    expect(r.body.totals.marginMicro).toBe(r.body.groups.reduce((a: number, x: any) => a + x.marginMicro, 0));
    expect(JSON.stringify(r.body)).not.toContain('geheimer');
    expect(Object.keys(r.body.groups[0]).sort()).toEqual(
      ['cacheReadTokens', 'cacheWriteTokens', 'chargedMicro', 'fallback', 'inputTokens', 'marginMicro', 'model', 'outputTokens', 'photo', 'providerCostMicro', 'requests', 'tier'].sort(),
    );
  });

  it('Zeitraum filtert', async () => {
    const e = baseEnv({ ADMIN_SECRET: ADMIN });
    const c = await Client.create(e);
    await fund(c);
    await c.call('POST', '/v1/messages', req('standard'));
    expect((await usage(e, '?from=2020-01-01&to=2020-02-01')).body.totals.requests).toBe(0);
    expect((await usage(e, '?from=2020-01-01')).body.totals.requests).toBe(1);
  });
});
