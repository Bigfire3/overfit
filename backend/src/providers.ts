import type { Config, Env, Price } from './types';
import type { Usage } from './money';
import {
  type ModelDef, type ProviderKind, type Tier, DEFAULT_TIER, ROUTING, TIER_NAMES, priceAt, routes,
} from './routing';

/**
 * Anbieter-Schicht des Guthabens (docs/DECISIONS.md „Anbieter umschaltbar“). Die App schickt immer das
 * Anthropic-Messages-Format mit `model` = Stufe; welches Modell welches Anbieters dahinter steckt, steht in
 * llm-routing.jsonc (src/routing.ts). Jeder Adapter übersetzt Anfrage und Antwort und bildet Fehler einheitlich auf
 * `UpstreamError` ab; die Antwort geht immer im Anthropic-Format an die App zurück.
 */
export type { Tier, ProviderKind, ModelDef };
export { TIER_NAMES, DEFAULT_TIER };

/**
 * Typische Token-Mengen je Aufgabe für die Kostenschätzung in der App, gemessen an den echten Prompts
 * (Methode: docs/DECISIONS.md „Kostenschätzung“). Ohne Cache-Rabatt, also eher zu hoch als zu niedrig.
 */
export const USAGE_PROFILES = {
  coach: { inputTokens: 8200, outputTokens: 1400 },
  chat: { inputTokens: 6700, outputTokens: 500 },
  autofill: { inputTokens: 900, outputTokens: 120 },
} as const;

/** Wechselkurs für alle Listenpreise in USD (Wert aus llm-routing.jsonc). */
export const EUR_PER_USD = ROUTING.usdToEur;
const NEURON_MILLI_MICRO = Math.round(0.011 * EUR_PER_USD * 1_000_000);
const neurons = (perMTok: number) => Math.round((perMTok * NEURON_MILLI_MICRO) / 1000);

/** Modelle, die nur noch in alten Buchungen vorkommen (Preis für Kostenrechnung und Anzeige). */
const RETIRED: Record<string, { price: Price; displayName: string; vendor: string }> = {
  '@cf/meta/llama-3.1-8b-instruct': {
    price: { in: neurons(25_608), cacheRead: neurons(25_608), cacheWrite: neurons(25_608), out: neurons(75_147) },
    displayName: 'Llama 3.1 8B',
    vendor: 'Cloudflare',
  },
};

/** Aktuell gültiger Preis je Modell (µ€/MTok, zum Zeitpunkt `nowMs`): alle Modelle der Routing-Tabelle plus alte. */
export function currentPrices(nowMs = Date.now()): Record<string, Price> {
  const out: Record<string, Price> = {};
  for (const [id, r] of Object.entries(RETIRED)) out[id] = r.price;
  for (const m of Object.values(ROUTING.models)) out[m.id] = priceAt(m.prices, nowMs);
  return out;
}

/** Anzeigenamen aller bekannten Modelle (für App: Protokoll, Ledger, Antworten). */
export function modelNames(): Record<string, { displayName: string; vendor: string }> {
  const out: Record<string, { displayName: string; vendor: string }> = {};
  for (const [id, r] of Object.entries(RETIRED)) out[id] = { displayName: r.displayName, vendor: r.vendor };
  for (const m of Object.values(ROUTING.models)) out[m.id] = { displayName: m.displayName, vendor: m.vendor };
  return out;
}

/** Feste Zuordnung alter Modell-IDs (alte App-Versionen schicken die Modell-ID statt der Stufe). */
const LEGACY_MODELS: Record<string, Tier> = {
  'gemini-3.6-flash': 'standard',
  '@cf/meta/llama-3.1-8b-instruct': 'lite',
  '@cf/meta/llama-3.3-70b-instruct-fp8-fast': 'lite',
  'claude-haiku-5-5': 'lite',
  'claude-sonnet-5-5': 'standard',
  'claude-opus-5-5': 'pro',
};

/** Stufe zu einem `model`-Wert: Stufenname, aktuelles Modell einer Stufe oder bekanntes früheres Modell. */
export function tierForModel(model: string, env?: Env): Tier | null {
  if ((TIER_NAMES as string[]).includes(model)) return model as Tier;
  if (env) {
    const r = routes(env).tiers;
    const hit = TIER_NAMES.find((t) => r[t].primary?.id === model);
    if (hit) return hit;
  }
  return LEGACY_MODELS[model] ?? null;
}

