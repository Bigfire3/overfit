import type { Env } from './types';

/** Zwecke, die die App in `overfit.purpose` schicken darf; alles andere wird `other`. */
export const PURPOSES = ['review', 'chat', 'autofill', 'plan', 'other'] as const;
export type Purpose = (typeof PURPOSES)[number];

export function purposeOf(value: unknown): Purpose | null {
  if (value === undefined || value === null) return null;
  return (PURPOSES as readonly unknown[]).includes(value) ? (value as Purpose) : 'other';
}

export interface RequestLog {
  accountId: string;
  ts: number;
  durationMs: number;
  purpose: Purpose | null;
  tier: string;
  status: 'ok' | 'failed';
  errorCode?: string | null;
  requestedModel: string;
  requestedProvider?: string | null;
  model?: string | null;
  provider?: string | null;
  fallbackFromStatus?: number | null;
  fallbackReason?: string | null;
  input?: number;
  cacheRead?: number;
  cacheWrite?: number;
  output?: number;
  chargedMicro?: number;
  gift?: boolean;
  photo?: boolean;
}

/** Schreibt einen Protokolleintrag und gibt seine ID zurück. Fehler beim Schreiben dürfen die Antwort nie verhindern (dann null). */
export async function logRequest(env: Env, r: RequestLog): Promise<number | null> {
  try {
    const res = await env.DB.prepare(
      `INSERT INTO requests (account_id, ts, duration_ms, purpose, tier, status, error_code, requested_model, model,
         fallback_from_status, fallback_reason, input_tokens, cache_read_tokens, cache_write_tokens, output_tokens, charged_micro, gift, photo,
         requested_provider, provider)
       VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
    )
      .bind(
        r.accountId, r.ts, r.durationMs, r.purpose, r.tier, r.status, r.errorCode ?? null, r.requestedModel, r.model ?? null,
        r.fallbackFromStatus ?? null, r.fallbackReason ? r.fallbackReason.slice(0, 200) : null,
        r.input ?? 0, r.cacheRead ?? 0, r.cacheWrite ?? 0, r.output ?? 0, r.chargedMicro ?? 0, r.gift ? 1 : 0, r.photo ? 1 : 0,
        r.requestedProvider ?? null, r.provider ?? null,
      )
      .run();
    return res.meta.last_row_id ?? null;
  } catch (e) {
    console.error('requests: Protokoll nicht geschrieben', e instanceof Error ? e.message : String(e));
    return null;
  }
}

/** `GET /v1/account/requests?limit=50&before=<id>`: neueste zuerst. */
export async function requestsPage(env: Env, accountId: string, url: URL) {
  const limit = Math.min(Math.max(Number(url.searchParams.get('limit') ?? 50) || 50, 1), 200);
  const before = Number(url.searchParams.get('before') ?? Number.MAX_SAFE_INTEGER) || Number.MAX_SAFE_INTEGER;
  const rows = await env.DB.prepare(
    `SELECT id, ts, duration_ms AS durationMs, purpose, tier, status, error_code AS errorCode, requested_model AS requestedModel,
            model, fallback_from_status AS fallbackFromStatus, fallback_reason AS fallbackReason, input_tokens AS inputTokens,
            cache_read_tokens AS cacheReadTokens, cache_write_tokens AS cacheWriteTokens, output_tokens AS outputTokens,
            charged_micro AS chargedMicro, gift, photo, requested_provider AS requestedProvider, provider
     FROM requests WHERE account_id = ? AND id < ? ORDER BY id DESC LIMIT ?`,
  )
    .bind(accountId, before, limit)
    .all<Record<string, unknown> & { id: number; gift: number; photo: number }>();
  const entries = rows.results.map((r) => ({ ...r, gift: !!r.gift, photo: !!r.photo, fallback: r.model != null && r.model !== r.requestedModel }));
  return { entries, next: entries.length === limit ? entries[entries.length - 1].id : null };
}

