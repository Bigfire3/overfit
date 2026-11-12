package app.overfit.ui.history

import app.overfit.ui.photos.DayPhotoGrid
import app.overfit.ui.components.Hint

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import app.overfit.ui.components.pressScale
import androidx.compose.ui.semantics.Role
import app.overfit.ui.components.FloatingCard
import androidx.compose.foundation.layout.heightIn
import app.overfit.ui.components.ExerciseCardHeader
import app.overfit.ui.components.Block
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
import androidx.compose.foundation.background
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
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
import app.overfit.ui.components.Panel
import app.overfit.ui.components.panelRow
import app.overfit.ui.components.RowDivider
import app.overfit.ui.components.PanelItem
import app.overfit.ui.components.PanelSection
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

/**
 * One finished training on its calendar day, already "opened": name, time, the key numbers and one line per exercise
 * (sets done and the best set), so the day usually needs no second tap. A tap opens the full detail (all sets, edit,
 * share, delete). [workout] is null while loading – then only the head is shown.
 */
@Composable
internal fun TrainingRow(row: HistoryRow, unit: WeightUnit, language: Language, onOpen: (String) -> Unit, workout: Workout? = null) {
    val t = LocalStrings.current
    val h = t.history
    val meta = listOfNotNull(
        row.endedAt?.let { WorkoutFormat.elapsed(it - row.startedAt) },
        h.setCount(row.completedSets),
        formatVolume(row.volumeKg, unit, language).takeIf { row.volumeKg > 0 },
        h.sauna.takeIf { row.sauna },
    ).joinToString(" · ")
    // Each training is its own floating card (Fabian: lists of standalone things as cards).
    FloatingCard(Modifier.testTag("day-training-${row.id}"), onClick = { onOpen(row.id) }) {
        Column(Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(horizontal = Space.m, vertical = Space.m)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(row.name, Modifier.weight(1f), style = Theme.type.label, maxLines = 1)
                Spacer(Modifier.width(Space.m))
                Text(clockTime(row.startedAt), style = Theme.type.caption, tone = Tone.Muted)
                Spacer(Modifier.width(Space.s))
                LineIcon(Icon.ChevronRight, size = 20.dp, color = Theme.colors.muted)
            }
            Spacer(Modifier.height(2.dp))
            Text(meta, style = Theme.type.caption, tone = Tone.Muted)
            val exercises = workout?.exercises.orEmpty()
            if (exercises.isNotEmpty()) {
                Spacer(Modifier.height(Space.s))
                exercises.forEach { e ->
                    val done = e.sets.filter { it.completed }
                    // Best set by what the exercise measures: weight first (only if it uses weight), then reps, then time.
                    val type = e.exercise.trackingType
                    val best = done.maxWithOrNull(compareBy<app.overfit.domain.WorkoutSet>(
                        { if (type.usesWeight) it.actual.weightKg ?: 0.0 else 0.0 },
                        { if (type.usesReps) it.actual.reps ?: 0 else 0 },
                        { if (type.usesTime) it.actual.durationSec ?: 0 else 0 },
                    ))
                    val bestText = best?.let { WorkoutFormat.values(it.actual, e.exercise.trackingType, unit, language, t) }.orEmpty()
                    val right = when {
                        done.isEmpty() -> if (e.skipped) t.training.skipped else h.notDone
                        else -> listOf(h.setCount(done.size), bestText, t.training.skipped.takeIf { e.skipped }).filter { !it.isNullOrEmpty() }.joinToString(" · ")
                    }
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(e.exercise.name, Modifier.weight(1f), style = Theme.type.caption, maxLines = 1)
                        Spacer(Modifier.width(Space.s))
                        Text(right, style = Theme.type.caption, tone = Tone.Muted, maxLines = 1)
                    }
                }
            }
        }
    }
}

private data class DetailData(val finished: FinishedWorkout, val summary: WorkoutSummary)

/**
 * One finished training (opened from its calendar day): numbers, all sets, photos and actions
 * (edit, share, delete, save as workout, coach review). Reloads after every change, e.g. an edit.
 */
