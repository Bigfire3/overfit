package app.overfit.ui.coach

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.overfit.ai.ProviderId
import app.overfit.ai.Providers
import app.overfit.chat.ChatFeature
import app.overfit.coach.ChatMessage
import app.overfit.coach.ChatMessageStatus
import app.overfit.coach.CoachChatService
import app.overfit.coach.CoachService
import app.overfit.coach.PromptEntry
import app.overfit.coach.ReviewKind
import app.overfit.coach.ReviewStatus
import app.overfit.data.AppState
import app.overfit.data.DiaryPhoto
import app.overfit.data.db.CoachChatEntity
import app.overfit.data.db.CoachChatMessageEntity
import app.overfit.data.db.CoachMemoryEntity
import app.overfit.fuel.FuelSession
import app.overfit.fuel.formatCost
import app.overfit.fuel.formatEuro
import app.overfit.i18n.LocalChatStrings
import app.overfit.i18n.LocalCoachStrings
import app.overfit.i18n.LocalFuelStrings
import app.overfit.i18n.LocalStrings
import app.overfit.share.SharedTraining
import app.overfit.ui.chat.ChatProvider
import app.overfit.ui.chat.NoteBody
import app.overfit.ui.components.BackHeader
import app.overfit.ui.components.Hairline
import app.overfit.ui.components.ListRow
import app.overfit.ui.components.MultilineInput
import app.overfit.ui.components.PromptView
import app.overfit.ui.components.Screen
import app.overfit.ui.components.SectionTitle
import app.overfit.ui.components.Text
import app.overfit.ui.components.SmallButton
import app.overfit.ui.components.Title
import app.overfit.ui.components.Tone
import app.overfit.ui.components.pressableRow
import app.overfit.ui.fuel.Loaded
import app.overfit.ui.fuel.rememberLoaded
import app.overfit.ui.history.PhotoThumb
import app.overfit.ui.theme.Space
import app.overfit.ui.theme.Theme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/** Navigation of the Coach tab on top of [CoachNav]. */
class CoachTabNav(
    val coach: CoachNav,
    /** Opens the chat; [chatId] empty = new chat, [draft] goes into the input. */
    val openChat: (chatId: String, draft: String) -> Unit,
    val allReviews: () -> Unit,
    /** AI access: own key, Fuel, disconnect. */
    val access: () -> Unit,
    val chatProvider: (ChatProvider) -> Unit,
    val chatNotes: () -> Unit,
)

// ---------------------------------------------------------------------------------------------
// Tab

/**
 * The Coach tab: AI access, latest review / progress / memory, chat, prompts & templates, AI chat connections. Every
 * coach entry of the app lives here; the profile only links to it.
 */
