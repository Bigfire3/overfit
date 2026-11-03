package app.overfit.ui.history

import app.overfit.chat.ChatNote

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.overfit.data.FinishedWorkout
import app.overfit.data.TemplateRepository
import app.overfit.data.WorkoutRepository
import app.overfit.data.db.HistoryRow
import app.overfit.domain.ExerciseOrigin
import app.overfit.domain.Language
import app.overfit.domain.PlannedSet
import app.overfit.domain.SetValues
import app.overfit.domain.Template
import app.overfit.domain.TemplateExercise
import app.overfit.domain.WeightUnit
import app.overfit.domain.Workout
import app.overfit.domain.WorkoutSummary
import app.overfit.i18n.LocalStrings
import app.overfit.ui.components.HeaderDeleteButton
import app.overfit.ui.components.HeaderIconButton
import app.overfit.ui.components.BackHeader
import app.overfit.ui.components.Hairline
import app.overfit.ui.components.Icon
import app.overfit.ui.components.LineIcon
import app.overfit.ui.components.ListRow
import app.overfit.ui.components.Screen
import app.overfit.ui.components.SectionTitle
import app.overfit.ui.components.Text
import app.overfit.ui.components.Title
import app.overfit.ui.components.Tone
import app.overfit.ui.components.pressableRow
import app.overfit.ui.theme.Space
import app.overfit.ui.theme.Theme
import app.overfit.workout.WorkoutFormat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** History tab: finished workouts, newest first. [rows] null = still loading. */
@Composable
fun HistoryScreen(
    rows: List<HistoryRow>?,
    unit: WeightUnit,
    language: Language,
    onOpen: (String) -> Unit,
) {
    val t = LocalStrings.current
    val h = t.history
    Screen(scroll = true) {
        Title(t.historyTitle)
        Spacer(Modifier.height(Space.l))
        if (rows != null && rows.isEmpty()) Text(t.historyEmpty, tone = Tone.Muted)
        if (rows != null && rows.isNotEmpty()) Hairline()
        rows?.forEach { row ->
            val meta = listOfNotNull(
                row.endedAt?.let { WorkoutFormat.elapsed(it - row.startedAt) },
                h.setCount(row.completedSets),
                formatVolume(row.volumeKg, unit, language).takeIf { row.volumeKg > 0 },
                h.photoCount(row.photoCount).takeIf { row.photoCount > 0 },
            ).joinToString(" · ")
            Column(Modifier.pressableRow { onOpen(row.id) }) {
                Column(Modifier.fillMaxWidth().padding(vertical = Space.m)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(row.name, Modifier.weight(1f), style = Theme.type.label, maxLines = 1)
                        Spacer(Modifier.width(Space.m))
                        Text(t.date(localDateTime(row.startedAt).date), style = Theme.type.caption, tone = Tone.Muted)
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(meta, style = Theme.type.caption, tone = Tone.Muted)
                }
                Hairline()
            }
        }
    }
}

private data class DetailData(val finished: FinishedWorkout, val summary: WorkoutSummary)

