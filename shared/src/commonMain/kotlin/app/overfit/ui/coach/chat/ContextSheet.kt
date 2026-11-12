package app.overfit.ui.coach.chat

import androidx.compose.ui.graphics.compositeOver
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.overfit.coach.ChatAttachment
import app.overfit.coach.key
import app.overfit.data.DiaryPhoto
import app.overfit.i18n.CoachChatStrings
import app.overfit.ui.components.Icon
import app.overfit.ui.components.LineIcon
import app.overfit.ui.components.PressScale
import app.overfit.ui.components.PrimaryButton
import app.overfit.ui.components.SearchInput
import app.overfit.ui.components.Text
import app.overfit.ui.components.Tone
import app.overfit.ui.components.pressScale
import app.overfit.ui.history.PhotoThumb
import app.overfit.ui.theme.Space
import app.overfit.ui.theme.Theme

internal enum class ContextPage { Main, Trainings, Plans, Exercises }

/**
 * Selection of one opening of the "+" sheet: photos in tap order and the other attachments. [maxPhotos] is what the
 * question can still take (at most [MAX_PHOTOS] photos per question, photos already attached count).
 */
@Stable
internal class ContextSelection(val maxPhotos: Int = MAX_PHOTOS) {
    var page by mutableStateOf(ContextPage.Main)
    val photos = mutableStateListOf<String>()
    val items = mutableStateListOf<ChatAttachment>()
    val count: Int get() = photos.size + items.size

    fun togglePhoto(id: String) {
        if (id in photos) photos.remove(id) else if (canAddPhoto) photos.add(id)
    }

    val canAddPhoto: Boolean get() = photos.size < maxPhotos

    fun selected(a: ChatAttachment): Boolean = items.any { it.key() == a.key() }

    fun toggle(a: ChatAttachment) {
        val i = items.indexOfFirst { it.key() == a.key() }
        if (i >= 0) items.removeAt(i) else items.add(a)
    }

    fun result(): List<ChatAttachment> = photos.map { ChatAttachment.Photo(it) } + items

    companion object { const val MAX_PHOTOS = 4 }
}

/** "+" sheet: photos (camera + recent, numbered multi-select), training / workout / history / exercise, profile again. */
@Composable
internal fun ColumnScope.ContextSheet(
    env: CoachChatEnv,
    photos: List<DiaryPhoto>,
    state: ContextSelection,
    profileRemoved: Boolean,
    onCamera: (() -> Unit)?,
    onProfileAgain: () -> Unit,
) {
    val s = chatStrings()
    when (state.page) {
        ContextPage.Main -> MainPage(env, photos, state, profileRemoved, onCamera, onProfileAgain, s)
        ContextPage.Trainings -> {
            val list by env.trainings.collectAsState(initial = null)
            SubPage(s.rowTraining, s, state) {
                ItemList(list?.take(MAX_LIST), s.emptyTrainings, state) { attachmentText(it) to null }
            }
        }
        ContextPage.Plans -> {
            val list by env.plans.collectAsState(initial = null)
            SubPage(s.rowWorkout, s, state) {
                ItemList(list, s.emptyPlans, state) { it.label to null }
            }
        }
        ContextPage.Exercises -> {
            val list by produceState<List<Pair<ChatAttachment.Exercise, String>>?>(null) { value = env.exercises() }
            var query by remember { mutableStateOf("") }
            SubPage(s.rowExercise, s, state) {
                if ((list?.size ?: 0) > SEARCH_FROM) {
                    SearchInput(query, { query = it }, s.searchExercise, s.clearSearch)
                    Spacer(Modifier.height(Space.s))
                }
                val shown = list?.filter { query.isBlank() || it.first.label.contains(query.trim(), ignoreCase = true) }
                val subs = list.orEmpty().associate { it.first.key() to it.second }
                ItemList(shown?.map { it.first }, if (query.isBlank()) s.emptyExercises else s.noExerciseMatch, state) { it.label to subs[it.key()] }
            }
        }
    }
}

