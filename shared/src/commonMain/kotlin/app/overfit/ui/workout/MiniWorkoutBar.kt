package app.overfit.ui.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.structuralEqualityPolicy
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.overfit.domain.Workout
import app.overfit.i18n.LocalStrings
import app.overfit.ui.components.Hairline
import app.overfit.ui.components.Icon
import app.overfit.ui.components.LineIcon
import app.overfit.ui.components.RollingText
import app.overfit.ui.components.PressScale
import app.overfit.ui.components.ProgressLine
import app.overfit.ui.components.Swap
import app.overfit.ui.components.pressScale
import app.overfit.ui.theme.Motion
import app.overfit.ui.components.Text
import app.overfit.ui.components.Tone
import app.overfit.ui.theme.Space
import app.overfit.ui.theme.Theme
import app.overfit.workout.WorkoutFormat
import kotlinx.coroutines.delay

/**
 * Slim bar above the tab bar while a workout is running but minimized: name, current exercise and set,
 * elapsed time – or, during a rest, the countdown in large tabular digits. Tap or swipe up reopens the workout.
 * Pure UI; the workout state is not touched.
 */
@Composable
fun MiniWorkoutBar(workout: Workout, clock: () -> Long, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val t = LocalStrings.current
    // The clock ticks every 250 ms, but the bar only recomposes when something it shows changes: the texts are derived
    // from it (once a second at most), the rest line reads it while drawing.
    val now = produceState(clock()) { while (true) { value = clock(); delay(250) } }
    val open by rememberUpdatedState(onOpen)
    val rest by remember(workout) { derivedStateOf(structuralEqualityPolicy()) { workout.rest?.takeIf { !it.isOver(now.value) } } }
    val next = remember(workout, rest?.afterSetId) { workout.nextOpenSet(rest?.afterSetId) }
    val elapsed by remember(workout) { derivedStateOf { WorkoutFormat.elapsed(now.value - workout.startedAt) } }
    val countdown by remember(workout) { derivedStateOf { WorkoutFormat.countdown(workout.rest?.takeIf { !it.isOver(now.value) }?.remainingMs(now.value) ?: 0) } }
    val label = t.openWorkout + ", " + workout.name
    val interaction = remember { MutableInteractionSource() }

    Column(
        modifier
            .fillMaxWidth()
            // The bar gives way a little under the finger, like a card: the workout rises out of it.
            .pressScale(interaction, PressScale.card)
            .background(Theme.colors.surface)
            .pointerInput(Unit) {
                var dy = 0f
                detectVerticalDragGestures(
                    onDragStart = { dy = 0f },
                    onVerticalDrag = { _, delta -> dy += delta },
                    onDragEnd = { if (dy < -24f) open() },
                )
            }
            .clickable(interaction, indication = null, role = Role.Button) { open() }
            .semantics { contentDescription = label },
    ) {
        // During a rest the top edge is the rest's progress in the "now" colour; otherwise a plain hairline.
        ProgressLine(
            { workout.rest?.takeIf { !it.isOver(now.value) }?.progress(now.value) ?: 0f },
            color = Theme.colors.accent, height = 2.dp, spec = Motion.tick(),
        )
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = Space.m, vertical = Space.s),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.m),
        ) {
            Column(Modifier.weight(1f)) {
                Text(workout.name, style = Theme.type.label, maxLines = 1)
                Text(
                    next?.let { t.miniSet(it.exercise.exercise.name, it.number, it.exercise.sets.size) }
                        ?: t.setsDone(workout.completedSets, workout.totalSets),
                    style = Theme.type.caption,
                    tone = Tone.Muted,
                    maxLines = 1,
                )
            }
            // Rest begins or ends: countdown and elapsed time change places softly.
            Swap(rest != null, contentAlignment = Alignment.CenterEnd) { resting ->
                if (resting) {
                    // Same countdown as in the workout: "now" colour, digits roll down. (Shows 0:00 while it leaves.)
                    RollingText(countdown, style = Theme.type.title, color = Theme.colors.accent, countsDown = true)
                } else {
                    Text(
                        elapsed,
                        style = Theme.type.label.copy(fontFeatureSettings = "tnum"),
                        tone = Tone.Muted,
                        maxLines = 1,
                    )
                }
            }
            LineIcon(Icon.ChevronUp, color = Theme.colors.muted)
        }
    }
}
