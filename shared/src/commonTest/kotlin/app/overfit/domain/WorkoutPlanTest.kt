package app.overfit.domain

import app.overfit.workout.entry
import app.overfit.workout.exercise
import app.overfit.workout.planned
import app.overfit.workout.templateOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class WorkoutPlanTest {
    private val bench = exercise("bench", name = "Bench")
    private val squat = exercise("squat", name = "Squat")
    private val row = exercise("row", name = "Row")
    private val template = templateOf(
        entry(bench, 60, planned(50.0, 10), planned(50.0, 8)),
        entry(squat, null, planned(80.0, 5)),
    )
    private fun started() = Workout.fromTemplate(template, 0L)

    @Test
    fun unchangedWorkout_keepsTemplateAsIs() {
        val w = started().completeSet(started().exercises[0].sets[0].id, 1)
        assertSame(template, WorkoutPlan.adopt(template, w))
    }

    @Test
    fun addedSetAndExercise_reorder_keepPlannedValuesOfExistingSets() {
        var w = started()
        w = w.addSet(w.exercises[0].id)
        w = w.addExercise(Workout.added(row, emptyList()))
        w = w.moveExercise(2, 0)
        val adopted = WorkoutPlan.adopt(template, w)
        assertEquals(listOf("row", "bench", "squat"), adopted.exercises.map { it.exercise.id })
        assertEquals(listOf(planned(50.0, 10), planned(50.0, 8), PlannedSet()), adopted.exercises[1].sets)
        assertEquals(List(Workout.DEFAULT_SETS) { PlannedSet() }, adopted.exercises[0].sets)
        assertEquals(60, adopted.exercises[1].restSec)
        assertEquals(template.id, adopted.id)
    }

    @Test
    fun removedExerciseAndSet_andRename_areTakenOver() {
        var w = started()
        w = w.removeExercise(w.exercises[1].id)
        w = w.removeSet(w.exercises[0].sets[1].id)
        w = w.withName("Push leicht")
        val adopted = WorkoutPlan.adopt(template, w)
        assertEquals("Push leicht", adopted.name)
        assertEquals(listOf("bench"), adopted.exercises.map { it.exercise.id })
        assertEquals(listOf(planned(50.0, 10)), adopted.exercises[0].sets)
    }

    @Test
    fun emptyWorkout_neverWipesTheTemplate() {
        var w = started()
        w.exercises.forEach { e -> w = w.removeExercise(e.id) }
        assertSame(template, WorkoutPlan.adopt(template, w))
        assertNull(WorkoutPlan.create(Workout.empty("x", 0L)))
    }

    @Test
    fun create_fromEmptyStart_hasStructureWithoutTargets() {
        var w = Workout.empty("Workout vom 3. Nov.", 0L)
        w = w.addExercise(Workout.added(row, listOf(planned(40.0, 10), planned(40.0, 9))))
        w = w.setExerciseRest(w.exercises[0].id, 75)
        val created = WorkoutPlan.create(w)!!
        assertEquals(0L, created.id)
        assertEquals("Workout vom 3. Nov.", created.name)
        assertEquals(75, created.exercises.single().restSec)
        assertEquals(listOf(PlannedSet(), PlannedSet()), created.exercises.single().sets)
    }
}
