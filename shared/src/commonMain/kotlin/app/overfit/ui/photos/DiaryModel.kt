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

/** One calendar day of the diary: its photos (oldest first), finished workouts and body weight (kg). */
internal data class DiaryDay(
    val date: LocalDate,
    val photos: List<DiaryPhoto>,
    val workouts: List<HistoryRow>,
    val weightKg: Double? = null,
) {
    val hasPhoto get() = photos.isNotEmpty()

    /** Sauna after one of the day's workouts. */
    val sauna get() = workouts.any { it.sauna }
}

/** Days with a photo, a workout or a body weight. A photo belongs to its day ([app.overfit.data.WorkoutPhoto.date], v13). */
internal fun buildDiaryDays(
    photos: List<DiaryPhoto>,
    history: List<HistoryRow>,
    weights: List<app.overfit.domain.WeightEntry> = emptyList(),
): Map<LocalDate, DiaryDay> {
    val p = photos.sortedBy { it.photo.takenAt }.groupBy { it.photo.date }
    val w = history.groupBy { dateOf(it.startedAt) }
    val kg = weights.mapNotNull { e -> runCatching { LocalDate.parse(e.date) }.getOrNull()?.let { it to e.kg } }.toMap()
    return (p.keys + w.keys + kg.keys).associateWith { DiaryDay(it, p[it].orEmpty(), w[it].orEmpty(), kg[it]) }
}

/**
 * Time stamp of a photo added to [date] (day view, training detail, finish screen): now for today, noon of the day for
 * earlier days. The photo belongs to [date] anyway (stored day, v13); the time only orders the day's photos.
 */
internal fun dayPhotoTakenAt(date: LocalDate, today: LocalDate, now: Long): Long =
    if (date == today) now else LocalDateTime(date, LocalTime(12, 0)).toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds()

/** Remembers whether the calendar tab shows the list (true) or the month grid (false). */
class DiaryViewPref(private val kv: app.overfit.data.KeyValueStore) {
    var list: Boolean
        get() = kv.get(KEY) == "list"
        set(value) = kv.put(KEY, if (value) "list" else "calendar")

    private companion object { const val KEY = "diary.view" }
}

/** Days with a photo, workout or weight, newest first, grouped by (year, month), for the list view. */
internal fun diaryListMonths(days: Map<LocalDate, DiaryDay>): List<Pair<Pair<Int, Int>, List<DiaryDay>>> =
    days.values.sortedByDescending { it.date }.groupBy { it.date.year to it.date.monthNumber }.toList()
