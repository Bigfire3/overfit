import { ApiError } from './errors';
import type { Config, Device, Env } from './types';
import { availableMicro, giftedCostSince, grantReviewStatements, monthStart, release, reserve, reserveReview, returnReview, settle, settleReview, now } from './ledger';
import { costMicro } from './money';
import { type Tier, callTier, GIFT_TIER, outputLimit, tierAvailable, tierForModel, TIER_NAMES, TIERS, UpstreamError } from './providers';
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
  // Coach-Auswertung mit dem günstigsten Modell (Haiku): zuerst eine geschenkte Auswertung (Einladungsbonus) verbrauchen.
  const giftId = purpose === 'review' ? await reserveGift(env, cfg, device.accountId, tier, hold) : null;
  const holdId = giftId ? null : await reserve(env, device.accountId, hold);
  const log = { accountId: device.accountId, ts, purpose, tier, requestedModel: TIERS[tier].model, photo: images > 0 };
  if (!giftId && !holdId) {
    const requestId = await logRequest(env, { ...log, durationMs: Date.now() - t0, status: 'failed', errorCode: 'insufficient_funds' });
    throw new ApiError(402, 'insufficient_funds', 'Guthaben reicht nicht', {
      requestId,
      requiredMicro: hold,
      availableMicro: await availableMicro(env, device.accountId),
    });
  }

  // Erst die angefragte Stufe, bei Ausfall einmal die Ausweich-Stufe (falls verfügbar und noch Zeit übrig ist).
  const started = Date.now();
  const opts = (timeoutMs: number) => ({ timeoutMs, hasImages: images > 0, tier });
  let served: Tier = tier;
  let firstFailure = null as UpstreamError | null;
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
      firstFailure = e;
      served = fb;
      result = await callTier(env, cfg, fb, body, maxTokens as number, { ...opts(Math.min(cfg.upstreamTimeoutMs, left)), fallback: true });
    }
  } catch (e) {
    if (holdId) await release(env, holdId);
    if (giftId) await returnReview(env, giftId);
    const code = !(e instanceof UpstreamError) ? 'internal_error' : e.timeout || e.status === 0 || e.unavailable ? 'upstream_unavailable' : 'upstream_rejected';
    const requestId = await logRequest(env, {
      ...log, durationMs: Date.now() - t0, status: 'failed', errorCode: code,
      fallbackFromStatus: firstFailure?.status ?? null, fallbackReason: firstFailure?.message ?? (e instanceof Error ? e.message : null),
    });
    if (!(e instanceof UpstreamError)) throw e;
    if (e.timeout || e.status === 0) {
      throw new ApiError(504, 'upstream_unavailable', `${e.message}, nichts abgebucht`, { upstreamStatus: e.status, requestId });
    }
    if (!e.unavailable) {
      throw new ApiError(400, 'upstream_rejected', e.message || 'KI-Anbieter hat die Anfrage abgelehnt, nichts abgebucht', {
        upstreamStatus: e.status,
        requestId,
      });
    }
    throw new ApiError(502, 'upstream_unavailable', e.message || 'KI-Anbieter nicht verfügbar, nichts abgebucht', { upstreamStatus: e.status, requestId });
  }

  const servedModel = TIERS[served].model;
  const usage = result.usage;
  // Abgerechnet wird höchstens der Preis der angefragten Stufe (Ausweichen auf eine teurere Stufe trägt die Marge).
  // Ohne Nutzungsangabe sicherheitshalber der reservierte Höchstbetrag.
  const cost = usage
    ? Math.min(costMicro(usage, cfg.prices[servedModel], cfg.markupBp), costMicro(usage, price, cfg.markupBp))
    : hold;
  const entry = {
    model: servedModel,
    tier,
    input: usage?.input_tokens ?? 0,
    cacheRead: usage?.cache_read_input_tokens ?? 0,
    cacheWrite: usage?.cache_creation_input_tokens ?? 0,
    output: usage?.output_tokens ?? 0,
    // Ohne Nutzungsangabe bei einer geschenkten Auswertung: die reservierten Höchstkosten (fürs Monatsbudget).
    providerCostMicro: usage ? costMicro(usage, cfg.prices[servedModel], 0) : giftId ? Math.ceil(hold / (1 + cfg.markupBp / 10_000)) : null,
    fallback: served !== tier,
    photo: images > 0,
  };
  let charged = 0;
  if (giftId) await settleReview(env, device.accountId, giftId, entry);
  else charged = await settle(env, device.accountId, holdId!, hold, cost, entry);
  const requestId = await logRequest(env, {
    ...log, durationMs: Date.now() - t0, status: 'ok', model: servedModel,
    fallbackFromStatus: firstFailure?.status ?? null, fallbackReason: firstFailure?.message ?? null,
    input: entry.input, cacheRead: entry.cacheRead, cacheWrite: entry.cacheWrite, output: entry.output, chargedMicro: charged, gift: !!giftId,
  });
  await rewardInviter(env, cfg, device.accountId);
  const balance = await availableMicro(env, device.accountId);
  return Response.json(
    {
      ...result.body,
      overfit: { costMicro: charged, balanceMicro: balance, giftReview: !!giftId, currency: 'EUR', tier, servedTier: served, model: servedModel, modelName: TIERS[served].displayName, vendor: TIERS[served].vendor, requestedModel: TIERS[tier].model, requestedModelName: TIERS[tier].displayName, requestedVendor: TIERS[tier].vendor, fallback: served !== tier, fallbackReason: firstFailure ? `HTTP ${firstFailure.status}: ${firstFailure.message}`.slice(0, 200) : null, purpose, requestId },
    },
    { headers: { 'x-overfit-cost-micro': String(charged), 'x-overfit-balance-micro': String(balance) } },
  );
}

