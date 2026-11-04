package app.overfit.ui.photos

import app.overfit.data.db.HistoryRow
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DayPhotoTargetTest {
    private val zone = TimeZone.currentSystemDefault()
    private val today = LocalDate(2026, 5, 20)
    private val yesterday = LocalDate(2026, 5, 19)

    private fun at(date: LocalDate, h: Int, m: Int = 0) =
        LocalDateTime(date, LocalTime(h, m)).toInstant(zone).toEpochMilliseconds()

    private fun row(id: String, startedAt: Long) =
        HistoryRow(id, id, startedAt, startedAt + 1000, null, 1, 1, 0.0, 0)

    @Test
    fun todayUsesNowAndLastTrainingOfTheDay() {
        val now = at(today, 20)
        val rows = listOf(row("early", at(today, 8)), row("late", at(today, 18)), row("other", at(yesterday, 19)))
        val t = dayPhotoTarget(today, today, rows, now)
        assertEquals(now, t.takenAt)
        assertEquals("late", t.sessionId)
    }

    @Test
    fun todayWithoutTrainingHasNoSession() {
        val now = at(today, 9)
        val t = dayPhotoTarget(today, today, listOf(row("y", at(yesterday, 9))), now)
        assertEquals(now, t.takenAt)
        assertNull(t.sessionId)
    }

    @Test
    fun earlierDayWithoutTrainingUsesNoonAndNoSession() {
        val t = dayPhotoTarget(yesterday, today, listOf(row("t", at(today, 8))), at(today, 20))
        assertEquals(at(yesterday, 12), t.takenAt)
        assertNull(t.sessionId)
    }

    @Test
    fun earlierDayWithTrainingUsesNoonAndThatSession() {
        val rows = listOf(row("a", at(yesterday, 7)), row("b", at(yesterday, 17)))
        val t = dayPhotoTarget(yesterday, today, rows, at(today, 20))
        assertEquals(at(yesterday, 12), t.takenAt)
        assertEquals("b", t.sessionId)
    }
}
