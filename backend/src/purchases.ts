import { ApiError } from './errors';
import type { Config, Device, Env } from './types';
import { availableMicro, grantStatements, now } from './ledger';
import { packageCreditMicro } from './money';
import { getPurchaseVerifier, type Platform } from './verifiers';

export async function handleVerifyPurchase(env: Env, cfg: Config, device: Device, body: Record<string, unknown>) {
  const platform = body.platform;
  if (platform !== 'google' && platform !== 'apple') throw new ApiError(400, 'invalid_request', 'platform: google | apple');
  const productId = body.productId;
  const token = body.purchaseToken;
  if (typeof productId !== 'string' || typeof token !== 'string' || !token || token.length > 8192) {
    throw new ApiError(400, 'invalid_request', 'productId und purchaseToken erforderlich');
  }
  const pkg = cfg.packages[productId];
  if (!pkg) throw new ApiError(400, 'unknown_product', 'Unbekanntes Paket', { products: Object.keys(cfg.packages) });
  const verifier = getPurchaseVerifier(env, cfg, platform as Platform);
  if (!verifier) throw new ApiError(503, 'purchases_unavailable', 'Kauf ist noch nicht freigeschaltet');

  const verified = await verifier.verify({ productId, token });
  if (!verified || verified.productId !== productId) {
    throw new ApiError(402, 'purchase_invalid', 'Kauf konnte nicht bestätigt werden');
  }
  const existing = await env.DB.prepare('SELECT account_id, credited_micro FROM purchases WHERE platform = ? AND order_id = ?')
    .bind(platform, verified.orderId)
    .first<{ account_id: string; credited_micro: number }>();
  const credited = packageCreditMicro(pkg.priceMicro);
  if (existing) {
    // Idempotent: derselbe Kauf wird nie zweimal gutgeschrieben (auch nicht für ein anderes Konto).
    return {
      productId,
      creditedMicro: existing.credited_micro,
      alreadyCredited: true,
      balanceMicro: await availableMicro(env, device.accountId),
    };
  }
  try {
    await env.DB.batch([
      env.DB.prepare(
        `INSERT INTO purchases (platform, order_id, account_id, product_id, price_micro, credited_micro, created_at)
         VALUES (?, ?, ?, ?, ?, ?, ?)`,
      ).bind(platform, verified.orderId, device.accountId, productId, pkg.priceMicro, credited, now()),
      ...grantStatements(env, device.accountId, 'purchase', credited, null, `${platform}:${verified.orderId}`),
    ]);
  } catch {
    // Paralleler Doppelaufruf: der andere hat gewonnen.
    return {
      productId,
      creditedMicro: credited,
      alreadyCredited: true,
      balanceMicro: await availableMicro(env, device.accountId),
    };
  }
  return { productId, creditedMicro: credited, alreadyCredited: false, balanceMicro: await availableMicro(env, device.accountId) };
}
