export interface Env {
  DB: D1Database;
  /** R2-Bucket für freigegebene Körperfotos (KI-Chat-Verbindung), verschlüsselt abgelegt. */
  PHOTOS: R2Bucket;
  /** Cloudflare-AI-Binding: Workers AI (`run`) und AI-Gateway-URLs (`getUrl`). Fehlt im lokalen Dev und in Tests ohne Mock. */
  AI?: AiBinding;
  // KI-Schlüssel je Anbieter (welche nötig sind, hängt von der Voreinstellung in llm-routing.jsonc ab)
  ANTHROPIC_API_KEY?: string;
  OPENAI_API_KEY?: string;
  GEMINI_API_KEY?: string;
  OPENROUTER_API_KEY?: string;
  /** Voreinstellung aus llm-routing.jsonc (claude, openai, gemini, gratis, gemischt, …); leer = `default` der Datei. */
  LLM_PRESET?: string;
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
  /** Nur Tests/lokal: andere API-Basis je Anbieter. */
  ANTHROPIC_BASE_URL?: string;
  OPENAI_BASE_URL?: string;
  GEMINI_BASE_URL?: string;
  OPENROUTER_BASE_URL?: string;
  /** Name des Cloudflare AI Gateways; leer = Anbieter direkt (lokal). Setzt der Deploy-Workflow. */
  AI_GATEWAY_ID?: string;
  /** Token für ein authentifiziertes AI Gateway (`cf-aig-authorization`); ohne Token gehen Anfragen direkt an Anthropic. */
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
  PURCHASE_MODE?: string;
  /** `test` = Testbetrieb (siehe README); alles andere = Produktion. Nur der exakte Wert `test` schaltet etwas frei. */
  DEPLOY_MODE?: string;
  /** Geheimnis für `POST /v1/admin/credit` (nur im Testbetrieb). */
  ADMIN_SECRET?: string;
  TEST_CREDIT_CAP_MICRO?: string;
  MARKUP_BP?: string;
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
  /** Testbetrieb: Test-Guthaben per Admin-Gutschrift, niedrige Limits. Nie in Produktion. */
  testMode: boolean;
  /** Obergrenze für alles Test-Guthaben zusammen (Admin-Gutschriften; früher auch Startguthaben). */
  testCreditCapMicro: number;
  purchaseMode: 'required' | 'mock';
  aiGatewayId: string | null;
  upstreamTimeoutMs: number;
  upstreamBudgetMs: number;
  markupBp: number;
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
