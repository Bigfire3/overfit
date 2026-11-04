package app.overfit.ui.photos

import app.overfit.data.DiaryPhoto
import app.overfit.data.db.HistoryRow
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.toInstant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

internal fun dateOf(epochMs: Long): LocalDate =
    Instant.fromEpochMilliseconds(epochMs).toLocalDateTime(TimeZone.currentSystemDefault()).date

/** One calendar day of the diary: its photos (oldest first) and finished workouts. */
internal data class DiaryDay(val date: LocalDate, val photos: List<DiaryPhoto>, val workouts: List<HistoryRow>) {
    val hasPhoto get() = photos.isNotEmpty()

    /** Sauna after one of the day's workouts. */
    val sauna get() = workouts.any { it.sauna }
}

/** Days with a photo or a workout. A photo belongs to the day it was taken. */
internal fun buildDiaryDays(photos: List<DiaryPhoto>, history: List<HistoryRow>): Map<LocalDate, DiaryDay> {
    val p = photos.sortedBy { it.photo.takenAt }.groupBy { dateOf(it.photo.takenAt) }
    val w = history.groupBy { dateOf(it.startedAt) }
    return (p.keys + w.keys).associateWith { DiaryDay(it, p[it].orEmpty(), w[it].orEmpty()) }
}

/**
 * Where a photo added in the day view goes: the day's last training (so the coach and the training
 * detail see it), or no training at all. [takenAt] is now for today, noon of the day for earlier days,
 * so the photo always lands on the tapped day.
 */
internal data class DayPhotoTarget(val sessionId: String?, val takenAt: Long)

internal fun dayPhotoTarget(date: LocalDate, today: LocalDate, workouts: List<HistoryRow>, now: Long): DayPhotoTarget {
    val session = workouts.filter { dateOf(it.startedAt) == date }.maxByOrNull { it.startedAt }?.id
    val takenAt = if (date == today) now else LocalDateTime(date, LocalTime(12, 0)).toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds()
    return DayPhotoTarget(session, takenAt)
}
