package app.overfit.domain

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus

/**
 * Body-weight maths for the calendar card and the coach context. Pure, common tests. Entries are [WeightEntry]s
 * (ISO date, kg), at most one per day; order does not matter.
 */
object BodyWeightStats {
    /** Days of the mini chart on the calendar card (about 8 weeks). */
    const val SPARKLINE_DAYS = 56

    /** Window of the coach trend: entries up to 8 weeks before the latest one. */
    const val TREND_DAYS = 56

    /** A trend needs entries at least this many days apart. */
    const val TREND_MIN_SPAN_DAYS = 7

    fun sorted(entries: List<WeightEntry>): List<WeightEntry> = entries.sortedBy { it.date }

    /**
     * Change of the latest entry against the week before: the newest entry that is 7 to 14 days older than the
     * latest. null without such an entry (no comparison against a value months ago, that would not be "last week").
     */
    fun weekChange(entries: List<WeightEntry>): Double? {
        val list = sorted(entries)
        val latest = list.lastOrNull() ?: return null
        val last = LocalDate.parse(latest.date)
        val from = last.minus(DatePeriod(days = 14))
        val to = last.minus(DatePeriod(days = 7))
        val ref = list.lastOrNull { val d = LocalDate.parse(it.date); d in from..to } ?: return null
        return round1(latest.kg - ref.kg)
    }

    /** Points of the mini chart: entries of the last [SPARKLINE_DAYS] days up to [today], oldest first. */
    fun sparkline(entries: List<WeightEntry>, today: LocalDate, days: Int = SPARKLINE_DAYS): List<WeightEntry> {
        val from = today.minus(DatePeriod(days = days - 1))
        return sorted(entries).filter { val d = LocalDate.parse(it.date); d in from..today }
    }

    /** Change over a time span (for the coach): [deltaKg] between the oldest and the newest entry of the window. */
    data class Trend(val deltaKg: Double, val days: Int, val entries: Int)

    /**
     * Trend of the last [TREND_DAYS] days before the latest entry. null with fewer than two entries or when they
     * are less than [TREND_MIN_SPAN_DAYS] days apart.
     */
    fun trend(entries: List<WeightEntry>): Trend? {
        val list = sorted(entries)
        val latest = list.lastOrNull() ?: return null
        val last = LocalDate.parse(latest.date)
        val from = last.minus(DatePeriod(days = TREND_DAYS))
        val window = list.filter { LocalDate.parse(it.date) >= from }
        if (window.size < 2) return null
        val first = window.first()
        val span = LocalDate.parse(first.date).daysUntil(last)
        if (span < TREND_MIN_SPAN_DAYS) return null
        return Trend(round1(latest.kg - first.kg), span, window.size)
    }
}

/** "+0.4" / "−1.2" / "±0" in the display unit (calendar card). */
fun formatWeightChange(deltaKg: Double, unit: WeightUnit, language: Language): String {
    val v = displayWeight(kotlin.math.abs(deltaKg), unit)
    val sign = when {
        v == 0.0 -> "±"
        deltaKg > 0 -> "+"
        else -> "−"
    }
    return "$sign${formatNumber(v, language)} ${unit.symbol}"
}
