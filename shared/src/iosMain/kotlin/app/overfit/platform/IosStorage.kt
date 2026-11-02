@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package app.overfit.platform

import app.overfit.data.KeyValueStore
import app.overfit.data.SecretStore
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFMutableDictionaryRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDefaults
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData

class IosKeyValueStore : KeyValueStore {
    private val defaults = NSUserDefaults.standardUserDefaults
    override fun get(key: String): String? = defaults.stringForKey(key)
    override fun put(key: String, value: String) = defaults.setObject(value, forKey = key)
    override fun remove(key: String) = defaults.removeObjectForKey(key)
}

/** Stores the API key in the iOS Keychain (this device only, no iCloud sync). */
class KeychainSecretStore(
    private val service: String = "app.overfit",
    private val account: String = "apiKey",
) : SecretStore {

    /** Builds a Keychain query; bridged values are released after [block] runs. */
    private fun <T> query(vararg extra: Pair<Any?, Any?>, block: (CFMutableDictionaryRef?) -> T): T {
        val retained = mutableListOf<Any?>()
        val dict = CFDictionaryCreateMutable(null, 0, kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr)
        fun bridge(value: Any?) = CFBridgingRetain(value).also { retained += it }
        CFDictionaryAddValue(dict, kSecClass, kSecClassGenericPassword)
        CFDictionaryAddValue(dict, kSecAttrService, bridge(service))
        CFDictionaryAddValue(dict, kSecAttrAccount, bridge(account))
        try {
            for ((key, value) in extra) {
                @Suppress("UNCHECKED_CAST")
                CFDictionaryAddValue(dict, key as kotlinx.cinterop.CPointer<*>?, value as kotlinx.cinterop.CPointer<*>?)
            }
            return block(dict)
        } finally {
            retained.forEach { CFBridgingRelease(it as kotlinx.cinterop.CPointer<*>?) }
            CFRelease(dict)
        }
    }

    override fun load(): String? = memScoped {
        val result = alloc<CFTypeRefVar>()
        val status = query(kSecReturnData to kCFBooleanTrue, kSecMatchLimit to kSecMatchLimitOne) {
            SecItemCopyMatching(it, result.ptr)
        }
        if (status != errSecSuccess) return null
        val data = CFBridgingRelease(result.value) as? NSData ?: return null
        NSString.create(data = data, encoding = NSUTF8StringEncoding)?.toString()
    }

    override fun save(value: String) {
        delete()
        val data = NSString.create(string = value).dataUsingEncoding(NSUTF8StringEncoding) ?: return
        val cfData = CFBridgingRetain(data)
        try {
            query(kSecValueData to cfData, kSecAttrAccessible to kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly) {
                SecItemAdd(it, null)
            }
        } finally {
            CFBridgingRelease(cfData)
        }
    }

    override fun delete() {
        query { SecItemDelete(it) }
    }
}
