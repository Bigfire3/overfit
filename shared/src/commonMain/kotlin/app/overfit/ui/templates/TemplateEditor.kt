package app.overfit.ui.templates

import app.overfit.ui.components.SetColumnsHeader
import app.overfit.ui.workout.setColumnLabels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.overfit.data.ExerciseRepository
import app.overfit.data.TemplateRepository
import app.overfit.domain.Language
import app.overfit.domain.Numbers
import app.overfit.domain.TrackingType
import app.overfit.domain.WeightUnit
import app.overfit.i18n.LocalStrings
import app.overfit.i18n.Strings
import app.overfit.ui.components.BackHeader
import app.overfit.ui.components.Block
import app.overfit.ui.components.BlockButton
import app.overfit.ui.components.BlockShape
import app.overfit.ui.components.HeaderIconButton
import app.overfit.ui.components.MenuItem
import app.overfit.ui.components.MoreMenu
import app.overfit.ui.components.SetField
import app.overfit.ui.components.SetRowSurface
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.sp
import app.overfit.ui.components.BigInput
import app.overfit.ui.components.ExerciseImage
import app.overfit.ui.components.Icon
import app.overfit.ui.components.LineIcon
import app.overfit.ui.components.PrimaryButton
import app.overfit.ui.components.Screen
import app.overfit.ui.components.SmallInput
import app.overfit.ui.components.Text
import app.overfit.ui.components.SmallButton
import app.overfit.ui.components.Tone
import app.overfit.ui.components.dragHandle
import app.overfit.ui.components.ReorderState
import app.overfit.ui.components.pressableRow
import app.overfit.ui.components.reorderableItem
import app.overfit.ui.components.rememberReorderState
import app.overfit.ui.templates.TemplateDraft.DraftExercise
import app.overfit.ui.theme.Space
import app.overfit.ui.theme.Theme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class TemplateEditorViewModel(
    private val templateId: Long,
    private val templates: TemplateRepository,
    private val exercises: ExerciseRepository,
    private val unit: WeightUnit,
    private val language: Language,
) : ViewModel() {

    data class State(
        val loading: Boolean = true,
        val draft: TemplateDraft = TemplateDraft(),
        val dirty: Boolean = false,
        val saving: Boolean = false,
        val problem: TemplateDraft.Problem? = null,
        /** Set once the editor should close (saved, deleted or template missing). */
        val closed: Boolean = false,
    )

    private var original = TemplateDraft()
    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    /** Exercise that the picker result replaces (replace mode). */
    var pendingReplaceKey: Long? = null

    init {
        viewModelScope.launch {
            if (templateId == 0L) {
                _state.value = State(loading = false)
                return@launch
            }
            val template = templates.get(templateId)
            if (template == null) {
                _state.value = State(loading = false, closed = true)
            } else {
                original = TemplateDraft.from(template, unit, language)
                _state.value = State(loading = false, draft = original)
            }
        }
    }

    fun edit(transform: (TemplateDraft) -> TemplateDraft) = _state.update { s ->
        val draft = transform(s.draft)
        s.copy(draft = draft, dirty = draft != original, problem = null)
    }

    /** Picker result: add the exercises, or replace one if [pendingReplaceKey] is set. */
    fun onExercisesPicked(ids: List<String>) {
        if (ids.isEmpty()) return
        val replaceKey = pendingReplaceKey
        pendingReplaceKey = null
        viewModelScope.launch {
            val picked = exercises.getAll(ids)
            if (replaceKey != null) picked.firstOrNull()?.let { ex -> edit { it.replaceExercise(replaceKey, ex) } }
            else edit { it.addExercises(picked) }
        }
    }

    /** Last swiped-away set: [before] comes back on "Undo" as long as nothing else was changed ([after]). */
    data class Undo(val before: TemplateDraft, val after: TemplateDraft)
    private val _undo = MutableStateFlow<Undo?>(null)
    val undo: StateFlow<Undo?> = _undo.asStateFlow()

    fun removeSet(exerciseKey: Long, setKey: Long) {
        val before = _state.value.draft
        edit { it.removeSet(exerciseKey, setKey) }
        _undo.value = Undo(before, _state.value.draft)
    }

    fun undoRemoveSet() {
        val u = _undo.value ?: return
        _undo.value = null
        if (_state.value.draft == u.after) edit { u.before }
    }

    fun dismissUndo() { _undo.value = null }

    fun save() {
        val s = _state.value
        if (s.saving || s.loading) return
        s.draft.validate(unit)?.let { problem -> _state.update { it.copy(problem = problem) }; return }
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            templates.save(s.draft.toTemplate(unit))
            _state.update { it.copy(saving = false, dirty = false, closed = true) }
        }
    }

    fun delete() {
        viewModelScope.launch {
            if (templateId != 0L) templates.delete(templateId)
            _state.update { it.copy(closed = true) }
        }
    }
}

