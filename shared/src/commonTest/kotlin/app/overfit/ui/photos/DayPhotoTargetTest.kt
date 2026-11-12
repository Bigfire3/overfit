package app.overfit.ui.photos

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals

/** Photos belong to their day (v14); only the time stamp depends on whether the day is today. */
class DayPhotoTargetTest {
    private val zone = TimeZone.currentSystemDefault()
    private val today = LocalDate(2026, 5, 20)
    private val yesterday = LocalDate(2026, 5, 19)

    private fun at(date: LocalDate, h: Int, m: Int = 0) =
        LocalDateTime(date, LocalTime(h, m)).toInstant(zone).toEpochMilliseconds()

    @Test
    fun todayUsesNow() {
        val now = at(today, 20)
        assertEquals(now, dayPhotoTakenAt(today, today, now))
    }

    @Test
    fun earlierDayUsesNoonOfThatDay() {
        assertEquals(at(yesterday, 12), dayPhotoTakenAt(yesterday, today, at(today, 20)))
    }
}
