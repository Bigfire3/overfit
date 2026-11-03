package app.overfit.ui.photos

import app.overfit.data.DiaryPhoto
import app.overfit.data.db.HistoryRow
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

internal fun dateOf(epochMs: Long): LocalDate =
    Instant.fromEpochMilliseconds(epochMs).toLocalDateTime(TimeZone.currentSystemDefault()).date

/** One calendar day of the diary: its photos (oldest first) and finished workouts. */
internal data class DiaryDay(val date: LocalDate, val photos: List<DiaryPhoto>, val workouts: List<HistoryRow>) {
    val hasPhoto get() = photos.isNotEmpty()
}

/** Days with a photo or a workout. A photo belongs to the day it was taken. */
internal fun buildDiaryDays(photos: List<DiaryPhoto>, history: List<HistoryRow>): Map<LocalDate, DiaryDay> {
    val p = photos.sortedBy { it.photo.takenAt }.groupBy { dateOf(it.photo.takenAt) }
    val w = history.groupBy { dateOf(it.startedAt) }
    return (p.keys + w.keys).associateWith { DiaryDay(it, p[it].orEmpty(), w[it].orEmpty()) }
}
