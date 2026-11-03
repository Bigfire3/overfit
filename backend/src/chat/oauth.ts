import type { Config, Env } from '../types';
import { b64uEncode, hex, randomId, sha256 } from '../crypto';
import { now } from '../ledger';
import { rateLimit, clientIp } from '../ratelimit';
import { getSync, hashPairing, normalizePairingCode, revokeStatements } from './sync';
import { authorizePage, errorPage } from './pages';

// OAuth 2.1 Autorisierungsserver für die KI-Chat-Verbindung (MCP-Autorisierung):
// RFC 8414 (Metadaten), RFC 9728 (Protected Resource Metadata), RFC 7591 (Dynamic Client Registration),
// Client ID Metadata Documents (client_id = https-URL), PKCE S256 Pflicht, RFC 8707 (resource), RFC 7009 (Revocation).
// Anmeldung ohne Konto: Der Nutzer tippt den Kopplungscode aus der App ein.

export const SCOPE = 'overfit';
export const ACCESS_TTL_SEC = 3600;
export const REFRESH_TTL_SEC = 90 * 24 * 3600;
const CODE_TTL_SEC = 300;

export function baseUrl(req: Request, cfg: Config): string {
  return cfg.publicBaseUrl ?? new URL(req.url).origin;
}

export function resourceUrl(req: Request, cfg: Config): string {
  return `${baseUrl(req, cfg)}/mcp`;
}

const noStore = { 'cache-control': 'no-store', pragma: 'no-cache' };

function oauthError(status: number, error: string, description: string): Response {
  return Response.json({ error, error_description: description }, { status, headers: noStore });
}

export function protectedResourceMetadata(req: Request, cfg: Config) {
  return {
    resource: resourceUrl(req, cfg),
    authorization_servers: [baseUrl(req, cfg)],
    scopes_supported: [SCOPE],
    bearer_methods_supported: ['header'],
    resource_name: 'Overfit',
    resource_documentation: 'https://github.com/Bigfire3/overfit/blob/main/docs/backend-api.md',
  };
}

export function authServerMetadata(req: Request, cfg: Config) {
  const b = baseUrl(req, cfg);
  return {
    issuer: b,
    authorization_endpoint: `${b}/oauth/authorize`,
    token_endpoint: `${b}/oauth/token`,
    registration_endpoint: `${b}/oauth/register`,
    revocation_endpoint: `${b}/oauth/revoke`,
    response_types_supported: ['code'],
    response_modes_supported: ['query'],
    grant_types_supported: ['authorization_code', 'refresh_token'],
    code_challenge_methods_supported: ['S256'],
    token_endpoint_auth_methods_supported: ['none', 'client_secret_post', 'client_secret_basic'],
    revocation_endpoint_auth_methods_supported: ['none', 'client_secret_post', 'client_secret_basic'],
    scopes_supported: [SCOPE],
    client_id_metadata_document_supported: true,
    service_documentation: 'https://github.com/Bigfire3/overfit/blob/main/backend/README.md',
  };
}

// ---------- Clients ----------

export interface Client {
  clientId: string;
  name: string | null;
  redirectUris: string[];
  secretHash: string | null;
}

function validRedirect(u: string): boolean {
  try {
    const url = new URL(u);
    if (url.hash) return false;
    if (url.protocol === 'https:') return true;
    return url.protocol === 'http:' && ['localhost', '127.0.0.1', '[::1]'].includes(url.hostname);
  } catch {
    return false;
  }
}

function cleanName(v: unknown): string | null {
  if (typeof v !== 'string') return null;
  const s = v.replace(/[\u0000-\u001f\u007f]/g, '').trim().slice(0, 60);
  return s || null;
}

