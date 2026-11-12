package app.overfit.ui.coach.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.overfit.ai.ProviderId
import app.overfit.coach.ChatAttachment
import app.overfit.coach.ChatMessage
import app.overfit.coach.ChatMessageStatus
import app.overfit.coach.ChatSendRequest
import app.overfit.coach.ChatCost
import app.overfit.coach.ChatPricing
import app.overfit.coach.TokenEstimate
import app.overfit.i18n.LocalStrings
import kotlinx.coroutines.launch
import app.overfit.coach.key
import app.overfit.data.DiaryPhoto
import app.overfit.i18n.CoachChatStrings
import app.overfit.i18n.LocalCoachStrings
import app.overfit.i18n.LocalFuelStrings
import app.overfit.i18n.coachChatStringsFor
import app.overfit.share.SharedTraining
import app.overfit.ui.chat.NoteBody
import app.overfit.ui.coach.AnswerCostLine
import app.overfit.ui.components.Icon
import app.overfit.ui.components.LineIcon
import app.overfit.ui.components.PressScale
import app.overfit.ui.components.PrimaryButton
import app.overfit.ui.components.SmallButton
import app.overfit.ui.components.Text
import app.overfit.ui.components.Title
import app.overfit.ui.components.Tone
import app.overfit.ui.components.ButtonShape
import app.overfit.ui.components.pressScale
import app.overfit.ui.components.textInputArea
import app.overfit.ui.history.PhotoThumb
import app.overfit.ui.theme.Space
import app.overfit.ui.theme.Theme
import kotlinx.coroutines.flow.flowOf

/** Texts of the chat in the app language. */
@Composable
internal fun chatStrings(): CoachChatStrings = coachChatStringsFor(LocalFuelStrings.current.language)

/** Chips of a chat question are kept across configuration changes as their stored JSON. */
private val AttachmentListSaver = Saver<List<ChatAttachment>, String>(
    save = { ChatAttachment.encode(it).orEmpty() },
    restore = { ChatAttachment.decode(it) },
)

/** Side gutter of the chat (wider content than the 24 dp of other screens). */
private val Gutter = Space.m

/**
 * The Coach tab: one chat. Empty chat = three suggestions; conversation with the user's bubbles on the right and the
 * coach's Markdown answers on the left; context chips (profile, last training, everything from "+") above one rounded
 * composer block with "+", model chip, cost estimate and send. [openRequest] opens a chat from elsewhere (CoachChatRoute,
 * automatic review).
 */
