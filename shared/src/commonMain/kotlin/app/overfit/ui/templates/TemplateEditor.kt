package app.overfit.ui.templates

import app.overfit.ui.components.SetColumnsHeader
import app.overfit.ui.workout.setColumnLabels
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import app.overfit.ui.components.AnimatedItems
import app.overfit.ui.components.Reveal
import app.overfit.ui.components.RevealNotNull
import app.overfit.ui.components.RollingText
import app.overfit.ui.components.Swap
import app.overfit.ui.components.motionItem
import app.overfit.ui.components.rememberHaptics
import app.overfit.ui.theme.Motion
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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
        /** Counts attempts to leave/start/share that an invalid draft blocked (the screen then explains and arms back). */
        val blocked: Int = 0,
    )

    private val pendingExercises: Map<String, CustomExerciseInput> = seed?.let { ShareMapper.pendingInputs(it.preview) }.orEmpty()

    private var original = TemplateDraft()
    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    /** Exercise that the picker result replaces (replace mode). */
    var pendingReplaceKey: Long? = null

    /** Exercise that gets the picker result as an alternative ("Add alternative"). */
    var pendingAlternativeKey: Long? = null

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
        // Deleted meanwhile: never store it again.
        if (_state.value.deleted) return@withLock null
        val draft = _state.value.draft
        // Unchanged: nothing to store, and nothing to validate (a stored workout is left/started as it is).
        if (draft == original) return@withLock draft.toTemplate(unit)
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
            } catch (_: IllegalArgumentException) {
                // Deleted meanwhile (e.g. on another screen; save requires the row): nothing to store any more.
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
        viewModelScope.launch {
            val saved = persist()
            if (saved == null) _state.update { it.copy(blocked = it.blocked + 1) }
            then(saved)
        }
    }

    /** Back in autosave mode: store and close; an invalid draft first shows why (a second back discards it). */
    fun leave(force: Boolean) {
        saveJob?.cancel()
        viewModelScope.launch {
            val saved = persist()
            // Invalid: the screen shows why and arms back (a second back discards).
            if (saved != null || force) _state.update { it.copy(closed = true) } else _state.update { it.copy(blocked = it.blocked + 1) }
        }
    }

    /** Picker result: add the exercises, or replace one if [pendingReplaceKey] is set. */
    fun onExercisesPicked(ids: List<String>) {
        if (ids.isEmpty()) return
        val replaceKey = pendingReplaceKey
        pendingReplaceKey = null
        val alternativeKey = pendingAlternativeKey
        pendingAlternativeKey = null
        viewModelScope.launch {
            val picked = exercises.getAll(ids)
            if (alternativeKey != null) picked.firstOrNull()?.let { ex -> edit { it.addAlternative(alternativeKey, ex) } }
            else if (replaceKey != null) picked.firstOrNull()?.let { ex -> undoable(UndoKind.Replaced) { it.replaceExercise(replaceKey, ex) } }
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
                suspend fun resolve(te: app.overfit.domain.TemplateExercise): app.overfit.domain.TemplateExercise {
                    val input = pendingExercises[te.exercise.id] ?: return te
                    return te.copy(exercise = te.exercise.copy(id = created.getOrPut(te.exercise.id) { exercises.createCustom(input) }))
                }
                template = template.copy(exercises = template.exercises.map { te ->
                    resolve(te).copy(alternatives = te.alternatives.map { resolve(it) })
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
    val haptics = rememberHaptics()
    val listState = rememberLazyListState()
    val nameFocus = remember { FocusRequester() }

    // "Last time" per exercise, loaded once per set of exercises (not on every typed value), all at once. The list
    // only shows once the first batch is there, so no card grows a line a moment after the screen opened.
    val exerciseIds = if (state.loading) null else draft.allExercises.map { it.exercise.id }.distinct()
    var last by remember { mutableStateOf<Map<String, LastPerformance?>>(emptyMap()) }
    var lastLoaded by remember { mutableStateOf(false) }
    LaunchedEffect(exerciseIds) {
        if (exerciseIds == null) return@LaunchedEffect
        val missing = exerciseIds.filter { it !in last && !it.startsWith(ShareMapper.PENDING_PREFIX) }
        if (missing.isNotEmpty()) {
            val loaded = coroutineScope {
                missing.map { id ->
                    async { id to try { loadLast(id) } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) { null } }
                }.awaitAll()
            }
            last = last + loaded
        }
        lastLoaded = true
    }
    val contentReady = !state.loading && (lastLoaded || exerciseIds.isNullOrEmpty())

    // A blocked leave/start/save says why (footer), buzzes and brings the cause into view: the name field (focused on
    // "Save", where the keyboard helps; not on a blocked back, where a second back must still discard) or the exercise.
    suspend fun showProblem(problem: TemplateDraft.Problem?, focusName: Boolean) {
        haptics.reject()
        when (problem) {
            TemplateDraft.Problem.MissingName -> {
                listState.animateScrollToItem(0)
                if (focusName) runCatching { nameFocus.requestFocus() }
            }
            is TemplateDraft.Problem.InvalidValue -> {
                val index = draft.exercises.indexOfFirst { e -> e.exercise.name == problem.exercise || e.alternatives.any { it.exercise.name == problem.exercise } }
                if (index >= 0) listState.animateScrollToItem(1 + index)
            }
            null -> Unit
        }
    }
    LaunchedEffect(state.blocked) { if (state.blocked > 0) { backArmed = true; showProblem(state.problem, focusName = false) } }
    LaunchedEffect(state.problem) { val p = state.problem; if (!autosave && p != null) showProblem(p, focusName = true) }

    // A new, empty workout starts with its name: the field is focused (keyboard open) as the editor opens.
    LaunchedEffect(contentReady) {
        if (contentReady && !autosave && viewModel.seed == null && draft.name.isEmpty() && draft.exercises.isEmpty()) {
            withFrameNanos { }
            runCatching { nameFocus.requestFocus() }
        }
    }

    // Exercises that were just added (picker) land below the fold: the list goes to the first new one (unfolded).
    val exerciseKeys = if (state.loading) null else draft.exercises.map { it.key }
    var knownExercises by rememberSaveable { mutableStateOf<List<Long>?>(null) }
    LaunchedEffect(exerciseKeys) {
        if (exerciseKeys == null) return@LaunchedEffect
        val known = knownExercises
        knownExercises = exerciseKeys
        if (known == null) return@LaunchedEffect
        val added = exerciseKeys.firstOrNull { it !in known }
        if (added != null && exerciseKeys.size > known.size) {
            collapsed = collapsed - added
            listState.animateScrollToItem(1 + exerciseKeys.indexOf(added))
        }
    }
    // While a value is typed, the keyboard takes half the screen: the main button steps aside, "Undo" stays.
    val typing = WindowInsets.ime.getBottom(LocalDensity.current) > 0
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
            state.loading -> onClose()
            autosave -> viewModel.leave(force = backArmed)
            state.saving -> Unit
            state.dirty && !backArmed -> backArmed = true
            else -> onClose()
        }
    }
    BackHandler(enabled = state.dirty || autosave, onBack = back)

    Screen(
        header = {
            // [⋮ share, delete] [coach]: sharing and deleting are rare, the corner is the coach's (UI audit 12.11.).
            // Present from the first frame (nothing pops in once the workout is loaded); they act once it is there.
            BackHeader(
                t.back, back,
                coach = if (autosave) onCoach else null,
                menu = if (autosave) listOf(
                    MenuItem(t.share.shareTemplate) { if (!state.loading) viewModel.flush { if (it != null) onShare() } },
                    MenuItem(t.deleteTemplate, destructive = true, confirm = true) { if (!state.loading) viewModel.delete() },
                ) else emptyList(),
            )
        },
        footer = {
            // Autosave: a problem is only shown once it blocks leaving/starting (not while typing "62," or a new name).
            val message = when {
                autosave && backArmed -> listOfNotNull(state.problem?.describe(t), t.unsavedHint).joinToString(" ")
                autosave -> null
                backArmed -> t.unsavedHint
                else -> state.problem?.describe(t)
            }
            // One column without gaps of its own: what comes and goes brings its gap along, so nothing jumps.
            Column(Modifier.fillMaxWidth()) {
                RevealNotNull(undo?.takeIf { showUndo }, from = Alignment.Bottom) { u ->
                    Row(Modifier.fillMaxWidth().padding(bottom = Space.m), verticalAlignment = Alignment.CenterVertically) {
                        // A second removal right after the first changes the label softly.
                        Swap(u.kind, Modifier.weight(1f)) { kind ->
                            val label = when (kind) {
                                TemplateEditorViewModel.UndoKind.SetRemoved -> t.setDeleted
                                TemplateEditorViewModel.UndoKind.ExerciseRemoved -> t.training.exerciseRemoved
                                TemplateEditorViewModel.UndoKind.Replaced -> t.training.exerciseReplaced
                            }
                            Text(label, style = Theme.type.caption, tone = Tone.Muted)
                        }
                        SmallButton(t.undo, { haptics.tick(); viewModel.undoRemoveSet() })
                    }
                }
                RevealNotNull(message, from = Alignment.Bottom) { m ->
                    Text(m, Modifier.padding(bottom = Space.m), style = Theme.type.caption, tone = Tone.Muted)
                }
                Reveal(!typing, from = Alignment.Bottom) {
                    when {
                        !autosave -> PrimaryButton(t.save, viewModel::save, enabled = !state.loading, loading = state.saving)
                        workoutRunning -> RunningWorkoutChoice(onToRunning, onEndRunning)
                        // There from the first frame, usable once the workout is loaded. Pending changes are stored
                        // first, so the training starts from what is on screen.
                        else -> PrimaryButton(
                            t.startThisWorkout,
                            { viewModel.flush { saved -> saved?.let(onStart) } },
                            enabled = !state.loading,
                        )
                    }
                }
            }
        },
    ) {
        // Loading -> loaded fades in place (no spinner; the header and the button are already there).
        Swap(contentReady, Modifier.weight(1f).fillMaxWidth()) { ready ->
        if (ready) LazyColumn(
            Modifier.fillMaxSize(),
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
                }
                val caption = when {
                    autosave -> listOfNotNull(lastDone, t.training.autosaved).joinToString(" · ")
                    draft.isNew -> t.newTemplate
                    else -> t.editMode
                }
                Text(caption, style = Theme.type.caption, tone = Tone.Muted)
                // Notes of an import (new exercises, chat message, diff): short, only when they apply.
                val newNames = draft.allExercises.filter { it.exercise.id.startsWith(ShareMapper.PENDING_PREFIX) }
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
                item(key = "empty") { Text(t.workoutPlanEmpty, motionItem().padding(vertical = Space.m), tone = Tone.Muted) }
            }
            itemsIndexed(draft.exercises, key = { _, e -> e.key }) { index, entry ->
                ExerciseBlock(
                    modifier = Modifier.then(reorderableItem(reorder, entry.key)),
                    reorder = reorder,
                    entry = entry,
                    unit = unit,
                    language = language,
                    last = { last[it] },
                    expanded = entry.key !in collapsed,
                    isFirst = index == 0,
                    isLast = index == draft.exercises.lastIndex,
                    onToggle = { collapsed = if (entry.key in collapsed) collapsed - entry.key else collapsed + entry.key },
                    onEdit = viewModel::edit,
                    onRemoveSet = { exerciseKey, setKey -> viewModel.removeSet(exerciseKey, setKey) },
                    onRemove = { viewModel.removeExercise(it) },
                    // Only one pending pick at a time: a picker left with back must not change what the next one does.
                    onReplace = { viewModel.pendingAlternativeKey = null; viewModel.pendingReplaceKey = it; onReplaceExercise() },
                    onAddAlternative = { viewModel.pendingReplaceKey = null; viewModel.pendingAlternativeKey = entry.key; onReplaceExercise() },
                    onDetails = { e -> if (!e.exercise.id.startsWith(ShareMapper.PENDING_PREFIX)) onExerciseDetails(e.exercise.id) },
                )
            }
            item(key = "actions") {
                Column(motionItem()) {
                    Spacer(Modifier.height(Space.s))
                    BlockButton(t.addExercise, onAddExercises)
                }
            }
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
    last: (exerciseId: String) -> LastPerformance?,
    expanded: Boolean,
    isFirst: Boolean,
    isLast: Boolean,
    onToggle: () -> Unit,
    onEdit: ((TemplateDraft) -> TemplateDraft) -> Unit,
    onRemoveSet: (exerciseKey: Long, setKey: Long) -> Unit,
    /** Removes the exercise or one of its alternatives (by key; undoable). */
    onRemove: (key: Long) -> Unit,
    /** Replaces the exercise or one of its alternatives (by key) with a picked one. */
    onReplace: (key: Long) -> Unit,
    onAddAlternative: () -> Unit,
    onDetails: (DraftExercise) -> Unit,
) {
    val t = LocalStrings.current
    val haptics = rememberHaptics()
    val key = entry.key
    Block(modifier) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 64.dp).clip(BlockShape.row).pressableRow(onToggle)
                .dragHandle(reorder, key) { haptics.threshold() }
                .semantics { contentDescription = t.reorder },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // One chevron that turns, instead of two icons that swap.
            val turn by animateFloatAsState(if (expanded) 90f else 0f, Motion.snappy(), label = "chevron")
            Box(Modifier.graphicsLayer { rotationZ = turn }) {
                LineIcon(Icon.ChevronRight, size = app.overfit.ui.theme.IconSize.inline, color = Theme.colors.muted)
            }
            Spacer(Modifier.width(Space.s))
            ExerciseThumb(entry) { onDetails(entry) }
            Spacer(Modifier.width(Space.m))
            ExerciseCaption(entry, unit, language, last(entry.exercise.id), Modifier.weight(1f))
            MoreMenu(
                t.moreActions(entry.exercise.name),
                buildList {
                    add(MenuItem(t.training.addAlternative, onClick = onAddAlternative))
                    add(MenuItem(t.replace) { onReplace(key) })
                    if (!isFirst) add(MenuItem(t.moveUp) { onEdit { it.moveExercise(key, -1) } })
                    if (!isLast) add(MenuItem(t.moveDown) { onEdit { it.moveExercise(key, 1) } })
                    add(MenuItem(t.remove, destructive = true) { onRemove(key) })
                },
            )
        }
        // Folding and unfolding moves the cards below along instead of making them jump.
        Reveal(expanded) { ExerciseEditor(entry, unit, onEdit) { onRemoveSet(key, it) } }
        // Alternatives (A/B): same card, each with its own sets; the training picks one of them. An added or removed
        // alternative grows in or shrinks away.
        AnimatedItems(entry.alternatives, key = { it.key }) { _, alt ->
            Column {
                Spacer(Modifier.height(Space.s))
                Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).testTag("alternative:${alt.exercise.id}"), verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.width(20.dp + Space.s))
                    ExerciseThumb(alt) { onDetails(alt) }
                    Spacer(Modifier.width(Space.m))
                    Column(Modifier.weight(1f)) {
                        Text(t.training.alternative, style = Theme.type.caption, tone = Tone.Muted)
                        ExerciseCaption(alt, unit, language, last(alt.exercise.id), Modifier.fillMaxWidth())
                    }
                    MoreMenu(
                        t.moreActions(alt.exercise.name),
                        listOf(
                            MenuItem(t.replace) { onReplace(alt.key) },
                            MenuItem(t.remove, destructive = true) { onRemove(alt.key) },
                        ),
                    )
                }
                Reveal(expanded) { ExerciseEditor(alt, unit, onEdit) { onRemoveSet(alt.key, it) } }
            }
        }
    }
}

