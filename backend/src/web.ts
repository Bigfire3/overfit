import { ApiError } from './errors';
import type { Config, Device, Env } from './types';
import { hex, randomId, sha256 } from './crypto';
import { availableMicro, clawbackStatements, grantStatements, now } from './ledger';
import { StripeProvider, stripeKeyEnvironment } from './payments/stripe';
import type { PaymentEnvironment, PaymentProvider } from './payments/provider';

/** Wie viele Checkouts ein Kauflink höchstens starten darf (Paket wechseln, abbrechen, neu versuchen). */
export const MAX_CHECKOUTS_PER_LINK = 5;
const MAX_WEBHOOK_BYTES = 64 * 1024;

// ---- Anbieter ------------------------------------------------------------------------------------------------

/**
 * Umgebung aus dem Stripe-Schlüssel, passend zum Betrieb: Test-/Dev-Worker nur mit Testschlüssel (`sk_test_…`),
 * Produktions-Worker nur mit Live-Schlüssel (`sk_live_…`). Sonst kein Web-Kauf: Testkarten würden in Produktion echtes
 * Guthaben (= echte KI-Kosten) erzeugen, echte Zahlungen im Testbetrieb nur Test-Guthaben.
 */
export function paymentEnvironment(cfg: Config, key: string): PaymentEnvironment | null {
  const e = stripeKeyEnvironment(key);
  if (!e) return null;
  if (cfg.testMode || cfg.devMode) return e === 'sandbox' ? e : null;
  return e === 'live' ? e : null;
}

let override: ((env: Env, cfg: Config) => PaymentProvider | null) | null = null;
/** Nur Tests: anderen Anbieter einsetzen (null = zurück zu Stripe). */
export function setPaymentProviderForTests(f: typeof override) {
  override = f;
}

export function getPaymentProvider(env: Env, cfg: Config): PaymentProvider | null {
  if (cfg.webPurchaseOff) return null;
  if (override) return override(env, cfg);
  const key = env.STRIPE_SECRET_KEY?.trim();
  const secret = env.STRIPE_WEBHOOK_SECRET?.trim();
  if (!key || !secret) return null;
  const environment = paymentEnvironment(cfg, key);
  if (!environment) return null;
  return new StripeProvider(key, secret, environment, {
    apiBase: env.STRIPE_API_BASE?.trim() || undefined,
    invoices: env.STRIPE_INVOICES === 'on',
  });
}

function requireProvider(env: Env, cfg: Config): PaymentProvider {
  const p = getPaymentProvider(env, cfg);
  if (!p) throw new ApiError(503, 'web_purchase_unavailable', 'Web-Kauf ist nicht eingerichtet');
  return p;
}

export function webPackages(cfg: Config) {
  return Object.entries(cfg.webPackages).map(([id, p]) => ({ id, priceMicro: p.priceMicro, creditMicro: p.priceMicro }));
}

/** Teil von `/v1/config`: ob und wo die App „Im Web aufladen“ zeigt. */
export function webPurchaseConfig(env: Env, cfg: Config) {
  const p = getPaymentProvider(env, cfg);
  return {
    enabled: !!p,
    /** true = Sandbox des Anbieters: keine echte Zahlung, Testkarten. */
    testMode: p ? p.environment === 'sandbox' : true,
    provider: p?.name ?? null,
    /** sideload = APK von GitHub, store = Play Store / App Store. Die App zeigt den Knopf nur in diesen Kanälen. */
    channels: cfg.webPurchaseChannels,
    pageUrl: cfg.webPurchaseUrl,
    packages: webPackages(cfg),
  };
}

// ---- Kauflink aus der App ------------------------------------------------------------------------------------

const linkHash = async (token: string) => hex(await sha256('overfit-weblink-v1:' + token));

/** `POST /v1/purchase/web-link` (signiert): kurzlebiger, einmaliger Kauflink zum anonymen Konto. */
export async function createWebLink(env: Env, cfg: Config, device: Device, body: Record<string, unknown>) {
  requireProvider(env, cfg);
  const token = randomId(24);
  const t = now();
  const expiresAt = t + cfg.webLinkTtlSec;
  await env.DB.prepare('INSERT INTO web_links (token_hash, account_id, created_at, expires_at) VALUES (?, ?, ?, ?)')
    .bind(await linkHash(token), device.accountId, t, expiresAt)
    .run();
  const lang = body.locale === 'en' ? 'en' : 'de';
  // Token im Fragment: landet weder in Server-Logs von GitHub Pages noch im Referer.
  return { url: `${cfg.webPurchaseUrl}#t=${token}&lang=${lang}`, expiresAt, ttlSec: cfg.webLinkTtlSec };
}

