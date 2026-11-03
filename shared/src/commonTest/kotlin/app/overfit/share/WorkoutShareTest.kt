package app.overfit.share

import app.overfit.domain.SetValues
import app.overfit.domain.Workout
import app.overfit.workout.entry
import app.overfit.workout.exercise
import app.overfit.workout.planned
import app.overfit.workout.templateOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class WorkoutShareTest {
    private fun workout(): Workout {
        var n = 0
        val w = Workout.fromTemplate(
            templateOf(entry(exercise("wg:bench-press").copy(source = app.overfit.domain.ExerciseSource.WORKOUT_GUIDE), 90, planned(60.0, 8), planned(60.0, 8))),
            now = 0, id = "w", idGen = { "s${n++}" },
        ).let { it.setExerciseNote(it.exercises[0].id, "geheim").copy(note = "privat") }
        val first = w.exercises[0].sets[0].id
        return w.setValues(first, SetValues(62.5, 7)).completeSet(first, 1)
    }

    @Test
    fun workoutSharesActualValuesWithoutNotes() {
        val t = ShareMapper.fromWorkout(workout(), withActual = true)
        assertEquals(ShareFormat.KIND_WORKOUT, t.kind)
        assertNull(t.note)
        assertNull(t.exercises[0].note)
        assertEquals(listOf(SharedSet(62.5, 7, null)), t.exercises[0].sets)
        val parsed = ShareFormat.fromPayload(ShareFormat.toPayload(t))
        assertIs<ShareParse.Ok>(parsed)
        assertEquals(t, parsed.training)
    }

    @Test
    fun workoutWithoutActualSharesThePlan() {
        val t = ShareMapper.fromWorkout(workout(), withActual = false)
        assertEquals(listOf(SharedSet(60.0, 8, null), SharedSet(60.0, 8, null)), t.exercises[0].sets)
    }
}
