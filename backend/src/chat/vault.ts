import { ApiError } from '../errors';
import type { Config, Env } from '../types';
import { b64uDecode } from '../crypto';

// Verschlüsselung at rest für KI-Chat-Daten: AES-256-GCM, Schlüssel per HKDF aus SYNC_ENCRYPTION_KEY und der Konto-ID.
// Format: 0x01 ‖ IV (12 Byte) ‖ Ciphertext+Tag. AAD bindet den Inhalt an Konto/Art/ID (kein Vertauschen von Zeilen).
// Hinweis: Das ist keine Ende-zu-Ende-Verschlüsselung – der Worker muss die Daten lesen, um sie dem Chat zu liefern.

const enc = new TextEncoder();
const dec = new TextDecoder();
const DEV_KEY = 'overfit-dev-only-sync-key-do-not-use-in-production';
const cache = new Map<string, CryptoKey>();

function masterKey(env: Env, cfg: Config): Uint8Array {
  const k = env.SYNC_ENCRYPTION_KEY;
  if (k) {
    try {
      const raw = b64uDecode(k);
      if (raw.length >= 32) return raw;
    } catch {
      /* fällt durch */
    }
    throw new ApiError(503, 'not_configured', 'SYNC_ENCRYPTION_KEY ungültig (≥ 32 Byte, Base64url)');
  }
  if (cfg.devMode) return enc.encode(DEV_KEY);
  throw new ApiError(503, 'not_configured', 'SYNC_ENCRYPTION_KEY fehlt');
}

async function accountKey(env: Env, cfg: Config, accountId: string): Promise<CryptoKey> {
  const master = masterKey(env, cfg);
  const cacheKey = `${master.length}:${master[0]}:${master[master.length - 1]}:${accountId}`;
  const hit = cache.get(cacheKey);
  if (hit) return hit;
  const ikm = await crypto.subtle.importKey('raw', master, 'HKDF', false, ['deriveKey']);
  const key = await crypto.subtle.deriveKey(
    { name: 'HKDF', hash: 'SHA-256', salt: enc.encode('overfit-chat-v1'), info: enc.encode(accountId) },
    ikm,
    { name: 'AES-GCM', length: 256 },
    false,
    ['encrypt', 'decrypt'],
  );
  if (cache.size > 500) cache.clear();
  cache.set(cacheKey, key);
  return key;
}

export async function seal(env: Env, cfg: Config, accountId: string, aad: string, plain: Uint8Array): Promise<Uint8Array> {
  const key = await accountKey(env, cfg, accountId);
  const iv = crypto.getRandomValues(new Uint8Array(12));
  const ct = new Uint8Array(
    await crypto.subtle.encrypt({ name: 'AES-GCM', iv, additionalData: enc.encode(`${accountId}|${aad}`) }, key, plain),
  );
  const out = new Uint8Array(1 + 12 + ct.length);
  out[0] = 1;
  out.set(iv, 1);
  out.set(ct, 13);
  return out;
}

export async function open(env: Env, cfg: Config, accountId: string, aad: string, blob: Uint8Array): Promise<Uint8Array> {
  if (blob[0] !== 1 || blob.length < 13 + 16) throw new Error('bad blob');
  const key = await accountKey(env, cfg, accountId);
  return new Uint8Array(
    await crypto.subtle.decrypt(
      { name: 'AES-GCM', iv: blob.slice(1, 13), additionalData: enc.encode(`${accountId}|${aad}`) },
      key,
      blob.slice(13),
    ),
  );
}

export async function sealJson(env: Env, cfg: Config, accountId: string, aad: string, v: unknown): Promise<Uint8Array> {
  return seal(env, cfg, accountId, aad, enc.encode(JSON.stringify(v)));
}

export async function openJson<T = unknown>(env: Env, cfg: Config, accountId: string, aad: string, blob: ArrayBuffer | Uint8Array): Promise<T> {
  const bytes = blob instanceof Uint8Array ? blob : new Uint8Array(blob);
  return JSON.parse(dec.decode(await open(env, cfg, accountId, aad, bytes))) as T;
}
