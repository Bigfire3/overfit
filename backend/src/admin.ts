import { ApiError } from './errors';
import type { Config, Env } from './types';
import { json, grantTestCredit } from './account';
import { sha256 } from './crypto';

/** Größte Einzel-Gutschrift (Mikro-Euro) und größte Kontenzahl für „an alle“. */
export const ADMIN_MAX_CREDIT_MICRO = 10_000_000;
const ADMIN_MAX_ACCOUNTS = 100;

async function sameSecret(a: string, b: string): Promise<boolean> {
  const [x, y] = await Promise.all([sha256(a), sha256(b)]);
  let diff = 0;
  for (let i = 0; i < x.length; i++) diff |= x[i] ^ y[i];
  return diff === 0;
}

/**
 * Test-Guthaben von Hand: `POST /v1/admin/credit` mit `Authorization: Bearer <ADMIN_SECRET>` und
 * `{ "amountMicro": 5000000, "accountId": "…" }` oder `{ "amountMicro": …, "all": true }`.
 * Gibt es nur im Testbetrieb und nur mit gesetztem ADMIN_SECRET; sonst 404 (die Route existiert dann nicht).
 */
export async function handleAdminCredit(req: Request, env: Env, cfg: Config, body: Uint8Array): Promise<Response> {
  if (!cfg.testMode || !env.ADMIN_SECRET || env.ADMIN_SECRET.length < 16) throw new ApiError(404, 'not_found', 'Unbekannter Endpunkt');
  if (req.method !== 'POST') throw new ApiError(405, 'method_not_allowed', 'Methode nicht erlaubt');
  const auth = req.headers.get('authorization') ?? '';
  if (!auth.startsWith('Bearer ') || !(await sameSecret(auth.slice(7), env.ADMIN_SECRET))) {
    throw new ApiError(401, 'unauthorized', 'Admin-Geheimnis fehlt oder ist falsch');
  }
  const b = json(body);
  const amount = b.amountMicro;
  if (typeof amount !== 'number' || !Number.isSafeInteger(amount) || amount <= 0 || amount > ADMIN_MAX_CREDIT_MICRO) {
    throw new ApiError(400, 'invalid_request', `amountMicro: ganze Zahl 1 … ${ADMIN_MAX_CREDIT_MICRO}`);
  }
  let ids: string[];
  if (b.all === true) {
    const rows = await env.DB.prepare('SELECT id FROM accounts ORDER BY created_at LIMIT ?').bind(ADMIN_MAX_ACCOUNTS + 1).all<{ id: string }>();
    ids = rows.results.map((r) => r.id);
    if (ids.length > ADMIN_MAX_ACCOUNTS) throw new ApiError(400, 'too_many_accounts', `„all“ nur bis ${ADMIN_MAX_ACCOUNTS} Konten`);
  } else if (typeof b.accountId === 'string' && b.accountId) {
    const row = await env.DB.prepare('SELECT id FROM accounts WHERE id = ?').bind(b.accountId).first<{ id: string }>();
    if (!row) throw new ApiError(404, 'not_found', 'Konto nicht gefunden');
    ids = [row.id];
  } else throw new ApiError(400, 'invalid_request', 'accountId oder all:true erforderlich');
  let credited = 0;
  let totalMicro = 0;
  for (const id of ids) {
    const got = await grantTestCredit(env, cfg, id, amount, 'admin');
    if (got > 0) credited++;
    totalMicro += got;
  }
  return Response.json({ accounts: ids.length, credited, totalMicro, capMicro: cfg.testCreditCapMicro });
}

/**
 * Inhaltsfreie Nutzungsauswertung: `GET /v1/admin/usage?from=&to=` (ISO-Datum, `to` exklusiv, Standard: letzte 30 Tage)
 * mit `Authorization: Bearer <ADMIN_SECRET>`. Liest nur Zahlen aus dem Ledger (Modell, Tokens, Kosten, Marge,
 * Stufe, Ausweichung, Foto), nie Prompts, Antworten oder Fotos. Gibt es auch in Produktion, aber nur mit ADMIN_SECRET.
 */
