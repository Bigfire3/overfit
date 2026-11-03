import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { env as workerEnv } from 'cloudflare:workers';
import { Client, baseEnv, rawFetch } from './helpers';
import { paddleSignature, verifyPaddleSignature } from '../src/payments/paddle';
import { formatCode, newCreditCode, normalizeCreditCode } from '../src/web';
import type { Env } from '../src/types';

const SECRET = 'pdl_ntfset_test_secret_0123456789';
const PADDLE = { PADDLE_API_KEY: 'pdl_sdbx_apikey_test', PADDLE_WEBHOOK_SECRET: SECRET, PADDLE_CLIENT_TOKEN: 'test_client_token' };
const env = (extra: Partial<Env> = {}) => baseEnv({ ...PADDLE, ...extra });
const DB = (workerEnv as unknown as Env).DB;

let paddle: ReturnType<typeof vi.fn<(url: string, init: RequestInit) => Promise<Response>>>;
let txnCounter = 0;
beforeEach(() => {
  txnCounter = 0;
  paddle = vi.fn(async (_url: string, init: RequestInit) => {
    const id = `txn_${++txnCounter}`;
    const body = JSON.parse(String(init.body));
    return Response.json({ data: { id, status: 'ready', custom_data: body.custom_data, checkout: { url: `https://pay.test/?_ptxn=${id}` } } }, { status: 201 });
  });
  const real = globalThis.fetch;
  vi.stubGlobal('fetch', (input: any, init?: any) => {
    const url = String(input instanceof Request ? input.url : input);
    if (url.startsWith('https://sandbox-api.paddle.com') || url.startsWith('https://api.paddle.com')) return paddle(url, init);
    return real(input, init);
  });
});
afterEach(() => vi.unstubAllGlobals());

const post = (e: Env, path: string, body: unknown, headers: Record<string, string> = {}) =>
  rawFetch(path, e, { method: 'POST', headers: { 'content-type': 'application/json', ...headers }, body: JSON.stringify(body) });

async function webhook(e: Env, payload: unknown, opts: { secret?: string; ts?: number; header?: string | null } = {}) {
  const raw = JSON.stringify(payload);
  const sig = opts.header === undefined ? await paddleSignature(opts.secret ?? SECRET, raw, opts.ts) : opts.header;
  return rawFetch('/v1/webhooks/payment', e, {
    method: 'POST',
    headers: { 'content-type': 'application/json', ...(sig ? { 'paddle-signature': sig } : {}) },
    body: raw,
  });
}

const completed = (txn: string, order: string | null, grandTotal = '500', currency = 'EUR') => ({
  event_id: `evt_${txn}`,
  event_type: 'transaction.completed',
  data: { id: txn, status: 'completed', currency_code: currency, custom_data: order ? { overfit_order: order } : null, details: { totals: { grand_total: grandTotal, total: grandTotal } } },
});
const adjustment = (id: string, txn: string, total: string, action = 'refund', status = 'approved', evtType = 'adjustment.updated') => ({
  event_type: evtType,
  data: { id, action, status, transaction_id: txn, totals: { total, currency_code: 'EUR' } },
});

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

  it('ohne Signatur abgelehnt, ohne Paddle-Secrets 503', async () => {
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
    expect(s.body).toMatchObject({ enabled: true, testMode: true, link: { status: 'valid' }, client: { provider: 'paddle', environment: 'sandbox', clientToken: 'test_client_token' } });
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
  it('erstellt eine Paddle-Transaktion mit Endpreis inkl. USt und unserer Bestell-ID', async () => {
    const e = env();
    const c = await Client.create(e);
    const r = await startCredit(c, e, 'web.10');
    expect(r.transactionId).toBe('txn_1');
    const [url, init] = paddle.mock.calls[0];
    expect(url).toBe('https://sandbox-api.paddle.com/transactions');
    expect((init.headers as Record<string, string>).authorization).toBe('Bearer pdl_sdbx_apikey_test');
    const body = JSON.parse(String(init.body));
    expect(body.items[0].price.unit_price).toEqual({ amount: '1000', currency_code: 'EUR' });
    expect(body.items[0].price.tax_mode).toBe('internal');
    expect(body.items[0].price.name).toBe('overfit Guthaben 10 €');
    expect(body.custom_data).toEqual({ overfit_order: r.orderId, kind: 'credit', package: 'web.10' });
    const order = await DB.prepare('SELECT * FROM web_orders WHERE id = ?').bind(r.orderId).first<any>();
    expect(order).toMatchObject({ status: 'open', provider_txn_id: 'txn_1', credit_micro: 10_000_000, price_micro: 10_000_000, kind: 'credit', environment: 'sandbox' });
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
    paddle.mockImplementationOnce(async () => Response.json({ error: { code: 'transaction_default_checkout_url_not_set' } }, { status: 400 }));
    const e = env();
    const r = await post(e, '/v1/web/checkout', { packageId: 'web.5', kind: 'code' });
    expect(r.status).toBe(502);
    expect(r.body.error.code).toBe('payment_unavailable');
  });
});