/** One finished workout: numbers, all sets, photos and actions (template, share, delete). */
@Composable
fun HistoryDetailScreen(
    workoutId: String,
    workouts: WorkoutRepository,
    templates: TemplateRepository,
    unit: WeightUnit,
    language: Language,
    appScope: CoroutineScope,
    onBack: () -> Unit,
    onOpenPhoto: (String) -> Unit,
    onTemplateCreated: (Long) -> Unit,
    onShare: () -> Unit,
    /** Accepted notes from the AI chat that belong to this workout. */
    chatNotes: List<ChatNote> = emptyList(),
    /** Coach review of this workout (phase 4). */
    onCoach: (() -> Unit)? = null,
) {
    val t = LocalStrings.current
    val h = t.history
    val data by produceState<DetailData?>(null, workoutId) {
        val finished = workouts.getFinished(workoutId)
        val summary = workouts.summary(workoutId)
        if (finished == null || summary == null) onBack() else value = DetailData(finished, summary)
    }
    val photos by remember(workoutId) { workouts.observePhotosOf(workoutId) }.collectAsStateWithLifecycle(emptyList())

    Screen(scroll = true, header = {
        BackHeader(t.back, onBack) {
            if (data != null) {
                HeaderIconButton(Icon.Share, h.shareWorkout, onShare)
                HeaderDeleteButton(t.delete, t.deleteConfirm, onConfirm = {
                    appScope.launch { workouts.deleteWorkout(workoutId) }
                    onBack()
                })
            }
        }
    }) {
        val d = data ?: return@Screen
        val w = d.finished.workout
        Text("${t.date(localDateTime(w.startedAt).date)} · ${clockTime(w.startedAt)}", tone = Tone.Muted)
        Spacer(Modifier.height(Space.s))
        Title(w.name)
        Spacer(Modifier.height(Space.l))
        SummaryRows(d.summary, unit, language, t)
        d.finished.feeling?.let { ListRow(h.feeling, "$it / 5") }
        d.finished.note?.let {
            SectionTitle(h.note)
            Text(it)
        }
        if (chatNotes.isNotEmpty()) {
            SectionTitle(h.chatNotes)
            chatNotes.forEach { n ->
                n.title?.let { Text(it, style = Theme.type.label) }
                Text(n.text)
                if (n.clientName.isNotBlank()) Text(n.clientName, style = Theme.type.caption, tone = Tone.Muted)
                Spacer(Modifier.height(Space.m))
            }
        }

        if (photos.isNotEmpty()) {
            SectionTitle(h.photos)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                photos.forEachIndexed { i, p -> PhotoThumb(p, h.photoDescription(i + 1), onClick = { onOpenPhoto(p.id) }) }
            }
        }

        w.exercises.forEach { e ->
            Spacer(Modifier.height(Space.xl))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(e.exercise.name, Modifier.weight(1f, fill = false), style = Theme.type.label)
                val marker = when (e.origin) {
                    ExerciseOrigin.ADDED -> h.markerAdded
                    ExerciseOrigin.REPLACED -> h.markerReplaced
                    ExerciseOrigin.PLANNED -> null
                }
                if (marker != null) {
                    Spacer(Modifier.width(Space.m))
                    Text(marker, style = Theme.type.caption, tone = Tone.Faint)
                }
            }
            Spacer(Modifier.height(Space.s))
            Hairline()
            e.sets.forEachIndexed { i, s ->
                val v = if (s.completed || s.actual != SetValues()) s.actual else s.target
                val text = WorkoutFormat.values(v, e.exercise.trackingType, unit, language, t).ifEmpty { "–" }
                val tone = if (s.completed) Tone.Default else Tone.Faint
                Row(Modifier.fillMaxWidth().padding(vertical = Space.s), verticalAlignment = Alignment.CenterVertically) {
                    Text("${i + 1}", Modifier.width(32.dp), tone = Tone.Faint)
                    Text(text, Modifier.weight(1f), tone = tone)
                    if (s.completed) LineIcon(Icon.Check, size = 18.dp) else Text(h.notDone, style = Theme.type.caption, tone = Tone.Faint)
                }
            }
            Hairline()
        }

        Spacer(Modifier.height(Space.xl))
        Hairline()
        ListRow(h.useAsTemplate, onClick = {
            appScope.launch {
                val id = templates.save(w.toTemplate())
                withContext(Dispatchers.Main) { onTemplateCreated(id) }
            }
        })
        if (onCoach != null) ListRow(app.overfit.i18n.LocalCoachStrings.current.reviewTitle, onClick = onCoach)
    }
}

/** Exercises in order; sets = logged values of completed sets, planned values for open ones. Notes are not copied. */
private fun Workout.toTemplate() = Template(
    id = 0,
    name = name,
    note = null,
    exercises = exercises.map { e ->
        TemplateExercise(
            exercise = e.exercise,
            restSec = e.restSec,
            note = null,
            sets = e.sets.map { s ->
                val v = if (s.completed) s.actual else s.target
                PlannedSet(v.weightKg, v.reps, v.durationSec)
            },
        )
    },
)
