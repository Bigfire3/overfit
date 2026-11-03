@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package app.overfit.platform

import app.overfit.fuel.Attestation
import app.overfit.fuel.Attestor
import app.overfit.fuel.Sha256
import app.overfit.fuel.toBase64Url
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.DeviceCheck.DCAppAttestService
import kotlin.coroutines.resume

/**
 * App Attest: generates a key, attests it with SHA-256(challenge) as the client data hash.
 * Token = JSON `{"keyId":"…","attestation":"<base64url>"}`. No App Attest (simulator, old devices) → null,
 * which ends in `attestation_unavailable` on the server and a friendly message in the UI.
 */
class IosAttestor : Attestor {
    override suspend fun attest(challenge: String): Attestation? {
        val service = DCAppAttestService.sharedService
        if (!service.isSupported()) return null
        val keyId = suspendCancellableCoroutine<String?> { cont ->
            service.generateKeyWithCompletionHandler { id, error -> if (cont.isActive) cont.resume(if (error == null) id else null) }
        } ?: return null
        val hash = Sha256.hash(challenge.encodeToByteArray()).toNSData()
        val attestation = suspendCancellableCoroutine<ByteArray?> { cont ->
            service.attestKey(keyId, clientDataHash = hash) { data, error ->
                if (cont.isActive) cont.resume(if (error == null) data?.toByteArray() else null)
            }
        } ?: return null
        val token = """{"keyId":"$keyId","attestation":"${attestation.toBase64Url()}"}"""
        return Attestation("apple", token)
    }
}
