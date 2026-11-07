import type { Env, Price } from './types';
import raw from './routing.gen.json';

/**
 * Routing-Tabelle des Guthabens (docs/DECISIONS.md „Anbieter umschaltbar“, docs/anbieter-umstellen.md).
 * Quelle: backend/llm-routing.jsonc → scripts/build-routing.mjs → src/routing.gen.json. Welche Voreinstellung gilt, bestimmt die
 * Worker-Variable `LLM_PRESET` (setzt der Deploy-Workflow aus der Auswahlliste bzw. der Repo-Variable), sonst `default`.
 */
export type Tier = 'standard' | 'pro' | 'lite';
export const TIER_NAMES: Tier[] = ['standard', 'pro', 'lite'];
export const DEFAULT_TIER: Tier = 'standard';

export const PROVIDERS = ['anthropic', 'openai', 'gemini', 'workers-ai', 'openrouter'] as const;
export type ProviderKind = (typeof PROVIDERS)[number];

/** Secret je Anbieter; Workers AI braucht keins (Binding `AI`, setzt der Deploy immer). */
export const PROVIDER_SECRETS: Record<ProviderKind, keyof Env | null> = {
  anthropic: 'ANTHROPIC_API_KEY',
  openai: 'OPENAI_API_KEY',
  gemini: 'GEMINI_API_KEY',
  openrouter: 'OPENROUTER_API_KEY',
  'workers-ai': null,
};

export interface PricePeriod extends Price {
  /** Gültig ab (ISO-Datum, UTC). Ohne Angabe: seit jeher. */
  since?: string;
}

export interface ModelDef {
  /** ID in Ledger, Protokoll, Preisen und App; zugleich die Modell-ID beim Anbieter. */
  id: string;
  provider: ProviderKind;
  displayName: string;
  vendor: string;
  images: boolean;
  /** Denk-Token zählen als Ausgabe → mehr Ausgabe-Raum (siehe outputLimit). */
  reasoning: boolean;
  /** Typische Denk-Token je Coach-Anfrage, nur für die Kostenschätzung der App. */
  thinkingTokens: number;
  /** Preise in µ€ je Mio. Token, aufsteigend nach `since`. */
  prices: PricePeriod[];
}

export interface TierRoute {
  model: string;
  fallback: string | null;
}

export interface Preset {
  description: string;
  tiers: Record<Tier, TierRoute>;
}

export interface Routing {
  usdToEur: number;
  default: string;
  fallbackOrder: string[];
  models: Record<string, ModelDef>;
  presets: Record<string, Preset>;
}

const isObj = (v: unknown): v is Record<string, unknown> => !!v && typeof v === 'object' && !Array.isArray(v);
const num = (v: unknown) => typeof v === 'number' && Number.isFinite(v) && v >= 0;

