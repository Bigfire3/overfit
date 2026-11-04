package app.overfit.ui.templates

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
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
import app.overfit.ui.components.SecondaryButton
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
    Screen(
        scroll = true,
        header = {
            BackHeader(t.back, onBack) {
                if (w != null) {
                    HeaderIconButton(Icon.Share, t.share.shareTemplate, onShare)
                    HeaderDeleteButton(t.deleteTemplate, onDelete)
                }
            }
        },
        // No footer until the workout is loaded: a visible but still disabled "Start" swallows early taps.
        // Bottom: big "Start", below it an equally big "Edit".
        footer = if (w == null) null else ({
            if (workoutRunning) {
                RunningWorkoutChoice(onToRunning, onEndRunning)
            } else {
                PrimaryButton(t.startThisWorkout, { onStart(w) })
            }
            SecondaryButton(t.edit, onEdit)
        }),
    ) {
        if (w == null) return@Screen
        Title(w.name)
        Spacer(Modifier.height(Space.s))
        Text(lastDone, style = Theme.type.caption, tone = Tone.Muted)
        Spacer(Modifier.height(Space.l))
        if (w.exercises.isEmpty()) Text(t.workoutPlanEmpty, Modifier.padding(vertical = Space.m), tone = Tone.Muted)
        else Panel { w.exercises.forEach { PlanExerciseRow(it, unit, language) } }
    }
}

@Composable
private fun PlanExerciseRow(e: TemplateExercise, unit: WeightUnit, language: Language) {
    val t = LocalStrings.current
    val values = e.sets.map { WorkoutFormat.values(it, e.exercise.trackingType, unit, language, t) }.filter { it.isNotEmpty() }.distinct()
    Column(Modifier.fillMaxWidth().panelRow().heightIn(min = 56.dp).padding(vertical = Space.m)) {
        Text(e.exercise.name, style = Theme.type.label)
        Text(listOf(t.setCount(e.sets.size)).plus(values).joinToString(" · "), style = Theme.type.caption, tone = Tone.Muted)
    }
    RowDivider()
}
