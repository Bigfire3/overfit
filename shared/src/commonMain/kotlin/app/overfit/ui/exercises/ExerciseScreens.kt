package app.overfit.ui.exercises

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import app.overfit.data.WorkoutRepository
import app.overfit.domain.Language
import app.overfit.domain.Numbers
import app.overfit.domain.SetValues
import app.overfit.domain.WeightUnit
import app.overfit.domain.WorkoutStats
import app.overfit.ui.components.Hairline
import app.overfit.ui.theme.Theme
import app.overfit.workout.WorkoutFormat
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.overfit.data.CustomExerciseInput
import app.overfit.data.ExerciseRepository
import app.overfit.domain.Equipment
import app.overfit.domain.Exercise
import app.overfit.domain.Muscle
import app.overfit.domain.TrackingType
import app.overfit.i18n.LocalStrings
import app.overfit.ui.components.BackHeader
import app.overfit.ui.components.BigInput
import app.overfit.ui.components.Chip
import app.overfit.ui.components.ExerciseImage
import app.overfit.ui.components.ListRow
import app.overfit.ui.components.PrimaryButton
import app.overfit.ui.components.RowInput
import app.overfit.ui.components.Screen
import app.overfit.ui.components.SectionTitle
import app.overfit.ui.components.Text
import app.overfit.ui.components.Title
import app.overfit.ui.components.Tone
import app.overfit.ui.theme.Space
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import app.overfit.ai.ExerciseAutofill
import app.overfit.ai.ExerciseSuggestion
import app.overfit.coach.CoachErrorKind
import app.overfit.coach.CoachException
import app.overfit.domain.ExerciseSearch
import app.overfit.i18n.LocalExerciseFormStrings
import app.overfit.ui.components.MultilineInput
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Loads one exercise and applies changes (settings, custom edits, delete); for the form also AI autofill and "Did you mean …?". */
class ExerciseViewModel(
    private val id: String?,
    private val repository: ExerciseRepository,
    private val autofill: ExerciseAutofill? = null,
) : ViewModel() {
    /** null while loading (or for a new custom exercise). */
    private val _exercise = MutableStateFlow<Exercise?>(null)
    val exercise: StateFlow<Exercise?> = _exercise.asStateFlow()
    private val _loaded = MutableStateFlow(id == null)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    init {
        if (id != null) viewModelScope.launch {
            _exercise.value = repository.get(id)
            _loaded.value = true
        }
    }

    /** Active catalogue as search index ("Did you mean …?"); loaded on first use. */
    private val _index = MutableStateFlow<List<ExerciseSearch.Indexed>>(emptyList())
    private var indexLoaded = false

    fun loadIndex() {
        if (indexLoaded) return
        indexLoaded = true
        viewModelScope.launch { _index.value = ExerciseSearch.index(repository.getActive()) }
    }

    /** Up to three existing exercises that look like [name] (empty for short names and when editing). */
    fun similar(name: String): List<Exercise> {
        val q = name.trim()
        if (id != null || q.length < 3) return emptyList()
        val index = _index.value
        if (index.isEmpty()) return emptyList()
        return ExerciseSearch.search(index, ExerciseSearch.Filter(query = q)).take(3)
    }
    val indexVersion: StateFlow<List<ExerciseSearch.Indexed>> = _index.asStateFlow()

    val aiAvailable: Boolean get() = autofill?.available == true

    sealed interface AutofillState {
        data object Idle : AutofillState
        data object Loading : AutofillState
        data object Done : AutofillState
        data class Failed(val kind: CoachErrorKind) : AutofillState
    }

    private val _autofillState = MutableStateFlow<AutofillState>(AutofillState.Idle)
    val autofillState: StateFlow<AutofillState> = _autofillState.asStateFlow()

    /** Latest AI answer, taken over by the form once ([consumeSuggestion]). */
    private val _suggestion = MutableStateFlow<ExerciseSuggestion?>(null)
    val suggestion: StateFlow<ExerciseSuggestion?> = _suggestion.asStateFlow()

    fun autofill(name: String, language: Language) {
        val ai = autofill ?: return
        if (name.isBlank() || _autofillState.value == AutofillState.Loading) return
        _autofillState.value = AutofillState.Loading
        viewModelScope.launch {
            _autofillState.value = try {
                _suggestion.value = ai.fill(name, language)
                AutofillState.Done
            } catch (e: CancellationException) {
                throw e
            } catch (e: CoachException) {
                AutofillState.Failed(e.kind)
            } catch (_: Exception) {
                AutofillState.Failed(CoachErrorKind.SERVER)
            }
        }
    }

    fun consumeSuggestion() { _suggestion.value = null }

    fun saveSettings(trackingType: TrackingType, restSec: Int?, done: () -> Unit) {
        val current = _exercise.value ?: return
        viewModelScope.launch { repository.updateSettings(current.id, trackingType, restSec); done() }
    }

    fun saveCustom(input: CustomExerciseInput, done: (String) -> Unit) {
        viewModelScope.launch {
            val savedId = if (id == null) repository.createCustom(input) else id.also { repository.updateCustom(it, input) }
            done(savedId)
        }
    }

    fun delete(done: (ExerciseRepository.DeleteResult) -> Unit) {
        val current = _exercise.value ?: return
        viewModelScope.launch { done(repository.deleteCustom(current.id)) }
    }
}