@Composable
private fun MainPage(
    env: CoachChatEnv,
    photos: List<DiaryPhoto>,
    state: ContextSelection,
    profileRemoved: Boolean,
    onCamera: (() -> Unit)?,
    onProfileAgain: () -> Unit,
    s: CoachChatStrings,
) {
    Text(s.contextTitle, Modifier.padding(vertical = Space.s), style = Theme.type.label.copy(fontWeight = FontWeight.SemiBold))
    Text(s.photos, Modifier.padding(top = Space.s, bottom = Space.s), style = Theme.type.caption, tone = Tone.Muted)
    val recent = photos.take(MAX_PHOTOS_SHOWN)
    if (recent.isEmpty() && onCamera == null) {
        Text(s.noPhotos, Modifier.padding(bottom = Space.s), style = Theme.type.caption, tone = Tone.Muted)
    } else {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            if (onCamera != null) item(key = "camera") { CameraTile(s.camera, onCamera) }
            items(recent, key = { it.photo.id }) { p ->
                val number = state.photos.indexOf(p.photo.id).takeIf { it >= 0 }?.plus(1)
                PhotoTile(p, number, enabled = number != null || state.canAddPhoto, s) { state.togglePhoto(p.photo.id) }
            }
        }
    }
    Spacer(Modifier.height(Space.l))
    SheetGroup {
        val trainings = state.items.count { it is ChatAttachment.Training }
        val plans = state.items.count { it is ChatAttachment.Plan }
        val exercises = state.items.count { it is ChatAttachment.Exercise }
        SheetRow(s.rowTraining, s.rowTrainingHint, count = trainings, tag = "context:training") { state.page = ContextPage.Trainings }
        SheetRow(s.rowWorkout, s.rowWorkoutHint, count = plans, tag = "context:workout") { state.page = ContextPage.Plans }
        val history = ChatAttachment.History(HISTORY_WEEKS, s.historyLabel(HISTORY_WEEKS))
        SheetRow(s.rowHistory, s.rowHistoryHint, checked = state.selected(history), tag = "context:history") { state.toggle(history) }
        SheetRow(s.rowExercise, s.rowExerciseHint, count = exercises, tag = "context:exercise") { state.page = ContextPage.Exercises }
    }
    if (profileRemoved) {
        Spacer(Modifier.height(Space.m))
        SheetGroup { SheetRow(s.profileAgain, null, tag = "context:profile", chevron = false) { onProfileAgain() } }
    }
}

/** Fixed bottom of the sheet: "Add N" (disabled without a selection). */
@Composable
internal fun ContextSheetFooter(state: ContextSelection, onAdd: () -> Unit) {
    val s = chatStrings()
    val n = state.count
    PrimaryButton(if (n > 0) s.add(n) else s.addNone, onAdd, enabled = n > 0, modifier = Modifier.fillMaxWidth().testTag("context:add"))
}

@Composable
private fun SubPage(title: String, s: CoachChatStrings, state: ContextSelection, content: @Composable ColumnScope.() -> Unit) {
    Row(Modifier.fillMaxWidth().padding(bottom = Space.s), verticalAlignment = Alignment.CenterVertically) {
        val interaction = remember { MutableInteractionSource() }
        Box(
            Modifier
                .size(48.dp)
                .pressScale(interaction, PressScale.icon)
                .clickable(interaction, indication = null, role = Role.Button) { state.page = ContextPage.Main }
                .semantics { contentDescription = s.back }
                .testTag("context:back"),
            contentAlignment = Alignment.CenterStart,
        ) { LineIcon(Icon.Back) }
        Text(title, style = Theme.type.label.copy(fontWeight = FontWeight.SemiBold))
    }
    Column(content = content)
}

@Composable
private fun <T : ChatAttachment> ItemList(list: List<T>?, empty: String, state: ContextSelection, text: @Composable (T) -> Pair<String, String?>) {
    when {
        list == null -> Unit
        list.isEmpty() -> Text(empty, Modifier.padding(vertical = Space.m), style = Theme.type.caption, tone = Tone.Muted)
        else -> SheetGroup {
            list.forEach { a ->
                val (label, sub) = text(a)
                SheetRow(label, sub, checked = state.selected(a), tag = "context:item") { state.toggle(a) }
            }
        }
    }
}

/**
 * Rows of the sheet: [Colors.surfacePressed] fill on the sheet's surface, round corners and black 1 dp dividers like
 * [app.overfit.ui.components.Panel] (the black background shows through the 1 dp gaps).
 */
