import type { Env } from './types';
import { randomId } from './crypto';

export const HOLD_TTL_SEC = 15 * 60;

/** Geschenkte Euro-Posten (Startguthaben im Testbetrieb, Admin-Gutschrift, alte Euro-Einladungsboni). Alles andere ist gekauft. */
export const GIFTED_LOT_KINDS = ['test_credit', 'invite_bonus', 'invite_reward'] as const;
const GIFTED_SQL = `kind IN (${GIFTED_LOT_KINDS.map((k) => `'${k}'`).join(', ')})`;
export const now = () => Math.floor(Date.now() / 1000);

const AVAILABLE_SQL = `
  (SELECT COALESCE(SUM(remaining_micro), 0) FROM lots
     WHERE account_id = ?1 AND (expires_at IS NULL OR expires_at > ?2))
  - (SELECT COALESCE(SUM(amount_micro), 0) FROM holds
     WHERE account_id = ?1 AND created_at > ?2 - ${HOLD_TTL_SEC})`;

/** Verfügbarer Saldo = nicht verfallene Posten − laufende Reservierungen. */
export async function availableMicro(env: Env, accountId: string): Promise<number> {
  const row = await env.DB.prepare(`SELECT ${AVAILABLE_SQL} AS v`).bind(accountId, now()).first<{ v: number }>();
  return row?.v ?? 0;
}

/** Atomar reservieren: ein einziges INSERT…SELECT mit Saldoprüfung. */
export async function reserve(env: Env, accountId: string, amountMicro: number): Promise<string | null> {
  const id = randomId();
  const res = await env.DB.prepare(
    `INSERT INTO holds (id, account_id, amount_micro, created_at)
     SELECT ?3, ?1, ?4, ?2 WHERE (${AVAILABLE_SQL}) >= ?4`,
  )
    .bind(accountId, now(), id, amountMicro)
    .run();
  return res.meta.changes > 0 ? id : null;
}

export async function release(env: Env, holdId: string): Promise<void> {
  await env.DB.prepare('DELETE FROM holds WHERE id = ?').bind(holdId).run();
}

export interface UsageEntry {
  /** Tatsächlich genutztes Modell (bei Ausweichen das der Ausweich-Stufe). */
  model: string;
  /** Angefragte und abgerechnete Stufe. */
  tier: string;
  input: number;
  cacheRead: number;
  cacheWrite: number;
  output: number;
  /** Anbieterkosten (µ€, Listenpreis ohne Aufschlag) des tatsächlich genutzten Modells; null = unbekannt. */
  providerCostMicro: number | null;
  fallback: boolean;
  /** Modell der Stufe (zuerst versucht); bei Ausweichen ≠ `model`. */
  requestedModel?: string;
  photo: boolean;
}

