package app.overfit.domain

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BodyWeightStatsTest {
    private val today = LocalDate(2026, 11, 7)

    @Test fun weekChangeComparesWithTheNewestEntrySevenToFourteenDaysOlder() {
        val entries = listOf(
            WeightEntry("2026-10-20", 85.0),
            WeightEntry("2026-10-28", 83.4), // 10 days before the latest
            WeightEntry("2026-10-31", 83.0), // 7 days before the latest → reference
            WeightEntry("2026-11-05", 82.8), // too close
            WeightEntry("2026-11-07", 82.4),
        ).shuffled()
        assertEquals(-0.6, BodyWeightStats.weekChange(entries)!!, 1e-9)
    }

    @Test fun weekChangeIsNullWithoutAValueFromTheWeekBefore() {
        assertNull(BodyWeightStats.weekChange(emptyList()))
        assertNull(BodyWeightStats.weekChange(listOf(WeightEntry("2026-11-07", 82.0))))
        // Only a value months ago and one two days ago: no "vs. last week".
        assertNull(BodyWeightStats.weekChange(listOf(WeightEntry("2026-08-01", 90.0), WeightEntry("2026-11-05", 82.0), WeightEntry("2026-11-07", 81.0))))
    }

    @Test fun sparklineKeepsTheLastEightWeeksOldestFirst() {
        val entries = listOf(
            WeightEntry("2026-09-12", 86.0), // 56 days before today → just outside
            WeightEntry("2026-09-13", 85.5), // first day of the window
            WeightEntry("2026-11-07", 82.0),
            WeightEntry("2026-10-01", 84.0),
            WeightEntry("2026-11-08", 81.0), // future, not shown
        )
        val points = BodyWeightStats.sparkline(entries, today)
        assertEquals(listOf("2026-09-13", "2026-10-01", "2026-11-07"), points.map { it.date })
        assertEquals(56, BodyWeightStats.SPARKLINE_DAYS)
    }

    @Test fun trendSpansUpToEightWeeksAndNeedsAWeek() {
        val entries = listOf(
            WeightEntry("2026-08-01", 95.0), // outside the 56-day window before the latest
            WeightEntry("2026-09-20", 85.0),
            WeightEntry("2026-10-15", 84.0),
            WeightEntry("2026-11-07", 82.6),
        )
        val trend = BodyWeightStats.trend(entries)!!
        assertEquals(-2.4, trend.deltaKg, 1e-9)
        assertEquals(48, trend.days)
        assertEquals(3, trend.entries)
        assertNull(BodyWeightStats.trend(listOf(WeightEntry("2026-11-03", 83.0), WeightEntry("2026-11-07", 82.0))))
        assertNull(BodyWeightStats.trend(listOf(WeightEntry("2026-11-07", 82.0))))
    }

    @Test fun formatsChangeWithSignAndUnit() {
        assertEquals("−0,6 kg", formatWeightChange(-0.6, WeightUnit.KG, Language.DE))
        assertEquals("+1.2 kg", formatWeightChange(1.2, WeightUnit.KG, Language.EN))
        assertEquals("±0 kg", formatWeightChange(0.0, WeightUnit.KG, Language.EN))
        assertTrue(formatWeightChange(1.0, WeightUnit.LB, Language.EN).startsWith("+2.2 lb"))
    }

    @Test fun coachFactsCarryTheTrend() {
        val f = aiProfileFacts(Profile(), listOf(WeightEntry("2026-10-07", 84.0), WeightEntry("2026-11-07", 82.5)), today, Language.EN)!!
        assertEquals("Body weight: 82.5 kg (as of 2026-11-07) · Weight trend: -1.5 kg over 31 days", f)
    }
}