export async function registerClient(req: Request, env: Env, cfg: Config): Promise<Response> {
  await rateLimit(env, `oreg:${clientIp(req)}`, 30, 3600);
  let body: Record<string, unknown>;
  try {
    body = (await req.json()) as Record<string, unknown>;
  } catch {
    return oauthError(400, 'invalid_client_metadata', 'Body muss JSON sein');
  }
  const uris = Array.isArray(body.redirect_uris) ? body.redirect_uris.map(String) : [];
  if (!uris.length || uris.length > 10 || !uris.every(validRedirect)) {
    return oauthError(400, 'invalid_redirect_uri', 'redirect_uris: https oder http://localhost, ohne Fragment');
  }
  const method = String(body.token_endpoint_auth_method ?? 'none');
  if (!['none', 'client_secret_post', 'client_secret_basic'].includes(method)) {
    return oauthError(400, 'invalid_client_metadata', 'token_endpoint_auth_method nicht unterstützt');
  }
  const clientId = `ovf_c_${randomId(16)}`;
  const secret = method === 'none' ? null : `ovf_cs_${randomId(32)}`;
  const name = cleanName(body.client_name);
  await env.DB.prepare(
    'INSERT INTO oauth_clients (client_id, client_secret_hash, client_name, redirect_uris, created_at) VALUES (?, ?, ?, ?, ?)',
  )
    .bind(clientId, secret ? hex(await sha256(secret)) : null, name, JSON.stringify(uris), now())
    .run();
  void cfg;
  return Response.json(
    {
      client_id: clientId,
      ...(secret ? { client_secret: secret, client_secret_expires_at: 0 } : {}),
      client_id_issued_at: now(),
      client_name: name ?? undefined,
      redirect_uris: uris,
      token_endpoint_auth_method: method,
      grant_types: ['authorization_code', 'refresh_token'],
      response_types: ['code'],
      scope: SCOPE,
    },
    { status: 201, headers: noStore },
  );
}

/** Client ID Metadata Document: client_id ist eine https-URL, die ein JSON mit client_id/redirect_uris liefert. */
async function fetchMetadataClient(clientId: string): Promise<Client | null> {
  let url: URL;
  try {
    url = new URL(clientId);
  } catch {
    return null;
  }
  if (url.protocol !== 'https:' || url.pathname === '/' || url.hash) return null;
  try {
    const res = await fetch(url.toString(), { headers: { accept: 'application/json' }, signal: AbortSignal.timeout(5000), redirect: 'error' });
    if (!res.ok) return null;
    const text = await res.text();
    if (text.length > 16 * 1024) return null;
    const doc = JSON.parse(text) as Record<string, unknown>;
    if (doc.client_id !== clientId) return null;
    const uris = Array.isArray(doc.redirect_uris) ? doc.redirect_uris.map(String).filter(validRedirect) : [];
    if (!uris.length) return null;
    return { clientId, name: cleanName(doc.client_name) ?? url.hostname, redirectUris: uris, secretHash: null };
  } catch {
    return null;
  }
}

export async function loadClient(env: Env, clientId: string): Promise<Client | null> {
  if (!clientId || clientId.length > 512) return null;
  if (clientId.startsWith('https://')) return fetchMetadataClient(clientId);
  const r = await env.DB.prepare('SELECT client_id, client_secret_hash, client_name, redirect_uris FROM oauth_clients WHERE client_id = ?')
    .bind(clientId)
    .first<{ client_id: string; client_secret_hash: string | null; client_name: string | null; redirect_uris: string }>();
  if (!r) return null;
  return { clientId: r.client_id, name: r.client_name, redirectUris: JSON.parse(r.redirect_uris) as string[], secretHash: r.client_secret_hash };
}

// ---------- Autorisierung ----------

interface AuthParams {
  clientId: string;
  redirectUri: string;
  state: string | null;
  codeChallenge: string;
  resource: string | null;
  scope: string;
}

function lang(req: Request): 'de' | 'en' {
  const first = (req.headers.get('accept-language') ?? '').split(',')[0].trim().toLowerCase();
  return first.startsWith('de') ? 'de' : 'en';
}

function redirectWith(redirectUri: string, params: Record<string, string | null>): Response {
  const u = new URL(redirectUri);
  for (const [k, v] of Object.entries(params)) if (v !== null) u.searchParams.set(k, v);
  return new Response(null, { status: 302, headers: { location: u.toString(), ...noStore } });
}

