import { ApiError } from '../errors';
import type { Config, Device, Env } from '../types';
import { hex, sha256 } from '../crypto';
import { now } from '../ledger';
import { openJson, seal, sealJson } from './vault';
import { summarizeWorkout } from './data';

// Geräteseitige Endpunkte der KI-Chat-Verbindung (alle signiert, siehe docs/backend-api.md).

export const DOC_KINDS = ['profile', 'memory', 'template', 'workout'] as const;
export type DocKind = (typeof DOC_KINDS)[number] | 'photo';
const ID_RE = /^[A-Za-z0-9_.:-]{1,80}$/;
const MAX_BATCH = 500;
const PAIRING_TTL_SEC = 600;
const PAIR_ALPHABET = 'ABCDEFGHJKMNPQRSTUVWXYZ23456789';

export interface ChatSync {
  photosEnabled: boolean;
  enabledAt: number;
  lastSyncAt: number | null;
}

export async function getSync(env: Env, accountId: string): Promise<ChatSync | null> {
  const r = await env.DB.prepare('SELECT photos_enabled, enabled_at, last_sync_at FROM chat_sync WHERE account_id = ?')
    .bind(accountId)
    .first<{ photos_enabled: number; enabled_at: number; last_sync_at: number | null }>();
  return r ? { photosEnabled: r.photos_enabled === 1, enabledAt: r.enabled_at, lastSyncAt: r.last_sync_at } : null;
}

async function requireSync(env: Env, accountId: string): Promise<ChatSync> {
  const s = await getSync(env, accountId);
  if (!s) throw new ApiError(409, 'chat_not_enabled', 'KI-Chat-Verbindung ist nicht eingeschaltet');
  return s;
}

export async function chatStatus(env: Env, accountId: string) {
  const s = await getSync(env, accountId);
  if (!s) return { enabled: false, photos: false, connections: [], inboxPending: 0, docs: {}, bytes: 0, lastSyncAt: null };
  const counts = await env.DB.prepare(
    'SELECT kind, COUNT(*) AS c, COALESCE(SUM(size), 0) AS b FROM chat_docs WHERE account_id = ? GROUP BY kind',
  )
    .bind(accountId)
    .all<{ kind: string; c: number; b: number }>();
  const grants = await env.DB.prepare(
    'SELECT id, client_name, created_at, last_used_at FROM oauth_grants WHERE account_id = ? ORDER BY created_at DESC',
  )
    .bind(accountId)
    .all<{ id: string; client_name: string | null; created_at: number; last_used_at: number | null }>();
  const inbox = await env.DB.prepare('SELECT COUNT(*) AS c FROM chat_inbox WHERE account_id = ?').bind(accountId).first<{ c: number }>();
  const docs: Record<string, number> = {};
  let bytes = 0;
  for (const r of counts.results) {
    docs[r.kind] = r.c;
    bytes += r.b;
  }
  return {
    enabled: true,
    photos: s.photosEnabled,
    enabledAt: s.enabledAt,
    lastSyncAt: s.lastSyncAt,
    docs,
    bytes,
    inboxPending: inbox?.c ?? 0,
    connections: grants.results.map((g) => ({
      id: g.id,
      clientName: g.client_name ?? 'KI-Chat',
      createdAt: g.created_at,
      lastUsedAt: g.last_used_at,
    })),
  };
}

export async function enableChat(env: Env, accountId: string, body: Record<string, unknown>) {
  const photos = body.photos === true;
  const existing = await getSync(env, accountId);
  if (!existing) {
    await env.DB.prepare('INSERT INTO chat_sync (account_id, photos_enabled, enabled_at) VALUES (?, ?, ?)')
      .bind(accountId, photos ? 1 : 0, now())
      .run();
  } else {
    await env.DB.prepare('UPDATE chat_sync SET photos_enabled = ? WHERE account_id = ?').bind(photos ? 1 : 0, accountId).run();
    if (existing.photosEnabled && !photos) await deleteAllPhotos(env, accountId);
  }
  return chatStatus(env, accountId);
}

