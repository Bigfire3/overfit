@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package app.overfit.platform

import app.overfit.fuel.DeviceKey
import app.overfit.fuel.rawPointOf
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArrayOf
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFErrorRefVar
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFRetain
import platform.CoreFoundation.CFTypeRef
import platform.CoreFoundation.CFTypeRefVar
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.SecKeyCopyExternalRepresentation
import platform.Security.SecKeyCopyPublicKey
import platform.Security.SecKeyCreateRandomKey
import platform.Security.SecKeyCreateSignature
import platform.Security.SecKeyRef
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleWhenUnlockedThisDeviceOnly
import platform.Security.kSecAttrApplicationTag
import platform.Security.kSecAttrIsPermanent
import platform.Security.kSecAttrKeySizeInBits
import platform.Security.kSecAttrKeyType
import platform.Security.kSecAttrKeyTypeECSECPrimeRandom
import platform.Security.kSecAttrTokenID
import platform.Security.kSecAttrTokenIDSecureEnclave
import platform.Security.kSecClass
import platform.Security.kSecClassKey
import platform.Security.kSecKeyAlgorithmECDSASignatureMessageX962SHA256
import platform.Security.kSecPrivateKeyAttrs
import platform.Security.kSecReturnRef

/** Bridges a CoreFoundation constant (e.g. `kSecClass`) to an object usable as a Kotlin map key/value without over-releasing it. */
internal fun cf(ref: CFTypeRef?): Any? = CFBridgingRelease(CFRetain(ref))

internal fun <T> withCfDictionary(map: Map<Any?, Any?>, block: (CFDictionaryRef?) -> T): T {
    val retained = CFBridgingRetain(map)
    try {
        return block(retained?.reinterpret())
    } finally {
        CFBridgingRelease(retained)
    }
}

internal fun ByteArray.toNSData(): NSData = memScoped {
    NSData.create(bytes = allocArrayOf(this@toNSData), length = size.toULong())
}

internal fun NSData.toByteArray(): ByteArray = if (length.toInt() == 0) ByteArray(0) else bytes!!.readBytes(length.toInt())

/**
 * ECDSA P-256 device identity: Secure Enclave on devices (key never leaves the chip), a Keychain software key
 * where there is no Secure Enclave (simulator). Signs `ECDSASignatureMessageX962SHA256` (DER), which the backend accepts.
 */
class IosDeviceKey(private val tag: String = "app.overfit.deviceKey") : DeviceKey {
    private val tagData: NSData get() = NSString.create(string = tag).dataUsingEncoding(NSUTF8StringEncoding)!!

    private fun lookup(): SecKeyRef? = memScoped {
        val result = alloc<CFTypeRefVar>()
        val query = mapOf<Any?, Any?>(
            cf(kSecClass) to cf(kSecClassKey),
            cf(kSecAttrApplicationTag) to tagData,
            cf(kSecAttrKeyType) to cf(kSecAttrKeyTypeECSECPrimeRandom),
            cf(kSecReturnRef) to true,
        )
        val status = withCfDictionary(query) { SecItemCopyMatching(it, result.ptr) }
        if (status == errSecSuccess) result.value?.reinterpret() else null
    }

    private fun create(secureEnclave: Boolean): SecKeyRef? = memScoped {
        val privateAttrs = mapOf<Any?, Any?>(
            cf(kSecAttrIsPermanent) to true,
            cf(kSecAttrApplicationTag) to tagData,
            cf(kSecAttrAccessible) to cf(kSecAttrAccessibleWhenUnlockedThisDeviceOnly),
        )
        val attrs = mutableMapOf<Any?, Any?>(
            cf(kSecAttrKeyType) to cf(kSecAttrKeyTypeECSECPrimeRandom),
            cf(kSecAttrKeySizeInBits) to 256,
            cf(kSecPrivateKeyAttrs) to privateAttrs,
        )
        if (secureEnclave) attrs[cf(kSecAttrTokenID)] = cf(kSecAttrTokenIDSecureEnclave)
        val error = alloc<CFErrorRefVar>()
        withCfDictionary(attrs) { SecKeyCreateRandomKey(it, error.ptr) }
    }

    private fun key(): SecKeyRef = lookup() ?: create(secureEnclave = true) ?: create(secureEnclave = false)
        ?: error("Could not create device key")

    override fun publicPoint(): ByteArray {
        val publicKey = SecKeyCopyPublicKey(key()) ?: error("No public key")
        val data = CFBridgingRelease(SecKeyCopyExternalRepresentation(publicKey, null)) as NSData
        return rawPointOf(data.toByteArray())
    }

    override fun sign(message: ByteArray): ByteArray {
        val data = CFBridgingRetain(message.toNSData())
        try {
            val signature = SecKeyCreateSignature(key(), kSecKeyAlgorithmECDSASignatureMessageX962SHA256, data?.reinterpret(), null)
            return (CFBridgingRelease(signature) as NSData).toByteArray()
        } finally {
            CFBridgingRelease(data)
        }
    }

    override fun delete() {
        val query = mapOf<Any?, Any?>(
            cf(kSecClass) to cf(kSecClassKey),
            cf(kSecAttrApplicationTag) to tagData,
            cf(kSecAttrKeyType) to cf(kSecAttrKeyTypeECSECPrimeRandom),
        )
        withCfDictionary(query) { SecItemDelete(it) }
    }
}