@Composable
fun CoachChatTabScreen(
    env: CoachChatEnv,
    openRequest: ChatOpenRequest?,
    onOpenRequestConsumed: (ChatOpenRequest) -> Unit,
    onHistory: () -> Unit,
    onStory: () -> Unit,
    onModelChip: () -> Unit,
    onApplyWorkout: (String) -> Unit,
    creditChip: @Composable () -> Unit = {},
    onCamera: (() -> Unit)? = null,
    /**
     * Opens exactly this earlier chat in its own screen ("Earlier chats" → tap): back arrow instead of story, list and
     * new chat; the user can keep writing in it.
     */
    openedChatId: String? = null,
    onBack: () -> Unit = {},
    /** Opened from a screen (coach icon): its context as chips, a close button instead of the tab's header icons. */
    overlay: CoachOverlayRequest? = null,
    onClose: () -> Unit = {},
    /** Proposal cards apply to the screen the coach was opened from (diff + apply); null = the normal import. */
    proposals: ProposalHandler? = null,
) {
    val s = chatStrings()
    var chatId by rememberSaveable { mutableStateOf(openedChatId.orEmpty()) }
    var input by rememberSaveable { mutableStateOf("") }
    var pending by rememberSaveable(stateSaver = AttachmentListSaver) { mutableStateOf(overlay?.attachments ?: emptyList()) }
    /** New chat: the user removed the automatic "last training" chip (an opened coach brings its own context). */
    var trainingRemoved by rememberSaveable { mutableStateOf(overlay != null) }
    /** New chat: the user removed the profile chip. */
    var newProfileOff by rememberSaveable { mutableStateOf(false) }
    var sheet by rememberSaveable { mutableStateOf(false) }
    var profilePopup by rememberSaveable { mutableStateOf(false) }

    val messages by remember(chatId) { if (chatId.isEmpty()) flowOf(emptyList()) else env.chat.messages(chatId) }.collectAsState(initial = emptyList())
    val chatRow by remember(chatId) { if (chatId.isEmpty()) flowOf(null) else env.chat.chat(chatId) }.collectAsState(initial = null)
    /** The open chat's row was there once: if it disappears again, the chat was deleted (earlier chats, delete all data). */
    var chatSeen by remember(chatId) { mutableStateOf(false) }
    val last by env.lastTraining.collectAsState(initial = null)
    val photos by env.photos.collectAsState(initial = emptyList())
    val templateNames by env.templateNames.collectAsState(initial = emptySet())
    val modelLabel by env.modelLabel.collectAsState()
    val pricing by env.pricing.collectAsState()

    // Until the chat row of a just sent first question exists, the local choice holds (no chip flashing back).
    val profileIncluded = if (chatId.isEmpty()) !newProfileOff else chatRow?.let { !it.profileOff } ?: !newProfileOff
    val autoTraining = last?.takeIf { t -> chatId.isEmpty() && !trainingRemoved && pending.none { it.key() == t.key() } }
    val chips = listOfNotNull(autoTraining) + pending
    // Live estimate of what the next question sends: rebuilt from the real prompt when the chat, its attachments or the
    // profile chip change; what is typed is added on the fly.
    var baseTokens by remember { mutableStateOf<TokenEstimate?>(null) }
    LaunchedEffect(chatId, chips, profileIncluded, messages.size) {
        // Small pause: quick changes in a row build the prompt once. The last estimate stays until the new one is there.
        kotlinx.coroutines.delay(150)
        try {
            baseTokens = env.chat.estimate(chatId.ifEmpty { null }, ChatSendRequest("", chips, profileIncluded))
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) { }
    }
    val language = LocalFuelStrings.current.language
    val price = sendPriceLabel(pricing, baseTokens?.plusText(input), s, language)

    fun newChat() {
        chatId = ""; input = ""; pending = emptyList(); trainingRemoved = false; newProfileOff = false
    }

    fun send(text: String, attachments: List<ChatAttachment>, includeProfile: Boolean = profileIncluded) {
        val question = text.trim()
        if (question.isEmpty()) return
        val (id, _) = env.chat.send(chatId.ifEmpty { null }, ChatSendRequest(question, attachments.distinctBy { it.key() }, includeProfile))
        chatId = id
        input = ""
        pending = emptyList()
    }

    fun setProfile(included: Boolean) {
        newProfileOff = !included
        if (chatId.isNotEmpty()) env.chat.setProfileIncluded(chatId, included)
    }

    // The open chat was deleted (in "Earlier chats" or with "Delete all data"): start a new one.
    LaunchedEffect(chatId, chatRow, messages.isEmpty()) {
        if (chatId.isEmpty()) return@LaunchedEffect
        if (chatRow != null) chatSeen = true
        else if (chatSeen && messages.isEmpty()) newChat()
    }

    LaunchedEffect(openRequest) {
        val r = openRequest ?: return@LaunchedEffect
        chatId = r.chatId
        // A draft only fills an empty input: text the user typed is never replaced.
        if (r.draft.isNotEmpty() && input.isBlank()) input = r.draft
        pending = r.attachments
        // Exactly what was asked for: no automatic training on top of an explicit request.
        trainingRemoved = r.attachments.isNotEmpty()
        newProfileOff = false
        sheet = false
        profilePopup = false
        onOpenRequestConsumed(r)
    }

    Box(Modifier.fillMaxSize().background(Theme.colors.background)) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .imePadding()
                .padding(horizontal = Gutter),
        ) {
            Spacer(Modifier.height(Space.m))
            when {
                overlay != null -> OverlayHeader(s, creditChip, onClose)
                openedChatId != null -> OpenedChatHeader(chatRow?.title.orEmpty(), onBack)
                else -> ChatHeader(s, onStory, onHistory, ::newChat, creditChip)
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (chatId.isEmpty() && messages.isEmpty()) {
                    Suggestions(s.suggestions) { input = it }
                } else {
                    Conversation(messages, photos, templateNames, s, onApplyWorkout, proposals) { env.chat.retry(it) }
                }
            }
            ContextChips(
                profile = profileIncluded,
                chips = chips,
                s = s,
                onProfile = { profilePopup = true },
                onRemoveProfile = { setProfile(false) },
                onRemove = { a ->
                    if (a == autoTraining) trainingRemoved = true else pending = pending.filterNot { it.key() == a.key() }
                },
            )
            Composer(
                value = input,
                onValueChange = { input = it },
                s = s,
                modelLabel = modelLabel,
                price = price,
                onPlus = { sheet = true },
                onModelChip = onModelChip,
                onSend = { send(input, chips) },
            )
            Spacer(Modifier.height(Space.s))
        }

        BottomSheetHost(
            visible = sheet,
            env = env,
            photos = photos,
            photosLeft = (ContextSelection.MAX_PHOTOS - pending.count { it is ChatAttachment.Photo }).coerceAtLeast(0),
            profileRemoved = !profileIncluded,
            onCamera = onCamera,
            onProfileAgain = { setProfile(true) },
            onAdd = { added ->
                val photoCount = pending.count { it is ChatAttachment.Photo }
                var n = photoCount
                val labelled = added.map { a -> if (a is ChatAttachment.Photo) { n += 1; a.copy(label = s.photoN(n)) } else a }
                // At most MAX_PHOTOS photos per question, also across several openings of the sheet.
                var photosKept = 0
                pending = (pending + labelled).distinctBy { it.key() }
                    .filter { it !is ChatAttachment.Photo || ++photosKept <= ContextSelection.MAX_PHOTOS }
                sheet = false
            },
            onDismiss = { sheet = false },
        )
        app.overfit.ui.components.CenterPopup(profilePopup, { profilePopup = false }, s.close) {
            ProfilePopup(env, onSaved = { profilePopup = false })
        }
    }
}

