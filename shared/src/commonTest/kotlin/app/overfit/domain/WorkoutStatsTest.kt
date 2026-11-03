package app.overfit.domain

import app.overfit.workout.entry
import app.overfit.workout.exercise
import app.overfit.workout.planned
import app.overfit.workout.templateOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WorkoutStatsTest {
    private val bench = exercise("bench", name = "Bench")
    private val hold = exercise("hold", TrackingType.TIME, name = "Hold")

    private fun workout(): Workout {
        var n = 0
        val w = Workout.fromTemplate(
            templateOf(entry(bench, 90, planned(60.0, 8), planned(60.0, 8), planned(60.0, 8)), entry(hold, null, planned(duration = 30))),
            now = 1_000,
            id = "w",
            idGen = { "s${n++}" },
        )
        val sets = w.exercises.flatMap { it.sets }
        return w.setValues(sets[0].id, SetValues(60.0, 8)).completeSet(sets[0].id, 2_000)
            .setValues(sets[1].id, SetValues(65.0, 6)).completeSet(sets[1].id, 3_000)
            .completeSet(sets[3].id, 4_000)
    }

    @Test
    fun e1rmUsesEpleyAndIgnoresInvalidSets() {
        assertEquals(100.0, WorkoutStats.e1rm(100.0, 1))
        assertEquals(80.0, WorkoutStats.e1rm(60.0, 10)!!, 1e-9)
        assertNull(WorkoutStats.e1rm(-20.0, 5))
        assertNull(WorkoutStats.e1rm(60.0, 0))
        assertNull(WorkoutStats.e1rm(60.0, 20))
    }

    @Test
    fun summaryCountsSetsVolumeAndDeviations() {
        val s = WorkoutStats.summarize(workout(), endedAt = 61_000, previous = emptyList())
        assertEquals(60_000, s.durationMs)
        assertEquals(3, s.completedSets)
        assertEquals(4, s.totalSets)
        assertEquals(60.0 * 8 + 65.0 * 6, s.volumeKg, 1e-9)
        assertEquals(1, s.skippedSets)
        assertTrue(s.records.isEmpty(), "first time is not a record")
    }

    @Test
    fun recordsBeatEarlierBests() {
        val previous = listOf(
            LoggedSet("bench", SetValues(62.5, 5)),
            LoggedSet("bench", SetValues(60.0, 8)),
            LoggedSet("hold", SetValues(durationSec = 40)),
        )
        val records = WorkoutStats.summarize(workout(), 61_000, previous).records
        val kinds = records.filter { it.exerciseId == "bench" }.associateBy { it.kind }
        assertEquals(65.0, kinds.getValue(RecordKind.WEIGHT).value)
        assertEquals(62.5, kinds.getValue(RecordKind.WEIGHT).previous)
        // 65×6 → 78 vs. 60×8 → 76.
        assertTrue(RecordKind.E1RM in kinds)
        assertTrue(RecordKind.REPS !in kinds, "8 reps equals the old best")
        assertTrue(records.none { it.exerciseId == "hold" }, "30 s does not beat 40 s")
    }

    @Test
    fun bestsOnlyForTheTrackingType() {
        val b = WorkoutStats.bests(listOf(SetValues(10.0, 5, 30)), TrackingType.TIME)
        assertNull(b.maxWeightKg)
        assertNull(b.maxReps)
        assertEquals(30, b.maxDurationSec)
    }
}
