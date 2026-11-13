package app.overfit.ui.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import app.overfit.ui.components.pressScale
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.platform.testTag
import app.overfit.ui.components.FloatingCard
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.style.TextAlign
import app.overfit.ui.components.ConfirmButton
import app.overfit.ui.components.ConfirmKind
import app.overfit.ui.components.SmallButton
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.overfit.domain.TemplateSummary
import app.overfit.domain.Workout
import app.overfit.i18n.LocalStrings
import app.overfit.i18n.Strings
import app.overfit.ui.components.Hairline
import app.overfit.ui.components.Icon
import app.overfit.ui.components.LineIcon
import app.overfit.ui.components.ListRow
import app.overfit.ui.components.Panel
import app.overfit.ui.components.Hint
import app.overfit.ui.components.panelRow
import app.overfit.ui.components.RowDivider
import app.overfit.ui.components.PrimaryButton
import app.overfit.ui.components.Screen
import app.overfit.ui.components.SectionTitle
import app.overfit.ui.components.Text
import app.overfit.ui.components.Title
import app.overfit.ui.components.Tone
import app.overfit.ui.components.pressableRow
import app.overfit.ui.share.ShareDependencies
import app.overfit.ui.theme.Space
import app.overfit.ui.theme.Theme
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * First tab "Training": the saved workouts (internally templates). "+" offers create / start
 * empty; a tap opens the workout with "Start". A running workout sits on top.
 */
@Composable
fun StartScreen(
    templates: List<TemplateSummary>?,
    lastDone: Map<Long, Long>,
    today: LocalDate,
    running: Workout?,
    onOpenRunning: () -> Unit,
    onEndRunning: () -> Unit,
    onNew: () -> Unit,
    onExercises: () -> Unit,
    onOpenWorkout: (Long) -> Unit,
    share: ShareDependencies,
    /** Play button on a card: starts that workout right away (one tap); hidden while a workout runs. */
    onStartWorkout: ((Long) -> Unit)? = null,
    chatInboxCount: Int = 0,
    chatInboxFull: Boolean = false,
    onChatInbox: () -> Unit = {},
) {
    val t = LocalStrings.current
    Screen(
        scroll = true,
        footer = when {
            running != null -> { { RunningWorkoutChoice(onOpenRunning, onEndRunning) } }
            // Empty state leads to the next step: the same chooser as "+" (create a workout or start empty).
            templates != null && templates.isEmpty() -> { { PrimaryButton(t.newWorkout, onNew, icon = Icon.Plus) } }
            else -> null
        },
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { Title(t.navStart) }
            // The exercise catalogue is not a tab: it sits here (and in every exercise picker).
            app.overfit.ui.components.HeaderActions {
                app.overfit.ui.components.HeaderIconButton(Icon.Exercises, t.navExercises, onExercises)
                app.overfit.ui.components.HeaderIconButton(Icon.Plus, t.newWorkout, onNew)
            }
        }
        Spacer(Modifier.height(Space.l))

        if (running != null) {
            Text(t.runningNow, Modifier.padding(bottom = Space.s), style = Theme.type.caption, tone = Tone.Muted)
            Panel { ListRow(running.name, t.setsDone(running.completedSets, running.totalSets), onClick = onOpenRunning) }
            Spacer(Modifier.height(Space.l))
        }

        if (chatInboxCount > 0) {
            val chatText = app.overfit.i18n.LocalChatStrings.current
            Panel { ListRow(chatText.inboxStart(chatInboxCount), onClick = onChatInbox) { LineIcon(Icon.Plus) } }
            if (chatInboxFull) Hint(chatText.inboxFull)
            Spacer(Modifier.height(Space.l))
        }

        if (templates != null && templates.isEmpty()) {
            Text(t.templatesEmpty, tone = Tone.Muted)
        }
        if (templates != null && templates.isNotEmpty()) {
            Column(Modifier.fillMaxWidth().padding(bottom = Space.l), verticalArrangement = Arrangement.spacedBy(Space.m)) {
                val start = onStartWorkout.takeIf { running == null }
                templates.forEach { w ->
                    WorkoutRow(w, lastDoneText(t, lastDone[w.id], today), onStart = start?.let { { it(w.id) } }) { onOpenWorkout(w.id) }
                }
            }
        }
    }
}

/** One saved workout: name, exercises in short, "last done 3 days ago". */
@Composable
fun WorkoutRow(workout: TemplateSummary, lastDone: String, onStart: (() -> Unit)? = null, onClick: () -> Unit) {
    val t = LocalStrings.current
    FloatingCard(Modifier.testTag("workout-card"), onClick = onClick) {
        Row(Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(horizontal = Space.m, vertical = Space.m), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(workout.name, style = Theme.type.label)
                Spacer(Modifier.height(2.dp))
                Text(t.templateSummary(workout.exerciseNames.size, workout.exerciseNames), style = Theme.type.caption, tone = Tone.Muted, maxLines = 2)
                Text(lastDone, style = Theme.type.caption, tone = Tone.Muted)
            }
            Spacer(Modifier.width(Space.m))
            if (onStart != null && workout.exerciseNames.isNotEmpty()) StartCircle(t.training.startNamed(workout.name), onStart)
            else LineIcon(Icon.ChevronRight, size = app.overfit.ui.theme.IconSize.inline, color = Theme.colors.muted)
        }
    }
}

/**
 * White round start button on a workout card (the main action, like [PrimaryButton]): the most frequent task in
 * one tap. It takes its own tap, the rest of the card opens the workout.
 */
@Composable
private fun StartCircle(description: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        // 48 dp to tap, the same 40 dp white circle as every round button (UI audit 12.11.).
        Modifier.size(app.overfit.ui.theme.TouchTarget.min).pressScale(interaction)
            .clickable(interaction, indication = null, role = Role.Button, onClickLabel = description) { onClick() }
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(app.overfit.ui.theme.Sizes.roundButton).clip(CircleShape).background(Theme.colors.inverseBackground),
            contentAlignment = Alignment.Center,
        ) { LineIcon(Icon.Play, size = app.overfit.ui.theme.IconSize.inline, color = Theme.colors.inverseForeground) }
    }
}

/** "last done 3 days ago" / "not done yet" from the start of the last session (epoch ms). */
fun lastDoneText(t: Strings, lastStartedAt: Long?, today: LocalDate): String {
    if (lastStartedAt == null) return t.neverDone
    val date = Instant.fromEpochMilliseconds(lastStartedAt).toLocalDateTime(TimeZone.currentSystemDefault()).date
    return t.lastDone(date.daysUntil(today).coerceAtLeast(0))
}

/**
 * A workout is running: only "go to it" or "end it" (end = finish and save, confirm pattern),
 * never a second one in parallel. Used on the Training tab, "+" and the workout page.
 */
@Composable
fun ColumnScope.RunningWorkoutChoice(onOpenRunning: () -> Unit, onEndRunning: () -> Unit) {
    val t = LocalStrings.current
    Text(t.runningWorkoutTitle, Modifier.fillMaxWidth(), style = Theme.type.caption, tone = Tone.Muted, align = TextAlign.Center)
    PrimaryButton(t.toRunningWorkout, onClick = onOpenRunning)
    ConfirmButton(t.endRunningWorkout, onConfirm = onEndRunning, modifier = Modifier.align(Alignment.CenterHorizontally), kind = ConfirmKind.Complete)
}