@Composable
private fun BottomSheetHost(
    visible: Boolean,
    env: CoachChatEnv,
    photos: List<DiaryPhoto>,
    photosLeft: Int,
    profileRemoved: Boolean,
    onCamera: (() -> Unit)?,
    onProfileAgain: () -> Unit,
    onAdd: (List<ChatAttachment>) -> Unit,
    onDismiss: () -> Unit,
) {
    // Fresh selection every time the sheet opens; photos only up to what the question can still take.
    val state = remember(visible) { ContextSelection(maxPhotos = photosLeft) }
    app.overfit.ui.components.BottomSheet(
        visible = visible,
        onDismiss = onDismiss,
        closeLabel = chatStrings().close,
        footer = { ContextSheetFooter(state) { onAdd(state.result()) } },
    ) {
        ContextSheet(env, photos, state, profileRemoved, onCamera, onProfileAgain = { onProfileAgain() })
    }
}

// ---------------------------------------------------------------------------------------------
// Header

@Composable
private fun ChatHeader(s: CoachChatStrings, onStory: () -> Unit, onHistory: () -> Unit, onNew: () -> Unit, creditChip: @Composable () -> Unit) {
    val story = LocalCoachStrings.current.hub.story.open
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        // Tapping the title opens the story; the info icon shows that it can be tapped.
        val storyInteraction = remember { MutableInteractionSource() }
        Row(
            Modifier
                .weight(1f)
                .pressScale(storyInteraction)
                .clickable(storyInteraction, indication = null, role = Role.Button, onClick = onStory)
                .semantics(mergeDescendants = true) { contentDescription = story }
                .testTag("coach:storyOpen"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Title(s.title)
            Spacer(Modifier.width(Space.s))
            LineIcon(Icon.Info, size = app.overfit.ui.theme.IconSize.inline, color = Theme.colors.muted)
        }
        app.overfit.ui.components.HeaderActions {
            creditChip()
            HeaderIcon(Icon.List, s.history, "coach:history", onHistory)
            HeaderIcon(Icon.Edit, s.newChat, "coach:new", onNew)
        }
    }
}