async function deleteAllPhotos(env: Env, accountId: string): Promise<void> {
  let cursor: string | undefined;
  do {
    const list = await env.PHOTOS.list({ prefix: `${accountId}/`, cursor });
    if (list.objects.length) await env.PHOTOS.delete(list.objects.map((o) => o.key));
    cursor = list.truncated ? list.cursor : undefined;
  } while (cursor);
  await env.DB.prepare("DELETE FROM chat_docs WHERE account_id = ? AND kind = 'photo'").bind(accountId).run();
}

/** Abschalten: löscht serverseitig alles zur KI-Chat-Verbindung (Daten, Fotos, Eingang, Kopplungscodes, Tokens). */
export async function disableChat(env: Env, accountId: string) {
  await deleteAllPhotos(env, accountId);
  const grants = await env.DB.prepare('SELECT id FROM oauth_grants WHERE account_id = ?').bind(accountId).all<{ id: string }>();
  const stmts = grants.results.flatMap((g) => revokeStatements(env, g.id));
  await env.DB.batch([
    ...stmts,
    env.DB.prepare('DELETE FROM chat_docs WHERE account_id = ?').bind(accountId),
    env.DB.prepare('DELETE FROM chat_inbox WHERE account_id = ?').bind(accountId),
    env.DB.prepare('DELETE FROM chat_pairing WHERE account_id = ?').bind(accountId),
    env.DB.prepare('DELETE FROM chat_sync WHERE account_id = ?').bind(accountId),
  ]);
  return { enabled: false, deleted: true };
}

export function revokeStatements(env: Env, grantId: string): D1PreparedStatement[] {
  return [
    env.DB.prepare('DELETE FROM oauth_tokens WHERE grant_id = ?').bind(grantId),
    env.DB.prepare('DELETE FROM oauth_codes WHERE grant_id = ?').bind(grantId),
    env.DB.prepare('DELETE FROM oauth_grants WHERE id = ?').bind(grantId),
  ];
}

export async function revokeConnection(env: Env, accountId: string, body: Record<string, unknown>) {
  const all = body.all === true;
  const q = all
    ? env.DB.prepare('SELECT id FROM oauth_grants WHERE account_id = ?').bind(accountId)
    : env.DB.prepare('SELECT id FROM oauth_grants WHERE account_id = ? AND id = ?').bind(accountId, String(body.grantId ?? ''));
  const rows = await q.all<{ id: string }>();
  if (!all && rows.results.length === 0) throw new ApiError(404, 'not_found', 'Verbindung unbekannt');
  if (rows.results.length) await env.DB.batch(rows.results.flatMap((r) => revokeStatements(env, r.id)));
  return { revoked: rows.results.length };
}

interface Upsert {
  kind: DocKind;
  id: string;
  updatedAt: number;
  data: Record<string, unknown>;
}

function parseUpsert(v: unknown, cfg: Config): { u: Upsert; bytes: Uint8Array } {
  const o = v as Record<string, unknown>;
  if (!o || typeof o !== 'object') throw new ApiError(400, 'invalid_request', 'upserts[] muss Objekte enthalten');
  const kind = String(o.kind) as DocKind;
  const id = String(o.id ?? '');
  if (!(DOC_KINDS as readonly string[]).includes(kind)) throw new ApiError(400, 'invalid_request', `Unbekannte Art: ${kind}`);
  if (!ID_RE.test(id)) throw new ApiError(400, 'invalid_request', `Ungültige ID: ${id}`);
  if (kind === 'profile' && id !== 'me') throw new ApiError(400, 'invalid_request', 'profile hat die ID "me"');
  if (kind === 'memory' && id !== 'coach') throw new ApiError(400, 'invalid_request', 'memory hat die ID "coach"');
  const updatedAt = Number(o.updatedAt);
  if (!Number.isSafeInteger(updatedAt) || updatedAt < 0) throw new ApiError(400, 'invalid_request', 'updatedAt (ms) fehlt');
  const data = o.data as Record<string, unknown>;
  if (!data || typeof data !== 'object' || Array.isArray(data)) throw new ApiError(400, 'invalid_request', 'data muss ein Objekt sein');
  if (kind === 'workout' && !Number.isFinite(Number(data.startedAt))) {
    throw new ApiError(400, 'invalid_request', 'workout.data.startedAt (ms) fehlt');
  }
  const bytes = new TextEncoder().encode(JSON.stringify(data));
  if (bytes.length > cfg.chatMaxDocBytes) throw new ApiError(413, 'doc_too_large', `Dokument ${kind}/${id} zu groß`);
  return { u: { kind, id, updatedAt, data }, bytes };
}