/** Prüft und übersetzt die Routing-Datei. Wirft mit allen Fehlern auf einmal (Tests und Deploy brechen dann klar ab). */
export function parseRouting(input: unknown): Routing {
  const errors: string[] = [];
  if (!isObj(input)) throw new Error('llm-routing: kein Objekt');
  const rate = input.usdToEur;
  if (!num(rate) || (rate as number) <= 0) errors.push('usdToEur muss eine positive Zahl sein');
  const usd = (v: number) => Math.round(v * (rate as number) * 1_000_000);
  const models: Record<string, ModelDef> = {};
  for (const [id, m] of Object.entries(isObj(input.models) ? input.models : {})) {
    if (!isObj(m)) { errors.push(`Modell ${id}: kein Objekt`); continue; }
    if (!(PROVIDERS as readonly unknown[]).includes(m.provider)) errors.push(`Modell ${id}: unbekannter Anbieter ${String(m.provider)} (erlaubt: ${PROVIDERS.join(', ')})`);
    if (typeof m.displayName !== 'string' || !m.displayName) errors.push(`Modell ${id}: displayName fehlt`);
    if (typeof m.vendor !== 'string' || !m.vendor) errors.push(`Modell ${id}: vendor fehlt`);
    const periods = Array.isArray(m.price) ? m.price : [m.price];
    const prices: PricePeriod[] = [];
    for (const p of periods) {
      if (!isObj(p) || !num(p.in) || !num(p.out) || (p.cacheRead !== undefined && !num(p.cacheRead)) || (p.cacheWrite !== undefined && !num(p.cacheWrite))) {
        errors.push(`Modell ${id}: Preis braucht in/out (Zahlen ≥ 0, USD je Mio. Token)`);
        continue;
      }
      if (p.since !== undefined && (typeof p.since !== 'string' || Number.isNaN(Date.parse(p.since)))) errors.push(`Modell ${id}: since ist kein Datum`);
      const inp = p.in as number;
      prices.push({
        ...(typeof p.since === 'string' ? { since: p.since } : {}),
        in: usd(inp),
        out: usd(p.out as number),
        cacheRead: usd((p.cacheRead as number | undefined) ?? inp),
        cacheWrite: usd((p.cacheWrite as number | undefined) ?? inp),
      });
    }
    if (prices.length === 0) errors.push(`Modell ${id}: Preis fehlt`);
    prices.sort((a, b) => (a.since ? Date.parse(a.since) : 0) - (b.since ? Date.parse(b.since) : 0));
    models[id] = {
      id,
      provider: m.provider as ProviderKind,
      displayName: String(m.displayName ?? id),
      vendor: String(m.vendor ?? ''),
      images: m.images === true,
      reasoning: m.reasoning === true,
      thinkingTokens: Number.isInteger(m.thinkingTokens) ? (m.thinkingTokens as number) : 0,
      prices,
    };
  }
  if (Object.keys(models).length === 0) errors.push('keine Modelle');
  const presets: Record<string, Preset> = {};
  for (const [name, p] of Object.entries(isObj(input.presets) ? input.presets : {})) {
    if (!/^[a-z0-9-]{1,30}$/.test(name)) errors.push(`Voreinstellung „${name}“: Name nur a-z, 0-9, - (für die Auswahlliste)`);
    if (!isObj(p) || !isObj(p.tiers)) { errors.push(`Voreinstellung ${name}: tiers fehlt`); continue; }
    const tiers = {} as Record<Tier, TierRoute>;
    for (const t of TIER_NAMES) {
      const r: unknown = p.tiers[t];
      if (!isObj(r) || typeof r.model !== 'string') { errors.push(`Voreinstellung ${name}: Stufe ${t} fehlt`); continue; }
      if (!models[r.model]) errors.push(`Voreinstellung ${name}, Stufe ${t}: Modell ${r.model} steht nicht unter models`);
      const fb = r.fallback ?? null;
      if (fb !== null && (typeof fb !== 'string' || !models[fb])) errors.push(`Voreinstellung ${name}, Stufe ${t}: Ausweich-Modell ${String(fb)} steht nicht unter models`);
      if (fb === r.model) errors.push(`Voreinstellung ${name}, Stufe ${t}: Ausweich-Modell ist dasselbe Modell`);
      tiers[t] = { model: r.model, fallback: typeof fb === 'string' ? fb : null };
    }
    for (const k of Object.keys(p.tiers)) if (!(TIER_NAMES as string[]).includes(k)) errors.push(`Voreinstellung ${name}: unbekannte Stufe ${k}`);
    presets[name] = { description: typeof p.description === 'string' ? p.description : '', tiers };
  }
  const def = input.default;
  if (typeof def !== 'string' || !presets[def]) errors.push(`default „${String(def)}“ ist keine Voreinstellung`);
  const order = Array.isArray(input.fallbackOrder) ? input.fallbackOrder.filter((x): x is string => typeof x === 'string') : [];
  for (const n of order) if (!presets[n]) errors.push(`fallbackOrder: ${n} ist keine Voreinstellung`);
  if (errors.length) throw new Error(`llm-routing.jsonc ungültig:\n- ${errors.join('\n- ')}`);
  return { usdToEur: rate as number, default: def as string, fallbackOrder: order, models, presets };
}

export const ROUTING: Routing = parseRouting(raw);

/** Ist der Anbieter einsatzbereit (Schlüssel bzw. Binding vorhanden)? */
export function providerReady(env: Env, p: ProviderKind): boolean {
  if (p === 'workers-ai') return !!env.AI && typeof env.AI.run === 'function';
  const key = PROVIDER_SECRETS[p]!;
  return !!(env[key] as string | undefined)?.trim();
}

