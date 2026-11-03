import { describe, expect, it, vi } from 'vitest';
import worker from '../src/index';
import { Client, baseEnv } from './helpers';
import { b64uEncode, sha256 } from '../src/crypto';
import { decodePayload } from '../src/chat/share';
import type { Env } from '../src/types';

const ctx = { waitUntil() {}, passThroughOnException() {} } as unknown as ExecutionContext;
const BASE = 'https://x.test';
const REDIRECT = 'https://claude.ai/api/mcp/auth_callback';

function req(path: string, e: Env, init?: RequestInit) {
  return worker.fetch(new Request(BASE + path, init), e, ctx);
}

const DAY = 86_400_000;
const T0 = Date.UTC(2026, 8, 1, 17, 0); // 1.9.2026

function workout(id: string, dayOffset: number, benchKg: number, opts: { running?: boolean; templateId?: string } = {}) {
  const startedAt = T0 + dayOffset * DAY;
  return {
    kind: 'workout',
    id,
    updatedAt: startedAt + 3_600_000,
    data: {
      name: 'Push',
      templateId: opts.templateId ?? 'tpl-push',
      startedAt,
      endedAt: opts.running ? null : startedAt + 3_600_000,
      feeling: 4,
      note: 'Geheime Notiz',
      exercises: [
        {
          exerciseId: 'wg:bench-press',
          name: 'Bench Press',
          trackingType: 'WEIGHT_REPS',
          sets: [
            { kg: benchKg, reps: 5, completed: true },
            { kg: benchKg, reps: 5, completed: true },
            { kg: benchKg, reps: 4, completed: !opts.running },
          ],
        },
        { exerciseId: 'c-uuid-1', name: 'Handstand', trackingType: 'TIME', sets: [{ sec: 30, completed: true }] },
      ],
    },
  };
}

async function setup(opts: { photos?: boolean } = {}) {
  const e = baseEnv();
  const c = await Client.create(e);
  expect((await c.call('POST', '/v1/chat/enable', { photos: opts.photos ?? false })).status).toBe(200);
  const r = await c.call('POST', '/v1/chat/sync', {
    reset: true,
    upserts: [
      { kind: 'profile', id: 'me', updatedAt: 1, data: { name: 'Fabian', birthYear: 1995, unit: 'kg', language: 'de', goals: 'Kraft', bodyweight: [{ date: '2026-09-01', kg: 80 }] } },
      { kind: 'memory', id: 'coach', updatedAt: 1, data: { text: 'Mag Grundübungen.' } },
      {
        kind: 'template',
        id: 'tpl-push',
        updatedAt: 1,
        data: { name: 'Push', exercises: [{ exerciseId: 'wg:bench-press', name: 'Bench Press', trackingType: 'WEIGHT_REPS', restSec: 150, sets: [{ kg: 80, reps: 5 }] }] },
      },
      workout('w1', 0, 80),
      workout('w2', 3, 82.5),
      workout('w3', 7, 85, { running: true }),
    ],
  });
  expect(r.status).toBe(200);
  return { e, c };
}

async function pkce() {
  const verifier = b64uEncode(crypto.getRandomValues(new Uint8Array(32)));
  return { verifier, challenge: b64uEncode(await sha256(verifier)) };
}

async function register(e: Env, extra: Record<string, unknown> = {}) {
  const res = await req('/oauth/register', e, {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ client_name: 'Claude', redirect_uris: [REDIRECT], token_endpoint_auth_method: 'none', ...extra }),
  });
  expect(res.status).toBe(201);
  return (await res.json()) as { client_id: string; client_secret?: string };
}

function authForm(clientId: string, challenge: string, code: string, extra: Record<string, string> = {}) {
  return new URLSearchParams({
    response_type: 'code',
    client_id: clientId,
    redirect_uri: REDIRECT,
    code_challenge: challenge,
    code_challenge_method: 'S256',
    state: 'st4te',
    scope: 'overfit',
    resource: `${BASE}/mcp`,
    pairing_code: code,
    action: 'allow',
    ...extra,
  });
}

