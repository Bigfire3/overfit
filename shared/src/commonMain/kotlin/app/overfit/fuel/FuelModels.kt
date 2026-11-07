package app.overfit.fuel

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Wire models of the credit backend (docs/backend-api.md). All amounts are integer µ€. Credit never expires. Fields an
 * older backend still sends (e.g. `invite`, `giftReviews`, `expiring`) are ignored (`ignoreUnknownKeys`).
 */
@Serializable
data class FuelAccount(
    val accountId: String = "",
    /** Available credit in µ€. */
    val balanceMicro: Long = 0,
    val hasRecoveryCode: Boolean = false,
)

@Serializable
data class LedgerEntry(
    val id: Long,
    val ts: Long,
    /** usage | purchase | code_redeem | refund | chargeback; older accounts may also hold other credit/debit types. */
    val type: String,
    val amountMicro: Long,
    val model: String? = null,
    /** Fuel tier the entry was billed at (standard | pro | lite); null for old entries. */
    val tier: String? = null,
    val inputTokens: Long = 0,
    val cacheReadTokens: Long = 0,
    val cacheWriteTokens: Long = 0,
    val outputTokens: Long = 0,
    /** A replacement model answered (the price of the chosen model, [tier], was charged). */
    val fallback: Boolean = false,
    /** Where credit came from: null = store/internal, `web` = bought on the web page, `code` = redeemed credit code. */
    val source: String? = null,
    /** paid | gifted | used | refunded | expired (from the server; computed locally for old backends). */
    val category: String? = null,
) {
    val kind: LedgerKind
        get() = when (category) {
            "paid" -> LedgerKind.Paid
            "gifted" -> LedgerKind.Credited
            "used" -> LedgerKind.Used
            "refunded" -> LedgerKind.Refunded
            "expired" -> LedgerKind.Deducted
            else -> when (type) {
                "usage" -> LedgerKind.Used
                "purchase", "code_redeem" -> LedgerKind.Paid
                "refund", "chargeback" -> LedgerKind.Refunded
                else -> if (amountMicro < 0) LedgerKind.Used else LedgerKind.Credited
            }
        }
}

/** How a ledger entry is labeled in the app: paid with real money, credited, used, refunded or deducted (`expired`). */
enum class LedgerKind { Paid, Credited, Used, Refunded, Deducted }

@Serializable
data class LedgerPage(val entries: List<LedgerEntry> = emptyList(), val next: Long? = null)

/** One entry of the server's request log (`GET /v1/account/requests`): metadata only, never prompt or answer. */
@Serializable
data class ServerRequest(
    val id: Long,
    /** Unix seconds. */
    val ts: Long,
    val durationMs: Long? = null,
    /** review | chat | autofill | plan | other; null = old app version. */
    val purpose: String? = null,
    val tier: String,
    /** ok | failed */
    val status: String,
    val errorCode: String? = null,
    val requestedModel: String,
    val model: String? = null,
    val fallbackFromStatus: Int? = null,
    val fallbackReason: String? = null,
    val inputTokens: Long = 0,
    val cacheReadTokens: Long = 0,
    val cacheWriteTokens: Long = 0,
    val outputTokens: Long = 0,
    val chargedMicro: Long = 0,
    val photo: Boolean = false,
)

@Serializable
data class ServerRequestPage(val entries: List<ServerRequest> = emptyList(), val next: Long? = null)

@Serializable
data class FuelPackage(val productId: String, val priceMicro: Long, val creditMicro: Long)

/** List price of a model in µ€ per million tokens (without the Fuel markup). */
@Serializable
data class TierPrice(@SerialName("in") val input: Long = 0, val out: Long = 0, val cacheRead: Long = 0, val cacheWrite: Long = 0)

/** One AI tier of the backend (`/v1/config` → `tiers`). */
@Serializable
data class FuelTierInfo(
    val available: Boolean = false,
    val model: String = "",
    val images: Boolean = false,
    val price: TierPrice = TierPrice(),
    /** Real model name and provider, e.g. "Gemini 3.6 Flash" / "Google" (never a marketing name). */
    val displayName: String = "",
    val vendor: String = "",
    /** Typical thinking tokens per coach request (counted as output), only for the cost estimate. */
    val thinkingTokens: Int = 0,
) {
    /** "Gemini 3.6 Flash (Google)"; falls back to the known names of the model ID. */
    val label: String get() = if (displayName.isNotEmpty()) "$displayName ($vendor)" else modelLabel(model)
}

/** Typical token amounts of one task (measured on the real prompts, backend `USAGE_PROFILES`). */
@Serializable
data class UsageProfile(val inputTokens: Long = 0, val outputTokens: Long = 0)

/** Buying credit on the web page (`/v1/config` → `webPurchase`). Off unless the server says otherwise. */
@Serializable
data class WebPurchaseConfig(
    val enabled: Boolean = false,
    /** True = sandbox of the payment provider: no real payment. */
    val testMode: Boolean = true,
    /** Channels in which the app shows "Top up on the web": `sideload` (APK from GitHub), `store` (Play Store, App Store). */
    val channels: List<String> = emptyList(),
    /** Packages of the purchase page: price = credit (1:1). Empty = old backend. */
    val packages: List<WebPackage> = emptyList(),
)

@Serializable
data class WebPackage(val id: String, val priceMicro: Long, val creditMicro: Long)

/** How this build was installed. Store builds hide the web purchase unless the server enables `store` (store rules). */
enum class Distribution(val key: String) { Sideload("sideload"), Store("store") }

/** Whether the "Top up on the web" button is shown for this build. */
fun FuelConfig.showsWebPurchase(distribution: Distribution): Boolean =
    webPurchase.enabled && distribution.key in webPurchase.channels

@Serializable
data class WebPurchaseLink(val url: String, val expiresAt: Long = 0, val ttlSec: Int = 900)

@Serializable
data class CodeRedeemResult(val creditedMicro: Long, val balanceMicro: Long = 0)

@Serializable
data class FuelConfig(
    /** `test` = test server (sandbox, nothing real); anything else = production. */
    val mode: String = "production",
    val markupBp: Int = 0,
    val maxTokensCap: Int = 8192,
    val defaultTier: String = "standard",
    /** standard | pro | lite → availability and price. Empty = old backend (only the model `claude-sonnet-5-5`). */
    val tiers: Map<String, FuelTierInfo> = emptyMap(),
    /** coach | chat | autofill → typical tokens, for the cost estimate. Empty = old backend. */
    val usageProfiles: Map<String, UsageProfile> = emptyMap(),
    val packages: List<FuelPackage> = emptyList(),
    val webPurchase: WebPurchaseConfig = WebPurchaseConfig(),
)

@Serializable
data class RecoveryCode(val code: String)

/** Answer of POST /v1/messages: the unchanged Anthropic JSON plus what the call cost. */
data class FuelMessageResult(
    val body: JsonElement,
    val costMicro: Long,
    val balanceMicro: Long,
    /** Model ID that really answered (null with an old backend). */
    val servedModel: String? = null,
    /** Model ID the user chose and whose price was charged; differs from [servedModel] on a fallback. */
    val chosenModel: String? = null,
    /** ID of the server's request log entry (`/v1/account/requests`). */
    val requestId: Long? = null,
    /** Why the chosen model did not answer on a fallback ("HTTP 529: Overloaded"); not charged. */
    val fallbackReason: String? = null,
)

/** Error from the backend or on the way there. [code] is the API error code (or a local one like "network"). */
class FuelException(val code: String, val status: Int = 0, val details: Map<String, Long> = emptyMap(), message: String? = null) :
    Exception(message ?: code)
