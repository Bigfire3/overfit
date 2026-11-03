import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { env as workerEnv } from 'cloudflare:workers';
import { Client, baseEnv, rawFetch } from './helpers';
import { stripeSignature, verifyStripeSignature } from '../src/payments/stripe';
import { formatCode, newCreditCode, normalizeCreditCode } from '../src/web';
import type { Env } from '../src/types';

const SECRET = 'whsec_test_secret_0123456789';
const STRIPE = { STRIPE_SECRET_KEY: 'sk_test_51abc', STRIPE_WEBHOOK_SECRET: SECRET };
// Die Tests rechnen mit 5/10/20 €; die Standardpakete (5/10/20/50 €) prüft „/v1/config“ unten.
const TEST_PACKAGES = JSON.stringify({ 'web.5': { priceMicro: 5_000_000 }, 'web.10': { priceMicro: 10_000_000 }, 'web.20': { priceMicro: 20_000_000 } });
const env = (extra: Partial<Env> = {}) => baseEnv({ ...STRIPE, WEB_PACKAGES: TEST_PACKAGES, ...extra });
const DB = (workerEnv as unknown as Env).DB;

let stripe: ReturnType<typeof vi.fn<(url: string, init: RequestInit) => Promise<Response>>>;
let sessionCounter = 0;
beforeEach(() => {
  sessionCounter = 0;
  stripe = vi.fn(async (_url: string, _init: RequestInit) => {
    const id = `cs_test_${++sessionCounter}`;
    return Response.json({ id, object: 'checkout.session', url: `https://checkout.stripe.com/c/pay/${id}` });
  });
  const real = globalThis.fetch;
  vi.stubGlobal('fetch', (input: any, init?: any) => {
    const url = String(input instanceof Request ? input.url : input);
    if (url.startsWith('https://api.stripe.com')) return stripe(url, init);
    return real(input, init);
  });
});
afterEach(() => vi.unstubAllGlobals());

const post = (e: Env, path: string, body: unknown, headers: Record<string, string> = {}) =>
  rawFetch(path, e, { method: 'POST', headers: { 'content-type': 'application/json', ...headers }, body: JSON.stringify(body) });

async function webhook(e: Env, payload: unknown, opts: { secret?: string; ts?: number; header?: string | null } = {}) {
  const raw = JSON.stringify(payload);
  const sig = opts.header === undefined ? await stripeSignature(opts.secret ?? SECRET, raw, opts.ts) : opts.header;
  return rawFetch('/v1/webhooks/payment', e, {
    method: 'POST',
    headers: { 'content-type': 'application/json', ...(sig ? { 'stripe-signature': sig } : {}) },
    body: raw,
  });
}

/** PaymentIntent zur Checkout Session (im Test fest abgeleitet). */
const pi = (cs: string) => cs.replace(/^cs_/, 'pi_');
const event = (type: string, object: Record<string, unknown>, livemode = false) => ({ id: `evt_${Math.random()}`, object: 'event', type, livemode, data: { object } });
const completed = (
  cs: string, order: string | null, amount = 500,
  o: { currency?: string; status?: string; type?: string; country?: string | null; livemode?: boolean } = {},
) =>
  event(o.type ?? 'checkout.session.completed', {
    id: cs, object: 'checkout.session', payment_status: o.status ?? 'paid', payment_intent: pi(cs), amount_total: amount,
    currency: o.currency ?? 'eur', client_reference_id: order, metadata: order ? { overfit_order: order } : {},
    customer_details: { address: { country: o.country === undefined ? 'DE' : o.country } },
  }, o.livemode);
/** Stripe meldet bei `charge.refunded` die Summe aller bisherigen Erstattungen. */
const refunded = (cs: string, amountRefunded: number, order: string | null = null) =>
  event('charge.refunded', { id: `ch_${cs}`, object: 'charge', payment_intent: pi(cs), amount_refunded: amountRefunded, currency: 'eur', metadata: order ? { overfit_order: order } : {} });
const dispute = (id: string, cs: string, amount: number) =>
  event('charge.dispute.created', { id, object: 'dispute', charge: `ch_${cs}`, payment_intent: pi(cs), amount, currency: 'eur' });