type LinkStatus = 'valid' | 'expired' | 'used' | 'invalid';

async function linkStatus(env: Env, token: string): Promise<{ status: LinkStatus; expiresAt?: number }> {
  if (!/^[A-Za-z0-9_-]{20,64}$/.test(token)) return { status: 'invalid' };
  const row = await env.DB.prepare('SELECT expires_at, used_at, checkouts FROM web_links WHERE token_hash = ?')
    .bind(await linkHash(token))
    .first<{ expires_at: number; used_at: number | null; checkouts: number }>();
  if (!row) return { status: 'invalid' };
  if (row.used_at || row.checkouts >= MAX_CHECKOUTS_PER_LINK) return { status: 'used', expiresAt: row.expires_at };
  if (row.expires_at <= now()) return { status: 'expired', expiresAt: row.expires_at };
  return { status: 'valid', expiresAt: row.expires_at };
}

// ---- Öffentliche Endpunkte der Kaufseite ---------------------------------------------------------------------

/** `POST /v1/web/session`: Pakete, Testmodus, Anbieter und (mit Token) der Zustand des Kauflinks. */
export async function webSession(env: Env, cfg: Config, body: Record<string, unknown>) {
  const p = getPaymentProvider(env, cfg);
  const token = typeof body.token === 'string' ? body.token : '';
  return {
    enabled: !!p,
    testMode: p ? p.environment === 'sandbox' : true,
    client: p?.client ?? null,
    /** Aufschlag auf die Modellpreise (Basispunkte), damit die Kaufseite ihn nennen kann. */
    markupBp: cfg.markupBp,
    packages: webPackages(cfg),
    link: token ? await linkStatus(env, token) : null,
  };
}

/** `POST /v1/web/checkout`: Bestellung anlegen und Transaktion beim Anbieter erstellen. */
export async function webCheckout(env: Env, cfg: Config, body: Record<string, unknown>) {
  const provider = requireProvider(env, cfg);
  const kind = body.kind === 'code' ? 'code' : body.kind === 'credit' ? 'credit' : null;
  if (!kind) throw new ApiError(400, 'invalid_request', 'kind: credit | code');
  const packageId = String(body.packageId ?? '');
  const pkg = cfg.webPackages[packageId];
  if (!pkg) throw new ApiError(400, 'unknown_product', 'Unbekanntes Paket', { products: Object.keys(cfg.webPackages) });
  const locale = body.locale === 'en' ? 'en' : 'de';

  let accountId: string | null = null;
  let hash: string | null = null;
  if (kind === 'credit') {
    const token = typeof body.token === 'string' ? body.token : '';
    const st = await linkStatus(env, token);
    if (st.status !== 'valid') throw new ApiError(st.status === 'invalid' ? 404 : 410, `link_${st.status}`, 'Kauflink ungültig oder abgelaufen');
    hash = await linkHash(token);
    const row = await env.DB.prepare(
      `UPDATE web_links SET checkouts = checkouts + 1
       WHERE token_hash = ? AND expires_at > ? AND used_at IS NULL AND checkouts < ? RETURNING account_id`,
    )
      .bind(hash, now(), MAX_CHECKOUTS_PER_LINK)
      .first<{ account_id: string }>();
    if (!row) throw new ApiError(410, 'link_used', 'Kauflink ungültig oder abgelaufen');
    accountId = row.account_id;
  }

  const orderId = randomId(18);
  await env.DB.prepare(
    `INSERT INTO web_orders (id, provider, environment, kind, account_id, link_hash, package_id, price_micro, credit_micro, status, created_at)
     VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'open', ?)`,
  )
    .bind(orderId, provider.name, provider.environment, kind, accountId, hash, packageId, pkg.priceMicro, pkg.priceMicro, now())
    .run();
  const page = cfg.webPurchaseUrl;
  const sep = page.includes('?') ? '&' : '?';
  const session = await provider.createCheckout({
    orderId, kind, packageId, priceMicro: pkg.priceMicro, creditMicro: pkg.priceMicro, locale,
    successUrl: `${page}${sep}order=${encodeURIComponent(orderId)}&lang=${locale}`,
    // Ohne Token: die Kaufseite merkt ihn sich selbst (sessionStorage), er landet nicht beim Anbieter.
    cancelUrl: `${page}${sep}canceled=1&lang=${locale}`,
  });
  await env.DB.prepare('UPDATE web_orders SET provider_txn_id = ? WHERE id = ?').bind(session.providerTxnId, orderId).run();
  return {
    orderId,
    transactionId: session.providerTxnId,
    checkoutUrl: session.checkoutUrl,
    client: provider.client,
    testMode: provider.environment === 'sandbox',
  };
}