/** Header of the coach opened over a screen: "Coach", credit chip, close. */
@Composable
private fun OverlayHeader(s: CoachChatStrings, creditChip: @Composable () -> Unit, onClose: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Title(s.title, Modifier.weight(1f))
        app.overfit.ui.components.HeaderActions {
            creditChip()
            HeaderIcon(Icon.Close, s.close, "coach:close", onClose)
        }
    }
}

/** Header of an earlier chat opened from the list: back arrow and the chat's title. */
@Composable
private fun OpenedChatHeader(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        HeaderIcon(Icon.Back, LocalStrings.current.back, "chat:back", onBack)
        Spacer(Modifier.width(Space.xs))
        Text(title, Modifier.weight(1f), style = Theme.type.label, maxLines = 1)
    }
}

@Composable
private fun HeaderIcon(icon: Icon, label: String, tag: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier
            .size(48.dp)
            .pressScale(interaction, PressScale.icon)
            .clickable(interaction, indication = null, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label }
            .testTag(tag),
        contentAlignment = Alignment.Center,
    ) { LineIcon(icon, size = app.overfit.ui.theme.IconSize.action) }
}

// ---------------------------------------------------------------------------------------------
// Empty chat

/** Exactly three grey, centred text buttons in the middle; a tap puts the text into the input (does not send). */
@Composable
private fun Suggestions(items: List<String>, onPick: (String) -> Unit) {
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(Space.s, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        items.forEachIndexed { i, text ->
            val interaction = remember { MutableInteractionSource() }
            Box(
                Modifier
                    .fillMaxWidth()
                    .pressScale(interaction)
                    .clickable(interaction, indication = null, role = Role.Button) { onPick(text) }
                    .heightIn(min = 52.dp)
                    .background(Theme.colors.surface, ButtonShape)
                    .padding(horizontal = Space.m, vertical = Space.s)
                    .testTag("chat:suggestion:$i"),
                contentAlignment = Alignment.Center,
            ) {
                Text(text, tone = Tone.Muted, align = TextAlign.Center)
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Conversation

@Composable
private fun Conversation(
    messages: List<ChatMessage>,
    photos: List<DiaryPhoto>,
    templateNames: Set<String>,
    s: CoachChatStrings,
    onApply: (String) -> Unit,
    proposals: ProposalHandler?,
    onRetry: (String) -> Unit,
) {
    val list = rememberLazyListState()
    // New message or an answer that arrived: show the end of the chat.
    // Index: leading spacer + messages + trailing spacer, so the last item is the very end.
    LaunchedEffect(messages.size, messages.lastOrNull()?.status) { if (messages.isNotEmpty()) list.animateScrollToItem(messages.size + 1) }
    LazyColumn(Modifier.fillMaxSize(), state = list, verticalArrangement = Arrangement.spacedBy(Space.m)) {
        item { Spacer(Modifier.height(Space.xs)) }
        items(messages, key = { it.id }) { m ->
            if (m.fromUser) UserMessage(m, photos) else CoachMessage(m, templateNames, s, onApply, proposals, onRetry)
        }
        item { Spacer(Modifier.height(Space.s)) }
    }
}

/** Takes at most [fraction] of the available width (bubbles), aligned to the end. */
@Composable
private fun EndAligned(fraction: Float, content: @Composable () -> Unit) {
    Layout(content, Modifier.fillMaxWidth()) { measurables, constraints ->
        val max = (constraints.maxWidth * fraction).toInt()
        val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0, maxWidth = max)) }
        val height = placeables.sumOf { it.height }
        layout(constraints.maxWidth, height) {
            var y = 0
            placeables.forEach { p -> p.placeRelative(constraints.maxWidth - p.width, y); y += p.height }
        }
    }
}

@Composable
private fun UserMessage(m: ChatMessage, photos: List<DiaryPhoto>) {
    val c = Theme.colors
    Column(Modifier.fillMaxWidth().testTag("chat:user"), horizontalAlignment = Alignment.End) {
        val shownPhotos = m.attachments.filterIsInstance<ChatAttachment.Photo>().map { it.photoId }.ifEmpty { m.photoIds }
        val cards = m.attachments.filter { it !is ChatAttachment.Photo }
        if (shownPhotos.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                shownPhotos.forEach { id ->
                    photos.firstOrNull { it.photo.id == id }?.let { PhotoThumb(it.photo, it.workoutName.orEmpty(), size = 56.dp) }
                }
            }
            Spacer(Modifier.height(Space.xs))
        }
        cards.forEach { a ->
            EndAligned(0.8f) { AttachmentCard(a) }
            Spacer(Modifier.height(Space.xs))
        }
        EndAligned(0.8f) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp))
                    .background(c.foreground)
                    .padding(horizontal = Space.m, vertical = 10.dp),
            ) {
                BasicText(m.text, style = Theme.type.body.copy(color = c.background))
            }
        }
    }
}

