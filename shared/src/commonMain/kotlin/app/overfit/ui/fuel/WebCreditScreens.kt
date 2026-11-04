package app.overfit.ui.fuel

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.overfit.domain.Language
import app.overfit.fuel.Distribution
import app.overfit.fuel.FuelConfig
import app.overfit.fuel.FuelException
import app.overfit.fuel.FuelService
import app.overfit.fuel.FuelSession
import app.overfit.fuel.formatEuro
import app.overfit.fuel.showsWebPurchase
import app.overfit.i18n.LocalFuelStrings
import app.overfit.i18n.LocalStrings
import app.overfit.i18n.webCreditStringsFor
import app.overfit.ui.components.BackHeader
import app.overfit.ui.components.BigInput
import app.overfit.ui.components.Hint
import app.overfit.ui.components.ListRow
import app.overfit.ui.components.Panel
import app.overfit.ui.components.PrimaryButton
import app.overfit.ui.components.Screen
import app.overfit.ui.components.Text
import app.overfit.ui.components.Title
import app.overfit.ui.components.Tone
import app.overfit.ui.theme.Space
import app.overfit.ui.theme.Theme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Credit screen: "Top up on the web" (only when the server enables it for this build's channel) and "Redeem code".
 * The web purchase opens the browser with a short-lived link; the balance is booked by the server and reloaded when the
 * app comes back ([FuelScreen] reloads on resume, the return link `overfit://guthaben` opens this screen).
 */
@Composable
fun WebCreditSection(
    service: FuelService,
    config: FuelConfig?,
    distribution: Distribution,
    language: Language,
    isDemo: Boolean,
    onRedeemCode: () -> Unit,
) {
    val w = webCreditStringsFor(language)
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    var opening by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val web = !isDemo && config != null && config.showsWebPurchase(distribution)
    Spacer(Modifier.height(Space.m))
    Panel {
        if (web && config != null) {
            if (config.webPurchase.packages.isNotEmpty()) {
                config.webPurchase.packages.forEach { p ->
                    ListRow(w.webPackage(formatEuro(p.creditMicro, language), formatEuro(p.priceMicro, language)), Tone.Muted)
                }
            }
            ListRow(if (opening) w.webTopUpOpening else w.webTopUp, onClick = {
                if (opening) return@ListRow
                opening = true
                failed = false
                scope.launch {
                    try {
                        val link = service.webPurchaseLink(if (language == Language.EN) "en" else "de")
                        uriHandler.openUri(link.url)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        failed = true
                    }
                    opening = false
                }
            })
        }
        ListRow(w.redeemCode, onClick = onRedeemCode)
    }
    if (web && config != null) {
        if (config.webPurchase.packages.isNotEmpty()) Hint(w.webChannel)
        Hint(if (config.webPurchase.testMode) "${w.webTopUpNote} ${w.webTopUpTestNote}" else w.webTopUpNote)
        if (failed) Text(w.webTopUpFailed, Modifier.padding(start = Space.m, top = Space.xs), style = Theme.type.caption)
    }
}

/** Credit → "Redeem code": enter a code bought on the web (or received as a gift). */
@Composable
fun RedeemCodeScreen(session: FuelSession, onBack: () -> Unit) {
    val t = LocalFuelStrings.current
    val w = webCreditStringsFor(t.language)
    val isDemo by session.demo.collectAsStateWithLifecycle()
    val service = remember(isDemo) { session.current() }
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    fun redeem() {
        if (service == null || input.isBlank() || busy) return
        busy = true
        message = null
        scope.launch {
            message = try {
                val r = service.redeemCode(input)
                input = ""
                w.redeemDone(formatEuro(r.creditedMicro, t.language))
            } catch (e: CancellationException) {
                throw e
            } catch (e: FuelException) {
                when (e.code) {
                    "invalid_code" -> w.redeemInvalid
                    "already_redeemed" -> w.redeemUsed
                    "code_revoked" -> w.redeemRevoked
                    else -> t.unreachable
                }
            } catch (_: Exception) {
                t.unreachable
            }
            busy = false
        }
    }

    Screen(
        scroll = true,
        header = { BackHeader(LocalStrings.current.back, onBack) },
        footer = { PrimaryButton(w.redeemAction, onClick = ::redeem, enabled = service != null && input.isNotBlank(), loading = busy) },
    ) {
        Title(w.redeemTitle)
        Spacer(Modifier.height(Space.m))
        Text(w.redeemText, tone = Tone.Muted)
        Spacer(Modifier.height(Space.l))
        BigInput(input, { input = formatCodeInput(it); message = null }, w.redeemPlaceholder, onDone = ::redeem)
        message?.let {
            Spacer(Modifier.height(Space.m))
            Text(it)
        }
    }
}

/** Upper case, only letters, digits and dashes (the server ignores dashes and spaces). */
fun formatCodeInput(raw: String): String = raw.uppercase().filter { it.isLetterOrDigit() || it == '-' }.take(16)
