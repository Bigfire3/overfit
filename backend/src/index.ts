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
import { handleVerifyPurchase } from './purchases';
import { createChallenge, createSpotToken, redeemSpot } from './spot';
import { packageCreditMicro } from './money';

function publicConfig(cfg: Config) {
  return {
    currency: 'EUR',
    unit: 'micro-euro',
    markupBp: cfg.markupBp,
    storeFeeBp: cfg.storeFeeBp,
    models: Object.fromEntries(Object.entries(cfg.prices).map(([m, p]) => [m, p])),
    maxTokensCap: cfg.maxTokensCap,
    packages: Object.entries(cfg.packages).map(([productId, p]) => ({
      productId,
      priceMicro: p.priceMicro,
      creditMicro: packageCreditMicro(p.priceMicro, cfg.storeFeeBp, cfg.markupBp),
    })),
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
  if (path === '/v1/config' && method === 'GET') return Response.json(publicConfig(cfg));

  if (Math.random() < 0.02) ctx.waitUntil(cleanup(env).catch(() => {}));

  if (path === '/v1/devices' && method === 'POST') {
    await rateLimit(env, `reg:${clientIp(req)}`, cfg.rlRegisterPerHourIp, 3600);
    const { deviceId, publicKeyRaw } = await authenticateRegistration(env, req, cfg);
    const r = await registerDevice(env, deviceId, publicKeyRaw);
    const device = { id: deviceId, accountId: r.accountId };
    return Response.json({ deviceId, ...(await accountSummary(env, cfg, device.accountId)) }, { status: r.created ? 201 : 200 });
  }

  const routes = ['/v1/account', '/v1/account/profile', '/v1/account/ledger', '/v1/messages', '/v1/purchases/verify',
    '/v1/spot/token', '/v1/spot/challenge', '/v1/spot/redeem', '/v1/recovery/code', '/v1/recovery/restore'];
  if (!routes.includes(path)) throw new ApiError(404, 'not_found', 'Unbekannter Endpunkt');

  const { device, body } = await authenticate(env, req, cfg);
  await rateLimit(env, `dev:${device.id}`, cfg.rlDevicePerMin, 60);
  const sensitive = () => rateLimit(env, `sens:${path}:${device.id}`, cfg.rlSensitivePerHour, 3600);
  const wrongMethod = () => new ApiError(405, 'method_not_allowed', 'Methode nicht erlaubt');

  switch (path) {
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
