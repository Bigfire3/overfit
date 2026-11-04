package app.overfit.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.overfit.ai.ProviderId
import app.overfit.ai.Providers
import app.overfit.chat.ChatConnection
import app.overfit.chat.ChatFeature
import app.overfit.chat.ChatSyncProblem
import app.overfit.data.AppState
import app.overfit.i18n.ChatStrings
import app.overfit.i18n.LocalChatStrings
import app.overfit.i18n.LocalStrings
import app.overfit.i18n.Strings
import app.overfit.share.PlatformShare
import app.overfit.ui.components.ConfirmButton
import app.overfit.ui.components.BackHeader
import app.overfit.ui.components.Hairline
import app.overfit.ui.components.Hint
import app.overfit.ui.components.ListRow
import app.overfit.ui.components.Panel
import app.overfit.ui.components.PanelItem
import app.overfit.ui.components.PanelSection
import app.overfit.ui.components.PrimaryButton
import app.overfit.ui.components.PromptView
import app.overfit.ui.components.Screen
import app.overfit.ui.components.SectionTitle
import app.overfit.ui.components.Segmented
import app.overfit.ui.components.Text
import app.overfit.ui.components.SmallButton
import app.overfit.ui.components.Title
import app.overfit.ui.components.Tone
import app.overfit.ui.theme.Space
import app.overfit.ui.theme.Theme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/** The three chat platforms. [displayName] is also what the step texts are keyed on. */
enum class ChatProvider(val displayName: String) {
    CLAUDE("Claude"), CHATGPT("ChatGPT"), GEMINI("Gemini");

    companion object { fun of(key: String) = entries.firstOrNull { it.name == key } ?: CLAUDE }
}

internal fun formatMillis(ms: Long, t: Strings): String {
    val dt = Instant.fromEpochMilliseconds(ms).toLocalDateTime(TimeZone.currentSystemDefault())
    return "${t.date(dt.date)} ${dt.hour.toString().padStart(2, '0')}:${dt.minute.toString().padStart(2, '0')}"
}

/** "9:41" from seconds. */
internal fun countdownText(seconds: Long): String {
    val s = seconds.coerceAtLeast(0)
    return "${s / 60}:${(s % 60).toString().padStart(2, '0')}"
}

internal fun ChatError.text(c: ChatStrings): String = when (this) {
    ChatError.NETWORK -> c.errNetwork
    ChatError.NOT_CONFIGURED -> c.errNotConfigured
    ChatError.GENERIC -> c.errGeneric
}

// ---------------------------------------------------------------------------------------------
// KI-Coach: the five ways

@Composable
fun AiCoachScreen(
    state: AppState,
    providers: Providers,
    chat: ChatFeature,
    onBack: () -> Unit,
    onOwnKey: () -> Unit,
    onFuel: () -> Unit,
    onProvider: (ChatProvider) -> Unit,
    /** In-app coach (phase 4): model, photo consent, instructions. */
    onCoachSettings: (() -> Unit)? = null,
    /** Removes the stored key (confirm pattern); moved here from the profile with the Coach tab. */
    onDisconnect: (() -> Unit)? = null,
) {
    val t = LocalStrings.current
    val c = LocalChatStrings.current
    val sync by chat.sync.state.collectAsStateWithLifecycle()
    Screen(scroll = true, header = { BackHeader(t.back, onBack) }) {
        Title(c.aiCoach)
        Spacer(Modifier.height(Space.m))
        Text(c.aiCoachIntro, tone = Tone.Muted)

        PanelSection(c.waysInApp) {
            ListRow(
                if (state.hasApiKey && state.providerId != ProviderId.FUEL) "${c.ownKey} · ${providers.get(state.providerId).label}" else c.ownKey,
                if (state.hasApiKey) c.wayConnected else c.wayNotConnected,
                onClick = onOwnKey,
            )
            ListRow(c.fuelWay, if (state.providerId == ProviderId.FUEL) c.wayConnected else c.wayNotConnected, onClick = onFuel)
            if (onCoachSettings != null) ListRow(app.overfit.i18n.LocalCoachStrings.current.settingsTitle, onClick = onCoachSettings)
        }
        Hint(c.wayInAppHint)
        if (onDisconnect != null && state.hasApiKey) {
            Spacer(Modifier.height(Space.m))
            app.overfit.ui.components.ConfirmButton(t.disconnect, onDisconnect)
        }

        val value = when {
            !chat.configured -> c.waySoon
            sync.enabled -> c.wayConnected
            else -> c.wayNotConnected
        }
        PanelSection(c.waysInChat) {
            ChatProvider.entries.forEach { p -> ListRow(p.displayName, value, onClick = { onProvider(p) }) }
        }
        Hint(c.wayInChatHint)
    }
}

