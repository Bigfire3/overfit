import { ApiError } from '../errors';
import { hex } from '../crypto';
import { now } from '../ledger';
import type { CheckoutRequest, CheckoutSession, ClientSettings, PaymentEnvironment, PaymentEvent, PaymentProvider } from './provider';

/** Toleranz für den Zeitstempel der Webhook-Signatur (Paddle signiert jeden Zustellversuch neu). */
export const PADDLE_SIG_TOLERANCE_SEC = 300;

export const PADDLE_API = {
  sandbox: 'https://sandbox-api.paddle.com',
  live: 'https://api.paddle.com',
} as const;

const enc = new TextEncoder();

async function hmacHex(secret: string, message: string): Promise<string> {
  const key = await crypto.subtle.importKey('raw', enc.encode(secret), { name: 'HMAC', hash: 'SHA-256' }, false, ['sign']);
  return hex(new Uint8Array(await crypto.subtle.sign('HMAC', key, enc.encode(message))));
}

function safeEqual(a: string, b: string): boolean {
  if (a.length !== b.length) return false;
  let d = 0;
  for (let i = 0; i < a.length; i++) d |= a.charCodeAt(i) ^ b.charCodeAt(i);
  return d === 0;
}

/** `Paddle-Signature: ts=…;h1=…[;h1=…]` – erzeugt den Header (für Tests) bzw. prüft ihn. */
export async function paddleSignature(secret: string, rawBody: string, ts = now()): Promise<string> {
  return `ts=${ts};h1=${await hmacHex(secret, `${ts}:${rawBody}`)}`;
}

export async function verifyPaddleSignature(secret: string, header: string | null, rawBody: string, t = now()): Promise<boolean> {
  if (!header) return false;
  let ts = '';
  const h1: string[] = [];
  for (const part of header.split(';')) {
    const i = part.indexOf('=');
    if (i < 0) continue;
    const k = part.slice(0, i).trim();
    const v = part.slice(i + 1).trim();
    if (k === 'ts') ts = v;
    else if (k === 'h1') h1.push(v.toLowerCase());
  }
  if (!/^\d{9,12}$/.test(ts) || h1.length === 0) return false;
  if (Math.abs(t - Number(ts)) > PADDLE_SIG_TOLERANCE_SEC) return false;
  const expected = await hmacHex(secret, `${ts}:${rawBody}`);
  return h1.some((s) => safeEqual(s, expected));
}

const euroCents = (micro: number) => String(Math.round(micro / 10_000));

function productName(req: CheckoutRequest): { name: string; description: string } {
  const euro = (req.priceMicro / 1_000_000).toFixed(0);
  if (req.locale === 'en') {
    return req.kind === 'code'
      ? { name: `overfit credit code €${euro}`, description: `Code for €${euro} of coach credit, redeemable in the overfit app` }
      : { name: `overfit credit €${euro}`, description: `€${euro} of coach credit for your overfit account` };
  }
  return req.kind === 'code'
    ? { name: `overfit Guthaben-Code ${euro} €`, description: `Code über ${euro} € Coach-Guthaben, einlösbar in der overfit-App` }
    : { name: `overfit Guthaben ${euro} €`, description: `${euro} € Coach-Guthaben für dein overfit-Konto` };
}

/** Paddle Billing: Transaktion per API (Preis ohne Katalog, inkl. USt), Checkout über Paddle.js auf der Kaufseite. */
export class PaddleProvider implements PaymentProvider {
  readonly name = 'paddle';
  readonly client: ClientSettings;

  constructor(
    private readonly apiKey: string,
    private readonly webhookSecret: string,
    clientToken: string,
    readonly environment: PaymentEnvironment,
    private readonly apiBase: string = PADDLE_API[environment],
  ) {
    this.client = { provider: this.name, environment, clientToken };
  }

  async createCheckout(req: CheckoutRequest): Promise<CheckoutSession> {
    const { name, description } = productName(req);
    const body = {
      items: [
        {
          quantity: 1,
          price: {
            name,
            description,
            unit_price: { amount: euroCents(req.priceMicro), currency_code: 'EUR' },
            // Endpreis: die USt ist enthalten, Paddle rechnet sie heraus und führt sie ab (Merchant of Record).
            tax_mode: 'internal',
            product: { name, tax_category: 'standard' },
          },
        },
      ],
      currency_code: 'EUR',
      collection_mode: 'automatic',
      custom_data: { overfit_order: req.orderId, kind: req.kind, package: req.packageId },
    };
    let res: Response;
    try {
      res = await fetch(`${this.apiBase}/transactions`, {
        method: 'POST',
        headers: { authorization: `Bearer ${this.apiKey}`, 'content-type': 'application/json' },
        body: JSON.stringify(body),
      });
    } catch {
      throw new ApiError(502, 'payment_unavailable', 'Zahlungsanbieter nicht erreichbar');
    }
    const json = (await res.json().catch(() => null)) as { data?: { id?: string; checkout?: { url?: string | null } } } | null;
    const id = json?.data?.id;
    if (!res.ok || typeof id !== 'string') {
      console.error('paddle create transaction', res.status, JSON.stringify(json)?.slice(0, 500));
      throw new ApiError(502, 'payment_unavailable', 'Checkout konnte nicht erstellt werden');
    }
    return { providerTxnId: id, checkoutUrl: json?.data?.checkout?.url ?? null };
  }

  async parseWebhook(headers: Headers, rawBody: string): Promise<PaymentEvent> {
    if (!(await verifyPaddleSignature(this.webhookSecret, headers.get('paddle-signature'), rawBody))) {
      throw new ApiError(401, 'invalid_signature', 'Webhook-Signatur ungültig');
    }
    let evt: { event_type?: string; data?: Record<string, any> };
    try {
      evt = JSON.parse(rawBody);
    } catch {
      throw new ApiError(400, 'invalid_request', 'Webhook-Body ist kein JSON');
    }
    const d = evt.data ?? {};
    switch (evt.event_type) {
      case 'transaction.completed': {
        const total = Number(d.details?.totals?.grand_total ?? d.details?.totals?.total);
        if (typeof d.id !== 'string' || !Number.isFinite(total)) throw new ApiError(400, 'invalid_request', 'Transaktion unvollständig');
        return {
          type: 'paid',
          providerTxnId: d.id,
          orderId: typeof d.custom_data?.overfit_order === 'string' ? d.custom_data.overfit_order : null,
          currency: String(d.currency_code ?? ''),
          totalMinor: total,
        };
      }
      case 'adjustment.created':
      case 'adjustment.updated': {
        // Erstattungen brauchen bei Paddle eine Freigabe (pending_approval → approved); erst dann zählt sie.
        if (d.status !== 'approved') return { type: 'ignored', reason: `adjustment ${d.status}` };
        if (d.action !== 'refund' && d.action !== 'chargeback') return { type: 'ignored', reason: `adjustment ${d.action}` };
        const amount = Number(d.totals?.total);
        if (typeof d.id !== 'string' || typeof d.transaction_id !== 'string' || !Number.isFinite(amount)) {
          throw new ApiError(400, 'invalid_request', 'Erstattung unvollständig');
        }
        return {
          type: 'reversal',
          action: d.action,
          adjustmentId: d.id,
          providerTxnId: d.transaction_id,
          currency: String(d.totals?.currency_code ?? d.currency_code ?? ''),
          amountMinor: amount,
        };
      }
      default:
        return { type: 'ignored', reason: String(evt.event_type) };
    }
  }
}
