package app.overfit.platform

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import app.overfit.fuel.DeviceKey
import app.overfit.fuel.rawPointOf
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec

/**
 * ECDSA P-256 device identity in the Android Keystore. The private key is not exportable and never leaves the device
 * (hardware-backed, StrongBox where available). Signs as `SHA256withECDSA` (DER), which the backend accepts.
 */
class AndroidDeviceKey(private val alias: String = ALIAS) : DeviceKey {
    private fun keyStore() = KeyStore.getInstance(KEYSTORE).apply { load(null) }

    private fun privateKey(): PrivateKey {
        (keyStore().getKey(alias, null) as? PrivateKey)?.let { return it }
        generate(strongBox = Build.VERSION.SDK_INT >= 28) ?: generate(strongBox = false)
        return keyStore().getKey(alias, null) as PrivateKey
    }

    private fun generate(strongBox: Boolean): Unit? = runCatching {
        val spec = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN)
            .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
            .setDigests(KeyProperties.DIGEST_SHA256)
            .apply { if (strongBox && Build.VERSION.SDK_INT >= 28) setIsStrongBoxBacked(true) }
            .build()
        KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, KEYSTORE).apply { initialize(spec) }.generateKeyPair()
        Unit
    }.getOrNull()

    override fun publicPoint(): ByteArray {
        privateKey()
        return rawPointOf(keyStore().getCertificate(alias).publicKey.encoded)
    }

    override fun sign(message: ByteArray): ByteArray =
        Signature.getInstance("SHA256withECDSA").apply { initSign(privateKey()); update(message) }.sign()

    override fun delete() {
        runCatching { keyStore().deleteEntry(alias) }
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "overfit.deviceKey"
    }
}