// ---------------------------------------------------------------------------------------------
// One chat platform

@Composable
fun ChatProviderScreen(
    provider: ChatProvider,
    chat: ChatFeature,
    viewModel: ChatViewModel,
    platform: PlatformShare,
    today: LocalDate,
    clock: () -> Long,
    onBack: () -> Unit,
) {
    val t = LocalStrings.current
    val c = LocalChatStrings.current
    val ui by viewModel.state.collectAsStateWithLifecycle()
    val sync by chat.sync.state.collectAsStateWithLifecycle()
    val inbox by chat.inbox.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var consent by remember { mutableStateOf(false) }
    var photos by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf<String?>(null) }
    val url = chat.serverUrl()

    LaunchedEffect(sync.enabled) { if (chat.configured && sync.enabled) viewModel.refresh() }

    if (consent && !sync.enabled) {
        Screen(
            scroll = true,
            header = { BackHeader(t.back, { consent = false }) },
            footer = {
                ui.error?.let { Text(it.text(c), style = Theme.type.caption) }
                PrimaryButton(c.consentAgree, onClick = { viewModel.enable(photos) }, loading = ui.busy || sync.busy)
                SmallButton(c.consentCancel, onClick = { consent = false })
            },
        ) {
            Title(c.consentTitle)
            Spacer(Modifier.height(Space.m))
            c.consentPoints.forEach { Text("• $it", Modifier.padding(bottom = Space.s), tone = Tone.Muted) }
            if (sync.busy) {
                Spacer(Modifier.height(Space.m))
                Text(if (sync.total > 0) c.uploading(sync.done, sync.total) else c.uploadingNoCount, style = Theme.type.caption)
            }
            Spacer(Modifier.height(Space.l))
            Panel { app.overfit.ui.components.SwitchRow(c.consentPhotos, photos) { photos = it } }
            Hint(c.consentPhotosHint)
        }
        return
    }

    Screen(
        scroll = true,
        header = { BackHeader(t.back, onBack) },
        footer = {
            ui.error?.let { Text(it.text(c), style = Theme.type.caption) }
            when {
                !chat.configured -> Unit
                !sync.enabled -> PrimaryButton(c.connectChat, onClick = { consent = true })
                else -> PrimaryButton(
                    if (ui.pairing != null) c.pairNew else c.pairShow,
                    onClick = viewModel::startPairing,
                    loading = ui.busy,
                )
            }
        },
    ) {
        Title(c.chatTitle(provider.displayName))
        Spacer(Modifier.height(Space.m))
        Text(c.chatIntro(provider.displayName), tone = Tone.Muted)

        if (url == null) {
            SectionTitle(c.soonTitle)
            Text(c.soonText, tone = Tone.Muted)
        } else {
            PanelSection(c.serverUrl) {
                ListRow(url, onClick = { platform.copyText(url); copied = "url" }) {
                    Text(if (copied == "url") c.serverUrlCopied else c.serverUrlCopy, style = Theme.type.caption, tone = Tone.Muted)
                }
            }
        }

        SectionTitle(c.stepsTitle)
        c.steps(provider.displayName, url ?: "https://…/mcp").forEachIndexed { i, step ->
            Row(Modifier.fillMaxWidth().padding(bottom = Space.s)) {
                Text("${i + 1}.", Modifier.padding(end = Space.s), tone = Tone.Muted)
                Text(step)
            }
        }
        if (provider == ChatProvider.GEMINI) {
            SectionTitle(c.geminiAltTitle)
            Text(c.geminiAltText, tone = Tone.Muted)
        }

        if (chat.configured && sync.enabled) {
            PanelSection(c.chatOn) {
                ListRow(sync.lastSyncAt?.let { c.lastSync(formatMillis(it, t)) } ?: c.neverSynced) {
                    SmallButton(c.syncNow, onClick = chat.sync::flush)
                }
            }
            if (sync.busy && sync.total > 0) Text(c.uploading(sync.done, sync.total), Modifier.padding(vertical = Space.s), style = Theme.type.caption)
            when (sync.problem) {
                ChatSyncProblem.OFFLINE -> Text(c.problemOffline, Modifier.padding(vertical = Space.s), style = Theme.type.caption, tone = Tone.Muted)
                ChatSyncProblem.QUOTA -> Text(c.problemQuota, Modifier.padding(vertical = Space.s), style = Theme.type.caption)
                ChatSyncProblem.FAILED -> Text(c.problemFailed, Modifier.padding(vertical = Space.s), style = Theme.type.caption, tone = Tone.Muted)
                ChatSyncProblem.NONE -> Unit
            }
            if (inbox.full) Text(c.inboxFull, Modifier.padding(vertical = Space.s), style = Theme.type.caption)

            PairingSection(ui, clock, c)

            val connections = ui.status?.connections.orEmpty()
            PanelSection(c.connections) {
                if (connections.isEmpty()) PanelItem { Text(c.connectionsNone, tone = Tone.Muted) }
                connections.forEach { ConnectionRow(it, t, c) { viewModel.revoke(it.id) } }
            }

            Spacer(Modifier.height(Space.m))
            Panel {
                app.overfit.ui.components.SwitchRow(c.photosToggle, sync.photos) { viewModel.setPhotos(it) }
            }
            Hint(c.photosToggleHint)
        }

        // The prompts show their full text before copying (shared component with the Coach tab).
        SectionTitle(c.contextCopy)
        Hairline()
        val contextText by androidx.compose.runtime.produceState<String?>(null, today) { value = chat.contextText(c, today) }
        PromptView(title = c.contextCopy, hint = c.contextHint, text = contextText, onCopy = platform::copyText)
        PromptView(title = c.gemCopy, hint = c.gemHint, text = remember(today) { chat.gemText(c, today) }, onCopy = platform::copyText)

        if (chat.configured && sync.enabled) {
            Spacer(Modifier.height(Space.l))
            ConfirmButton(c.disableChat, onConfirm = { viewModel.disable() })
            Text(c.disableHint, Modifier.padding(vertical = Space.s), style = Theme.type.caption, tone = Tone.Muted)
        }
    }
}

