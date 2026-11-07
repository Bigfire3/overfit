import type { Config, Env, Price } from './types';
import type { Usage } from './money';

/**
 * KI-Stufen des Guthabens (docs/DECISIONS.md „Guthaben nur mit Claude“, 7.11.2026). Alle drei Stufen sind Claude-Modelle
 * von Anthropic; der Server braucht nur `ANTHROPIC_API_KEY`. Die App schickt das Anthropic-Messages-Format mit
 * `model` = Stufe, das Backend setzt das Modell der Stufe ein. Über das Cloudflare AI Gateway nur mit `AI_GATEWAY_TOKEN`.
 * Gemini und Workers AI gibt es im Guthaben-Weg nicht mehr (bis 7.11.2026: standard = Gemini Flash, lite = Llama);
 * ihre Preise bleiben nur für die Kostenrechnung alter Buchungen.
 */
export type Tier = 'standard' | 'pro' | 'lite';
export type ProviderKind = 'anthropic';

export interface TierDef {
  provider: ProviderKind;
  model: string;
  /** Echter Modellname für die Anzeige (kein Marketingname, siehe DECISIONS.md „Ehrliche Begriffe“). */
  displayName: string;
  /** Anbieter für die Anzeige, z. B. „Anthropic“. */
  vendor: string;
  /** Typische Thinking-Token je Coach-Anfrage (zählen als Ausgabe), nur für die Kostenschätzung. */
  thinkingTokens: number;
  images: boolean;
  /** Ausweich-Stufe, wenn das Modell ausfällt (5xx, 429, Zeitüberschreitung, Netzwerk, abgelehnter Schlüssel). */
  fallback: Tier | null;
}

/**
 * lite = Claude Haiku 5.5 (kurze, günstige Anfragen, z. B. „Mit KI ausfüllen“), standard = Claude Sonnet 5.5 (Voreinstellung: Coach-Auswertung und Chat), pro = Claude Opus 5.5 (teuer).
 * Ausweichen geht nie zur teureren Stufe hoch, außer von Haiku zu Sonnet (berechnet wird ohnehin höchstens der Preis der
 * angefragten Stufe).
 */
export const TIERS: Record<Tier, TierDef> = {
  lite: { provider: 'anthropic', model: 'claude-haiku-5-5', displayName: 'Claude Haiku 5.5', vendor: 'Anthropic', thinkingTokens: 0, images: true, fallback: 'standard' },
  standard: { provider: 'anthropic', model: 'claude-sonnet-5-5', displayName: 'Claude Sonnet 5.5', vendor: 'Anthropic', thinkingTokens: 0, images: true, fallback: 'lite' },
  pro: { provider: 'anthropic', model: 'claude-opus-5-5', displayName: 'Claude Opus 5.5', vendor: 'Anthropic', thinkingTokens: 0, images: true, fallback: 'standard' },
};
export const TIER_NAMES: Tier[] = ['standard', 'pro', 'lite'];
export const DEFAULT_TIER: Tier = 'standard';

/**
 * Typische Token-Mengen je Aufgabe für die Kostenschätzung in der App, gemessen an den echten Prompts
 * (Methode: docs/DECISIONS.md „Kostenschätzung“). Ohne Cache-Rabatt, also eher zu hoch als zu niedrig.
 */
export const USAGE_PROFILES = {
  coach: { inputTokens: 8200, outputTokens: 1400 },
  chat: { inputTokens: 6700, outputTokens: 500 },
  autofill: { inputTokens: 900, outputTokens: 120 },
} as const;

