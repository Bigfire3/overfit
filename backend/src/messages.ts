import { ApiError } from './errors';
import type { Config, Device, Env } from './types';
import { availableMicro, release, reserve, settle, now } from './ledger';
import { costMicro } from './money';
import { randomId } from './crypto';
import { type Tier, callTier, outputLimit, tierAvailable, tierForModel, TIER_NAMES, TIERS, UpstreamError } from './providers';

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
  // `model` ist eine Stufe (standard | pro | lite); alte App-Versionen schicken die Modell-ID (→ deren Stufe).
  const tier = typeof body.model === 'string' ? tierForModel(body.model) : null;
  if (!tier) throw new ApiError(400, 'model_not_allowed', 'Stufe nicht erlaubt', { allowed: TIER_NAMES });
  const maxTokens = body.max_tokens;
  if (!Number.isInteger(maxTokens) || (maxTokens as number) < 1 || (maxTokens as number) > cfg.maxTokensCap) {
    throw new ApiError(400, 'invalid_max_tokens', `max_tokens muss 1..${cfg.maxTokensCap} sein`, { max: cfg.maxTokensCap });
  }
  if (body.stream) throw new ApiError(400, 'streaming_not_supported', 'Streaming wird nicht unterstützt');
  if (!Array.isArray(body.messages) || body.messages.length === 0) {
    throw new ApiError(400, 'invalid_request', 'messages fehlt');
  }
  const images = countImages(body.messages);
  if (images > 0 && !TIERS[tier].images) {
    throw new ApiError(400, 'images_not_supported', `Die Stufe ${tier} verarbeitet keine Bilder`, { tier });
  }
  if (!tierAvailable(env, tier)) {
    const available = TIER_NAMES.filter((t) => tierAvailable(env, t));
    if (available.length === 0) throw new ApiError(503, 'not_configured', 'Coach-Dienst nicht konfiguriert');
    throw new ApiError(503, 'tier_unavailable', `Stufe ${tier} ist nicht verfügbar`, { tier, available });
  }

  const price = cfg.prices[TIERS[tier].model];
  const maxPrice = { ...price, in: Math.max(price.in, price.cacheWrite) };
  const hold = costMicro(
    { input_tokens: estimateInputTokens(body, cfg), output_tokens: outputLimit(tier, maxTokens as number, cfg) },
    maxPrice,
    cfg.markupBp,
  );
  const holdId = await reserve(env, device.accountId, hold);
  if (!holdId) {
    throw new ApiError(402, 'insufficient_funds', 'Guthaben reicht nicht', {
      requiredMicro: hold,
      availableMicro: await availableMicro(env, device.accountId),
    });
  }

  // Erst die angefragte Stufe, bei Ausfall einmal die Ausweich-Stufe (falls verfügbar und noch Zeit übrig ist).
  const started = Date.now();
  const opts = (timeoutMs: number) => ({ timeoutMs, hasImages: images > 0, tier });
  let served: Tier = tier;
  let result;
  try {
    try {
      result = await callTier(env, cfg, tier, body, maxTokens as number, opts(Math.min(cfg.upstreamTimeoutMs, cfg.upstreamBudgetMs)));
    } catch (e) {
      const fb = TIERS[tier].fallback;
      const left = cfg.upstreamBudgetMs - (Date.now() - started);
      if (!(e instanceof UpstreamError) || !e.unavailable || !fb || !tierAvailable(env, fb) || left < 15_000) throw e;
      if (images > 0 && !TIERS[fb].images) throw e;
      console.warn('fallback', tier, '→', fb, e.status, e.message);
      served = fb;
      result = await callTier(env, cfg, fb, body, maxTokens as number, { ...opts(Math.min(cfg.upstreamTimeoutMs, left)), fallback: true });
    }
  } catch (e) {
    await release(env, holdId);
    if (!(e instanceof UpstreamError)) throw e;
    if (e.timeout || e.status === 0) {
      throw new ApiError(504, 'upstream_unavailable', `${e.message}, nichts abgebucht`, { upstreamStatus: e.status });
    }
    if (!e.unavailable) {
      throw new ApiError(400, 'upstream_rejected', e.message || 'KI-Anbieter hat die Anfrage abgelehnt, nichts abgebucht', {
        upstreamStatus: e.status,
      });
    }
    throw new ApiError(502, 'upstream_unavailable', e.message || 'KI-Anbieter nicht verfügbar, nichts abgebucht', { upstreamStatus: e.status });
  }

  const servedModel = TIERS[served].model;
  const usage = result.usage;
  // Abgerechnet wird höchstens der Preis der angefragten Stufe (Ausweichen auf eine teurere Stufe trägt die Marge).
  // Ohne Nutzungsangabe sicherheitshalber der reservierte Höchstbetrag.
  const cost = usage
    ? Math.min(costMicro(usage, cfg.prices[servedModel], cfg.markupBp), costMicro(usage, price, cfg.markupBp))
    : hold;
  const charged = await settle(env, device.accountId, holdId, hold, cost, {
    model: servedModel,
    tier,
    input: usage?.input_tokens ?? 0,
    cacheRead: usage?.cache_read_input_tokens ?? 0,
    cacheWrite: usage?.cache_creation_input_tokens ?? 0,
    output: usage?.output_tokens ?? 0,
    providerCostMicro: usage ? costMicro(usage, cfg.prices[servedModel], 0) : null,
    fallback: served !== tier,
    photo: images > 0,
  });
  if (charged > 0) await rewardInviter(env, cfg, device.accountId);
  const balance = await availableMicro(env, device.accountId);
  return Response.json(
    {
      ...result.body,
      overfit: { costMicro: charged, balanceMicro: balance, currency: 'EUR', tier, servedTier: served, model: servedModel, modelName: TIERS[served].displayName, vendor: TIERS[served].vendor, requestedModel: TIERS[tier].model, requestedModelName: TIERS[tier].displayName, requestedVendor: TIERS[tier].vendor, fallback: served !== tier },
    },
    { headers: { 'x-overfit-cost-micro': String(charged), 'x-overfit-balance-micro': String(balance) } },
  );
}

