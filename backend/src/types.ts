export interface Env {
  DB: D1Database;
  /** R2-Bucket für freigegebene Körperfotos (KI-Chat-Verbindung), verschlüsselt abgelegt. */
  PHOTOS: R2Bucket;
  // Secrets
  ANTHROPIC_API_KEY?: string;
  SPOT_SIGNING_KEY?: string;
  /** Hauptschlüssel für die Verschlüsselung der KI-Chat-Daten (≥ 32 zufällige Bytes, Base64url). */
  SYNC_ENCRYPTION_KEY?: string;
  GOOGLE_PLAY_SERVICE_ACCOUNT?: string;
  GOOGLE_PLAY_PACKAGE_NAME?: string;
  PLAY_INTEGRITY_SERVICE_ACCOUNT?: string;
  APPLE_IAP_KEY?: string;
  APPLE_IAP_KEY_ID?: string;
  APPLE_IAP_ISSUER_ID?: string;
  APPLE_BUNDLE_ID?: string;
  APPLE_APP_ATTEST_TEAM_ID?: string;
  // Konfiguration (alle optional, Defaults in config.ts)
  ANTHROPIC_BASE_URL?: string;
  /** Öffentliche Basis-URL des Workers (Issuer/Resource für OAuth), Standard: Origin der Anfrage. */
  PUBLIC_BASE_URL?: string;
  /** Basis für Import-Links, Standard https://bigfire3.github.io/overfit/t */
  SHARE_BASE_URL?: string;
  CHAT_MAX_DOCS?: string;
  CHAT_MAX_BYTES?: string;
  CHAT_MAX_PHOTOS?: string;
  CHAT_MAX_PHOTO_BYTES?: string;
  CHAT_INBOX_MAX?: string;
  RL_MCP_PER_MIN?: string;
  DEV_MODE?: string;
  ATTESTATION_MODE?: string;
  PURCHASE_MODE?: string;
  /** `test` = Testbetrieb (siehe README); alles andere = Produktion. Nur der exakte Wert `test` schaltet etwas frei. */
  DEPLOY_MODE?: string;
  /** Geheimnis für `POST /v1/admin/credit` (nur im Testbetrieb). */
  ADMIN_SECRET?: string;
  TEST_STARTER_MICRO?: string;
  TEST_CREDIT_CAP_MICRO?: string;
  MARKUP_BP?: string;
  STORE_FEE_BP?: string;
  SPOT_STARTER_MICRO?: string;
  SPOT_REWARD_MICRO?: string;
  SPOT_TOKEN_TTL_SEC?: string;
  SPOT_MONTHLY_LIMIT_PER_INVITER?: string;
  SPOT_GLOBAL_MONTHLY_BUDGET_MICRO?: string;
  SPOT_EXPIRY_DAYS?: string;
  REWARD_EXPIRY_DAYS?: string;
  MAX_TOKENS_CAP?: string;
  MAX_BODY_BYTES?: string;
  IMAGE_TOKENS_RESERVE?: string;
  PRICE_TABLE?: string;
  PACKAGES?: string;
  RL_DEVICE_PER_MIN?: string;
  RL_MESSAGES_PER_MIN?: string;
  RL_REGISTER_PER_HOUR_IP?: string;
  RL_SENSITIVE_PER_HOUR?: string;
}

export interface Price {
  /** Mikro-Euro pro 1 Mio. Token */
  in: number;
  cacheRead: number;
  cacheWrite: number;
  out: number;
}

export interface Config {
  devMode: boolean;
  /** Testbetrieb: keine Geräteprüfung, Test-Guthaben, niedrige Limits. Nie in Produktion. */
  testMode: boolean;
  /** Test-Startguthaben je neuem Konto (Mikro-Euro). */
  testStarterMicro: number;
  /** Obergrenze für alles Test-Guthaben zusammen (Startguthaben und Admin-Gutschriften). */
  testCreditCapMicro: number;
  attestationMode: 'required' | 'mock';
  purchaseMode: 'required' | 'mock';
  anthropicBaseUrl: string;
  markupBp: number;
  storeFeeBp: number;
  spotStarterMicro: number;
  spotRewardMicro: number;
  spotTokenTtlSec: number;
  spotMonthlyLimitPerInviter: number;
  spotGlobalMonthlyBudgetMicro: number;
  spotExpiryDays: number;
  rewardExpiryDays: number;
  maxTokensCap: number;
  maxBodyBytes: number;
  imageTokensReserve: number;
  prices: Record<string, Price>;
  packages: Record<string, { priceMicro: number }>;
  rlDevicePerMin: number;
  rlMessagesPerMin: number;
  rlRegisterPerHourIp: number;
  rlSensitivePerHour: number;
  publicBaseUrl: string | null;
  shareBaseUrl: string;
  chatMaxDocs: number;
  chatMaxBytes: number;
  chatMaxDocBytes: number;
  chatMaxPhotos: number;
  chatMaxPhotoBytes: number;
  chatInboxMax: number;
  rlMcpPerMin: number;
}

export interface Device {
  id: string;
  accountId: string;
}
