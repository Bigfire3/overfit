package app.overfit.fuel

/** Creates the proof for Spot redemption on this device (Play Integrity / App Attest). */
interface Attestor {
    /** Null = this device cannot attest (e.g. simulator, no Play services); the redeem then ends in `attestation_unavailable`. */
    suspend fun attest(challenge: String): Attestation?
}

object NoAttestor : Attestor {
    override suspend fun attest(challenge: String): Attestation? = null
}

/** What the Fuel/Spot screens need; implemented by the real client ([FuelApi]) and by the offline demo. */
interface FuelService {
    val isDemo: Boolean
    suspend fun config(): FuelConfig
    suspend fun account(): FuelAccount
    suspend fun setDisplayName(name: String?): String?
    suspend fun ledger(before: Long? = null, limit: Int = 50): LedgerPage
    suspend fun createRecoveryCode(): String
    suspend fun restore(code: String): FuelAccount
    suspend fun spotToken(): SpotToken
    suspend fun redeemSpot(token: String): SpotRedeemResult
    /** Sends an Anthropic-style Messages request through Fuel (used by the coach in phase 4). */
    suspend fun messages(bodyJson: String): FuelMessageResult
}

/**
 * Signed access to the backend for other features (KI-Chat-Verbindung): the device identity, signature, registration and
 * clock correction of [FuelApi]. Returns the JSON body of a 2xx answer as text; any other answer throws [FuelException].
 */
interface SignedTransport {
    suspend fun request(method: String, path: String, body: ByteArray? = null, contentType: String = "application/json"): String
}
