import { ApiError } from './errors';
import type { Config, Device, Env } from './types';
import { b64uDecode, b64uEncode, hmacSign, hmacVerify, randomId } from './crypto';
import { availableMicro, giftReviews, grantReviewStatements, monthKey, now } from './ledger';
import { getAttestationVerifier, type Platform } from './verifiers';

const enc = new TextEncoder();
const dec = new TextDecoder();

function signingKey(env: Env, cfg: Config): string {
  if (env.INVITE_SIGNING_KEY) return env.INVITE_SIGNING_KEY;
  if (cfg.devMode) return 'dev-only-invite-signing-key';
  throw new ApiError(503, 'not_configured', 'Einladungen sind nicht konfiguriert');
}

async function sealed(prefix: string, payload: object, key: string): Promise<string> {
  const body = b64uEncode(enc.encode(JSON.stringify(payload)));
  return `${body}.${await hmacSign(key, prefix + body)}`;
}

async function unseal<T>(prefix: string, token: string, key: string): Promise<T | null> {
  const [body, sig, ...rest] = token.split('.');
  if (!body || !sig || rest.length) return null;
  if (!(await hmacVerify(key, prefix + body, sig))) return null;
  try {
    return JSON.parse(dec.decode(b64uDecode(body))) as T;
  } catch {
    return null;
  }
}

/** Einladender: kurzlebiger Einmal-Token fürs QR-Code. */
export async function createInviteToken(env: Env, cfg: Config, device: Device) {
  const key = signingKey(env, cfg);
  const exp = now() + cfg.inviteTokenTtlSec;
  const token = await sealed('invite:', { i: device.accountId, e: exp, n: randomId(12) }, key);
  return { token, expiresAt: exp, ttlSec: cfg.inviteTokenTtlSec };
}

/** Herausforderung für die Attestierung (Play-Integrity-Nonce / App-Attest-clientDataHash). */
export async function createChallenge(env: Env, cfg: Config, device: Device) {
  const key = signingKey(env, cfg);
  const exp = now() + 300;
  const challenge = await sealed('challenge:', { d: device.id, e: exp, n: randomId(12) }, key);
  return { challenge, expiresAt: exp };
}

const CONSTRAINT_ERRORS: [string, ApiError][] = [
  ['invite_tokens_used', new ApiError(409, 'invite_token_used', 'Diese Einladung wurde bereits eingelöst')],
  ['redeemer_account_id', new ApiError(409, 'invite_already_redeemed', 'Du hast schon eine Einladung eingelöst')],
  ['redeemer_device_id', new ApiError(409, 'invite_already_redeemed', 'Du hast schon eine Einladung eingelöst')],
  ['attestation_key', new ApiError(409, 'invite_device_used', 'Auf diesem Gerät wurde schon eine Einladung eingelöst')],
];