@Composable
private fun PairingSection(ui: ChatViewModel.UiState, clock: () -> Long, c: ChatStrings) {
    val pairing = ui.pairing
    SectionTitle(c.pairTitle)
    when {
        pairing != null -> {
            var now by remember(pairing) { mutableStateOf(clock()) }
            LaunchedEffect(pairing) { while (true) { delay(1000); now = clock() } }
            val remaining = pairing.ttlSec - (now - ui.pairingAt) / 1000
            Text(c.pairText, tone = Tone.Muted)
            Spacer(Modifier.height(Space.l))
            Panel {
                PanelItem {
                    Text(
                        if (remaining > 0) pairing.code else "– – – –",
                        Modifier.fillMaxWidth(),
                        style = Theme.type.hero.copy(fontSize = 52.sp, letterSpacing = 6.sp, fontFeatureSettings = "tnum"),
                        align = TextAlign.Center,
                    )
                    Spacer(Modifier.height(Space.s))
                    Text(
                        if (remaining > 0) c.pairExpires(countdownText(remaining)) else c.pairExpired,
                        Modifier.fillMaxWidth(),
                        style = Theme.type.caption,
                        tone = Tone.Muted,
                        align = TextAlign.Center,
                    )
                }
            }
        }
        ui.newClient != null -> Text(c.pairConnected(ui.newClient), style = Theme.type.label)
        else -> Text(c.pairText, tone = Tone.Muted)
    }
}

@Composable
private fun ConnectionRow(conn: ChatConnection, t: Strings, c: ChatStrings, onRevoke: () -> Unit) {
    PanelItem {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(conn.clientName.ifBlank { "Chat" }, style = Theme.type.label)
                Text(
                    conn.lastUsedAt?.let { c.lastUsed(formatMillis(it * 1000, t)) } ?: c.neverUsed,
                    style = Theme.type.caption,
                    tone = Tone.Muted,
                )
            }
            SmallButton(c.disconnect, onClick = onRevoke)
        }
    }
}
