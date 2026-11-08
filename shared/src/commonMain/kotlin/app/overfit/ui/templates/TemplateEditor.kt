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
import app.overfit.data.CustomExerciseInput
import app.overfit.data.LastPerformance
import app.overfit.domain.Template
import app.overfit.ui.components.HeaderDeleteButton
import app.overfit.ui.components.HeaderIconButton
import app.overfit.ui.main.RunningWorkoutChoice
import app.overfit.workout.WorkoutFormat
import app.overfit.data.ExerciseRepository
import app.overfit.share.ShareMapper
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
import app.overfit.ui.components.MenuItem
import app.overfit.ui.components.MoreMenu
import app.overfit.ui.components.SetField
import app.overfit.ui.components.SetRowSurface
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
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
import kotlinx.coroutines.sync.withLock

class TemplateEditorViewModel(
    private val templateId: Long,
    private val templates: TemplateRepository,
    private val exercises: ExerciseRepository,
    private val unit: WeightUnit,
    private val language: Language,
    /** A workout from outside: opens pre-filled, counts as unsaved and creates its new exercises only on "Save". */
    val seed: EditorSeed? = null,
) : ViewModel() {
    /**
     * A saved workout opened from the list is edited in place: every valid change is stored after a short pause
     * ([AUTOSAVE_DELAY_MS]), leaving or "Start" stores what is still pending. New workouts and imports keep "Save".
     */
    val autosave: Boolean = templateId != 0L && seed == null

    data class State(
        val loading: Boolean = true,
        val draft: TemplateDraft = TemplateDraft(),
        val dirty: Boolean = false,
        val saving: Boolean = false,
        val problem: TemplateDraft.Problem? = null,
        /** Set once the editor should close (saved, deleted or template missing). */
        val closed: Boolean = false,
        /** ID of a new workout stored with "Save" (the app then opens it, ready to start). */
        val savedId: Long? = null,
        /** The workout was deleted (closing must not save it again). */
        val deleted: Boolean = false,
    )

    private val pendingExercises: Map<String, CustomExerciseInput> = seed?.let { ShareMapper.pendingInputs(it.preview) }.orEmpty()

    private var original = TemplateDraft()
    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    /** Exercise that the picker result replaces (replace mode). */
    var pendingReplaceKey: Long? = null

    init {
        viewModelScope.launch {
            if (seed != null) {
                _state.value = State(
                    loading = false,
                    draft = TemplateDraft.from(ShareMapper.toDraftTemplate(seed.preview, seed.replaceId ?: 0), unit, language),
                    dirty = true,
                )
                return@launch
            }
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

    fun edit(transform: (TemplateDraft) -> TemplateDraft) {
        _state.update { s ->
            val draft = transform(s.draft)
            s.copy(draft = draft, dirty = draft != original, problem = null)
        }
        if (autosave) scheduleSave()
    }

    private var saveJob: kotlinx.coroutines.Job? = null
    private val saveLock = kotlinx.coroutines.sync.Mutex()

    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = viewModelScope.launch { delay(AUTOSAVE_DELAY_MS); persist() }
    }

    /**
     * Autosave: stores the current draft if it changed and is valid; an invalid draft (no name, "9,,5") stays unsaved
     * and shows why. Returns the stored workout, or null if the draft cannot be stored.
     */
    private suspend fun persist(): app.overfit.domain.Template? = saveLock.withLock {
        val draft = _state.value.draft
        val problem = draft.validate(unit)
        if (problem != null) {
            _state.update { if (it.draft == draft) it.copy(problem = problem) else it }
            return@withLock null
        }
        val template = draft.toTemplate(unit)
        if (draft != original) {
            try {
                templates.save(template)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                // Deleted meanwhile (e.g. on another screen): nothing to store any more.
                _state.update { it.copy(closed = true, deleted = true) }
                return@withLock null
            }
            original = draft
            _state.update { it.copy(dirty = it.draft != original) }
        }
        template
    }

    /**
     * Autosave mode: stores what is pending and then calls [then] with the stored workout (null if the draft is
     * invalid). Runs in the view model scope, which outlives a tap that navigates away right after.
     */
    fun flush(then: (app.overfit.domain.Template?) -> Unit = {}) {
        saveJob?.cancel()
        viewModelScope.launch { then(persist()) }
    }

    /** Back in autosave mode: store and close; an invalid draft first shows why (a second back discards it). */
    fun leave(force: Boolean) {
        saveJob?.cancel()
        viewModelScope.launch {
            val saved = persist()
            if (saved != null || force) _state.update { it.copy(closed = true) }
        }
    }

    /** Picker result: add the exercises, or replace one if [pendingReplaceKey] is set. */
    fun onExercisesPicked(ids: List<String>) {
        if (ids.isEmpty()) return
        val replaceKey = pendingReplaceKey
        pendingReplaceKey = null
        viewModelScope.launch {
            val picked = exercises.getAll(ids)
            if (replaceKey != null) picked.firstOrNull()?.let { ex -> undoable(UndoKind.Replaced) { it.replaceExercise(replaceKey, ex) } }
            else edit { it.addExercises(picked) }
        }
    }

    /** What the last undoable change was (the footer says it next to "Undo"). */
    enum class UndoKind { SetRemoved, ExerciseRemoved, Replaced }

    /** Last removal/replacement: [before] comes back on "Undo" as long as nothing else was changed ([after]). */
    data class Undo(val before: TemplateDraft, val after: TemplateDraft, val kind: UndoKind = UndoKind.SetRemoved)
    private val _undo = MutableStateFlow<Undo?>(null)
    val undo: StateFlow<Undo?> = _undo.asStateFlow()

    /** A change that "Undo" can take back (removing a set or an exercise, replacing an exercise). */
    fun undoable(kind: UndoKind, transform: (TemplateDraft) -> TemplateDraft) {
        val before = _state.value.draft
        edit(transform)
        _undo.value = Undo(before, _state.value.draft, kind)
    }

    fun removeSet(exerciseKey: Long, setKey: Long) = undoable(UndoKind.SetRemoved) { it.removeSet(exerciseKey, setKey) }

    fun removeExercise(key: Long) = undoable(UndoKind.ExerciseRemoved) { it.removeExercise(key) }

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
            var template = s.draft.toTemplate(unit)
            if (pendingExercises.isNotEmpty()) {
                // New exercises of the import (only those still in the workout) are created now.
                val created = HashMap<String, String>()
                template = template.copy(exercises = template.exercises.map { te ->
                    val input = pendingExercises[te.exercise.id] ?: return@map te
                    te.copy(exercise = te.exercise.copy(id = created.getOrPut(te.exercise.id) { exercises.createCustom(input) }))
                })
            }
            val id = templates.save(template)
            seed?.onSaved?.invoke()
            _state.update { it.copy(saving = false, dirty = false, closed = true, savedId = id) }
        }
    }

    /** "Discard" of a chat proposal: acknowledges it without saving. */
    fun discardSeed() {
        val discard = seed?.onDiscard ?: return
        viewModelScope.launch { discard(); _state.update { it.copy(dirty = false, closed = true) } }
    }

    fun delete() {
        saveJob?.cancel()
        viewModelScope.launch {
            saveLock.withLock { if (templateId != 0L) templates.delete(templateId) }
            _state.update { it.copy(closed = true, deleted = true) }
        }
    }

    companion object {
        const val AUTOSAVE_DELAY_MS = 600L
    }
}