/**
 * Details of an exercise (no instructions: image, name, muscles). Tracking type and rest time are
 * editable for catalogue exercises too; custom exercises can be edited and deleted.
 */
@Composable
fun ExerciseDetailScreen(
    viewModel: ExerciseViewModel,
    onBack: () -> Unit,
    onEditCustom: (String) -> Unit,
    workouts: WorkoutRepository,
    unit: WeightUnit,
    language: Language,
) {
    val t = LocalStrings.current
    val exercise by viewModel.exercise.collectAsStateWithLifecycle()
    val loaded by viewModel.loaded.collectAsStateWithLifecycle()
    LaunchedEffect(loaded, exercise) { if (loaded && exercise == null) onBack() }
    val e = exercise ?: return

    var tracking by remember(e.id, e.trackingType) { mutableStateOf(e.trackingType) }
    var rest by remember(e.id, e.defaultRestSec) { mutableStateOf(e.defaultRestSec?.toString().orEmpty()) }
    var confirmDelete by remember { mutableStateOf(false) }
    LaunchedEffect(confirmDelete) { if (confirmDelete) { delay(4000); confirmDelete = false } }

    Screen(
        scroll = true,
        header = { BackHeader(t.back, onBack) },
        footer = {
            if (e.isCustom) PrimaryButton(t.edit, { onEditCustom(e.id) })
            else PrimaryButton(t.save, { viewModel.saveSettings(tracking, rest.toIntOrNull(), onBack) })
        },
    ) {
        ExerciseImage(e, size = 160.dp)
        Spacer(Modifier.height(Space.l))
        Title(e.name)
        Spacer(Modifier.height(Space.s))
        if (e.primaryMuscles.isNotEmpty()) Text(e.primaryMuscles.joinToString { t.muscle(it) }, tone = Tone.Muted)
        if (e.secondaryMuscles.isNotEmpty()) {
            Text(t.alsoTrains(e.secondaryMuscles.joinToString { t.muscle(it) }), tone = Tone.Faint)
        }
        e.equipment?.let { Text(t.equipmentName(it), tone = Tone.Faint) }
        e.note?.let {
            Spacer(Modifier.height(Space.m))
            Text(it)
        }

        SectionTitle(t.tracking)
        if (e.isCustom) {
            Text(t.trackingName(e.trackingType))
            e.defaultRestSec?.let { ListRow(t.restTime, it.toString()) }
            SectionTitle(t.data)
            ListRow(
                if (confirmDelete) t.deleteConfirm else t.delete,
                tone = if (confirmDelete) Tone.Default else Tone.Muted,
                onClick = { if (confirmDelete) viewModel.delete { onBack() } else confirmDelete = true },
            )
        } else {
            TrackingChooser(tracking) { tracking = it }
            Spacer(Modifier.height(Space.s))
            ListRow(t.restTime) {
                RowInput(rest, { rest = it.filter(Char::isDigit).take(4) }, onCommit = {}, keyboardType = KeyboardType.Number)
            }
        }

        ExerciseProgress(e, workouts, unit, language)
    }
}

