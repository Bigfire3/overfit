import { ApiError } from './errors';
import type { Env } from './types';
import { now } from './ledger';

/** Festes Zeitfenster pro Schlüssel; zählt in D1. */
export async function rateLimit(env: Env, key: string, limit: number, windowSec: number): Promise<void> {
  const window = Math.floor(now() / windowSec) * windowSec;
  const row = await env.DB.prepare(
    `INSERT INTO rate_limits (key, window, count) VALUES (?, ?, 1)
     ON CONFLICT (key, window) DO UPDATE SET count = count + 1 RETURNING count`,
  )
    .bind(key, window)
    .first<{ count: number }>();
  if ((row?.count ?? 0) > limit) {
    const retry = window + windowSec - now();
    throw new ApiError(429, 'rate_limited', 'Zu viele Anfragen', { retryAfterSec: Math.max(retry, 1) });
  }
}

export function clientIp(req: Request): string {
  return req.headers.get('cf-connecting-ip') ?? 'local';
}
