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
import androidx.compose.runtime.produceState
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
    val now by produceState(clock()) { while (true) { value = clock(); delay(250) } }
    val open by rememberUpdatedState(onOpen)
    val rest = workout.rest?.takeIf { !it.isOver(now) }
    val next = workout.nextOpenSet(rest?.afterSetId)
    val label = t.openWorkout + ", " + workout.name

    Column(
        modifier
            .fillMaxWidth()
            .background(Theme.colors.surface)
            .pointerInput(Unit) {
                var dy = 0f
                detectVerticalDragGestures(
                    onDragStart = { dy = 0f },
                    onVerticalDrag = { _, delta -> dy += delta },
                    onDragEnd = { if (dy < -24f) open() },
                )
            }
            .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button) { open() }
            .semantics { contentDescription = label },
    ) {
        Hairline()
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
            if (rest != null) {
                Text(
                    WorkoutFormat.countdown(rest.remainingMs(now)),
                    style = Theme.type.title.copy(fontFeatureSettings = "tnum"),
                    maxLines = 1,
                )
            } else {
                Text(
                    WorkoutFormat.elapsed(now - workout.startedAt),
                    style = Theme.type.label.copy(fontFeatureSettings = "tnum"),
                    tone = Tone.Muted,
                    maxLines = 1,
                )
            }
            LineIcon(Icon.ChevronUp, color = Theme.colors.muted)
        }
    }
}
