package app.overfit.ui.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
import app.overfit.ui.components.IconButton
import app.overfit.ui.components.LineIcon
import app.overfit.ui.components.ListRow
import app.overfit.ui.components.PrimaryButton
import app.overfit.ui.components.Screen
import app.overfit.ui.components.SectionTitle
import app.overfit.ui.components.Text
import app.overfit.ui.components.Title
import app.overfit.ui.components.Tone
import app.overfit.ui.components.pressableRow
import app.overfit.ui.share.SamplesRow
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
    onNew: () -> Unit,
    onOpenWorkout: (Long) -> Unit,
    share: ShareDependencies,
    onDuo: () -> Unit,
    coachTips: List<String> = emptyList(),
    onCoach: () -> Unit = {},
    chatInboxCount: Int = 0,
    chatInboxFull: Boolean = false,
    onChatInbox: () -> Unit = {},
) {
    val t = LocalStrings.current
    Screen(
        scroll = true,
        footer = if (running != null) {
            { PrimaryButton(t.continueWorkout, onClick = onOpenRunning) }
        } else {
            null
        },
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { Title(t.navStart) }
            IconButton(Icon.Plus, t.newWorkout, onNew)
        }
        Spacer(Modifier.height(Space.l))

        if (running != null) {
            Text(t.runningNow, style = Theme.type.caption, tone = Tone.Muted)
            ListRow(running.name, t.setsDone(running.completedSets, running.totalSets), onClick = onOpenRunning)
            Spacer(Modifier.height(Space.m))
        }

        if (chatInboxCount > 0) {
            val chatText = app.overfit.i18n.LocalChatStrings.current
            Hairline()
            ListRow(chatText.inboxStart(chatInboxCount), onClick = onChatInbox) { LineIcon(Icon.Plus) }
            if (chatInboxFull) Text(chatText.inboxFull, Modifier.padding(vertical = Space.s), style = Theme.type.caption)
        }

        if (templates != null && templates.isEmpty()) {
            Text(t.templatesEmpty, tone = Tone.Muted)
            Spacer(Modifier.height(Space.s))
            SamplesRow(share)
        }
        if (templates != null && templates.isNotEmpty()) Hairline()
        templates?.forEach { w -> WorkoutRow(w, lastDoneText(t, lastDone[w.id], today)) { onOpenWorkout(w.id) } }

        SectionTitle(t.coachTips)
        if (coachTips.isEmpty()) Text(t.coachTipsEmpty, tone = Tone.Muted)
        coachTips.forEach { Text("– $it", Modifier.padding(vertical = Space.xs)) }
        ListRow(t.coach, onClick = onCoach)
        Spacer(Modifier.height(Space.xl))
        Hairline()
        ListRow(t.share.duoSoon, onClick = onDuo, tone = Tone.Muted)
    }
}

/** One saved workout: name, exercises in short, "last done 3 days ago". */
@Composable
fun WorkoutRow(workout: TemplateSummary, lastDone: String, onClick: () -> Unit) {
    val t = LocalStrings.current
    Column(Modifier.fillMaxWidth().pressableRow(onClick)) {
        Column(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(vertical = Space.m)) {
            Text(workout.name, style = Theme.type.label)
            Spacer(Modifier.height(2.dp))
            Text(t.templateSummary(workout.exerciseNames.size, workout.exerciseNames), style = Theme.type.caption, tone = Tone.Muted, maxLines = 2)
            Text(lastDone, style = Theme.type.caption, tone = Tone.Muted)
        }
        Hairline()
    }
}

/** "last done 3 days ago" / "not done yet" from the start of the last session (epoch ms). */
fun lastDoneText(t: Strings, lastStartedAt: Long?, today: LocalDate): String {
    if (lastStartedAt == null) return t.neverDone
    val date = Instant.fromEpochMilliseconds(lastStartedAt).toLocalDateTime(TimeZone.currentSystemDefault()).date
    return t.lastDone(date.daysUntil(today).coerceAtLeast(0))
}
