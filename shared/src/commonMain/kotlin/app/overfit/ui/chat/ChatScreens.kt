package app.overfit.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.backhandler.BackHandler
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
import app.overfit.ui.components.AnimatedItems
import app.overfit.ui.components.ConfirmButton
import app.overfit.ui.components.BackHeader
import app.overfit.ui.components.Hairline
import app.overfit.ui.components.Hint
import app.overfit.ui.components.Icon
import app.overfit.ui.components.LineIcon
import app.overfit.ui.components.ProgressLine
import app.overfit.ui.components.Reveal
import app.overfit.ui.components.RevealNotNull
import app.overfit.ui.components.RollingText
import app.overfit.ui.components.Swap
import app.overfit.ui.components.rememberHaptics
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
import app.overfit.ui.theme.IconSize
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
            ValueRow(
                if (state.hasApiKey && state.providerId != ProviderId.FUEL) "${c.ownKey} · ${providers.get(state.providerId).label}" else c.ownKey,
                if (state.hasApiKey) c.wayConnected else c.wayNotConnected,
                onClick = onOwnKey,
            )
            ValueRow(c.fuelWay, if (state.providerId == ProviderId.FUEL) c.wayConnected else c.wayNotConnected, onClick = onFuel)
            if (onCoachSettings != null) ListRow(app.overfit.i18n.LocalCoachStrings.current.settingsTitle, onClick = onCoachSettings)
        }
        Hint(c.wayInAppHint)
        Reveal(onDisconnect != null && state.hasApiKey) {
            Column {
                Spacer(Modifier.height(Space.m))
                ConfirmButton(t.disconnect, { onDisconnect?.invoke() })
            }
        }

        val value = when {
            !chat.configured -> c.waySoon
            sync.enabled -> c.wayConnected
            else -> c.wayNotConnected
        }
        PanelSection(c.waysInChat) {
            ChatProvider.entries.forEach { p -> ValueRow(p.displayName, value, onClick = { onProvider(p) }) }
        }
        Hint(c.wayInChatHint)
    }
}

/** [ListRow] with a value whose text fades over when it changes ("Verbunden" ↔ "Nicht verbunden"). */
@Composable
private fun ValueRow(label: String, value: String, onClick: () -> Unit) {
    ListRow(label, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Swap(value, contentAlignment = Alignment.CenterEnd) { Text(it, tone = Tone.Muted) }
            Spacer(Modifier.width(Space.xs))
            LineIcon(Icon.ChevronRight, size = IconSize.inline, color = Theme.colors.muted)
        }
    }
}

/** Error in the footer: slides in above the main button instead of pushing it with a jump. */
@Composable
private fun ErrorLine(error: ChatError?, c: ChatStrings) {
    RevealNotNull(error, from = Alignment.Bottom) { Text(it.text(c), style = Theme.type.caption) }
}

