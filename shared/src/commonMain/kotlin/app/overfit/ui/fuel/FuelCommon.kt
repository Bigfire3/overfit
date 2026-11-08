package app.overfit.ui.fuel

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.overfit.fuel.FuelException
import app.overfit.i18n.FuelStrings
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

/** "4,20 € Guthaben", while requests run "4,20 € Guthaben (0,61 € reserviert)". */
fun creditSummary(account: app.overfit.fuel.FuelAccount, t: FuelStrings): String {
    val balance = t.creditAmount(app.overfit.fuel.formatEuro(account.balanceMicro, t.language))
    return reservedNote(account.reservedMicro, t)?.let { "$balance $it" } ?: balance
}

/** "(0,61 € reserviert)" while credit is held for running requests, else null. */
fun reservedNote(reservedMicro: Long, t: FuelStrings): String? =
    if (reservedMicro > 0) t.reservedNote(app.overfit.fuel.formatEuro(reservedMicro, t.language)) else null