function parseKey(v: unknown): { kind: string; id: string } {
  const o = v as Record<string, unknown>;
  const kind = String(o?.kind);
  const id = String(o?.id ?? '');
  if (!(DOC_KINDS as readonly string[]).includes(kind) || !ID_RE.test(id)) throw new ApiError(400, 'invalid_request', 'deletes[] ungültig');
  return { kind, id };
}

/** Upload von Snapshot (reset: true im ersten Paket) oder Inkrementen. Älteres updatedAt überschreibt nichts. */
export async function syncDocs(env: Env, cfg: Config, accountId: string, body: Record<string, unknown>) {
  await requireSync(env, accountId);
  const upserts = Array.isArray(body.upserts) ? body.upserts : [];
  const deletes = Array.isArray(body.deletes) ? body.deletes : [];
  if (upserts.length + deletes.length > MAX_BATCH) throw new ApiError(400, 'invalid_request', `Höchstens ${MAX_BATCH} Einträge je Anfrage`);
  const reset = body.reset === true;
  const parsed = upserts.map((u) => parseUpsert(u, cfg));
  const dels = deletes.map(parseKey);

  // Bestehende Zeilen der betroffenen Schlüssel (für Konflikt- und Größenprüfung)
  const keys = parsed.map((p) => `${p.u.kind}/${p.u.id}`);
  const existing = new Map<string, { size: number; updated_at: number }>();
  if (!reset && keys.length) {
    const rows = await env.DB.prepare(
      `SELECT kind, id, size, updated_at FROM chat_docs WHERE account_id = ? AND kind != 'photo'
       AND (kind || '/' || id) IN (SELECT value FROM json_each(?))`,
    )
      .bind(accountId, JSON.stringify(keys))
      .all<{ kind: string; id: string; size: number; updated_at: number }>();
    for (const r of rows.results) existing.set(`${r.kind}/${r.id}`, r);
  }
  const totals = reset
    ? { c: 0, b: 0 }
    : ((await env.DB.prepare("SELECT COUNT(*) AS c, COALESCE(SUM(size), 0) AS b FROM chat_docs WHERE account_id = ? AND kind != 'photo'")
        .bind(accountId)
        .first<{ c: number; b: number }>()) ?? { c: 0, b: 0 });

  const stmts: D1PreparedStatement[] = [];
  if (reset) stmts.push(env.DB.prepare("DELETE FROM chat_docs WHERE account_id = ? AND kind != 'photo'").bind(accountId));
  let count = totals.c;
  let bytes = totals.b;
  let applied = 0;
  let skipped = 0;
  const seen = new Set<string>();
  for (const { u, bytes: plain } of parsed) {
    const key = `${u.kind}/${u.id}`;
    const old = existing.get(key);
    if (old && old.updated_at > u.updatedAt) {
      skipped++;
      continue;
    }
    if (old) {
      bytes -= old.size;
    } else if (!seen.has(key)) count++;
    seen.add(key);
    bytes += plain.length;
    const blob = await seal(env, cfg, accountId, key, plain);
    const sortKey = u.kind === 'workout' ? Math.trunc(Number(u.data.startedAt)) : 0;
    const ref = u.kind === 'workout' && typeof u.data.templateId === 'string' ? u.data.templateId : null;
    const summary =
      u.kind === 'workout' ? await sealJson(env, cfg, accountId, `summary/${u.id}`, summarizeWorkout(u.id, u.data)) : null;
    stmts.push(
      env.DB.prepare(
        `INSERT INTO chat_docs (account_id, kind, id, updated_at, sort_key, ref, size, data, summary) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
         ON CONFLICT (account_id, kind, id) DO UPDATE SET updated_at = excluded.updated_at, sort_key = excluded.sort_key,
         ref = excluded.ref, size = excluded.size, data = excluded.data, summary = excluded.summary`,
      ).bind(accountId, u.kind, u.id, u.updatedAt, sortKey, ref, plain.length, blob, summary),
    );
    applied++;
  }
  for (const d of dels) {
    stmts.push(env.DB.prepare('DELETE FROM chat_docs WHERE account_id = ? AND kind = ? AND id = ?').bind(accountId, d.kind, d.id));
  }
  if (count > cfg.chatMaxDocs || bytes > cfg.chatMaxBytes) {
    throw new ApiError(413, 'sync_quota_exceeded', 'Speichergrenze der KI-Chat-Verbindung erreicht', {
      maxDocs: cfg.chatMaxDocs,
      maxBytes: cfg.chatMaxBytes,
    });
  }
  stmts.push(env.DB.prepare('UPDATE chat_sync SET last_sync_at = ? WHERE account_id = ?').bind(now(), accountId));
  await env.DB.batch(stmts);
  return { applied, skipped, deleted: dels.length, lastSyncAt: now() };
}

