package app.overfit.domain

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus

/** Time range of the muscle map in the calendar. Calendar periods (this week, not "the last 7 days"). */
enum class MusclePeriod(val key: String) {
    WEEK("week"), MONTH("month"), YEAR("year"), TOTAL("total");

    companion object {
        fun fromKey(key: String?): MusclePeriod? = entries.firstOrNull { it.key == key }
    }
}

/** Completed sets of one exercise in one finished training, with the exercise's muscle groups. */
data class MuscleSetRow(
    val startedAt: Long,
    val primary: List<Muscle>,
    val secondary: List<Muscle>,
    val sets: Int,
)

/** Sets per muscle: [primary] = the muscle was a main muscle of the exercise, [secondary] = it assisted. */
data class MuscleLoad(val primary: Int = 0, val secondary: Int = 0) {
    /** Primary sets count fully, assisting sets half. */
    val score: Double get() = primary + secondary / 2.0

    operator fun plus(other: MuscleLoad) = MuscleLoad(primary + other.primary, secondary + other.secondary)
}

/**
 * Training frequency per muscle group for the body map ("Trainierte Muskeln").
 * Only completed sets of finished trainings count. Groups that the figure cannot show (cardio, mobility)
 * are ignored, the catch-all "legs" counts for every leg region (quads, hamstrings, glutes, calves).
 */
object MuscleMap {
    /** Number of shades above "untrained": levels are 0 (untrained) … [LEVELS]. */
    const val LEVELS = 4

    /** Groups that have a region on the figure. */
    val shown: List<Muscle> = listOf(
        Muscle.CHEST, Muscle.SHOULDERS, Muscle.BICEPS, Muscle.TRICEPS, Muscle.FOREARMS, Muscle.CORE,
        Muscle.NECK, Muscle.UPPER_BACK, Muscle.LATS, Muscle.LOWER_BACK, Muscle.GLUTES, Muscle.ABDUCTORS,
        Muscle.ADDUCTORS, Muscle.QUADS, Muscle.HAMSTRINGS, Muscle.CALVES,
    )

    private val legs = listOf(Muscle.QUADS, Muscle.HAMSTRINGS, Muscle.GLUTES, Muscle.CALVES)

    private fun regions(muscles: List<Muscle>): Set<Muscle> =
        muscles.flatMap { if (it == Muscle.LEGS) legs else listOf(it) }.filter { it in shown }.toSet()

    /** Adds [sets] of one exercise; a muscle that is primary and secondary at once counts as primary only. */
    private fun MutableMap<Muscle, MuscleLoad>.add(primary: List<Muscle>, secondary: List<Muscle>, sets: Int) {
        if (sets <= 0) return
        val main = regions(primary)
        main.forEach { this[it] = (this[it] ?: MuscleLoad()) + MuscleLoad(primary = sets) }
        (regions(secondary) - main).forEach { this[it] = (this[it] ?: MuscleLoad()) + MuscleLoad(secondary = sets) }
    }

    /** Loads of all rows that started in [fromMs, untilMs) (null = open end). */
    fun aggregate(rows: List<MuscleSetRow>, fromMs: Long? = null, untilMs: Long? = null): Map<Muscle, MuscleLoad> {
        val result = mutableMapOf<Muscle, MuscleLoad>()
        rows.forEach { r ->
            if ((fromMs == null || r.startedAt >= fromMs) && (untilMs == null || r.startedAt < untilMs)) {
                result.add(r.primary, r.secondary, r.sets)
            }
        }
        return result
    }

    /** Loads of one training (completed sets only). */
    fun ofWorkout(workout: Workout): Map<Muscle, MuscleLoad> {
        val result = mutableMapOf<Muscle, MuscleLoad>()
        workout.exercises.forEach { e ->
            result.add(e.exercise.primaryMuscles, e.exercise.secondaryMuscles, e.sets.count { it.completed })
        }
        return result
    }

    /**
     * Shade per muscle, relative to the most trained one: 0 = untrained, else 1..[LEVELS] by equal quarters of the
     * maximum (the top muscle is always [LEVELS], anything trained at least 1).
     */
    fun levels(loads: Map<Muscle, MuscleLoad>): Map<Muscle, Int> {
        val max = loads.values.maxOfOrNull { it.score } ?: 0.0
        if (max <= 0.0) return emptyMap()
        return loads.filterValues { it.score > 0 }.mapValues { (_, load) ->
            kotlin.math.ceil(load.score / max * LEVELS - 1e-9).toInt().coerceIn(1, LEVELS)
        }
    }

    /** First day of [period] around [today] (null = all time). */
    fun periodStart(period: MusclePeriod, today: LocalDate, weekStart: DayOfWeek): LocalDate? = when (period) {
        MusclePeriod.WEEK -> DiaryCalendar.weekOf(today, weekStart)
        MusclePeriod.MONTH -> LocalDate(today.year, today.month, 1)
        MusclePeriod.YEAR -> LocalDate(today.year, 1, 1)
        MusclePeriod.TOTAL -> null
    }

    /** First day after [period] (null = open end). */
    fun periodEnd(period: MusclePeriod, today: LocalDate, weekStart: DayOfWeek): LocalDate? {
        val start = periodStart(period, today, weekStart) ?: return null
        return when (period) {
            MusclePeriod.WEEK -> start.plus(DatePeriod(days = 7))
            MusclePeriod.MONTH -> start.plus(DatePeriod(months = 1))
            MusclePeriod.YEAR -> start.plus(DatePeriod(years = 1))
            MusclePeriod.TOTAL -> null
        }
    }

    /** Loads of [period] around [today], days in [zone]. */
    fun forPeriod(
        rows: List<MuscleSetRow>,
        period: MusclePeriod,
        today: LocalDate,
        weekStart: DayOfWeek,
        zone: TimeZone = TimeZone.currentSystemDefault(),
    ): Map<Muscle, MuscleLoad> = aggregate(
        rows,
        periodStart(period, today, weekStart)?.atStartOfDayIn(zone)?.toEpochMilliseconds(),
        periodEnd(period, today, weekStart)?.atStartOfDayIn(zone)?.toEpochMilliseconds(),
    )
}