/** Prüft Client und redirect_uri (bei Fehler: Fehlerseite statt Weiterleitung) und danach die übrigen Parameter. */
async function validateAuth(
  req: Request,
  cfg: Config,
  env: Env,
  p: URLSearchParams,
): Promise<{ ok: true; params: AuthParams; client: Client } | { ok: false; res: Response }> {
  const l = lang(req);
  const clientId = p.get('client_id') ?? '';
  const client = await loadClient(env, clientId);
  if (!client) return { ok: false, res: errorPage(l, 400, 'unknown_client') };
  let redirectUri = p.get('redirect_uri');
  if (!redirectUri && client.redirectUris.length === 1) redirectUri = client.redirectUris[0];
  if (!redirectUri || !client.redirectUris.includes(redirectUri)) return { ok: false, res: errorPage(l, 400, 'bad_redirect') };
  const state = p.get('state');
  const fail = (error: string, desc: string) => ({ ok: false as const, res: redirectWith(redirectUri!, { error, error_description: desc, state, iss: baseUrl(req, cfg) }) });
  if (p.get('response_type') !== 'code') return fail('unsupported_response_type', 'Nur response_type=code');
  const challenge = p.get('code_challenge') ?? '';
  if (p.get('code_challenge_method') !== 'S256' || !/^[A-Za-z0-9_-]{43,128}$/.test(challenge)) {
    return fail('invalid_request', 'PKCE mit S256 ist Pflicht');
  }
  const resource = p.get('resource');
  if (resource && resource.replace(/\/+$/, '') !== resourceUrl(req, cfg) && resource.replace(/\/+$/, '') !== baseUrl(req, cfg)) {
    return fail('invalid_target', 'Unbekannte Ressource');
  }
  const scope = p.get('scope');
  if (scope && !scope.split(' ').every((s) => s === SCOPE || s === '')) return fail('invalid_scope', `Unterstützt: ${SCOPE}`);
  return { ok: true, client, params: { clientId, redirectUri, state, codeChallenge: challenge, resource, scope: SCOPE } };
}

export async function authorizeGet(req: Request, env: Env, cfg: Config): Promise<Response> {
  const p = new URL(req.url).searchParams;
  const v = await validateAuth(req, cfg, env, p);
  if (!v.ok) return v.res;
  return authorizePage(lang(req), { client: v.client, params: p, redirectUri: v.params.redirectUri });
}

export async function authorizePost(req: Request, env: Env, cfg: Config): Promise<Response> {
  const l = lang(req);
  const ct = req.headers.get('content-type') ?? '';
  if (!ct.includes('application/x-www-form-urlencoded')) return errorPage(l, 400, 'bad_request');
  const form = new URLSearchParams(await req.text());
  const v = await validateAuth(req, cfg, env, form);
  if (!v.ok) return v.res;
  const { params, client } = v;
  const iss = baseUrl(req, cfg);
  if (form.get('action') === 'deny') {
    return redirectWith(params.redirectUri, { error: 'access_denied', error_description: 'Abgelehnt', state: params.state, iss });
  }
  try {
    await rateLimit(env, `opair:${clientIp(req)}`, 10, 600);
  } catch {
    return errorPage(l, 429, 'rate_limited');
  }
  const normalized = normalizePairingCode(form.get('pairing_code') ?? '');
  const row = normalized
    ? await env.DB.prepare('SELECT account_id, expires_at FROM chat_pairing WHERE code_hash = ?')
        .bind(await hashPairing(normalized))
        .first<{ account_id: string; expires_at: number }>()
    : null;
  if (!row || row.expires_at < now() || !(await getSync(env, row.account_id))) {
    return authorizePage(l, { client, params: form, redirectUri: params.redirectUri, error: true }, 400);
  }
  const grantId = `g_${randomId(12)}`;
  const code = `ovf_ac_${randomId(32)}`;
  await env.DB.batch([
    env.DB.prepare('DELETE FROM chat_pairing WHERE code_hash = ?').bind(await hashPairing(normalized!)),
    env.DB.prepare('INSERT INTO oauth_grants (id, account_id, client_id, client_name, scope, created_at) VALUES (?, ?, ?, ?, ?, ?)').bind(
      grantId,
      row.account_id,
      client.clientId,
      client.name ?? new URL(params.redirectUri).hostname,
      params.scope,
      now(),
    ),
    env.DB.prepare(
      'INSERT INTO oauth_codes (code_hash, grant_id, client_id, redirect_uri, code_challenge, resource, expires_at) VALUES (?, ?, ?, ?, ?, ?, ?)',
    ).bind(hex(await sha256(code)), grantId, client.clientId, params.redirectUri, params.codeChallenge, params.resource, now() + CODE_TTL_SEC),
  ]);
  return redirectWith(params.redirectUri, { code, state: params.state, iss });
}

// ---------- Token ----------

async function readForm(req: Request): Promise<URLSearchParams> {
  const ct = req.headers.get('content-type') ?? '';
  if (ct.includes('application/json')) {
    const o = (await req.json().catch(() => ({}))) as Record<string, unknown>;
    return new URLSearchParams(Object.entries(o).map(([k, v]) => [k, String(v)]));
  }
  return new URLSearchParams(await req.text());
}