/** Wechselkurs für alle Listenpreise in USD (vom Betreiber zu pflegen). */
export const EUR_PER_USD = 0.92;
/** Workers AI (nur noch für alte Buchungen): 0,011 $ je 1000 Neurons → Milli-µ€ je Neuron. */
const NEURON_MILLI_MICRO = Math.round(0.011 * EUR_PER_USD * 1_000_000);
const usd = (perMTok: number) => Math.round(perMTok * EUR_PER_USD * 1_000_000);
const neurons = (perMTok: number) => Math.round((perMTok * NEURON_MILLI_MICRO) / 1000);
/** Anthropic: Cache-Lesen laut Preisliste, Cache-Schreiben (5 Minuten) = 1,25 × Eingabe. */
const claude = (input: number, out: number, cacheRead: number) => ({ in: usd(input), cacheRead: usd(cacheRead), cacheWrite: usd(input * 1.25), out: usd(out) });

interface PricePeriod extends Price {
  /** Gültig ab (ISO-Datum, UTC). Ohne Angabe: seit jeher. */
  since?: string;
}

/**
 * Preise in µ€ je 1 Mio. Token, Listenpreise der Anthropic-API (Stand 7.11.2026, Preisliste von Anthropic):
 * - claude-haiku-5-5: 0,10 $ ein / 0,50 $ aus, Cache-Lesen 0,01 $ (gilt bis 100 000 Token Eingabe; darüber 0,50 $ / 2,50 $ –
 *   kommt bei Overfit nicht vor, `MAX_BODY_BYTES` und die Prompts bleiben weit darunter).
 * - claude-sonnet-5-5: 2 $ / 10 $, Cache-Lesen 0,20 $.
 * - claude-opus-5-5: 4 $ / 20 $, Cache-Lesen 0,20 $.
 * Cache-Schreiben jeweils 1,25 × Eingabe. Thinking-Token zählen als Ausgabe.
 * Nur noch für alte Buchungen: gemini-3.6-flash, Llama über Workers AI.
 */
export const MODEL_PRICES: Record<string, PricePeriod[]> = {
  'claude-haiku-5-5': [claude(0.1, 0.5, 0.01)],
  'claude-sonnet-5-5': [claude(2, 10, 0.2)],
  'claude-opus-5-5': [claude(4, 20, 0.2)],
  'gemini-3.6-flash': [
    { in: usd(0.75), cacheRead: usd(0.075), cacheWrite: usd(0.75), out: usd(3.75) },
    { since: '2027-01-01', in: usd(1.5), cacheRead: usd(0.15), cacheWrite: usd(1.5), out: usd(7.5) },
  ],
  '@cf/meta/llama-3.3-70b-instruct-fp8-fast': [{ in: usd(0.293), cacheRead: usd(0.293), cacheWrite: usd(0.293), out: usd(2.253) }],
  '@cf/meta/llama-3.1-8b-instruct': [{ in: neurons(25_608), cacheRead: neurons(25_608), cacheWrite: neurons(25_608), out: neurons(75_147) }],
};

/** Aktuell gültiger Preis je Modell (zum Zeitpunkt `nowMs`). */
export function currentPrices(nowMs = Date.now()): Record<string, Price> {
  const out: Record<string, Price> = {};
  for (const [model, periods] of Object.entries(MODEL_PRICES)) {
    const valid = periods.filter((p) => !p.since || Date.parse(p.since) <= nowMs);
    const { since: _, ...price } = valid[valid.length - 1];
    out[model] = price;
  }
  return out;
}

/** Frühere Modelle der Stufen (alte App-Versionen schicken die Modell-ID statt der Stufe). */
const LEGACY_MODELS: Record<string, Tier> = {
  'gemini-3.6-flash': 'standard',
  '@cf/meta/llama-3.1-8b-instruct': 'lite',
  '@cf/meta/llama-3.3-70b-instruct-fp8-fast': 'lite',
};

/** Stufe zu einem `model`-Wert: Stufenname, Modell einer Stufe oder früheres Modell einer Stufe. */
export function tierForModel(model: string): Tier | null {
  if ((TIER_NAMES as string[]).includes(model)) return model as Tier;
  return TIER_NAMES.find((t) => TIERS[t].model === model) ?? LEGACY_MODELS[model] ?? null;
}