@Composable
fun WorkoutDetailScreen(
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
    /** Coach icon: opens the coach over this screen with this training attached (null = no icon). */
    onCoach: (() -> Unit)? = null,
    /** Edit sets, feeling, sauna and note afterwards. */
    onEdit: (() -> Unit)? = null,
    /** Tap on an exercise's image: its details (read only), like in the running workout. */
    onExerciseDetails: (String) -> Unit = {},
) {
    val t = LocalStrings.current
    val h = t.history
    val data by produceState<DetailData?>(null, workoutId) {
        // `changes` emits right away and after every write, so an edit shows up when coming back.
        workouts.changes.collect {
            val finished = workouts.getFinished(workoutId)
            val summary = workouts.summary(workoutId)
            if (finished == null || summary == null) {
                if (value == null) onBack()
            } else {
                value = DetailData(finished, summary)
            }
        }
    }
    // Photos belong to the training's day (v14): the detail shows them all and adds new ones to that day.
    val day = data?.finished?.workout?.startedAt?.let { localDateTime(it).date }
    val photos by remember(day) { day?.let { workouts.observePhotosOfDay(it) } ?: kotlinx.coroutines.flow.flowOf(emptyList()) }
        .collectAsStateWithLifecycle(emptyList())

    Screen(scroll = true, header = {
        BackHeader(t.back, onBack) {
            if (data != null) {
                if (onCoach != null) app.overfit.ui.components.CoachIconButton(onCoach)
                if (onEdit != null) HeaderIconButton(Icon.Edit, h.editWorkout, onEdit)
                HeaderIconButton(Icon.Share, h.shareWorkout, onShare)
                HeaderDeleteButton(t.delete, onConfirm = {
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
        // Tapping the name opens the edit mode with the name field (rename this training only).
        val titleInteraction = remember { MutableInteractionSource() }
        Title(
            w.name,
            if (onEdit == null) Modifier else Modifier.pressScale(titleInteraction)
                .clickable(titleInteraction, indication = null, onClickLabel = h.renameWorkout, role = Role.Button, onClick = onEdit),
        )
        Spacer(Modifier.height(Space.l))
        SummaryRows(d.summary, unit, language, t) {
            d.finished.feeling?.let { ListRow(h.feeling, "$it / 5") }
            // Changed afterwards via "Edit" (pencil top right).
            if (d.finished.sauna) ListRow(h.sauna) { LineIcon(Icon.Check, size = 20.dp) }
        }
        d.finished.note?.let {
            PanelSection(h.note) { PanelItem { Text(it) } }
        }
        if (chatNotes.isNotEmpty()) {
            PanelSection(h.chatNotes) {
                chatNotes.forEach { n ->
                    PanelItem {
                        n.title?.let { Text(it, style = Theme.type.label) }
                        Text(n.text)
                        if (n.clientName.isNotBlank()) Text(n.clientName, style = Theme.type.caption, tone = Tone.Muted)
                    }
                }
            }
        }

        if (day != null) {
            SectionTitle(h.dayPhotos)
            DayPhotoGrid(
                photos,
                onAdd = { bytes -> addDayPhoto(workouts, appScope, day, bytes) },
                onOpen = onOpenPhoto,
                onRemove = { id -> appScope.launch { workouts.deletePhoto(id) } },
            )
            Hint(h.dayPhotosHint)
        }

        // Muscles of exactly this training (completed sets), small version of the calendar's body map.
        val loads = remember(w) { app.overfit.domain.MuscleMap.ofWorkout(w) }
        if (loads.isNotEmpty()) {
            SectionTitle(t.muscles.title)
            Block(Modifier.testTag("workout-muscles")) {
                app.overfit.ui.muscles.MuscleMapBody(loads, figureHeight = 240.dp, Modifier.padding(vertical = Space.s))
            }
        }

        // One card per exercise, as in the running workout (image left, name, sets), only without input.
        if (w.exercises.isNotEmpty()) Spacer(Modifier.height(Space.l))
        Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
            w.exercises.forEach { e ->
                val marker = if (e.skipped) t.training.skipped else when (e.origin) {
                    ExerciseOrigin.ADDED -> h.markerAdded
                    ExerciseOrigin.REPLACED -> h.markerReplaced
                    ExerciseOrigin.PLANNED -> null
                }
                Block {
                    ExerciseCardHeader(
                        e.exercise,
                        listOfNotNull(listOfNotNull(t.setsDone(e.sets.count { it.completed }, e.sets.size), marker).joinToString(" · ")),
                        onDetails = { onExerciseDetails(e.exercise.id) },
                    )
                    // A skipped exercise shows only what was done before it was skipped.
                    e.sets.forEachIndexed { i, s ->
                        if (e.skipped && !s.completed) return@forEachIndexed
                        val v = if (s.completed || s.actual != SetValues()) s.actual else s.target
                        val text = WorkoutFormat.values(v, e.exercise.trackingType, unit, language, t).ifEmpty { "–" }
                        val tone = if (s.completed) Tone.Default else Tone.Muted
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 40.dp).padding(horizontal = Space.s),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("${i + 1}", Modifier.width(32.dp), tone = Tone.Muted)
                            Text(text, Modifier.weight(1f), tone = tone)
                            if (s.completed) LineIcon(Icon.Check, size = 18.dp) else Text(h.notDone, style = Theme.type.caption, tone = Tone.Muted)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(Space.xl))
        Panel {
            ListRow(h.useAsTemplate, onClick = {
                appScope.launch {
                    val id = templates.save(w.toTemplate())
                    withContext(Dispatchers.Main) { onTemplateCreated(id) }
                }
            })
        }
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

/** Prepares a picked image and stores it as a photo of [day] (in [appScope]: leaving the screen must not drop it). */
internal fun addDayPhoto(workouts: WorkoutRepository, appScope: CoroutineScope, day: kotlinx.datetime.LocalDate, bytes: ByteArray) {
    appScope.launch {
        val jpeg = withContext(Dispatchers.Default) { app.overfit.platform.prepareJpeg(bytes) } ?: return@launch
        val now = kotlin.time.Clock.System.now().toEpochMilliseconds()
        workouts.addPhoto(day, jpeg, app.overfit.ui.photos.dayPhotoTakenAt(day, localDateTime(now).date, now))
    }
}
