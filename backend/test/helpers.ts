import { env } from 'cloudflare:workers';
import worker from '../src/index';
import { b64uEncode, canonicalString, hex, sha256 } from '../src/crypto';
import type { Env } from '../src/types';

export const baseEnv = (extra: Partial<Env> = {}): Env =>
  ({
    ...(env as unknown as Env),
    DEV_MODE: 'true',
    ATTESTATION_MODE: 'mock',
    PURCHASE_MODE: 'mock',
    ANTHROPIC_API_KEY: 'test-key',
    RL_DEVICE_PER_MIN: '1000',
    RL_MESSAGES_PER_MIN: '1000',
    RL_SENSITIVE_PER_HOUR: '1000',
    RL_REGISTER_PER_HOUR_IP: '1000',
    ...extra,
  }) as Env;

const ctx = { waitUntil() {}, passThroughOnException() {} } as unknown as ExecutionContext;

export function derFromRaw(raw: Uint8Array): Uint8Array {
  const int = (b: Uint8Array) => {
    let v = [...b];
    while (v.length > 1 && v[0] === 0) v.shift();
    if (v[0] & 0x80) v = [0, ...v];
    return [0x02, v.length, ...v];
  };
  const body = [...int(raw.slice(0, 32)), ...int(raw.slice(32))];
  return new Uint8Array([0x30, body.length, ...body]);
}

export class Client {
  deviceId = '';
  /** Ausführungskontext für worker.fetch; Tests können `waitUntil` mitschneiden. */
  ctx: ExecutionContext = ctx;
  constructor(
    public keys: CryptoKeyPair,
    public publicRaw: Uint8Array,
    public env: Env,
    public der = false,
  ) {}

  static async create(e: Env = baseEnv(), opts: { der?: boolean; register?: boolean } = {}): Promise<Client> {
    const keys = (await crypto.subtle.generateKey({ name: 'ECDSA', namedCurve: 'P-256' }, true, ['sign', 'verify'])) as CryptoKeyPair;
    const raw = new Uint8Array(await crypto.subtle.exportKey("raw", keys.publicKey) as ArrayBuffer);
    const c = new Client(keys, raw, e, opts.der);
    c.deviceId = b64uEncode(await sha256(raw));
    if (opts.register !== false) {
      const r = await c.call('POST', '/v1/devices', { publicKey: b64uEncode(raw) });
      if (r.status >= 300) throw new Error('register failed ' + JSON.stringify(r.body));
    }
    return c;
  }

  async sign(method: string, path: string, body: string | Uint8Array, over: { ts?: number; nonce?: string } = {}) {
    const ts = String(over.ts ?? Math.floor(Date.now() / 1000));
    const nonce = over.nonce ?? b64uEncode(crypto.getRandomValues(new Uint8Array(16)));
    const text = canonicalString(method, path, ts, nonce, hex(await sha256(typeof body === 'string' ? new TextEncoder().encode(body) : body)));
    let sig: Uint8Array = new Uint8Array(await crypto.subtle.sign({ name: 'ECDSA', hash: 'SHA-256' }, this.keys.privateKey, new TextEncoder().encode(text)));
    if (this.der) sig = derFromRaw(sig);
    return {
      'x-overfit-device': this.deviceId,
      'x-overfit-timestamp': ts,
      'x-overfit-nonce': nonce,
      'x-overfit-signature': b64uEncode(sig),
    };
  }

  async call(method: string, path: string, body?: unknown, over: { ts?: number; nonce?: string } = {}) {
    const text = body === undefined ? '' : JSON.stringify(body);
    const headers = { ...(await this.sign(method, path, text, over)), 'content-type': 'application/json' };
    const res = await worker.fetch(new Request('https://x.test' + path, { method, headers, body: text || undefined }), this.env, this.ctx);
    return { status: res.status, body: (await res.json()) as any, headers: res.headers };
  }

  /** Signierte Anfrage mit Binär-Body (z. B. Foto-Upload). */
  async callRaw(method: string, path: string, bytes: Uint8Array, contentType = 'image/jpeg'): Promise<RawCallResult> {
    const headers = { ...(await this.sign(method, path, bytes)), 'content-type': contentType };
    const res = await worker.fetch(new Request('https://x.test' + path, { method, headers, body: bytes }), this.env, ctx);
    return { status: res.status, body: (await res.json()) as any };
  }
}

export interface RawCallResult {
  status: number;
  body: any;
}

export async function rawFetch(path: string, e: Env, init?: RequestInit) {
  const res = await worker.fetch(new Request('https://x.test' + path, init), e, ctx);
  return { status: res.status, body: (await res.json()) as any };
}

/** Konto mit Guthaben (Kauf über den Mock-Prüfer). */
export async function fund(c: Client, orderId = crypto.randomUUID()) {
  const r = await c.call('POST', '/v1/purchases/verify', { platform: 'google', productId: 'overfit.fuel.10', purchaseToken: `mock:${orderId}` });
  if (r.status !== 200) throw new Error('fund failed ' + JSON.stringify(r.body));
  return r.body;
}
