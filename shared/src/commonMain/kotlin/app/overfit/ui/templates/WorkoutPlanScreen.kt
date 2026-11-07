package app.overfit.ui.templates

import app.overfit.ui.components.ExerciseCardHeader
import app.overfit.ui.components.Block
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import app.overfit.data.LastPerformance
import app.overfit.ui.components.LineIcon
import app.overfit.ui.components.PressScale
import app.overfit.ui.components.pressScale
import app.overfit.ui.history.localDateTime
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.overfit.data.TemplateRepository
import app.overfit.domain.Language
import app.overfit.domain.Template
import app.overfit.domain.TemplateExercise
import app.overfit.domain.WeightUnit
import app.overfit.i18n.LocalStrings
import app.overfit.ui.components.BackHeader
import app.overfit.ui.components.Hairline
import app.overfit.ui.components.Panel
import app.overfit.ui.components.panelRow
import app.overfit.ui.components.RowDivider
import app.overfit.ui.components.HeaderDeleteButton
import app.overfit.ui.components.HeaderIconButton
import app.overfit.ui.components.Icon
import app.overfit.ui.components.PrimaryButton
import app.overfit.ui.components.Screen
import app.overfit.ui.components.Text
import app.overfit.ui.components.Title
import app.overfit.ui.components.Tone
import app.overfit.ui.main.RunningWorkoutChoice
import app.overfit.ui.theme.Space
import app.overfit.ui.theme.Theme
import app.overfit.workout.WorkoutFormat

/**
 * A saved workout: exercises with sets and targets; "Start" and below it "Edit" at the bottom,
 * share and delete as icons top right.
 * Follows the stored workout, so it shows changes made in the editor or by a workout right away.
 */
@Composable
fun WorkoutPlanScreen(
    templateId: Long,
    templates: TemplateRepository,
    lastDone: String,
    unit: WeightUnit,
    language: Language,
    /** Another workout is running: "Start" becomes "Resume workout" (one workout at a time). */
    workoutRunning: Boolean,
    onBack: () -> Unit,
    onStart: (Template) -> Unit,
    onToRunning: () -> Unit,
    onEndRunning: () -> Unit,
    onEdit: () -> Unit,
    onShare: () -> Unit,
    /** Deletes the saved workout (in the app scope); the screen closes itself once it is gone. */
    onDelete: () -> Unit,
    /** Tap on an exercise's image: its details (read only), like in the running workout. */
    onExerciseDetails: (String) -> Unit = {},
    /** "Last time" of an exercise (preferably in this workout); null = never done. Shown when its card is opened. */
    loadLast: suspend (exerciseId: String) -> LastPerformance? = { null },
) {
    val t = LocalStrings.current
    var template by remember(templateId) { mutableStateOf<Template?>(null) }
    LaunchedEffect(templateId) {
        // The list flow emits more than once after a delete (names and workouts are separate queries). Closing once only:
        // a second pop would take the training tab down with it and leave a black screen.
        var closed = false
        templates.templates.collect {
            if (closed) return@collect
            val loaded = templates.get(templateId)
            if (loaded == null) { closed = true; onBack() } else template = loaded
        }
    }
    val w = template
    // Loaded once per set of exercises (not on every edit of the values), so opening a card shows them right away.
    val exerciseIds = w?.exercises?.map { it.exercise.id }?.distinct()
    var last by remember(templateId) { mutableStateOf<Map<String, LastPerformance?>?>(null) }
    LaunchedEffect(exerciseIds) {
        if (exerciseIds != null) last = exerciseIds.associateWith { runCatching { loadLast(it) }.getOrNull() }
    }
    Screen(
        scroll = true,
        header = {
            BackHeader(t.back, onBack) {
                if (w != null) {
                    HeaderIconButton(Icon.Edit, t.edit, onEdit)
                    HeaderIconButton(Icon.Share, t.share.shareTemplate, onShare)
                    HeaderDeleteButton(t.deleteTemplate, onDelete)
                }
            }
        },
        // No footer until the workout is loaded: a visible but still disabled "Start" swallows early taps.
        // Bottom: only the main action; edit is the pencil top right.
        footer = if (w == null) null else ({
            if (workoutRunning) {
                RunningWorkoutChoice(onToRunning, onEndRunning)
            } else {
                PrimaryButton(t.startThisWorkout, { onStart(w) })
            }
        }),
    ) {
        if (w == null) return@Screen
        Title(w.name)
        Spacer(Modifier.height(Space.s))
        Text(lastDone, style = Theme.type.caption, tone = Tone.Muted)
        Spacer(Modifier.height(Space.l))
        if (w.exercises.isEmpty()) Text(t.workoutPlanEmpty, Modifier.padding(vertical = Space.m), tone = Tone.Muted)
        // One card per exercise, as in the running workout (image left, name, sets), only without input.
        else Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
            w.exercises.forEachIndexed { i, e ->
                key(i, e.exercise.id) {
                    PlanExerciseCard(e, unit, language, last?.let { Loaded(it[e.exercise.id]) }) { onExerciseDetails(e.exercise.id) }
                }
            }
        }
    }
}

/** Wraps the loaded "last time" so that "loaded, never done" (null inside) differs from "still loading". */
private class Loaded(val value: LastPerformance?)

/**
 * One exercise of the plan: image, name and targets. A tap opens the card and shows the sets from last time
 * (date, every set as entered), a second tap closes it; the image still opens the exercise details.
 */
@Composable
private fun PlanExerciseCard(e: TemplateExercise, unit: WeightUnit, language: Language, last: Loaded?, onDetails: () -> Unit) {
    val t = LocalStrings.current
    val type = e.exercise.trackingType
    val values = e.sets.map { WorkoutFormat.values(it, type, unit, language, t) }.filter { it.isNotEmpty() }.distinct()
    var open by rememberSaveable { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    Block(
        Modifier.pressScale(interaction, PressScale.card)
            .clickable(interaction, indication = null, role = Role.Button, onClickLabel = t.showLastTime(e.exercise.name)) { open = !open },
    ) {
        ExerciseCardHeader(
            e.exercise,
            listOf(listOf(t.setCount(e.sets.size)).plus(values).joinToString(" · ")),
            onDetails,
            trailing = { LineIcon(if (open) Icon.ChevronUp else Icon.ChevronDown, size = 20.dp, color = Theme.colors.muted) },
        )
        if (open && last != null) {
            val p = last.value
            val sets = p?.sets?.map { WorkoutFormat.values(it, type, unit, language, t) }?.filter { it.isNotEmpty() }.orEmpty()
            Column(Modifier.fillMaxWidth().padding(start = 48.dp + Space.m, top = Space.xs, bottom = Space.xs)) {
                if (p == null || sets.isEmpty()) {
                    Text(t.notTrainedYet, style = Theme.type.caption, tone = Tone.Muted)
                } else {
                    Text(t.lastTimeOn(t.date(localDateTime(p.startedAt).date), p.inThisWorkout), style = Theme.type.caption, tone = Tone.Muted)
                    Spacer(Modifier.height(Space.xs))
                    sets.forEachIndexed { i, v ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(t.setNumber(i + 1), Modifier.width(72.dp), style = Theme.type.caption, tone = Tone.Muted)
                            Text(v, style = Theme.type.label)
                        }
                    }
                }
            }
        }
    }
}