/**
 * A saved workout and its editor in one screen. Opened from the list it is edited in place ([TemplateEditorViewModel.autosave]):
 * name, one grey [Block] per exercise (tap = collapse; image opens details; ⋮ for replace/move/remove), one
 * [SetRowSurface] per set (swipe left = delete), "Undo" for removals, share and delete top right and "Start" floating
 * at the bottom. A new workout or an import ("Neues Workout") keeps "Save" at the bottom and needs a second back
 * press to leave with unsaved changes.
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
    language: Language = Language.DE,
    /** "last done 3 days ago" under the name (saved workouts only). */
    lastDone: String? = null,
    /** Another workout is running: "Start" becomes the choice "to the running one / end it". */
    workoutRunning: Boolean = false,
    /** Starts the stored workout (pending changes are saved first). */
    onStart: (Template) -> Unit = {},
    onToRunning: () -> Unit = {},
    onEndRunning: () -> Unit = {},
    onShare: () -> Unit = {},
    /** "Last time" of an exercise (preferably in this workout); null = never done. */
    loadLast: suspend (exerciseId: String) -> LastPerformance? = { null },
    /** A new workout was stored with "Save": the app opens it (ready to start) instead of just closing. */
    onSaved: ((Long) -> Unit)? = null,
    /** Coach icon in the header of a saved workout (wired by the coach feature); null = none. */
    onCoach: (() -> Unit)? = null,
) {
    val t = LocalStrings.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val undo by viewModel.undo.collectAsStateWithLifecycle()
    val draft = state.draft
    val autosave = viewModel.autosave
    var collapsed by rememberSaveable { mutableStateOf(listOf<Long>()) }
    var backArmed by remember { mutableStateOf(false) }
    LaunchedEffect(state.closed) {
        if (!state.closed) return@LaunchedEffect
        val saved = state.savedId
        if (saved != null && onSaved != null) onSaved(saved) else onClose()
    }
    LaunchedEffect(backArmed) { if (backArmed) { delay(4000); backArmed = false } }
    LaunchedEffect(undo) { if (undo != null) { delay(5000); viewModel.dismissUndo() } }
    val showUndo = undo?.after == draft && undo != null

    // "Last time" per exercise, loaded once per set of exercises (not on every typed value).
    val exerciseIds = if (state.loading) null else draft.exercises.map { it.exercise.id }.distinct()
    var last by remember { mutableStateOf<Map<String, LastPerformance?>>(emptyMap()) }
    LaunchedEffect(exerciseIds) {
        val missing = exerciseIds.orEmpty().filter { it !in last && !it.startsWith(ShareMapper.PENDING_PREFIX) }
        if (missing.isNotEmpty()) last = last + missing.associateWith {
            try { loadLast(it) } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) { null }
        }
    }

    val listState = rememberLazyListState()
    val reorder = rememberReorderState(listState, canMove = { it is Long }) { from, to ->
        viewModel.edit { d ->
            val a = d.exercises.indexOfFirst { it.key == from }
            val b = d.exercises.indexOfFirst { it.key == to }
            if (a < 0 || b < 0) d else d.moveExercise(from as Long, b - a)
        }
    }

    // Autosave: back stores and closes; only an invalid draft needs a second back (it cannot be stored).
    // Otherwise no leaving while saving: the save must not be cancelled half-way (new exercises, workout, chat acknowledgement).
    val back = {
        when {
            autosave -> if (state.problem != null && state.dirty && !backArmed) backArmed = true else viewModel.leave(force = backArmed)
            state.saving -> Unit
            state.dirty && !backArmed -> backArmed = true
            else -> onClose()
        }
    }
    BackHandler(enabled = state.dirty || autosave, onBack = back)

    Screen(
        header = {
            BackHeader(t.back, back) {
                if (autosave && !state.loading) {
                    onCoach?.let { HeaderIconButton(Icon.Coach, t.navCoach, it) }
                    HeaderIconButton(Icon.Share, t.share.shareTemplate, { viewModel.flush { if (it != null) onShare() } })
                    HeaderDeleteButton(t.deleteTemplate, viewModel::delete)
                }
            }
        },
        footer = {
            val message = when {
                backArmed -> t.unsavedHint
                else -> state.problem?.describe(t)
            }
            val u = undo
            if (showUndo && u != null) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    val label = when (u.kind) {
                        TemplateEditorViewModel.UndoKind.SetRemoved -> t.setDeleted
                        TemplateEditorViewModel.UndoKind.ExerciseRemoved -> t.training.exerciseRemoved
                        TemplateEditorViewModel.UndoKind.Replaced -> t.training.exerciseReplaced
                    }
                    Text(label, Modifier.weight(1f), style = Theme.type.caption, tone = Tone.Muted)
                    SmallButton(t.undo, viewModel::undoRemoveSet)
                }
            }
            if (message != null) Text(message, style = Theme.type.caption, tone = Tone.Muted)
            when {
                !autosave -> PrimaryButton(t.save, viewModel::save, enabled = !state.loading, loading = state.saving)
                state.loading -> Unit
                workoutRunning -> RunningWorkoutChoice(onToRunning, onEndRunning)
                // Pending changes are stored first, so the training starts from what is on screen.
                else -> PrimaryButton(t.startThisWorkout, { viewModel.flush { saved -> saved?.let(onStart) } })
            }
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
                        Modifier.weight(1f),
                    )
                }
                val caption = when {
                    autosave -> listOfNotNull(lastDone, t.training.autosaved).joinToString(" · ")
                    draft.isNew -> t.newTemplate
                    else -> t.editMode
                }
                Text(caption, style = Theme.type.caption, tone = Tone.Muted)
                // Notes of an import (new exercises, chat message, diff): short, only when they apply.
                val newNames = draft.exercises.filter { it.exercise.id.startsWith(ShareMapper.PENDING_PREFIX) }
                    .map { it.exercise.name }.distinctBy { it.lowercase() }
                viewModel.seed?.hints?.forEach { Text(it, Modifier.padding(top = Space.s), style = Theme.type.caption, tone = Tone.Muted) }
                if (newNames.isNotEmpty()) Text(t.share.importNewNote(newNames), Modifier.padding(top = Space.s), style = Theme.type.caption, tone = Tone.Muted)
                viewModel.seed?.discardLabel?.let { label ->
                    Spacer(Modifier.height(Space.s))
                    SmallButton(label, onClick = viewModel::discardSeed)
                }
                Spacer(Modifier.height(Space.m))
            }
            if (draft.exercises.isEmpty() && autosave) {
                item(key = "empty") { Text(t.workoutPlanEmpty, Modifier.padding(vertical = Space.m), tone = Tone.Muted) }
            }
            itemsIndexed(draft.exercises, key = { _, e -> e.key }) { index, entry ->
                ExerciseBlock(
                    modifier = Modifier.then(reorderableItem(reorder, entry.key)),
                    reorder = reorder,
                    entry = entry,
                    unit = unit,
                    language = language,
                    last = last[entry.exercise.id],
                    expanded = entry.key !in collapsed,
                    isFirst = index == 0,
                    isLast = index == draft.exercises.lastIndex,
                    onToggle = { collapsed = if (entry.key in collapsed) collapsed - entry.key else collapsed + entry.key },
                    onEdit = viewModel::edit,
                    onRemoveSet = { setKey -> viewModel.removeSet(entry.key, setKey) },
                    onRemove = { viewModel.removeExercise(entry.key) },
                    onReplace = { viewModel.pendingReplaceKey = entry.key; onReplaceExercise() },
                    onDetails = { if (!entry.exercise.id.startsWith(ShareMapper.PENDING_PREFIX)) onExerciseDetails(entry.exercise.id) },
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
    language: Language,
    last: LastPerformance?,
    expanded: Boolean,
    isFirst: Boolean,
    isLast: Boolean,
    onToggle: () -> Unit,
    onEdit: ((TemplateDraft) -> TemplateDraft) -> Unit,
    onRemoveSet: (setKey: Long) -> Unit,
    onRemove: () -> Unit,
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
                // What was done last time (as in the running training), so a target can be set right here.
                last?.sets?.takeIf { it.isNotEmpty() }?.let { sets ->
                    Text(t.lastTime(WorkoutFormat.lastTime(sets, type, unit, language, t)), style = Theme.type.caption, tone = Tone.Muted, maxLines = 2)
                }
            }
            MoreMenu(
                t.moreActions(entry.exercise.name),
                buildList {
                    add(MenuItem(t.replace, onClick = onReplace))
                    if (!isFirst) add(MenuItem(t.moveUp) { onEdit { it.moveExercise(key, -1) } })
                    if (!isLast) add(MenuItem(t.moveDown) { onEdit { it.moveExercise(key, 1) } })
                    add(MenuItem(t.remove, destructive = true, onClick = onRemove))
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
                // Rest time and note each on their own line: the note gets the full width (3 lines) instead of a narrow column.
                Column(Modifier.fillMaxWidth().padding(top = Space.xs)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(t.restTime, Modifier.weight(1f), style = Theme.type.caption, tone = Tone.Muted)
                        SmallInput(
                            entry.rest,
                            { v -> onEdit { it.setRest(key, v) } },
                            unit = t.secondsUnit,
                            placeholder = t.restDefault,
                            description = t.restTime,
                            modifier = Modifier.width(96.dp),
                        )
                    }
                    SmallInput(
                        entry.note,
                        { v -> onEdit { it.setNote(key, v.take(300)) } },
                        unit = "",
                        placeholder = t.note,
                        keyboardType = KeyboardType.Text,
                        align = TextAlign.Start,
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

/** Exercise image (tap opens the exercise details); no badge on it. */
@Composable
private fun ExerciseThumb(entry: DraftExercise, onDetails: () -> Unit) {
    val t = LocalStrings.current
    Box(Modifier.size(48.dp).clip(BlockShape.field).semantics { contentDescription = t.exerciseDetails(entry.exercise.name) }
        .pressableRow(onDetails)) {
        ExerciseImage(entry.exercise, size = 48.dp)
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
