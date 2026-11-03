package app.overfit.ui.templates

import app.overfit.domain.Exercise
import app.overfit.domain.ExerciseSource
import app.overfit.domain.Language
import app.overfit.domain.PlannedSet
import app.overfit.domain.TrackingType
import app.overfit.domain.WeightUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class TemplateDraftTest {
    private fun ex(id: String, type: TrackingType = TrackingType.WEIGHT_REPS, rest: Int? = null) = Exercise(
        id = id, name = id, source = ExerciseSource.WORKOUT_GUIDE, imageRef = null,
        primaryMuscles = emptyList(), secondaryMuscles = emptyList(), equipment = null,
        trackingType = type, defaultRestSec = rest,
    )

    @Test
    fun addExercisesCreatesDefaultSetsAndTakesExerciseRest() {
        val d = TemplateDraft().addExercises(listOf(ex("a", rest = 120), ex("b")))
        assertEquals(listOf("a", "b"), d.exercises.map { it.exercise.id })
        assertEquals(TemplateDraft.DEFAULT_SETS, d.exercises[0].sets.size)
        assertEquals("120", d.exercises[0].rest)
        assertEquals("", d.exercises[1].rest)
        // Alle Schlüssel eindeutig (Compose-Keys).
        val keys = d.exercises.map { it.key } + d.exercises.flatMap { e -> e.sets.map { it.key } }
        assertEquals(keys.size, keys.toSet().size)
    }

    @Test
    fun addSetCopiesLastValues() {
        var d = TemplateDraft().addExercises(listOf(ex("a")))
        val e = d.exercises[0]
        d = d.updateSet(e.key, e.sets.last().key) { it.copy(weight = "92,5", reps = "5") }
        d = d.addSet(e.key)
        val sets = d.exercises[0].sets
        assertEquals(4, sets.size)
        assertEquals("92,5", sets.last().weight)
        assertEquals("5", sets.last().reps)
        assertEquals(sets.size, sets.map { it.key }.toSet().size)
    }

    @Test
    fun moveRemoveReplace() {
        var d = TemplateDraft().addExercises(listOf(ex("a"), ex("b"), ex("c")))
        val (a, b, c) = d.exercises.map { it.key }
        d = d.moveExercise(c, -1)
        assertEquals(listOf("a", "c", "b"), d.exercises.map { it.exercise.id })
        d = d.moveExercise(a, -1) // schon oben → unverändert
        assertEquals(listOf("a", "c", "b"), d.exercises.map { it.exercise.id })
        d = d.replaceExercise(b, ex("z"))
        assertEquals(listOf("a", "c", "z"), d.exercises.map { it.exercise.id })
        assertEquals(TemplateDraft.DEFAULT_SETS, d.exercises[2].sets.size)
        d = d.removeExercise(a)
        assertEquals(listOf("c", "z"), d.exercises.map { it.exercise.id })
    }

    @Test
    fun toTemplateParsesOnlyFieldsOfTrackingType() {
        var d = TemplateDraft(name = "  Push  ").addExercises(listOf(ex("bench"), ex("hold", TrackingType.TIME)))
        val bench = d.exercises[0]
        val hold = d.exercises[1]
        d = d.updateSet(bench.key, bench.sets[0].key) { it.copy(weight = "-10,5", reps = "8", duration = "30") }
        d = d.updateSet(hold.key, hold.sets[0].key) { it.copy(weight = "5", duration = "1:30") }
        d = d.setRest(bench.key, "90s")
        val t = d.toTemplate()
        assertEquals("Push", t.name)
        assertEquals(PlannedSet(weightKg = -10.5, reps = 8), t.exercises[0].sets[0])
        assertEquals(PlannedSet(), t.exercises[0].sets[1])
        assertEquals(90, t.exercises[0].restSec)
        assertEquals(PlannedSet(durationSec = 90), t.exercises[1].sets[0])
    }

    @Test
    fun validation() {
        val kg = WeightUnit.KG
        assertEquals(TemplateDraft.Problem.MissingName, TemplateDraft().validate(kg))
        var d = TemplateDraft(name = "A").addExercises(listOf(ex("a")))
        assertNull(d.validate(kg))
        val e = d.exercises[0]
        d = d.updateSet(e.key, e.sets[1].key) { it.copy(weight = "abc") }
        val problem = d.validate(kg)
        assertIs<TemplateDraft.Problem.InvalidValue>(problem)
        assertEquals("a", problem.exercise)
        assertEquals(2, problem.set)
    }

    @Test
    fun roundTripFromTemplate() {
        var d = TemplateDraft(name = "Pull").addExercises(listOf(ex("row")))
        val e = d.exercises[0]
        d = d.updateSet(e.key, e.sets[0].key) { it.copy(weight = "92.5", reps = "6") }
        val t = d.toTemplate(WeightUnit.KG).copy(id = 7)
        val back = TemplateDraft.from(t, WeightUnit.KG, Language.DE)
        assertEquals(7, back.id)
        assertEquals("92,5", back.exercises[0].sets[0].weight)
        assertEquals(t, back.toTemplate(WeightUnit.KG))
        assertEquals("92.5", TemplateDraft.from(t, WeightUnit.KG, Language.EN).exercises[0].sets[0].weight)
    }

    @Test
    fun poundsAreStoredAsKgAndShownAsPounds() {
        var d = TemplateDraft(name = "Lift").addExercises(listOf(ex("squat")))
        val e = d.exercises[0]
        d = d.updateSet(e.key, e.sets[0].key) { it.copy(weight = "225", reps = "5") }
        assertNull(d.validate(WeightUnit.LB))
        val t = d.toTemplate(WeightUnit.LB)
        assertEquals(102.0583, t.exercises[0].sets[0].weightKg!!, 1e-9)
        val back = TemplateDraft.from(t, WeightUnit.LB, Language.EN)
        assertEquals("225", back.exercises[0].sets[0].weight)
        assertEquals(t, back.toTemplate(WeightUnit.LB))
    }
}
