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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Loads one exercise and applies changes (settings, custom edits, delete). */
class ExerciseViewModel(private val id: String?, private val repository: ExerciseRepository) : ViewModel() {
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

/** Create ([id] = null) or edit a custom exercise. */
@Composable
fun CustomExerciseScreen(
    viewModel: ExerciseViewModel,
    initialName: String,
    onBack: () -> Unit,
    onSaved: (String) -> Unit,
) {
    val t = LocalStrings.current
    val existing by viewModel.exercise.collectAsStateWithLifecycle()
    val loaded by viewModel.loaded.collectAsStateWithLifecycle()
    if (!loaded) return

    val e = existing
    var name by rememberSaveable(e?.id) { mutableStateOf(e?.name ?: initialName) }
    var tracking by rememberSaveable(e?.id) { mutableStateOf(e?.trackingType ?: TrackingType.WEIGHT_REPS) }
    var muscle by rememberSaveable(e?.id) { mutableStateOf(e?.primaryMuscles?.firstOrNull()) }
    var equipment by rememberSaveable(e?.id) { mutableStateOf(e?.equipment) }
    var rest by rememberSaveable(e?.id) { mutableStateOf(e?.defaultRestSec?.toString().orEmpty()) }

    Screen(
        scroll = true,
        header = { BackHeader(t.back, onBack) },
        footer = {
            PrimaryButton(t.save, {
                viewModel.saveCustom(CustomExerciseInput(name, tracking, rest.toIntOrNull(), muscle, equipment), onSaved)
            }, enabled = name.isNotBlank())
        },
    ) {
        Text(if (e == null) t.newCustomExercise else t.editCustomExercise, tone = Tone.Muted)
        Spacer(Modifier.height(Space.s))
        BigInput(name, { name = it.take(60) }, t.exerciseNamePlaceholder, Modifier.fillMaxWidth())

        SectionTitle(t.tracking)
        TrackingChooser(tracking) { tracking = it }

        SectionTitle(t.muscleGroup)
        ChoiceChips(Muscle.entries, muscle, { t.muscle(it) }) { muscle = it }

        SectionTitle(t.equipment)
        ChoiceChips(Equipment.entries, equipment, { t.equipmentName(it) }) { equipment = it }

        Spacer(Modifier.height(Space.m))
        ListRow(t.restTime) {
            RowInput(rest, { rest = it.filter(Char::isDigit).take(4) }, onCommit = {}, keyboardType = KeyboardType.Number)
        }
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
