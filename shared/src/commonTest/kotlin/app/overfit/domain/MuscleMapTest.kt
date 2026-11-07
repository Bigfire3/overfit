package app.overfit.domain

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MuscleMapTest {
    private val zone = TimeZone.UTC
    private fun at(d: LocalDate, h: Int = 12, min: Int = 0) = LocalDateTime(d, LocalTime(h, min)).toInstant(zone).toEpochMilliseconds()
    private fun row(d: LocalDate, primary: List<Muscle>, secondary: List<Muscle> = emptyList(), sets: Int = 3, h: Int = 12, min: Int = 0) =
        MuscleSetRow(at(d, h, min), primary, secondary, sets)

    @Test fun primaryCountsFullSecondaryHalf() {
        val bench = row(LocalDate(2026, 11, 4), listOf(Muscle.CHEST), listOf(Muscle.TRICEPS, Muscle.SHOULDERS), sets = 4)
        val dips = row(LocalDate(2026, 11, 5), listOf(Muscle.TRICEPS), listOf(Muscle.CHEST), sets = 3)
        val loads = MuscleMap.aggregate(listOf(bench, dips))
        assertEquals(MuscleLoad(primary = 4, secondary = 3), loads[Muscle.CHEST])
        assertEquals(5.5, loads.getValue(Muscle.CHEST).score)
        assertEquals(MuscleLoad(primary = 3, secondary = 4), loads[Muscle.TRICEPS])
        assertEquals(5.0, loads.getValue(Muscle.TRICEPS).score)
        assertEquals(MuscleLoad(secondary = 4), loads[Muscle.SHOULDERS])
        assertEquals(2.0, loads.getValue(Muscle.SHOULDERS).score)
    }

    @Test fun muscleInBothListsCountsOnceAsPrimary() {
        val loads = MuscleMap.aggregate(listOf(row(LocalDate(2026, 11, 4), listOf(Muscle.CHEST), listOf(Muscle.CHEST), sets = 2)))
        assertEquals(MuscleLoad(primary = 2), loads[Muscle.CHEST])
    }

    @Test fun legsSpreadOverLegRegionsAndUnshownGroupsAreIgnored() {
        val loads = MuscleMap.aggregate(
            listOf(row(LocalDate(2026, 11, 4), listOf(Muscle.LEGS, Muscle.CARDIO), listOf(Muscle.MOBILITY, Muscle.QUADS), sets = 2)),
        )
        listOf(Muscle.QUADS, Muscle.HAMSTRINGS, Muscle.GLUTES, Muscle.CALVES).forEach { assertEquals(MuscleLoad(primary = 2), loads[it], "$it") }
        assertNull(loads[Muscle.CARDIO])
        assertNull(loads[Muscle.MOBILITY])
        assertNull(loads[Muscle.LEGS])
    }

    @Test fun zeroSetsAddNothing() {
        assertTrue(MuscleMap.aggregate(listOf(row(LocalDate(2026, 11, 4), listOf(Muscle.CHEST), sets = 0))).isEmpty())
    }

    @Test fun periodBoundaries() {
        // Wednesday 4 Nov 2026; Monday-first week = 2..8 Nov, Sunday-first week = 1..7 Nov.
        val today = LocalDate(2026, 11, 4)
        assertEquals(LocalDate(2026, 11, 2), MuscleMap.periodStart(MusclePeriod.WEEK, today, DayOfWeek.MONDAY))
        assertEquals(LocalDate(2026, 11, 9), MuscleMap.periodEnd(MusclePeriod.WEEK, today, DayOfWeek.MONDAY))
        assertEquals(LocalDate(2026, 11, 1), MuscleMap.periodStart(MusclePeriod.WEEK, today, DayOfWeek.SUNDAY))
        assertEquals(LocalDate(2026, 11, 1), MuscleMap.periodStart(MusclePeriod.MONTH, today, DayOfWeek.MONDAY))
        assertEquals(LocalDate(2026, 12, 1), MuscleMap.periodEnd(MusclePeriod.MONTH, today, DayOfWeek.MONDAY))
        assertEquals(LocalDate(2026, 1, 1), MuscleMap.periodStart(MusclePeriod.YEAR, today, DayOfWeek.MONDAY))
        assertEquals(LocalDate(2027, 1, 1), MuscleMap.periodEnd(MusclePeriod.YEAR, today, DayOfWeek.MONDAY))
        assertNull(MuscleMap.periodStart(MusclePeriod.TOTAL, today, DayOfWeek.MONDAY))
        assertNull(MuscleMap.periodEnd(MusclePeriod.TOTAL, today, DayOfWeek.MONDAY))
    }

    @Test fun periodsIncludeTheirFirstMinuteAndExcludeTheDayBefore() {
        val today = LocalDate(2026, 11, 4)
        val rows = listOf(
            row(LocalDate(2026, 11, 2), listOf(Muscle.CHEST), sets = 1, h = 0, min = 0), // Monday 0:00 → this week
            row(LocalDate(2026, 11, 1), listOf(Muscle.LATS), sets = 1, h = 23, min = 59), // Sunday → last week, this month
            row(LocalDate(2026, 10, 31), listOf(Muscle.QUADS), sets = 1, h = 23, min = 59), // last month, this year
            row(LocalDate(2025, 12, 31), listOf(Muscle.CALVES), sets = 1, h = 23, min = 59), // last year
        )
        fun keys(p: MusclePeriod, start: DayOfWeek = DayOfWeek.MONDAY) = MuscleMap.forPeriod(rows, p, today, start, zone).keys
        assertEquals(setOf(Muscle.CHEST), keys(MusclePeriod.WEEK))
        // With Sunday as first day the Sunday workout belongs to this week.
        assertEquals(setOf(Muscle.CHEST, Muscle.LATS), keys(MusclePeriod.WEEK, DayOfWeek.SUNDAY))
        assertEquals(setOf(Muscle.CHEST, Muscle.LATS), keys(MusclePeriod.MONTH))
        assertEquals(setOf(Muscle.CHEST, Muscle.LATS, Muscle.QUADS), keys(MusclePeriod.YEAR))
        assertEquals(setOf(Muscle.CHEST, Muscle.LATS, Muscle.QUADS, Muscle.CALVES), keys(MusclePeriod.TOTAL))
    }

    @Test fun levelsAreQuartersOfTheMaximum() {
        val levels = MuscleMap.levels(
            mapOf(
                Muscle.CHEST to MuscleLoad(primary = 20),
                Muscle.TRICEPS to MuscleLoad(primary = 15), // 75 % → 3
                Muscle.SHOULDERS to MuscleLoad(primary = 10, secondary = 1), // 52.5 % → 3
                Muscle.BICEPS to MuscleLoad(primary = 10), // exactly 50 % → 2
                Muscle.LATS to MuscleLoad(primary = 5), // 25 % → 1
                Muscle.CORE to MuscleLoad(secondary = 1), // 2.5 % → still 1
                Muscle.CALVES to MuscleLoad(), // nothing → untrained
            ),
        )
        assertEquals(4, levels[Muscle.CHEST])
        assertEquals(3, levels[Muscle.TRICEPS])
        assertEquals(3, levels[Muscle.SHOULDERS])
        assertEquals(2, levels[Muscle.BICEPS])
        assertEquals(1, levels[Muscle.LATS])
        assertEquals(1, levels[Muscle.CORE])
        assertNull(levels[Muscle.CALVES])
        assertTrue(MuscleMap.levels(emptyMap()).isEmpty())
    }

    @Test fun workoutCountsOnlyCompletedSets() {
        val bench = Exercise("bench", "Bankdrücken", ExerciseSource.WORKOUT_GUIDE, null, listOf(Muscle.CHEST), listOf(Muscle.TRICEPS), null, TrackingType.WEIGHT_REPS, null)
        var w = Workout.empty("Push", 0L, id = "w")
        w = w.addExercise(Workout.added(bench, emptyList()))
        val sets = w.exercises[0].sets
        w = w.completeSet(sets[0].id, 1L).completeSet(sets[1].id, 2L)
        val loads = MuscleMap.ofWorkout(w)
        assertEquals(MuscleLoad(primary = 2), loads[Muscle.CHEST])
        assertEquals(MuscleLoad(secondary = 2), loads[Muscle.TRICEPS])
    }
}
