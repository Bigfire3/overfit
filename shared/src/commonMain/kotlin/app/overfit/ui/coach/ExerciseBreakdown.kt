package app.overfit.ui.coach

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.overfit.coach.ExerciseComparison
import app.overfit.coach.ExerciseStats
import app.overfit.coach.Verdict
import app.overfit.domain.Language
import app.overfit.domain.WeightUnit
import app.overfit.domain.displayWeight
import app.overfit.domain.formatNumber
import app.overfit.i18n.LocalCoachStrings
import app.overfit.i18n.LocalStrings
import app.overfit.ui.components.Hairline
import app.overfit.ui.components.Text
import app.overfit.ui.components.Tone
import app.overfit.ui.history.formatVolume
import app.overfit.ui.theme.Space
import app.overfit.ui.theme.Theme
import app.overfit.workout.WorkoutFormat
import kotlinx.datetime.LocalDate
import kotlin.math.abs

/** Colour for the meaning of a change: better green, worse red, none muted. */
@Composable
internal fun deltaColor(delta: Double): Color = when {
    delta > 0 -> Theme.colors.positive
    delta < 0 -> Theme.colors.negative
    else -> Theme.colors.muted
}

/** "+5" / "−5" / "0" (a real minus sign). */
internal fun signPrefix(delta: Double): String = when {
    delta > 0 -> "+"
    delta < 0 -> "−"
    else -> ""
}

/** Signed weight in the profile unit: "+5 kg". */
internal fun signedWeight(deltaKg: Double, unit: WeightUnit, language: Language): String =
    signPrefix(deltaKg) + formatNumber(abs(displayWeight(deltaKg, unit)), language) + " " + unit.symbol

/** Coloured text for a change; the colour carries the meaning. */
@Composable
internal fun DeltaText(text: String, delta: Double, modifier: Modifier = Modifier) {
    androidx.compose.foundation.text.BasicText(
        text,
        modifier,
        style = Theme.type.caption.copy(color = deltaColor(delta)),
    )
}

/** The exercises of a reviewed workout with today against last time (local numbers, plus the coach's words if any). */
@Composable
fun ExerciseBreakdown(list: List<ExerciseComparison>, unit: WeightUnit, language: Language) {
    list.forEach { c ->
        ExerciseBlock(c, unit, language)
        Hairline()
    }
}

@Composable
private fun ExerciseBlock(c: ExerciseComparison, unit: WeightUnit, language: Language) {
    val s = LocalCoachStrings.current
    val t = LocalStrings.current
    fun date(iso: String) = runCatching { t.date(LocalDate.parse(iso)) }.getOrDefault(iso)
    fun stats(x: ExerciseStats): String = listOfNotNull(
        x.bestSet?.let { WorkoutFormat.values(it, c.type, unit, language, t) }?.takeIf { it.isNotEmpty() },
        s.setsCount(x.setsDone),
        x.volumeKg.takeIf { it > 0 }?.let { "${s.volumeLabel} ${formatVolume(it, unit, language)}" },
        x.e1rmKg?.let { "e1RM ${formatNumber(displayWeight(it, unit), language)} ${unit.symbol}" },
    ).joinToString(" · ")

    Column(Modifier.fillMaxWidth().padding(vertical = Space.m).testTag("exercise-review-${c.exerciseId}")) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(c.name, Modifier.weight(1f), style = Theme.type.label)
            Spacer(Modifier.width(Space.s))
            VerdictBadge(c)
        }
        Spacer(Modifier.height(Space.xs))
        Text("${s.todayLabel}: ${stats(c.today)}")
        c.last?.let { Text("${s.lastTimeLabel(date(it.date))}: ${stats(it)}", tone = Tone.Muted) }

        val deltas = buildList<Pair<String, Double>> {
            c.e1rmDelta?.let { add(s.deltaE1rm(signedWeight(it, unit, language)) to it) }
            c.volumeDelta?.let { add(s.deltaVolume(signPrefix(it) + formatVolume(abs(it), unit, language)) to it) }
            c.repsDelta?.let { add(s.deltaReps(signPrefix(it.toDouble()) + abs(it)) to it.toDouble()) }
            c.durationDelta?.let { add(s.deltaSeconds(signPrefix(it.toDouble()) + abs(it)) to it.toDouble()) }
        }
        if (deltas.isNotEmpty()) {
            Spacer(Modifier.height(Space.xs))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.m)) {
                deltas.forEach { (text, d) -> DeltaText(text, d) }
            }
        }
        c.ai?.comment?.takeIf { it.isNotBlank() }?.let {
            Spacer(Modifier.height(Space.s))
            Text(it)
        }
        c.ai?.next?.let { n ->
            val v = app.overfit.domain.SetValues(n.weightKg, n.reps, n.durationSec)
            val text = WorkoutFormat.values(v, c.type, unit, language, t)
            if (text.isNotEmpty()) {
                Spacer(Modifier.height(Space.s))
                Text(s.nextTimeLine(text), style = Theme.type.label)
            }
        }
    }
}

@Composable
private fun VerdictBadge(c: ExerciseComparison) {
    val s = LocalCoachStrings.current
    val colors = Theme.colors
    val (label, fg, bg) = when (c.verdict) {
        Verdict.BETTER -> Triple(s.verdictBetter, colors.positive, colors.positiveSubtle)
        Verdict.WORSE -> Triple(s.verdictWorse, colors.negative, colors.negativeSubtle)
        Verdict.SAME -> Triple(s.verdictSame, colors.muted, colors.surface)
        null -> Triple(s.verdictFirst, colors.muted, Color.Transparent)
    }
    val shape = RoundedCornerShape(6.dp)
    val base = Modifier.testTag("verdict-${c.exerciseId}")
    androidx.compose.foundation.text.BasicText(
        label,
        if (c.verdict == null) base else base.background(bg, shape).padding(horizontal = Space.s, vertical = Space.xs),
        style = Theme.type.caption.copy(color = fg),
    )
}
