import type { Config, Env, Price } from './types';
import { currentPrices } from './providers';

/**
 * Einziger Aufschlag auf alle Modellpreise (Basispunkte). Herleitung und Tabellen: docs/monetization.md „Rechnung“.
 * 2800 = 28 %: deckt im Web (Stripe, Kleinunternehmer ohne USt) Zahlungsgebühr und Spot-Gutschriften (10 % vom Netto)
 * und lässt beim kleinsten Paket (5 €) noch 5 % vom Netto, ab 10 € über 7 %. Der Store trägt seine USt und Gebühr
 * selbst über einen offen ausgewiesenen Aufpreis (Store-Preis > Guthaben, siehe DEFAULT_PACKAGES).
 */
export const DEFAULT_MARKUP_BP = 2800;

/**
 * Store-Pakete: `priceMicro` = Preis im Store (Endpreis inkl. USt, die Apple/Google als Verkäufer abführen, und
 * 15 % Store-Gebühr), `creditMicro` = Guthaben. Preisstufen so gewählt, dass der Gewinn auch bei 27 % USt ≥ 5 % vom
 * Netto bleibt (backend/test/pricing.test.ts). Produkt-IDs bleiben stabil.
 */
export const DEFAULT_PACKAGES: Record<string, { priceMicro: number; creditMicro: number }> = {
  'overfit.fuel.5': { priceMicro: 6_990_000, creditMicro: 5_000_000 },
  'overfit.fuel.10': { priceMicro: 13_990_000, creditMicro: 10_000_000 },
};

/** Web-Pakete (Stripe): Endpreis = Guthaben, 1:1. Kleinunternehmer nach § 19 UStG: keine USt. */
export const DEFAULT_WEB_PACKAGES: Record<string, { priceMicro: number }> = {
  'web.5': { priceMicro: 5_000_000 },
  'web.10': { priceMicro: 10_000_000 },
  'web.20': { priceMicro: 20_000_000 },
  'web.50': { priceMicro: 50_000_000 },
};

function int(v: string | undefined, def: number, min = 0): number {
  if (v === undefined || v === '') return def;
  const n = Number(v);
  return Number.isSafeInteger(n) && n >= min ? n : def;
}

function json<T>(v: unknown, def: T): T {
  if (v === undefined || v === '') return def;
  if (typeof v === 'object') return v as T;
  try {
    return JSON.parse(String(v)) as T;
  } catch {
    return def;
  }
}

/** Ältere `PACKAGES`-Werte ohne `creditMicro`: Guthaben = Preis. */
function storePackages(p: Record<string, { priceMicro: number; creditMicro?: number }>) {
  return Object.fromEntries(Object.entries(p).map(([id, v]) => [id, { priceMicro: v.priceMicro, creditMicro: v.creditMicro ?? v.priceMicro }]));
}

export function getConfig(env: Env): Config {
  const devMode = env.DEV_MODE === 'true';
  // Mock-Modi gibt es nur im Dev-Modus; sonst zählt immer „required“.
  const mock = (v: string | undefined) => (devMode && v === 'mock' ? 'mock' : 'required');
  const testMode = env.DEPLOY_MODE === 'test';
  return {
    devMode,
    testMode,
    testStarterMicro: int(env.TEST_STARTER_MICRO, 1_000_000),
    testCreditCapMicro: int(env.TEST_CREDIT_CAP_MICRO, 20_000_000),
    attestationMode: mock(env.ATTESTATION_MODE),
    purchaseMode: mock(env.PURCHASE_MODE),
    aiGatewayId: env.AI_GATEWAY_ID?.trim() || null,
    upstreamTimeoutMs: int(env.UPSTREAM_TIMEOUT_MS, 100_000, 1000),
    upstreamBudgetMs: int(env.UPSTREAM_BUDGET_MS, 140_000, 1000),
    markupBp: int(env.MARKUP_BP, DEFAULT_MARKUP_BP),
    spotStarterMicro: int(env.SPOT_STARTER_MICRO, 1_000_000),
    spotRewardMicro: int(env.SPOT_REWARD_MICRO, 1_000_000),
    spotTokenTtlSec: int(env.SPOT_TOKEN_TTL_SEC, 45, 5),
    spotMonthlyLimitPerInviter: int(env.SPOT_MONTHLY_LIMIT_PER_INVITER, 10),
    spotGlobalMonthlyBudgetMicro: int(env.SPOT_GLOBAL_MONTHLY_BUDGET_MICRO, 50_000_000),
    spotExpiryDays: int(env.SPOT_EXPIRY_DAYS, 180, 1),
    rewardExpiryDays: int(env.REWARD_EXPIRY_DAYS, 180, 1),
    maxTokensCap: int(env.MAX_TOKENS_CAP, 8192, 1),
    maxBodyBytes: int(env.MAX_BODY_BYTES, 8 * 1024 * 1024, 1024),
    imageTokensReserve: int(env.IMAGE_TOKENS_RESERVE, 3000),
    // Preise je Modell in µ€/MTok (Tabelle in providers.ts); PRICE_TABLE überschreibt einzelne Modelle.
    prices: { ...currentPrices(), ...json<Record<string, Price>>(env.PRICE_TABLE, {}) },
    packages: storePackages(json<Record<string, { priceMicro: number; creditMicro?: number }>>(env.PACKAGES, DEFAULT_PACKAGES)),
    webPackages: json(env.WEB_PACKAGES, DEFAULT_WEB_PACKAGES),
    webPurchaseOff: env.WEB_PURCHASE === 'off',
    webPurchaseUrl: env.WEB_PURCHASE_URL || 'https://bigfire3.github.io/overfit/guthaben/',
    webPurchaseChannels: (env.WEB_PURCHASE_CHANNELS ?? 'sideload').split(',').map((c) => c.trim()).filter((c) => c === 'sideload' || c === 'store'),
    webLinkTtlSec: int(env.WEB_LINK_TTL_SEC, 15 * 60, 60),
    rlDevicePerMin: int(env.RL_DEVICE_PER_MIN, 60, 1),
    rlMessagesPerMin: int(env.RL_MESSAGES_PER_MIN, 10, 1),
    rlRegisterPerHourIp: int(env.RL_REGISTER_PER_HOUR_IP, 20, 1),
    rlSensitivePerHour: int(env.RL_SENSITIVE_PER_HOUR, 10, 1),
    publicBaseUrl: env.PUBLIC_BASE_URL ? env.PUBLIC_BASE_URL.replace(/\/+$/, '') : null,
    shareBaseUrl: (env.SHARE_BASE_URL || 'https://bigfire3.github.io/overfit/t').replace(/\/+$/, ''),
    chatMaxDocs: int(env.CHAT_MAX_DOCS, 5000, 1),
    chatMaxBytes: int(env.CHAT_MAX_BYTES, 25 * 1024 * 1024, 1024),
    chatMaxDocBytes: 256 * 1024,
    chatMaxPhotos: int(env.CHAT_MAX_PHOTOS, 300, 0),
    chatMaxPhotoBytes: int(env.CHAT_MAX_PHOTO_BYTES, 3 * 1024 * 1024, 1024),
    chatInboxMax: int(env.CHAT_INBOX_MAX, 50, 1),
    rlMcpPerMin: int(env.RL_MCP_PER_MIN, 120, 1),
  };
}
