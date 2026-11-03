package app.overfit.ui.ai

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import app.overfit.ai.KeyValidation
import app.overfit.ai.LlmProvider
import app.overfit.i18n.LocalFuelStrings
import app.overfit.ai.ProviderId
import app.overfit.ai.Providers
import app.overfit.i18n.LocalStrings
import app.overfit.ui.components.Hairline
import app.overfit.ui.components.Icon
import app.overfit.ui.components.KeyInput
import app.overfit.ui.components.LineIcon
import app.overfit.ui.components.ListRow
import app.overfit.ui.components.PrimaryButton
import app.overfit.ui.components.Screen
import app.overfit.ui.components.Text
import app.overfit.ui.components.TextButton
import app.overfit.ui.components.Title
import app.overfit.ui.components.Tone
import app.overfit.ui.theme.Space
import app.overfit.ui.theme.Theme
import kotlinx.coroutines.launch

private enum class Status { IDLE, CHECKING, OK, INVALID, NETWORK }

/** Choose a provider, paste the key, validate it without spending tokens. */
@Composable
fun ConnectAiScreen(
    providers: Providers,
    initialProvider: ProviderId,
    header: @Composable () -> Unit,
    onConnected: (ProviderId, String) -> Unit,
    onSkip: (() -> Unit)? = null,
    /** Opens the Fuel screen (coach credit); null hides the Fuel option. */
    onUseFuel: (() -> Unit)? = null,
) {
    val t = LocalStrings.current
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val uriHandler = LocalUriHandler.current
    var providerId by rememberSaveable { mutableStateOf(initialProvider) }
    var key by remember { mutableStateOf("") }
    var status by remember { mutableStateOf(Status.IDLE) }
    val fuelStrings = LocalFuelStrings.current
    val provider = providers.get(providerId)
    val fuelSelected = !provider.usesKey
    val trimmed = key.trim()

    val connect: () -> Unit = connect@{
        if (trimmed.isEmpty() || status == Status.CHECKING) return@connect
        status = Status.CHECKING
        scope.launch {
            status = when (provider.validateKey(trimmed)) {
                KeyValidation.OK -> Status.OK
                KeyValidation.INVALID -> Status.INVALID
                KeyValidation.NETWORK -> Status.NETWORK
            }
            if (status == Status.OK) {
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                onConnected(providerId, trimmed)
            } else {
                haptics.performHapticFeedback(HapticFeedbackType.Reject)
            }
        }
    }

    Screen(
        scroll = true,
        header = header,
        footer = {
            if (fuelSelected) {
                PrimaryButton(label = fuelStrings.aiFuelOpen, onClick = { onUseFuel?.invoke() })
            } else PrimaryButton(
                label = if (status == Status.OK) t.aiConnected else t.aiConnect,
                onClick = connect,
                enabled = trimmed.isNotEmpty(),
                loading = status == Status.CHECKING,
            )
            if (onSkip != null) {
                TextButton(t.aiLater, onSkip, Modifier.align(androidx.compose.ui.Alignment.CenterHorizontally))
            }
        },
    ) {
        Title(t.aiTitle)
        Spacer(Modifier.height(Space.m))
        Text(t.aiSubtitle, tone = Tone.Muted)

        Spacer(Modifier.height(Space.xl))
        Column {
            Hairline()
            (if (onUseFuel != null) listOf(providers.fuel) else emptyList<LlmProvider>()).plus(providers.all).forEach { p ->
                val active = p.id == providerId
                ListRow(
                    label = p.label,
                    tone = if (active) Tone.Default else Tone.Muted,
                    onClick = { providerId = p.id; status = Status.IDLE },
                    trailing = if (active) ({ LineIcon(Icon.Check, size = Space.l) }) else null,
                )
            }
        }

        Spacer(Modifier.height(Space.l))
        if (fuelSelected) {
            Text(fuelStrings.aiFuelDescription, tone = Tone.Muted)
        } else KeyInput(
            value = key,
            onValueChange = { key = it; if (status != Status.CHECKING) status = Status.IDLE },
            placeholder = "${t.aiKeyPlaceholder} (${provider.keyPlaceholder})",
            onDone = connect,
        )
        val message = when (status) {
            Status.INVALID -> t.aiInvalid
            Status.NETWORK -> t.aiNetwork
            else -> null
        }
        if (message != null) {
            Spacer(Modifier.height(Space.m))
            Text(message, style = Theme.type.caption)
        }

        if (!fuelSelected) {
            Spacer(Modifier.height(Space.l))
            TextButton(t.aiHowTo, { uriHandler.openUri(provider.keyUrl) }, tone = Tone.Default)
        }
        Spacer(Modifier.height(Space.l))
        Text(t.aiPrivacy, style = Theme.type.caption, tone = Tone.Muted)
    }
}
