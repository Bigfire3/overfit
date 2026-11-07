package app.overfit.ui.photos

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.overfit.domain.BodyWeightStats
import app.overfit.domain.Language
import app.overfit.domain.WeightEntry
import app.overfit.domain.WeightUnit
import app.overfit.domain.formatWeight
import app.overfit.domain.formatWeightChange
import app.overfit.i18n.LocalStrings
import app.overfit.ui.components.FloatingCard
import app.overfit.ui.components.Icon
import app.overfit.ui.components.LineIcon
import app.overfit.ui.components.Text
import app.overfit.ui.components.Tone
import app.overfit.ui.theme.Space
import app.overfit.ui.theme.Theme
import kotlinx.datetime.LocalDate

/** Test tag of the body-weight card at the top of the calendar tab. */
const val WEIGHT_CARD_TAG = "weight-card"

/**
 * Quick entry at the top of the calendar tab: latest body weight, change against the week before and a mini chart
 * of the last ~8 weeks. A tap opens today's entry (day view, field focused).
 */
@Composable
fun WeightCard(entries: List<WeightEntry>, today: LocalDate, unit: WeightUnit, language: Language, onClick: () -> Unit) {
    val t = LocalStrings.current
    val w = t.bodyWeight
    // Only up to today (a future date can only come from an edited backup).
    val past = remember(entries, today) { entries.filter { it.date <= today.toString() } }
    val latest = remember(past) { BodyWeightStats.sorted(past).lastOrNull() }
    val change = remember(past) { BodyWeightStats.weekChange(past) }
    val points = remember(past, today) { BodyWeightStats.sparkline(past, today) }
    if (latest == null) {
        FloatingCard(Modifier.testTag(WEIGHT_CARD_TAG).semantics(mergeDescendants = true) {}, onClick = onClick) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = Space.m, vertical = Space.s),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(w.logToday, style = Theme.type.label)
                    Text(w.logTodayHint, style = Theme.type.caption, tone = Tone.Muted)
                }
                LineIcon(Icon.Plus, size = 20.dp)
            }
        }
        return
    }
    val value = formatWeight(latest.kg, unit, language)
    val latestDate = LocalDate.parse(latest.date)
    val detail = when {
        change != null -> w.weekChange(formatWeightChange(change, unit, language))
        else -> w.noWeekChange
    }
    val stamp = if (latestDate == today) w.today else w.asOf(t.date(latestDate))
    FloatingCard(
        Modifier.testTag(WEIGHT_CARD_TAG).clearAndSetSemantics { contentDescription = w.cardDescription(value, "$stamp, $detail") },
        onClick = onClick,
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = Space.m, vertical = Space.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("${w.title} · $stamp", style = Theme.type.caption, tone = Tone.Muted, maxLines = 1)
                Text(value, style = Theme.type.title, maxLines = 1)
                Text(detail, style = Theme.type.caption, tone = Tone.Muted, maxLines = 1)
            }
            if (points.size >= 2) {
                Spacer(Modifier.width(Space.m))
                Sparkline(points, today, Modifier.size(width = 104.dp, height = 40.dp))
            }
        }
    }
}

/**
 * Line of the entries over the last [BodyWeightStats.SPARKLINE_DAYS] days. x = date (gaps stay visible), y = weight
 * scaled to its own range; the newest point gets a dot. Foreground colour only (black and white design).
 */
@Composable
fun Sparkline(points: List<WeightEntry>, today: LocalDate, modifier: Modifier = Modifier) {
    val color = Theme.colors.foreground
    val days = BodyWeightStats.SPARKLINE_DAYS
    Canvas(modifier.testTag("weight-sparkline")) {
        val start = today.toEpochDays() - (days - 1)
        val xs = points.map { (LocalDate.parse(it.date).toEpochDays() - start).toFloat() / (days - 1) }
        val min = points.minOf { it.kg }
        val max = points.maxOf { it.kg }
        val range = (max - min).takeIf { it > 0.0 } ?: 1.0
        val pad = 4.dp.toPx()
        val h = size.height - 2 * pad
        fun y(kg: Double) = if (max == min) size.height / 2 else pad + (h * (1 - (kg - min) / range)).toFloat()
        val path = Path()
        points.forEachIndexed { i, p ->
            val o = Offset(pad + xs[i] * (size.width - 2 * pad), y(p.kg))
            if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y)
        }
        drawPath(path, color, style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        val last = points.last()
        drawCircle(color, 3.dp.toPx(), Offset(pad + xs.last() * (size.width - 2 * pad), y(last.kg)))
    }
}

