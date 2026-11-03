import { ApiError, errorResponse } from './errors';
import type { Config, Env } from './types';
import { getConfig } from './config';
import { authenticate, authenticateRegistration, cleanup } from './auth';
import { clientIp, rateLimit } from './ratelimit';
import {
  accountSummary,
  createRecoveryCode,
  json,
  ledgerPage,
  registerDevice,
  restoreAccount,
  setProfile,
} from './account';
import { handleMessages } from './messages';
import { DEFAULT_TIER, TIER_NAMES, TIERS, tierAvailable } from './providers';
import { handleAdminCredit } from './admin';
import { handleVerifyPurchase } from './purchases';
import { createChallenge, createSpotToken, redeemSpot } from './spot';
import { packageCreditMicro } from './money';
import { createWebLink, handlePaymentWebhook, redeemCode, webCheckout, webOrder, webPurchaseConfig, webSession } from './web';
import { authServerMetadata, authorizeGet, authorizePost, protectedResourceMetadata, registerClient, revoke, token } from './chat/oauth';
import { handleMcp } from './chat/mcp';
import {
  ackInbox,
  chatStatus,
  createPairing,
  deletePhoto,
  disableChat,
  enableChat,
  listInbox,
  putPhoto,
  revokeConnection,
  syncDocs,
} from './chat/sync';

const CORS = {
  'access-control-allow-origin': '*',
  'access-control-allow-methods': 'GET, POST, OPTIONS',
  'access-control-allow-headers': 'authorization, content-type, mcp-protocol-version, mcp-session-id',
  'access-control-expose-headers': 'www-authenticate, mcp-protocol-version',
  'access-control-max-age': '86400',
};

/** Öffentliche Endpunkte der KI-Chat-Verbindung (MCP + OAuth). null = nicht zuständig. */
async function chatPublicRoute(req: Request, env: Env, cfg: Config, path: string): Promise<Response | null> {
  const m = req.method;
  if (path === '/.well-known/oauth-protected-resource' || path === '/.well-known/oauth-protected-resource/mcp') {
    return m === 'GET' ? Response.json(protectedResourceMetadata(req, cfg)) : null;
  }
  if (path === '/.well-known/oauth-authorization-server' || path === '/.well-known/openid-configuration') {
    return m === 'GET' ? Response.json(authServerMetadata(req, cfg)) : null;
  }
  if (path === '/mcp' || path === '/mcp/') return handleMcp(req, env, cfg);
  if (path === '/oauth/register' && m === 'POST') return registerClient(req, env, cfg);
  if (path === '/oauth/authorize' && m === 'GET') return authorizeGet(req, env, cfg);
  if (path === '/oauth/authorize' && m === 'POST') return authorizePost(req, env, cfg);
  if (path === '/oauth/token' && m === 'POST') return token(req, env, cfg);
  if (path === '/oauth/revoke' && m === 'POST') return revoke(req, env);
  return null;
}

/** Öffentliche Endpunkte der Kaufseite (GitHub Pages, ohne Login, CORS offen: keine Cookies, keine Konto-Daten). */
async function webPublicRoute(req: Request, env: Env, cfg: Config, path: string): Promise<Response> {
  const m = req.method;
  const ip = clientIp(req);
  const body = async () => {
    const text = await req.text();
    if (text.length > 4096) throw new ApiError(413, 'body_too_large', 'Anfrage zu groß');
    return json(new TextEncoder().encode(text || '{}'));
  };
  if (path === '/v1/web/session' && m === 'POST') {
    await rateLimit(env, `websess:${ip}`, 120, 3600);
    return Response.json(await webSession(env, cfg, await body()));
  }
  if (path === '/v1/web/checkout' && m === 'POST') {
    await rateLimit(env, `webco:${ip}`, cfg.rlSensitivePerHour * 3, 3600);
    return Response.json(await webCheckout(env, cfg, await body()));
  }
  const order = WEB_ORDER_ROUTE.exec(path);
  if (order && m === 'GET') {
    await rateLimit(env, `weborder:${ip}`, 600, 3600);
    return Response.json(await webOrder(env, order[1]));
  }
  throw new ApiError(404, 'not_found', 'Unbekannter Endpunkt');
}
const WEB_ORDER_ROUTE = /^\/v1\/web\/orders\/([A-Za-z0-9_-]{16,64})$/;

const CHAT_ROUTES = ['/v1/chat', '/v1/chat/enable', '/v1/chat/disable', '/v1/chat/sync', '/v1/chat/pairing', '/v1/chat/inbox',
  '/v1/chat/inbox/ack', '/v1/chat/connections/revoke'];
const PHOTO_ROUTE = /^\/v1\/chat\/photos\/([A-Za-z0-9_.:-]{1,80})$/;