export function tierAvailable(env: Env, _tier: Tier): boolean {
  return !!env.ANTHROPIC_API_KEY;
}

/** Höchstzahl Ausgabe-Token, die beim Anbieter angefragt (und reserviert) wird. */
export function outputLimit(_tier: Tier, maxTokens: number, _cfg: Config): number {
  return maxTokens;
}

// ---- Anbieter-Aufrufe ------------------------------------------------------------------------------------------

export interface Normalized {
  /** Antwort im Anthropic-Format (ohne `overfit`). */
  body: Record<string, unknown>;
  usage: Usage | null;
}

/** Fehler beim Anbieter. `unavailable` = Ausfall (Ausweichen erlaubt), sonst Ablehnung der Anfrage. */
export class UpstreamError extends Error {
  constructor(
    public unavailable: boolean,
    public status: number,
    message: string,
    public timeout = false,
  ) {
    super(message);
  }
}

export interface CallOptions {
  timeoutMs: number;
  /** Anfrage enthält Bilder (Körperfotos) → das Gateway speichert keine Inhalte, nur Metadaten. */
  hasImages: boolean;
  tier: Tier;
  /** Ein anderes Modell als das gewählte beantwortet die Anfrage. */
  fallback?: boolean;
}

/**
 * Basis-URL eines Anbieters: ausdrücklicher Override (Tests, Dev) → AI Gateway → direkt.
 * Das Gateway nur mit `AI_GATEWAY_TOKEN`: Cloudflare legt neue Gateways seit 2026 immer mit Pflicht-Authentifizierung an
 * (nicht abschaltbar), ohne `cf-aig-authorization` antwortet es mit 401. Ohne Token geht jede Anfrage
 * direkt an Anthropic. Das Gateway selbst kostet nichts (Protokolle, Kosten-Übersicht); das Binding `AI` dient nur `getUrl`.
 */
async function route(env: Env, cfg: Config, provider: 'anthropic'): Promise<{ url: string; gateway: boolean }> {
  const override = env.ANTHROPIC_BASE_URL;
  if (override) return { url: override.replace(/\/+$/, ''), gateway: false };
  if (cfg.aiGatewayId && env.AI && env.AI_GATEWAY_TOKEN) {
    return { url: (await env.AI.gateway(cfg.aiGatewayId).getUrl(provider)).replace(/\/+$/, ''), gateway: true };
  }
  return { url: directUrl(provider), gateway: false };
}

function directUrl(_provider: 'anthropic'): string {
  return 'https://api.anthropic.com';
}

/**
 * Anfrage an einen Anbieter, über das Gateway, falls eingerichtet. Lehnt das Gateway selbst ab (401/403, z. B. Token
 * falsch oder abgelaufen), geht dieselbe Anfrage einmal direkt an den Anbieter: Ein Gateway-Problem darf die
 * Auswertung nicht verhindern.
 */
async function providerPost(
  env: Env,
  cfg: Config,
  provider: 'anthropic',
  path: string,
  headers: Record<string, string>,
  body: unknown,
  o: CallOptions,
): Promise<Record<string, unknown>> {
  const r = await route(env, cfg, provider);
  if (!r.gateway) return post(`${r.url}${path}`, headers, body, o.timeoutMs);
  const started = Date.now();
  try {
    return await post(`${r.url}${path}`, { ...headers, ...gatewayHeaders(cfg, o), 'cf-aig-authorization': `Bearer ${env.AI_GATEWAY_TOKEN}` }, body, o.timeoutMs);
  } catch (e) {
    if (!(e instanceof UpstreamError) || (e.status !== 401 && e.status !== 403)) throw e;
    console.warn('AI Gateway lehnt ab, direkt an den Anbieter', provider, e.status, e.message);
    return post(`${directUrl(provider)}${path}`, headers, body, Math.max(o.timeoutMs - (Date.now() - started), 1_000));
  }
}