/**
 * Create/edit a saved workout ("Bearbeitungsmodus"): name in the header (pencil = rename), one grey [Block] per
 * exercise (collapsible; image with "?" for details; ⋮ for replace/move/remove), one [SetRowSurface] per set
 * (swipe left = delete, with "Undo"), "+ Add set", "+ Add exercise" and "Save" pinned at the bottom.
 * No share or delete here: those live on the workout screen. Leaving with unsaved changes needs a second back press.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun TemplateEditorScreen(
    viewModel: TemplateEditorViewModel,
    unit: WeightUnit,
    onClose: () -> Unit,
    onAddExercises: () -> Unit,
    onReplaceExercise: () -> Unit,
    onExerciseDetails: (String) -> Unit,
) {
    val t = LocalStrings.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val undo by viewModel.undo.collectAsStateWithLifecycle()
    val draft = state.draft
    var collapsed by rememberSaveable { mutableStateOf(listOf<Long>()) }
    var backArmed by remember { mutableStateOf(false) }
    LaunchedEffect(state.closed) { if (state.closed) onClose() }
    LaunchedEffect(backArmed) { if (backArmed) { delay(4000); backArmed = false } }
    LaunchedEffect(undo) { if (undo != null) { delay(5000); viewModel.dismissUndo() } }
    val showUndo = undo?.after == draft && undo != null

    val listState = rememberLazyListState()
    val reorder = rememberReorderState(listState, canMove = { it is Long }) { from, to ->
        viewModel.edit { d ->
            val a = d.exercises.indexOfFirst { it.key == from }
            val b = d.exercises.indexOfFirst { it.key == to }
            if (a < 0 || b < 0) d else d.moveExercise(from as Long, b - a)
        }
    }

    val back = { if (state.dirty && !backArmed) backArmed = true else onClose() }
    BackHandler(enabled = state.dirty, onBack = back)
    val nameFocus = remember { FocusRequester() }

    Screen(
        header = { BackHeader(t.back, back) },
        footer = {
            val message = when {
                backArmed -> t.unsavedHint
                else -> state.problem?.describe(t)
            }
            if (showUndo) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(t.setDeleted, Modifier.weight(1f), style = Theme.type.caption, tone = Tone.Muted)
                    SmallButton(t.undo, viewModel::undoRemoveSet)
                }
            }
            if (message != null) Text(message, style = Theme.type.caption, tone = Tone.Muted)
            PrimaryButton(t.save, viewModel::save, enabled = !state.loading, loading = state.saving)
        },
    ) {
        if (state.loading) return@Screen
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            state = listState,
            contentPadding = PaddingValues(bottom = Space.l),
            verticalArrangement = Arrangement.spacedBy(Space.s),
        ) {
            item(key = "name") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BigInput(
                        draft.name,
                        { v -> viewModel.edit { it.withName(v.take(60)) } },
                        t.templateNamePlaceholder,
                        Modifier.weight(1f).focusRequester(nameFocus),
                    )
                    HeaderIconButton(Icon.Edit, t.rename, { nameFocus.requestFocus() }, color = Theme.colors.muted)
                }
                Text(if (draft.isNew) t.newTemplate else t.editMode, style = Theme.type.caption, tone = Tone.Muted)
                Spacer(Modifier.height(Space.m))
            }
            itemsIndexed(draft.exercises, key = { _, e -> e.key }) { index, entry ->
                ExerciseBlock(
                    modifier = Modifier.then(reorderableItem(reorder, entry.key)),
                    reorder = reorder,
                    entry = entry,
                    unit = unit,
                    expanded = entry.key !in collapsed,
                    isFirst = index == 0,
                    isLast = index == draft.exercises.lastIndex,
                    onToggle = { collapsed = if (entry.key in collapsed) collapsed - entry.key else collapsed + entry.key },
                    onEdit = viewModel::edit,
                    onRemoveSet = { setKey -> viewModel.removeSet(entry.key, setKey) },
                    onReplace = { viewModel.pendingReplaceKey = entry.key; onReplaceExercise() },
                    onDetails = { onExerciseDetails(entry.exercise.id) },
                )
            }
            item(key = "actions") {
                Spacer(Modifier.height(Space.s))
                BlockButton(t.addExercise, onAddExercises)
            }
        }
    }
}

private fun TemplateDraft.Problem.describe(t: Strings): String = when (this) {
    TemplateDraft.Problem.MissingName -> t.missingName
    is TemplateDraft.Problem.InvalidValue -> t.invalidValue(exercise, set)
}

@Composable
private fun ExerciseBlock(
    modifier: Modifier,
    reorder: ReorderState,
    entry: DraftExercise,
    unit: WeightUnit,
    expanded: Boolean,
    isFirst: Boolean,
    isLast: Boolean,
    onToggle: () -> Unit,
    onEdit: ((TemplateDraft) -> TemplateDraft) -> Unit,
    onRemoveSet: (setKey: Long) -> Unit,
    onReplace: () -> Unit,
    onDetails: () -> Unit,
) {
    val t = LocalStrings.current
    val key = entry.key
    val type = entry.exercise.trackingType
    Block(modifier) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 64.dp).clip(BlockShape.row).pressableRow(onToggle).dragHandle(reorder, key)
                .semantics { contentDescription = t.reorder },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LineIcon(if (expanded) Icon.ChevronDown else Icon.ChevronRight, size = 20.dp, color = Theme.colors.muted)
            Spacer(Modifier.width(Space.s))
            ExerciseThumb(entry, onDetails)
            Spacer(Modifier.width(Space.m))
            Column(Modifier.weight(1f)) {
                Text(entry.exercise.name, style = Theme.type.label, maxLines = 2)
                val rest = entry.rest.toIntOrNull()?.let { t.restSummary(Numbers.formatDuration(it)) }
                Text(listOfNotNull(t.setCount(entry.sets.size), rest).joinToString(" · "), style = Theme.type.caption, tone = Tone.Muted)
            }
            MoreMenu(
                t.moreActions(entry.exercise.name),
                buildList {
                    add(MenuItem(t.replace, onClick = onReplace))
                    if (!isFirst) add(MenuItem(t.moveUp) { onEdit { it.moveExercise(key, -1) } })
                    if (!isLast) add(MenuItem(t.moveDown) { onEdit { it.moveExercise(key, 1) } })
                    add(MenuItem(t.remove, destructive = true) { onEdit { it.removeExercise(key) } })
                },
            )
        }
        if (expanded) {
            Column(Modifier.padding(top = Space.xs), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                SetColumnsHeader(t.setColumn, setColumnLabels(type, unit, t))
                val focus = remember { mutableMapOf<Any, FocusRequester>() }
                fun focusOf(id: Any) = focus.getOrPut(id) { FocusRequester() }
                entry.sets.forEachIndexed { i, set ->
                    androidx.compose.runtime.key(set.key) {
                        SetRow(
                            i + 1, type, unit, set,
                            onChange = { transform -> onEdit { it.updateSet(key, set.key, transform) } },
                            focusRequester = focusOf(set.key),
                            next = entry.sets.getOrNull(i + 1)?.let { focusOf(it.key) },
                        ) {
                            onRemoveSet(set.key)
                        }
                    }
                }
                BlockButton(t.addSet, { onEdit { it.addSet(key) } }, inBlock = true)
                Row(Modifier.fillMaxWidth().padding(top = Space.xs), horizontalArrangement = Arrangement.spacedBy(Space.m), verticalAlignment = Alignment.Bottom) {
                    SmallInput(
                        entry.rest,
                        { v -> onEdit { it.setRest(key, v) } },
                        unit = t.secondsUnit,
                        placeholder = t.restDefault,
                        description = t.restTime,
                        modifier = Modifier.width(96.dp),
                    )
                    SmallInput(
                        entry.note,
                        { v -> onEdit { it.setNote(key, v.take(300)) } },
                        unit = "",
                        placeholder = t.note,
                        keyboardType = KeyboardType.Text,
                        align = TextAlign.Start,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/** Exercise image with a small "?" in the corner: opens the exercise details. */