/** `GET /v1/web/orders/<id>`: Zustand für die Erfolgsseite; bei Code-Käufen der Code, sobald bezahlt. */
export async function webOrder(env: Env, orderId: string) {
  const o = await env.DB.prepare('SELECT kind, status, price_micro, credit_micro, environment FROM web_orders WHERE id = ?')
    .bind(orderId)
    .first<{ kind: string; status: string; price_micro: number; credit_micro: number; environment: string }>();
  if (!o) throw new ApiError(404, 'not_found', 'Bestellung unbekannt');
  const code =
    o.kind === 'code' && o.status !== 'open'
      ? await env.DB.prepare('SELECT code, status, amount_micro FROM credit_codes WHERE order_id = ?')
          .bind(orderId)
          .first<{ code: string; status: string; amount_micro: number }>()
      : null;
  return {
    kind: o.kind,
    status: o.status,
    priceMicro: o.price_micro,
    creditMicro: o.credit_micro,
    testMode: o.environment === 'sandbox',
    code: code && code.status !== 'revoked' ? formatCode(code.code) : null,
    codeStatus: code?.status ?? null,
  };
}

// ---- Webhook ------------------------------------------------------------------------------------------------

interface OrderRow {
  id: string;
  provider: string;
  provider_txn_id: string | null;
  provider_payment_id: string | null;
  kind: 'credit' | 'code';
  account_id: string | null;
  link_hash: string | null;
  price_micro: number;
  credit_micro: number;
  status: string;
  reversed_micro: number;
}

async function eventSeen(env: Env, provider: string, key: string): Promise<boolean> {
  return !!(await env.DB.prepare('SELECT 1 AS x FROM web_events WHERE provider = ? AND event_key = ?').bind(provider, key).first());
}

