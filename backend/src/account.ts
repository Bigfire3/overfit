import { ApiError } from './errors';
import type { Config, Device, Env } from './types';
import { b64uEncode, randomId, sha256, hex } from './crypto';
import { availableMicro, balanceBreakdown, expiringLots, grantStatements, ledgerCategory, now, reservedMicro } from './ledger';

export function json(req: Uint8Array): Record<string, unknown> {
  try {
    const v = JSON.parse(new TextDecoder().decode(req));
    if (v && typeof v === 'object' && !Array.isArray(v)) return v as Record<string, unknown>;
  } catch {
    /* fällt durch */
  }
  throw new ApiError(400, 'invalid_request', 'Body muss ein JSON-Objekt sein');
}

export async function accountSummary(env: Env, cfg: Config, accountId: string) {
  const acc = await env.DB.prepare('SELECT display_name, recovery_hash FROM accounts WHERE id = ?')
    .bind(accountId)
    .first<{ display_name: string | null; recovery_hash: string | null }>();
  return {
    accountId,
    balanceMicro: await availableMicro(env, accountId),
    /** Laufende Reservierungen (Anfragen beim KI-Anbieter), schon von `balanceMicro` abgezogen; Buchungen = balance + reserved. */
    reservedMicro: await reservedMicro(env, accountId),
    /** Gekauft vs. geschenkt (Test-Gutschrift, alte Start-/Einladungsboni); Geschenktes wird zuerst verbraucht. */
    ...(await balanceBreakdown(env, accountId)),
    expiring: await expiringLots(env, accountId),
    displayName: acc?.display_name ?? null,
    hasRecoveryCode: !!acc?.recovery_hash,
  };
}

export async function registerDevice(env: Env, deviceId: string, publicKeyRaw: Uint8Array, cfg?: Config) {
  const existing = await env.DB.prepare('SELECT account_id FROM devices WHERE id = ?')
    .bind(deviceId)
    .first<{ account_id: string }>();
  if (existing) return { accountId: existing.account_id, created: false };
  const accountId = randomId(12);
  const t = now();
  await env.DB.batch([
    env.DB.prepare('INSERT INTO accounts (id, created_at) VALUES (?, ?)').bind(accountId, t),
    env.DB.prepare('INSERT INTO devices (id, account_id, public_key, created_at) VALUES (?, ?, ?, ?)').bind(
      deviceId,
      accountId,
      b64uEncode(publicKeyRaw),
      t,
    ),
  ]);
  // Kein Startguthaben (Fabian, 7.11.2026): neue Konten starten mit 0 €.
  return { accountId, created: true };
}

/** Test-Guthaben (nur Testbetrieb), begrenzt durch `testCreditCapMicro` über alle Konten. Gibt den gutgeschriebenen Betrag zurück. */
export async function grantTestCredit(env: Env, cfg: Config, accountId: string, amountMicro: number, ref: string): Promise<number> {
  if (!cfg.testMode) throw new ApiError(404, 'not_found', 'Unbekannter Endpunkt');
  const used = await env.DB.prepare("SELECT COALESCE(SUM(amount_micro), 0) AS s FROM ledger WHERE type = 'test_credit'").first<{ s: number }>();
  const amount = Math.min(amountMicro, Math.max(cfg.testCreditCapMicro - (used?.s ?? 0), 0));
  if (amount <= 0) return 0;
  // Läuft nie ab (Fabian, 7.11.2026: Guthaben verfällt nicht).
  await env.DB.batch(grantStatements(env, accountId, 'test_credit', amount, null, `test:${ref}`));
  return amount;
}

export async function setProfile(env: Env, device: Device, body: Record<string, unknown>) {
  let name: string | null;
  if (body.displayName === null) name = null;
  else if (typeof body.displayName === 'string') {
    name = body.displayName.trim().replace(/\s+/g, ' ');
    if (name.length === 0) name = null;
    else if ([...name].length > 24) throw new ApiError(400, 'invalid_request', 'displayName höchstens 24 Zeichen');
  } else throw new ApiError(400, 'invalid_request', 'displayName muss String oder null sein');
  await env.DB.prepare('UPDATE accounts SET display_name = ? WHERE id = ?').bind(name, device.accountId).run();
  return { displayName: name };
}