@Composable
private fun AttachmentCard(a: ChatAttachment) {
    Row(
        Modifier.clip(RoundedCornerShape(12.dp)).background(Theme.colors.surface).padding(horizontal = 12.dp, vertical = Space.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LineIcon(a.icon(), size = app.overfit.ui.theme.IconSize.small, color = Theme.colors.muted)
        Spacer(Modifier.width(Space.s))
        Text(attachmentText(a), style = Theme.type.caption, maxLines = 1)
    }
}

internal fun ChatAttachment.icon(): Icon = when (this) {
    is ChatAttachment.Training -> Icon.Calendar
    is ChatAttachment.Plan -> Icon.Workouts
    is ChatAttachment.History -> Icon.List
    is ChatAttachment.Exercise -> Icon.Exercises
    is ChatAttachment.Photo -> Icon.Photos
    is ChatAttachment.Running -> Icon.Workouts
    is ChatAttachment.Day -> Icon.Today
}

@Composable
private fun CoachMessage(
    m: ChatMessage,
    templateNames: Set<String>,
    s: CoachChatStrings,
    onApply: (String) -> Unit,
    proposals: ProposalHandler?,
    onRetry: (String) -> Unit,
) {
    val language = LocalFuelStrings.current.language
    Column(Modifier.fillMaxWidth().testTag("chat:coach")) {
        when (m.status) {
            ChatMessageStatus.SENDING -> Text(s.thinking, tone = Tone.Muted)
            ChatMessageStatus.FAILED -> {
                Text(s.failed, tone = Tone.Muted)
                // Why: key, model / rejected request, credit, connection, provider (same wording as the request log).
                val reason = app.overfit.ui.fuel.errorText(m.errorCode ?: app.overfit.coach.CoachErrorKind.SERVER.name, app.overfit.i18n.requestLogStringsFor(language))
                Text(reason, Modifier.testTag("chat:error"), style = Theme.type.caption, tone = Tone.Muted)
                Spacer(Modifier.height(Space.xs))
                SmallButton(s.retry, { onRetry(m.id) })
            }
            ChatMessageStatus.DONE -> {
                if (m.text.isNotBlank()) NoteBody(m.text)
                val w = m.workout
                if (w != null && m.workoutJson != null) {
                    if (proposals != null) ChangeCard(m.id, m.workoutJson, proposals, s)
                    else WorkoutCard(w, w.name in templateNames, s) { onApply(m.workoutJson) }
                }
                if (m.demo) Text(s.demoHint, Modifier.padding(top = Space.s), style = Theme.type.caption, tone = Tone.Muted)
                Spacer(Modifier.height(Space.xs))
                when {
                    m.costMicro != null -> AnswerCostLine(LocalCoachStrings.current, language, m.costMicro, null, m.servedModel, m.fallbackFrom)
                    m.providerId != null && m.providerId != ProviderId.FUEL && m.model != null ->
                        Text(s.ownKey(app.overfit.fuel.modelLabel(m.model)), style = Theme.type.caption, tone = Tone.Muted)
                }
            }
        }
    }
}

/** Proposed workout: "Change for …" (a saved workout of that name exists) or "Workout proposal: …", first exercises, Apply. */
@Composable
private fun WorkoutCard(w: SharedTraining, isChange: Boolean, s: CoachChatStrings, onApply: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = Space.m)
            .clip(RoundedCornerShape(20.dp))
            .background(Theme.colors.surface)
            .padding(Space.m)
            .testTag("chat:workout"),
    ) {
        Text(if (isChange) s.workoutChange(w.name) else s.workoutProposal(w.name), style = Theme.type.label)
        Spacer(Modifier.height(Space.s))
        w.exercises.take(MAX_CARD_EXERCISES).forEach { e ->
            val name = e.name ?: w.custom.firstOrNull { it.ref == e.ref }?.name ?: e.id.orEmpty()
            Text(s.workoutExercise(name, e.sets.size), Modifier.padding(vertical = 2.dp), style = Theme.type.caption, tone = Tone.Muted)
        }
        if (w.exercises.size > MAX_CARD_EXERCISES) {
            Text(s.moreExercises(w.exercises.size - MAX_CARD_EXERCISES), style = Theme.type.caption, tone = Tone.Muted)
        }
        Spacer(Modifier.height(Space.m))
        PrimaryButton(s.apply, onApply, modifier = Modifier.testTag("chat:apply"), compact = true)
    }
}

