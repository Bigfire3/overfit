export interface Env {
  DB: D1Database;
  /** R2-Bucket für freigegebene Körperfotos (KI-Chat-Verbindung), verschlüsselt abgelegt. */
  PHOTOS: R2Bucket;
  /** Workers AI (Stufe `lite`) und AI-Gateway-URLs. Fehlt im lokalen Dev und in Tests ohne Mock. */
  AI?: AiBinding;
  // Secrets (mindestens einer der beiden KI-Schlüssel)
  ANTHROPIC_API_KEY?: string;
  /** Google-AI-Studio-Key (Gemini, Stufe `standard`). */
  GEMINI_API_KEY?: string;
  INVITE_SIGNING_KEY?: string;
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
  /**
   * Web-Kauf über Stripe Checkout. Fehlt einer der beiden Werte, ist der Web-Kauf aus. `sk_test_…`/`rk_test_…` = Testmodus
   * (nur im Test-/Dev-Betrieb), `sk_live_…`/`rk_live_…` = echte Zahlungen (nur im Produktionsbetrieb).
   */
  STRIPE_SECRET_KEY?: string;
  /** Signing secret des Webhook-Endpunkts (`whsec_…`). */
  STRIPE_WEBHOOK_SECRET?: string;
  // Konfiguration (alle optional, Defaults in config.ts)
  /** Nur Tests/lokal: andere API-Basis für Stripe. */
  STRIPE_API_BASE?: string;
  /** `on` = zu jedem Kauf eine Stripe-Rechnung (PDF) mit §-19-Fußzeile; kostet bei Stripe extra. Standard: nur Beleg. */
  STRIPE_INVOICES?: string;
  /** `off` schaltet den Web-Kauf ab, auch wenn die Stripe-Secrets gesetzt sind. */
  WEB_PURCHASE?: string;
  /** Adresse der Kaufseite, Standard https://bigfire3.github.io/overfit/guthaben/ */
  WEB_PURCHASE_URL?: string;
  /** Kanäle, in denen die App „Im Web aufladen“ zeigt (Komma-Liste aus sideload, store). Standard: sideload. */
  WEB_PURCHASE_CHANNELS?: string;
  /** JSON {"web.5": {"priceMicro": 5000000}, …}; Guthaben = Preis (1:1). */
  WEB_PACKAGES?: string;
  WEB_LINK_TTL_SEC?: string;
  ANTHROPIC_BASE_URL?: string;
  GEMINI_BASE_URL?: string;
  /** Name des Cloudflare AI Gateways; leer = Anbieter direkt (lokal). Setzt der Deploy-Workflow. */
  AI_GATEWAY_ID?: string;
  /** Token für ein authentifiziertes AI Gateway (`cf-aig-authorization`); ohne Token gehen Gemini/Anthropic direkt. */
  AI_GATEWAY_TOKEN?: string;
  /** Zeitlimit je Anbieter-Versuch (ms) und für die ganze Anfrage inklusive Ausweichen. */
  UPSTREAM_TIMEOUT_MS?: string;
  UPSTREAM_BUDGET_MS?: string;
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
  INVITE_STARTER_REVIEWS?: string;
  INVITE_REWARD_REVIEWS?: string;
  INVITE_TOKEN_TTL_SEC?: string;
  INVITE_MONTHLY_LIMIT_PER_INVITER?: string;
  INVITE_GLOBAL_MONTHLY_BUDGET_REVIEWS?: string;
  INVITE_GIFT_MONTHLY_COST_CAP_MICRO?: string;
  INVITE_GIFT_MAX_COST_MICRO?: string;
  INVITE_EXPIRY_DAYS?: string;
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

/** Ausschnitt des Workers-AI-Bindings, den das Backend nutzt. */
export interface AiBinding {
  run(model: string, inputs: unknown, options?: unknown): Promise<unknown>;
  gateway(id: string): { getUrl(provider?: string): Promise<string> };
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
  aiGatewayId: string | null;
  upstreamTimeoutMs: number;
  upstreamBudgetMs: number;
  markupBp: number;
  /** Geschenkte Auswertungen für den Eingeladenen / den Einladenden. */
  inviteStarterReviews: number;
  inviteRewardReviews: number;
  inviteTokenTtlSec: number;
  inviteMonthlyLimitPerInviter: number;
  /** Höchstens so viele geschenkte Auswertungen werden je Monat vergeben (Einlösung zählt Start + Belohnung). */
  inviteGlobalMonthlyBudgetReviews: number;
  /** Harte Grenze: echte KI-Kosten (µ€) aller geschenkten Auswertungen im Monat; darüber zahlt das Guthaben. */
  inviteGiftMonthlyCostCapMicro: number;
  /** Eine Anfrage, deren reservierte Höchstkosten (Anbieterpreis) darüber liegen, zählt nicht als geschenkte Auswertung. */
  inviteGiftMaxCostMicro: number;
  inviteExpiryDays: number;
  maxTokensCap: number;
  maxBodyBytes: number;
  imageTokensReserve: number;
  prices: Record<string, Price>;
  /** Store-Pakete: Preis im Store (inkl. USt und Store-Gebühr, offen ausgewiesen) und Guthaben. */
  packages: Record<string, { priceMicro: number; creditMicro: number }>;
  /** Pakete der Kaufseite: Endpreis (Kleinunternehmer, keine USt); Guthaben = Preis (1:1). */
  webPackages: Record<string, { priceMicro: number }>;
  webPurchaseOff: boolean;
  webPurchaseUrl: string;
  webPurchaseChannels: string[];
  webLinkTtlSec: number;
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