export async function ledgerPage(env: Env, accountId: string, url: URL) {
  const limit = Math.min(Math.max(Number(url.searchParams.get('limit') ?? 50) || 50, 1), 200);
  const before = Number(url.searchParams.get('before') ?? Number.MAX_SAFE_INTEGER) || Number.MAX_SAFE_INTEGER;
  const rows = await env.DB.prepare(
    `SELECT id, ts, type, amount_micro AS amountMicro, model, tier, source, input_tokens AS inputTokens,
            cache_read_tokens AS cacheReadTokens, cache_write_tokens AS cacheWriteTokens, output_tokens AS outputTokens, fallback, requested_model AS requestedModel, reviews
     FROM ledger WHERE account_id = ? AND id < ? ORDER BY id DESC LIMIT ?`,
  )
    .bind(accountId, before, limit)
    .all<{
      id: number; type: string; amountMicro: number; reviews: number | null; fallback: number | null;
      inputTokens: number | null; cacheReadTokens: number | null; cacheWriteTokens: number | null; outputTokens: number | null;
    }>();
  // category: paid (bezahlt) | gifted (geschenkt) | used (verbraucht) | refunded (erstattet) | expired (verfallen)
  // D1 liefert `fallback` als 0/1 und NULL-Token bei Gutschriften; die App liest Boolean und Zahlen (Fabian 8.11.:
  // „Transactions“ meldete „can't be reached“, weil `"fallback":0` kein Boolean ist).
  const entries = rows.results.map((r) => ({
    ...r,
    fallback: !!r.fallback,
    inputTokens: r.inputTokens ?? 0,
    cacheReadTokens: r.cacheReadTokens ?? 0,
    cacheWriteTokens: r.cacheWriteTokens ?? 0,
    outputTokens: r.outputTokens ?? 0,
    category: ledgerCategory(r.type, r.amountMicro, r.reviews),
  }));
  const next = rows.results.length === limit ? rows.results[rows.results.length - 1].id : null;
  // Kontostand dazu, damit die Liste aufgeht: Summe aller Buchungen = balanceMicro + reservedMicro.
  return { entries, next, balanceMicro: await availableMicro(env, accountId), reservedMicro: await reservedMicro(env, accountId) };
}

// ---- Wiederherstellungscode ----
const ALPHABET = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789'; // 32 Zeichen, ohne I/O/0/1

export function newRecoveryCode(): string {
  const bytes = crypto.getRandomValues(new Uint8Array(24));
  let s = '';
  for (const b of bytes) s += ALPHABET[b & 31]; // 24 × 5 Bit = 120 Bit
  return s.match(/.{4}/g)!.join('-');
}

export function normalizeRecoveryCode(input: string): string | null {
  const c = input.toUpperCase().replace(/[\s-]/g, '').replace(/O/g, '0').replace(/[IL]/g, '1');
  // Der Alphabet-Satz enthält keine 0/1; Eingaben mit O/I/L werden daher verworfen statt „korrigiert“.
  const raw = input.toUpperCase().replace(/[\s-]/g, '');
  void c;
  return /^[A-HJ-NP-Z2-9]{24}$/.test(raw) ? raw : null;
}

export async function hashRecoveryCode(normalized: string): Promise<string> {
  return hex(await sha256('overfit-recovery-v1:' + normalized));
}

export async function createRecoveryCode(env: Env, device: Device) {
  const code = newRecoveryCode();
  const hash = await hashRecoveryCode(normalizeRecoveryCode(code)!);
  await env.DB.prepare('UPDATE accounts SET recovery_hash = ? WHERE id = ?').bind(hash, device.accountId).run();
  return { code };
}

/** Umzug: Das aufrufende (neue) Gerät übernimmt das Konto zum Code; alte Geräte werden abgemeldet. */
export async function restoreAccount(env: Env, cfg: Config, device: Device, code: string) {
  const normalized = normalizeRecoveryCode(code);
  if (!normalized) throw new ApiError(400, 'invalid_recovery_code', 'Wiederherstellungscode ungültig');
  const hash = await hashRecoveryCode(normalized);
  const target = await env.DB.prepare('SELECT id FROM accounts WHERE recovery_hash = ?')
    .bind(hash)
    .first<{ id: string }>();
  if (!target) throw new ApiError(404, 'invalid_recovery_code', 'Wiederherstellungscode ungültig');
  const from = device.accountId;
  if (target.id !== from) {
    await env.DB.batch([
      env.DB.prepare('DELETE FROM devices WHERE account_id = ? AND id != ?').bind(target.id, device.id),
      env.DB.prepare('UPDATE devices SET account_id = ? WHERE id = ?').bind(target.id, device.id),
      env.DB.prepare('UPDATE lots SET account_id = ? WHERE account_id = ?').bind(target.id, from),
      env.DB.prepare('UPDATE holds SET account_id = ? WHERE account_id = ?').bind(target.id, from),
      env.DB.prepare('UPDATE ledger SET account_id = ? WHERE account_id = ?').bind(target.id, from),
      env.DB.prepare('UPDATE OR IGNORE invite_redemptions SET redeemer_account_id = ? WHERE redeemer_account_id = ?').bind(
        target.id,
        from,
      ),
      env.DB.prepare('UPDATE invite_redemptions SET inviter_account_id = ? WHERE inviter_account_id = ?').bind(target.id, from),
      env.DB.prepare('UPDATE review_gifts SET account_id = ? WHERE account_id = ?').bind(target.id, from),
      env.DB.prepare('UPDATE web_orders SET account_id = ? WHERE account_id = ?').bind(target.id, from),
      env.DB.prepare('UPDATE web_links SET account_id = ? WHERE account_id = ?').bind(target.id, from),
      env.DB.prepare('UPDATE requests SET account_id = ? WHERE account_id = ?').bind(target.id, from),
      env.DB.prepare('UPDATE credit_codes SET redeemed_by = ? WHERE redeemed_by = ?').bind(target.id, from),
      env.DB.prepare('UPDATE accounts SET recovery_hash = NULL WHERE id = ?').bind(target.id),
      env.DB.prepare('DELETE FROM accounts WHERE id = ?').bind(from),
    ]);
  } else {
    await env.DB.prepare('UPDATE accounts SET recovery_hash = NULL WHERE id = ?').bind(target.id).run();
  }
  return accountSummary(env, cfg, target.id);
}
