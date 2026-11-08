package app.overfit.domain

import app.overfit.workout.entry
import app.overfit.workout.exercise
import app.overfit.workout.planned
import app.overfit.workout.templateOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Exercise alternatives (A/B weeks): start, switching, rotation and taking a training over into the plan. */
class AlternativesTest {
    private val bench = exercise("bench", name = "Bench")
    private val dumbbell = exercise("db", name = "Dumbbell Press")
    private val cable = exercise("cable", name = "Cable Fly")
    private val squat = exercise("squat", name = "Squat")

    /** Bench (A, 2 sets, 60 s) with the alternative dumbbell press (B, reps only, 90 s), then squat. */
    private val template = templateOf(
        entry(bench, 60, planned(50.0, 10), planned(50.0, 8)).copy(
            alternatives = listOf(entry(dumbbell, 90, planned(reps = 12)).copy(note = "B-Notiz")),
        ),
        entry(squat, null, planned(80.0, 5)),
    )

    private var counter = 0
    private val ids: () -> String = { "id${counter++}" }

    // --- fromTemplate ---------------------------------------------------------------------------

    @Test
    fun fromTemplate_withoutVariants_takesTheExerciseAndOffersAllVariants() {
        val w = Workout.fromTemplate(template, 0L)
        val e = w.exercises[0]
        assertEquals("bench", e.exercise.id)
        assertEquals(60, e.restSec)
        assertEquals(listOf("bench", "db"), e.variants.map { it.exercise.id })
        assertEquals(0, e.variantIndex)
        // No alternatives: no variants (no chips).
        assertTrue(w.exercises[1].variants.isEmpty())
        assertEquals(-1, w.exercises[1].variantIndex)
    }

    @Test
    fun fromTemplate_withVariant_takesSetsRestNoteOfIt_andFillsGapsFromItsLastTime() {
        val last = mapOf(
            "db" to listOf(planned(30.0, 10)),
            "bench" to listOf(planned(99.0, 1)),
        )
        val w = Workout.fromTemplate(template, 0L, last, variants = mapOf(0 to 1))
        val e = w.exercises[0]
        assertEquals("db", e.exercise.id)
        assertEquals(90, e.restSec)
        assertEquals("B-Notiz", e.note)
        assertEquals(1, e.variantIndex)
        // Reps from the plan, the missing weight from the last time of the dumbbell press (not of bench).
        assertEquals(listOf(planned(30.0, 12)), e.sets.map { it.target })
        assertEquals(e.sets.map { it.target }, e.sets.map { it.actual })
        assertEquals("squat", w.exercises[1].exercise.id)
    }

    @Test
    fun fromTemplate_unknownVariantIndex_fallsBackToTheExercise() {
        val w = Workout.fromTemplate(template, 0L, variants = mapOf(0 to 5))
        assertEquals("bench", w.exercises[0].exercise.id)
    }

    // --- switchVariant --------------------------------------------------------------------------

    @Test
    fun switchVariant_switchesExerciseRestNoteAndSets() {
        val w = Workout.fromTemplate(template, 0L, idGen = ids)
        val entryId = w.exercises[0].id
        val switched = w.switchVariant(entryId, 1, listOf(planned(32.5, 9)), ids)
        val e = switched.exercise(entryId)!!
        assertEquals("db", e.exercise.id)
        assertEquals(90, e.restSec)
        assertEquals("B-Notiz", e.note)
        assertEquals(listOf(planned(32.5, 12)), e.sets.map { it.target })
        assertEquals(1, e.variantIndex)
        // Back to A: the plan of bench again.
        val back = switched.switchVariant(entryId, 0, emptyList(), ids).exercise(entryId)!!
        assertEquals("bench", back.exercise.id)
        assertEquals(listOf(planned(50.0, 10), planned(50.0, 8)), back.sets.map { it.target })
        assertEquals("", back.note)
    }

    @Test
    fun switchVariant_refusedOnceASetIsDone_orForUnknownIndex() {
        val w = Workout.fromTemplate(template, 0L)
        val e = w.exercises[0]
        val done = w.completeSet(e.sets[0].id, 1L)
        assertSame(done.exercises[0], done.switchVariant(e.id, 1, emptyList()).exercises[0])
        assertEquals(w.exercises[0], w.switchVariant(e.id, 7, emptyList()).exercises[0])
        // Without alternatives nothing can be switched.
        assertEquals(w.exercises[1], w.switchVariant(w.exercises[1].id, 1, emptyList()).exercises[1])
    }

    // --- nextVariant ----------------------------------------------------------------------------

    @Test
    fun nextVariant_roundRobin() {
        val ab = listOf("a", "b")
        assertEquals(1, WorkoutPlan.nextVariant(ab, "a"))
        assertEquals(0, WorkoutPlan.nextVariant(ab, "b"))
        val abc = listOf("a", "b", "c")
        assertEquals(1, WorkoutPlan.nextVariant(abc, "a"))
        assertEquals(2, WorkoutPlan.nextVariant(abc, "b"))
        assertEquals(0, WorkoutPlan.nextVariant(abc, "c"))
        assertEquals(0, WorkoutPlan.nextVariant(abc, null))
        assertEquals(0, WorkoutPlan.nextVariant(abc, "x"))
    }

