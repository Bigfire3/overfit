package app.overfit.fuel

/** Header names of the signature scheme (docs/backend-api.md). */
object FuelHeaders {
    const val DEVICE = "X-Overfit-Device"
    const val TIMESTAMP = "X-Overfit-Timestamp"
    const val NONCE = "X-Overfit-Nonce"
    const val SIGNATURE = "X-Overfit-Signature"
}

class SignedHeaders(val device: String, val timestamp: String, val nonce: String, val signature: String)

/**
 * Signs requests exactly as specified in docs/backend-api.md: ECDSA-SHA-256 over
 * `OVERFIT-V1 \n METHOD \n path+query \n timestamp \n nonce \n hex(sha256(body))`.
 */
class RequestSigner(private val key: DeviceKey) {
    val deviceId: String get() = deviceIdOf(key.publicPoint())

    fun publicKeyBase64Url(): String = key.publicPoint().toBase64Url()

    fun sign(method: String, pathAndQuery: String, body: ByteArray, timestampSec: Long, nonce: String): SignedHeaders {
        val text = canonicalString(method, pathAndQuery, timestampSec.toString(), nonce, body)
        return SignedHeaders(deviceId, timestampSec.toString(), nonce, key.sign(text.encodeToByteArray()).toBase64Url())
    }

    companion object {
        fun canonicalString(method: String, pathAndQuery: String, timestamp: String, nonce: String, body: ByteArray): String =
            listOf("OVERFIT-V1", method.uppercase(), pathAndQuery, timestamp, nonce, Sha256.hash(body).toHex()).joinToString("\n")
    }
}
