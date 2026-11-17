package app.overfit.ui.history

import androidx.compose.ui.text.style.TextAlign
import app.overfit.ui.photos.DayPhotoGrid

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
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
import app.overfit.ui.components.PopIn
import app.overfit.ui.components.Reveal
import app.overfit.ui.components.RevealNotNull
import app.overfit.ui.components.Stat
import app.overfit.ui.components.StatGrid
import app.overfit.ui.components.rememberCountUp
import app.overfit.ui.components.rememberHaptics
import app.overfit.ui.theme.Motion
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import app.overfit.ui.components.CheckRow
import app.overfit.ui.components.BigInput
import app.overfit.domain.Workout
import app.overfit.ui.components.ListRow
import app.overfit.ui.components.Panel
import app.overfit.ui.components.PanelItem
import app.overfit.ui.components.PanelSection
import app.overfit.ui.components.Hint
import app.overfit.ui.components.MultilineInput
import app.overfit.ui.components.PrimaryButton
import app.overfit.ui.components.Screen
import app.overfit.ui.components.SectionTitle
import app.overfit.ui.components.Text
import app.overfit.ui.components.SmallButton
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
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun FinishScreen(
    workoutId: String,
    workouts: WorkoutRepository,
    awaitFinished: suspend () -> Unit,
    unit: WeightUnit,
    language: Language,
    appScope: CoroutineScope,
    /** Coach card; its callback saves like "Save" and then calls [onReview] instead of [onClose]. */
    coachSection: @Composable (workoutId: String, onReview: () -> Unit) -> Unit,
    onSaved: (String) -> Unit,
    onClose: () -> Unit,
    /** "Get this training reviewed": after saving, open the coach chat with this training attached (nothing is sent). */
    onReview: (String) -> Unit = { onClose() },
    /** Renames the finished workout and its saved workout (runs in [appScope]). */
    rename: suspend (workout: Workout, name: String) -> Unit = { _, _ -> },
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
    // Photos belong to the training's day (v14): shown and added for that day.
    val day = data?.finished?.workout?.startedAt?.let { localDateTime(it).date }
    val photos by remember(day) { day?.let { workouts.observePhotosOfDay(it) } ?: kotlinx.coroutines.flow.flowOf(emptyList()) }
        .collectAsStateWithLifecycle(emptyList())

    var feeling by rememberSaveable { mutableStateOf<Int?>(null) }
    var sauna by rememberSaveable { mutableStateOf<Boolean?>(null) }
    var note by rememberSaveable { mutableStateOf("") }
    // The name is set here (an empty start got a default name like "Workout vom 3. Nov.").
    var name by rememberSaveable { mutableStateOf<String?>(null) }
    var seeded by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(data) {
        val d = data ?: return@LaunchedEffect
        if (!seeded) {
            // Only fill what the user has not touched yet: the screen is usable before the data has loaded.
            seeded = true
            if (feeling == null) feeling = d.finished.feeling
            if (sauna == null) sauna = d.finished.sauna
            if (note.isEmpty()) note = d.finished.note.orEmpty()
            if (name == null) name = d.finished.workout.name
        }
    }
    var saving by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    // The summary celebrates once (count-up, record pop, one confirm haptic); after rotation or a return it just stands.
    var celebrated by rememberSaveable { mutableStateOf(false) }
    val celebrate = remember { !celebrated }
    val haptics = rememberHaptics()
    LaunchedEffect(data != null) {
        if (data != null && !celebrated) {
            celebrated = true
            haptics.confirm()
        }
    }
    LaunchedEffect(failed) { if (failed) haptics.reject() }

    fun save(review: Boolean) {
        if (!saving) {
            saving = true
            failed = false
            val noteText = note
            val feelingValue = feeling
            val saunaValue = sauna
            val newName = name?.trim().orEmpty()
            val finishedWorkout = data?.finished?.workout
            appScope.launch {
                // A failed write keeps the screen open with a message (and the button usable again) instead of
                // leaving it stuck on "…" or closing as if everything was saved.
                val ok = runCatching {
                    if (data != null) {
                        workouts.updateDetails(workoutId, noteText, feelingValue)
                        // Saved before onSaved, so the coach review that starts there already sees it.
                        if (saunaValue != null && saunaValue != data?.finished?.sauna) workouts.setSauna(workoutId, saunaValue)
                    }
                    if (finishedWorkout != null && newName.isNotEmpty() && newName != finishedWorkout.name) {
                        rename(finishedWorkout, newName)
                    }
                }.isSuccess
                withContext(Dispatchers.Main) {
                    if (!ok) { saving = false; failed = true; return@withContext }
                    onSaved(workoutId)
                    if (review) onReview(workoutId) else onClose()
                }
            }
        }
    }
    val save = { save(review = false) }

    // System back (Android gesture/button) saves note and feeling like the header back button does.
    // After a failed save, back leaves anyway (the training itself is already stored; only note/feeling are lost).
    val back = { if (failed) onClose() else save() }
    androidx.compose.ui.backhandler.BackHandler(enabled = true, onBack = back)

    Screen(
        scroll = true,
        header = { BackHeader(t.back, back) },
        footer = {
            Reveal(failed) {
                Text(h.editSaveFailed, Modifier.fillMaxWidth(), style = Theme.type.caption, tone = Tone.Muted, align = TextAlign.Center)
            }
            PrimaryButton(t.save, save, enabled = data != null, loading = saving)
        },
    ) {
        val d = data
        Text(h.finishTitle, tone = Tone.Muted)
        Spacer(Modifier.height(Space.s))
        // Always the input (empty until the name has loaded): no swap from a title to a field of another height.
        BigInput(name ?: d?.finished?.workout?.name.orEmpty(), { name = it.take(60) }, t.workoutNamePlaceholder, Modifier.fillMaxWidth())
        Spacer(Modifier.height(Space.l))
        // The tiles stand from the start ("–" while loading), so nothing below jumps when the data arrives.
        FinishStats(d?.summary, unit, language, t, celebrate)
        RevealNotNull(d?.summary?.records?.takeIf { it.isNotEmpty() }) { records ->
            Column {
                PanelSection(h.newRecords) {
                    records.forEach { r -> RecordRow(r, unit, language, t, celebrate) }
                }
            }
        }

        PanelSection(h.feeling) {
            PanelItem { FeelingPicker(feeling, h.feelingValue, showMeaning = true) { feeling = it } }
            CheckRow(h.sauna, sauna == true) { sauna = it }
        }

        SectionTitle(h.note)
        MultilineInput(note, { note = it.take(2000) }, h.notePlaceholder)

        SectionTitle(h.dayPhotos)
        RevealNotNull(day) { photoDay ->
            DayPhotoGrid(
                photos,
                onAdd = { bytes -> addDayPhoto(workouts, appScope, photoDay, bytes) },
                onOpen = null,
                onRemove = { id -> appScope.launch { workouts.deletePhoto(id) } },
            )
        }
        Hint(h.photosPrivate)

        Spacer(Modifier.height(Space.l))
        coachSection(workoutId) { save(review = true) }
    }
}

