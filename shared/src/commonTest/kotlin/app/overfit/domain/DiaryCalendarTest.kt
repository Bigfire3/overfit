package app.overfit.domain

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class DiaryCalendarTest {
    @Test fun weekStartFollowsLanguage() {
        assertEquals(DayOfWeek.MONDAY, DiaryCalendar.weekStart(Language.DE))
        assertEquals(DayOfWeek.SUNDAY, DiaryCalendar.weekStart(Language.EN))
        assertEquals(listOf(DayOfWeek.SUNDAY, DayOfWeek.MONDAY), DiaryCalendar.weekdays(DayOfWeek.SUNDAY).take(2))
        assertEquals(DayOfWeek.SUNDAY, DiaryCalendar.weekdays(DayOfWeek.MONDAY).last())
    }

    @Test fun novemberGridStartsOnSundayWhenMondayFirst() {
        // 1 Nov 2026 is a Sunday → six leading blanks with Monday as week start.
        val cells = DiaryCalendar.monthGrid(2026, 11, DayOfWeek.MONDAY)
        assertEquals(0, cells.size % 7)
        assertEquals(6, cells.indexOfFirst { it != null })
        assertEquals(LocalDate(2026, 11, 1), cells[6])
        assertEquals(LocalDate(2026, 11, 30), cells.last { it != null })
        assertEquals(30, cells.count { it != null })
        assertEquals(42, cells.size) // six rows
    }

    @Test fun novemberGridSundayFirstHasNoLeadingBlanks() {
        val cells = DiaryCalendar.monthGrid(2026, 11, DayOfWeek.SUNDAY)
        assertEquals(LocalDate(2026, 11, 1), cells.first())
        assertEquals(35, cells.size)
    }

    @Test fun leapFebruaryAndDecember() {
        assertEquals(29, DiaryCalendar.monthGrid(2028, 2, DayOfWeek.MONDAY).count { it != null })
        val dec = DiaryCalendar.monthGrid(2026, 12, DayOfWeek.MONDAY)
        assertEquals(31, dec.count { it != null })
        assertEquals(LocalDate(2026, 12, 31), dec.last { it != null })
    }

    @Test fun monthsNewestFirstAcrossYears() {
        val months = DiaryCalendar.months(LocalDate(2025, 11, 20), LocalDate(2026, 2, 3))
        assertEquals(listOf(2026 to 2, 2026 to 1, 2025 to 12, 2025 to 11), months)
    }

    @Test fun monthsShowAtLeastThreeEmptyMonthsBack() {
        val today = LocalDate(2026, 11, 3)
        val expected = listOf(2026 to 11, 2026 to 10, 2026 to 9, 2026 to 8)
        assertEquals(expected, DiaryCalendar.months(null, today))
        // Only training this month: still four months.
        assertEquals(expected, DiaryCalendar.months(LocalDate(2026, 11, 1), today))
        // Older training wins, empty months in between are included.
        assertEquals(6, DiaryCalendar.months(LocalDate(2026, 6, 30), today).size)
    }

    private val mon = DayOfWeek.MONDAY
    // Today: Tue 3 Nov 2026 → week starts Mon 2 Nov.

    @Test fun streakCountsConsecutiveWeeks() {
        val days = listOf(LocalDate(2026, 11, 2), LocalDate(2026, 10, 28), LocalDate(2026, 10, 20))
        assertEquals(3, DiaryCalendar.streakWeeks(days, LocalDate(2026, 11, 3), mon))
    }

    @Test fun runningWeekWithoutWorkoutDoesNotBreakStreak() {
        val days = listOf(LocalDate(2026, 10, 28), LocalDate(2026, 10, 21))
        assertEquals(2, DiaryCalendar.streakWeeks(days, LocalDate(2026, 11, 3), mon))
    }

    @Test fun gapBreaksStreakAndNoWorkoutsIsZero() {
        val days = listOf(LocalDate(2026, 11, 2), LocalDate(2026, 10, 14))
        assertEquals(1, DiaryCalendar.streakWeeks(days, LocalDate(2026, 11, 3), mon))
        assertEquals(0, DiaryCalendar.streakWeeks(emptyList(), LocalDate(2026, 11, 3), mon))
        assertEquals(0, DiaryCalendar.streakWeeks(listOf(LocalDate(2026, 10, 1)), LocalDate(2026, 11, 3), mon))
    }

    @Test fun sundayBelongsToPreviousWeekWithMondayStart() {
        // Sun 1 Nov belongs to the week of Mon 26 Oct; with Sunday start it opens the new week.
        assertEquals(LocalDate(2026, 10, 26), DiaryCalendar.weekOf(LocalDate(2026, 11, 1), DayOfWeek.MONDAY))
        assertEquals(LocalDate(2026, 11, 1), DiaryCalendar.weekOf(LocalDate(2026, 11, 1), DayOfWeek.SUNDAY))
    }
}