@Composable
private fun SheetGroup(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Theme.colors.background),
        verticalArrangement = Arrangement.spacedBy(1.dp),
        content = content,
    )
}

@Composable
private fun SheetRow(
    label: String,
    hint: String?,
    tag: String,
    count: Int = 0,
    checked: Boolean? = null,
    chevron: Boolean = checked == null,
    onClick: () -> Unit,
) {
    val c = Theme.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Row(
        Modifier
            .fillMaxWidth()
            // Pressed: a light white veil over the row (like the ⋮ menu), so the grey hint on it stays readable.
            .background(if (pressed) c.foreground.copy(alpha = 0.08f).compositeOver(c.surfacePressed) else c.surfacePressed)
            .clickable(interaction, indication = null, role = if (checked != null) Role.Checkbox else Role.Button, onClick = onClick)
            .then(if (checked != null) Modifier.semantics { selected = checked } else Modifier)
            .testTag(tag)
            .heightIn(min = 56.dp)
            .padding(horizontal = Space.m, vertical = Space.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label)
            if (hint != null) Text(hint, style = Theme.type.caption, tone = Tone.Muted)
        }
        if (count > 0) {
            CountBadge(count)
            Spacer(Modifier.width(Space.s))
        }
        when {
            checked != null -> CheckCircle(checked)
            chevron -> LineIcon(Icon.ChevronRight, size = 20.dp, color = c.muted)
        }
    }
}

/**
 * Round check: chosen = white with a black tick. Empty = a darker fill than its row: the rows are already
 * [Colors.surfacePressed] (one step up from the sheet's surface), so the empty circle is the black background.
 */
@Composable
private fun CheckCircle(checked: Boolean) {
    val c = Theme.colors
    Box(Modifier.size(24.dp).clip(CircleShape).background(if (checked) c.foreground else c.background), contentAlignment = Alignment.Center) {
        if (checked) LineIcon(Icon.Check, size = 16.dp, color = c.background)
    }
}

@Composable
private fun CountBadge(n: Int) {
    val c = Theme.colors
    Box(Modifier.size(24.dp).clip(CircleShape).background(c.foreground), contentAlignment = Alignment.Center) {
        BasicText(n.toString(), style = Theme.type.caption.copy(color = c.background, fontWeight = FontWeight.SemiBold, fontSize = 12.sp))
    }
}

@Composable
private fun CameraTile(label: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Column(
        Modifier
            .size(TILE)
            .pressScale(interaction, PressScale.card)
            .clip(RoundedCornerShape(12.dp))
            .background(Theme.colors.surfacePressed)
            .clickable(interaction, indication = null, role = Role.Button, onClick = onClick)
            .testTag("context:camera"),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        LineIcon(Icon.Photos, size = 22.dp)
        Spacer(Modifier.height(Space.xs))
        Text(label, style = Theme.type.caption, tone = Tone.Muted)
    }
}

@Composable
private fun PhotoTile(p: DiaryPhoto, number: Int?, enabled: Boolean, s: CoachChatStrings, onClick: () -> Unit) {
    val c = Theme.colors
    Box(
        Modifier.size(TILE).alpha(if (enabled) 1f else 0.35f).testTag("context:photo").semantics(mergeDescendants = true) {
            selected = number != null
            if (number != null) contentDescription = s.selectedPhoto(number)
            if (!enabled) disabled()
        },
    ) {
        // Limit reached: the other photos cannot be chosen (dimmed, no tap).
        PhotoThumb(p.photo, p.workoutName ?: s.photos, size = TILE, onClick = if (enabled) onClick else null)
        if (number != null) {
            Box(
                Modifier.align(Alignment.TopEnd).padding(6.dp).size(24.dp).clip(CircleShape).background(c.foreground),
                contentAlignment = Alignment.Center,
            ) {
                BasicText(number.toString(), style = Theme.type.caption.copy(color = c.background, fontWeight = FontWeight.SemiBold, fontSize = 12.sp))
            }
        }
    }
}

private val TILE = 76.dp
private const val MAX_PHOTOS_SHOWN = 20
private const val MAX_LIST = 40
private const val SEARCH_FROM = 8
private const val HISTORY_WEEKS = 4