@Composable
fun CoachTabScreen(
    coach: CoachService,
    chat: CoachChatService,
    chatFeature: ChatFeature,
    state: AppState,
    providers: Providers,
    fuelSession: FuelSession,
    prompts: suspend () -> List<PromptEntry>,
    copy: (String) -> Unit,
    nav: CoachTabNav,
) {
    val c = LocalCoachStrings.current
    val h = c.hub
    val t = LocalStrings.current
    Screen(scroll = true) {
        Title(h.tabTitle)
        Spacer(Modifier.height(Space.l))

        AccessRow(state, providers, fuelSession, nav.access)

        // Coach: latest review, review / progress, memory
        val reviews by coach.reviews.collectAsState(initial = emptyList())
        val latest by coach.latestFinished.collectAsState(initial = null)
        val memory by coach.memory.collectAsState(initial = null)
        SectionTitle(c.reviews)
        Hairline()
        reviews.firstOrNull { it.status == ReviewStatus.DONE }?.let { item ->
            val title = if (item.kind == ReviewKind.PROGRESS) c.progressTitle else c.reviewTitle
            ListRow(h.latestReview(dateText(item.createdAt)), item.review?.rating?.let(c.rating) ?: title, onClick = { nav.coach.openReview(item.id) })
        }
        latest?.let { s -> HintRow(c.reviewLast, c.reviewLastHint) { nav.coach.openSession(s.id) } }
        HintRow(c.assessProgress, c.assessProgressHint) { nav.coach.openReview(coach.startProgressReview()) }
        if (reviews.isNotEmpty()) ListRow(h.allReviews, reviews.size.toString(), onClick = nav.allReviews)
        MemoryRow(memory, nav.coach.memory)
        ListRow(c.settingsTitle, onClick = nav.coach.settings)

        ChatSection(chat, nav)

        // Prompts & templates
        SectionTitle(h.promptsTitle)
        Text(h.promptsIntro, Modifier.padding(bottom = Space.s), style = Theme.type.caption, tone = Tone.Muted)
        Hairline()
        val list by produceState<List<PromptEntry>?>(null, state.language, state.profile, state.weights) { value = prompts() }
        if (list == null) Text(h.promptLoading, Modifier.padding(vertical = Space.m), tone = Tone.Muted)
        list?.forEach { p ->
            PromptView(
                title = p.title,
                hint = p.hint,
                text = p.text,
                onCopy = copy,
                onUseInChat = p.chatAsk?.let { ask -> { nav.openChat("", ask) } },
            )
        }

        // AI chat connections
        val cs = LocalChatStrings.current
        val sync by chatFeature.sync.state.collectAsState()
        SectionTitle(h.connectionsTitle)
        Text(h.connectionsHint, Modifier.padding(bottom = Space.s), style = Theme.type.caption, tone = Tone.Muted)
        Hairline()
        val value = when {
            !chatFeature.configured -> cs.waySoon
            sync.enabled -> cs.wayConnected
            else -> cs.wayNotConnected
        }
        ChatProvider.entries.forEach { p -> ListRow(p.displayName, value, onClick = { nav.chatProvider(p) }) }
        ListRow(cs.notesTitle, onClick = nav.chatNotes)
    }
}

/** "AI access: Own key · Anthropic / Fuel · 4,20 € / No AI access" with the way to set it up. */
@Composable
private fun AccessRow(state: AppState, providers: Providers, fuelSession: FuelSession, onClick: () -> Unit) {
    val h = LocalCoachStrings.current.hub
    val f = LocalFuelStrings.current
    val fuel = state.providerId == ProviderId.FUEL
    val service = if (fuel) fuelSession.current() else null
    val (account, _) = rememberLoaded(service) { service?.account() }
    val label = when {
        fuel && service != null -> h.accessFuel((account as? Loaded.Ready)?.value?.let { app.overfit.ui.fuel.creditSummary(it, f) })
        state.hasApiKey && !fuel -> h.accessOwnKey(providers.get(state.providerId).label)
        else -> h.accessNone
    }
    val ready = (fuel && service != null) || (state.hasApiKey && !fuel)
    Hairline()
    Column(Modifier.fillMaxWidth().pressableRow(onClick).testTag("coach:access")) {
        Row(Modifier.fillMaxWidth().padding(vertical = Space.m), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(h.accessTitle, style = Theme.type.caption, tone = Tone.Muted)
                Text(label, style = Theme.type.label)
            }
            Text(if (ready) h.accessChange else h.accessSetup, style = Theme.type.label, tone = Tone.Muted)
        }
        if (!ready) Text(h.accessNoneHint, Modifier.padding(bottom = Space.m), style = Theme.type.caption, tone = Tone.Muted)
        if (state.hasApiKey && !fuel) {
            Text(
                h.accessOwnKeyPrivacy(providers.get(state.providerId).label),
                Modifier.padding(bottom = Space.m).testTag("coach:access-privacy"), style = Theme.type.caption, tone = Tone.Muted,
            )
        }
        Hairline()
    }
}

@Composable
private fun MemoryRow(memory: CoachMemoryEntity?, onClick: () -> Unit) {
    val t = LocalStrings.current
    Column(Modifier.fillMaxWidth().pressableRow(onClick)) {
        Column(Modifier.fillMaxWidth().padding(vertical = Space.m)) {
            Text(t.coachMemory)
            memory?.text?.takeIf { it.isNotBlank() }?.let { Text(it, style = Theme.type.caption, tone = Tone.Muted, maxLines = 2) }
        }
        Hairline()
    }
}

