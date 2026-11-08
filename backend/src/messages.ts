import { ApiError } from './errors';
import type { Config, Device, Env } from './types';
import { availableMicro, release, reserve, settle } from './ledger';
import { costMicro } from './money';
import { type ModelDef, callModel, outputLimit, tierForModel, TIER_NAMES, UpstreamError } from './providers';
import { routes } from './routing';
import { logRequest, purposeOf } from './requests';

const enc = new TextEncoder();

/** Obergrenze der Eingabe-Token. Bilder zählen pauschal, Base64-Daten werden nicht als Text gerechnet. */
export function estimateInputTokens(body: Record<string, unknown>, cfg: Config): number {
  let images = 0;
  const json = JSON.stringify(body, (key, value) => {
    if (key === 'source' && value && typeof value === 'object' && (value as { type?: string }).type === 'base64') {
      images++;
      return { type: 'base64' };
    }
    return value;
  });
  return Math.ceil(enc.encode(json).length / 2) + images * cfg.imageTokensReserve;
}

/** Zählt Bild-Blöcke in allen Nachrichten. */
function countImages(messages: unknown[]): number {
  let n = 0;
  for (const m of messages) {
    const c = (m as { content?: unknown })?.content;
    if (Array.isArray(c)) n += c.filter((b) => (b as { type?: string })?.type === 'image').length;
  }
  return n;
}

