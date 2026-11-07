package app.overfit.domain

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/** Calendar maths for the photo diary (month grid, week start, week streak). Pure, common tests. */
object DiaryCalendar {
    /** Mon–Sun in German, Sun–Sat in English (US convention). */
    fun weekStart(language: Language): DayOfWeek = if (language == Language.DE) DayOfWeek.MONDAY else DayOfWeek.SUNDAY

    /** The seven weekdays in display order, starting with [weekStart]. */
    fun weekdays(weekStart: DayOfWeek): List<DayOfWeek> = List(7) { DayOfWeek.entries[(weekStart.ordinal + it) % 7] }

    /**
     * Cells of one month: leading nulls up to the first weekday, the days, trailing nulls so the
     * list fills whole rows of 7.
     */
    fun monthGrid(year: Int, month: Int, weekStart: DayOfWeek): List<LocalDate?> {
        val first = LocalDate(year, month, 1)
        val lead = (first.dayOfWeek.ordinal - weekStart.ordinal + 7) % 7
        val next = if (month == 12) LocalDate(year + 1, 1, 1) else LocalDate(year, month + 1, 1)
        val days = first.daysUntil(next)
        val cells = MutableList<LocalDate?>(lead) { null }
        for (d in 0 until days) cells += first.plus(DatePeriod(days = d))
        while (cells.size % 7 != 0) cells += null
        return cells
    }

    /** Months always shown besides the current one, even when empty (BeReal-style memories). */
    const val MIN_MONTHS_BACK = 3

    /** Future months the calendar tab shows above the current one. */
    const val MONTHS_AHEAD = 12

    /**
     * Months from [today]'s month back to the month of [earliest], newest first, as (year, month).
     * Empty months in between are included, and at least the last [MIN_MONTHS_BACK] months plus the
     * current one, so the diary never shows a single lonely month. [ahead] adds that many future months on top
     * (calendar tab: [MONTHS_AHEAD]); the current month is then at index [ahead].
     */
    fun months(earliest: LocalDate?, today: LocalDate, ahead: Int = 0): List<Pair<Int, Int>> {
        val start = earliest?.takeIf { it <= today } ?: today
        val floor = today.minus(DatePeriod(months = MIN_MONTHS_BACK))
        val from = if (start < floor) start else floor
        val out = mutableListOf<Pair<Int, Int>>()
        val top = today.plus(DatePeriod(months = ahead.coerceAtLeast(0)))
        var y = top.year
        var m = top.monthNumber
        while (y > from.year || (y == from.year && m >= from.monthNumber)) {
            out += y to m
            if (m == 1) { m = 12; y-- } else m--
        }
        return out
    }

    /** First day of the week containing [date]. */
    fun weekOf(date: LocalDate, weekStart: DayOfWeek): LocalDate =
        date.minus(DatePeriod(days = (date.dayOfWeek.ordinal - weekStart.ordinal + 7) % 7))

    /**
     * Current streak in weeks with at least one workout. The running week counts if it has one;
     * if it has none yet, the streak is not broken until the week is over.
     */
    fun streakWeeks(trainingDays: Collection<LocalDate>, today: LocalDate, weekStart: DayOfWeek): Int {
        val weeks = trainingDays.map { weekOf(it, weekStart) }.toSet()
        var week = weekOf(today, weekStart)
        if (week !in weeks) week = week.minus(DatePeriod(days = 7))
        var n = 0
        while (week in weeks) { n++; week = week.minus(DatePeriod(days = 7)) }
        return n
    }
}