export async function handleAdminUsage(req: Request, env: Env, url: URL): Promise<Response> {
  if (!env.ADMIN_SECRET || env.ADMIN_SECRET.length < 16) throw new ApiError(404, 'not_found', 'Unbekannter Endpunkt');
  if (req.method !== 'GET') throw new ApiError(405, 'method_not_allowed', 'Methode nicht erlaubt');
  const auth = req.headers.get('authorization') ?? '';
  if (!auth.startsWith('Bearer ') || !(await sameSecret(auth.slice(7), env.ADMIN_SECRET))) {
    throw new ApiError(401, 'unauthorized', 'Admin-Geheimnis fehlt oder ist falsch');
  }
  const day = (v: string | null, def: number) => {
    if (!v) return def;
    const t = Date.parse(v);
    if (!Number.isFinite(t)) throw new ApiError(400, 'invalid_request', 'from/to: ISO-Datum, z. B. 2026-11-01');
    return Math.floor(t / 1000);
  };
  const to = day(url.searchParams.get('to'), Math.floor(Date.now() / 1000) + 1);
  const from = day(url.searchParams.get('from'), to - 30 * 86400);
  const rows = await env.DB.prepare(
    `SELECT model, tier, fallback, photo,
            COUNT(*) AS requests,
            SUM(input_tokens) AS inputTokens, SUM(cache_read_tokens) AS cacheReadTokens,
            SUM(cache_write_tokens) AS cacheWriteTokens, SUM(output_tokens) AS outputTokens,
            SUM(-amount_micro) AS chargedMicro,
            SUM(cost_micro) AS providerCostMicro,
            SUM(CASE WHEN cost_micro IS NULL THEN 0 ELSE 1 END) AS costKnown
     FROM ledger WHERE type = 'usage' AND ts >= ? AND ts < ?
     GROUP BY model, tier, fallback, photo ORDER BY chargedMicro DESC`,
  )
    .bind(from, to)
    .all<Record<string, number | string | null>>();
  const groups = rows.results.map((r) => ({
    model: r.model,
    tier: r.tier,
    fallback: r.fallback === 1,
    photo: r.photo === 1,
    requests: r.requests,
    inputTokens: r.inputTokens ?? 0,
    cacheReadTokens: r.cacheReadTokens ?? 0,
    cacheWriteTokens: r.cacheWriteTokens ?? 0,
    outputTokens: r.outputTokens ?? 0,
    chargedMicro: r.chargedMicro ?? 0,
    providerCostMicro: r.costKnown ? (r.providerCostMicro ?? 0) : null,
    // Marge = berechnet − Anbieterkosten; nur wo die Selbstkosten bekannt sind (ältere Buchungen: null).
    marginMicro: r.costKnown ? Number(r.chargedMicro ?? 0) - Number(r.providerCostMicro ?? 0) : null,
  }));
  const sum = (f: (g: (typeof groups)[number]) => number) => groups.reduce((a, g) => a + f(g), 0);
  return Response.json({
    from,
    to,
    currency: 'EUR',
    unit: 'micro-euro',
    totals: {
      requests: sum((g) => Number(g.requests)),
      photoRequests: sum((g) => (g.photo ? Number(g.requests) : 0)),
      fallbackRequests: sum((g) => (g.fallback ? Number(g.requests) : 0)),
      inputTokens: sum((g) => Number(g.inputTokens)),
      outputTokens: sum((g) => Number(g.outputTokens)),
      chargedMicro: sum((g) => Number(g.chargedMicro)),
      providerCostMicro: sum((g) => Number(g.providerCostMicro ?? 0)),
      marginMicro: sum((g) => Number(g.marginMicro ?? 0)),
    },
    groups,
  });
}