@Composable
internal fun SummaryRows(
    s: WorkoutSummary,
    unit: WeightUnit,
    language: Language,
    t: Strings,
    extra: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit = {},
) {
    val h = t.history
    Panel {
        ListRow(h.duration, WorkoutFormat.elapsed(s.durationMs))
        ListRow(h.setsLabel, h.setsOf(s.completedSets, s.totalSets))
        if (s.volumeKg > 0) ListRow(h.volume, formatVolume(s.volumeKg, unit, language))
        ListRow(h.exercisesLabel, s.exerciseCount.toString())
        extra()
    }
    SummaryDeviations(s, t)
}

/** "1 Übung ausgelassen · 2 Sätze übersprungen": what differed from the plan, as a hint under the summary. */
@Composable
internal fun SummaryDeviations(s: WorkoutSummary, t: Strings) {
    if (s.skippedSets > 0 || s.addedExercises > 0 || s.replacedExercises > 0 || s.skippedExercises > 0) {
        val skipped = s.skippedExercises.takeIf { it > 0 }?.let(t.training.skippedExercises)
        val other = t.history.deviations(s.skippedSets, s.addedExercises, s.replacedExercises).ifEmpty { null }
        Hint(listOfNotNull(skipped, other).joinToString(" · "))
    }
}