/**
 * Höchstzahl Ausgabe-Token, die beim Anbieter angefragt (und reserviert) wird. Denkende Modelle (Gemini, GPT-5) zählen
 * Denk-Token in die Ausgabe, deshalb bekommen sie das Doppelte (mindestens 4096, höchstens 2 × Obergrenze).
 */
export function outputLimit(model: ModelDef, maxTokens: number, cfg: Config): number {
  if (model.reasoning) return Math.min(Math.max(maxTokens * 2, 4096), cfg.maxTokensCap * 2);
  return maxTokens;
}

// ---- Gemeinsame Teile ------------------------------------------------------------------------------------------

export interface Normalized {
  /** Antwort im Anthropic-Format (ohne `overfit`). */
  body: Record<string, unknown>;
  usage: Usage | null;
}

/** Fehler beim Anbieter, für alle Anbieter gleich. `unavailable` = Ausfall (Ausweichen erlaubt), sonst Ablehnung der Anfrage. */
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

export function systemText(system: unknown): string {
  return blocks(system)
    .filter((b) => b.type === 'text')
    .map((b) => String(b.text ?? ''))
    .join('\n\n');
}

function schemaOf(body: Record<string, unknown>): unknown | null {
  const fmt = (body.output_config as { format?: { type?: string; schema?: unknown } } | undefined)?.format;
  return fmt?.type === 'json_schema' && fmt.schema ? fmt.schema : null;
}

function effortOf(body: Record<string, unknown>): 'low' | 'medium' | 'high' | null {
  const e = (body.output_config as { effort?: string } | undefined)?.effort;
  return e === 'low' || e === 'medium' || e === 'high' ? e : null;
}

function imageSource(b: Block): { mediaType: string; data: string } | null {
  const src = b.source as { type?: string; media_type?: string; data?: string } | undefined;
  return b.type === 'image' && src?.type === 'base64' && typeof src.data === 'string' ? { mediaType: src.media_type ?? 'image/jpeg', data: src.data } : null;
}

/** HTTP-Gateway-Name und direkte Basis-URL je Anbieter (Workers AI läuft über das Binding). */
const HTTP: Record<Exclude<ProviderKind, 'workers-ai'>, { gateway: string; direct: string; override: keyof Env }> = {
  anthropic: { gateway: 'anthropic', direct: 'https://api.anthropic.com', override: 'ANTHROPIC_BASE_URL' },
  openai: { gateway: 'openai', direct: 'https://api.openai.com/v1', override: 'OPENAI_BASE_URL' },
  gemini: { gateway: 'google-ai-studio', direct: 'https://generativelanguage.googleapis.com', override: 'GEMINI_BASE_URL' },
  openrouter: { gateway: 'openrouter', direct: 'https://openrouter.ai/api', override: 'OPENROUTER_BASE_URL' },
};
type HttpProvider = keyof typeof HTTP;

/**
 * Basis-URL eines Anbieters: ausdrücklicher Override (Tests, Dev) → AI Gateway → direkt.
 * Das Gateway nur mit `AI_GATEWAY_TOKEN`: Cloudflare legt neue Gateways seit 2026 immer mit Pflicht-Authentifizierung an
 * (nicht abschaltbar), ohne `cf-aig-authorization` antwortet es mit 401. Ohne Token geht jede Anfrage direkt an den Anbieter.
 */
async function route(env: Env, cfg: Config, provider: HttpProvider): Promise<{ url: string; gateway: boolean }> {
  const override = env[HTTP[provider].override] as string | undefined;
  if (override) return { url: override.replace(/\/+$/, ''), gateway: false };
  if (cfg.aiGatewayId && env.AI && env.AI_GATEWAY_TOKEN) {
    return { url: (await env.AI.gateway(cfg.aiGatewayId).getUrl(HTTP[provider].gateway)).replace(/\/+$/, ''), gateway: true };
  }
  return { url: HTTP[provider].direct, gateway: false };
}

/**
 * Anfrage an einen Anbieter, über das Gateway, falls eingerichtet. Lehnt das Gateway selbst ab (401/403, z. B. Token
 * falsch oder abgelaufen), geht dieselbe Anfrage einmal direkt an den Anbieter: Ein Gateway-Problem darf die
 * Auswertung nicht verhindern.
 */
