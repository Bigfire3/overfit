package app.overfit.platform

import android.content.Context
import app.overfit.fuel.Attestation
import app.overfit.fuel.Attestor
import app.overfit.fuel.Sha256
import app.overfit.fuel.toBase64Url
import com.google.android.play.core.integrity.IntegrityManagerFactory
import com.google.android.play.core.integrity.IntegrityTokenRequest
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Play Integrity token for Spot redemption. The server challenge is bound via the nonce (base64url of its SHA-256).
 * Without Play services (or when the request fails, e.g. sideloaded app without a linked Cloud project) → null, and the
 * server-side result for "no attestation" is `attestation_unavailable`, which the UI explains.
 */
class AndroidAttestor(private val context: Context) : Attestor {
    override suspend fun attest(challenge: String): Attestation? = suspendCancellableCoroutine { cont ->
        try {
            val nonce = Sha256.hash(challenge.encodeToByteArray()).toBase64Url()
            IntegrityManagerFactory.create(context.applicationContext)
                .requestIntegrityToken(IntegrityTokenRequest.builder().setNonce(nonce).build())
                .addOnSuccessListener { if (cont.isActive) cont.resume(Attestation("google", it.token())) }
                .addOnFailureListener { if (cont.isActive) cont.resume(null) }
        } catch (_: Throwable) {
            if (cont.isActive) cont.resume(null)
        }
    }
}
