package app.overfit.ui.templates

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
import app.overfit.ui.components.BigInput
import app.overfit.ui.components.ExerciseImage
import app.overfit.ui.components.Hairline
import app.overfit.ui.components.Icon
import app.overfit.ui.components.IconButton
import app.overfit.ui.components.LineIcon
import app.overfit.ui.components.ListRow
import app.overfit.ui.components.PrimaryButton
import app.overfit.ui.components.Screen
import app.overfit.ui.components.SmallInput
import app.overfit.ui.components.Text
import app.overfit.ui.components.TextButton
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
 * Create/edit a template: name and exercises (collapsible sections) with planned sets and target
 * values, rest and note per exercise. Saving writes everything in one transaction. Leaving with
 * unsaved changes needs a second back press.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun TemplateEditorScreen(
    viewModel: TemplateEditorViewModel,
    unit: WeightUnit,
    onClose: () -> Unit,
    onAddExercises: () -> Unit,
    onReplaceExercise: () -> Unit,
) {
    val t = LocalStrings.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val draft = state.draft
    var collapsed by rememberSaveable { mutableStateOf(listOf<Long>()) }
    var backArmed by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    LaunchedEffect(state.closed) { if (state.closed) onClose() }
    LaunchedEffect(backArmed) { if (backArmed) { delay(4000); backArmed = false } }
    LaunchedEffect(confirmDelete) { if (confirmDelete) { delay(4000); confirmDelete = false } }

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

    Screen(
        header = { BackHeader(t.back, back) },
        footer = {
            val message = when {
                backArmed -> t.unsavedHint
                else -> state.problem?.describe(t)
            }
            if (message != null) Text(message, style = Theme.type.caption, tone = Tone.Muted)
            PrimaryButton(t.save, viewModel::save, enabled = !state.loading, loading = state.saving)
        },
    ) {
        if (state.loading) return@Screen
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = listState, contentPadding = PaddingValues(bottom = Space.l)) {
            item(key = "name") {
                Text(if (draft.isNew) t.newTemplate else t.editTemplate, tone = Tone.Muted)
                Spacer(Modifier.height(Space.s))
                BigInput(draft.name, { v -> viewModel.edit { it.withName(v.take(60)) } }, t.templateNamePlaceholder, Modifier.fillMaxWidth())
                Spacer(Modifier.height(Space.l))
                Hairline()
            }
            itemsIndexed(draft.exercises, key = { _, e -> e.key }) { index, entry ->
                ExerciseSection(
                    modifier = Modifier.then(reorderableItem(reorder, entry.key)),
                    reorder = reorder,
                    entry = entry,
                    unit = unit,
                    expanded = entry.key !in collapsed,
                    isFirst = index == 0,
                    isLast = index == draft.exercises.lastIndex,
                    onToggle = { collapsed = if (entry.key in collapsed) collapsed - entry.key else collapsed + entry.key },
                    onEdit = viewModel::edit,
                    onReplace = { viewModel.pendingReplaceKey = entry.key; onReplaceExercise() },
                )
            }
            item(key = "actions") {
                ListRow(t.addExercise, onClick = onAddExercises) { LineIcon(Icon.Plus) }
                if (!draft.isNew) {
                    ListRow(
                        if (confirmDelete) t.deleteConfirm else t.deleteTemplate,
                        tone = if (confirmDelete) Tone.Default else Tone.Muted,
                        onClick = { if (confirmDelete) viewModel.delete() else confirmDelete = true },
                    )
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
private fun ExerciseSection(
    modifier: Modifier,
    reorder: ReorderState,
    entry: DraftExercise,
    unit: WeightUnit,
    expanded: Boolean,
    isFirst: Boolean,
    isLast: Boolean,
    onToggle: () -> Unit,
    onEdit: ((TemplateDraft) -> TemplateDraft) -> Unit,
    onReplace: () -> Unit,
) {
    val t = LocalStrings.current
    val key = entry.key
    val type = entry.exercise.trackingType
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 64.dp).pressableRow(onToggle).padding(vertical = Space.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ExerciseImage(entry.exercise, size = 40.dp)
            Spacer(Modifier.width(Space.m))
            Column(Modifier.weight(1f)) {
                Text(entry.exercise.name, style = Theme.type.label)
                val rest = entry.rest.toIntOrNull()?.let { t.restSummary(Numbers.formatDuration(it)) }
                Text(listOfNotNull(t.setCount(entry.sets.size), rest).joinToString(" · "), style = Theme.type.caption, tone = Tone.Muted)
            }
            Box(
                Modifier.size(40.dp).dragHandle(reorder, key).semantics { contentDescription = t.reorder },
                contentAlignment = Alignment.Center,
            ) { LineIcon(Icon.Drag, color = Theme.colors.muted) }
            LineIcon(if (expanded) Icon.ChevronUp else Icon.ChevronDown, size = 20.dp, color = Theme.colors.muted)
        }
        if (expanded) {
            entry.sets.forEachIndexed { i, set ->
                SetRow(i + 1, type, unit, set, onChange = { transform -> onEdit { it.updateSet(key, set.key, transform) } }) {
                    onEdit { it.removeSet(key, set.key) }
                }
            }
            Row(Modifier.fillMaxWidth().padding(vertical = Space.s), horizontalArrangement = Arrangement.spacedBy(Space.l)) {
                TextButton(t.addSet, { onEdit { it.addSet(key) } }, tone = Tone.Default)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.m), verticalAlignment = Alignment.Bottom) {
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
            Row(Modifier.fillMaxWidth().padding(vertical = Space.m), horizontalArrangement = Arrangement.spacedBy(Space.l)) {
                if (!isFirst) TextButton(t.moveUp, { onEdit { it.moveExercise(key, -1) } })
                if (!isLast) TextButton(t.moveDown, { onEdit { it.moveExercise(key, 1) } })
                TextButton(t.replace, onReplace)
                TextButton(t.remove, { onEdit { it.removeExercise(key) } })
            }
        }
        Hairline()
    }
}

@Composable
private fun SetRow(
    number: Int,
    type: TrackingType,
    unit: WeightUnit,
    set: TemplateDraft.DraftSet,
    onChange: ((TemplateDraft.DraftSet) -> TemplateDraft.DraftSet) -> Unit,
    onRemove: () -> Unit,
) {
    val t = LocalStrings.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.m)) {
        Text("$number", Modifier.width(20.dp), style = Theme.type.caption, tone = Tone.Muted)
        if (type.usesWeight) {
            Row(Modifier.weight(1.3f), verticalAlignment = Alignment.CenterVertically) {
                SmallInput(
                    set.weight,
                    { v -> onChange { it.copy(weight = v.filter { c -> c.isDigit() || c == ',' || c == '.' || c == '-' }.take(8)) } },
                    unit = unit.symbol,
                    keyboardType = KeyboardType.Decimal,
                    modifier = Modifier.weight(1f),
                )
                // Many number keyboards have no minus key: negative weight = assistance (e.g. a band).
                TextButton("±", {
                    onChange { it.copy(weight = if (it.weight.startsWith("-")) it.weight.removePrefix("-") else "-${it.weight}") }
                }, Modifier.padding(start = Space.xs))
            }
        }
        if (type.usesReps) {
            SmallInput(set.reps, { v -> onChange { it.copy(reps = v.filter(Char::isDigit).take(4)) } }, unit = t.repsUnit, modifier = Modifier.weight(1f))
        }
        if (type.usesTime) {
            SmallInput(
                set.duration,
                { v -> onChange { it.copy(duration = v.filter { c -> c.isDigit() || c == ':' }.take(6)) } },
                unit = t.secondsUnit,
                keyboardType = KeyboardType.Number,
                modifier = Modifier.weight(1f),
            )
        }
        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            IconButton(Icon.Close, t.removeSet(number), onRemove)
        }
    }
}
