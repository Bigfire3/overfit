package app.overfit.fuel

import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Cross-checks the signature scheme against WebCrypto (the backend's verifier) and against a real JVM ECDSA key. */
class SignatureVectorTest {
    // Generated with WebCrypto (node): ECDSA P-256 over the canonical string of the contract example.
    private val pub = "BIXpQAdyxlgIflfDjScfp-sTATKxHp-YJo9o3cCIctCXu9qGUSLx6UjO6LmlPlYoqOsthCZVGMXj-aDUGTBKqs4"
    private val sig = "q-1HTbcu6CzIgdgSlvpbqEn3bYoxdlKVKlklFn13M8sbjJWJSxpaEVbP91vcLJkQE5bT-ef8M09xMm0qA5Sodw"
    private val deviceId = "AA00sx2vrWdwAuOcHDVoH34Ht9SaHcRRA8Iy-owiL6Y"
    private val canonical = RequestSigner.canonicalString(
        "PUT", "/v1/account/profile", "1762128000", "q3xK9mZc2YpWb7LdEoT5Ag", """{"displayName":"Fabian"}""".encodeToByteArray(),
    )

    private val spkiPrefix = "3059301306072a8648ce3d020106082a8648ce3d030107034200".chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    private fun rawToDer(raw: ByteArray): ByteArray {
        fun int(b: ByteArray): ByteArray {
            var v: ByteArray = b.dropWhile { it == 0.toByte() }.toByteArray()
            if (v.isEmpty()) v = byteArrayOf(0)
            if (v[0] < 0) v = byteArrayOf(0) + v
            return byteArrayOf(2, v.size.toByte()) + v
        }
        val body = int(raw.copyOfRange(0, 32)) + int(raw.copyOfRange(32, 64))
        return byteArrayOf(0x30, body.size.toByte()) + body
    }

    @Test
    fun deviceIdMatchesWebCrypto() {
        assertEquals(deviceId, deviceIdOf(pub.fromBase64Url()))
    }

    @Test
    fun webCryptoSignatureVerifiesOverOurCanonicalString() {
        val key = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(spkiPrefix + pub.fromBase64Url()))
        val ok = Signature.getInstance("SHA256withECDSA").apply { initVerify(key); update(canonical.encodeToByteArray()) }
            .verify(rawToDer(sig.fromBase64Url()))
        assertTrue(ok, "canonical string must be byte-identical to the one the backend verifies")
    }

    @Test
    fun ourSignerProducesVerifiableDerSignature() {
        val pair = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
        val key = object : DeviceKey {
            override fun publicPoint() = rawPointOf(pair.public.encoded)
            override fun sign(message: ByteArray) = Signature.getInstance("SHA256withECDSA").apply { initSign(pair.private); update(message) }.sign()
            override fun delete() = Unit
        }
        val h = RequestSigner(key).sign("PUT", "/v1/account/profile", """{"displayName":"Fabian"}""".encodeToByteArray(), 1762128000, "q3xK9mZc2YpWb7LdEoT5Ag")
        assertEquals(deviceIdOf(key.publicPoint()), h.device)
        val ok = Signature.getInstance("SHA256withECDSA").apply { initVerify(pair.public); update(canonical.encodeToByteArray()) }.verify(h.signature.fromBase64Url())
        assertTrue(ok)
    }
}