export async function handleMessages(env: Env, cfg: Config, device: Device, bodyBytes: Uint8Array): Promise<Response> {
  let body: Record<string, unknown>;
  try {
    body = JSON.parse(new TextDecoder().decode(bodyBytes));
    if (!body || typeof body !== 'object' || Array.isArray(body)) throw 0;
  } catch {
    throw new ApiError(400, 'invalid_request', 'Body muss ein JSON-Objekt sein');
  }
  // Overfit-eigene Angaben (`overfit.purpose`: review | chat | autofill | plan | other) gehen nie an den KI-Anbieter.
  const purpose = purposeOf((body.overfit as { purpose?: unknown } | undefined)?.purpose);
  const t0 = Date.now();
  const ts = Math.floor(t0 / 1000);
  if ('overfit' in body) {
    const { overfit: _, ...rest } = body;
    body = rest;
  }
  // `model` ist eine Stufe (standard | pro | lite); alte App-Versionen schicken die Modell-ID (→ deren Stufe).
  const tier = typeof body.model === 'string' ? tierForModel(body.model, env) : null;
  if (!tier) throw new ApiError(400, 'model_not_allowed', 'Stufe nicht erlaubt', { allowed: TIER_NAMES });
  // Mehr als die Obergrenze wird gekappt statt abgelehnt: Die App schickt 4000 (Chat) bzw. 8000 (Auswertung), der
  // Testserver erlaubt nur MAX_TOKENS_CAP (vor dem 8.11. kam dort 400 invalid_max_tokens → „Provider rejected the request“).
  if (!Number.isInteger(body.max_tokens) || (body.max_tokens as number) < 1) {
    throw new ApiError(400, 'invalid_max_tokens', `max_tokens muss 1..${cfg.maxTokensCap} sein`, { max: cfg.maxTokensCap });
  }
  const maxTokens = Math.min(body.max_tokens as number, cfg.maxTokensCap);
  body.max_tokens = maxTokens;
  if (body.stream) throw new ApiError(400, 'streaming_not_supported', 'Streaming wird nicht unterstützt');
  if (!Array.isArray(body.messages) || body.messages.length === 0) {
    throw new ApiError(400, 'invalid_request', 'messages fehlt');
  }
  const images = countImages(body.messages);
  const all = routes(env).tiers;
  const route = all[tier];
  if (!route.primary) {
    const available = TIER_NAMES.filter((t) => all[t].primary);
    if (available.length === 0) throw new ApiError(503, 'not_configured', 'Coach-Dienst nicht konfiguriert');
    throw new ApiError(503, 'tier_unavailable', `Stufe ${tier} ist nicht verfügbar`, { tier, available });
  }
  // Abgerechnet wird nach dem Modell der Stufe (requested). Kann es keine Fotos, antwortet gleich das Ausweich-Modell, falls das Fotos kann.
  const requested: ModelDef = route.primary;
  let first: ModelDef = requested;
  let second: ModelDef | null = route.fallback;
  if (images > 0 && !requested.images) {
    if (!route.fallback?.images) throw new ApiError(400, 'images_not_supported', `Die Stufe ${tier} verarbeitet keine Bilder`, { tier });
    first = route.fallback;
    second = null;
  }
  if (images > 0 && second && !second.images) second = null;

  const price = cfg.prices[requested.id];
  const maxPrice = { ...price, in: Math.max(price.in, price.cacheWrite) };
  // Reserve für den teuersten möglichen Verlauf: Preis der Stufe (mehr wird nie berechnet) × größter Ausgabe-Raum der Kandidaten.
  const maxOut = Math.max(...[first, second].filter((m): m is ModelDef => !!m).map((m) => outputLimit(m, maxTokens as number, cfg)));
  const hold = costMicro({ input_tokens: estimateInputTokens(body, cfg), output_tokens: maxOut }, maxPrice, cfg.markupBp);
  const holdId = await reserve(env, device.accountId, hold);
  const log = { accountId: device.accountId, ts, purpose, tier, requestedModel: requested.id, requestedProvider: requested.provider, photo: images > 0 };
  if (!holdId) {
    const requestId = await logRequest(env, { ...log, durationMs: Date.now() - t0, status: 'failed', errorCode: 'insufficient_funds' });
    throw new ApiError(402, 'insufficient_funds', 'Guthaben reicht nicht', {
      requestId,
      requiredMicro: hold,
      availableMicro: await availableMicro(env, device.accountId),
    });
  }

  // Erst das Modell der Stufe, bei Ausfall einmal das Ausweich-Modell (auch bei einem anderen Anbieter, falls bereit und noch Zeit ist).
  const started = Date.now();
  const opts = (timeoutMs: number, fallback: boolean) => ({ timeoutMs, hasImages: images > 0, tier, fallback });
  let served: ModelDef = first;
  let firstFailure = null as UpstreamError | null;
  const substituted = first !== requested;
  let result;
  try {
    try {
      result = await callModel(env, cfg, first, body, maxTokens as number, opts(Math.min(cfg.upstreamTimeoutMs, cfg.upstreamBudgetMs), substituted));
    } catch (e) {
      const left = cfg.upstreamBudgetMs - (Date.now() - started);
      if (!(e instanceof UpstreamError) || !e.unavailable || !second || left < 15_000) throw e;
      console.warn('fallback', tier, first.id, '→', second.id, e.status, e.message);
      firstFailure = e;
      served = second;
      result = await callModel(env, cfg, second, body, maxTokens as number, opts(Math.min(cfg.upstreamTimeoutMs, left), true));
    }
  } catch (e) {
    await release(env, holdId);
    const code = !(e instanceof UpstreamError) ? 'internal_error' : e.timeout || e.status === 0 || e.unavailable ? 'upstream_unavailable' : 'upstream_rejected';
    const requestId = await logRequest(env, {
      ...log, durationMs: Date.now() - t0, status: 'failed', errorCode: code,
      fallbackFromStatus: firstFailure?.status ?? null,
      // Ohne Ausweichen steht hier der Fehler des Anbieters als Code („HTTP 400 invalid_request_error“), nie dessen Text.
      fallbackReason: failureLabel(firstFailure) ?? (substituted ? NO_IMAGES : null) ?? failureLabel(e instanceof UpstreamError ? e : null),
    });
    if (!(e instanceof UpstreamError)) throw e;
    if (e.timeout || e.status === 0) {
      throw new ApiError(504, 'upstream_unavailable', `${e.message}, nichts abgebucht`, { upstreamStatus: e.status, requestId });
    }
    if (!e.unavailable) {
      // Nur Status und Fehlercode des Anbieters, nicht sein Fehlertext (kann Teile der Anfrage wiederholen).
      console.warn('upstream_rejected', served.provider, served.id, e.status, e.code ?? '-');
      throw new ApiError(400, 'upstream_rejected', `KI-Anbieter hat die Anfrage abgelehnt (HTTP ${e.status}${e.code ? ` ${e.code}` : ''}), nichts abgebucht`, {
        upstreamStatus: e.status,
        ...(e.code ? { upstreamCode: e.code } : {}),
        requestId,
      });
    }
    throw new ApiError(502, 'upstream_unavailable', e.message || 'KI-Anbieter nicht verfügbar, nichts abgebucht', { upstreamStatus: e.status, requestId });
  }

  const usage = result.usage;
  const fallback = served !== requested;
  const reason = failureLabel(firstFailure) ?? (substituted ? NO_IMAGES : null);
  // Abgerechnet wird aus den echten Token, und zwar zum günstigeren Preis von Stufen-Modell und antwortendem Modell
  // (Ausweichen auf ein teureres Modell trägt die Marge). Ohne Nutzungsangabe sicherheitshalber der reservierte Höchstbetrag.
  const servedPrice = cfg.prices[served.id];
  const cost = usage ? Math.min(costMicro(usage, servedPrice, cfg.markupBp), costMicro(usage, price, cfg.markupBp)) : hold;
  const entry = {
    model: served.id,
    tier,
    input: usage?.input_tokens ?? 0,
    cacheRead: usage?.cache_read_input_tokens ?? 0,
    cacheWrite: usage?.cache_creation_input_tokens ?? 0,
    output: usage?.output_tokens ?? 0,
    providerCostMicro: usage ? costMicro(usage, servedPrice, 0) : null,
    fallback,
    photo: images > 0,
    requestedModel: requested.id,
  };
  const charged = await settle(env, device.accountId, holdId, hold, cost, entry);
  const requestId = await logRequest(env, {
    ...log, durationMs: Date.now() - t0, status: 'ok', model: served.id, provider: served.provider,
    fallbackFromStatus: firstFailure?.status ?? null, fallbackReason: reason,
    input: entry.input, cacheRead: entry.cacheRead, cacheWrite: entry.cacheWrite, output: entry.output, chargedMicro: charged,
  });
  const balance = await availableMicro(env, device.accountId);
  return Response.json(
    {
      ...result.body,
      overfit: {
        costMicro: charged, balanceMicro: balance, currency: 'EUR', tier, servedTier: fallback ? (TIER_NAMES.find((t) => all[t].primary?.id === served.id) ?? tier) : tier,
        model: served.id, modelName: served.displayName, vendor: served.vendor, provider: served.provider,
        requestedModel: requested.id, requestedModelName: requested.displayName, requestedVendor: requested.vendor, requestedProvider: requested.provider,
        fallback, fallbackReason: reason, purpose, requestId,
      },
    },
    { headers: { 'x-overfit-cost-micro': String(charged), 'x-overfit-balance-micro': String(balance) } },
  );
}

/** Grund, wenn ein Foto-Auftrag gleich beim Ausweich-Modell landet (das Modell der Stufe kann keine Bilder). */
const NO_IMAGES = 'keine Bilder';

/**
 * Kurzer, inhaltsfreier Grund, warum das zuerst versuchte Modell nicht antwortete. Bewusst ohne den Fehlertext des Anbieters:
 * der könnte Teile der Anfrage wiederholen, und das Protokoll speichert nie Inhalte.
 */
function failureLabel(e: UpstreamError | null): string | null {
  if (!e) return null;
  if (e.timeout) return 'Zeitüberschreitung';
  if (e.status === 0) return 'nicht erreichbar';
  return e.code ? `HTTP ${e.status} ${e.code}` : `HTTP ${e.status}`;
}