/** Hold auflösen und `costMicro` (höchstens der Hold-Betrag) von den Posten abbuchen; verfallende zuerst. */
export async function settle(
  env: Env,
  accountId: string,
  holdId: string,
  holdAmount: number,
  cost: number,
  usage: UsageEntry,
): Promise<number> {
  const charge = Math.min(cost, holdAmount);
  const lots = await env.DB.prepare(
    `SELECT id, remaining_micro FROM lots WHERE account_id = ?1 AND remaining_micro > 0 AND (expires_at IS NULL OR expires_at > ?2)
     ORDER BY (${GIFTED_SQL}) DESC, (expires_at IS NULL), expires_at, created_at`,
  )
    .bind(accountId, now())
    .all<{ id: string; remaining_micro: number }>();
  const stmts: D1PreparedStatement[] = [env.DB.prepare('DELETE FROM holds WHERE id = ?').bind(holdId)];
  let left = charge;
  for (const lot of lots.results) {
    if (left <= 0) break;
    const take = Math.min(left, lot.remaining_micro);
    stmts.push(
      env.DB.prepare('UPDATE lots SET remaining_micro = MAX(remaining_micro - ?1, 0) WHERE id = ?2').bind(take, lot.id),
    );
    left -= take;
  }
  const charged = charge - left;
  stmts.push(
    env.DB.prepare(
      `INSERT INTO ledger (account_id, ts, type, amount_micro, model, tier, input_tokens, cache_read_tokens, cache_write_tokens, output_tokens, ref, cost_micro, fallback, photo, requested_model)
       VALUES (?, ?, 'usage', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
    ).bind(accountId, now(), -charged, usage.model, usage.tier, usage.input, usage.cacheRead, usage.cacheWrite, usage.output, holdId, usage.providerCostMicro, usage.fallback ? 1 : 0, usage.photo ? 1 : 0, usage.requestedModel ?? null),
  );
  await env.DB.batch(stmts);
  return charged;
}

/** Statements für einen neuen Guthaben-Posten samt Buchung (zum Einbau in eine Batch-Transaktion). */
export function grantStatements(
  env: Env,
  accountId: string,
  kind: 'purchase' | 'invite_bonus' | 'invite_reward' | 'test_credit' | 'code_redeem',
  amountMicro: number,
  expiresAt: number | null,
  ref: string,
  /** Quelle der Buchung: null = Store/intern, 'web' = Web-Kauf, 'code' = Guthaben-Code. */
  source: 'web' | 'code' | null = null,
): D1PreparedStatement[] {
  const t = now();
  return [
    env.DB.prepare(
      `INSERT INTO lots (id, account_id, kind, amount_micro, remaining_micro, expires_at, created_at, ref)
       VALUES (?, ?, ?, ?, ?, ?, ?, ?)`,
    ).bind(randomId(), accountId, kind, amountMicro, amountMicro, expiresAt, t, ref),
    env.DB.prepare(
      `INSERT INTO ledger (account_id, ts, type, amount_micro, ref, source) VALUES (?, ?, ?, ?, ?, ?)`,
    ).bind(accountId, t, kind, amountMicro, ref, source),
  ];
}

export async function expiringLots(
  env: Env,
  accountId: string,
): Promise<{ amountMicro: number; expiresAt: number }[]> {
  const rows = await env.DB.prepare(
    `SELECT remaining_micro AS a, expires_at AS e FROM lots
     WHERE account_id = ? AND remaining_micro > 0 AND expires_at IS NOT NULL AND expires_at > ?
     ORDER BY expires_at`,
  )
    .bind(accountId, now())
    .all<{ a: number; e: number }>();
  return rows.results.map((r) => ({ amountMicro: r.a, expiresAt: r.e }));
}

export function monthKey(ts = now()): string {
  return new Date(ts * 1000).toISOString().slice(0, 7);
}

/**
 * Statements, die bis zu `amountMicro` von den Posten eines Kontos abziehen (nie unter 0), plus die Buchung.
 * Für Rückerstattungen und Chargebacks. Gibt den tatsächlich abgezogenen Betrag zurück.
 */
export async function clawbackStatements(
  env: Env,
  accountId: string,
  amountMicro: number,
  type: 'refund' | 'chargeback',
  ref: string,
  source: 'web' | 'code',
): Promise<{ stmts: D1PreparedStatement[]; takenMicro: number }> {
  const lots = await env.DB.prepare(
    `SELECT id, remaining_micro FROM lots WHERE account_id = ?1 AND remaining_micro > 0 AND (expires_at IS NULL OR expires_at > ?2)
     ORDER BY (expires_at IS NULL) DESC, created_at DESC`,
  )
    .bind(accountId, now())
    .all<{ id: string; remaining_micro: number }>();
  const stmts: D1PreparedStatement[] = [];
  let left = amountMicro;
  for (const lot of lots.results) {
    if (left <= 0) break;
    const take = Math.min(left, lot.remaining_micro);
    stmts.push(env.DB.prepare('UPDATE lots SET remaining_micro = MAX(remaining_micro - ?1, 0) WHERE id = ?2').bind(take, lot.id));
    left -= take;
  }
  const taken = amountMicro - left;
  stmts.push(
    env.DB.prepare('INSERT INTO ledger (account_id, ts, type, amount_micro, ref, source) VALUES (?, ?, ?, ?, ?, ?)').bind(
      accountId, now(), type, -taken, ref, source,
    ),
  );
  return { stmts, takenMicro: taken };
}

/**
 * Saldo getrennt nach Herkunft. Laufende Reservierungen gehen (wie der Verbrauch) zuerst vom Geschenkten ab.
 * `giftedMicro + purchasedMicro` = verfügbarer Saldo (sofern nicht negativ).
 */
export async function balanceBreakdown(env: Env, accountId: string): Promise<{ purchasedMicro: number; giftedMicro: number }> {
  const t = now();
  const row = await env.DB.prepare(
    `SELECT
       (SELECT COALESCE(SUM(remaining_micro), 0) FROM lots WHERE account_id = ?1 AND ${GIFTED_SQL} AND (expires_at IS NULL OR expires_at > ?2)) AS g,
       (SELECT COALESCE(SUM(remaining_micro), 0) FROM lots WHERE account_id = ?1 AND NOT ${GIFTED_SQL} AND (expires_at IS NULL OR expires_at > ?2)) AS p,
       (SELECT COALESCE(SUM(amount_micro), 0) FROM holds WHERE account_id = ?1 AND created_at > ?2 - ${HOLD_TTL_SEC}) AS h`,
  )
    .bind(accountId, t)
    .first<{ g: number; p: number; h: number }>();
  const g = row?.g ?? 0;
  const p = row?.p ?? 0;
  const h = row?.h ?? 0;
  return { giftedMicro: Math.max(g - h, 0), purchasedMicro: Math.max(p - Math.max(h - g, 0), 0) };
}

/** Beginn des laufenden Kalendermonats (UTC) in Unix-Sekunden. */
export function monthStart(ts = now()): number {
  const d = new Date(ts * 1000);
  return Math.floor(Date.UTC(d.getUTCFullYear(), d.getUTCMonth(), 1) / 1000);
}

/** Kategorie einer Buchung für die Buchungsliste der App. */
export function ledgerCategory(type: string, amountMicro: number, reviews: number | null): 'paid' | 'gifted' | 'used' | 'refunded' | 'expired' {
  switch (type) {
    case 'usage':
      return 'used';
    case 'purchase':
    case 'code_redeem':
      return 'paid';
    case 'refund':
    case 'chargeback':
      return 'refunded';
    case 'invite_expired':
      return 'expired';
    default:
      return amountMicro < 0 || (reviews ?? 0) < 0 ? 'used' : 'gifted';
  }
}
