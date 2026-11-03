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
