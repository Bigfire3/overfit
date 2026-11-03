package app.overfit.coach

import app.overfit.domain.Exercise
import app.overfit.domain.Language
import app.overfit.domain.PlannedSet
import app.overfit.domain.Template
import app.overfit.domain.TrackingType
import app.overfit.domain.WeightUnit
import app.overfit.workout.entry
import app.overfit.workout.exercise
import app.overfit.workout.planned
import app.overfit.workout.templateOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PlanDiffTest {
    private val a = exercise("A")
    private val b = exercise("B", TrackingType.REPS)
    private val c = exercise("C", TrackingType.TIME)
    private val n = exercise("N")
    private val m = exercise("M", TrackingType.REPS)
    private val known = listOf(a, b, c, n, m).associateBy { it.id }
    private val lookup: (String) -> Exercise? = { known[it] }

    private val template: Template = templateOf(
        entry(a, 120, planned(90.0, 8), planned(90.0, 8), planned(90.0, 8)),
        entry(b, 60, planned(reps = 10), planned(reps = 10), planned(reps = 10)),
        entry(c, null, planned(duration = 45), planned(duration = 45), planned(duration = 45)),
    )

    private fun sug(id: String, rest: Int?, vararg sets: SuggestedSet, isNew: Boolean = false, reason: String? = null) =
        SuggestedExercise(id, isNew, rest, sets.toList(), reason)

    private val sameA get() = sug("A", 120, *Array(3) { SuggestedSet(90.0, 8, null) })
    private val sameB get() = sug("B", 60, *Array(3) { SuggestedSet(null, 10, null) })
    private val sameC get() = sug("C", null, *Array(3) { SuggestedSet(null, null, 45) })
    private fun plan(vararg e: SuggestedExercise) = PlanSuggestion(e.toList())

    // ---- diff ----

    @Test
    fun unchangedSuggestionIsEmpty() {
        val d = PlanDiffs.diff(template, plan(sameA, sameB, sameC), lookup)
        assertTrue(d.isEmpty)
        assertTrue(d.unknownIds.isEmpty())
    }

    @Test
    fun weightChangeIsModified() {
        val changed = sug("A", 120, *Array(3) { SuggestedSet(92.5, 8, null) }, reason = "up")
        val d = PlanDiffs.diff(template, plan(changed, sameB, sameC), lookup)
        val ch = assertIs<PlanChange.Modified>(d.changes.single())
        assertEquals(0, ch.index)
        assertEquals(template.exercises[0].sets, ch.before)
        assertEquals(List(3) { PlannedSet(92.5, 8, null) }, ch.after)
        assertTrue(ch.setsChanged)
        assertEquals("up", ch.reason)
    }

    @Test
    fun restChangeOnly() {
        val d = PlanDiffs.diff(template, plan(sameA, sug("B", 90, *Array(3) { SuggestedSet(null, 10, null) }), sameC), lookup)
        val ch = assertIs<PlanChange.Modified>(d.changes.single())
        assertEquals(1, ch.index)
        assertTrue(ch.restChanged)
        assertEquals(60, ch.restBefore)
        assertEquals(90, ch.restAfter)
        assertEquals(false, ch.setsChanged)
    }

    @Test
    fun missingExerciseIsRemoved() {
        val d = PlanDiffs.diff(template, plan(sameA, sameC), lookup)
        val ch = assertIs<PlanChange.Removed>(d.changes.single())
        assertEquals(1, ch.index)
        assertEquals("B", ch.exercise.id)
    }

    @Test
    fun newKnownExerciseIsAddedAfterPrecedingTemplateExercise() {
        val added = sug("N", 90, SuggestedSet(50.0, 10, null), isNew = true, reason = "new")
        val d = PlanDiffs.diff(template, plan(sameA, added, sameB, sameC), lookup)
        val ch = assertIs<PlanChange.Added>(d.changes.single())
        assertEquals(0, ch.afterIndex)
        assertEquals("N", ch.exercise.id)
        assertTrue(ch.markedNew)
        assertEquals(listOf(PlannedSet(50.0, 10, null)), ch.sets)
        assertEquals(90, ch.restSec)
    }

    @Test
    fun unknownIdGoesToUnknownIds() {
        val d = PlanDiffs.diff(template, plan(sameA, sug("ZZZ", null), sameB, sameC), lookup)
        assertTrue(d.isEmpty)
        assertEquals(listOf("ZZZ"), d.unknownIds)
    }

    @Test
    fun duplicateIdsMatchInOrder() {
        val dup = templateOf(
            entry(a, 120, planned(50.0, 10)),
            entry(a, 120, planned(60.0, 8)),
        )
        val unchanged = PlanDiffs.diff(dup, plan(sug("A", 120, SuggestedSet(50.0, 10, null)), sug("A", 120, SuggestedSet(60.0, 8, null))), lookup)
        assertTrue(unchanged.isEmpty)
        val d = PlanDiffs.diff(dup, plan(sug("A", 120, SuggestedSet(50.0, 10, null)), sug("A", 120, SuggestedSet(65.0, 8, null))), lookup)
        val ch = assertIs<PlanChange.Modified>(d.changes.single())
        assertEquals(1, ch.index)
    }

    @Test
    fun emptySuggestionSetsKeepTemplateSets() {
        val d = PlanDiffs.diff(template, plan(sug("A", 120), sameB, sameC), lookup)
        assertTrue(d.isEmpty)
    }

    @Test
    fun valuesAreFittedToTrackingType() {
        // B is a REPS exercise: weight and duration from the model are dropped, so nothing changes.
        val d = PlanDiffs.diff(template, plan(sameA, sug("B", 60, *Array(3) { SuggestedSet(20.0, 10, 30) }), sameC), lookup)
        assertTrue(d.isEmpty)
    }

    // ---- apply ----

    private val fullSuggestion = plan(
        sug("N", 90, SuggestedSet(50.0, 10, null), isNew = true),
        sug("A", 120, *Array(3) { SuggestedSet(92.5, 8, null) }),
        sameC,
        sug("M", null, SuggestedSet(20.0, 12, null), isNew = true),
    )

    @Test
    fun acceptAllEqualsSuggestion() {
        val d = PlanDiffs.diff(template, fullSuggestion, lookup)
        val result = PlanDiffs.apply(template, d, d.changes.map { it.key }.toSet())
        assertEquals(listOf("N", "A", "C", "M"), result.exercises.map { it.exercise.id })
        assertEquals(List(3) { PlannedSet(92.5, 8, null) }, result.exercises[1].sets)
        assertEquals(listOf(PlannedSet(50.0, 10, null)), result.exercises[0].sets)
        // M is a REPS exercise, so the suggested weight is dropped.
        assertEquals(listOf(PlannedSet(null, 12, null)), result.exercises[3].sets)
        assertEquals(template.id, result.id)
    }

    @Test
    fun acceptSubsetOnlyAppliesChosen() {
        val d = PlanDiffs.diff(template, fullSuggestion, lookup)
        val modKey = d.changes.filterIsInstance<PlanChange.Modified>().single().key
        val result = PlanDiffs.apply(template, d, setOf(modKey))
        assertEquals(listOf("A", "B", "C"), result.exercises.map { it.exercise.id })
        assertEquals(List(3) { PlannedSet(92.5, 8, null) }, result.exercises[0].sets)
        assertEquals(template.exercises[1], result.exercises[1])
        assertEquals(template.exercises[2], result.exercises[2])
    }

    @Test
    fun acceptRemoved() {
        val d = PlanDiffs.diff(template, plan(sameA, sameC), lookup)
        val result = PlanDiffs.apply(template, d, d.changes.map { it.key }.toSet())
        assertEquals(listOf("A", "C"), result.exercises.map { it.exercise.id })
    }

    @Test
    fun acceptAddedAtStartAndInMiddle() {
        val s = plan(sug("N", null, SuggestedSet(50.0, 10, null)), sameA, sug("M", null, SuggestedSet(null, 12, null)), sameB, sameC)
        val d = PlanDiffs.diff(template, s, lookup)
        val added = d.changes.filterIsInstance<PlanChange.Added>()
        assertEquals(listOf(-1, 0), added.map { it.afterIndex })
        val result = PlanDiffs.apply(template, d, d.changes.map { it.key }.toSet())
        assertEquals(listOf("N", "A", "M", "B", "C"), result.exercises.map { it.exercise.id })
        // only the middle one
        val onlyMiddle = PlanDiffs.apply(template, d, setOf(added[1].key))
        assertEquals(listOf("A", "M", "B", "C"), onlyMiddle.exercises.map { it.exercise.id })
    }

    @Test
    fun acceptNoneKeepsTemplate() {
        val d = PlanDiffs.diff(template, fullSuggestion, lookup)
        assertEquals(template, PlanDiffs.apply(template, d, emptySet()))
    }

    // ---- summary ----

    private fun eq(n: Int, w: Double?, r: Int?, d: Int?) = List(n) { PlannedSet(w, r, d) }

    @Test
    fun summaryEqualSets() {
        assertEquals("3 × 8 · 90 kg", PlanDiffs.summary(eq(3, 90.0, 8, null), TrackingType.WEIGHT_REPS, WeightUnit.KG, Language.EN))
        assertEquals("3 × 8 · 92,5 kg", PlanDiffs.summary(eq(3, 92.5, 8, null), TrackingType.WEIGHT_REPS, WeightUnit.KG, Language.DE))
    }

    @Test
    fun summaryDifferingSets() {
        val sets = listOf(PlannedSet(90.0, 8, null), PlannedSet(92.5, 6, null))
        assertEquals("90 kg × 8 · 92.5 kg × 6", PlanDiffs.summary(sets, TrackingType.WEIGHT_REPS, WeightUnit.KG, Language.EN))
    }

    @Test
    fun summaryPoundsAndOtherTypes() {
        assertEquals("2 × 5 · 220.5 lb", PlanDiffs.summary(eq(2, 100.0, 5, null), TrackingType.WEIGHT_REPS, WeightUnit.LB, Language.EN))
        assertEquals("3 × 45 s", PlanDiffs.summary(eq(3, null, null, 45), TrackingType.TIME, WeightUnit.KG, Language.EN))
        assertEquals("3 × 10", PlanDiffs.summary(eq(3, null, 10, null), TrackingType.REPS, WeightUnit.KG, Language.EN))
        assertEquals("–", PlanDiffs.summary(emptyList(), TrackingType.REPS, WeightUnit.KG, Language.EN))
    }

    // ---- toTemplate ----

    @Test
    fun toTemplateSkipsUnknownIds() {
        val t = PlanDiffs.toTemplate("New", PlanSuggestion(listOf(sameA, sug("ZZZ", null, SuggestedSet(null, 5, null)), sug("B", 60)), note = "hi"), lookup)
        assertEquals(listOf("A", "B"), t.exercises.map { it.exercise.id })
        assertEquals("New", t.name)
        assertEquals("hi", t.note)
        assertEquals(3, t.exercises[1].sets.size) // empty sets -> 3 default sets
    }
}