export async function putPhoto(env: Env, cfg: Config, accountId: string, photoId: string, url: URL, body: Uint8Array) {
  const s = await requireSync(env, accountId);
  if (!s.photosEnabled) throw new ApiError(403, 'photos_not_enabled', 'Fotos sind für den KI-Chat nicht freigegeben');
  if (!ID_RE.test(photoId)) throw new ApiError(400, 'invalid_request', 'Ungültige Foto-ID');
  if (body.length === 0 || body.length > cfg.chatMaxPhotoBytes) throw new ApiError(413, 'photo_too_large', 'Foto fehlt oder ist zu groß');
  const isJpeg = body[0] === 0xff && body[1] === 0xd8;
  const isPng = body[0] === 0x89 && body[1] === 0x50;
  if (!isJpeg && !isPng) throw new ApiError(400, 'invalid_request', 'Nur JPEG oder PNG');
  const takenAt = Number(url.searchParams.get('takenAt'));
  if (!Number.isSafeInteger(takenAt)) throw new ApiError(400, 'invalid_request', 'takenAt (ms) fehlt');
  const workoutId = url.searchParams.get('workoutId');
  const existing = await env.DB.prepare("SELECT 1 AS x FROM chat_docs WHERE account_id = ? AND kind = 'photo' AND id = ?")
    .bind(accountId, photoId)
    .first();
  if (!existing) {
    const c = await env.DB.prepare("SELECT COUNT(*) AS c FROM chat_docs WHERE account_id = ? AND kind = 'photo'")
      .bind(accountId)
      .first<{ c: number }>();
    if ((c?.c ?? 0) >= cfg.chatMaxPhotos) throw new ApiError(413, 'sync_quota_exceeded', 'Zu viele Fotos', { maxPhotos: cfg.chatMaxPhotos });
  }
  const meta = {
    id: photoId,
    takenAt,
    workoutId: workoutId && ID_RE.test(workoutId) ? workoutId : null,
    mimeType: isJpeg ? 'image/jpeg' : 'image/png',
    size: body.length,
    sha256: hex(await sha256(body)),
  };
  await env.PHOTOS.put(`${accountId}/${photoId}`, await seal(env, cfg, accountId, `photo-bytes/${photoId}`, body));
  const blob = await sealJson(env, cfg, accountId, `photo/${photoId}`, meta);
  await env.DB.prepare(
    `INSERT INTO chat_docs (account_id, kind, id, updated_at, sort_key, ref, size, data) VALUES (?, 'photo', ?, ?, ?, ?, ?, ?)
     ON CONFLICT (account_id, kind, id) DO UPDATE SET updated_at = excluded.updated_at, sort_key = excluded.sort_key,
     ref = excluded.ref, size = excluded.size, data = excluded.data`,
  )
    .bind(accountId, photoId, Date.now(), takenAt, meta.workoutId, body.length, blob)
    .run();
  return meta;
}