/** "Lade hoch … 3 von 20" with a bar that moves along; comes and goes with the upload. */
@Composable
private fun UploadProgress(visible: Boolean, done: Int, total: Int, c: ChatStrings, modifier: Modifier = Modifier) {
    Reveal(visible) {
        Column(modifier.fillMaxWidth()) {
            RollingText(if (total > 0) c.uploading(done, total) else c.uploadingNoCount, style = Theme.type.caption)
            Reveal(total > 0) {
                Column {
                    Spacer(Modifier.height(Space.xs))
                    ProgressLine(if (total > 0) done.toFloat() / total else 1f)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// One chat platform

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
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

    LaunchedEffect(sync.enabled) { if (chat.configured && sync.enabled) viewModel.refresh() }
    val haptics = rememberHaptics()
    LaunchedEffect(ui.error) { if (ui.error != null) haptics.reject() }
    // "Adresse kopiert" goes back to "Kopieren" after a moment, like the prompts.
    LaunchedEffect(copied) { if (copied != null) { delay(3000); copied = null } }

    val showConsent = consent && !sync.enabled
    // System back leaves only the consent, not the whole page.
    BackHandler(enabled = showConsent) { consent = false }

    Swap(showConsent) { inConsent ->
        if (inConsent) {
            Screen(
                scroll = true,
                header = { BackHeader(t.back, { consent = false }) },
                footer = {
                    ErrorLine(ui.error, c)
                    PrimaryButton(c.consentAgree, onClick = { viewModel.enable(photos) }, loading = ui.busy || sync.busy)
                    SmallButton(c.consentCancel, onClick = { consent = false })
                },
            ) {
                Title(c.consentTitle)
                Spacer(Modifier.height(Space.m))
                c.consentPoints.forEach { Text("• $it", Modifier.padding(bottom = Space.s), tone = Tone.Muted) }
                UploadProgress(sync.busy, sync.done, sync.total, c, Modifier.padding(top = Space.m))
                Spacer(Modifier.height(Space.l))
                Panel { app.overfit.ui.components.SwitchRow(c.consentPhotos, photos) { photos = it } }
                Hint(c.consentPhotosHint)
            }
        } else {
            ProviderMain(
                provider, chat, viewModel, platform, today, clock, onBack, ui, sync, inbox.full, copied,
                onCopied = { copied = it }, onConsent = { consent = true },
            )
        }
    }
}

@Composable
private fun ProviderMain(
    provider: ChatProvider,
    chat: ChatFeature,
    viewModel: ChatViewModel,
    platform: PlatformShare,
    today: LocalDate,
    clock: () -> Long,
    onBack: () -> Unit,
    ui: ChatViewModel.UiState,
    sync: app.overfit.chat.ChatSyncState,
    inboxFull: Boolean,
    copied: String?,
    onCopied: (String) -> Unit,
    onConsent: () -> Unit,
) {
    val t = LocalStrings.current
    val c = LocalChatStrings.current
    val haptics = rememberHaptics()
    val url = chat.serverUrl()
    val chatOn = chat.configured && sync.enabled
    Screen(
        scroll = true,
        header = { BackHeader(t.back, onBack) },
        footer = {
            ErrorLine(ui.error, c)
            when {
                !chat.configured -> Unit
                !sync.enabled -> PrimaryButton(c.connectChat, onClick = onConsent)
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
                ListRow(url, onClick = { platform.copyText(url); onCopied("url"); haptics.confirm() }) {
                    Swap(copied == "url", contentAlignment = Alignment.CenterEnd) { done ->
                        Text(if (done) c.serverUrlCopied else c.serverUrlCopy, style = Theme.type.caption, tone = Tone.Muted)
                    }
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

        // Everything that belongs to "chat on" comes and goes as one block.
        Reveal(chatOn) {
            Column {
                PanelSection(c.chatOn) {
                    ListRow(sync.lastSyncAt?.let { c.lastSync(formatMillis(it, t)) } ?: c.neverSynced) {
                        SmallButton(c.syncNow, onClick = chat.sync::flush)
                    }
                }
                UploadProgress(sync.busy && sync.total > 0, sync.done, sync.total, c, Modifier.padding(vertical = Space.s))
                RevealNotNull(sync.problem.takeIf { it != ChatSyncProblem.NONE }) { problem ->
                    when (problem) {
                        ChatSyncProblem.QUOTA -> Text(c.problemQuota, Modifier.padding(vertical = Space.s), style = Theme.type.caption)
                        ChatSyncProblem.OFFLINE -> Text(c.problemOffline, Modifier.padding(vertical = Space.s), style = Theme.type.caption, tone = Tone.Muted)
                        else -> Text(c.problemFailed, Modifier.padding(vertical = Space.s), style = Theme.type.caption, tone = Tone.Muted)
                    }
                }
                Reveal(inboxFull) { Text(c.inboxFull, Modifier.padding(vertical = Space.s), style = Theme.type.caption) }

                PairingSection(ui, clock, c)

                val connections = ui.status?.connections.orEmpty()
                PanelSection(c.connections) {
                    Reveal(connections.isEmpty()) { PanelItem { Text(c.connectionsNone, tone = Tone.Muted) } }
                    AnimatedItems(connections, key = { it.id }) { _, conn -> ConnectionRow(conn, t, c) { viewModel.revoke(conn.id) } }
                }

                Spacer(Modifier.height(Space.m))
                Panel {
                    app.overfit.ui.components.SwitchRow(c.photosToggle, sync.photos) { viewModel.setPhotos(it) }
                }
                Hint(c.photosToggleHint)
            }
        }

        // The prompts show their full text before copying (shared component with the Coach tab).
        SectionTitle(c.contextCopy)
        Hairline()
        val contextText by androidx.compose.runtime.produceState<String?>(null, today) { value = chat.contextText(c, today) }
        PromptView(title = c.contextCopy, hint = c.contextHint, text = contextText, onCopy = platform::copyText)
        PromptView(title = c.gemCopy, hint = c.gemHint, text = remember(today) { chat.gemText(c, today) }, onCopy = platform::copyText)

        Reveal(chatOn) {
            Column {
                Spacer(Modifier.height(Space.l))
                ConfirmButton(c.disableChat, onConfirm = { viewModel.disable() })
                Text(c.disableHint, Modifier.padding(vertical = Space.s), style = Theme.type.caption, tone = Tone.Muted)
            }
        }
    }
}

@Composable
private fun PairingSection(ui: ChatViewModel.UiState, clock: () -> Long, c: ChatStrings) {
    val haptics = rememberHaptics()
    // Feel it when the chat has picked up the code (not when the screen opens on an earlier connection).
    val initialClient = remember { ui.newClient }
    LaunchedEffect(ui.newClient) { if (ui.newClient != null && ui.newClient != initialClient) haptics.confirm() }
    SectionTitle(c.pairTitle)
    // No code → code → connected in one place: the content fades over and the height follows.
    Swap(
        ui.pairing to ui.newClient,
        contentKey = { (p, n) -> if (p != null) 1 else if (n != null) 2 else 0 },
    ) { (pairing, newClient) ->
        when {
            pairing != null -> {
                var now by remember(pairing) { mutableStateOf(clock()) }
                LaunchedEffect(pairing) { while (true) { delay(1000); now = clock() } }
                val remaining = pairing.ttlSec - (now - ui.pairingAt) / 1000
                Column {
                    Text(c.pairText, tone = Tone.Muted)
                    Spacer(Modifier.height(Space.l))
                    Panel {
                        PanelItem {
                            Swap(remaining > 0, Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { valid ->
                                Text(
                                    if (valid) pairing.code else "– – – –",
                                    Modifier.fillMaxWidth(),
                                    style = Theme.type.hero.copy(fontSize = 52.sp, letterSpacing = 6.sp, fontFeatureSettings = "tnum"),
                                    align = TextAlign.Center,
                                )
                            }
                            Spacer(Modifier.height(Space.s))
                            Swap(remaining > 0, Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { valid ->
                                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                    if (valid) {
                                        // Live countdown: only the changing digits roll, in the "now" colour.
                                        RollingText(
                                            c.pairExpires(countdownText(remaining)),
                                            style = Theme.type.caption,
                                            color = Theme.colors.accent,
                                            countsDown = true,
                                        )
                                    } else {
                                        Text(c.pairExpired, Modifier.fillMaxWidth(), style = Theme.type.caption, tone = Tone.Muted, align = TextAlign.Center)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            newClient != null -> Text(c.pairConnected(newClient), Modifier.fillMaxWidth(), style = Theme.type.label)
            else -> Text(c.pairText, Modifier.fillMaxWidth(), tone = Tone.Muted)
        }
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
            ConfirmButton(c.disconnect, onConfirm = onRevoke, modifier = Modifier, compact = true)
        }
    }
}