@Composable
private fun HintRow(label: String, hint: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().pressableRow(onClick)) {
        Column(Modifier.fillMaxWidth().padding(vertical = Space.m)) {
            Text(label)
            Text(hint, style = Theme.type.caption, tone = Tone.Muted)
        }
        Hairline()
    }
}

/** Chat part of the tab: input that continues the latest chat, its last exchange, new chat, earlier chats. */
@Composable
private fun ChatSection(chat: CoachChatService, nav: CoachTabNav) {
    val h = LocalCoachStrings.current.hub
    val chats by chat.chats.collectAsState(initial = emptyList())
    val current = chats.firstOrNull()
    val preview by remember(current?.id) { current?.let { chat.messages(it.id) } ?: flowOf(emptyList()) }.collectAsState(initial = emptyList())
    var input by rememberSaveable { mutableStateOf("") }

    SectionTitle(h.chatTitle)
    Text(h.chatIntro, Modifier.padding(bottom = Space.s), style = Theme.type.caption, tone = Tone.Muted)
    if (!chat.available()) Text(h.chatNoAccess, Modifier.padding(bottom = Space.s), style = Theme.type.caption, tone = Tone.Muted)
    if (current != null && preview.isNotEmpty()) {
        Hairline()
        Column(Modifier.fillMaxWidth().pressableRow { nav.openChat(current.id, "") }.padding(vertical = Space.m)) {
            preview.takeLast(2).forEach { m ->
                Text(
                    (if (m.fromUser) h.you else h.coach) + ": " + previewText(m, h.chatThinking),
                    style = Theme.type.caption,
                    tone = if (m.fromUser) Tone.Muted else Tone.Default,
                    maxLines = 3,
                )
                Spacer(Modifier.height(Space.xs))
            }
            Text(h.chatContinue, style = Theme.type.label)
        }
    }
    MultilineInput(input, { input = it }, h.chatPlaceholder, Modifier.testTag("coach:tabInput"), minHeight = 48.dp)
    Row(Modifier.fillMaxWidth().padding(vertical = Space.s), horizontalArrangement = Arrangement.SpaceBetween) {
        SmallButton(h.chatNew, { nav.openChat("", input.trim()); input = "" })
        SmallButton(h.chatSend, {
            val text = input.trim()
            if (text.isNotEmpty()) {
                val (id, _) = chat.send(current?.id, text)
                input = ""
                nav.openChat(id, "")
            }
        }, tone = if (input.isBlank()) Tone.Muted else Tone.Default)
    }
    val earlier = chats.drop(1).take(EARLIER_CHATS)
    if (earlier.isNotEmpty()) {
        Text(h.chatEarlier, Modifier.padding(top = Space.s), style = Theme.type.caption, tone = Tone.Muted)
        Hairline()
        earlier.forEach { ch -> ListRow(ch.title, dateText(ch.updatedAt), onClick = { nav.openChat(ch.id, "") }) }
    }
}

private fun previewText(m: ChatMessage, thinking: String): String = when {
    m.status == ChatMessageStatus.SENDING -> thinking
    m.text.isNotBlank() -> m.text.replace('\n', ' ')
    m.workout != null -> m.workout.name
    else -> "…"
}

private const val EARLIER_CHATS = 5

@Composable
private fun dateText(ms: Long): String =
    LocalStrings.current.date(Instant.fromEpochMilliseconds(ms).toLocalDateTime(TimeZone.currentSystemDefault()).date)

// ---------------------------------------------------------------------------------------------
// Chat

/**
 * One coach chat. [chatId] empty = new chat (created with the first message). Photos only go along when the user
 * picks them under "Attach photo". A proposed workout shows a card whose "Use workout" opens the import preview.
 */