/** Erste abgerechnete Coach-Anfrage eines eingeladenen Kontos → Gutschrift an den Einladenden (einmalig). */
async function rewardInviter(env: Env, cfg: Config, redeemerAccountId: string): Promise<void> {
  const red = await env.DB.prepare(
    'SELECT id, inviter_account_id FROM spot_redemptions WHERE redeemer_account_id = ? AND rewarded_at IS NULL',
  )
    .bind(redeemerAccountId)
    .first<{ id: string; inviter_account_id: string }>();
  if (!red || cfg.spotRewardMicro <= 0) return;
  const t = now();
  const expires = t + cfg.rewardExpiryDays * 86400;
  const amount = cfg.spotRewardMicro;
  // changes() bezieht sich auf das jeweils vorherige Statement der Batch-Transaktion: gutgeschrieben wird nur,
  // wenn dieser Aufruf die Markierung „belohnt“ gesetzt hat (kein doppeltes Gutschreiben bei parallelen Anfragen).
  await env.DB.batch([
    env.DB.prepare('UPDATE spot_redemptions SET rewarded_at = ? WHERE id = ? AND rewarded_at IS NULL').bind(t, red.id),
    env.DB.prepare(
      `INSERT INTO lots (id, account_id, kind, amount_micro, remaining_micro, expires_at, created_at, ref)
       SELECT ?, ?, 'spot_reward', ?, ?, ?, ?, ? WHERE changes() > 0`,
    ).bind(randomId(), red.inviter_account_id, amount, amount, expires, t, red.id),
    env.DB.prepare(
      `INSERT INTO ledger (account_id, ts, type, amount_micro, ref) SELECT ?, ?, 'spot_reward', ?, ? WHERE changes() > 0`,
    ).bind(red.inviter_account_id, t, amount, red.id),
  ]);
}
