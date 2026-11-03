package app.overfit.coach

import app.overfit.domain.Language
import app.overfit.domain.SetValues
import app.overfit.domain.TrackingType
import app.overfit.i18n.coachStringsFor
import app.overfit.workout.entry
import app.overfit.workout.exercise
import app.overfit.workout.planned
import app.overfit.workout.templateOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class CoachDemoTest {
    private val t = coachStringsFor(Language.EN)

    @Test
    fun firstWeightExerciseGetsBump() {
        val tpl = templateOf(
            entry(exercise("pushups", TrackingType.REPS), 60, planned(reps = 15)),
            entry(exercise("bench"), 120, planned(80.0, 8), planned(80.0, 8)),
            entry(exercise("press"), 90, planned(40.0, 8)),
        )
        val plan = assertNotNull(CoachDemo.review("Push", tpl, t).nextSession)
        val ex = plan.exercises
        assertEquals(listOf("pushups", "bench", "press"), ex.map { it.exerciseId })
        assertEquals(listOf(SuggestedSet(null, 15, null)), ex[0].sets)
        assertNull(ex[0].reason)
        assertEquals(listOf(82.5, 82.5), ex[1].sets.map { it.weightKg })
        assertEquals(t.demoReason, ex[1].reason)
        assertEquals(listOf(40.0), ex[2].sets.map { it.weightKg })
        assertNull(ex[2].reason)
        assertEquals(120, ex[1].restSec)
    }

    @Test
    fun noPositiveWeightMeansNoBump() {
        val tpl = templateOf(entry(exercise("bw"), 60, planned(0.0, 8)))
        val ex = assertNotNull(CoachDemo.review("x", tpl, t).nextSession).exercises.single()
        assertEquals(0.0, ex.sets.single().weightKg)
        assertNull(ex.reason)
    }

    @Test
    fun nullTemplateGivesNoPlan() {
        assertNull(CoachDemo.review("Free", null, t).nextSession)
    }

    private fun comparison(id: String, type: TrackingType, best: SetValues, verdict: Verdict?): ExerciseComparison {
        val stats = ExerciseStats("2026-11-03", best, 3, 0.0, null, best.reps, best.durationSec, 1.0)
        return ExerciseComparison(id, "Ex $id", type, stats, null, verdict)
    }

    @Test
    fun comparisonsBecomeExerciseReviewsWithLocalVerdict() {
        val list = listOf(
            comparison("a", TrackingType.WEIGHT_REPS, SetValues(80.0, 8, null), Verdict.BETTER),
            comparison("b", TrackingType.REPS, SetValues(null, 10, null), Verdict.WORSE),
            comparison("c", TrackingType.TIME, SetValues(null, null, 60), null),
        )
        val r = CoachDemo.review("Push", null, t, list).exerciseReviews
        assertEquals(listOf("a", "b", "c"), r.map { it.exerciseId })
        assertEquals(listOf(Verdict.BETTER, Verdict.WORSE, Verdict.SAME), r.map { it.verdict })
        assertEquals(listOf(SuggestedSet(82.5, 8, null), SuggestedSet(null, 10, null), SuggestedSet(null, null, 65)), r.map { it.next })
        assertEquals(t.demoExerciseComment(Verdict.BETTER), r[0].comment)
        assertEquals(t.demoExerciseComment(null), r[2].comment)
    }

    @Test
    fun noComparisonsMeansNoExerciseReviews() {
        assertEquals(emptyList(), CoachDemo.review("Push", null, t).exerciseReviews)
    }
}
