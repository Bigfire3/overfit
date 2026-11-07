import { describe, expect, it } from 'vitest';
import jsonc from '../llm-routing.jsonc?raw';
import gen from '../src/routing.gen.json';
import { choosePreset, parseRouting, priceAt, ROUTING, routes, TIER_NAMES } from '../src/routing';
import type { Env } from '../src/types';

const stripComments = (t: string) => JSON.parse(t.split('\n').filter((l) => !/^\s*\/\//.test(l)).join('\n'));
const AI = { run: async () => ({}), gateway: () => ({ getUrl: async () => '' }) };
const env = (keys: Partial<Env> = {}, withAi = false) => ({ ...keys, ...(withAi ? { AI } : {}) }) as unknown as Env;
const valid = () => structuredClone(stripComments(jsonc));

describe('llm-routing.jsonc', () => {
  it('src/routing.gen.json entspricht llm-routing.jsonc (wird vor jedem Test neu erzeugt)', () => {
    expect(gen).toEqual(stripComments(jsonc));
  });

  it('ist gültig: jede Voreinstellung belegt alle Stufen mit bekannten Modellen, Preise vorhanden', () => {
    expect(() => parseRouting(stripComments(jsonc))).not.toThrow();
    for (const [name, p] of Object.entries(ROUTING.presets)) {
      for (const t of TIER_NAMES) {
        expect(ROUTING.models[p.tiers[t].model], `${name}.${t}`).toBeDefined();
        const fb = p.tiers[t].fallback;
        if (fb) expect(ROUTING.models[fb], `${name}.${t} fallback`).toBeDefined();
      }
    }
    for (const n of ['claude', 'openai', 'gemini', 'gratis', 'gemischt']) expect(ROUTING.presets[n]).toBeDefined();
    const providers = new Set(Object.values(ROUTING.models).map((m) => m.provider));
    for (const p of ['anthropic', 'openai', 'gemini', 'workers-ai', 'openrouter']) expect(providers.has(p as never)).toBe(true);
  });

  it('meldet alle Fehler verständlich', () => {
    const bad = valid();
    bad.models['gpt-5'].provider = 'mistral';
    delete bad.models['gpt-5-mini'].price;
    bad.presets.claude.tiers.lite.model = 'gibts-nicht';
    bad.presets.openai.tiers.pro.fallback = 'auch-nicht';
    delete bad.presets.gemini.tiers.pro;
    bad.default = 'nix';
    bad.fallbackOrder.push('weg');
    let msg = '';
    try {
      parseRouting(bad);
    } catch (e) {
      msg = (e as Error).message;
    }
    for (const part of ['unbekannter Anbieter mistral', 'gpt-5-mini: Preis', 'gibts-nicht steht nicht unter models', 'auch-nicht', 'Stufe pro fehlt', 'default „nix“', 'fallbackOrder: weg']) {
      expect(msg).toContain(part);
    }
  });

  it('Preise: USD je MTok → µ€ (Kurs aus der Datei), Cache fehlt = Eingabepreis, zeitabhängige Preise', () => {
    const r = parseRouting({
      usdToEur: 0.5, default: 'x', fallbackOrder: [],
      models: { m: { provider: 'openai', displayName: 'M', vendor: 'V', price: [{ in: 2, out: 8 }, { since: '2027-01-01', in: 4, out: 16, cacheRead: 1 }] } },
      presets: { x: { tiers: { lite: { model: 'm' }, standard: { model: 'm' }, pro: { model: 'm' } } } },
    });
    expect(priceAt(r.models.m.prices, Date.parse('2026-12-31'))).toEqual({ in: 1_000_000, out: 4_000_000, cacheRead: 1_000_000, cacheWrite: 1_000_000 });
    expect(priceAt(r.models.m.prices, Date.parse('2027-01-02'))).toEqual({ in: 2_000_000, out: 8_000_000, cacheRead: 500_000, cacheWrite: 2_000_000 });
  });
});

describe('Voreinstellung wählen', () => {
  it('ohne LLM_PRESET gilt default (claude)', () => {
    expect(choosePreset(env({ ANTHROPIC_API_KEY: 'a' }))).toMatchObject({ name: 'claude', notice: null });
  });

  it('LLM_PRESET wählt die Voreinstellung', () => {
    expect(choosePreset(env({ LLM_PRESET: 'openai', OPENAI_API_KEY: 'o' })).name).toBe('openai');
    expect(choosePreset(env({ LLM_PRESET: 'gratis' }, true)).name).toBe('gratis');
  });

  it('fehlender Schlüssel → nächste vollständige aus fallbackOrder, mit Hinweis', () => {
    const c = choosePreset(env({ LLM_PRESET: 'openai', GEMINI_API_KEY: 'g' }));
    expect(c).toMatchObject({ name: 'gemini', requested: 'openai' });
    expect(c.notice).toContain('OPENAI_API_KEY');
  });

  it('unbekannter Name → default', () => {
    expect(choosePreset(env({ LLM_PRESET: 'quatsch', ANTHROPIC_API_KEY: 'a' })).name).toBe('claude');
  });

  it('wirksame Routen: fehlt nur das Ausweich-Modell, gibt es kein Ausweichen; fehlt das erste, antwortet das Ausweich-Modell', () => {
    // gemischt: lite = Gemini (→ Haiku), standard = Sonnet (→ Gemini). Nur Anthropic-Schlüssel: Preset unvollständig → claude.
    const onlyGemini = routes(env({ LLM_PRESET: 'gemischt', GEMINI_API_KEY: 'g', ANTHROPIC_API_KEY: '' }));
    expect(onlyGemini.preset.name).toBe('gemini');
    const r = routes(env({ LLM_PRESET: 'gemischt', GEMINI_API_KEY: 'g', ANTHROPIC_API_KEY: 'a' })).tiers;
    expect(r.lite.primary?.id).toBe('gemini-3.6-flash');
    expect(r.lite.fallback?.id).toBe('claude-haiku-5-5');
    expect(r.standard.fallback?.id).toBe('gemini-3.6-flash');
    // gratis ohne Anthropic: Llama ohne Ausweichen
    const g = routes(env({ LLM_PRESET: 'gratis' }, true)).tiers;
    expect(g.lite.primary?.provider).toBe('workers-ai');
    expect(g.lite.fallback).toBeNull();
  });
});