export function modelReady(env: Env, id: string | null, routing: Routing = ROUTING): boolean {
  const m = id ? routing.models[id] : undefined;
  return !!m && providerReady(env, m.provider);
}

/** Vollständig = jedes erste Modell aller Stufen ist einsatzbereit. */
export function presetComplete(env: Env, name: string, routing: Routing = ROUTING): boolean {
  const p = routing.presets[name];
  return !!p && TIER_NAMES.every((t) => modelReady(env, p.tiers[t].model, routing));
}

export interface PresetChoice {
  /** Tatsächlich genutzte Voreinstellung. */
  name: string;
  /** Gewünschte Voreinstellung (LLM_PRESET bzw. default). */
  requested: string;
  /** Grund, wenn `name` ≠ `requested`. */
  notice: string | null;
}

/**
 * Gewünschte Voreinstellung, falls vollständig; sonst die erste vollständige aus `fallbackOrder`; gibt es keine,
 * bleibt die gewünschte (Stufen ohne Schlüssel melden dann `tier_unavailable`).
 */
export function choosePreset(env: Env, routing: Routing = ROUTING): PresetChoice {
  const wanted = env.LLM_PRESET?.trim() || routing.default;
  const requested = routing.presets[wanted] ? wanted : routing.default;
  const unknown = requested !== wanted ? `Voreinstellung „${wanted}“ gibt es nicht. ` : '';
  if (presetComplete(env, requested, routing)) return { name: requested, requested, notice: unknown || null };
  const missing = missingSecrets(env, requested, routing);
  const alt = routing.fallbackOrder.find((n) => presetComplete(env, n, routing));
  if (alt) return { name: alt, requested, notice: `${unknown}„${requested}“ unvollständig (fehlt: ${missing.join(', ')}), genutzt wird „${alt}“.` };
  return { name: requested, requested, notice: `${unknown}„${requested}“ unvollständig (fehlt: ${missing.join(', ')}), keine vollständige Voreinstellung.` };
}

/** Fehlende Secrets für die ersten Modelle einer Voreinstellung. */
export function missingSecrets(env: Env, name: string, routing: Routing = ROUTING): string[] {
  const p = routing.presets[name];
  if (!p) return [];
  const out = new Set<string>();
  for (const t of TIER_NAMES) {
    const m = routing.models[p.tiers[t].model];
    if (m && !providerReady(env, m.provider)) out.add(PROVIDER_SECRETS[m.provider] ?? 'Workers-AI-Binding');
  }
  return [...out];
}

export interface Route {
  tier: Tier;
  /** Zuerst angefragtes Modell (null = Stufe nicht verfügbar). */
  primary: ModelDef | null;
  /** Ausweich-Modell bei Ausfall (nur wenn einsatzbereit). */
  fallback: ModelDef | null;
}

/**
 * Wirksame Zuordnung je Stufe. Fehlt der Schlüssel des ersten Modells, aber das Ausweich-Modell ist bereit, antwortet
 * gleich das Ausweich-Modell (dann ohne weiteres Ausweichen).
 */
export function routes(env: Env, routing: Routing = ROUTING): { preset: PresetChoice; tiers: Record<Tier, Route> } {
  const preset = choosePreset(env, routing);
  const p = routing.presets[preset.name];
  const tiers = {} as Record<Tier, Route>;
  for (const t of TIER_NAMES) {
    const r = p.tiers[t];
    const first = modelReady(env, r.model, routing) ? routing.models[r.model] : null;
    const fb = modelReady(env, r.fallback, routing) ? routing.models[r.fallback!] : null;
    tiers[t] = first ? { tier: t, primary: first, fallback: fb } : { tier: t, primary: fb, fallback: null };
  }
  return { preset, tiers };
}

/** Preis eines Modells zum Zeitpunkt `nowMs`. */
export function priceAt(periods: PricePeriod[], nowMs: number): Price {
  const valid = periods.filter((p) => !p.since || Date.parse(p.since) <= nowMs);
  const { since: _, ...price } = valid[valid.length - 1] ?? periods[0];
  return price;
}
