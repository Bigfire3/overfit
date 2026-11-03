package app.overfit.fuel

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/** Wire models of the Fuel/Spot backend (docs/backend-api.md). All amounts are integer µ€. */
@Serializable
data class ExpiringItem(val amountMicro: Long, val expiresAt: Long)

@Serializable
data class SpotStatus(val invitedThisMonth: Int = 0, val monthlyLimit: Int = 0, val redeemedStarter: Boolean = false)

@Serializable
data class FuelAccount(
    val accountId: String = "",
    val balanceMicro: Long = 0,
    val expiring: List<ExpiringItem> = emptyList(),
    val displayName: String? = null,
    val hasRecoveryCode: Boolean = false,
    val spot: SpotStatus = SpotStatus(),
)

@Serializable
data class LedgerEntry(
    val id: Long,
    val ts: Long,
    /** usage | purchase | spot_bonus | spot_reward */
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
)

@Serializable
data class LedgerPage(val entries: List<LedgerEntry> = emptyList(), val next: Long? = null)

@Serializable
data class FuelPackage(val productId: String, val priceMicro: Long, val creditMicro: Long)

@Serializable
data class SpotConfig(
    val starterMicro: Long = 1_000_000,
    val rewardMicro: Long = 1_000_000,
    val tokenTtlSec: Int = 45,
    val monthlyLimitPerInviter: Int = 10,
    val expiryDays: Int = 180,
)

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
)

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
    /** `test` = test server (no device check, test credit); anything else = production. */
    val mode: String = "production",
    val markupBp: Int = 0,
    val storeFeeBp: Int = 0,
    val maxTokensCap: Int = 8192,
    val defaultTier: String = "standard",
    /** standard | pro | lite → availability and price. Empty = old backend (only the model `claude-sonnet-5-5`). */
    val tiers: Map<String, FuelTierInfo> = emptyMap(),
    /** coach | chat | autofill → typical tokens, for the cost estimate. Empty = old backend. */
    val usageProfiles: Map<String, UsageProfile> = emptyMap(),
    val packages: List<FuelPackage> = emptyList(),
    val webPurchase: WebPurchaseConfig = WebPurchaseConfig(),
    val spot: SpotConfig = SpotConfig(),
)

@Serializable
data class SpotToken(val token: String, val expiresAt: Long, val ttlSec: Int = 45)

@Serializable
data class SpotChallenge(val challenge: String, val expiresAt: Long = 0)

@Serializable
data class SpotInviter(val displayName: String? = null)

@Serializable
data class SpotRedeemResult(
    val grantedMicro: Long,
    val expiresAt: Long = 0,
    val inviter: SpotInviter = SpotInviter(),
    val balanceMicro: Long = 0,
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
)

/** Proof that the device is genuine (Play Integrity token / App Attest object), bound to a server challenge. */
data class Attestation(val platform: String, val token: String)

/** Error from the backend or on the way there. [code] is the API error code (or a local one like "network"). */
class FuelException(val code: String, val status: Int = 0, val details: Map<String, Long> = emptyMap(), message: String? = null) :
    Exception(message ?: code)
