package app.overfit.ui.templates

import app.overfit.domain.Exercise
import app.overfit.domain.ExerciseSource
import app.overfit.domain.PlannedSet
import app.overfit.domain.Template
import app.overfit.domain.TemplateExercise
import app.overfit.domain.TrackingType
import app.overfit.domain.WeightUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TemplateDraftAlternativesTest {
    private fun ex(id: String, type: TrackingType = TrackingType.WEIGHT_REPS, rest: Int? = null) = Exercise(
        id = id, name = id, source = ExerciseSource.WORKOUT_GUIDE, imageRef = null,
        primaryMuscles = emptyList(), secondaryMuscles = emptyList(), equipment = null,
        trackingType = type, defaultRestSec = rest,
    )

    private val template = Template(
        id = 4, name = "Push", note = null,
        exercises = listOf(
            TemplateExercise(
                ex("a"), 60, "n", listOf(PlannedSet(50.0, 10)),
                alternatives = listOf(
                    TemplateExercise(ex("b"), 90, "alt", listOf(PlannedSet(20.0, 12), PlannedSet(22.5, 10))),
                    TemplateExercise(ex("c", TrackingType.TIME), null, null, listOf(PlannedSet(durationSec = 30))),
                ),
            ),
            TemplateExercise(ex("d"), null, null, listOf(PlannedSet(reps = 5))),
        ),
    )

    @Test
    fun roundTripKeepsAlternatives() {
        val d = TemplateDraft.from(template)
        assertEquals(listOf("b", "c"), d.exercises[0].alternatives.map { it.exercise.id })
        assertEquals(template, d.toTemplate())
        // Keys stay unique across exercises, alternatives and sets (Compose keys).
        val keys = d.allExercises.map { it.key } + d.allExercises.flatMap { e -> e.sets.map { it.key } }
        assertEquals(keys.size, keys.toSet().size)
    }

    @Test
    fun addAlternativeAndEditIt_thenRemoveOnlyTheAlternative() {
        var d = TemplateDraft().addExercises(listOf(ex("a"), ex("b")))
        val a = d.exercises[0].key
        d = d.addAlternative(a, ex("x", rest = 45))
        val alt = d.exercises[0].alternatives.single()
        assertEquals("x", alt.exercise.id)
        assertEquals("45", alt.rest)
        assertEquals(TemplateDraft.DEFAULT_SETS, alt.sets.size)
        val keys = d.allExercises.map { it.key } + d.allExercises.flatMap { e -> e.sets.map { it.key } }
        assertEquals(keys.size, keys.toSet().size)

        // Editing the alternative changes only the alternative.
        d = d.updateSet(alt.key, alt.sets[0].key) { it.copy(weight = "20", reps = "8") }
        d = d.addSet(alt.key)
        d = d.replaceExercise(alt.key, ex("y"))
        val edited = d.exercises[0].alternatives.single()
        assertEquals("y", edited.exercise.id)
        assertEquals(4, edited.sets.size)
        assertEquals("20", edited.sets[0].weight)
        assertEquals(TemplateDraft.DEFAULT_SETS, d.exercises[0].sets.size)
        assertEquals("a", d.exercises[0].exercise.id)
        val t = d.withName("T").toTemplate()
        assertEquals(PlannedSet(20.0, 8), t.exercises[0].alternatives.single().sets[0])

        d = d.removeExercise(edited.key)
        assertEquals(listOf("a", "b"), d.exercises.map { it.exercise.id })
        assertTrue(d.exercises[0].alternatives.isEmpty())
    }

    @Test
    fun removingTheExerciseRemovesItsAlternatives() {
        var d = TemplateDraft.from(template)
        d = d.removeExercise(d.exercises[0].key)
        assertEquals(listOf("d"), d.allExercises.map { it.exercise.id })
    }

    @Test
    fun validateCoversAlternatives() {
        var d = TemplateDraft.from(template)
        assertNull(d.validate(WeightUnit.KG))
        val alt = d.exercises[0].alternatives[0]
        d = d.updateSet(alt.key, alt.sets[1].key) { it.copy(weight = "abc") }
        val p = assertIs<TemplateDraft.Problem.InvalidValue>(d.validate(WeightUnit.KG))
        assertEquals("b", p.exercise)
        assertEquals(2, p.set)
    }
}