private const val MAX_CARD_EXERCISES = 5

/**
 * Proposal in a coach opened from a screen: what changes for exactly that workout (diff lines), "Apply" changes it
 * (running workout: right away, with "Undo"; saved workout: opens its editor with the proposal).
 */
@Composable
private fun ChangeCard(messageId: String, json: String, proposals: ProposalHandler, s: CoachChatStrings) {
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val diff by androidx.compose.runtime.produceState<List<String>?>(null, json) {
        value = try { proposals.diff(json) } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) { emptyList() }
    }
    // Applied in this chat: kept in the handler's map (survives scrolling and rotation, not a restart).
    val applied = proposals.applied[messageId]
    var undoFailed by remember(messageId) { mutableStateOf(false) }
    var busy by remember(messageId) { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = Space.m)
            .clip(RoundedCornerShape(20.dp))
            .background(Theme.colors.surface)
            .padding(Space.m)
            .testTag("chat:change"),
    ) {
        Text(proposals.title, style = Theme.type.label)
        Spacer(Modifier.height(Space.s))
        val lines = diff
        when {
            lines == null -> Unit
            lines.isEmpty() -> Text(s.changeNone, style = Theme.type.caption, tone = Tone.Muted)
            else -> lines.forEach { Text(it, Modifier.padding(vertical = 2.dp).testTag("chat:diff"), style = Theme.type.caption, tone = Tone.Muted) }
        }
        Spacer(Modifier.height(Space.m))
        val done = applied
        if (done == null) {
            PrimaryButton(s.apply, {
                if (!busy) {
                    busy = true
                    scope.launch {
                        try { proposals.apply(json)?.let { proposals.applied[messageId] = it } } finally { busy = false }
                    }
                }
            }, modifier = Modifier.testTag("chat:apply"), compact = true, loading = busy)
        } else {
            done.note?.let { Text(it, Modifier.padding(bottom = Space.s), style = Theme.type.caption, tone = Tone.Muted) }
            if (done.failed) {
                SmallButton(s.apply, { proposals.applied.remove(messageId) }, modifier = Modifier.testTag("chat:retry-apply"))
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (undoFailed) s.undoTooLate else s.applied, Modifier.weight(1f).testTag("chat:applied"))
                    if (!undoFailed) SmallButton(LocalStrings.current.undo, {
                        if (done.undo?.invoke() == true) proposals.applied.remove(messageId) else undoFailed = true
                    }, modifier = Modifier.testTag("chat:undo"))
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Context chips

@Composable
private fun ContextChips(
    profile: Boolean,
    chips: List<ChatAttachment>,
    s: CoachChatStrings,
    onProfile: () -> Unit,
    onRemoveProfile: () -> Unit,
    onRemove: (ChatAttachment) -> Unit,
) {
    if (!profile && chips.isEmpty()) return
    LazyRow(
        Modifier.fillMaxWidth().padding(top = Space.s, bottom = Space.s),
        horizontalArrangement = Arrangement.spacedBy(Space.s),
    ) {
        if (profile) item(key = "profile") {
            ContextChip(s.chipProfile, Icon.Profile, s.remove(s.chipProfile), "chip:profile", onProfile, onRemoveProfile)
        }
        items(chips, key = { it.key() }) { a ->
            val tag = when (a) {
                is ChatAttachment.Training -> "chip:training"
                is ChatAttachment.Plan -> "chip:plan"
                is ChatAttachment.History -> "chip:history"
                is ChatAttachment.Exercise -> "chip:exercise"
                is ChatAttachment.Photo -> "chip:photo"
                is ChatAttachment.Running -> "chip:running"
                is ChatAttachment.Day -> "chip:day"
            }
            val text = attachmentText(a)
            ContextChip(text, a.icon(), s.remove(text), tag, onClick = null, onRemove = { onRemove(a) })
        }
    }
}

/** Removable chip: surface fill, 16 dp corners, label (tappable when [onClick] is set) and its own × target. */
@Composable
private fun ContextChip(label: String, icon: Icon, removeLabel: String, tag: String, onClick: (() -> Unit)?, onRemove: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    // A 40 dp pill like every chip, drawn inside a 48 dp row so the chip and its ✕ are full touch targets.
    val fill = Theme.colors.surface
    Row(
        Modifier
            .heightIn(min = app.overfit.ui.theme.TouchTarget.min)
            .drawBehind {
                val inset = (app.overfit.ui.theme.TouchTarget.min - app.overfit.ui.theme.Sizes.chip).toPx() / 2
                val h = size.height - 2 * inset
                drawRoundRect(fill, topLeft = androidx.compose.ui.geometry.Offset(0f, inset), size = androidx.compose.ui.geometry.Size(size.width, h), cornerRadius = androidx.compose.ui.geometry.CornerRadius(h / 2))
            }
            .widthIn(max = 240.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .then(if (onClick != null) Modifier.pressScale(interaction).clickable(interaction, indication = null, role = Role.Button, onClick = onClick) else Modifier)
                .testTag(tag)
                .padding(start = 12.dp, top = Space.s, bottom = Space.s)
                .weight(1f, fill = false),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LineIcon(icon, size = app.overfit.ui.theme.IconSize.small, color = Theme.colors.muted)
            Spacer(Modifier.width(6.dp))
            Text(label, style = Theme.type.caption, maxLines = 1)
        }
        val removeInteraction = remember { MutableInteractionSource() }
        Box(
            Modifier
                .size(app.overfit.ui.theme.TouchTarget.min)
                .pressScale(removeInteraction, PressScale.icon)
                .clickable(removeInteraction, indication = null, role = Role.Button, onClick = onRemove)
                .semantics { contentDescription = removeLabel }
                .testTag("$tag:remove"),
            contentAlignment = Alignment.Center,
        ) { LineIcon(Icon.Close, size = app.overfit.ui.theme.IconSize.small, color = Theme.colors.muted) }
    }
}

// ---------------------------------------------------------------------------------------------
// Composer

@Composable
private fun Composer(
    value: String,
    onValueChange: (String) -> Unit,
    s: CoachChatStrings,
    modelLabel: String,
    /** Estimated price shown on the send button ("≈ 2,6 ct", "own key", "Demo · free"); null = not known yet. */
    price: String?,
    onPlus: () -> Unit,
    onModelChip: () -> Unit,
    onSend: () -> Unit,
) {
    val c = Theme.colors
    val style = Theme.type.body.copy(color = c.foreground)
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(c.surface).padding(Space.s)) {
        Box(Modifier.fillMaxWidth().testTag("coach:input")) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = style,
                maxLines = 6,
                cursorBrush = SolidColor(c.foreground),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Default),
                modifier = Modifier.textInputArea().fillMaxWidth().heightIn(min = 44.dp).padding(horizontal = Space.s, vertical = 10.dp),
                decorationBox = { inner ->
                    Box {
                        if (value.isEmpty()) BasicText(s.placeholder, style = style.copy(color = c.muted))
                        inner()
                    }
                },
            )
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            RoundIcon(Icon.Plus, s.addContext, "coach:plus", fill = c.surfacePressed, tint = c.foreground, onClick = onPlus)
            Spacer(Modifier.width(Space.xs))
            // The chip takes what it needs (long names are cut off), the rest stays free before cost and send.
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) { ModelChip(modelLabel, s, onModelChip, Modifier) }
            SendButton(price, s, enabled = value.isNotBlank(), onClick = onSend)
        }
    }
}