export async function redeemInvite(env: Env, cfg: Config, device: Device, body: Record<string, unknown>) {
  const key = signingKey(env, cfg);
  const token = body.token;
  const att = body.attestation as { platform?: string; token?: string; challenge?: string } | undefined;
  if (typeof token !== 'string' || !att || typeof att.token !== 'string' || typeof att.challenge !== 'string') {
    throw new ApiError(400, 'invalid_request', 'token und attestation {platform, token, challenge} erforderlich');
  }
  if (att.platform !== 'google' && att.platform !== 'apple') {
    throw new ApiError(400, 'invalid_request', 'attestation.platform: google | apple');
  }
  const t = now();
  const tok = await unseal<{ i: string; e: number; n: string }>('invite:', token, key);
  if (!tok || typeof tok.i !== 'string') throw new ApiError(400, 'invite_token_invalid', 'Einladungscode ungültig');
  if (tok.e < t) throw new ApiError(410, 'invite_token_expired', 'Einladungscode abgelaufen, bitte neu scannen');
  if (tok.i === device.accountId) throw new ApiError(400, 'invite_self', 'Du kannst dich nicht selbst einladen');

  const ch = await unseal<{ d: string; e: number; n: string }>('challenge:', att.challenge, key);
  if (!ch || ch.d !== device.id || ch.e < t) throw new ApiError(400, 'challenge_invalid', 'Herausforderung ungültig oder abgelaufen');

  const verifier = getAttestationVerifier(env, cfg, att.platform as Platform);
  if (!verifier) throw new ApiError(503, 'attestation_unavailable', 'Einladungen sind noch nicht freigeschaltet');

  const inviter = await env.DB.prepare('SELECT display_name FROM accounts WHERE id = ?')
    .bind(tok.i)
    .first<{ display_name: string | null }>();
  if (!inviter) throw new ApiError(400, 'invite_token_invalid', 'Einladungscode ungültig');

  const month = monthKey(t);
  const count = await env.DB.prepare('SELECT COUNT(*) AS c FROM invite_redemptions WHERE inviter_account_id = ? AND month = ?')
    .bind(tok.i, month)
    .first<{ c: number }>();
  if ((count?.c ?? 0) >= cfg.inviteMonthlyLimitPerInviter) {
    throw new ApiError(429, 'invite_inviter_limit', 'Diese Person hat diesen Monat schon alle Einladungen vergeben');
  }
  const total = await env.DB.prepare('SELECT COUNT(*) AS c FROM invite_redemptions WHERE month = ?').bind(month).first<{ c: number }>();
  // Globales Monatsbudget in Auswertungen: jede Einlösung zählt Startbonus + spätere Belohnung.
  if (((total?.c ?? 0) + 1) * (cfg.inviteStarterReviews + cfg.inviteRewardReviews) > cfg.inviteGlobalMonthlyBudgetReviews) {
    throw new ApiError(429, 'invite_budget_exhausted', 'Einladungsboni sind diesen Monat aufgebraucht, bitte später wieder versuchen');
  }
  const already = await env.DB.prepare('SELECT 1 AS x FROM invite_redemptions WHERE redeemer_account_id = ? OR redeemer_device_id = ?')
    .bind(device.accountId, device.id)
    .first();
  if (already) throw new ApiError(409, 'invite_already_redeemed', 'Du hast schon eine Einladung eingelöst');

  // Challenge einmalig verbrauchen, erst dann die (teure) Attestierung prüfen.
  try {
    await env.DB.prepare('INSERT INTO nonces (device_id, nonce, ts) VALUES (?, ?, ?)').bind('challenge', ch.n, t).run();
  } catch {
    throw new ApiError(400, 'challenge_invalid', 'Herausforderung wurde schon verwendet');
  }
  const result = await verifier.verify({ token: att.token, challenge: att.challenge });
  if (!result.ok) throw new ApiError(403, 'attestation_failed', 'Geräteprüfung fehlgeschlagen', { reason: result.reason });

  const redemptionId = randomId(12);
  try {
    await env.DB.batch([
      env.DB.prepare('INSERT INTO invite_tokens_used (token_id, inviter_account_id, used_at) VALUES (?, ?, ?)').bind(tok.n, tok.i, t),
      env.DB.prepare(
        `INSERT INTO invite_redemptions (id, inviter_account_id, redeemer_account_id, redeemer_device_id, attestation_key, created_at, month)
         VALUES (?, ?, ?, ?, ?, ?, ?)`,
      ).bind(redemptionId, tok.i, device.accountId, device.id, `${att.platform}:${result.deviceKey}`, t, month),
      ...(cfg.inviteStarterReviews > 0
        ? grantReviewStatements(env, device.accountId, 'invite_bonus', cfg.inviteStarterReviews, t + cfg.inviteExpiryDays * 86400, redemptionId)
        : []),
    ]);
  } catch (e) {
    const msg = String(e);
    for (const [needle, err] of CONSTRAINT_ERRORS) if (msg.includes(needle)) throw err;
    throw e;
  }
  return {
    grantedReviews: cfg.inviteStarterReviews,
    expiresAt: t + cfg.inviteExpiryDays * 86400,
    inviter: { displayName: inviter.display_name },
    balanceMicro: await availableMicro(env, device.accountId),
    giftReviews: await giftReviews(env, device.accountId),
  };
}