function publicConfig(env: Env, cfg: Config) {
  return {
    /** `test` = Testbetrieb (keine Geräteprüfung, Test-Guthaben); die App zeigt dann einen Hinweis. */
    mode: cfg.testMode ? 'test' : 'production',
    currency: 'EUR',
    unit: 'micro-euro',
    markupBp: cfg.markupBp,
    storeFeeBp: cfg.storeFeeBp,
    models: Object.fromEntries(Object.entries(cfg.prices).map(([m, p]) => [m, p])),
    maxTokensCap: cfg.maxTokensCap,
    /** KI-Stufen: die App schickt den Namen als `model`; Preise in µ€/MTok ohne Aufschlag. */
    defaultTier: DEFAULT_TIER,
    tiers: Object.fromEntries(
      TIER_NAMES.map((t) => [
        t,
        { available: tierAvailable(env, t), model: TIERS[t].model, images: TIERS[t].images, fallback: TIERS[t].fallback, price: cfg.prices[TIERS[t].model] },
      ]),
    ),
    packages: Object.entries(cfg.packages).map(([productId, p]) => ({
      productId,
      priceMicro: p.priceMicro,
      creditMicro: packageCreditMicro(p.priceMicro, cfg.storeFeeBp, cfg.markupBp),
    })),
    /** Guthaben im Web kaufen (Merchant of Record); `testMode` = Sandbox, keine echte Zahlung. */
    webPurchase: webPurchaseConfig(env, cfg),
    spot: {
      starterMicro: cfg.spotStarterMicro,
      rewardMicro: cfg.spotRewardMicro,
      tokenTtlSec: cfg.spotTokenTtlSec,
      monthlyLimitPerInviter: cfg.spotMonthlyLimitPerInviter,
      expiryDays: cfg.spotExpiryDays,
    },
  };
}