/**
 * Geschenkte Auswertung reservieren, wenn die Anfrage dafür in Frage kommt: Stufe `GIFT_TIER` (Claude Haiku 5.5, das günstigste Modell),
 * reservierte Höchstkosten zum Anbieterpreis höchstens `inviteGiftMaxCostMicro`, und das Monatsbudget der echten
 * KI-Kosten für Geschenktes ist nicht ausgeschöpft. Sonst null (dann zahlt das Guthaben).
 */
async function reserveGift(env: Env, cfg: Config, accountId: string, tier: Tier, holdMicro: number): Promise<string | null> {
  if (tier !== GIFT_TIER) return null;
  // Der Hold enthält den Aufschlag; die Anbieterkosten sind Hold ÷ (1 + Aufschlag).
  const maxProviderCost = Math.ceil(holdMicro / (1 + cfg.markupBp / 10_000));
  if (maxProviderCost > cfg.inviteGiftMaxCostMicro) return null;
  const spent = await giftedCostSince(env, monthStart());
  if (spent + maxProviderCost > cfg.inviteGiftMonthlyCostCapMicro) return null;
  return reserveReview(env, accountId);
}

/** Erste erfolgreiche Coach-Anfrage eines eingeladenen Kontos → geschenkte Auswertungen für den Einladenden (einmalig). */
async function rewardInviter(env: Env, cfg: Config, redeemerAccountId: string): Promise<void> {
  const red = await env.DB.prepare(
    'SELECT id, inviter_account_id FROM invite_redemptions WHERE redeemer_account_id = ? AND rewarded_at IS NULL',
  )
    .bind(redeemerAccountId)
    .first<{ id: string; inviter_account_id: string }>();
  // Nie an sich selbst (z. B. nach einem Kontoumzug ins einladende Konto).
  if (!red || cfg.inviteRewardReviews <= 0 || red.inviter_account_id === redeemerAccountId) return;
  const t = now();
  const [lot, entry] = grantReviewStatements(env, red.inviter_account_id, 'invite_reward', cfg.inviteRewardReviews, t + cfg.inviteExpiryDays * 86400, red.id);
  // Gutgeschrieben wird nur, wenn dieser Aufruf die Markierung „belohnt“ gesetzt hat (kein doppeltes Gutschreiben bei
  // parallelen Anfragen): die Batch läuft als Transaktion, ein zweiter Aufruf scheitert an der Markierung und bricht ab.
  const mark = await env.DB.prepare('UPDATE invite_redemptions SET rewarded_at = ? WHERE id = ? AND rewarded_at IS NULL').bind(t, red.id).run();
  if (mark.meta.changes === 0) return;
  await env.DB.batch([lot, entry]);
}