/** "Progress": best values and the last workouts with this exercise. */
@Composable
private fun ColumnScope.ExerciseProgress(e: Exercise, workouts: WorkoutRepository, unit: WeightUnit, language: Language) {
    val t = LocalStrings.current
    val h = t.history
    val rows by remember(e.id) { workouts.exerciseSets(e.id) }.collectAsStateWithLifecycle(initialValue = null)
    val list = rows ?: return
    SectionTitle(h.progress)
    if (list.isEmpty()) {
        Text(h.progressEmpty, tone = Tone.Muted)
        return
    }
    val type = e.trackingType
    fun kg(v: Double) = "${Numbers.formatWeight(v, unit, language)} ${unit.symbol}"
    val bests = WorkoutStats.bests(list.map { SetValues(it.weightKg, it.reps, it.durationSec) }, type)
    Text(h.bests, style = Theme.type.caption, tone = Tone.Muted)
    bests.maxWeightKg?.let { ListRow(h.maxWeight, kg(it)) }
    bests.bestE1rmKg?.let { ListRow(h.bestE1rm, kg(it)) }
    bests.maxReps?.let { ListRow(h.maxReps, "$it ${t.repsUnit}") }
    bests.maxDurationSec?.let { ListRow(h.longestTime, Numbers.formatDuration(it)) }

    Spacer(Modifier.height(Space.l))
    Text(h.recentWorkouts, style = Theme.type.caption, tone = Tone.Muted)
    val sessions = list.groupBy { it.sessionId }.values.take(10)
    sessions.forEach { sets ->
        val ordered = sets.sortedBy { it.position }
        val values = ordered.map { SetValues(it.weightKg, it.reps, it.durationSec) }
        val e1rm = if (type == TrackingType.WEIGHT_REPS) WorkoutStats.bests(values, type).bestE1rmKg else null
        val date = t.date(Instant.fromEpochMilliseconds(ordered.first().startedAt).toLocalDateTime(TimeZone.currentSystemDefault()).date)
        val text = values.map { WorkoutFormat.values(it, type, unit, language, t) }.filter { it.isNotEmpty() }.joinToString(" · ")
        Column {
            Column(Modifier.fillMaxWidth().padding(vertical = Space.m)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(date, Modifier.weight(1f), tone = Tone.Muted)
                    if (e1rm != null) Text(h.e1rmShort(kg(e1rm)), style = Theme.type.caption, tone = Tone.Muted)
                }
                Text(text)
            }
            Hairline()
        }
    }
}

/**
 * Create ([id] = null) or edit a custom exercise. Only the name is needed; "Fill in with AI" suggests tracking type,
 * muscles, equipment, rest and a short note, all editable before saving. For new exercises similar catalogue entries
 * are offered first ("Did you mean …?", [onPickExisting]) so no duplicates are created.
 */
