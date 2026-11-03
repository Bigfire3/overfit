package app.overfit.workout

import app.overfit.domain.SetValues
import app.overfit.domain.Workout
import app.overfit.domain.TemplateExercise
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FinishedEditTest {
    private fun edit(): FinishedEdit {
        var n = 0
        val w = Workout.fromTemplate(
            templateOf(TemplateExercise(exercise("a"), 60, null, listOf(planned(50.0, 8), planned(55.0, 6)))),
            now = 1000, id = "w1", idGen = { "id${n++}" },
        )
        val first = w.exercises[0].sets[0].id
        val done = w.completeSet(first, 2000)
        return FinishedEdit(done, note = "n", feeling = 4, sauna = false, endedAt = 9000)
    }

    private fun FinishedEdit.sets() = workout.exercises[0].sets

    @Test
    fun addSetAddsCompletedSetAtEndedAt() {
        val e = edit()
        val exId = e.workout.exercises[0].id
        val added = e.addSet(exId, "new")
        val set = added.sets().last()
        assertEquals("new", set.id)
        assertEquals(3, added.sets().size)
        assertTrue(set.completed)
        assertEquals(9000L, set.completedAt)
    }

    @Test
    fun addSetToUnknownExerciseChangesNothing() {
        val e = edit()
        assertEquals(e, e.addSet("missing", "new"))
    }

    @Test
    fun toggleSetWorksBothWays() {
        val e = edit()
        val open = e.sets()[1].id
        val done = e.sets()[0].id
        val on = e.toggleSet(open)
        assertTrue(on.sets()[1].completed)
        assertEquals(9000L, on.sets()[1].completedAt)
        val off = on.toggleSet(done)
        assertFalse(off.sets()[0].completed)
        assertNull(off.sets()[0].completedAt)
        assertEquals(e, e.toggleSet("missing"))
    }

    @Test
    fun setValuesClampsNegativeToZero() {
        val e = edit()
        val id = e.sets()[0].id
        val v = e.setValues(id, SetValues(-5.0, -3, -1)).sets()[0].actual
        assertEquals(SetValues(0.0, 0, 0), v)
        val ok = e.setValues(id, SetValues(60.0, 5, null)).sets()[0].actual
        assertEquals(SetValues(60.0, 5, null), ok)
    }

    @Test
    fun removeSetRemovesIt() {
        val e = edit()
        val removed = e.removeSet(e.sets()[0].id)
        assertEquals(1, removed.sets().size)
        assertEquals(e.sets()[1].id, removed.sets()[0].id)
    }

    @Test
    fun noOpEqualsOriginalAndChangeDiffers() {
        val e = edit()
        assertEquals(e, e.setValues(e.sets()[0].id, e.sets()[0].actual))
        assertEquals(e, e.withNote("n").withFeeling(4).withSauna(false))
        assertNotEquals(e, e.withSauna(true))
        assertNotEquals(e, e.withFeeling(2))
        assertNotEquals(e, e.withNote("other"))
        assertEquals(5, e.withFeeling(9).feeling)
        assertEquals(1, e.withFeeling(0).feeling)
        assertNull(e.withFeeling(null).feeling)
        assertEquals(2000, e.withNote("x".repeat(3000)).note.length)
    }
}
