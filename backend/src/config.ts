import type { Config, Env, Price } from './types';

// Preise in Mikro-Euro pro 1 Mio. Token (Listenpreis in USD × Wechselkurs, vom Betreiber zu pflegen).
// Sonnet 5.5: 2 $ / 10 $ pro MTok, Cache-Read 0,20 $, Cache-Write 2,50 $ – umgerechnet mit 0,92 €/$.
const DEFAULT_PRICES: Record<string, Price> = {
  'claude-sonnet-5-5': { in: 1_840_000, cacheRead: 184_000, cacheWrite: 2_300_000, out: 9_200_000 },
};

const DEFAULT_PACKAGES = {
  'overfit.fuel.5': { priceMicro: 5_000_000 },
  'overfit.fuel.10': { priceMicro: 10_000_000 },
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

export function getConfig(env: Env): Config {
  const devMode = env.DEV_MODE === 'true';
  // Mock-Modi gibt es nur im Dev-Modus; sonst zählt immer „required“.
  const mock = (v: string | undefined) => (devMode && v === 'mock' ? 'mock' : 'required');
  return {
    devMode,
    attestationMode: mock(env.ATTESTATION_MODE),
    purchaseMode: mock(env.PURCHASE_MODE),
    anthropicBaseUrl: env.ANTHROPIC_BASE_URL || 'https://api.anthropic.com',
    markupBp: int(env.MARKUP_BP, 800),
    storeFeeBp: int(env.STORE_FEE_BP, 1500),
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
    prices: json(env.PRICE_TABLE, DEFAULT_PRICES),
    packages: json(env.PACKAGES, DEFAULT_PACKAGES),
    rlDevicePerMin: int(env.RL_DEVICE_PER_MIN, 60, 1),
    rlMessagesPerMin: int(env.RL_MESSAGES_PER_MIN, 10, 1),
    rlRegisterPerHourIp: int(env.RL_REGISTER_PER_HOUR_IP, 20, 1),
    rlSensitivePerHour: int(env.RL_SENSITIVE_PER_HOUR, 10, 1),
  };
}
