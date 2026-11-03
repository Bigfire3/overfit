package app.overfit.fuel

/**
 * The device identity: an ECDSA P-256 key pair whose private key never leaves the device
 * (Android Keystore, iOS Secure Enclave; simulator: Keychain). Implementations are created per platform.
 */
interface DeviceKey {
    /** Uncompressed public point `0x04‖X‖Y` (65 bytes). */
    fun publicPoint(): ByteArray

    /** ECDSA-SHA-256 signature over [message]; DER or raw `r‖s` – the backend accepts both. */
    fun sign(message: ByteArray): ByteArray

    /** Deletes the key (reset of the app). A new key is created on the next use. */
    fun delete()
}

/** Device ID = base64url(SHA-256(65-byte raw public point)), 43 characters. */
fun deviceIdOf(publicPoint: ByteArray): String = Sha256.hash(publicPoint).toBase64Url()

/** Extracts the 65-byte point from either a raw point or an SPKI-DER public key (Android `publicKey.encoded`). */
fun rawPointOf(publicKey: ByteArray): ByteArray = when {
    publicKey.size == 65 && publicKey[0] == 0x04.toByte() -> publicKey
    publicKey.size >= 65 && publicKey[publicKey.size - 65] == 0x04.toByte() -> publicKey.copyOfRange(publicKey.size - 65, publicKey.size)
    else -> throw IllegalArgumentException("Unexpected public key format")
}
