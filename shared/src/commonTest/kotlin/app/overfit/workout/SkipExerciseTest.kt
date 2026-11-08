package app.overfit.workout

import app.overfit.domain.Workout
import app.overfit.domain.WorkoutPlan
import app.overfit.domain.WorkoutStats
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** "Skip today": skipped sets are not open, finishing becomes possible, the plan keeps the exercise. */
class SkipExerciseTest {
    private val bench = exercise("bench")
    private val squat = exercise("squat")
    private val template = templateOf(
        entry(bench, 60, planned(50.0, 10), planned(50.0, 8)),
        entry(squat, null, planned(80.0, 5)),
    )

    private fun started(): Workout {
        var n = 0
        return Workout.fromTemplate(template, now = 1000, id = "w", idGen = { "id${n++}" })
    }

    @Test
    fun skippedSetsAreNotOpen_andDoneOnesStillCount() {
        var w = started()
        val benchId = w.exercises[0].id
        w = w.completeSet(w.exercises[0].sets[0].id, 2000)
        assertEquals(3, w.totalSets)
        assertFalse(w.allDone)
        w = w.setSkipped(benchId, true)
        assertEquals(2, w.totalSets) // 1 done bench set + 1 squat set
        assertEquals(1, w.completedSets)
        // The next open set passes over the skipped exercise.
        assertEquals(w.exercises[1].sets[0].id, w.nextOpenSet()?.set?.id)
        w = w.completeSet(w.exercises[1].sets[0].id, 3000)
        assertNull(w.nextOpenSet())
        assertTrue(w.allDone)
        // Undo brings the open set back.
        val back = w.setSkipped(benchId, false)
        assertFalse(back.allDone)
        assertEquals(w.exercises[0].sets[1].id, back.nextOpenSet()?.set?.id)
    }

    @Test
    fun allSkipped_isDone_emptyWorkoutIsNot() {
        var w = started()
        w.exercises.forEach { e -> w = w.setSkipped(e.id, true) }
        assertTrue(w.allDone)
        assertEquals(0, w.totalSets)
        assertFalse(Workout.empty("x", 0).allDone)
    }

    @Test
    fun plan_keepsSkippedExerciseUnchanged() {
        var w = started()
        // Skipped bench with one set removed: the plan must not lose the set (it was not trained).
        w = w.removeSet(w.exercises[0].sets[1].id).setSkipped(w.exercises[0].id, true)
        assertSame(template, WorkoutPlan.adopt(template, w))
    }

    @Test
    fun summary_countsSkippedExercisesNotTheirSetsAsLeftOpen() {
        var w = started()
        w = w.setSkipped(w.exercises[0].id, true)
        val s = WorkoutStats.summarize(w, 5000, emptyList())
        assertEquals(1, s.skippedExercises)
        assertEquals(1, s.skippedSets) // only the open squat set
        assertEquals(1, s.totalSets)
    }
}

class SkipExerciseEdgeTest {
    @Test
    fun exercisesWithoutSets_doNotCountAsDone() {
        val w = Workout.empty("x", 0).addExercise(Workout.added(exercise("a"), emptyList()))
        val noSets = w.removeSet(w.exercises[0].sets[0].id).let { it.removeSet(it.exercises[0].sets[0].id) }.let { it.removeSet(it.exercises[0].sets[0].id) }
        assertFalse(noSets.allDone)
        assertTrue(noSets.setSkipped(noSets.exercises[0].id, true).allDone)
    }

    @Test
    fun addedThenSkipped_doesNotJoinThePlan() {
        val template = templateOf(entry(exercise("bench"), null, planned(50.0, 10)))
        var w = Workout.fromTemplate(template, 0)
        w = w.addExercise(Workout.added(exercise("row"), emptyList()))
        w = w.setSkipped(w.exercises[1].id, true)
        assertSame(template, WorkoutPlan.adopt(template, w))
    }
}
