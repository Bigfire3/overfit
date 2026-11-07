package app.overfit.fuel

/** What the credit screens need; implemented by the real client ([FuelApi]) and by the offline demo. */
interface FuelService {
    val isDemo: Boolean
    suspend fun config(): FuelConfig
    suspend fun account(): FuelAccount
    suspend fun ledger(before: Long? = null, limit: Int = 50): LedgerPage
    /** Request log of the account (newest first); every AI request through the credit, also failed ones. */
    suspend fun requests(before: Long? = null, limit: Int = 50): ServerRequestPage = ServerRequestPage()
    suspend fun createRecoveryCode(): String
    suspend fun restore(code: String): FuelAccount
    /** Sends an Anthropic-style Messages request through Fuel (used by the coach in phase 4). */
    suspend fun messages(bodyJson: String): FuelMessageResult
    /** Short-lived, single-use link to the web purchase page for this account (opened in the browser). */
    suspend fun webPurchaseLink(locale: String): WebPurchaseLink = throw FuelException("web_purchase_unavailable", 503)
    /** Redeems a credit code bought on the web. Errors: `invalid_code`, `already_redeemed`, `code_revoked`. */
    suspend fun redeemCode(code: String): CodeRedeemResult = throw FuelException("invalid_code", 404)
}

/**
 * Signed access to the backend for other features (KI-Chat-Verbindung): the device identity, signature, registration and
 * clock correction of [FuelApi]. Returns the JSON body of a 2xx answer as text; any other answer throws [FuelException].
 */
interface SignedTransport {
    suspend fun request(method: String, path: String, body: ByteArray? = null, contentType: String = "application/json"): String
}