const tokenOf = (url: string) => /#t=([A-Za-z0-9_-]+)/.exec(url)![1];
const balance = async (c: Client) => (await c.call('GET', '/v1/account')).body.balanceMicro as number;

/** App holt Kauflink → Kaufseite startet Checkout für ein Paket. */
async function startCredit(c: Client, e: Env, packageId = 'web.5') {
  const link = await c.call('POST', '/v1/purchase/web-link', { locale: 'de' });
  expect(link.status).toBe(200);
  const token = tokenOf(link.body.url);
  const co = await post(e, '/v1/web/checkout', { token, packageId, kind: 'credit', locale: 'de' });
  expect(co.status).toBe(200);
  return { token, ...co.body } as { token: string; orderId: string; transactionId: string };
}

describe('Kauflink aus der App', () => {
  it('liefert URL zur Kaufseite mit Token im Fragment, 15 Minuten gültig', async () => {
    const c = await Client.create(env());
    const r = await c.call('POST', '/v1/purchase/web-link', { locale: 'en' });
    expect(r.status).toBe(200);
    expect(r.body.url).toMatch(/^https:\/\/bigfire3\.github\.io\/overfit\/guthaben\/#t=[A-Za-z0-9_-]{32}&lang=en$/);
    expect(r.body.ttlSec).toBe(900);
    expect(r.body.expiresAt - Math.floor(Date.now() / 1000)).toBeGreaterThan(890);
    // Nur der Hash liegt in der Datenbank
    const row = await DB.prepare('SELECT token_hash FROM web_links').first<{ token_hash: string }>();
    expect(row!.token_hash).not.toContain(tokenOf(r.body.url));
    // Alter Pfad aus docs/backend-api.md funktioniert ebenfalls
    expect((await c.call('POST', '/v1/purchases/web-link', {})).status).toBe(200);
  });

  it('ohne Signatur abgelehnt, ohne Stripe-Secrets 503', async () => {
    expect((await post(env(), '/v1/purchase/web-link', {})).status).toBe(401);
    const c = await Client.create(baseEnv());
    const r = await c.call('POST', '/v1/purchase/web-link', {});
    expect(r.status).toBe(503);
    expect(r.body.error.code).toBe('web_purchase_unavailable');
  });

  it('Kaufseite sieht den Zustand des Links; abgelaufen und unbekannt werden erkannt', async () => {
    const e = env();
    const c = await Client.create(e);
    const token = tokenOf((await c.call('POST', '/v1/purchase/web-link', {})).body.url);
    const s = await post(e, '/v1/web/session', { token });
    expect(s.status).toBe(200);
    expect(s.body).toMatchObject({ enabled: true, testMode: true, link: { status: 'valid' }, client: { provider: 'stripe', environment: 'sandbox' } });
    expect(s.body.packages).toEqual([
      { id: 'web.5', priceMicro: 5_000_000, creditMicro: 5_000_000 },
      { id: 'web.10', priceMicro: 10_000_000, creditMicro: 10_000_000 },
      { id: 'web.20', priceMicro: 20_000_000, creditMicro: 20_000_000 },
    ]);
    expect((await post(e, '/v1/web/session', { token: 'x'.repeat(32) })).body.link.status).toBe('invalid');
    await DB.prepare('UPDATE web_links SET expires_at = 1').run();
    expect((await post(e, '/v1/web/session', { token })).body.link.status).toBe('expired');
    const co = await post(e, '/v1/web/checkout', { token, packageId: 'web.5', kind: 'credit' });
    expect(co.status).toBe(410);
    expect(co.body.error.code).toBe('link_expired');
  });

  it('CORS-Vorabanfrage der Kaufseite', async () => {
    const worker = (await import('../src/index')).default;
    const r = await worker.fetch(new Request('https://x.test/v1/web/checkout', { method: 'OPTIONS' }), env(), { waitUntil() {}, passThroughOnException() {} } as any);
    expect(r.status).toBe(204);
    expect(r.headers.get('access-control-allow-origin')).toBe('*');
  });
});

describe('Checkout beim Anbieter', () => {
  it('erstellt eine Stripe Checkout Session: Endpreis ohne USt, §-19-Hinweis, Bestell-ID, Rücksprung ohne Token', async () => {
    const e = env();
    const c = await Client.create(e);
    const r = await startCredit(c, e, 'web.10');
    expect(r.transactionId).toBe('cs_test_1');
    expect((r as any).checkoutUrl).toBe('https://checkout.stripe.com/c/pay/cs_test_1');
    const [url, init] = stripe.mock.calls[0];
    expect(url).toBe('https://api.stripe.com/v1/checkout/sessions');
    const h = init.headers as Record<string, string>;
    expect(h.authorization).toBe('Bearer sk_test_51abc');
    expect(h['content-type']).toBe('application/x-www-form-urlencoded');
    expect(h['idempotency-key']).toBe(`overfit-${r.orderId}`);
    const f = new URLSearchParams(String(init.body));
    expect(f.get('mode')).toBe('payment');
    expect(f.get('line_items[0][price_data][unit_amount]')).toBe('1000');
    expect(f.get('line_items[0][price_data][currency]')).toBe('eur');
    expect(f.get('line_items[0][price_data][product_data][name]')).toBe('overfit Guthaben 10 €');
    expect(f.get('metadata[overfit_order]')).toBe(r.orderId);
    expect(f.get('payment_intent_data[metadata][overfit_order]')).toBe(r.orderId);
    expect(f.get('client_reference_id')).toBe(r.orderId);
    expect(f.get('custom_text[submit][message]')).toBe('Gemäß § 19 UStG wird keine Umsatzsteuer berechnet.');
    expect(f.get('success_url')).toBe(`https://bigfire3.github.io/overfit/guthaben/?order=${r.orderId}&lang=de`);
    expect(f.get('cancel_url')).toBe('https://bigfire3.github.io/overfit/guthaben/?canceled=1&lang=de');
    // Kein Stripe Tax, keine fest verdrahteten Zahlarten (kommen aus dem Dashboard), Token nie beim Anbieter
    expect([...f.keys()].some((k) => k.startsWith('automatic_tax') || k.startsWith('payment_method_types'))).toBe(false);
    expect(String(init.body)).not.toContain(r.token);
    expect(f.get('invoice_creation[enabled]')).toBeNull();
    const order = await DB.prepare('SELECT * FROM web_orders WHERE id = ?').bind(r.orderId).first<any>();
    expect(order).toMatchObject({ status: 'open', provider: 'stripe', provider_txn_id: 'cs_test_1', credit_micro: 10_000_000, price_micro: 10_000_000, kind: 'credit', environment: 'sandbox' });
  });

  it('mit STRIPE_INVOICES=on: Rechnung mit §-19-Fußzeile (EN)', async () => {
    const e = env({ STRIPE_INVOICES: 'on' });
    await post(e, '/v1/web/checkout', { packageId: 'web.5', kind: 'code', locale: 'en' });
    const f = new URLSearchParams(String(stripe.mock.calls[0][1].body));
    expect(f.get('invoice_creation[enabled]')).toBe('true');
    expect(f.get('invoice_creation[invoice_data][footer]')).toContain('§ 19 UStG');
    expect(f.get('locale')).toBe('en');
  });

  it('unbekanntes Paket, Credit ohne Token, höchstens 5 Checkouts je Link', async () => {
    const e = env();
    const c = await Client.create(e);
    const token = tokenOf((await c.call('POST', '/v1/purchase/web-link', {})).body.url);
    expect((await post(e, '/v1/web/checkout', { token, packageId: 'web.7', kind: 'credit' })).body.error.code).toBe('unknown_product');
    expect((await post(e, '/v1/web/checkout', { packageId: 'web.5', kind: 'credit' })).body.error.code).toBe('link_invalid');
    for (let i = 0; i < 5; i++) expect((await post(e, '/v1/web/checkout', { token, packageId: 'web.5', kind: 'credit' })).status).toBe(200);
    expect((await post(e, '/v1/web/checkout', { token, packageId: 'web.5', kind: 'credit' })).body.error.code).toBe('link_used');
  });

  it('Anbieter-Fehler wird zu 502 payment_unavailable', async () => {
    stripe.mockImplementationOnce(async () => Response.json({ error: { type: 'invalid_request_error' } }, { status: 400 }));
    const e = env();
    const r = await post(e, '/v1/web/checkout', { packageId: 'web.5', kind: 'code' });
    expect(r.status).toBe(502);
    expect(r.body.error.code).toBe('payment_unavailable');
  });
});

describe('Webhook: Signatur', () => {
  it('prüft HMAC-SHA256 über "t.body", Zeitfenster und mehrere v1 (Secret-Wechsel)', async () => {
    const body = '{"a":1}';
    const t = 1_800_000_000;
    const h = await stripeSignature(SECRET, body, t);
    expect(h).toMatch(/^t=1800000000,v1=[0-9a-f]{64}$/);
    expect(await verifyStripeSignature(SECRET, h, body, t)).toBe(true);
    expect(await verifyStripeSignature(SECRET, h, body + ' ', t)).toBe(false);
    expect(await verifyStripeSignature('other', h, body, t)).toBe(false);
    expect(await verifyStripeSignature(SECRET, h, body, t + 301)).toBe(false);
    expect(await verifyStripeSignature(SECRET, h, body, t - 301)).toBe(false);
    expect(await verifyStripeSignature(SECRET, `t=${t},v1=deadbeef,${h.split(',')[1]},v0=abc`, body, t)).toBe(true);
    expect(await verifyStripeSignature(SECRET, `t=${t},v0=${h.split('v1=')[1]}`, body, t)).toBe(false);
    expect(await verifyStripeSignature(SECRET, null, body, t)).toBe(false);
    expect(await verifyStripeSignature(SECRET, 'garbage', body, t)).toBe(false);
  });

  it('falsche, fehlende oder alte Signatur → 401, nichts gebucht', async () => {
    const e = env();
    const c = await Client.create(e);
    const r = await startCredit(c, e);
    const evt = completed(r.transactionId, r.orderId);
    expect((await webhook(e, evt, { secret: 'wrong' })).status).toBe(401);
    expect((await webhook(e, evt, { header: null })).status).toBe(401);
    expect((await webhook(e, evt, { ts: Math.floor(Date.now() / 1000) - 3600 })).status).toBe(401);
    expect(await balance(c)).toBe(0);
  });
});

describe('Webhook: Zahlung', () => {
  it('bucht 5 € = 5 € Guthaben mit Quelle web, merkt PaymentIntent und Land, Link ist danach verbraucht', async () => {
    const e = env();
    const c = await Client.create(e);
    const r = await startCredit(c, e);
    const res = await webhook(e, completed(r.transactionId, r.orderId, 500, { country: 'at' }));
    expect(res.status).toBe(200);
    expect(res.body).toMatchObject({ ok: true, credited: 5_000_000 });
    expect(await balance(c)).toBe(5_000_000);
    const entry = (await c.call('GET', '/v1/account/ledger')).body.entries[0];
    expect(entry).toMatchObject({ type: 'purchase', amountMicro: 5_000_000, source: 'web' });
    const ref = await DB.prepare("SELECT ref FROM ledger WHERE source = 'web'").first<{ ref: string }>();
    expect(ref!.ref).toBe('stripe:cs_test_1');
    const o = await DB.prepare('SELECT provider_payment_id, country, paid_micro FROM web_orders WHERE id = ?').bind(r.orderId).first<any>();
    expect(o).toEqual({ provider_payment_id: 'pi_test_1', country: 'AT', paid_micro: 5_000_000 });
    expect((await rawFetch(`/v1/web/orders/${r.orderId}`, e)).body).toMatchObject({ status: 'paid', kind: 'credit', creditMicro: 5_000_000, testMode: true, code: null });
    expect((await post(e, '/v1/web/session', { token: r.token })).body.link.status).toBe('used');
  });

  it('verzögerte Zahlart: erst async_payment_succeeded bucht', async () => {
    const e = env();
    const c = await Client.create(e);
    const r = await startCredit(c, e);
    expect((await webhook(e, completed(r.transactionId, r.orderId, 500, { status: 'unpaid' }))).body.ignored).toBe('payment_status unpaid');
    expect(await balance(c)).toBe(0);
    expect((await rawFetch(`/v1/web/orders/${r.orderId}`, e)).body.status).toBe('open');
    const ok = await webhook(e, completed(r.transactionId, r.orderId, 500, { type: 'checkout.session.async_payment_succeeded' }));
    expect(ok.body.credited).toBe(5_000_000);
    expect(await balance(c)).toBe(5_000_000);
  });

  it('ist idempotent: Wiederholung und parallele Zustellung buchen genau einmal', async () => {
    const e = env();
    const c = await Client.create(e);
    const r = await startCredit(c, e);
    const evt = completed(r.transactionId, r.orderId);
    expect((await webhook(e, evt)).body.credited).toBe(5_000_000);
    expect((await webhook(e, evt)).body.duplicate).toBe(true);
    // Auch async_payment_succeeded nach completed bucht nicht doppelt
    expect((await webhook(e, completed(r.transactionId, r.orderId, 500, { type: 'checkout.session.async_payment_succeeded' }))).body.duplicate).toBe(true);
    const r2 = await startCredit(c, e, 'web.10');
    const evt2 = completed(r2.transactionId, r2.orderId, 1000);
    const both = await Promise.all([webhook(e, evt2), webhook(e, evt2), webhook(e, evt2)]);
    expect(both.every((b) => b.status === 200)).toBe(true);
    expect(await balance(c)).toBe(15_000_000);
    const n = await DB.prepare("SELECT COUNT(*) AS n FROM ledger WHERE type = 'purchase'").first<{ n: number }>();
    expect(n!.n).toBe(2);
  });

  it('fremde Zahlung, falscher Betrag, Währung oder Modus: nichts gebucht, Vorfall markiert', async () => {
    const e = env();
    const c = await Client.create(e);
    expect((await webhook(e, completed('cs_other', null))).body.ignored).toBe('unknown_order');
    const r = await startCredit(c, e);
    expect((await webhook(e, completed(r.transactionId, r.orderId, 100))).body.ignored).toBe('amount_mismatch');
    expect((await webhook(e, completed(r.transactionId, r.orderId, 500, { currency: 'usd' }))).body.ignored).toBe('amount_mismatch');
    // Live-Ereignis an einen Test-Worker
    expect((await webhook(e, completed(r.transactionId, r.orderId, 500, { livemode: true }))).body.ignored).toBe('livemode_mismatch');
    expect(await balance(c)).toBe(0);
    const o = await DB.prepare('SELECT incident, status FROM web_orders WHERE id = ?').bind(r.orderId).first<any>();
    expect(o).toEqual({ incident: 'amount_mismatch', status: 'open' });
    // Andere Ereignisse werden quittiert und ignoriert
    expect((await webhook(e, event('payment_intent.created', {}))).body.ignored).toBe('payment_intent.created');
  });

  it('Produktion nur mit Live-Key, Testbetrieb nur mit Test-Key', async () => {
    const prod = baseEnv({ ...STRIPE, DEV_MODE: 'false' });
    expect((await rawFetch('/v1/config', prod)).body.webPurchase.enabled).toBe(false);
    expect((await webhook(prod, completed('cs_1', null))).status).toBe(503);
    const live = baseEnv({ STRIPE_SECRET_KEY: 'sk_live_51abc', STRIPE_WEBHOOK_SECRET: SECRET, WEB_PACKAGES: TEST_PACKAGES, DEV_MODE: 'false' });
    expect((await rawFetch('/v1/config', live)).body.webPurchase).toMatchObject({ enabled: true, testMode: false, provider: 'stripe' });
    expect((await post(live, '/v1/web/checkout', { packageId: 'web.5', kind: 'code' })).status).toBe(200);
    // Testereignis an den Live-Worker wird nicht gebucht
    expect((await webhook(live, completed('cs_test_1', null))).body.ignored).toBe('livemode_mismatch');
    const test = baseEnv({ STRIPE_SECRET_KEY: 'sk_live_51abc', STRIPE_WEBHOOK_SECRET: SECRET, DEV_MODE: 'false', DEPLOY_MODE: 'test' });
    expect((await rawFetch('/v1/config', test)).body.webPurchase.enabled).toBe(false);
    const testOk = baseEnv({ ...STRIPE, DEV_MODE: 'false', DEPLOY_MODE: 'test' });
    expect((await rawFetch('/v1/config', testOk)).body.webPurchase).toMatchObject({ enabled: true, testMode: true });
    // Publishable Key oder Unsinn statt Secret Key: aus
    expect((await rawFetch('/v1/config', env({ STRIPE_SECRET_KEY: 'pk_test_abc' }))).body.webPurchase.enabled).toBe(false);
  });
});

describe('Webhook: Rückerstattung und Chargeback', () => {
  async function paid(e: Env, c: Client, pkg = 'web.5', total = 500) {
    const r = await startCredit(c, e, pkg);
    await webhook(e, completed(r.transactionId, r.orderId, total));
    return r;
  }

  it('Teil-Erstattungen ziehen jeweils nur den neuen Teil ab, idempotent', async () => {
    const e = env();
    const c = await Client.create(e);
    const r = await paid(e, c, 'web.10', 1000);
    const res = await webhook(e, refunded(r.transactionId, 400));
    expect(res.body).toMatchObject({ reversed: 4_000_000, incident: 'refund' });
    expect((await webhook(e, refunded(r.transactionId, 400))).body.duplicate).toBe(true);
    expect(await balance(c)).toBe(6_000_000);
    const entry = (await c.call('GET', '/v1/account/ledger')).body.entries[0];
    expect(entry).toMatchObject({ type: 'refund', amountMicro: -4_000_000, source: 'web' });
    expect((await rawFetch(`/v1/web/orders/${r.orderId}`, e)).body.status).toBe('refunded');
    // Zweite Erstattung: Stripe meldet die Summe (10 €), gebucht werden die restlichen 6 €
    expect((await webhook(e, refunded(r.transactionId, 1000))).body.reversed).toBe(6_000_000);
    expect(await balance(c)).toBe(0);
  });

  it('Chargeback nach Verbrauch: höchstens bis 0, Vorfall markiert', async () => {
    const e = env();
    const c = await Client.create(e);
    const r = await paid(e, c);
    // Guthaben fast verbraucht
    await DB.prepare('UPDATE lots SET remaining_micro = 1000000').run();
    const res = await webhook(e, dispute('dp_1', r.transactionId, 500));
    expect(res.body).toMatchObject({ reversed: 1_000_000, incident: 'chargeback:shortfall' });
    expect((await webhook(e, dispute('dp_1', r.transactionId, 500))).body.duplicate).toBe(true);
    expect(await balance(c)).toBe(0);
    const o = await DB.prepare('SELECT status, incident, reversed_micro FROM web_orders WHERE id = ?').bind(r.orderId).first<any>();
    expect(o).toEqual({ status: 'chargeback', incident: 'chargeback:shortfall', reversed_micro: 5_000_000 });
    // Erstattung über den Preis hinaus ändert nichts mehr
    expect((await webhook(e, refunded(r.transactionId, 500))).body.reversed).toBe(0);
    const lots = await DB.prepare('SELECT MIN(remaining_micro) AS m FROM lots').first<{ m: number }>();
    expect(lots!.m).toBe(0);
  });

  it('Erstattung kommt vor der Zahlungsbestätigung an: nur der Rest wird gutgeschrieben', async () => {
    const e = env();
    const c = await Client.create(e);
    const r = await startCredit(c, e, 'web.10');
    // PaymentIntent noch unbekannt: Stripe trägt unsere Bestell-ID über die Metadaten mit
    expect((await webhook(e, refunded(r.transactionId, 300, r.orderId))).body.reversed).toBe(0);
    expect((await webhook(e, completed(r.transactionId, r.orderId, 1000))).body.credited).toBe(7_000_000);
    expect(await balance(c)).toBe(7_000_000);
    expect((await rawFetch(`/v1/web/orders/${r.orderId}`, e)).body.status).toBe('refunded');
  });

  it('Erstattung einer fremden Zahlung wird ignoriert', async () => {
    const e = env();
    expect((await webhook(e, refunded('cs_fremd', 500))).body.ignored).toBe('unknown_order');
  });

  it('nimmt anderes Guthaben (z. B. Store-Kauf) mit, aber nie unter 0', async () => {
    const e = env();
    const c = await Client.create(e);
    const r = await paid(e, c);
    await c.call('POST', '/v1/purchases/verify', { platform: 'google', productId: 'overfit.fuel.5', purchaseToken: 'mock:s1' });
    // Web-Guthaben schon verbraucht, Store-Guthaben (Rest 3,94 €) noch da → Erstattung über 5 € nimmt es mit, endet bei 0.
    await DB.prepare("UPDATE lots SET remaining_micro = 0 WHERE ref LIKE 'stripe:%'").run();
    await DB.prepare("UPDATE lots SET remaining_micro = 3935185 WHERE ref NOT LIKE 'stripe:%' AND remaining_micro = 5000000").run();
    expect(await balance(c)).toBe(3_935_185);
    expect((await webhook(e, refunded(r.transactionId, 500))).body).toMatchObject({ reversed: 3_935_185, incident: 'refund:shortfall' });
    expect(await balance(c)).toBe(0);
  });
});

describe('Guthaben-Codes', () => {
  it('Format: 12 Zeichen ohne 0/O/1/I/L, Eingabe tolerant', () => {
    const c = newCreditCode();
    expect(c).toMatch(/^[2-9A-HJ-KM-NP-Z]{12}$/);
    expect(formatCode('ABCDEFGHJKMN')).toBe('ABCD-EFGH-JKMN');
    expect(normalizeCreditCode(' abcd-efgh jkmn ')).toBe('ABCDEFGHJKMN');
    expect(normalizeCreditCode('ABCD-EFGH-JKM0')).toBeNull();
    expect(normalizeCreditCode('ABCD')).toBeNull();
  });

  async function buyCode(e: Env, pkg = 'web.5', total = 500) {
    const co = await post(e, '/v1/web/checkout', { packageId: pkg, kind: 'code', locale: 'en' });
    expect(co.status).toBe(200);
    const pending = await rawFetch(`/v1/web/orders/${co.body.orderId}`, e);
    expect(pending.body).toMatchObject({ status: 'open', kind: 'code', code: null });
    expect((await webhook(e, completed(co.body.transactionId, co.body.orderId, total))).body.code).toBe(true);
    const order = await rawFetch(`/v1/web/orders/${co.body.orderId}`, e);
    expect(order.body).toMatchObject({ status: 'paid', codeStatus: 'active' });
    expect(order.body.code).toMatch(/^[A-Z0-9]{4}-[A-Z0-9]{4}-[A-Z0-9]{4}$/);
    return { ...co.body, code: order.body.code as string };
  }

  it('Kauf ohne App → Code auf der Erfolgsseite → in der App einlösen, nur einmal', async () => {
    const e = env();
    const { code, orderId } = await buyCode(e);
    expect(new URLSearchParams(String(stripe.mock.calls[0][1].body)).get('line_items[0][price_data][product_data][name]')).toBe('overfit credit code €5');
    const a = await Client.create(e);
    const b = await Client.create(e);
    const r = await a.call('POST', '/v1/codes/redeem', { code: code.toLowerCase() });
    expect(r.status).toBe(200);
    expect(r.body).toEqual({ creditedMicro: 5_000_000, balanceMicro: 5_000_000 });
    expect((await a.call('GET', '/v1/account/ledger')).body.entries[0]).toMatchObject({ type: 'code_redeem', amountMicro: 5_000_000, source: 'code' });
    expect((await a.call('POST', '/v1/codes/redeem', { code })).body.error.code).toBe('already_redeemed');
    expect((await b.call('POST', '/v1/codes/redeem', { code })).status).toBe(409);
    expect(await balance(b)).toBe(0);
    expect((await rawFetch(`/v1/web/orders/${orderId}`, e)).body.codeStatus).toBe('redeemed');
  });

  it('parallel einlösen: genau einer bekommt das Guthaben', async () => {
    const e = env();
    const { code } = await buyCode(e);
    const cs = await Promise.all([Client.create(e), Client.create(e), Client.create(e)]);
    const rs = await Promise.all(cs.map((c) => c.call('POST', '/v1/codes/redeem', { code })));
    expect(rs.filter((r) => r.status === 200)).toHaveLength(1);
    const total = (await Promise.all(cs.map(balance))).reduce((a, b) => a + b, 0);
    expect(total).toBe(5_000_000);
  });

  it('ungültige Codes', async () => {
    const c = await Client.create(env());
    expect((await c.call('POST', '/v1/codes/redeem', { code: 'nope' })).body.error.code).toBe('invalid_code');
    expect((await c.call('POST', '/v1/codes/redeem', { code: 'ABCD-EFGH-JKMN' })).status).toBe(404);
  });

  it('Erstattung vor dem Einlösen sperrt den Code; danach zieht sie beim Einlöser ab', async () => {
    const e = env();
    const first = await buyCode(e);
    await webhook(e, refunded(first.transactionId, 500));
    const c = await Client.create(e);
    const r = await c.call('POST', '/v1/codes/redeem', { code: first.code });
    expect(r.body.error.code).toBe('code_revoked');
    expect((await rawFetch(`/v1/web/orders/${first.orderId}`, e)).body).toMatchObject({ code: null, codeStatus: 'revoked' });

    const second = await buyCode(e);
    await c.call('POST', '/v1/codes/redeem', { code: second.code });
    expect(await balance(c)).toBe(5_000_000);
    expect((await webhook(e, dispute('dp_c2', second.transactionId, 500))).body.reversed).toBe(5_000_000);
    expect(await balance(c)).toBe(0);
    expect((await c.call('GET', '/v1/account/ledger')).body.entries[0]).toMatchObject({ type: 'chargeback', source: 'code' });
  });

  it('Teil-Erstattung vor dem Einlösen verringert den Code-Wert', async () => {
    const e = env();
    const { code, transactionId } = await buyCode(e, 'web.10', 1000);
    await webhook(e, refunded(transactionId, 300));
    const c = await Client.create(e);
    expect((await c.call('POST', '/v1/codes/redeem', { code })).body.creditedMicro).toBe(7_000_000);
  });
});

describe('/v1/config', () => {
  it('meldet Web-Kauf, Testmodus, Kanäle und Pakete', async () => {
    const cfg = (await rawFetch('/v1/config', env())).body.webPurchase;
    expect(cfg).toMatchObject({ enabled: true, testMode: true, provider: 'stripe', channels: ['sideload'], pageUrl: 'https://bigfire3.github.io/overfit/guthaben/' });
    expect(cfg.packages.map((p: any) => p.id)).toEqual(['web.5', 'web.10', 'web.20']);
    // Standardpakete ohne Überschreibung: 5/10/20/50 €, Guthaben = Preis (docs/monetization.md „Rechnung“).
    const std = (await rawFetch('/v1/config', baseEnv({ ...STRIPE }))).body.webPurchase.packages;
    expect(std).toEqual([
      { id: 'web.5', priceMicro: 5_000_000, creditMicro: 5_000_000 },
      { id: 'web.10', priceMicro: 10_000_000, creditMicro: 10_000_000 },
      { id: 'web.20', priceMicro: 20_000_000, creditMicro: 20_000_000 },
      { id: 'web.50', priceMicro: 50_000_000, creditMicro: 50_000_000 },
    ]);
    expect((await rawFetch('/v1/config', env({ WEB_PURCHASE_CHANNELS: 'sideload,store' }))).body.webPurchase.channels).toEqual(['sideload', 'store']);
    expect((await rawFetch('/v1/config', env({ WEB_PURCHASE: 'off' }))).body.webPurchase.enabled).toBe(false);
    expect((await rawFetch('/v1/config', baseEnv())).body.webPurchase).toMatchObject({ enabled: false, provider: null });
  });
});

describe('Umzug per Wiederherstellungscode', () => {
  it('nimmt Web-Bestellungen mit, damit spätere Erstattungen das richtige Konto treffen', async () => {
    const e = env();
    const old = await Client.create(e);
    const r = await startCredit(old, e);
    await webhook(e, completed(r.transactionId, r.orderId));
    const { code } = (await old.call('POST', '/v1/recovery/code')).body;
    const neu = await Client.create(e);
    expect((await neu.call('POST', '/v1/recovery/restore', { code })).status).toBe(200);
    await webhook(e, refunded(r.transactionId, 200));
    expect(await balance(neu)).toBe(3_000_000);
  });
});