describe('Webhook: Signatur', () => {
  it('prüft HMAC-SHA256 über "ts:body", Zeitfenster und mehrere h1 (Schlüsselwechsel)', async () => {
    const body = '{"a":1}';
    const t = 1_800_000_000;
    const h = await paddleSignature(SECRET, body, t);
    expect(await verifyPaddleSignature(SECRET, h, body, t)).toBe(true);
    expect(await verifyPaddleSignature(SECRET, h, body + ' ', t)).toBe(false);
    expect(await verifyPaddleSignature('other', h, body, t)).toBe(false);
    expect(await verifyPaddleSignature(SECRET, h, body, t + 301)).toBe(false);
    expect(await verifyPaddleSignature(SECRET, `ts=${t};h1=deadbeef;${h.split(';')[1]}`, body, t)).toBe(true);
    expect(await verifyPaddleSignature(SECRET, null, body, t)).toBe(false);
    expect(await verifyPaddleSignature(SECRET, 'garbage', body, t)).toBe(false);
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
  it('bucht 5 € = 5 € Guthaben mit Quelle web und Transaktions-ID, Link ist danach verbraucht', async () => {
    const e = env();
    const c = await Client.create(e);
    const r = await startCredit(c, e);
    const res = await webhook(e, completed(r.transactionId, r.orderId));
    expect(res.status).toBe(200);
    expect(res.body).toMatchObject({ ok: true, credited: 5_000_000 });
    expect(await balance(c)).toBe(5_000_000);
    const entry = (await c.call('GET', '/v1/account/ledger')).body.entries[0];
    expect(entry).toMatchObject({ type: 'purchase', amountMicro: 5_000_000, source: 'web' });
    const ref = await DB.prepare("SELECT ref FROM ledger WHERE source = 'web'").first<{ ref: string }>();
    expect(ref!.ref).toBe('paddle:txn_1');
    expect((await rawFetch(`/v1/web/orders/${r.orderId}`, e)).body).toMatchObject({ status: 'paid', kind: 'credit', creditMicro: 5_000_000, testMode: true, code: null });
    expect((await post(e, '/v1/web/session', { token: r.token })).body.link.status).toBe('used');
  });

  it('ist idempotent: Wiederholung und parallele Zustellung buchen genau einmal', async () => {
    const e = env();
    const c = await Client.create(e);
    const r = await startCredit(c, e);
    const evt = completed(r.transactionId, r.orderId);
    expect((await webhook(e, evt)).body.credited).toBe(5_000_000);
    expect((await webhook(e, evt)).body.duplicate).toBe(true);
    const r2 = await startCredit(c, e, 'web.10');
    const evt2 = completed(r2.transactionId, r2.orderId, '1000');
    const both = await Promise.all([webhook(e, evt2), webhook(e, evt2), webhook(e, evt2)]);
    expect(both.every((b) => b.status === 200)).toBe(true);
    expect(await balance(c)).toBe(15_000_000);
    const n = await DB.prepare("SELECT COUNT(*) AS n FROM ledger WHERE type = 'purchase'").first<{ n: number }>();
    expect(n!.n).toBe(2);
  });

  it('fremde Transaktion, falscher Betrag oder Währung: nichts gebucht, Vorfall markiert', async () => {
    const e = env();
    const c = await Client.create(e);
    expect((await webhook(e, completed('txn_other', null))).body.ignored).toBe('unknown_order');
    const r = await startCredit(c, e);
    expect((await webhook(e, completed(r.transactionId, r.orderId, '100'))).body.ignored).toBe('amount_mismatch');
    expect((await webhook(e, completed(r.transactionId, r.orderId, '500', 'USD'))).body.ignored).toBe('amount_mismatch');
    expect(await balance(c)).toBe(0);
    const o = await DB.prepare('SELECT incident, status FROM web_orders WHERE id = ?').bind(r.orderId).first<any>();
    expect(o).toEqual({ incident: 'amount_mismatch', status: 'open' });
    // Andere Ereignisse werden quittiert und ignoriert
    expect((await webhook(e, { event_type: 'transaction.created', data: {} })).body.ignored).toBe('transaction.created');
  });

  it('Produktion: Sandbox-Keys schalten den Web-Kauf nicht frei, live nur mit PADDLE_ENV=live', async () => {
    const prod = baseEnv({ ...PADDLE, DEV_MODE: 'false' });
    const cfg = (await rawFetch('/v1/config', prod)).body.webPurchase;
    expect(cfg.enabled).toBe(false);
    expect((await webhook(prod, completed('txn_1', null))).status).toBe(503);
    const live = baseEnv({ ...PADDLE, DEV_MODE: 'false', PADDLE_ENV: 'live' });
    expect((await rawFetch('/v1/config', live)).body.webPurchase).toMatchObject({ enabled: true, testMode: false });
    await post(live, '/v1/web/checkout', { packageId: 'web.5', kind: 'code' });
    expect(paddle.mock.calls[0][0]).toBe('https://api.paddle.com/transactions');
    // Testbetrieb ist immer Sandbox, auch mit PADDLE_ENV=live
    const test = baseEnv({ ...PADDLE, DEV_MODE: 'false', DEPLOY_MODE: 'test', PADDLE_ENV: 'live' });
    expect((await rawFetch('/v1/config', test)).body.webPurchase).toMatchObject({ enabled: true, testMode: true });
  });
});

describe('Webhook: Rückerstattung und Chargeback', () => {
  async function paid(e: Env, c: Client, pkg = 'web.5', total = '500') {
    const r = await startCredit(c, e, pkg);
    await webhook(e, completed(r.transactionId, r.orderId, total));
    return r;
  }

  it('Erstattung zieht Guthaben ab, idempotent, erst nach Freigabe', async () => {
    const e = env();
    const c = await Client.create(e);
    const r = await paid(e, c, 'web.10', '1000');
    expect((await webhook(e, adjustment('adj_1', r.transactionId, '400', 'refund', 'pending_approval', 'adjustment.created'))).body.ignored).toBe('adjustment pending_approval');
    expect(await balance(c)).toBe(10_000_000);
    const res = await webhook(e, adjustment('adj_1', r.transactionId, '400'));
    expect(res.body).toMatchObject({ reversed: 4_000_000, incident: 'refund' });
    expect((await webhook(e, adjustment('adj_1', r.transactionId, '400'))).body.duplicate).toBe(true);
    expect(await balance(c)).toBe(6_000_000);
    const entry = (await c.call('GET', '/v1/account/ledger')).body.entries[0];
    expect(entry).toMatchObject({ type: 'refund', amountMicro: -4_000_000, source: 'web' });
    expect((await rawFetch(`/v1/web/orders/${r.orderId}`, e)).body.status).toBe('refunded');
  });

  it('Chargeback nach Verbrauch: höchstens bis 0, Vorfall markiert', async () => {
    const e = env();
    const c = await Client.create(e);
    const r = await paid(e, c);
    // Guthaben fast verbraucht
    await DB.prepare('UPDATE lots SET remaining_micro = 1000000').run();
    const res = await webhook(e, adjustment('adj_cb', r.transactionId, '500', 'chargeback'));
    expect(res.body).toMatchObject({ reversed: 1_000_000, incident: 'chargeback:shortfall' });
    expect(await balance(c)).toBe(0);
    const o = await DB.prepare('SELECT status, incident, reversed_micro FROM web_orders WHERE id = ?').bind(r.orderId).first<any>();
    expect(o).toEqual({ status: 'chargeback', incident: 'chargeback:shortfall', reversed_micro: 5_000_000 });
    // Mehrfach-Erstattung über den Preis hinaus ändert nichts mehr
    expect((await webhook(e, adjustment('adj_more', r.transactionId, '500'))).body.reversed).toBe(0);
    const lots = await DB.prepare('SELECT MIN(remaining_micro) AS m FROM lots').first<{ m: number }>();
    expect(lots!.m).toBe(0);
  });

  it('Erstattung kommt vor der Zahlungsbestätigung an: nur der Rest wird gutgeschrieben', async () => {
    const e = env();
    const c = await Client.create(e);
    const r = await startCredit(c, e, 'web.10');
    expect((await webhook(e, adjustment('adj_early', r.transactionId, '300'))).body.reversed).toBe(0);
    expect((await webhook(e, completed(r.transactionId, r.orderId, '1000'))).body.credited).toBe(7_000_000);
    expect(await balance(c)).toBe(7_000_000);
    expect((await rawFetch(`/v1/web/orders/${r.orderId}`, e)).body.status).toBe('refunded');
  });

  it('nimmt anderes Guthaben (z. B. Store-Kauf) mit, aber nie unter 0', async () => {
    const e = env();
    const c = await Client.create(e);
    const r = await paid(e, c);
    await c.call('POST', '/v1/purchases/verify', { platform: 'google', productId: 'overfit.fuel.5', purchaseToken: 'mock:s1' });
    // Web-Guthaben schon verbraucht, Store-Guthaben (3,94 €) noch da → Erstattung über 5 € nimmt es mit, endet bei 0.
    await DB.prepare("UPDATE lots SET remaining_micro = 0 WHERE ref LIKE 'paddle:%'").run();
    expect(await balance(c)).toBe(3_935_185);
    expect((await webhook(e, adjustment('adj_x', r.transactionId, '500'))).body).toMatchObject({ reversed: 3_935_185, incident: 'refund:shortfall' });
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

  async function buyCode(e: Env, pkg = 'web.5', total = '500') {
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
    expect(JSON.parse(String(paddle.mock.calls[0][1].body)).items[0].price.name).toBe('overfit credit code €5');
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
    await webhook(e, adjustment('adj_c1', first.transactionId, '500'));
    const c = await Client.create(e);
    const r = await c.call('POST', '/v1/codes/redeem', { code: first.code });
    expect(r.body.error.code).toBe('code_revoked');
    expect((await rawFetch(`/v1/web/orders/${first.orderId}`, e)).body).toMatchObject({ code: null, codeStatus: 'revoked' });

    const second = await buyCode(e);
    await c.call('POST', '/v1/codes/redeem', { code: second.code });
    expect(await balance(c)).toBe(5_000_000);
    expect((await webhook(e, adjustment('adj_c2', second.transactionId, '500', 'chargeback'))).body.reversed).toBe(5_000_000);
    expect(await balance(c)).toBe(0);
    expect((await c.call('GET', '/v1/account/ledger')).body.entries[0]).toMatchObject({ type: 'chargeback', source: 'code' });
  });

  it('Teil-Erstattung vor dem Einlösen verringert den Code-Wert', async () => {
    const e = env();
    const { code, transactionId } = await buyCode(e, 'web.10', '1000');
    await webhook(e, adjustment('adj_p', transactionId, '300'));
    const c = await Client.create(e);
    expect((await c.call('POST', '/v1/codes/redeem', { code })).body.creditedMicro).toBe(7_000_000);
  });
});

describe('/v1/config', () => {
  it('meldet Web-Kauf, Testmodus, Kanäle und Pakete', async () => {
    const cfg = (await rawFetch('/v1/config', env())).body.webPurchase;
    expect(cfg).toMatchObject({ enabled: true, testMode: true, provider: 'paddle', channels: ['sideload'], pageUrl: 'https://bigfire3.github.io/overfit/guthaben/' });
    expect(cfg.packages.map((p: any) => p.id)).toEqual(['web.5', 'web.10', 'web.20']);
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
    await webhook(e, adjustment('adj_m', r.transactionId, '200'));
    expect(await balance(neu)).toBe(3_000_000);
  });
});