/** Client-Authentifizierung: Basic-Header, client_secret im Body oder öffentlicher Client. */
async function authClient(env: Env, req: Request, form: URLSearchParams): Promise<Client | Response> {
  let clientId = form.get('client_id');
  let secret = form.get('client_secret');
  const auth = req.headers.get('authorization');
  if (auth?.startsWith('Basic ')) {
    try {
      const [id, sec] = atob(auth.slice(6)).split(':');
      clientId = decodeURIComponent(id);
      secret = decodeURIComponent(sec ?? '');
    } catch {
      return oauthError(401, 'invalid_client', 'Basic-Auth ungültig');
    }
  }
  if (!clientId) return oauthError(401, 'invalid_client', 'client_id fehlt');
  const client = clientId.startsWith('https://')
    ? ({ clientId, name: null, redirectUris: [], secretHash: null } satisfies Client)
    : await loadClient(env, clientId);
  if (!client) return oauthError(401, 'invalid_client', 'Unbekannter Client');
  if (client.secretHash && (!secret || hex(await sha256(secret)) !== client.secretHash)) {
    return oauthError(401, 'invalid_client', 'client_secret falsch');
  }
  return client;
}

async function issueTokens(env: Env, grantId: string): Promise<{ stmts: D1PreparedStatement[]; body: Record<string, unknown> }> {
  const access = `ovf_at_${randomId(32)}`;
  const refresh = `ovf_rt_${randomId(32)}`;
  const t = now();
  return {
    stmts: [
      env.DB.prepare('INSERT INTO oauth_tokens (token_hash, grant_id, kind, expires_at) VALUES (?, ?, ?, ?)').bind(
        hex(await sha256(access)),
        grantId,
        'access',
        t + ACCESS_TTL_SEC,
      ),
      env.DB.prepare('INSERT INTO oauth_tokens (token_hash, grant_id, kind, expires_at) VALUES (?, ?, ?, ?)').bind(
        hex(await sha256(refresh)),
        grantId,
        'refresh',
        t + REFRESH_TTL_SEC,
      ),
    ],
    body: { access_token: access, token_type: 'Bearer', expires_in: ACCESS_TTL_SEC, refresh_token: refresh, scope: SCOPE },
  };
}

async function pkceS256(verifier: string): Promise<string> {
  return b64uEncode(await sha256(verifier));
}

export async function token(req: Request, env: Env, cfg: Config): Promise<Response> {
  await rateLimit(env, `otok:${clientIp(req)}`, 120, 60);
  const form = await readForm(req);
  const client = await authClient(env, req, form);
  if (client instanceof Response) return client;
  const grantType = form.get('grant_type');

  if (grantType === 'authorization_code') {
    const code = form.get('code') ?? '';
    const codeHash = hex(await sha256(code));
    const row = await env.DB.prepare(
      'SELECT grant_id, client_id, redirect_uri, code_challenge, resource, expires_at FROM oauth_codes WHERE code_hash = ?',
    )
      .bind(codeHash)
      .first<{ grant_id: string; client_id: string; redirect_uri: string; code_challenge: string; resource: string | null; expires_at: number }>();
    if (!row) return oauthError(400, 'invalid_grant', 'Code ungültig oder schon benutzt');
    // Einmalig: sofort löschen, auch wenn die weitere Prüfung scheitert.
    await env.DB.prepare('DELETE FROM oauth_codes WHERE code_hash = ?').bind(codeHash).run();
    if (row.expires_at < now()) return oauthError(400, 'invalid_grant', 'Code abgelaufen');
    if (row.client_id !== client.clientId) return oauthError(400, 'invalid_grant', 'Code gehört zu einem anderen Client');
    const redirectUri = form.get('redirect_uri');
    if (redirectUri && redirectUri !== row.redirect_uri) return oauthError(400, 'invalid_grant', 'redirect_uri passt nicht');
    const verifier = form.get('code_verifier') ?? '';
    if (!/^[A-Za-z0-9._~-]{43,128}$/.test(verifier) || (await pkceS256(verifier)) !== row.code_challenge) {
      return oauthError(400, 'invalid_grant', 'PKCE-Prüfung fehlgeschlagen');
    }
    const resource = form.get('resource');
    if (resource && row.resource && resource !== row.resource) return oauthError(400, 'invalid_target', 'resource passt nicht');
    const { stmts, body } = await issueTokens(env, row.grant_id);
    await env.DB.batch(stmts);
    void cfg;
    return Response.json(body, { headers: noStore });
  }

  if (grantType === 'refresh_token') {
    const rt = form.get('refresh_token') ?? '';
    const h = hex(await sha256(rt));
    const row = await env.DB.prepare(
      `SELECT t.grant_id, t.expires_at, g.client_id, g.account_id FROM oauth_tokens t JOIN oauth_grants g ON g.id = t.grant_id
       WHERE t.token_hash = ? AND t.kind = 'refresh'`,
    )
      .bind(h)
      .first<{ grant_id: string; expires_at: number; client_id: string; account_id: string }>();
    if (!row || row.expires_at < now()) return oauthError(400, 'invalid_grant', 'Refresh-Token ungültig');
    if (row.client_id !== client.clientId) return oauthError(400, 'invalid_grant', 'Token gehört zu einem anderen Client');
    if (!(await getSync(env, row.account_id))) return oauthError(400, 'invalid_grant', 'Verbindung wurde getrennt');
    const { stmts, body } = await issueTokens(env, row.grant_id);
    // Rotation: altes Refresh-Token und abgelaufene Tokens dieser Freigabe entfernen.
    await env.DB.batch([
      env.DB.prepare('DELETE FROM oauth_tokens WHERE token_hash = ?').bind(h),
      env.DB.prepare('DELETE FROM oauth_tokens WHERE grant_id = ? AND expires_at < ?').bind(row.grant_id, now()),
      ...stmts,
    ]);
    return Response.json(body, { headers: noStore });
  }

  return oauthError(400, 'unsupported_grant_type', 'Unterstützt: authorization_code, refresh_token');
}

