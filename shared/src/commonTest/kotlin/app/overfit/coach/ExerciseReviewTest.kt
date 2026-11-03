package app.overfit.coach

import app.overfit.domain.ExerciseOrigin
import app.overfit.domain.SetValues
import app.overfit.domain.TrackingType
import app.overfit.domain.WorkoutExercise
import app.overfit.domain.WorkoutSet
import app.overfit.workout.exercise
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExerciseReviewTest {
    private val bench = exercise("wg:bench", name = "Bench Press")
    private val pushups = exercise("wg:pushups", TrackingType.REPS, name = "Push-ups")
    private val plank = exercise("wg:plank", TrackingType.TIME, name = "Plank")
    private val dips = exercise("wg:dips", name = "Dips")

    private fun w(weight: Double?, reps: Int?) = SetValues(weight, reps, null)
    private fun secs(s: Int) = SetValues(null, null, s)
    private fun done(vararg v: SetValues) = v.mapIndexed { i, x -> WorkoutSet("s$i", x, x, true, 1L + i) }
    private fun ex(e: app.overfit.domain.Exercise, sets: List<WorkoutSet>, id: String = "we-${e.id}") =
        WorkoutExercise(id, e, null, "", ExerciseOrigin.PLANNED, null, sets)

    private fun session(id: String, start: Long, vararg e: WorkoutExercise) =
        SessionRecord(id, "Push", 7, start, start + 3_600_000, null, null, null, e.toList())

    private val dateOf: (Long) -> String = { if (it == 2000L) "2026-11-03" else "2026-10-27" }

    private suspend fun compare(today: List<WorkoutExercise>, last: SessionRecord?): List<ExerciseComparison> =
        ExerciseReviews.compare(session("cur", 2000L, *today.toTypedArray()), dateOf) { last }

    private fun lastWith(vararg e: WorkoutExercise) = session("old", 1000L, *e)

    private suspend fun single(today: WorkoutExercise, last: WorkoutExercise?): ExerciseComparison =
        compare(listOf(today), last?.let { lastWith(it) }).single()

    @Test
    fun moreE1rmIsBetter() = runTest {
        val c = single(ex(bench, done(w(82.5, 8))), ex(bench, done(w(80.0, 8))))
        assertEquals(Verdict.BETTER, c.verdict)
        assertEquals(Verdict.BETTER, c.localVerdict)
        assertEquals("2026-10-27", c.last!!.date)
        assertEquals("2026-11-03", c.today.date)
        assertEquals(w(82.5, 8), c.today.bestSet)
    }

    @Test
    fun withinOnePercentIsSame() = runTest {
        // e1RM 101.33 vs 101.83: difference 0.5 < 1 %.
        val c = single(ex(bench, done(w(80.0, 8))), ex(bench, done(w(80.5, 8))))
        assertEquals(Verdict.SAME, c.verdict)
        assertEquals(Verdict.SAME, single(ex(bench, done(w(80.0, 8))), ex(bench, done(w(80.0, 8)))).verdict)
    }

    @Test
    fun lessE1rmIsWorse() = runTest {
        val c = single(ex(bench, done(w(77.5, 8))), ex(bench, done(w(80.0, 8))))
        assertEquals(Verdict.WORSE, c.verdict)
    }

    @Test
    fun bodyWeightOneMoreRepIsBetter() = runTest {
        assertEquals(Verdict.BETTER, single(ex(pushups, done(SetValues(null, 11, null))), ex(pushups, done(SetValues(null, 10, null)))).verdict)
        assertEquals(Verdict.WORSE, single(ex(pushups, done(SetValues(null, 9, null))), ex(pushups, done(SetValues(null, 10, null)))).verdict)
        assertEquals(Verdict.SAME, single(ex(pushups, done(SetValues(null, 10, null))), ex(pushups, done(SetValues(null, 10, null)))).verdict)
        // weighted exercise without weight (body weight dips)
        assertEquals(Verdict.BETTER, single(ex(dips, done(w(0.0, 11))), ex(dips, done(w(0.0, 10)))).verdict)
    }

    @Test
    fun timedExerciseUsesSeconds() = runTest {
        val c = single(ex(plank, done(secs(65))), ex(plank, done(secs(60))))
        assertEquals(Verdict.BETTER, c.verdict)
        assertEquals(5, c.durationDelta)
        assertEquals(Verdict.WORSE, single(ex(plank, done(secs(55))), ex(plank, done(secs(60)))).verdict)
        assertEquals(-5, single(ex(plank, done(secs(55))), ex(plank, done(secs(60)))).durationDelta)
    }

    @Test
    fun firstTimeHasNoVerdictAndNoDeltas() = runTest {
        val c = single(ex(bench, done(w(80.0, 8))), null)
        assertNull(c.last)
        assertNull(c.localVerdict)
        assertNull(c.verdict)
        assertNull(c.volumeDelta)
        assertNull(c.e1rmDelta)
        assertNull(c.repsDelta)
        assertNull(c.durationDelta)
        // earlier session exists but without completed sets of this exercise
        val c2 = single(ex(bench, done(w(80.0, 8))), ex(bench, listOf(WorkoutSet("x", w(80.0, 8), w(80.0, 8), false))))
        assertNull(c2.last)
        assertNull(c2.verdict)
    }

    @Test
    fun exerciseWithoutCompletedSetsIsLeftOut() = runTest {
        val open = ex(dips, listOf(WorkoutSet("x", w(10.0, 8), w(10.0, 8), false)))
        val list = compare(listOf(open, ex(bench, done(w(80.0, 8)))), null)
        assertEquals(listOf("wg:bench"), list.map { it.exerciseId })
        assertTrue(compare(listOf(open), null).isEmpty())
    }

    @Test
    fun duplicateExerciseIsMergedInWorkoutOrder() = runTest {
        val list = compare(
            listOf(
                ex(bench, done(w(80.0, 8)), "a"),
                ex(dips, done(w(10.0, 10)), "b"),
                ex(bench, done(w(85.0, 5), w(70.0, 10)), "c"),
            ),
            null,
        )
        assertEquals(listOf("wg:bench", "wg:dips"), list.map { it.exerciseId })
        val b = list[0]
        assertEquals(3, b.today.setsDone)
        assertEquals(w(80.0, 8), b.today.bestSet) // e1RM 101.3 beats 85×5 (99.2) and 70×10 (93.3)
        assertEquals(640.0 + 425.0 + 700.0, b.today.volumeKg)
        assertEquals(1, list[1].today.setsDone)
    }

    @Test
    fun sameTopResultButMoreVolumeIsBetter() = runTest {
        val last = ex(bench, done(w(80.0, 8), w(40.0, 10))) // 640 + 400 = 1040
        val today = ex(bench, done(w(80.0, 8), w(40.0, 12))) // 640 + 480 = 1120 (+7.7 %)
        val c = single(today, last)
        assertEquals(1120.0, c.today.volumeKg)
        assertEquals(Verdict.BETTER, c.verdict)
        assertEquals(80.0, c.volumeDelta)
        // mirrored: less volume at the same top result
        assertEquals(Verdict.WORSE, single(last, today).verdict)
        // within 5 % volume stays same
        assertEquals(Verdict.SAME, single(ex(bench, done(w(80.0, 8), w(40.0, 10))), ex(bench, done(w(80.0, 8), w(40.0, 10)))).verdict)
    }

    @Test
    fun deltas() = runTest {
        val c = single(ex(bench, done(w(82.5, 8), w(82.5, 6))), ex(bench, done(w(80.0, 8), w(80.0, 5))))
        assertEquals(82.5 * 14 - 80.0 * 13, c.volumeDelta!!, 1e-9)
        assertEquals(82.5 * (1 + 8 / 30.0) - 80.0 * (1 + 8 / 30.0), c.e1rmDelta!!, 1e-9)
        assertEquals(0, c.repsDelta) // 8 vs 8
        assertNull(c.durationDelta)
        val d = single(ex(pushups, done(SetValues(null, 12, null))), ex(pushups, done(SetValues(null, 10, null))))
        assertEquals(2, d.repsDelta)
        assertNull(d.volumeDelta) // no weight on either side
        assertNull(d.e1rmDelta)
    }

    @Test
    fun withFeedbackOverridesVerdictAndIgnoresUnknownIds() = runTest {
        val list = compare(listOf(ex(bench, done(w(82.5, 8))), ex(dips, done(w(10.0, 8)))), lastWith(ex(bench, done(w(80.0, 8)))))
        val fb = listOf(
            ExerciseFeedback("wg:bench", Verdict.WORSE, "More reps elsewhere", SuggestedSet(85.0, 8, null)),
            ExerciseFeedback("wg:unknown", Verdict.BETTER, null, null),
        )
        val merged = ExerciseReviews.withFeedback(list, fb)
        assertEquals(2, merged.size)
        val bench = merged.first { it.exerciseId == "wg:bench" }
        assertEquals(Verdict.WORSE, bench.verdict)
        assertEquals(Verdict.BETTER, bench.localVerdict)
        assertEquals(SuggestedSet(85.0, 8, null), bench.ai!!.next)
        assertNull(merged.first { it.exerciseId == "wg:dips" }.ai)
        // coach feedback without a verdict falls back to the local one
        val nullVerdict = ExerciseReviews.withFeedback(list, listOf(ExerciseFeedback("wg:bench", null, "c", null)))
        assertEquals(Verdict.BETTER, nullVerdict.first().verdict)
    }

    @Test
    fun simpleNextByType() = runTest {
        suspend fun next(e: WorkoutExercise, last: WorkoutExercise?) = ExerciseReviews.simpleNext(single(e, last))
        assertEquals(SuggestedSet(82.5, 8, null), next(ex(bench, done(w(80.0, 8))), null)) // first time: up
        assertEquals(SuggestedSet(82.5, 8, null), next(ex(bench, done(w(80.0, 8))), ex(bench, done(w(80.0, 8))))) // same: up
        assertEquals(SuggestedSet(77.5, 8, null), next(ex(bench, done(w(77.5, 8))), ex(bench, done(w(80.0, 8))))) // worse: repeat
        assertEquals(SuggestedSet(null, 11, null), next(ex(pushups, done(SetValues(null, 10, null))), null))
        assertEquals(SuggestedSet(null, 10, null), next(ex(pushups, done(SetValues(null, 10, null))), ex(pushups, done(SetValues(null, 12, null)))))
        assertEquals(SuggestedSet(null, null, 65), next(ex(plank, done(secs(60))), null))
        assertEquals(SuggestedSet(0.0, 9, null), next(ex(dips, done(w(0.0, 8))), null)) // no weight: +1 rep
    }

    @Test
    fun promptLinesShowTodayLastAndDate() = runTest {
        val c = single(ex(bench, done(w(82.5, 8), w(82.5, 7))), ex(bench, done(w(80.0, 8))))
        val line = ExerciseReviews.promptLines(listOf(c)).single()
        assertTrue(line.startsWith("Bench Press [wg:bench]: today "), line)
        assertTrue(line.contains("best 82.5kg×8"), line)
        assertTrue(line.contains("2 sets"), line)
        assertTrue(line.contains("| last 2026-10-27: best 80kg×8"), line)
        assertTrue(line.endsWith("by the numbers: better"), line)

        val first = ExerciseReviews.promptLines(listOf(single(ex(bench, done(w(80.0, 8))), null))).single()
        assertTrue(first.contains("last first time"), first)
        assertTrue(first.endsWith("by the numbers: n/a"), first)
    }
}