async function connect(e: Env, c: Client) {
  const client = await register(e);
  const { verifier, challenge } = await pkce();
  const pairing = (await c.call('POST', '/v1/chat/pairing')).body;
  expect(pairing.code).toMatch(/^[A-Z2-9]{4}-[A-Z2-9]{4}$/);
  const res = await req('/oauth/authorize', e, {
    method: 'POST',
    headers: { 'content-type': 'application/x-www-form-urlencoded' },
    body: authForm(client.client_id, challenge, pairing.code.toLowerCase().replace('-', ' ')),
    redirect: 'manual',
  });
  expect(res.status).toBe(302);
  const loc = new URL(res.headers.get('location')!);
  expect(loc.origin + loc.pathname).toBe(REDIRECT);
  expect(loc.searchParams.get('state')).toBe('st4te');
  expect(loc.searchParams.get('iss')).toBe(BASE);
  const code = loc.searchParams.get('code')!;
  const tok = await req('/oauth/token', e, {
    method: 'POST',
    headers: { 'content-type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({ grant_type: 'authorization_code', code, redirect_uri: REDIRECT, client_id: client.client_id, code_verifier: verifier }),
  });
  expect(tok.status).toBe(200);
  const t = (await tok.json()) as { access_token: string; refresh_token: string; expires_in: number };
  return { ...t, clientId: client.client_id, code, verifier };
}

let rpcId = 1;
async function mcp(e: Env, token: string | null, method: string, params?: unknown) {
  const res = await req('/mcp', e, {
    method: 'POST',
    headers: { 'content-type': 'application/json', accept: 'application/json, text/event-stream', ...(token ? { authorization: `Bearer ${token}` } : {}) },
    body: JSON.stringify({ jsonrpc: '2.0', id: rpcId++, method, params }),
  });
  return { status: res.status, headers: res.headers, body: res.status === 202 ? null : ((await res.json()) as any) };
}

async function tool(e: Env, token: string, name: string, args: Record<string, unknown> = {}) {
  const r = await mcp(e, token, 'tools/call', { name, arguments: args });
  expect(r.status).toBe(200);
  return r.body.result as { content: any[]; structuredContent?: any; isError?: boolean };
}

describe('KI-Chat: Synchronisation', () => {
  it('ist ohne Einschalten gesperrt und zeigt den Status', async () => {
    const c = await Client.create();
    expect((await c.call('GET', '/v1/chat')).body.enabled).toBe(false);
    const r = await c.call('POST', '/v1/chat/sync', { upserts: [] });
    expect(r.status).toBe(409);
    expect(r.body.error.code).toBe('chat_not_enabled');
    expect((await c.call('POST', '/v1/chat/pairing')).body.error.code).toBe('chat_not_enabled');
  });

  it('speichert Dokumente verschlüsselt und ignoriert ältere Stände', async () => {
    const { e, c } = await setup();
    const st = (await c.call('GET', '/v1/chat')).body;
    expect(st).toMatchObject({ enabled: true, photos: false, docs: { profile: 1, memory: 1, template: 1, workout: 3 } });
    const rows = await e.DB.prepare('SELECT data FROM chat_docs').all<{ data: ArrayBuffer }>();
    const all = rows.results.map((r) => new TextDecoder().decode(new Uint8Array(r.data))).join('');
    expect(all).not.toContain('Geheime Notiz');
    expect(all).not.toContain('Fabian');
    const older = await c.call('POST', '/v1/chat/sync', { upserts: [{ kind: 'memory', id: 'coach', updatedAt: 0, data: { text: 'alt' } }] });
    expect(older.body).toMatchObject({ applied: 0, skipped: 1 });
    const del = await c.call('POST', '/v1/chat/sync', { deletes: [{ kind: 'workout', id: 'w1' }] });
    expect(del.body.deleted).toBe(1);
    expect((await c.call('GET', '/v1/chat')).body.docs.workout).toBe(2);
  });

  it('prüft Eingaben und Speichergrenzen', async () => {
    const e = baseEnv({ CHAT_MAX_DOCS: '2' });
    const c = await Client.create(e);
    await c.call('POST', '/v1/chat/enable', {});
    const bad = await c.call('POST', '/v1/chat/sync', { upserts: [{ kind: 'secret', id: 'x', updatedAt: 1, data: {} }] });
    expect(bad.status).toBe(400);
    const noStart = await c.call('POST', '/v1/chat/sync', { upserts: [{ kind: 'workout', id: 'x', updatedAt: 1, data: {} }] });
    expect(noStart.status).toBe(400);
    const full = await c.call('POST', '/v1/chat/sync', { upserts: [workout('a', 0, 1), workout('b', 1, 1), workout('c', 2, 1)] });
    expect(full.status).toBe(413);
    expect(full.body.error.code).toBe('sync_quota_exceeded');
    expect((await c.call('GET', '/v1/chat')).body.docs).toEqual({});
  });

  it('Abschalten löscht alles serverseitig und trennt alle Chats', async () => {
    const { e, c } = await setup({ photos: true });
    const jpeg = new Uint8Array([0xff, 0xd8, 0xff, 0xe0, 1, 2, 3, 4]);
    const put = await c.callRaw('PUT', '/v1/chat/photos/p1?takenAt=' + T0, jpeg);
    expect(put.status).toBe(200);
    const { access_token } = await connect(e, c);
    expect((await mcp(e, access_token, 'tools/list')).status).toBe(200);
    expect((await c.call('POST', '/v1/chat/disable')).body.deleted).toBe(true);
    for (const t of ['chat_docs', 'chat_inbox', 'oauth_grants', 'oauth_tokens', 'chat_sync']) {
      expect((await e.DB.prepare(`SELECT COUNT(*) AS c FROM ${t}`).first<{ c: number }>())!.c).toBe(0);
    }
    expect((await e.PHOTOS.list({ prefix: (await c.call('GET', '/v1/account')).body.accountId })).objects.length).toBe(0);
    expect((await mcp(e, access_token, 'tools/list')).status).toBe(401);
  });
});

describe('KI-Chat: OAuth', () => {
  it('liefert Metadaten und 401 mit Verweis darauf', async () => {
    const e = baseEnv();
    const prm = (await (await req('/.well-known/oauth-protected-resource', e)).json()) as any;
    expect(prm.resource).toBe(`${BASE}/mcp`);
    expect(prm.authorization_servers).toEqual([BASE]);
    const as = (await (await req('/.well-known/oauth-authorization-server', e)).json()) as any;
    expect(as.code_challenge_methods_supported).toEqual(['S256']);
    expect(as.registration_endpoint).toBe(`${BASE}/oauth/register`);
    const r = await mcp(e, null, 'initialize', {});
    expect(r.status).toBe(401);
    expect(r.headers.get('www-authenticate')).toContain(`resource_metadata="${BASE}/.well-known/oauth-protected-resource"`);
    expect((await mcp(e, 'ovf_at_falsch', 'tools/list')).status).toBe(401);
  });

  it('Anmeldeseite zeigt Client und Ziel, falscher Code wird abgewiesen', async () => {
    const { e, c } = await setup();
    const client = await register(e, { client_name: '<script>x</script>' });
    const { challenge } = await pkce();
    const q = authForm(client.client_id, challenge, '');
    q.delete('pairing_code');
    q.delete('action');
    const page = await req('/oauth/authorize?' + q, e, { headers: { 'accept-language': 'de-DE' } });
    expect(page.status).toBe(200);
    const html = await page.text();
    expect(html).toContain('&lt;script&gt;');
    expect(html).not.toContain('<script>');
    expect(html).toContain('claude.ai');
    expect(html).toContain('Kopplungscode');
    expect(page.headers.get('content-security-policy')).toContain("frame-ancestors 'none'");
    const wrong = await req('/oauth/authorize', e, {
      method: 'POST',
      headers: { 'content-type': 'application/x-www-form-urlencoded' },
      body: authForm(client.client_id, challenge, 'AAAA-AAAA'),
      redirect: 'manual',
    });
    expect(wrong.status).toBe(400);
    void c;
  });

  it('weist fremde redirect_uri und fehlendes PKCE ab', async () => {
    const e = baseEnv();
    const client = await register(e);
    const bad = await req(`/oauth/authorize?response_type=code&client_id=${client.client_id}&redirect_uri=https://evil.test/cb&code_challenge=x&code_challenge_method=S256`, e, { redirect: 'manual' });
    expect(bad.status).toBe(400);
    expect(bad.headers.get('location')).toBeNull();
    const noPkce = await req(`/oauth/authorize?response_type=code&client_id=${client.client_id}&redirect_uri=${encodeURIComponent(REDIRECT)}&state=s`, e, { redirect: 'manual' });
    expect(noPkce.status).toBe(302);
    expect(new URL(noPkce.headers.get('location')!).searchParams.get('error')).toBe('invalid_request');
    const reg = await req('/oauth/register', e, { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify({ redirect_uris: ['http://evil.test/cb'] }) });
    expect(reg.status).toBe(400);
  });

  it('Token-Tausch: PKCE, Einmal-Code, Refresh-Rotation, Widerruf aus der App', async () => {
    const { e, c } = await setup();
    const t = await connect(e, c);
    const post = (body: Record<string, string>) =>
      req('/oauth/token', e, { method: 'POST', headers: { 'content-type': 'application/x-www-form-urlencoded' }, body: new URLSearchParams(body) });
    // Code ist verbraucht
    const again = await post({ grant_type: 'authorization_code', code: t.code, client_id: t.clientId, code_verifier: t.verifier, redirect_uri: REDIRECT });
    expect(((await again.json()) as any).error).toBe('invalid_grant');
    // Refresh rotiert
    const r1 = await post({ grant_type: 'refresh_token', refresh_token: t.refresh_token, client_id: t.clientId });
    expect(r1.status).toBe(200);
    const n = (await r1.json()) as any;
    expect(n.refresh_token).not.toBe(t.refresh_token);
    expect((await post({ grant_type: 'refresh_token', refresh_token: t.refresh_token, client_id: t.clientId })).status).toBe(400);
    expect((await mcp(e, n.access_token, 'ping')).status).toBe(200);
    // App sieht und trennt die Verbindung
    const st = (await c.call('GET', '/v1/chat')).body;
    expect(st.connections).toHaveLength(1);
    expect(st.connections[0].clientName).toBe('Claude');
    expect((await c.call('POST', '/v1/chat/connections/revoke', { grantId: st.connections[0].id })).body.revoked).toBe(1);
    expect((await mcp(e, n.access_token, 'ping')).status).toBe(401);
    expect((await post({ grant_type: 'refresh_token', refresh_token: n.refresh_token, client_id: t.clientId })).status).toBe(400);
  });

  it('falscher code_verifier und Kopplungscode nur einmal', async () => {
    const { e, c } = await setup();
    const client = await register(e);
    const { challenge } = await pkce();
    const pairing = (await c.call('POST', '/v1/chat/pairing')).body;
    const form = () => ({ method: 'POST', headers: { 'content-type': 'application/x-www-form-urlencoded' }, body: authForm(client.client_id, challenge, pairing.code), redirect: 'manual' as const });
    const ok = await req('/oauth/authorize', e, form());
    expect(ok.status).toBe(302);
    expect((await req('/oauth/authorize', e, form())).status).toBe(400);
    const code = new URL(ok.headers.get('location')!).searchParams.get('code')!;
    const tok = await req('/oauth/token', e, {
      method: 'POST',
      headers: { 'content-type': 'application/x-www-form-urlencoded' },
      body: new URLSearchParams({ grant_type: 'authorization_code', code, client_id: client.client_id, code_verifier: 'x'.repeat(43) }),
    });
    expect(((await tok.json()) as any).error).toBe('invalid_grant');
  });

  it('vertrauliche Clients brauchen ihr Secret (Basic-Auth)', async () => {
    const { e, c } = await setup();
    const client = await register(e, { token_endpoint_auth_method: 'client_secret_basic', client_name: 'ChatGPT' });
    expect(client.client_secret).toBeTruthy();
    const { verifier, challenge } = await pkce();
    const pairing = (await c.call('POST', '/v1/chat/pairing')).body;
    const ok = await req('/oauth/authorize', e, { method: 'POST', headers: { 'content-type': 'application/x-www-form-urlencoded' }, body: authForm(client.client_id, challenge, pairing.code), redirect: 'manual' });
    const code = new URL(ok.headers.get('location')!).searchParams.get('code')!;
    const body = new URLSearchParams({ grant_type: 'authorization_code', code, code_verifier: verifier, redirect_uri: REDIRECT });
    const noAuth = await req('/oauth/token', e, { method: 'POST', headers: { 'content-type': 'application/x-www-form-urlencoded' }, body: new URLSearchParams({ ...Object.fromEntries(body), client_id: client.client_id }) });
    expect(noAuth.status).toBe(401);
    // Client-Prüfung kommt vor dem Code: der Code bleibt nach dem Fehlversuch gültig.
    const basic = btoa(`${client.client_id}:${client.client_secret}`);
    const tok = await req('/oauth/token', e, { method: 'POST', headers: { 'content-type': 'application/x-www-form-urlencoded', authorization: `Basic ${basic}` }, body });
    expect(tok.status).toBe(200);
  });

  it('Client ID Metadata Document (client_id als URL)', async () => {
    const { e, c } = await setup();
    const cid = 'https://chat.example/oauth/client.json';
    const spy = vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      expect(String(input)).toBe(cid);
      return Response.json({ client_id: cid, client_name: 'Example Chat', redirect_uris: [REDIRECT] });
    });
    try {
      const { verifier, challenge } = await pkce();
      const pairing = (await c.call('POST', '/v1/chat/pairing')).body;
      const ok = await req('/oauth/authorize', e, { method: 'POST', headers: { 'content-type': 'application/x-www-form-urlencoded' }, body: authForm(cid, challenge, pairing.code), redirect: 'manual' });
      expect(ok.status).toBe(302);
      const code = new URL(ok.headers.get('location')!).searchParams.get('code')!;
      const tok = await req('/oauth/token', e, { method: 'POST', headers: { 'content-type': 'application/x-www-form-urlencoded' }, body: new URLSearchParams({ grant_type: 'authorization_code', code, client_id: cid, code_verifier: verifier }) });
      expect(tok.status).toBe(200);
      expect((await c.call('GET', '/v1/chat')).body.connections[0].clientName).toBe('Example Chat');
    } finally {
      spy.mockRestore();
    }
  });
});