async function route(req: Request, env: Env, ctx: ExecutionContext): Promise<Response> {
  const cfg = getConfig(env);
  const url = new URL(req.url);
  const { pathname: path } = url;
  const method = req.method;

  if (path === '/v1/health' && method === 'GET') return Response.json({ ok: true });
  if (path === '/v1/config' && method === 'GET') return Response.json(publicConfig(env, cfg));

  if (Math.random() < 0.02) ctx.waitUntil(cleanup(env).catch(() => {}));

  const isChatPublic = path === '/mcp' || path.startsWith('/oauth/') || path.startsWith('/.well-known/');
  if (isChatPublic && method === 'OPTIONS') return new Response(null, { status: 204, headers: CORS });
  if (isChatPublic) {
    const res = await chatPublicRoute(req, env, cfg, path);
    if (!res) throw new ApiError(404, 'not_found', 'Unbekannter Endpunkt');
    const out = new Response(res.body, res);
    if (!out.headers.get('content-type')?.startsWith('text/html')) for (const [k, v] of Object.entries(CORS)) out.headers.set(k, v);
    return out;
  }

  if (path.startsWith('/v1/web/')) {
    if (method === 'OPTIONS') return new Response(null, { status: 204, headers: CORS });
    const out = await webPublicRoute(req, env, cfg, path).catch((e) => (e instanceof ApiError ? errorResponse(e) : Promise.reject(e)));
    const res = new Response(out.body, out);
    for (const [k, v] of Object.entries(CORS)) res.headers.set(k, v);
    return res;
  }

  if (path === '/v1/webhooks/payment' && method === 'POST') {
    return Response.json(await handlePaymentWebhook(env, cfg, req));
  }

  if (path === '/v1/admin/credit') {
    await rateLimit(env, `admin:${clientIp(req)}`, 20, 3600);
    return handleAdminCredit(req, env, cfg, new Uint8Array(await req.arrayBuffer()));
  }

  if (path === '/v1/devices' && method === 'POST') {
    await rateLimit(env, `reg:${clientIp(req)}`, cfg.rlRegisterPerHourIp, 3600);
    const { deviceId, publicKeyRaw } = await authenticateRegistration(env, req, cfg);
    const r = await registerDevice(env, deviceId, publicKeyRaw, cfg);
    const device = { id: deviceId, accountId: r.accountId };
    return Response.json({ deviceId, ...(await accountSummary(env, cfg, device.accountId)) }, { status: r.created ? 201 : 200 });
  }

  const routes = ['/v1/account', '/v1/account/profile', '/v1/account/ledger', '/v1/messages', '/v1/purchases/verify',
    '/v1/spot/token', '/v1/spot/challenge', '/v1/spot/redeem', '/v1/recovery/code', '/v1/recovery/restore',
    '/v1/purchase/web-link', '/v1/purchases/web-link', '/v1/codes/redeem'];
  const photo = PHOTO_ROUTE.exec(path);
  if (!routes.includes(path) && !CHAT_ROUTES.includes(path) && !photo) throw new ApiError(404, 'not_found', 'Unbekannter Endpunkt');

  const { device, body } = await authenticate(env, req, cfg);
  await rateLimit(env, `dev:${device.id}`, cfg.rlDevicePerMin, 60);
  const sensitive = () => rateLimit(env, `sens:${path}:${device.id}`, cfg.rlSensitivePerHour, 3600);
  const wrongMethod = () => new ApiError(405, 'method_not_allowed', 'Methode nicht erlaubt');

  if (photo) {
    if (method === 'PUT') return Response.json(await putPhoto(env, cfg, device.accountId, photo[1], url, body));
    if (method === 'DELETE') return Response.json(await deletePhoto(env, device.accountId, photo[1]));
    throw wrongMethod();
  }

  switch (path) {
    case '/v1/chat':
      if (method !== 'GET') throw wrongMethod();
      return Response.json(await chatStatus(env, device.accountId));
    case '/v1/chat/enable':
      if (method !== 'POST') throw wrongMethod();
      return Response.json(await enableChat(env, device.accountId, json(body)));
    case '/v1/chat/disable':
      if (method !== 'POST') throw wrongMethod();
      return Response.json(await disableChat(env, device.accountId));
    case '/v1/chat/sync':
      if (method !== 'POST') throw wrongMethod();
      return Response.json(await syncDocs(env, cfg, device.accountId, json(body)));
    case '/v1/chat/pairing':
      if (method !== 'POST') throw wrongMethod();
      await sensitive();
      return Response.json(await createPairing(env, device.accountId));
    case '/v1/chat/inbox':
      if (method !== 'GET') throw wrongMethod();
      return Response.json(await listInbox(env, cfg, device.accountId));
    case '/v1/chat/inbox/ack':
      if (method !== 'POST') throw wrongMethod();
      return Response.json(await ackInbox(env, device.accountId, json(body)));
    case '/v1/chat/connections/revoke':
      if (method !== 'POST') throw wrongMethod();
      return Response.json(await revokeConnection(env, device.accountId, json(body)));
    case '/v1/account':
      if (method !== 'GET') throw wrongMethod();
      return Response.json(await accountSummary(env, cfg, device.accountId));
    case '/v1/account/profile':
      if (method !== 'PUT') throw wrongMethod();
      return Response.json(await setProfile(env, device, json(body)));
    case '/v1/account/ledger':
      if (method !== 'GET') throw wrongMethod();
      return Response.json(await ledgerPage(env, device.accountId, url));
    case '/v1/messages':
      if (method !== 'POST') throw wrongMethod();
      await rateLimit(env, `msg:${device.id}`, cfg.rlMessagesPerMin, 60);
      return handleMessages(env, cfg, device, body);
    case '/v1/purchases/verify':
      if (method !== 'POST') throw wrongMethod();
      await sensitive();
      return Response.json(await handleVerifyPurchase(env, cfg, device, json(body)));
    case '/v1/purchase/web-link':
    case '/v1/purchases/web-link':
      if (method !== 'POST') throw wrongMethod();
      await sensitive();
      return Response.json(await createWebLink(env, cfg, device, body.length ? json(body) : {}));
    case '/v1/codes/redeem':
      if (method !== 'POST') throw wrongMethod();
      await sensitive();
      await rateLimit(env, `codeip:${clientIp(req)}`, cfg.rlSensitivePerHour * 3, 3600);
      return Response.json(await redeemCode(env, device, json(body)));
    case '/v1/spot/token':
      if (method !== 'POST') throw wrongMethod();
      return Response.json(await createSpotToken(env, cfg, device));
    case '/v1/spot/challenge':
      if (method !== 'POST') throw wrongMethod();
      await sensitive();
      return Response.json(await createChallenge(env, cfg, device));
    case '/v1/spot/redeem':
      if (method !== 'POST') throw wrongMethod();
      await sensitive();
      await rateLimit(env, `spotip:${clientIp(req)}`, cfg.rlSensitivePerHour * 3, 3600);
      return Response.json(await redeemSpot(env, cfg, device, json(body)));
    case '/v1/recovery/code':
      if (method !== 'POST') throw wrongMethod();
      return Response.json(await createRecoveryCode(env, device));
    default: // /v1/recovery/restore
      if (method !== 'POST') throw wrongMethod();
      await sensitive();
      await rateLimit(env, `restoreip:${clientIp(req)}`, cfg.rlSensitivePerHour * 3, 3600);
      return Response.json(await restoreAccount(env, cfg, device, String(json(body).code ?? '')));
  }
}

export default {
  async fetch(req: Request, env: Env, ctx: ExecutionContext): Promise<Response> {
    try {
      return await route(req, env, ctx);
    } catch (e) {
      if (e instanceof ApiError) {
        const res = errorResponse(e);
        const retry = e.details?.retryAfterSec;
        if (typeof retry === 'number') res.headers.set('retry-after', String(retry));
        return res;
      }
      console.error('unhandled', e);
      return errorResponse(new ApiError(500, 'internal_error', 'Interner Fehler'));
    }
  },
};
