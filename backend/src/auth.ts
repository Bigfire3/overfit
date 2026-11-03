import { ApiError } from './errors';
import type { Config, Device, Env } from './types';
import {
  b64uDecode,
  b64uEncode,
  canonicalString,
  hex,
  importPublicKey,
  rawPublicKey,
  sha256,
  verifyEcdsa,
} from './crypto';
import { now } from './ledger';
import { cleanupOauth } from './chat/oauth';

export const MAX_SKEW_SEC = 300;
const enc = new TextEncoder();

export interface SignedRequest {
  bytes: Uint8Array;
  deviceId: string;
  nonce: string;
  signature: Uint8Array;
}

/** Liest Body und Header; prüft Format und Zeitfenster, aber noch nicht die Signatur. */
export async function parseSigned(req: Request, maxBody: number): Promise<{ signed: SignedRequest; ts: string }> {
  const len = Number(req.headers.get('content-length') ?? 0);
  if (len > maxBody) throw new ApiError(413, 'body_too_large', 'Anfrage zu groß');
  const bytes = new Uint8Array(await req.arrayBuffer());
  if (bytes.length > maxBody) throw new ApiError(413, 'body_too_large', 'Anfrage zu groß');
  const deviceId = req.headers.get('x-overfit-device');
  const ts = req.headers.get('x-overfit-timestamp');
  const nonce = req.headers.get('x-overfit-nonce');
  const sig = req.headers.get('x-overfit-signature');
  if (!deviceId || !ts || !nonce || !sig) throw new ApiError(401, 'unauthorized', 'Signatur-Header fehlen');
  if (!/^\d{9,12}$/.test(ts) || Math.abs(now() - Number(ts)) > MAX_SKEW_SEC) {
    throw new ApiError(401, 'timestamp_out_of_range', 'Zeitstempel außerhalb des erlaubten Fensters', { serverTime: now() });
  }
  if (!/^[A-Za-z0-9_-]{16,64}$/.test(nonce)) throw new ApiError(401, 'unauthorized', 'Ungültige Nonce');
  let signature: Uint8Array;
  try {
    signature = b64uDecode(sig);
  } catch {
    throw new ApiError(401, 'unauthorized', 'Ungültige Signatur');
  }
  return { signed: { bytes, deviceId, nonce, signature }, ts };
}

async function checkSignature(req: Request, s: SignedRequest, ts: string, publicKey: Uint8Array): Promise<void> {
  const url = new URL(req.url);
  const text = canonicalString(req.method, url.pathname + url.search, ts, s.nonce, hex(await sha256(s.bytes)));
  const key = await importPublicKey(publicKey);
  if (!(await verifyEcdsa(key, s.signature, enc.encode(text)))) {
    throw new ApiError(401, 'bad_signature', 'Signatur ungültig');
  }
}

async function consumeNonce(env: Env, deviceId: string, nonce: string): Promise<void> {
  try {
    await env.DB.prepare('INSERT INTO nonces (device_id, nonce, ts) VALUES (?, ?, ?)').bind(deviceId, nonce, now()).run();
  } catch {
    throw new ApiError(401, 'replay', 'Nonce wurde bereits verwendet');
  }
}

export function deviceIdOf(raw: Uint8Array): Promise<string> {
  return sha256(raw).then(b64uEncode);
}

/** Normale Anfrage: Schlüssel kommt aus der Datenbank. */
export async function authenticate(env: Env, req: Request, cfg: Config): Promise<{ device: Device; body: Uint8Array }> {
  const { signed, ts } = await parseSigned(req, cfg.maxBodyBytes);
  const row = await env.DB.prepare('SELECT account_id, public_key FROM devices WHERE id = ?')
    .bind(signed.deviceId)
    .first<{ account_id: string; public_key: string }>();
  if (!row) throw new ApiError(401, 'unknown_device', 'Gerät nicht registriert');
  await checkSignature(req, signed, ts, b64uDecode(row.public_key));
  await consumeNonce(env, signed.deviceId, signed.nonce);
  return { device: { id: signed.deviceId, accountId: row.account_id }, body: signed.bytes };
}

/** Registrierung: Schlüssel steckt im Body ({publicKey}); die Anfrage beweist den Besitz des privaten Schlüssels. */
export async function authenticateRegistration(
  env: Env,
  req: Request,
  cfg: Config,
): Promise<{ deviceId: string; publicKeyRaw: Uint8Array }> {
  const { signed, ts } = await parseSigned(req, 4096);
  let raw: Uint8Array;
  try {
    const body = JSON.parse(new TextDecoder().decode(signed.bytes)) as { publicKey?: string };
    raw = rawPublicKey(b64uDecode(String(body.publicKey)));
  } catch {
    throw new ApiError(400, 'invalid_request', 'publicKey fehlt oder ist ungültig');
  }
  const id = await deviceIdOf(raw);
  if (id !== signed.deviceId) throw new ApiError(401, 'unknown_device', 'Geräte-ID passt nicht zum Schlüssel');
  await checkSignature(req, signed, ts, raw);
  await consumeNonce(env, id, signed.nonce);
  void cfg;
  return { deviceId: id, publicKeyRaw: raw };
}

export async function cleanup(env: Env): Promise<void> {
  const t = now();
  await env.DB.batch([
    env.DB.prepare('DELETE FROM nonces WHERE ts < ?').bind(t - 2 * MAX_SKEW_SEC),
    env.DB.prepare('DELETE FROM rate_limits WHERE window < ?').bind(t - 7200),
    env.DB.prepare('DELETE FROM holds WHERE created_at < ?').bind(t - 2 * 15 * 60),
    ...cleanupOauth(env),
  ]);
}