/** `POST /v1/webhooks/payment`: Signatur prüfen, Guthaben idempotent buchen bzw. bei Erstattung zurückholen. */
export async function handlePaymentWebhook(env: Env, cfg: Config, req: Request): Promise<Record<string, unknown>> {
  const provider = requireProvider(env, cfg);
  const len = Number(req.headers.get('content-length') ?? 0);
  if (len > MAX_WEBHOOK_BYTES) throw new ApiError(413, 'body_too_large', 'Anfrage zu groß');
  const raw = await req.text();
  if (raw.length > MAX_WEBHOOK_BYTES) throw new ApiError(413, 'body_too_large', 'Anfrage zu groß');
  const evt = await provider.parseWebhook(req.headers, raw);
  const P = provider.name;

  if (evt.type === 'ignored') return { ok: true, ignored: evt.reason };

  if (evt.type === 'paid') {
    const key = `paid:${evt.providerTxnId}`;
    if (await eventSeen(env, P, key)) return { ok: true, duplicate: true };
    const order = await env.DB.prepare('SELECT * FROM web_orders WHERE provider = ? AND provider_txn_id = ?')
      .bind(P, evt.providerTxnId)
      .first<OrderRow>()
      ?? (evt.orderId ? await env.DB.prepare('SELECT * FROM web_orders WHERE id = ? AND provider = ?').bind(evt.orderId, P).first<OrderRow>() : null);
    // Fremde Zahlung (z. B. im Stripe-Dashboard angelegt): nicht unser Kauf, nichts buchen.
    if (!order) return { ok: true, ignored: 'unknown_order' };
    if (order.provider_txn_id && order.provider_txn_id !== evt.providerTxnId) return { ok: true, ignored: 'txn_mismatch' };
    if (evt.currency !== 'EUR' || evt.totalMinor * 10_000 < order.price_micro) {
      await env.DB.prepare("UPDATE web_orders SET incident = 'amount_mismatch' WHERE id = ?").bind(order.id).run();
      console.error('web purchase amount mismatch', order.id, evt.currency, evt.totalMinor);
      return { ok: true, ignored: 'amount_mismatch' };
    }
    const t = now();
    // Webhooks kommen nicht garantiert in Reihenfolge: lief eine Erstattung schon vor der Zahlungsbestätigung ein,
    // wird nur der nicht erstattete Teil gutgeschrieben.
    const credit = Math.max(order.credit_micro - Math.floor((order.reversed_micro * order.credit_micro) / order.price_micro), 0);
    const stmts: D1PreparedStatement[] = [
      // Wächter: zweite Zustellung desselben Ereignisses scheitert hier, die ganze Batch-Transaktion rollt zurück.
      env.DB.prepare('INSERT INTO web_events (provider, event_key, created_at) VALUES (?, ?, ?)').bind(P, key, t),
      env.DB.prepare(
        `UPDATE web_orders SET status = CASE WHEN status = 'open' THEN 'paid' ELSE status END, paid_at = ?, provider_txn_id = ?,
           provider_payment_id = COALESCE(?, provider_payment_id), country = ?, paid_micro = ? WHERE id = ?`,
      ).bind(t, evt.providerTxnId, evt.paymentId, evt.country, evt.totalMinor * 10_000, order.id),
    ];
    if (order.link_hash) {
      stmts.push(env.DB.prepare('UPDATE web_links SET used_at = ? WHERE token_hash = ? AND used_at IS NULL').bind(t, order.link_hash));
    }
    if (order.kind === 'credit') {
      if (!order.account_id) throw new ApiError(500, 'internal_error', 'Bestellung ohne Konto');
      if (credit > 0) stmts.push(...grantStatements(env, order.account_id, 'purchase', credit, null, `${P}:${evt.providerTxnId}`, 'web'));
    } else {
      stmts.push(
        env.DB.prepare('INSERT INTO credit_codes (code, order_id, amount_micro, status, created_at) VALUES (?, ?, ?, ?, ?)').bind(
          newCreditCode(), order.id, credit, credit > 0 ? 'active' : 'revoked', t,
        ),
      );
    }
    try {
      await env.DB.batch(stmts);
    } catch (e) {
      if (await eventSeen(env, P, key)) return { ok: true, duplicate: true };
      throw e;
    }
    return { ok: true, credited: order.kind === 'credit' ? credit : 0, code: order.kind === 'code' };
  }

  // Rückerstattung / Chargeback
  const key = `adj:${evt.adjustmentId}`;
  if (await eventSeen(env, P, key)) return { ok: true, duplicate: true };
  const order =
    (await env.DB.prepare('SELECT * FROM web_orders WHERE provider = ? AND provider_payment_id = ?').bind(P, evt.paymentId).first<OrderRow>()) ??
    (evt.orderId ? await env.DB.prepare('SELECT * FROM web_orders WHERE id = ? AND provider = ?').bind(evt.orderId, P).first<OrderRow>() : null);
  if (!order) return { ok: true, ignored: 'unknown_order' };
  if (order.provider_payment_id && order.provider_payment_id !== evt.paymentId) return { ok: true, ignored: 'payment_mismatch' };
  const t = now();
  // Stripe meldet bei Erstattungen die Summe aller bisherigen; gebucht wird nur der neue Teil.
  const newMicro = evt.cumulative ? evt.amountMinor * 10_000 - order.reversed_micro : evt.amountMinor * 10_000;
  const refundMicro = Math.max(Math.min(newMicro, order.price_micro - order.reversed_micro), 0);
  // Anteil des Guthabens, der zur erstatteten Summe gehört (heute 1:1).
  const creditBack = Math.max(Math.floor((refundMicro * order.credit_micro) / order.price_micro), 0);
  const ref = `${P}:${evt.adjustmentId}`;
  const stmts: D1PreparedStatement[] = [
    env.DB.prepare('INSERT INTO web_events (provider, event_key, created_at) VALUES (?, ?, ?)').bind(P, key, t),
  ];
  let taken = 0;
  let codeRevoked = false;
  if (order.status === 'open') {
    // Erstattung vor der Zahlungsbestätigung: es wurde nie etwas gutgeschrieben.
  } else if (order.kind === 'credit' && order.account_id) {
    const c = await clawbackStatements(env, order.account_id, creditBack, evt.action, ref, 'web');
    stmts.push(...c.stmts);
    taken = c.takenMicro;
  } else if (order.kind === 'code') {
    const code = await env.DB.prepare('SELECT code, status, amount_micro, redeemed_by FROM credit_codes WHERE order_id = ?')
      .bind(order.id)
      .first<{ code: string; status: string; amount_micro: number; redeemed_by: string | null }>();
    if (code?.status === 'active') {
      const rest = Math.max(code.amount_micro - creditBack, 0);
      codeRevoked = rest === 0;
      stmts.push(
        env.DB.prepare('UPDATE credit_codes SET amount_micro = ?, status = ? WHERE code = ? AND status = ?').bind(
          rest, codeRevoked ? 'revoked' : 'active', code.code, 'active',
        ),
      );
      taken = code.amount_micro - rest;
    } else if (code?.status === 'redeemed' && code.redeemed_by) {
      const c = await clawbackStatements(env, code.redeemed_by, creditBack, evt.action, ref, 'code');
      stmts.push(...c.stmts);
      taken = c.takenMicro;
    }
  }
  // Vorfall markieren; reicht das Guthaben nicht (schon verbraucht), steht das dabei.
  const incident = taken < creditBack && order.status !== 'open' ? `${evt.action}:shortfall` : evt.action;
  stmts.push(
    env.DB.prepare(
      'UPDATE web_orders SET status = ?, reversed_micro = reversed_micro + ?, incident = ?, provider_payment_id = COALESCE(provider_payment_id, ?) WHERE id = ?',
    ).bind(evt.action === 'chargeback' ? 'chargeback' : 'refunded', refundMicro, incident, evt.paymentId, order.id),
  );
  try {
    await env.DB.batch(stmts);
  } catch (e) {
    if (await eventSeen(env, P, key)) return { ok: true, duplicate: true };
    throw e;
  }
  if (incident.endsWith('shortfall')) console.warn('web purchase reversal shortfall', order.id, creditBack, taken);
  return { ok: true, reversed: taken, incident, codeRevoked };
}