export async function revoke(req: Request, env: Env): Promise<Response> {
  const form = await readForm(req);
  const client = await authClient(env, req, form);
  if (client instanceof Response) return client;
  const h = hex(await sha256(form.get('token') ?? ''));
  const row = await env.DB.prepare(
    'SELECT t.kind, t.grant_id, g.client_id FROM oauth_tokens t JOIN oauth_grants g ON g.id = t.grant_id WHERE t.token_hash = ?',
  )
    .bind(h)
    .first<{ kind: string; grant_id: string; client_id: string }>();
  if (row && row.client_id === client.clientId) {
    if (row.kind === 'refresh') await env.DB.batch(revokeStatements(env, row.grant_id));
    else await env.DB.prepare('DELETE FROM oauth_tokens WHERE token_hash = ?').bind(h).run();
  }
  return new Response(null, { status: 200, headers: noStore });
}

// ---------- Bearer für /mcp ----------

export interface ChatPrincipal {
  accountId: string;
  grantId: string;
  clientName: string;
}

export async function bearer(req: Request, env: Env): Promise<ChatPrincipal | null> {
  const auth = req.headers.get('authorization') ?? '';
  const m = /^Bearer\s+(\S+)$/i.exec(auth);
  if (!m) return null;
  const row = await env.DB.prepare(
    `SELECT t.grant_id, t.expires_at, g.account_id, g.client_name, g.last_used_at FROM oauth_tokens t
     JOIN oauth_grants g ON g.id = t.grant_id WHERE t.token_hash = ? AND t.kind = 'access'`,
  )
    .bind(hex(await sha256(m[1])))
    .first<{ grant_id: string; expires_at: number; account_id: string; client_name: string | null; last_used_at: number | null }>();
  if (!row || row.expires_at < now()) return null;
  if (!row.last_used_at || row.last_used_at < now() - 300) {
    await env.DB.prepare('UPDATE oauth_grants SET last_used_at = ? WHERE id = ?').bind(now(), row.grant_id).run();
  }
  return { accountId: row.account_id, grantId: row.grant_id, clientName: row.client_name ?? 'KI-Chat' };
}

export function unauthorized(req: Request, cfg: Config, error = 'invalid_token'): Response {
  const meta = `${baseUrl(req, cfg)}/.well-known/oauth-protected-resource`;
  return Response.json(
    { jsonrpc: '2.0', error: { code: -32001, message: 'Nicht angemeldet – bitte Overfit erneut verbinden' }, id: null },
    {
      status: 401,
      headers: { 'www-authenticate': `Bearer error="${error}", resource_metadata="${meta}", scope="${SCOPE}"` },
    },
  );
}

export function cleanupOauth(env: Env): D1PreparedStatement[] {
  const t = now();
  return [
    env.DB.prepare('DELETE FROM oauth_tokens WHERE expires_at < ?').bind(t),
    env.DB.prepare('DELETE FROM oauth_codes WHERE expires_at < ?').bind(t),
    env.DB.prepare('DELETE FROM chat_pairing WHERE expires_at < ?').bind(t),
  ];
}