async function providerPost(
  env: Env,
  cfg: Config,
  provider: HttpProvider,
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
    return post(`${HTTP[provider].direct}${path}`, headers, body, Math.max(o.timeoutMs - (Date.now() - started), 1_000));
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

/** Einheitliche Fehlerabbildung: Netzwerk/Zeit → Ausfall; 5xx, 429, 408, 401, 403 → Ausfall; sonst Ablehnung der Anfrage. */
export function upstreamErrorFor(status: number, parsed: Record<string, unknown> | null): UpstreamError {
  const err = parsed?.error as { message?: string } | { message?: string }[] | string | undefined;
  // Anthropic/OpenAI/Gemini: { error: { message } }; Cloudflare (AI Gateway): { error: [{ code, message }] }; OpenRouter teils { error: "…" }.
  const detail = typeof err === 'string' ? err : Array.isArray(err) ? err.map((x) => x?.message).filter(Boolean).join('; ') : err?.message;
  const msg = detail || `KI-Anbieter antwortet mit HTTP ${status}`;
  // 401/403: Schlüssel oder Gateway des Betreibers kaputt; 404: Modell-ID in llm-routing.jsonc falsch oder abgekündigt;
  // 402: Konto beim Anbieter leer (OpenRouter). Alles Betreiberfehler, nicht die Anfrage → wie ein Ausfall (Ausweichen, nichts abgebucht).
  const unavailable = status < 400 || status >= 500 || [401, 402, 403, 404, 408, 429].includes(status);
  return new UpstreamError(unavailable, status, msg);
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
  if (!res.ok || !parsed || typeof parsed !== 'object' || Array.isArray(parsed)) throw upstreamErrorFor(res.ok ? 502 : res.status, parsed);
  // OpenRouter meldet manche Fehler mit HTTP 200 und `error` im Body.
  if (parsed.error && !parsed.choices && !parsed.content && !parsed.candidates) {
    const code = Number((parsed.error as { code?: unknown }).code);
    throw upstreamErrorFor(Number.isInteger(code) && code >= 400 ? code : 502, parsed);
  }
  return parsed;
}

export function anthropicShape(text: string, stop: string, model: string, usage: Usage | null, id?: unknown): Record<string, unknown> {
  return {
    id: typeof id === 'string' && id ? `msg_${id}` : `msg_${crypto.randomUUID().replace(/-/g, '')}`,
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

// ---- Anthropic -------------------------------------------------------------------------------------------------

/** Body unverändert, nur das Modell; `effort: medium`, wenn die App keins schickt. */
async function callAnthropic(env: Env, cfg: Config, body: Record<string, unknown>, model: string, maxTokens: number, o: CallOptions): Promise<Normalized> {
  const outputConfig = { ...((body.output_config as object | undefined) ?? {}) } as Record<string, unknown>;
  if (outputConfig.effort === undefined) outputConfig.effort = 'medium';
  const req = { ...body, model, max_tokens: maxTokens, output_config: outputConfig };
  const res = await providerPost(env, cfg, 'anthropic', '/v1/messages', { 'x-api-key': env.ANTHROPIC_API_KEY!, 'anthropic-version': '2023-06-01' }, req, o);
  const u = res.usage as Record<string, number> | undefined;
  const usage = u && typeof u.input_tokens === 'number' && typeof u.output_tokens === 'number' ? (u as Usage) : null;
  return { body: res, usage };
}

// ---- OpenAI und OpenRouter (Chat Completions) ------------------------------------------------------------------

/** Anthropic-Body → OpenAI Chat Completions (auch OpenRouter). Bilder als Data-URL, JSON über `response_format`. */
export function openAiRequest(body: Record<string, unknown>, model: string, maxOutput: number, provider: 'openai' | 'openrouter'): Record<string, unknown> {
  const messages: Record<string, unknown>[] = [];
  const sys = systemText(body.system);
  if (sys) messages.push({ role: provider === 'openai' ? 'developer' : 'system', content: sys });
  for (const m of body.messages as { role?: string; content?: unknown }[]) {
    const role = m.role === 'assistant' ? 'assistant' : 'user';
    const parts = blocks(m.content).flatMap((b): Record<string, unknown>[] => {
      if (b.type === 'text') return [{ type: 'text', text: String(b.text ?? '') }];
      const img = imageSource(b);
      if (img && role === 'user') return [{ type: 'image_url', image_url: { url: `data:${img.mediaType};base64,${img.data}` } }];
      return [];
    });
    const textOnly = parts.every((p) => p.type === 'text');
    messages.push({ role, content: textOnly ? parts.map((p) => p.text).join('\n') : parts });
  }
  const req: Record<string, unknown> = { model, messages };
  const schema = schemaOf(body);
  if (schema) req.response_format = { type: 'json_schema', json_schema: { name: 'overfit', schema, strict: false } };
  const effort = effortOf(body);
  if (provider === 'openai') {
    // GPT-5: `max_completion_tokens` (inkl. Denk-Token), keine eigene Temperatur.
    req.max_completion_tokens = maxOutput;
    req.reasoning_effort = effort ?? 'medium';
  } else {
    req.max_tokens = maxOutput;
    if (typeof body.temperature === 'number') req.temperature = body.temperature;
    req.usage = { include: true };
  }
  return req;
}

const OPENAI_STOP: Record<string, string> = { stop: 'end_turn', length: 'max_tokens', content_filter: 'refusal', tool_calls: 'end_turn' };

export function openAiToAnthropic(res: Record<string, unknown>, model: string): Normalized {
  const choice = (res.choices as { message?: { content?: unknown; refusal?: unknown }; finish_reason?: string }[] | undefined)?.[0];
  const content = choice?.message?.content;
  const text = typeof content === 'string' ? content : Array.isArray(content) ? content.map((p) => (p as { text?: string })?.text ?? '').join('') : '';
  const refusal = typeof choice?.message?.refusal === 'string' && choice.message.refusal;
  const stop = refusal ? 'refusal' : (OPENAI_STOP[choice?.finish_reason ?? 'stop'] ?? 'end_turn');
  const u = res.usage as { prompt_tokens?: number; completion_tokens?: number; prompt_tokens_details?: { cached_tokens?: number } } | undefined;
  let usage: Usage | null = null;
  if (u && typeof u.prompt_tokens === 'number' && typeof u.completion_tokens === 'number') {
    const cached = u.prompt_tokens_details?.cached_tokens ?? 0;
    usage = { input_tokens: Math.max(u.prompt_tokens - cached, 0), cache_read_input_tokens: cached, cache_creation_input_tokens: 0, output_tokens: u.completion_tokens };
  }
  return { body: anthropicShape(text || (refusal as string) || '', stop, model, usage, res.id), usage };
}

async function callOpenAi(env: Env, cfg: Config, provider: 'openai' | 'openrouter', body: Record<string, unknown>, model: string, maxOutput: number, o: CallOptions): Promise<Normalized> {
  const key = provider === 'openai' ? env.OPENAI_API_KEY! : env.OPENROUTER_API_KEY!;
  const path = provider === 'openai' ? '/chat/completions' : '/v1/chat/completions';
  const headers: Record<string, string> = { authorization: `Bearer ${key}` };
  if (provider === 'openrouter') headers['x-title'] = 'Overfit';
  const res = await providerPost(env, cfg, provider, path, headers, openAiRequest(body, model, maxOutput, provider), o);
  if (!Array.isArray(res.choices) || res.choices.length === 0) throw new UpstreamError(true, 502, `${provider}: Antwort ohne Inhalt`);
  return openAiToAnthropic(res, model);
}

// ---- Google Gemini ---------------------------------------------------------------------------------------------

const GEMINI_STOP: Record<string, string> = {
  STOP: 'end_turn', MAX_TOKENS: 'max_tokens', SAFETY: 'refusal', RECITATION: 'refusal', BLOCKLIST: 'refusal',
  PROHIBITED_CONTENT: 'refusal', SPII: 'refusal', IMAGE_SAFETY: 'refusal',
};

/** Anthropic-Body → Gemini generateContent (native API, Structured Output über `responseJsonSchema`). */
export function geminiRequest(body: Record<string, unknown>, maxOutput: number): Record<string, unknown> {
  const contents = (body.messages as { role?: string; content?: unknown }[]).map((m) => ({
    role: m.role === 'assistant' ? 'model' : 'user',
    parts: blocks(m.content).flatMap((b): Record<string, unknown>[] => {
      if (b.type === 'text') return [{ text: String(b.text ?? '') }];
      const img = imageSource(b);
      return img ? [{ inlineData: { mimeType: img.mediaType, data: img.data } }] : [];
    }),
  }));
  const generationConfig: Record<string, unknown> = {
    maxOutputTokens: maxOutput,
    thinkingConfig: { thinkingLevel: effortOf(body) ?? 'medium' },
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
  const res = await providerPost(env, cfg, 'gemini', `/v1beta/models/${model}:generateContent`, { 'x-goog-api-key': env.GEMINI_API_KEY! }, geminiRequest(body, maxOutput), o);
  if (!res.candidates && !res.promptFeedback) throw new UpstreamError(true, 502, 'Gemini-Antwort ohne Inhalt');
  return geminiToAnthropic(res, model);
}

// ---- Cloudflare Workers AI -------------------------------------------------------------------------------------

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

export function workersAiToAnthropic(res: unknown, model: string, maxOutput: number): Normalized {
  const r = res as { response?: unknown; choices?: { message?: { content?: unknown } }[]; usage?: { prompt_tokens?: number; completion_tokens?: number } } | null;
  // Ältere Modelle: { response }, neuere: OpenAI-Format { choices: [{ message: { content } }] }.
  const out = r?.response ?? r?.choices?.[0]?.message?.content;
  const text = typeof out === 'string' ? out : out && typeof out === 'object' ? JSON.stringify(out) : '';
  const u = r?.usage;
  const usage: Usage | null =
    u && typeof u.prompt_tokens === 'number' && typeof u.completion_tokens === 'number' ? { input_tokens: u.prompt_tokens, output_tokens: u.completion_tokens } : null;
  const stop = usage && (usage.output_tokens ?? 0) >= maxOutput ? 'max_tokens' : 'end_turn';
  return { body: anthropicShape(text, stop, model, usage), usage };
}

async function callWorkersAi(env: Env, cfg: Config, body: Record<string, unknown>, model: string, maxOutput: number, o: CallOptions): Promise<Normalized> {
  const ai = env.AI!;
  // Über das Gateway nur mit Token (wie bei den HTTP-Anbietern; authentifizierte Gateways lehnen sonst ab).
  const opts = cfg.aiGatewayId && env.AI_GATEWAY_TOKEN ? { gateway: { id: cfg.aiGatewayId, metadata: { tier: o.tier, photo: o.hasImages, fallback: !!o.fallback } } } : {};
  let res: unknown;
  let timer: ReturnType<typeof setTimeout> | undefined;
  try {
    res = await Promise.race([
      ai.run(model, workersAiRequest(body, maxOutput), opts),
      new Promise((_, reject) => {
        timer = setTimeout(() => reject(new UpstreamError(true, 0, 'Zeitüberschreitung bei Workers AI', true)), o.timeoutMs);
      }),
    ]);
  } catch (e) {
    if (e instanceof UpstreamError) throw e;
    // Ausfall, Tageskontingent aufgebraucht oder nicht erfüllbarer JSON-Modus: Ausweichen ist in allen Fällen sinnvoll.
    throw new UpstreamError(true, 502, `Workers AI: ${e instanceof Error ? e.message : String(e)}`);
  } finally {
    if (timer) clearTimeout(timer);
  }
  return workersAiToAnthropic(res, model, maxOutput);
}

// ---- Einstieg --------------------------------------------------------------------------------------------------

/** Ruft ein Modell der Routing-Tabelle; Antwort immer im Anthropic-Format. */
export function callModel(env: Env, cfg: Config, def: ModelDef, body: Record<string, unknown>, maxTokens: number, o: CallOptions): Promise<Normalized> {
  const limit = outputLimit(def, maxTokens, cfg);
  switch (def.provider) {
    case 'anthropic':
      return callAnthropic(env, cfg, body, def.id, limit, o);
    case 'openai':
    case 'openrouter':
      return callOpenAi(env, cfg, def.provider, body, def.id, limit, o);
    case 'gemini':
      return callGemini(env, cfg, body, def.id, limit, o);
    case 'workers-ai':
      return callWorkersAi(env, cfg, body, def.id, limit, o);
  }
}