// ---- Guthaben-Codes ------------------------------------------------------------------------------------------

/** Ohne 0/O/1/I/L, damit Codes abtippbar sind. 31 Zeichen × 12 Stellen ≈ 59 Bit. */
const CODE_ALPHABET = '23456789ABCDEFGHJKMNPQRSTUVWXYZ';

export function newCreditCode(): string {
  const out: string[] = [];
  while (out.length < 12) {
    for (const b of crypto.getRandomValues(new Uint8Array(16))) {
      if (b < 248 && out.length < 12) out.push(CODE_ALPHABET[b % 31]); // 248 = 8 × 31, ohne Verzerrung
    }
  }
  return out.join('');
}

export function formatCode(code: string): string {
  return code.match(/.{1,4}/g)!.join('-');
}

export function normalizeCreditCode(input: string): string | null {
  const c = input.toUpperCase().replace(/[\s-]/g, '');
  return new RegExp(`^[${CODE_ALPHABET}]{12}$`).test(c) ? c : null;
}

/** `POST /v1/codes/redeem` (signiert): Code einmalig aufs Konto buchen. */
export async function redeemCode(env: Env, device: Device, body: Record<string, unknown>) {
  const code = normalizeCreditCode(String(body.code ?? ''));
  if (!code) throw new ApiError(400, 'invalid_code', 'Code ungültig');
  const t = now();
  const lotId = randomId();
  const ref = `code:${code.slice(0, 4)}`;
  const active = `FROM credit_codes WHERE code = ?1 AND status = 'active' AND amount_micro > 0`;
  // Eine Transaktion: Posten und Buchung nur, wenn der Code noch aktiv ist; danach als eingelöst markieren.
  const res = await env.DB.batch([
    env.DB.prepare(
      `INSERT INTO lots (id, account_id, kind, amount_micro, remaining_micro, expires_at, created_at, ref)
       SELECT ?2, ?3, 'code', amount_micro, amount_micro, NULL, ?4, ?5 ${active}`,
    ).bind(code, lotId, device.accountId, t, ref),
    env.DB.prepare(
      `INSERT INTO ledger (account_id, ts, type, amount_micro, ref, source) SELECT ?2, ?3, 'code_redeem', amount_micro, ?4, 'code' ${active}`,
    ).bind(code, device.accountId, t, ref),
    env.DB.prepare(`UPDATE credit_codes SET status = 'redeemed', redeemed_by = ?2, redeemed_at = ?3 WHERE code = ?1 AND status = 'active' AND amount_micro > 0`).bind(
      code, device.accountId, t,
    ),
  ]);
  if (res[2].meta.changes === 0) {
    const row = await env.DB.prepare('SELECT status FROM credit_codes WHERE code = ?').bind(code).first<{ status: string }>();
    if (row?.status === 'redeemed') throw new ApiError(409, 'already_redeemed', 'Code wurde schon eingelöst');
    if (row?.status === 'revoked') throw new ApiError(410, 'code_revoked', 'Code wurde nach einer Erstattung gesperrt');
    throw new ApiError(404, 'invalid_code', 'Code ungültig');
  }
  const lot = await env.DB.prepare('SELECT amount_micro FROM lots WHERE id = ?').bind(lotId).first<{ amount_micro: number }>();
  return { creditedMicro: lot?.amount_micro ?? 0, balanceMicro: await availableMicro(env, device.accountId) };
}
