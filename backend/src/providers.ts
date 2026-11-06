import type { Config, Env, Price } from './types';
import type { Usage } from './money';

/**
 * KI-Stufen von Fuel (docs/DECISIONS.md „Fuel: Stufen statt fester Anbieter“). Die App schickt immer das
 * Anthropic-Messages-Format; das Backend bildet es auf den Anbieter der Stufe ab und antwortet wieder im
 * Anthropic-Format. Alle Anbieter-Aufrufe laufen über das Cloudflare AI Gateway (`AI_GATEWAY_ID`), sofern konfiguriert.
 */
export type Tier = 'standard' | 'pro' | 'lite';
export type ProviderKind = 'gemini' | 'anthropic' | 'workers-ai';

export interface TierDef {
  provider: ProviderKind;
  model: string;
  /** Echter Modellname für die Anzeige (kein Marketingname, siehe DECISIONS.md „Ehrliche Begriffe“). */
  displayName: string;
  /** Anbieter für die Anzeige, z. B. „Google“. */
  vendor: string;
  /** Typische Thinking-Token je Coach-Anfrage (zählen als Ausgabe), nur für die Kostenschätzung. */
  thinkingTokens: number;
  images: boolean;
  /** Ausweich-Stufe, wenn der Anbieter ausfällt (5xx, 429, Zeitüberschreitung, Netzwerk). */
  fallback: Tier | null;
}

export const TIERS: Record<Tier, TierDef> = {
  standard: { provider: 'gemini', model: 'gemini-3.6-flash', displayName: 'Gemini 3.6 Flash', vendor: 'Google', thinkingTokens: 1000, images: true, fallback: 'pro' },
  pro: { provider: 'anthropic', model: 'claude-sonnet-5-5', displayName: 'Claude Sonnet 5.5', vendor: 'Anthropic', thinkingTokens: 0, images: true, fallback: 'standard' },
  lite: { provider: 'workers-ai', model: '@cf/meta/llama-3.3-70b-instruct-fp8-fast', displayName: 'Llama 3.3 70B', vendor: 'Cloudflare', thinkingTokens: 0, images: false, fallback: 'standard' },
};
export const TIER_NAMES = Object.keys(TIERS) as Tier[];
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
/** Workers AI: 0,011 $ je 1000 Neurons → µ€ je Neuron (×1000 für Genauigkeit: Milli-µ€). */
const NEURON_MILLI_MICRO = Math.round(0.011 * EUR_PER_USD * 1_000_000); // 10 120 Milli-µ€ = 10,12 µ€ je Neuron
const usd = (perMTok: number) => Math.round(perMTok * EUR_PER_USD * 1_000_000);
const neurons = (perMTok: number) => Math.round((perMTok * NEURON_MILLI_MICRO) / 1000);

interface PricePeriod extends Price {
  /** Gültig ab (ISO-Datum, UTC). Ohne Angabe: seit jeher. */
  since?: string;
}

/**
 * Preise in µ€ je 1 Mio. Token, Listenpreise (Stand 2026-11, recherchiert):
 * - gemini-3.6-flash: 0,75 $ ein / 3,75 $ aus (inkl. Thinking) / 0,075 $ Cache; ab 2027-01-01 doppelt so teuer.
 *   Kein Cache-Write-Preis (implizites Caching) → cacheWrite = Eingabepreis.
 * - claude-sonnet-5-5: 2 $ / 10 $, Cache-Read 0,20 $, Cache-Write 2,50 $.
 * - @cf/meta/llama-3.3-70b-instruct-fp8-fast: 0,293 $ / 2,253 $ (Workers AI, JSON-Modus; Stufe lite seit 6.11.2026).
 * - @cf/meta/llama-3.1-8b-instruct: 25 608 Neurons ein / 75 147 Neurons aus je MTok (0,282 $ / 0,827 $). Bei Workers AI am
 *   30.5.2026 abgekündigt, bleibt nur für die Kostenrechnung alter Buchungen.
 */