/** Name, "3 sets · rest" and what was done last time (as in the running training). */
@Composable
private fun ExerciseCaption(entry: DraftExercise, unit: WeightUnit, language: Language, last: LastPerformance?, modifier: Modifier) {
    val t = LocalStrings.current
    val type = entry.exercise.trackingType
    Column(modifier) {
        Text(entry.exercise.name, style = Theme.type.label, maxLines = 2)
        val rest = entry.rest.toIntOrNull()?.let { t.restSummary(Numbers.formatDuration(it)) }
        // Only the digits that change roll (a set added, the rest time typed).
        RollingText(listOfNotNull(t.setCount(entry.sets.size), rest).joinToString(" · "), style = Theme.type.caption, tone = Tone.Muted)
        // Loaded with the workout; for an exercise added later the line grows in once it is known.
        RevealNotNull(last?.sets?.takeIf { it.isNotEmpty() }) { sets ->
            Text(t.lastTime(WorkoutFormat.lastTime(sets, type, unit, language, t)), style = Theme.type.caption, tone = Tone.Muted, maxLines = 2)
        }
    }
}

/** Set rows, "+ Add set", rest time and note of one exercise (or alternative). */
@Composable
private fun ExerciseEditor(
    entry: DraftExercise,
    unit: WeightUnit,
    onEdit: ((TemplateDraft) -> TemplateDraft) -> Unit,
    onRemoveSet: (setKey: Long) -> Unit,
) {
    val t = LocalStrings.current
    val key = entry.key
    val type = entry.exercise.trackingType
    val haptics = rememberHaptics()
    // No spacing of its own: a set row brings its gap along (AnimatedItems), so nothing jumps when it comes or goes.
    Column(Modifier.padding(top = Space.xs)) {
        SetColumnsHeader(t.setColumn, setColumnLabels(type, unit, t))
        Spacer(Modifier.height(Space.xs))
        val focus = remember { mutableMapOf<Any, FocusRequester>() }
        fun focusOf(id: Any) = focus.getOrPut(id) { FocusRequester() }
        // A new set grows in, a deleted one shrinks away; the rows below slide.
        AnimatedItems(entry.sets, key = { it.key }, spacing = Space.xs) { i, set ->
            SetRow(
                i + 1, type, unit, set,
                onChange = { transform -> onEdit { it.updateSet(key, set.key, transform) } },
                focusRequester = focusOf(set.key),
                next = entry.sets.getOrNull(i + 1)?.let { focusOf(it.key) },
            ) {
                onRemoveSet(set.key)
            }
        }
        BlockButton(t.addSet, { haptics.tick(); onEdit { it.addSet(key) } }, inBlock = true)
        Spacer(Modifier.height(Space.xs))
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