@Composable
private fun ExerciseThumb(entry: DraftExercise, onDetails: () -> Unit) {
    val t = LocalStrings.current
    Box(Modifier.size(48.dp).clip(BlockShape.field).semantics { contentDescription = t.exerciseDetails(entry.exercise.name) }
        .pressableRow(onDetails)) {
        ExerciseImage(entry.exercise, size = 48.dp)
        Box(
            Modifier.align(Alignment.BottomEnd).padding(2.dp).size(16.dp).clip(CircleShape).background(Theme.colors.inverseBackground),
            contentAlignment = Alignment.Center,
        ) { Text("?", style = Theme.type.caption.copy(fontSize = 11.sp), tone = Tone.Inverse) }
    }
}

@Composable
private fun SetRow(
    number: Int,
    type: TrackingType,
    unit: WeightUnit,
    set: TemplateDraft.DraftSet,
    onChange: ((TemplateDraft.DraftSet) -> TemplateDraft.DraftSet) -> Unit,
    focusRequester: FocusRequester,
    next: FocusRequester?,
    onRemove: () -> Unit,
) {
    val t = LocalStrings.current
    // Keyboard order: weight -> reps/time -> next set; the last field says "Done".
    val fieldCount = listOf(type.usesWeight, type.usesReps, type.usesTime).count { it }
    val more = remember(fieldCount) { List((fieldCount - 1).coerceAtLeast(0)) { FocusRequester() } }
    val requesters = listOf(focusRequester) + more
    var field = 0
    fun req(): FocusRequester = requesters[field]
    fun nextOf(): FocusRequester? = requesters.getOrNull(field + 1) ?: next
    SetRowSurface(t.removeSet(number), onRemove, Modifier.testTag("set-row:$number")) {
        Text("$number", Modifier.width(24.dp), style = Theme.type.label, tone = Tone.Muted, align = TextAlign.Center)
        if (type.usesWeight) {
            SetField(
                set.weight,
                { v -> onChange { it.copy(weight = v.filter { c -> c.isDigit() || c == ',' || c == '.' }.take(8)) } },
                description = t.weightOfSet(number),
                keyboardType = KeyboardType.Decimal,
                focusRequester = req(),
                next = nextOf(),
                modifier = Modifier.weight(1f),
            )
            field++
        }
        if (type.usesReps) {
            SetField(
                set.reps, { v -> onChange { it.copy(reps = v.filter(Char::isDigit).take(4)) } },
                description = t.repsOfSet(number),
                focusRequester = req(),
                next = nextOf(),
                modifier = Modifier.weight(1f),
            )
            field++
        }
        if (type.usesTime) {
            SetField(
                set.duration,
                { v -> onChange { it.copy(duration = v.filter { c -> c.isDigit() || c == ':' }.take(6)) } },
                description = t.durationOfSet(number),
                keyboardType = KeyboardType.Number,
                focusRequester = req(),
                next = nextOf(),
                modifier = Modifier.weight(1f),
            )
        }
    }
}