export const MODEL_PRICES: Record<string, PricePeriod[]> = {
  'gemini-3.6-flash': [
    { in: usd(0.75), cacheRead: usd(0.075), cacheWrite: usd(0.75), out: usd(3.75) },
    { since: '2027-01-01', in: usd(1.5), cacheRead: usd(0.15), cacheWrite: usd(1.5), out: usd(7.5) },
  ],
  'claude-sonnet-5-5': [{ in: usd(2), cacheRead: usd(0.2), cacheWrite: usd(2.5), out: usd(10) }],
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

/** Alte App-Versionen schicken eine Modell-ID statt einer Stufe. */
export function tierForModel(model: string): Tier | null {
  if ((TIER_NAMES as string[]).includes(model)) return model as Tier;
  return TIER_NAMES.find((t) => TIERS[t].model === model) ?? null;
}

export function tierAvailable(env: Env, tier: Tier): boolean {
  switch (TIERS[tier].provider) {
    case 'gemini':
      return !!env.GEMINI_API_KEY;
    case 'anthropic':
      return !!env.ANTHROPIC_API_KEY;
    case 'workers-ai':
      return !!env.AI;
  }
}

/**
 * Höchstzahl Ausgabe-Token, die beim Anbieter angefragt (und reserviert) wird. Gemini zählt Thinking-Token in die
 * Ausgabe, deshalb bekommt es das Doppelte (mindestens 4096, höchstens 2 × Obergrenze).
 */
export function outputLimit(tier: Tier, maxTokens: number, cfg: Config): number {
  if (TIERS[tier].provider === 'gemini') return Math.min(Math.max(maxTokens * 2, 4096), cfg.maxTokensCap * 2);
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

type Block = Record<string, unknown>;

function blocks(content: unknown): Block[] {
  if (typeof content === 'string') return [{ type: 'text', text: content }];
  return Array.isArray(content) ? (content.filter((b) => b && typeof b === 'object') as Block[]) : [];
}

function systemText(system: unknown): string {
  return blocks(system)
    .filter((b) => b.type === 'text')
    .map((b) => String(b.text ?? ''))
    .join('\n\n');
}

function schemaOf(body: Record<string, unknown>): unknown | null {
  const fmt = (body.output_config as { format?: { type?: string; schema?: unknown } } | undefined)?.format;
  return fmt?.type === 'json_schema' && fmt.schema ? fmt.schema : null;
}

/**
 * Basis-URL eines Anbieters: ausdrücklicher Override (Tests, Dev) → AI Gateway → direkt.
 * Das Gateway nur mit `AI_GATEWAY_TOKEN`: Cloudflare legt neue Gateways seit 2026 immer mit Pflicht-Authentifizierung an
 * (nicht abschaltbar), ohne `cf-aig-authorization` antwortet es mit 401. Ohne Token gehen Gemini und Anthropic deshalb
 * direkt an den Anbieter; Workers AI läuft über das Binding und braucht keinen Token.
 */
async function route(env: Env, cfg: Config, provider: 'anthropic' | 'google-ai-studio'): Promise<{ url: string; gateway: boolean }> {
  const override = provider === 'anthropic' ? env.ANTHROPIC_BASE_URL : env.GEMINI_BASE_URL;
  if (override) return { url: override.replace(/\/+$/, ''), gateway: false };
  if (cfg.aiGatewayId && env.AI && env.AI_GATEWAY_TOKEN) {
    return { url: (await env.AI.gateway(cfg.aiGatewayId).getUrl(provider)).replace(/\/+$/, ''), gateway: true };
  }
  return { url: directUrl(provider), gateway: false };
}

function directUrl(provider: 'anthropic' | 'google-ai-studio'): string {
  return provider === 'anthropic' ? 'https://api.anthropic.com' : 'https://generativelanguage.googleapis.com';
}

/**
 * Anfrage an einen Anbieter, über das Gateway, falls eingerichtet. Lehnt das Gateway selbst ab (401/403, z. B. Token
 * falsch oder abgelaufen), geht dieselbe Anfrage einmal direkt an den Anbieter: Ein Gateway-Problem darf die
 * Auswertung nicht verhindern.
 */
async function providerPost(
  env: Env,
  cfg: Config,
  provider: 'anthropic' | 'google-ai-studio',
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
    return post(`${directUrl(provider)}${path}`, headers, body, Math.max(o.timeoutMs - (Date.now() - started), 5_000));
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

const GEMINI_STOP: Record<string, string> = {
  STOP: 'end_turn',
  MAX_TOKENS: 'max_tokens',
  SAFETY: 'refusal',
  RECITATION: 'refusal',
  BLOCKLIST: 'refusal',
  PROHIBITED_CONTENT: 'refusal',
  SPII: 'refusal',
  IMAGE_SAFETY: 'refusal',
};

/** Anthropic-Body → Gemini generateContent (native API, Structured Output über `responseJsonSchema`). */
export function geminiRequest(body: Record<string, unknown>, maxOutput: number): Record<string, unknown> {
  const contents = (body.messages as { role?: string; content?: unknown }[]).map((m) => ({
    role: m.role === 'assistant' ? 'model' : 'user',
    parts: blocks(m.content).flatMap((b): Record<string, unknown>[] => {
      if (b.type === 'text') return [{ text: String(b.text ?? '') }];
      const src = b.source as { type?: string; media_type?: string; data?: string } | undefined;
      if (b.type === 'image' && src?.type === 'base64') return [{ inlineData: { mimeType: src.media_type ?? 'image/jpeg', data: src.data ?? '' } }];
      return [];
    }),
  }));
  const effort = (body.output_config as { effort?: string } | undefined)?.effort;
  const generationConfig: Record<string, unknown> = {
    maxOutputTokens: maxOutput,
    thinkingConfig: { thinkingLevel: effort === 'low' || effort === 'high' ? effort : 'medium' },
  };
  if (typeof body.temperature === 'number') generationConfig.temperature = body.temperature;
  const schema = schemaOf(body);
  if (schema) {
    generationConfig.responseMimeType = 'application/json';
    generationConfig.responseJsonSchema = schema;
  }
  const sys = systemText(body.system);
  return { ...(sys ? { systemInstruction: { parts: [{ text: sys }] } } : {}), contents, generationConfig };
}

export function geminiToAnthropic(res: Record<string, unknown>, model: string): Normalized {
  const cand = (res.candidates as { content?: { parts?: { text?: string; thought?: boolean }[] }; finishReason?: string }[] | undefined)?.[0];
  const blocked = (res.promptFeedback as { blockReason?: string } | undefined)?.blockReason;
  const text = (cand?.content?.parts ?? []).filter((p) => !p.thought && typeof p.text === 'string').map((p) => p.text).join('');
  const stop = blocked ? 'refusal' : (GEMINI_STOP[cand?.finishReason ?? 'STOP'] ?? 'end_turn');
  const m = res.usageMetadata as Record<string, number> | undefined;
  let usage: Usage | null = null;
  if (m && typeof m.promptTokenCount === 'number') {
    const cached = m.cachedContentTokenCount ?? 0;
    usage = {
      input_tokens: Math.max(m.promptTokenCount - cached, 0),
      cache_read_input_tokens: cached,
      cache_creation_input_tokens: 0,
      output_tokens: (m.candidatesTokenCount ?? 0) + (m.thoughtsTokenCount ?? 0),
    };
  }
  return { body: anthropicShape(text, stop, model, usage, res.responseId), usage };
}

async function callGemini(env: Env, cfg: Config, body: Record<string, unknown>, model: string, maxOutput: number, o: CallOptions): Promise<Normalized> {
  const res = await providerPost(
    env, cfg, 'google-ai-studio', `/v1beta/models/${model}:generateContent`, { 'x-goog-api-key': env.GEMINI_API_KEY! }, geminiRequest(body, maxOutput), o,
  );
  if (!res.candidates && !res.promptFeedback) throw new UpstreamError(true, 200, 'Gemini-Antwort ohne Inhalt');
  return geminiToAnthropic(res, model);
}

/** Anthropic-Body → Workers-AI-Chat (nur Text; JSON-Modus über `response_format`). */
export function workersAiRequest(body: Record<string, unknown>, maxOutput: number): Record<string, unknown> {
  const messages: { role: string; content: string }[] = [];
  const sys = systemText(body.system);
  if (sys) messages.push({ role: 'system', content: sys });
  for (const m of body.messages as { role?: string; content?: unknown }[]) {
    const text = blocks(m.content).filter((b) => b.type === 'text').map((b) => String(b.text ?? '')).join('\n');
    messages.push({ role: m.role === 'assistant' ? 'assistant' : 'user', content: text });
  }
  const schema = schemaOf(body);
  return {
    messages,
    max_tokens: maxOutput,
    ...(typeof body.temperature === 'number' ? { temperature: body.temperature } : {}),
    ...(schema ? { response_format: { type: 'json_schema', json_schema: schema } } : {}),
  };
}

async function callWorkersAi(env: Env, cfg: Config, body: Record<string, unknown>, model: string, maxOutput: number, o: CallOptions): Promise<Normalized> {
  const ai = env.AI!;
  const opts = cfg.aiGatewayId ? { gateway: { id: cfg.aiGatewayId, metadata: { tier: o.tier, photo: o.hasImages, fallback: !!o.fallback } } } : {};
  let res: {
    response?: unknown;
    choices?: { message?: { content?: unknown } }[];
    usage?: { prompt_tokens?: number; completion_tokens?: number };
  };
  try {
    res = (await Promise.race([
      ai.run(model, workersAiRequest(body, maxOutput), opts),
      new Promise((_, reject) => setTimeout(() => reject(new UpstreamError(true, 0, 'Zeitüberschreitung bei Workers AI', true)), o.timeoutMs)),
    ])) as typeof res;
  } catch (e) {
    if (e instanceof UpstreamError) throw e;
    // Ausfall oder nicht erfüllbarer JSON-Modus: Ausweichen auf die nächste Stufe ist in beiden Fällen sinnvoll.
    throw new UpstreamError(true, 502, `Workers AI: ${e instanceof Error ? e.message : String(e)}`);
  }
  // Ältere Modelle: { response }, neuere: OpenAI-Format { choices: [{ message: { content } }] }.
  const r = res?.response ?? res?.choices?.[0]?.message?.content;
  const text = typeof r === 'string' ? r : r && typeof r === 'object' ? JSON.stringify(r) : '';
  const u = res?.usage;
  const usage: Usage | null =
    u && typeof u.prompt_tokens === 'number' && typeof u.completion_tokens === 'number'
      ? { input_tokens: u.prompt_tokens, output_tokens: u.completion_tokens }
      : null;
  const stop = usage && (usage.output_tokens ?? 0) >= maxOutput ? 'max_tokens' : 'end_turn';
  return { body: anthropicShape(text, stop, model, usage), usage };
}

function anthropicShape(text: string, stop: string, model: string, usage: Usage | null, id?: unknown): Record<string, unknown> {
  return {
    id: typeof id === 'string' ? `msg_${id}` : `msg_${crypto.randomUUID().replace(/-/g, '')}`,
    type: 'message',
    role: 'assistant',
    model,
    content: text ? [{ type: 'text', text }] : [],
    stop_reason: stop,
    stop_sequence: null,
    usage: {
      input_tokens: usage?.input_tokens ?? 0,
      output_tokens: usage?.output_tokens ?? 0,
      cache_read_input_tokens: usage?.cache_read_input_tokens ?? 0,
      cache_creation_input_tokens: usage?.cache_creation_input_tokens ?? 0,
    },
  };
}

export function callTier(env: Env, cfg: Config, tier: Tier, body: Record<string, unknown>, maxTokens: number, o: CallOptions): Promise<Normalized> {
  const def = TIERS[tier];
  const limit = outputLimit(tier, maxTokens, cfg);
  switch (def.provider) {
    case 'anthropic':
      return callAnthropic(env, cfg, body, def.model, limit, o);
    case 'gemini':
      return callGemini(env, cfg, body, def.model, limit, o);
    case 'workers-ai':
      return callWorkersAi(env, cfg, body, def.model, limit, o);
  }
}
