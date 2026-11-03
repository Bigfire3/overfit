import { ApiError } from './errors';
import type { Config, Device, Env } from './types';
import { availableMicro, release, reserve, settle, now } from './ledger';
import { costMicro } from './money';
import { randomId } from './crypto';

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

export async function handleMessages(env: Env, cfg: Config, device: Device, bodyBytes: Uint8Array): Promise<Response> {
  let body: Record<string, unknown>;
  try {
    body = JSON.parse(new TextDecoder().decode(bodyBytes));
    if (!body || typeof body !== 'object' || Array.isArray(body)) throw 0;
  } catch {
    throw new ApiError(400, 'invalid_request', 'Body muss ein JSON-Objekt sein');
  }
  const model = body.model;
  if (typeof model !== 'string' || !cfg.prices[model]) {
    throw new ApiError(400, 'model_not_allowed', 'Modell nicht erlaubt', { allowed: Object.keys(cfg.prices) });
  }
  const price = cfg.prices[model];
  const maxTokens = body.max_tokens;
  if (!Number.isInteger(maxTokens) || (maxTokens as number) < 1 || (maxTokens as number) > cfg.maxTokensCap) {
    throw new ApiError(400, 'invalid_max_tokens', `max_tokens muss 1..${cfg.maxTokensCap} sein`, { max: cfg.maxTokensCap });
  }
  if (body.stream) throw new ApiError(400, 'streaming_not_supported', 'Streaming wird nicht unterstützt');
  if (!Array.isArray(body.messages) || body.messages.length === 0) {
    throw new ApiError(400, 'invalid_request', 'messages fehlt');
  }
  if (!env.ANTHROPIC_API_KEY) throw new ApiError(503, 'not_configured', 'Coach-Dienst nicht konfiguriert');

  const maxPrice = { ...price, in: Math.max(price.in, price.cacheWrite) };
  const hold = costMicro(
    { input_tokens: estimateInputTokens(body, cfg), output_tokens: maxTokens as number },
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

  let upstream: Response;
  try {
    upstream = await fetch(`${cfg.anthropicBaseUrl}/v1/messages`, {
      method: 'POST',
      headers: {
        'content-type': 'application/json',
        'x-api-key': env.ANTHROPIC_API_KEY,
        'anthropic-version': '2023-06-01',
      },
      body: bodyBytes,
      signal: AbortSignal.timeout(120_000),
    });
  } catch {
    await release(env, holdId);
    throw new ApiError(504, 'upstream_unavailable', 'KI-Anbieter nicht erreichbar, nichts abgebucht');
  }

  const text = await upstream.text();
  let parsed: Record<string, unknown> | null = null;
  try {
    parsed = JSON.parse(text);
  } catch {
    /* parsed bleibt null */
  }
  if (!upstream.ok || !parsed) {
    await release(env, holdId);
    const upstreamMessage = (parsed?.error as { message?: string } | undefined)?.message;
    const code = upstream.status >= 400 && upstream.status < 500 && upstream.status !== 429 ? 'upstream_rejected' : 'upstream_unavailable';
    throw new ApiError(code === 'upstream_rejected' ? 400 : 502, code, upstreamMessage ?? 'KI-Anbieter hat die Anfrage abgelehnt, nichts abgebucht', {
      upstreamStatus: upstream.status,
    });
  }

  const usage = (parsed.usage ?? {}) as Record<string, number>;
  const hasUsage = typeof usage.input_tokens === 'number' && typeof usage.output_tokens === 'number';
  // Ohne Nutzungsangabe wird sicherheitshalber der reservierte Höchstbetrag berechnet.
  const cost = hasUsage ? costMicro(usage, price, cfg.markupBp) : hold;
  const charged = await settle(env, device.accountId, holdId, hold, cost, {
    model,
    input: usage.input_tokens ?? 0,
    cacheRead: usage.cache_read_input_tokens ?? 0,
    cacheWrite: usage.cache_creation_input_tokens ?? 0,
    output: usage.output_tokens ?? 0,
  });
  if (charged > 0) await rewardInviter(env, cfg, device.accountId);
  const balance = await availableMicro(env, device.accountId);
  return Response.json(
    { ...parsed, overfit: { costMicro: charged, balanceMicro: balance, currency: 'EUR' } },
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