describe('KI-Chat: MCP-Server', () => {
  it('initialize, tools/list, Notifications', async () => {
    const { e, c } = await setup();
    const { access_token } = await connect(e, c);
    const init = await mcp(e, access_token, 'initialize', { protocolVersion: '2025-06-18', capabilities: {}, clientInfo: { name: 't', version: '1' } });
    expect(init.body.result.protocolVersion).toBe('2025-06-18');
    expect(init.body.result.serverInfo.name).toBe('overfit');
    expect(init.body.result.instructions).toContain('create_template');
    const note = await req('/mcp', e, { method: 'POST', headers: { authorization: `Bearer ${access_token}`, 'content-type': 'application/json' }, body: JSON.stringify({ jsonrpc: '2.0', method: 'notifications/initialized' }) });
    expect(note.status).toBe(202);
    const tools = (await mcp(e, access_token, 'tools/list')).body.result.tools.map((t: any) => t.name);
    expect(tools).toEqual(expect.arrayContaining(['get_profile', 'list_templates', 'get_template', 'list_workouts', 'get_workout', 'get_current_workout', 'list_exercises', 'get_exercise_progress', 'search_exercise_catalog', 'list_photos', 'get_photo', 'create_template', 'add_note']));
    expect((await mcp(e, access_token, 'prompts/list')).body.result.prompts).toHaveLength(3);
    expect((await mcp(e, access_token, 'nope')).body.error.code).toBe(-32601);
    expect((await req('/mcp', e, { headers: { authorization: `Bearer ${access_token}` } })).status).toBe(405);
  });

  it('liest Profil, Verlauf, laufendes Training und Fortschritt', async () => {
    const { e, c } = await setup();
    const { access_token: t } = await connect(e, c);
    const p = (await tool(e, t, 'get_profile')).structuredContent;
    expect(p.profile.name).toBe('Fabian');
    expect(p.coachMemory).toBe('Mag Grundübungen.');
    expect(p.counts.workout).toBe(3);
    const list = (await tool(e, t, 'list_workouts', { limit: 10 })).structuredContent;
    expect(list.workouts.map((w: any) => w.workoutId)).toEqual(['w3', 'w2', 'w1']);
    expect(list.workouts[1]).toMatchObject({ setsDone: 4, setsPlanned: 4, volumeKg: 82.5 * 14, durationMin: 60, start: '2026-09-04 19:00' });
    const ranged = (await tool(e, t, 'list_workouts', { from: '2026-09-02', to: '2026-09-05' })).structuredContent;
    expect(ranged.workouts.map((w: any) => w.workoutId)).toEqual(['w2']);
    const cur = (await tool(e, t, 'get_current_workout')).structuredContent;
    expect(cur.workoutId).toBe('w3');
    const prog = (await tool(e, t, 'get_exercise_progress', { exercise_id: 'wg:bench-press' })).structuredContent;
    expect(prog.sessionsCount).toBe(3);
    expect(prog.bests.e1rmKg.value).toBe(Math.round(85 * (1 + 5 / 30) * 10) / 10);
    expect(prog.change.topKg).toBe(5);
    const byName = (await tool(e, t, 'get_exercise_progress', { name: 'handstand' })).structuredContent;
    expect(byName.bests.maxSec.value).toBe(30);
    const w = (await tool(e, t, 'get_workout', { workout_id: 'w1' })).structuredContent;
    expect(w.workout.note).toBe('Geheime Notiz');
    expect(w.stats.exercises[0].bestE1rmKg).toBe(93.3);
    const ex = (await tool(e, t, 'list_exercises')).structuredContent.exercises;
    expect(ex.find((x: any) => x.exerciseId === 'c-uuid-1')).toMatchObject({ custom: true, sessions: 3 });
    const missing = await tool(e, t, 'get_workout', { workout_id: 'nope' });
    expect(missing.isError).toBe(true);
    const cat = (await tool(e, t, 'search_exercise_catalog', { query: 'bench press' })).structuredContent.results;
    expect(cat.length).toBeGreaterThan(0);
    expect(cat[0].name.toLowerCase()).toContain('bench press');
  });

  it('create_template legt eine importierbare Vorlage in den Eingang', async () => {
    const { e, c } = await setup();
    const { access_token: t } = await connect(e, c);
    const cat = (await tool(e, t, 'search_exercise_catalog', { query: 'bench press' })).structuredContent.results[0];
    const r = await tool(e, t, 'create_template', {
      name: 'Push B',
      message: 'Mehr Volumen',
      replaces_template_id: 'tpl-push',
      exercises: [
        { exercise_id: cat.id, rest_sec: 180, sets: [{ kg: 87.5, reps: 5 }, { kg: 87.5, reps: 5 }] },
        { exercise_id: 'c-uuid-1', sets: [{ sec: 40 }] },
        { name: 'Dips', sets: [{ reps: 10 }] },
        { name: 'Pike Hold', tracking_type: 'TIME', sets: [{ sec: 20 }] },
      ],
    });
    expect(r.isError).toBeUndefined();
    const sc = r.structuredContent;
    expect(sc.appLink.startsWith('overfit://t#')).toBe(true);
    const decoded = (await decodePayload(sc.importLink.split('#')[1])) as any;
    expect(decoded).toMatchObject({ v: 1, kind: 'template', name: 'Push B' });
    expect(decoded.exercises[0]).toEqual({ id: cat.id, restSec: 180, sets: [{ kg: 87.5, reps: 5 }, { kg: 87.5, reps: 5 }] });
    expect(decoded.exercises[1].ref).toBe('c1');
    expect(decoded.exercises[2]).toEqual({ name: 'Dips', sets: [{ reps: 10 }] });
    expect(decoded.custom).toEqual([
      { ref: 'c1', name: 'Handstand', trackingType: 'TIME' },
      { ref: 'c2', name: 'Pike Hold', trackingType: 'TIME' },
    ]);
    const bad = await tool(e, t, 'create_template', { name: 'X', exercises: [{ exercise_id: 'wg:gibtsnicht', sets: [] }] });
    expect(bad.isError).toBe(true);

    const note = await tool(e, t, 'add_note', { title: 'Auswertung', text: 'Stark!', workout_id: 'w2', rating: 8 });
    expect(note.isError).toBeUndefined();

    const inbox = (await c.call('GET', '/v1/chat/inbox')).body.items;
    expect(inbox).toHaveLength(2);
    expect(inbox[0]).toMatchObject({ kind: 'template', source: { clientName: 'Claude' }, replacesTemplateId: 'tpl-push', message: 'Mehr Volumen' });
    expect(inbox[0].template.name).toBe('Push B');
    expect(inbox[1]).toMatchObject({ kind: 'note', note: { title: 'Auswertung', text: 'Stark!', workoutId: 'w2', rating: 8 } });
    expect((await c.call('POST', '/v1/chat/inbox/ack', { ids: inbox.map((i: any) => i.id) })).body.removed).toBe(2);
    expect((await c.call('GET', '/v1/chat/inbox')).body.items).toHaveLength(0);
  });

  it('Eingang hat eine Obergrenze', async () => {
    const e = baseEnv({ CHAT_INBOX_MAX: '1' });
    const c = await Client.create(e);
    await c.call('POST', '/v1/chat/enable', {});
    const { access_token: t } = await connect(e, c);
    expect((await tool(e, t, 'add_note', { text: 'a' })).isError).toBeUndefined();
    const full = await tool(e, t, 'add_note', { text: 'b' });
    expect(full.isError).toBe(true);
    expect(full.content[0].text).toContain('Eingang ist voll');
  });

  it('Fotos nur mit Freigabe, als Bildinhalt', async () => {
    const { e, c } = await setup({ photos: false });
    const jpeg = new Uint8Array([0xff, 0xd8, 0xff, 0xe0, 9, 8, 7, 6, 5]);
    const denied = await c.callRaw('PUT', `/v1/chat/photos/p1?takenAt=${T0}&workoutId=w1`, jpeg);
    expect(denied.status).toBe(403);
    const { access_token: t } = await connect(e, c);
    expect((await tool(e, t, 'list_photos')).structuredContent.photos).toEqual([]);
    await c.call('POST', '/v1/chat/enable', { photos: true });
    expect((await c.callRaw('PUT', `/v1/chat/photos/p1?takenAt=${T0}&workoutId=w1`, jpeg)).status).toBe(200);
    expect((await c.callRaw('PUT', `/v1/chat/photos/p2?takenAt=${T0}`, new Uint8Array([1, 2, 3]))).status).toBe(400);
    const photos = (await tool(e, t, 'list_photos')).structuredContent.photos;
    expect(photos).toEqual([{ photoId: 'p1', date: '2026-09-01 19:00', workoutId: 'w1' }]);
    const img = await tool(e, t, 'get_photo', { photo_id: 'p1' });
    const part = img.content.find((x: any) => x.type === 'image');
    expect(part.mimeType).toBe('image/jpeg');
    expect(Uint8Array.from(atob(part.data), (ch) => ch.charCodeAt(0))).toEqual(jpeg);
    // Foto-Freigabe zurücknehmen löscht die Fotos
    await c.call('POST', '/v1/chat/enable', { photos: false });
    expect((await tool(e, t, 'get_photo', { photo_id: 'p1' })).isError).toBe(true);
    expect((await e.DB.prepare("SELECT COUNT(*) AS c FROM chat_docs WHERE kind = 'photo'").first<{ c: number }>())!.c).toBe(0);
  });
});