/**
 * Finish summary as tiles (half the height of four rows). While [s] is loading the tiles show "–"; once it is there
 * the numbers count up once ([celebrate]), a new record shows as a green tile.
 */
@Composable
private fun FinishStats(s: WorkoutSummary?, unit: WeightUnit, language: Language, t: Strings, celebrate: Boolean) {
    val h = t.history
    val modifier = Modifier.animateContentSize(Motion.smooth())
    if (s == null) {
        StatGrid(listOf(h.duration, h.setsLabel, h.volume, h.exercisesLabel).map { Stat(it, "–") }, modifier)
        return
    }
    @Composable
    fun count(target: Double): Double = if (celebrate) rememberCountUp(target.toFloat()).value.toDouble() else target
    val duration = count(s.durationMs.toDouble())
    val sets = count(s.completedSets.toDouble())
    val volume = count(s.volumeKg)
    val exercises = count(s.exerciseCount.toDouble())
    // Ends exactly on the real values (the float count-up may stop a hair below).
    fun int(v: Double, real: Int) = if (v >= real) real else v.roundToInt().coerceAtMost(real)
    StatGrid(
        listOfNotNull(
            Stat(h.duration, WorkoutFormat.elapsed(if (duration >= s.durationMs) s.durationMs else duration.roundToLong())),
            Stat(h.setsLabel, h.setsOf(int(sets, s.completedSets), s.totalSets)),
            if (s.volumeKg > 0) Stat(h.volume, formatVolume(if (volume >= s.volumeKg) s.volumeKg else volume, unit, language)) else null,
            Stat(h.exercisesLabel, int(exercises, s.exerciseCount).toString()),
            if (s.records.isNotEmpty()) Stat(h.newRecords, s.records.size.toString(), positive = true) else null,
        ),
        modifier,
    )
    SummaryDeviations(s, t)
}

@Composable
private fun RecordRow(r: PersonalRecord, unit: WeightUnit, language: Language, t: Strings, celebrate: Boolean = false) {
    val delta = r.value - r.previous
    ListRow(recordText(r, unit, language, t)) {
        if (delta > 0) {
            val text = when (r.kind) {
                RecordKind.WEIGHT, RecordKind.E1RM -> "+" + formatKg(delta, unit, language)
                RecordKind.REPS -> "+${delta.toInt()} ${t.repsUnit}"
                RecordKind.DURATION -> "+${delta.toInt()} s"
            }
            // The green delta pops in once the numbers above have counted up (all rows together, no stagger).
            var shown by remember { mutableStateOf(!celebrate) }
            LaunchedEffect(Unit) {
                if (!shown) {
                    kotlinx.coroutines.delay(Motion.COUNT_UP_MS.toLong())
                    shown = true
                }
            }
            PopIn(shown) {
                androidx.compose.foundation.text.BasicText(text, style = Theme.type.caption.copy(color = Theme.colors.positive))
            }
        }
    }
}

internal fun recordText(r: PersonalRecord, unit: WeightUnit, language: Language, t: Strings): String {
    val h = t.history
    fun kg(v: Double) = formatKg(v, unit, language)
    return when (r.kind) {
        RecordKind.WEIGHT -> h.recordLine(r.exerciseName, "", kg(r.value), kg(r.previous))
        RecordKind.E1RM -> h.recordLine(r.exerciseName, h.estimatedOneRm, kg(r.value), kg(r.previous))
        RecordKind.REPS -> h.recordLine(r.exerciseName, "", "${r.value.toInt()} ${t.repsUnit}", "${r.previous.toInt()} ${t.repsUnit}")
        RecordKind.DURATION -> h.recordLine(r.exerciseName, "", Numbers.formatDuration(r.value.toInt()), Numbers.formatDuration(r.previous.toInt()))
    }
}
