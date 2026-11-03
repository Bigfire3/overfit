package app.overfit.ui.fuel

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.overfit.fuel.FuelException
import app.overfit.i18n.FuelStrings
import app.overfit.ui.theme.Theme
import kotlinx.coroutines.CancellationException

sealed interface Loaded<out T> {
    data object Loading : Loaded<Nothing>
    data class Failed(val code: String) : Loaded<Nothing>
    data class Ready<T>(val value: T) : Loaded<T>
}

/** Runs [block] whenever [key] changes or the returned function is called; errors become [Loaded.Failed]. */
@Composable
fun <T> rememberLoaded(key: Any?, block: suspend () -> T): Pair<Loaded<T>, () -> Unit> {
    var nonce by remember { mutableIntStateOf(0) }
    val state = produceState<Loaded<T>>(Loaded.Loading, key, nonce) {
        value = Loaded.Loading
        value = try {
            Loaded.Ready(block())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Loaded.Failed((e as? FuelException)?.code ?: "network")
        }
    }
    return state.value to { nonce++ }
}

/** Two-pixel progress line on a hairline track (rotation of the invite code, etc.). */
@Composable
fun HairlineProgress(fraction: Float, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(2.dp).background(Theme.colors.hairline)) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(2.dp).background(Theme.colors.foreground))
    }
}

/** Friendly text for an invite redeem error. */
fun inviteErrorText(code: String, t: FuelStrings): String = when (code) {
    "invite_token_invalid", "challenge_invalid", "invalid_request" -> t.inviteErrInvalid
    "invite_token_used" -> t.inviteErrUsed
    "invite_token_expired" -> t.inviteErrExpired
    "invite_self" -> t.inviteErrSelf
    "invite_already_redeemed" -> t.inviteErrAlready
    "invite_device_used" -> t.inviteErrDevice
    "invite_inviter_limit" -> t.inviteErrInviterLimit
    "invite_budget_exhausted" -> t.inviteErrBudget
    "attestation_unavailable", "attestation_failed" -> t.inviteErrAttestation
    "rate_limited" -> t.inviteErrRateLimited
    "network", "upstream_unavailable" -> t.inviteErrNetwork
    else -> t.inviteErrGeneric
}

/** "4,20 € Guthaben · 5 geschenkte Auswertungen" (the second part only when there are gifted reviews). */
fun creditSummary(account: app.overfit.fuel.FuelAccount, t: FuelStrings): String {
    val credit = t.creditAmount(app.overfit.fuel.formatEuro(account.balanceMicro, t.language))
    val reviews = account.giftReviews.remaining
    return if (reviews > 0) "$credit · ${t.giftReviews(reviews)}" else credit
}
