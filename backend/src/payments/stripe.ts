import { ApiError } from '../errors';
import { hex } from '../crypto';
import { now } from '../ledger';
import type { CheckoutRequest, CheckoutSession, ClientSettings, PaymentEnvironment, PaymentEvent, PaymentProvider } from './provider';

/** Toleranz für den Zeitstempel der Webhook-Signatur (Stripes Standard; jeder Zustellversuch wird neu signiert). */
export const STRIPE_SIG_TOLERANCE_SEC = 300;
export const STRIPE_API = 'https://api.stripe.com';

/** Pflichthinweis für Kleinunternehmer; steht auf dem Checkout, der Kaufseite und (optional) der Rechnung. */
export const SMALL_BUSINESS_NOTE = {
  de: 'Gemäß § 19 UStG wird keine Umsatzsteuer berechnet.',
  en: 'No VAT is charged under the German small business scheme (§ 19 UStG).',
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

/** `Stripe-Signature: t=…,v1=…[,v1=…]`: HMAC-SHA256 über `t.Rohbody`, hex. Erzeugt den Header (für Tests). */
export async function stripeSignature(secret: string, rawBody: string, ts = now()): Promise<string> {
  return `t=${ts},v1=${await hmacHex(secret, `${ts}.${rawBody}`)}`;
}

/** Prüft den Header wie Stripes SDK: Zeitstempel im Toleranzfenster, mindestens eine passende `v1`-Signatur. */
export async function verifyStripeSignature(secret: string, header: string | null, rawBody: string, t = now()): Promise<boolean> {
  if (!header) return false;
  let ts = '';
  const v1: string[] = [];
  for (const part of header.split(',')) {
    const i = part.indexOf('=');
    if (i < 0) continue;
    const k = part.slice(0, i).trim();
    const v = part.slice(i + 1).trim();
    if (k === 't') ts = v;
    else if (k === 'v1') v1.push(v.toLowerCase());
  }
  if (!/^\d{9,12}$/.test(ts) || v1.length === 0) return false;
  if (Math.abs(t - Number(ts)) > STRIPE_SIG_TOLERANCE_SEC) return false;
  const expected = await hmacHex(secret, `${ts}.${rawBody}`);
  return v1.some((s) => safeEqual(s, expected));
}

/** Umgebung aus dem Schlüssel: `sk_test_`/`rk_test_` = Testmodus, `sk_live_`/`rk_live_` = echt; sonst unbrauchbar. */
export function stripeKeyEnvironment(key: string): PaymentEnvironment | null {
  if (/^(sk|rk)_test_/.test(key)) return 'sandbox';
  if (/^(sk|rk)_live_/.test(key)) return 'live';
  return null;
}

const cents = (micro: number) => String(Math.round(micro / 10_000));

function productText(req: CheckoutRequest): { name: string; description: string } {
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

const str = (v: unknown): string | null => (typeof v === 'string' && v ? v : null);
const idOf = (v: unknown): string | null => str(v) ?? (v && typeof v === 'object' ? str((v as { id?: unknown }).id) : null);

/**
 * Stripe Checkout (gehostete Seite): Preis ohne Katalog (`price_data`), ohne Stripe Tax (Kleinunternehmer, Endpreis
 * ohne USt). Zahlarten kommen aus dem Dashboard („dynamische Zahlungsmethoden“). Ohne Node-SDK: Formular-POST per fetch.
 */
export class StripeProvider implements PaymentProvider {
  readonly name = 'stripe';
  readonly client: ClientSettings;

  constructor(
    private readonly secretKey: string,
    private readonly webhookSecret: string,
    readonly environment: PaymentEnvironment,
    private readonly opts: { apiBase?: string; invoices?: boolean } = {},
  ) {
    this.client = { provider: this.name, environment };
  }

  /** Formularfelder für `POST /v1/checkout/sessions` (exportiert für Tests). */
  checkoutForm(req: CheckoutRequest): URLSearchParams {
    const { name, description } = productText(req);
    const note = SMALL_BUSINESS_NOTE[req.locale];
    const f = new URLSearchParams({
      mode: 'payment',
      'line_items[0][quantity]': '1',
      'line_items[0][price_data][currency]': 'eur',
      'line_items[0][price_data][unit_amount]': cents(req.priceMicro),
      'line_items[0][price_data][product_data][name]': name,
      'line_items[0][price_data][product_data][description]': description,
      success_url: req.successUrl,
      cancel_url: req.cancelUrl,
      client_reference_id: req.orderId,
      'metadata[overfit_order]': req.orderId,
      'metadata[kind]': req.kind,
      'metadata[package]': req.packageId,
      // Auch auf Zahlung/Charge, damit Erstattungen die Bestellung finden, falls sie vor der Zahlungsbestätigung eintreffen.
      'payment_intent_data[metadata][overfit_order]': req.orderId,
      'payment_intent_data[description]': name,
      locale: req.locale,
      // Land fürs Lieferschwellen-Reporting; Stripe fragt bei Karten ohnehin Land (und ggf. PLZ) ab.
      billing_address_collection: 'auto',
      'custom_text[submit][message]': note,
    });
    if (this.opts.invoices) {
      f.set('invoice_creation[enabled]', 'true');
      f.set('invoice_creation[invoice_data][footer]', note);
      f.set('invoice_creation[invoice_data][description]', description);
    }
    return f;
  }

  async createCheckout(req: CheckoutRequest): Promise<CheckoutSession> {
    let res: Response;
    try {
      res = await fetch(`${this.opts.apiBase ?? STRIPE_API}/v1/checkout/sessions`, {
        method: 'POST',
        headers: {
          authorization: `Bearer ${this.secretKey}`,
          'content-type': 'application/x-www-form-urlencoded',
          // Gleiche Bestellung = gleiche Session, auch wenn der Aufruf wiederholt wird.
          'idempotency-key': `overfit-${req.orderId}`,
        },
        body: this.checkoutForm(req).toString(),
      });
    } catch {
      throw new ApiError(502, 'payment_unavailable', 'Zahlungsanbieter nicht erreichbar');
    }
    const json = (await res.json().catch(() => null)) as { id?: string; url?: string | null; error?: unknown } | null;
    if (!res.ok || typeof json?.id !== 'string') {
      console.error('stripe create checkout session', res.status, JSON.stringify(json)?.slice(0, 500));
      throw new ApiError(502, 'payment_unavailable', 'Checkout konnte nicht erstellt werden');
    }
    return { providerTxnId: json.id, checkoutUrl: json.url ?? null };
  }

  async parseWebhook(headers: Headers, rawBody: string): Promise<PaymentEvent> {
    if (!(await verifyStripeSignature(this.webhookSecret, headers.get('stripe-signature'), rawBody))) {
      throw new ApiError(401, 'invalid_signature', 'Webhook-Signatur ungültig');
    }
    let evt: { type?: string; livemode?: boolean; data?: { object?: Record<string, any> } };
    try {
      evt = JSON.parse(rawBody);
    } catch {
      throw new ApiError(400, 'invalid_request', 'Webhook-Body ist kein JSON');
    }
    // Testereignisse an einen Live-Worker (oder umgekehrt) nie buchen.
    if (typeof evt.livemode === 'boolean' && evt.livemode !== (this.environment === 'live')) {
      return { type: 'ignored', reason: 'livemode_mismatch' };
    }
    const o = evt.data?.object ?? {};
    switch (evt.type) {
      case 'checkout.session.completed':
      case 'checkout.session.async_payment_succeeded': {
        // Verzögerte Zahlarten (z. B. SEPA-Lastschrift): erst `async_payment_succeeded` bestätigt das Geld.
        if (o.payment_status !== 'paid') return { type: 'ignored', reason: `payment_status ${o.payment_status}` };
        const total = Number(o.amount_total);
        if (typeof o.id !== 'string' || !Number.isFinite(total)) throw new ApiError(400, 'invalid_request', 'Checkout unvollständig');
        const country = str(o.customer_details?.address?.country);
        return {
          type: 'paid',
          providerTxnId: o.id,
          paymentId: idOf(o.payment_intent),
          orderId: str(o.metadata?.overfit_order) ?? str(o.client_reference_id),
          currency: String(o.currency ?? '').toUpperCase(),
          totalMinor: total,
          country: country && /^[A-Za-z]{2}$/.test(country) ? country.toUpperCase() : null,
        };
      }
      case 'charge.refunded': {
        // Stripe meldet jede (Teil-)Erstattung mit der Summe aller bisherigen Erstattungen der Charge.
        const amount = Number(o.amount_refunded);
        const pi = idOf(o.payment_intent);
        if (typeof o.id !== 'string' || !pi || !Number.isFinite(amount)) throw new ApiError(400, 'invalid_request', 'Erstattung unvollständig');
        return {
          type: 'reversal',
          action: 'refund',
          adjustmentId: `refund:${o.id}:${amount}`,
          paymentId: pi,
          orderId: str(o.metadata?.overfit_order),
          currency: String(o.currency ?? '').toUpperCase(),
          amountMinor: amount,
          cumulative: true,
        };
      }
      case 'charge.dispute.created': {
        const amount = Number(o.amount);
        const pi = idOf(o.payment_intent);
        if (typeof o.id !== 'string' || !pi || !Number.isFinite(amount)) throw new ApiError(400, 'invalid_request', 'Chargeback unvollständig');
        return {
          type: 'reversal',
          action: 'chargeback',
          adjustmentId: `dispute:${o.id}`,
          paymentId: pi,
          orderId: null,
          currency: String(o.currency ?? '').toUpperCase(),
          amountMinor: amount,
          cumulative: false,
        };
      }
      default:
        return { type: 'ignored', reason: String(evt.type) };
    }
  }
}