export async function deletePhoto(env: Env, accountId: string, photoId: string) {
  await requireSync(env, accountId);
  await env.PHOTOS.delete(`${accountId}/${photoId}`);
  await env.DB.prepare("DELETE FROM chat_docs WHERE account_id = ? AND kind = 'photo' AND id = ?").bind(accountId, photoId).run();
  return { deleted: true };
}

function pairingCode(): string {
  const bytes = crypto.getRandomValues(new Uint8Array(8));
  let s = '';
  for (const b of bytes) s += PAIR_ALPHABET[b % PAIR_ALPHABET.length];
  return `${s.slice(0, 4)}-${s.slice(4)}`;
}

export function normalizePairingCode(input: string): string | null {
  const s = input.toUpperCase().replace(/[\s-]/g, '');
  if (s.length !== 8 || [...s].some((c) => !PAIR_ALPHABET.includes(c))) return null;
  return s;
}

export async function hashPairing(normalized: string): Promise<string> {
  return hex(await sha256(`overfit-pair:${normalized}`));
}

/** Kopplungscode für die Anmeldung im Chat: 8 Zeichen, 10 min gültig, einmalig. Ersetzt einen älteren Code. */
export async function createPairing(env: Env, accountId: string) {
  await requireSync(env, accountId);
  const code = pairingCode();
  const expiresAt = now() + PAIRING_TTL_SEC;
  await env.DB.batch([
    env.DB.prepare('DELETE FROM chat_pairing WHERE account_id = ? OR expires_at < ?').bind(accountId, now()),
    env.DB.prepare('INSERT INTO chat_pairing (code_hash, account_id, expires_at) VALUES (?, ?, ?)').bind(
      await hashPairing(normalizePairingCode(code)!),
      accountId,
      expiresAt,
    ),
  ]);
  return { code, expiresAt, ttlSec: PAIRING_TTL_SEC };
}

export async function listInbox(env: Env, cfg: Config, accountId: string) {
  await requireSync(env, accountId);
  const rows = await env.DB.prepare(
    'SELECT id, kind, created_at, client_name, data FROM chat_inbox WHERE account_id = ? ORDER BY created_at, rowid',
  )
    .bind(accountId)
    .all<{ id: string; kind: string; created_at: number; client_name: string | null; data: ArrayBuffer }>();
  const items = [];
  for (const r of rows.results) {
    try {
      const payload = await openJson<Record<string, unknown>>(env, cfg, accountId, `inbox/${r.id}`, r.data);
      items.push({ id: r.id, kind: r.kind, createdAt: r.created_at, source: { clientName: r.client_name ?? 'KI-Chat' }, ...payload });
    } catch {
      /* unlesbar (Schlüsselwechsel) → überspringen */
    }
  }
  return { items };
}

export async function ackInbox(env: Env, accountId: string, body: Record<string, unknown>) {
  const ids = Array.isArray(body.ids) ? body.ids.map(String).slice(0, 200) : [];
  if (!ids.length) return { removed: 0 };
  const r = await env.DB.prepare('DELETE FROM chat_inbox WHERE account_id = ? AND id IN (SELECT value FROM json_each(?))')
    .bind(accountId, JSON.stringify(ids))
    .run();
  return { removed: r.meta.changes ?? 0 };
}