/**
 * Gateway-Header. Bei Foto-Anfragen `cf-aig-collect-log-payload: false`: Das Gateway speichert dann weder Anfrage noch
 * Antwort, protokolliert aber weiter Metadaten (Tokens, Kosten, Modell, Dauer, Status). Bewusst nicht
 * `cf-aig-collect-log: false`, das würde den ganzen Eintrag samt Kosten unterdrücken.
 */
export function gatewayHeaders(cfg: Config, o: CallOptions): Record<string, string> {
  if (!cfg.aiGatewayId) return {};
  const meta = { tier: o.tier, photo: o.hasImages, fallback: !!o.fallback };
  const h: Record<string, string> = { 'cf-aig-metadata': JSON.stringify(meta) };
  if (o.hasImages) h['cf-aig-collect-log-payload'] = 'false';
  return h;
}

async function post(url: string, headers: Record<string, string>, body: unknown, timeoutMs: number): Promise<Record<string, unknown>> {
  let res: Response;
  try {
    res = await fetch(url, {
      method: 'POST',
      headers: { 'content-type': 'application/json', ...headers },
      body: JSON.stringify(body),
      signal: AbortSignal.timeout(timeoutMs),
    });
  } catch (e) {
    const timeout = e instanceof Error && (e.name === 'TimeoutError' || e.name === 'AbortError');
    throw new UpstreamError(true, 0, timeout ? 'Zeitüberschreitung beim KI-Anbieter' : 'KI-Anbieter nicht erreichbar', timeout);
  }
  const text = await res.text();
  let parsed: Record<string, unknown> | null = null;
  try {
    parsed = JSON.parse(text);
  } catch {
    /* bleibt null */
  }
  if (!res.ok || !parsed || typeof parsed !== 'object') {
    const err = parsed?.error as { message?: string } | { message?: string }[] | undefined;
    // Anbieter: { error: { message } }; Cloudflare (AI Gateway): { error: [{ code, message }] }.
    const detail = Array.isArray(err) ? err.map((x) => x?.message).filter(Boolean).join('; ') : err?.message;
    const msg = detail || `KI-Anbieter antwortet mit HTTP ${res.status}`;
    // 401/403: Schlüssel oder Gateway des Betreibers kaputt, nicht die Anfrage → wie ein Ausfall (Ausweich-Stufe, nichts abgebucht).
    const unavailable = res.ok || res.status >= 500 || res.status === 429 || res.status === 408 || res.status === 401 || res.status === 403;
    throw new UpstreamError(unavailable, res.status, msg);
  }
  return parsed;
}

// Anthropic: Body unverändert, nur das Modell der Stufe; Sonnet 5.5 bekommt effort „medium“, wenn die App keins schickt.
async function callAnthropic(env: Env, cfg: Config, body: Record<string, unknown>, model: string, maxTokens: number, o: CallOptions): Promise<Normalized> {
  const outputConfig = { ...((body.output_config as object | undefined) ?? {}) } as Record<string, unknown>;
  if (outputConfig.effort === undefined) outputConfig.effort = 'medium';
  const req = { ...body, model, max_tokens: maxTokens, output_config: outputConfig };
  const res = await providerPost(env, cfg, 'anthropic', '/v1/messages', { 'x-api-key': env.ANTHROPIC_API_KEY!, 'anthropic-version': '2023-06-01' }, req, o);
  const u = res.usage as Record<string, number> | undefined;
  const usage = u && typeof u.input_tokens === 'number' && typeof u.output_tokens === 'number' ? (u as Usage) : null;
  return { body: res, usage };
}

export function callTier(env: Env, cfg: Config, tier: Tier, body: Record<string, unknown>, maxTokens: number, o: CallOptions): Promise<Normalized> {
  const def = TIERS[tier];
  const limit = outputLimit(tier, maxTokens, cfg);
  return callAnthropic(env, cfg, body, def.model, limit, o);
}