@Composable
fun CoachChatScreen(
    chat: CoachChatService,
    chatId: String,
    draft: String,
    photos: Flow<List<DiaryPhoto>>,
    onImportWorkout: (String) -> Unit,
    onAccess: () -> Unit,
    onBack: () -> Unit,
) {
    val h = LocalCoachStrings.current.hub
    val t = LocalStrings.current
    var id by rememberSaveable { mutableStateOf(chatId) }
    var input by rememberSaveable { mutableStateOf(draft) }
    var picking by remember { mutableStateOf(false) }
    val attached = remember { mutableStateListOf<String>() }
    val messages by remember(id) { if (id.isEmpty()) flowOf(emptyList()) else chat.messages(id) }.collectAsState(initial = emptyList())
    val list = rememberLazyListState()
    // New message or an answer that arrived: show the end of the chat.
    LaunchedEffect(messages.size, messages.lastOrNull()?.status) { if (messages.isNotEmpty()) list.animateScrollToItem(messages.size - 1) }

    Screen(
        header = { BackHeader(t.back, onBack) },
        footer = {
            if (picking) PhotoPicker(photos, attached)
            if (attached.isNotEmpty()) Text(h.attachedPhotos(attached.size), style = Theme.type.caption, tone = Tone.Muted)
            MultilineInput(input, { input = it }, h.chatPlaceholder, Modifier.testTag("coach:chatInput"), minHeight = 48.dp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                SmallButton(h.attachPhoto, { picking = !picking }, tone = if (picking) Tone.Default else Tone.Muted)
                app.overfit.ui.components.PrimaryButton(h.chatSend, {
                    val text = input.trim()
                    if (text.isNotEmpty()) {
                        val (newId, _) = chat.send(id.ifEmpty { null }, text, attached.toList())
                        id = newId
                        input = ""
                        attached.clear()
                        picking = false
                    }
                }, enabled = input.isNotBlank(), modifier = Modifier, compact = true)
            }
        },
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { Title(h.chatTitle) }
            if (id.isNotEmpty()) {
                SmallButton(h.chatNew, { id = ""; input = ""; attached.clear() })
                Spacer(Modifier.width(Space.m))
                // Confirm pattern from the design system: first tap turns red with "Really delete?".
                app.overfit.ui.components.ConfirmButton(
                    h.chatDelete, h.chatDeleteConfirm, { chat.delete(id); id = "" },
                    Modifier, compact = true,
                )
            }
        }
        Spacer(Modifier.height(Space.m))
        if (!chat.available()) {
            Text(h.chatNoAccess, style = Theme.type.caption, tone = Tone.Muted)
            SmallButton(h.accessSetup, onAccess)
            Spacer(Modifier.height(Space.s))
        }
        if (messages.isEmpty()) Text(LocalCoachStrings.current.hub.chatIntro, tone = Tone.Muted)
        LazyColumn(Modifier.fillMaxWidth().weight(1f), state = list) {
            items(messages, key = { it.id }) { m -> MessageItem(m, onImportWorkout, onRetry = { chat.retry(m.id) }) }
        }
    }
}

@Composable
private fun MessageItem(m: ChatMessage, onImportWorkout: (String) -> Unit, onRetry: () -> Unit) {
    val h = LocalCoachStrings.current.hub
    val f = LocalFuelStrings.current
    Column(Modifier.fillMaxWidth().padding(vertical = Space.s).testTag(if (m.fromUser) "chat:user" else "chat:coach")) {
        Text(if (m.fromUser) h.you else h.coach, style = Theme.type.caption, tone = Tone.Muted)
        Spacer(Modifier.height(Space.xs))
        when {
            m.fromUser -> {
                Column(Modifier.fillMaxWidth().background(Theme.colors.surface, RoundedCornerShape(8.dp)).padding(Space.m)) {
                    Text(m.text)
                    if (m.photoIds.isNotEmpty()) Text(h.attachedPhotos(m.photoIds.size), style = Theme.type.caption, tone = Tone.Muted)
                }
            }
            m.status == ChatMessageStatus.SENDING -> Text(h.chatThinking, tone = Tone.Muted)
            m.status == ChatMessageStatus.FAILED -> {
                Text(h.chatFailed, tone = Tone.Muted)
                SmallButton(h.chatRetry, onRetry, tone = Tone.Default)
            }
            else -> {
                NoteBody(m.text)
                val w = m.workout
                if (w != null && m.workoutJson != null) WorkoutCard(w) { onImportWorkout(m.workoutJson) }
                if (m.demo) Text(h.chatDemoHint, Modifier.padding(top = Space.s), style = Theme.type.caption, tone = Tone.Muted)
                m.costMicro?.let { AnswerCostLine(app.overfit.i18n.LocalCoachStrings.current, f.language, it, null, m.servedModel, m.fallbackFrom) }
            }
        }
    }
}

