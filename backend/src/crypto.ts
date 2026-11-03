const enc = new TextEncoder();

export function b64uEncode(bytes: Uint8Array): string {
  let s = '';
  for (const b of bytes) s += String.fromCharCode(b);
  return btoa(s).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

export function b64uDecode(s: string): Uint8Array {
  if (!/^[A-Za-z0-9_-]*$/.test(s)) throw new Error('bad base64url');
  const pad = '='.repeat((4 - (s.length % 4)) % 4);
  const bin = atob(s.replace(/-/g, '+').replace(/_/g, '/') + pad);
  const out = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) out[i] = bin.charCodeAt(i);
  return out;
}

export function hex(bytes: Uint8Array): string {
  return [...bytes].map((b) => b.toString(16).padStart(2, '0')).join('');
}

export async function sha256(data: BufferSource | string): Promise<Uint8Array> {
  const buf = typeof data === 'string' ? enc.encode(data) : data;
  return new Uint8Array(await crypto.subtle.digest('SHA-256', buf));
}

export function randomId(bytes = 16): string {
  return b64uEncode(crypto.getRandomValues(new Uint8Array(bytes)));
}

async function hmacKey(secret: string, usage: 'sign' | 'verify'): Promise<CryptoKey> {
  return crypto.subtle.importKey('raw', enc.encode(secret), { name: 'HMAC', hash: 'SHA-256' }, false, [usage]);
}

export async function hmacSign(secret: string, message: string): Promise<string> {
  const sig = await crypto.subtle.sign('HMAC', await hmacKey(secret, 'sign'), enc.encode(message));
  return b64uEncode(new Uint8Array(sig));
}

export async function hmacVerify(secret: string, message: string, sigB64u: string): Promise<boolean> {
  try {
    return await crypto.subtle.verify('HMAC', await hmacKey(secret, 'verify'), b64uDecode(sigB64u), enc.encode(message));
  } catch {
    return false;
  }
}

/** DER-kodierte ECDSA-Signatur (Android Keystore) → rohes r||s (64 Byte, WebCrypto/CryptoKit). */
export function derToRaw(der: Uint8Array): Uint8Array {
  let i = 0;
  if (der[i++] !== 0x30) throw new Error('bad der');
  let len = der[i++];
  if (len & 0x80) {
    i += len & 0x7f;
  }
  const readInt = (): Uint8Array => {
    if (der[i++] !== 0x02) throw new Error('bad der');
    const l = der[i++];
    let v = der.slice(i, i + l);
    i += l;
    while (v.length > 32 && v[0] === 0) v = v.slice(1);
    if (v.length > 32) throw new Error('bad der');
    const out = new Uint8Array(32);
    out.set(v, 32 - v.length);
    return out;
  };
  const r = readInt();
  const s = readInt();
  const raw = new Uint8Array(64);
  raw.set(r, 0);
  raw.set(s, 32);
  return raw;
}

/** Akzeptiert 65-Byte-Rohpunkt (0x04||X||Y) oder SPKI-DER (Android: PublicKey.getEncoded()). */
export function rawPublicKey(bytes: Uint8Array): Uint8Array {
  if (bytes.length === 65 && bytes[0] === 0x04) return bytes;
  if (bytes.length === 91 && bytes[0] === 0x30) {
    const raw = bytes.slice(26);
    if (raw[0] === 0x04) return raw;
  }
  throw new Error('bad public key');
}

export async function importPublicKey(raw: Uint8Array): Promise<CryptoKey> {
  return crypto.subtle.importKey('raw', raw, { name: 'ECDSA', namedCurve: 'P-256' }, false, ['verify']);
}

export async function verifyEcdsa(key: CryptoKey, signature: Uint8Array, data: Uint8Array): Promise<boolean> {
  try {
    const raw = signature.length === 64 ? signature : derToRaw(signature);
    return await crypto.subtle.verify({ name: 'ECDSA', hash: 'SHA-256' }, key, raw, data);
  } catch {
    return false;
  }
}

export function canonicalString(
  method: string,
  pathAndQuery: string,
  timestamp: string,
  nonce: string,
  bodySha256Hex: string,
): string {
  return ['OVERFIT-V1', method.toUpperCase(), pathAndQuery, timestamp, nonce, bodySha256Hex].join('\n');
}
