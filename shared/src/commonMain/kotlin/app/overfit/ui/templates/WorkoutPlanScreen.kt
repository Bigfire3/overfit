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
import app.overfit.ui.components.PrimaryButton
import app.overfit.ui.components.Screen
import app.overfit.ui.components.Text
import app.overfit.ui.components.TextButton
import app.overfit.ui.components.Title
import app.overfit.ui.components.Tone
import app.overfit.ui.theme.Space
import app.overfit.ui.theme.Theme
import app.overfit.workout.WorkoutFormat

/**
 * A saved workout: exercises with sets and targets, "Start" as the primary action, edit and share.
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
    onEdit: () -> Unit,
    onShare: () -> Unit,
) {
    val t = LocalStrings.current
    var template by remember(templateId) { mutableStateOf<Template?>(null) }
    LaunchedEffect(templateId) {
        templates.templates.collect {
            val loaded = templates.get(templateId)
            if (loaded == null) onBack() else template = loaded
        }
    }
    val w = template
    Screen(
        scroll = true,
        header = { BackHeader(t.back, onBack) },
        footer = {
            if (workoutRunning) {
                Text(t.otherWorkoutRunning, style = Theme.type.caption, tone = Tone.Muted)
                PrimaryButton(t.continueWorkout, onToRunning)
            } else {
                PrimaryButton(t.startThisWorkout, { w?.let(onStart) }, enabled = w != null)
            }
        },
    ) {
        if (w == null) return@Screen
        Title(w.name)
        Spacer(Modifier.height(Space.s))
        Text(lastDone, style = Theme.type.caption, tone = Tone.Muted)
        Row(Modifier.padding(top = Space.m), horizontalArrangement = Arrangement.spacedBy(Space.l)) {
            TextButton(t.edit, onEdit, tone = Tone.Default)
            TextButton(t.share.shareTemplate, onShare, tone = Tone.Default)
        }
        Spacer(Modifier.height(Space.l))
        Hairline()
        if (w.exercises.isEmpty()) Text(t.workoutPlanEmpty, Modifier.padding(vertical = Space.m), tone = Tone.Muted)
        w.exercises.forEach { PlanExerciseRow(it, unit, language) }
    }
}

@Composable
private fun PlanExerciseRow(e: TemplateExercise, unit: WeightUnit, language: Language) {
    val t = LocalStrings.current
    val values = e.sets.map { WorkoutFormat.values(it, e.exercise.trackingType, unit, language, t) }.filter { it.isNotEmpty() }.distinct()
    Column(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(vertical = Space.m)) {
        Text(e.exercise.name, style = Theme.type.label)
        Text(listOf(t.setCount(e.sets.size)).plus(values).joinToString(" · "), style = Theme.type.caption, tone = Tone.Muted)
    }
    Hairline()
}
