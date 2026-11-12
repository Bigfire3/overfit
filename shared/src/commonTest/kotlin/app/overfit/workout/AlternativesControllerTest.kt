package app.overfit.workout

import app.overfit.domain.Template
import app.overfit.domain.Workout
import app.overfit.platform.RecordingHaptics
import app.overfit.platform.RecordingRestAlerts
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A/B rotation at the start and switching the variant in a running training. */
@OptIn(ExperimentalCoroutinesApi::class)
class AlternativesControllerTest {
    private class Env(val scope: TestScope, testScope: CoroutineScope) {
        val store = InMemoryWorkoutStore()
        val c = WorkoutController(store, RecordingRestAlerts(), RecordingHaptics(), { WorkoutPrefs() }, testScope, { scope.testScheduler.currentTime })
    }

    private fun TestScope.env() = Env(this, backgroundScope)

    private val bench = exercise("bench", name = "Bench")
    private val dumbbell = exercise("db", name = "Dumbbell Press")
    private val fly = exercise("fly", name = "Fly")
    private val squat = exercise("squat", name = "Squat")

    private val template = templateOf(
        entry(squat, null, planned(80.0, 5)),
        entry(bench, 60, planned(50.0, 10)).copy(
            alternatives = listOf(entry(dumbbell, 90, planned(reps = 12)), entry(fly, null, planned(15.0, 15))),
        ),
    )

    private suspend fun Env.started(t: Template = template): Workout {
        c.awaitLoaded()
        assertTrue(c.startFromTemplate(t))
        return c.state.value.workout!!
    }

    @Test
    fun start_neverDone_takesTheExercise() = runTest {
        val w = env().started()
        assertEquals(listOf("squat", "bench"), w.exercises.map { it.exercise.id })
        assertEquals(listOf("bench", "db", "fly"), w.exercises[1].variants.map { it.exercise.id })
    }

    @Test
    fun start_rotatesAfterTheVariantDoneLast() = runTest {
        val cases = mapOf("bench" to "db", "db" to "fly", "fly" to "bench")
        cases.forEach { (lastDone, expected) ->
            val e = env()
            e.store.lastVariants[template.id] = listOf(lastDone)
            assertEquals(expected, e.started().exercises[1].exercise.id, "after $lastDone")
        }
    }

    @Test
    fun start_lastVariantOfAnotherTemplate_isIgnored() = runTest {
        val e = env()
        e.store.lastVariants[99L] = listOf("bench")
        assertEquals("bench", e.started().exercises[1].exercise.id)
    }

    @Test
    fun switchVariant_usesLastTimeOfTheTargetExercise() = runTest {
        val e = env()
        e.store.last["db"] = listOf(planned(32.5, 8))
        e.store.last["bench"] = listOf(planned(70.0, 3))
        val w = e.started()
        val entryId = w.exercises[1].id
        e.c.switchVariant(entryId, 1)
        val switched = e.c.state.value.workout!!.exercise(entryId)!!
        assertEquals("db", switched.exercise.id)
        assertEquals(90, switched.restSec)
        assertEquals(listOf(planned(32.5, 12)), switched.sets.map { it.target })
        e.c.flush()
        assertEquals("db", e.store.active!!.exercise(entryId)!!.exercise.id)
    }

    @Test
    fun switchVariant_afterADoneSet_doesNothing() = runTest {
        val e = env()
        val w = e.started()
        val entry = w.exercises[1]
        e.c.toggleSet(entry.sets[0].id)
        e.c.switchVariant(entry.id, 2)
        assertEquals("bench", e.c.state.value.workout!!.exercise(entry.id)!!.exercise.id)
    }
}