@Composable
private fun ModelChip(label: String, s: CoachChatStrings, onClick: () -> Unit, modifier: Modifier) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier
            .pressScale(interaction)
            .clickable(interaction, indication = null, role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = s.modelChip(label) }
            .testTag("coach:model")
            .heightIn(min = 40.dp)
            .padding(horizontal = Space.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(
            label, Modifier.weight(1f, fill = false),
            style = Theme.type.caption.copy(color = Theme.colors.muted), maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(2.dp))
        LineIcon(Icon.ChevronDown, size = app.overfit.ui.theme.IconSize.small, color = Theme.colors.muted)
    }
}

/**
 * Send: a white pill with the estimated price and an arrow ("≈ 2,6 ct ↑"), so nothing goes out without the user
 * seeing what it costs; only the arrow while the price is not known yet.
 */
@Composable
private fun SendButton(price: String?, s: CoachChatStrings, enabled: Boolean, onClick: () -> Unit) {
    val c = Theme.colors
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier
            .heightIn(min = 48.dp)
            .pressScale(interaction, PressScale.icon)
            .clickable(interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = if (price != null) s.sendWithPrice(price) else s.send }
            .testTag("coach:send"),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier
                .heightIn(min = 40.dp)
                .alpha(if (enabled) 1f else 0.3f)
                .clip(RoundedCornerShape(20.dp))
                .background(c.foreground)
                .padding(start = if (price != null) 14.dp else 9.dp, end = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (price != null) {
                BasicText(
                    price, Modifier.widthIn(max = 150.dp).testTag("coach:price"),
                    style = Theme.type.caption.copy(color = c.background, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(6.dp))
            }
            LineIcon(Icon.ArrowUp, size = app.overfit.ui.theme.IconSize.inline, color = c.background)
        }
    }
}

