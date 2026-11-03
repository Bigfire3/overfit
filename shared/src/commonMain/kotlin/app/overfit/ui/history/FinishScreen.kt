package app.overfit.ui.history

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.overfit.data.FinishedWorkout
import app.overfit.data.WorkoutRepository
import app.overfit.domain.Language
import app.overfit.domain.PersonalRecord
import app.overfit.domain.RecordKind
import app.overfit.domain.WeightUnit
import app.overfit.domain.Numbers
import app.overfit.domain.WorkoutSummary
import app.overfit.i18n.LocalStrings
import app.overfit.i18n.Strings
import app.overfit.platform.prepareJpeg
import app.overfit.platform.rememberPhotoPicker
import app.overfit.ui.components.BackHeader
import app.overfit.ui.components.ListRow
import app.overfit.ui.components.MultilineInput
import app.overfit.ui.components.PrimaryButton
import app.overfit.ui.components.Screen
import app.overfit.ui.components.SectionTitle
import app.overfit.ui.components.Text
import app.overfit.ui.components.TextButton
import app.overfit.ui.components.Title
import app.overfit.ui.components.Tone
import app.overfit.ui.theme.Space
import app.overfit.ui.theme.Theme
import app.overfit.workout.WorkoutFormat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class FinishData(val summary: WorkoutSummary, val finished: FinishedWorkout)

/**
 * Finish flow: summary, new records, feeling, note, photos and the coach slot. The workout is
 * already stored as finished; "Save" (and back) only add note and feeling. Everything that must
 * survive leaving this screen runs in [appScope].
 */
@Composable
fun FinishScreen(
    workoutId: String,
    workouts: WorkoutRepository,
    awaitFinished: suspend () -> Unit,
    unit: WeightUnit,
    language: Language,
    appScope: CoroutineScope,
    coachSection: @Composable (String) -> Unit,
    onSaved: (String) -> Unit,
    onClose: () -> Unit,
) {
    val t = LocalStrings.current
    val h = t.history
    val data by produceState<FinishData?>(null, workoutId) {
        awaitFinished()
        val summary = workouts.summary(workoutId)
        val finished = workouts.getFinished(workoutId)
        value = if (summary != null && finished != null) FinishData(summary, finished) else null
        if (value == null) onClose()
    }
    val photos by remember(workoutId) { workouts.observePhotosOf(workoutId) }.collectAsStateWithLifecycle(emptyList())

    var feeling by rememberSaveable { mutableStateOf<Int?>(null) }
    var note by rememberSaveable { mutableStateOf("") }
    var seeded by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(data) {
        val d = data ?: return@LaunchedEffect
        if (!seeded) { seeded = true; feeling = d.finished.feeling; note = d.finished.note.orEmpty() }
    }
    var saving by remember { mutableStateOf(false) }

    val picker = rememberPhotoPicker { bytes ->
        appScope.launch {
            val jpeg = withContext(Dispatchers.Default) { prepareJpeg(bytes) } ?: return@launch
            workouts.addPhoto(workoutId, jpeg)
        }
    }

    val save = {
        if (!saving) {
            saving = true
            val noteText = note
            val feelingValue = feeling
            appScope.launch {
                if (data != null) workouts.updateDetails(workoutId, noteText, feelingValue)
                withContext(Dispatchers.Main) {
                    onSaved(workoutId)
                    onClose()
                }
            }
        }
        Unit
    }

    Screen(
        scroll = true,
        header = { BackHeader(t.back, save) },
        footer = { PrimaryButton(t.save, save, enabled = data != null, loading = saving) },
    ) {
        val d = data
        Text(h.finishTitle, tone = Tone.Muted)
        Spacer(Modifier.height(Space.s))
        Title(d?.finished?.workout?.name.orEmpty())
        if (d != null) {
            Spacer(Modifier.height(Space.l))
            SummaryRows(d.summary, unit, language, t)

            if (d.summary.records.isNotEmpty()) {
                SectionTitle(h.newRecords)
                d.summary.records.forEach { r -> ListRow(recordText(r, unit, language, t)) }
            }
        }

        SectionTitle(h.feeling)
        FeelingPicker(feeling, h.feelingValue) { feeling = it }

        SectionTitle(h.note)
        MultilineInput(note, { note = it.take(2000) }, h.notePlaceholder)

        SectionTitle(h.photos)
        if (photos.isNotEmpty()) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                photos.forEachIndexed { i, p ->
                    PhotoThumb(
                        p, h.photoDescription(i + 1),
                        removeLabel = h.removePhoto,
                        onRemove = { appScope.launch { workouts.deletePhoto(p.id) } },
                    )
                }
            }
            Spacer(Modifier.height(Space.s))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.l)) {
            if (picker.canTakePhoto) TextButton(h.takePhoto, picker.takePhoto, tone = Tone.Default)
            TextButton(h.pickPhoto, picker.pickFromLibrary, tone = Tone.Default)
        }
        Text(h.photosPrivate, style = Theme.type.caption, tone = Tone.Faint)

        Spacer(Modifier.height(Space.l))
        coachSection(workoutId)
    }
}

@Composable
internal fun SummaryRows(s: WorkoutSummary, unit: WeightUnit, language: Language, t: Strings) {
    val h = t.history
    ListRow(h.duration, WorkoutFormat.elapsed(s.durationMs))
    ListRow(h.setsLabel, h.setsOf(s.completedSets, s.totalSets))
    if (s.volumeKg > 0) ListRow(h.volume, formatVolume(s.volumeKg, unit, language))
    ListRow(h.exercisesLabel, s.exerciseCount.toString())
    if (s.skippedSets > 0 || s.addedExercises > 0 || s.replacedExercises > 0) {
        Spacer(Modifier.height(Space.s))
        Text(h.deviations(s.skippedSets, s.addedExercises, s.replacedExercises), style = Theme.type.caption, tone = Tone.Muted)
    }
}

private fun recordText(r: PersonalRecord, unit: WeightUnit, language: Language, t: Strings): String {
    val h = t.history
    fun kg(v: Double) = formatKg(v, unit, language)
    return when (r.kind) {
        RecordKind.WEIGHT -> h.recordLine(r.exerciseName, "", kg(r.value), kg(r.previous))
        RecordKind.E1RM -> h.recordLine(r.exerciseName, h.estimatedOneRm, kg(r.value), kg(r.previous))
        RecordKind.REPS -> h.recordLine(r.exerciseName, "", "${r.value.toInt()} ${t.repsUnit}", "${r.previous.toInt()} ${t.repsUnit}")
        RecordKind.DURATION -> h.recordLine(r.exerciseName, "", Numbers.formatDuration(r.value.toInt()), Numbers.formatDuration(r.previous.toInt()))
    }
}