    // --- adopt ----------------------------------------------------------------------------------

    @Test
    fun adopt_unchangedTrainingOfVariantB_keepsTemplateAsIs() {
        val w = Workout.fromTemplate(template, 0L, variants = mapOf(0 to 1))
        val done = w.completeSet(w.exercises[0].sets[0].id, 1L)
        assertSame(template, WorkoutPlan.adopt(template, done))
        val a = Workout.fromTemplate(template, 0L)
        assertSame(template, WorkoutPlan.adopt(template, a.completeSet(a.exercises[0].sets[0].id, 1L)))
    }

    @Test
    fun adopt_trainedVariantB_onlyItsSetCountChanges_alternativesKept() {
        var w = Workout.fromTemplate(template, 0L, variants = mapOf(0 to 1))
        w = w.addSet(w.exercises[0].id)
        val adopted = WorkoutPlan.adopt(template, w)
        val slot = adopted.exercises[0]
        assertEquals(template.exercises[0].copy(alternatives = slot.alternatives), slot)
        assertEquals("bench", slot.exercise.id)
        assertEquals(listOf(planned(50.0, 10), planned(50.0, 8)), slot.sets)
        val b = slot.alternatives.single()
        assertEquals("db", b.exercise.id)
        assertEquals(listOf(planned(reps = 12), PlannedSet()), b.sets)
        assertEquals(90, b.restSec)
        assertEquals("B-Notiz", b.note)
        assertEquals(template.exercises[1], adopted.exercises[1])
    }

    @Test
    fun adopt_trainedVariantA_removedSet_keepsAlternative() {
        var w = Workout.fromTemplate(template, 0L)
        w = w.removeSet(w.exercises[0].sets[1].id)
        val slot = WorkoutPlan.adopt(template, w).exercises[0]
        assertEquals("bench", slot.exercise.id)
        assertEquals(listOf(planned(50.0, 10)), slot.sets)
        assertEquals(60, slot.restSec)
        assertEquals(template.exercises[0].alternatives, slot.alternatives)
    }

    @Test
    fun adopt_skippedSlot_staysExactlyAsPlanned() {
        var w = Workout.fromTemplate(template, 0L, variants = mapOf(0 to 1))
        w = w.setSkipped(w.exercises[0].id, true)
        w = w.addSet(w.exercises[1].id)
        val adopted = WorkoutPlan.adopt(template, w)
        assertEquals(template.exercises[0], adopted.exercises[0])
        assertEquals(2, adopted.exercises[1].sets.size)
    }

    @Test
    fun adopt_replacedVariantB_replacesOnlyThatVariant() {
        var w = Workout.fromTemplate(template, 0L, variants = mapOf(0 to 1))
        w = w.replaceExercise(w.exercises[0].id, cable)
        val adopted = WorkoutPlan.adopt(template, w)
        val slot = adopted.exercises[0]
        assertEquals("bench", slot.exercise.id)
        assertEquals(listOf(planned(50.0, 10), planned(50.0, 8)), slot.sets)
        assertEquals(listOf("cable"), slot.alternatives.map { it.exercise.id })
        // The sets of the replaced variant stay planned (same count).
        assertEquals(listOf(planned(reps = 12)), slot.alternatives.single().sets)
        assertEquals(listOf("bench", "squat"), adopted.exercises.map { it.exercise.id })
    }

    @Test
    fun adopt_replacedVariantA_replacesTheExercise_keepsAlternatives() {
        var w = Workout.fromTemplate(template, 0L)
        w = w.replaceExercise(w.exercises[0].id, cable)
        val slot = WorkoutPlan.adopt(template, w).exercises[0]
        assertEquals("cable", slot.exercise.id)
        assertEquals(listOf("db"), slot.alternatives.map { it.exercise.id })
        assertEquals(listOf(planned(50.0, 10), planned(50.0, 8)), slot.sets)
    }
}

class AlternativesGuardTest {
    private val a = app.overfit.workout.exercise("a")
    private val b = app.overfit.workout.exercise("b")

    @Test
    fun duplicateVariantsCollapse_andReplaceDropsTheChips() {
        val slot = app.overfit.workout.entry(a, null, app.overfit.workout.planned(10.0, 5)).copy(
            alternatives = listOf(app.overfit.workout.entry(a, null), app.overfit.workout.entry(b, null)),
        )
        kotlin.test.assertEquals(listOf("a", "b"), slot.variants.map { it.exercise.id })
        val w = Workout.fromTemplate(app.overfit.workout.templateOf(slot), 0)
        kotlin.test.assertEquals(2, w.exercises[0].variants.size)
        val replaced = w.replaceExercise(w.exercises[0].id, app.overfit.workout.exercise("c"))
        kotlin.test.assertTrue(replaced.exercises[0].variants.isEmpty())
    }

    @Test
    fun draftIgnoresTheSameExerciseAsAlternative() {
        val d = app.overfit.ui.templates.TemplateDraft().addExercises(listOf(a))
        val key = d.exercises[0].key
        kotlin.test.assertEquals(d, d.addAlternative(key, a))
        val withB = d.addAlternative(key, b)
        kotlin.test.assertEquals(withB, withB.addAlternative(key, b))
        kotlin.test.assertEquals(1, withB.exercises[0].alternatives.size)
    }
}
