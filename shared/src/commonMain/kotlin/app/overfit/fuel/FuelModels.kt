package app.overfit.fuel

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
    val inputTokens: Long = 0,
    val cacheReadTokens: Long = 0,
    val cacheWriteTokens: Long = 0,
    val outputTokens: Long = 0,
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

@Serializable
data class FuelConfig(
    /** `test` = test server (no device check, test credit); anything else = production. */
    val mode: String = "production",
    val markupBp: Int = 0,
    val storeFeeBp: Int = 0,
    val maxTokensCap: Int = 8192,
    val packages: List<FuelPackage> = emptyList(),
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
data class FuelMessageResult(val body: JsonElement, val costMicro: Long, val balanceMicro: Long)

/** Proof that the device is genuine (Play Integrity token / App Attest object), bound to a server challenge. */
data class Attestation(val platform: String, val token: String)

/** Error from the backend or on the way there. [code] is the API error code (or a local one like "network"). */
class FuelException(val code: String, val status: Int = 0, val details: Map<String, Long> = emptyMap(), message: String? = null) :
    Exception(message ?: code)