@Composable
fun CustomExerciseScreen(
    viewModel: ExerciseViewModel,
    initialName: String,
    language: Language,
    onBack: () -> Unit,
    onSaved: (String) -> Unit,
    onPickExisting: ((Exercise) -> Unit)? = null,
) {
    val t = LocalStrings.current
    val f = LocalExerciseFormStrings.current
    val existing by viewModel.exercise.collectAsStateWithLifecycle()
    val loaded by viewModel.loaded.collectAsStateWithLifecycle()
    if (!loaded) return

    val e = existing
    var name by rememberSaveable(e?.id) { mutableStateOf(e?.name ?: initialName) }
    var tracking by rememberSaveable(e?.id) { mutableStateOf(e?.trackingType ?: TrackingType.WEIGHT_REPS) }
    var primary by rememberSaveable(e?.id) { mutableStateOf(MuscleKeys.of(e?.primaryMuscles.orEmpty())) }
    var secondary by rememberSaveable(e?.id) { mutableStateOf(MuscleKeys.of(e?.secondaryMuscles.orEmpty())) }
    var equipment by rememberSaveable(e?.id) { mutableStateOf(e?.equipment) }
    var rest by rememberSaveable(e?.id) { mutableStateOf(e?.defaultRestSec?.toString().orEmpty()) }
    var note by rememberSaveable(e?.id) { mutableStateOf(e?.note.orEmpty()) }

    val autofillState by viewModel.autofillState.collectAsStateWithLifecycle()
    val suggestion by viewModel.suggestion.collectAsStateWithLifecycle()
    LaunchedEffect(suggestion) {
        val s = suggestion ?: return@LaunchedEffect
        tracking = s.trackingType
        primary = MuscleKeys.of(s.primary)
        secondary = MuscleKeys.of(s.secondary)
        equipment = s.equipment
        s.restSec?.let { rest = it.toString() }
        s.note?.let { note = it }
        viewModel.consumeSuggestion()
    }
    val index by viewModel.indexVersion.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { if (e == null) viewModel.loadIndex() }
    val similar = remember(name, index) { viewModel.similar(name) }

    Screen(
        scroll = true,
        header = { BackHeader(t.back, onBack) },
        footer = {
            PrimaryButton(t.save, {
                val input = CustomExerciseInput(
                    name = name,
                    trackingType = tracking,
                    restSec = rest.toIntOrNull(),
                    primary = MuscleKeys.parse(primary),
                    equipment = equipment,
                    secondary = MuscleKeys.parse(secondary),
                    note = note,
                )
                viewModel.saveCustom(input, onSaved)
            }, enabled = name.isNotBlank())
        },
    ) {
        Text(if (e == null) t.newCustomExercise else t.editCustomExercise, tone = Tone.Muted)
        Spacer(Modifier.height(Space.s))
        BigInput(name, { name = it.take(60) }, t.exerciseNamePlaceholder, Modifier.fillMaxWidth())

        Spacer(Modifier.height(Space.m))
        if (viewModel.aiAvailable) {
            val loading = autofillState == ExerciseViewModel.AutofillState.Loading
            ListRow(
                if (loading) f.aiFilling else f.aiFill,
                tone = if (name.isBlank() || loading) Tone.Muted else Tone.Default,
                onClick = { if (!loading && name.isNotBlank()) viewModel.autofill(name, language) },
            )
            val hint = when (val st = autofillState) {
                ExerciseViewModel.AutofillState.Done -> f.aiFilled
                is ExerciseViewModel.AutofillState.Failed -> f.aiError(st.kind)
                else -> if (name.isBlank()) f.aiNeedName else f.aiFillHint
            }
            Text(hint, Modifier.padding(vertical = Space.s), style = Theme.type.caption, tone = Tone.Muted)
        } else {
            Text(f.aiNoAccess, style = Theme.type.caption, tone = Tone.Muted)
        }

        if (similar.isNotEmpty() && onPickExisting != null) {
            SectionTitle(f.didYouMean)
            Text(f.didYouMeanHint, style = Theme.type.caption, tone = Tone.Muted)
            similar.forEach { candidate -> ExerciseRow(candidate, language, selected = null) { onPickExisting(candidate) } }
        }

        SectionTitle(t.tracking)
        TrackingChooser(tracking) { tracking = it }

        SectionTitle(f.primaryMuscles)
        MultiChoiceChips(Muscle.entries, MuscleKeys.parse(primary), { t.muscle(it) }) { m ->
            val list = MuscleKeys.parse(primary)
            primary = MuscleKeys.of(if (m in list) list - m else list + m)
            if (m !in list) secondary = MuscleKeys.of(MuscleKeys.parse(secondary) - m)
        }

        SectionTitle(f.secondaryMuscles)
        MultiChoiceChips(Muscle.entries.filter { it !in MuscleKeys.parse(primary) }, MuscleKeys.parse(secondary), { t.muscle(it) }) { m ->
            val list = MuscleKeys.parse(secondary)
            secondary = MuscleKeys.of(if (m in list) list - m else list + m)
        }

        SectionTitle(t.equipment)
        ChoiceChips(Equipment.entries, equipment, { t.equipmentName(it) }) { equipment = it }

        Spacer(Modifier.height(Space.m))
        ListRow(t.restTime) {
            RowInput(rest, { rest = it.filter(Char::isDigit).take(4) }, onCommit = {}, keyboardType = KeyboardType.Number)
        }

        SectionTitle(f.note)
        MultilineInput(note, { note = it.take(500) }, f.notePlaceholder, minHeight = 64.dp)
    }
}

/** Muscle lists as comma-separated keys, so they survive in rememberSaveable on every platform. */
internal object MuscleKeys {
    fun of(list: List<Muscle>): String = list.distinct().joinToString(",") { it.key }
    fun parse(keys: String): List<Muscle> = keys.split(',').mapNotNull { Muscle.fromKey(it) }
}

/** Multiple choice: tapping toggles. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> MultiChoiceChips(options: List<T>, selected: List<T>, label: (T) -> String, onToggle: (T) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s), verticalArrangement = Arrangement.spacedBy(Space.s)) {
        options.forEach { option -> Chip(label(option), option in selected, { onToggle(option) }) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TrackingChooser(selected: TrackingType, onChange: (TrackingType) -> Unit) {
    val t = LocalStrings.current
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s), verticalArrangement = Arrangement.spacedBy(Space.s)) {
        TrackingType.entries.forEach { type -> Chip(t.trackingName(type), type == selected, { onChange(type) }) }
    }
}

/** Single choice with "none" by tapping the selected chip again. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> ChoiceChips(options: List<T>, selected: T?, label: (T) -> String, onChange: (T?) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s), verticalArrangement = Arrangement.spacedBy(Space.s)) {
        options.forEach { option -> Chip(label(option), option == selected, { onChange(if (option == selected) null else option) }) }
    }
}