/** Proposed workout: name, exercises, "Use workout" (opens the same import preview as a link). */
@Composable
private fun WorkoutCard(w: SharedTraining, onUse: () -> Unit) {
    val h = LocalCoachStrings.current.hub
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = Space.m)
            .border(1.dp, Theme.colors.hairline, RoundedCornerShape(8.dp))
            .padding(Space.m)
            .testTag("chat:workout"),
    ) {
        Text(h.workoutCard(w.name), style = Theme.type.label)
        Text(h.workoutCardExercises(w.exercises.size), style = Theme.type.caption, tone = Tone.Muted)
        Spacer(Modifier.height(Space.s))
        w.exercises.take(8).forEach { e ->
            val name = e.name ?: w.custom.firstOrNull { it.ref == e.ref }?.name ?: e.id.orEmpty()
            Text("– $name · ${e.sets.size}×", style = Theme.type.caption)
        }
        Spacer(Modifier.height(Space.s))
        SmallButton(h.workoutUse, onUse, tone = Tone.Default)
    }
}

/** Recent workout photos to attach explicitly; tap toggles. */
@Composable
private fun PhotoPicker(photos: Flow<List<DiaryPhoto>>, attached: MutableList<String>) {
    val h = LocalCoachStrings.current.hub
    val t = LocalStrings.current
    val all by photos.collectAsState(initial = null)
    Text(h.attachHint, style = Theme.type.caption, tone = Tone.Muted)
    val recent = all.orEmpty().take(MAX_PICK_PHOTOS)
    if (all != null && recent.isEmpty()) Text(h.noPhotos, style = Theme.type.caption, tone = Tone.Muted)
    LazyRow(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
        items(recent, key = { it.photo.id }) { p ->
            val selected = p.photo.id in attached
            val label = h.photoLabel(t.date(Instant.fromEpochMilliseconds(p.photo.takenAt).toLocalDateTime(TimeZone.currentSystemDefault()).date))
            Box(
                if (selected) Modifier.border(2.dp, Theme.colors.foreground, RoundedCornerShape(6.dp)).padding(2.dp) else Modifier.padding(2.dp),
            ) {
                PhotoThumb(p.photo, label, size = 64.dp, onClick = {
                    if (selected) attached.remove(p.photo.id) else if (attached.size < MAX_ATTACHED) attached.add(p.photo.id)
                })
            }
        }
    }
}

private const val MAX_PICK_PHOTOS = 12
private const val MAX_ATTACHED = 3

// ---------------------------------------------------------------------------------------------
// Backup

/** The coach chats go into the backup (key "coachChat"). */
class CoachChatBackup(private val chat: CoachChatService) : app.overfit.data.backup.BackupContributor {
    override val key = "coachChat"
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Serializable
    private data class Content(
        val chats: List<CoachChatEntity> = emptyList(),
        val messages: List<CoachChatMessageEntity> = emptyList(),
    )

    override suspend fun export(): JsonElement? {
        val (chats, messages) = chat.exportAll()
        return json.encodeToJsonElement(Content.serializer(), Content(chats, messages))
    }

    override suspend fun import(value: JsonElement?) {
        val content = value?.let { runCatching { json.decodeFromJsonElement(Content.serializer(), it) }.getOrNull() } ?: Content()
        chat.importAll(content.chats, content.messages)
    }
}