/**
 * Text on the send button: credit → "≈ 2,6 ct" (price of the tier + markup), own key → "own key · ≈ 2,1 ct" (list price
 * if Overfit knows the model) or "own key", no access → "Demo · free"; null while the price table is unknown.
 */
internal fun sendPriceLabel(pricing: ChatPricing, tokens: TokenEstimate?, s: CoachChatStrings, language: app.overfit.domain.Language): String? {
    fun cents(micro: Long) = "≈ " + app.overfit.fuel.formatCents(micro, language)
    return when (pricing) {
        ChatPricing.Demo -> s.priceDemo
        ChatPricing.Unknown -> s.priceUnknown
        is ChatPricing.Credit -> tokens?.let { cents(ChatCost.micro(it, pricing.outputTokens, pricing.price, pricing.markupBp)) }
        is ChatPricing.OwnKey -> {
            val micro = tokens?.let { t -> pricing.price?.let { ChatCost.micro(t, pricing.outputTokens, it) } }
            if (micro != null) s.priceOwnKeyCost(cents(micro)) else s.priceOwnKey
        }
    }
}

/** Round 40 dp button (48 dp to tap) with a line icon. */
@Composable
private fun RoundIcon(
    icon: Icon,
    label: String,
    tag: String,
    fill: androidx.compose.ui.graphics.Color,
    tint: androidx.compose.ui.graphics.Color,
    enabled: Boolean = true,
    size: Dp = app.overfit.ui.theme.Sizes.roundButton,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier
            .size(48.dp)
            .pressScale(interaction, PressScale.icon)
            .clickable(interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label }
            .testTag(tag),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(size).alpha(if (enabled) 1f else 0.3f).clip(CircleShape).background(fill),
            contentAlignment = Alignment.Center,
        ) { LineIcon(icon, size = app.overfit.ui.theme.IconSize.inline, color = tint) }
    }
}
