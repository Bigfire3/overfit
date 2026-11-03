package app.overfit.workout

import app.overfit.domain.ExerciseOrigin
import app.overfit.domain.RestTimer
import app.overfit.domain.SetValues
import app.overfit.domain.TrackingType
import app.overfit.domain.Workout
import app.overfit.domain.WorkoutExercise
import app.overfit.domain.WorkoutSet
import app.overfit.domain.fitTo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class WorkoutModelTest {
    private fun counter(prefix: String = "id"): () -> String {
        var n = 0
        return { "$prefix${n++}" }
    }

    private fun simple(): Workout {
        val bench = exercise("bench")
        val squat = exercise("squat")
        val t = templateOf(
            entry(bench, 60, planned(50.0, 10), planned(50.0, 8)),
            entry(squat, null, planned(80.0, 5)),
        )
        return Workout.fromTemplate(t, now = 1000, id = "w", idGen = counter())
    }

    // --- fromTemplate / added ------------------------------------------------------------------

    @Test
    fun fromTemplate_takesTargetsAndStartsActualAsTarget() {
        val w = simple()
        assertEquals("w", w.id)
        assertEquals("Push", w.name)
        assertEquals(7L, w.templateId)
        assertEquals(1000L, w.startedAt)
        assertNull(w.rest)
        assertEquals(listOf("bench", "squat"), w.exercises.map { it.exercise.id })
        val bench = w.exercises[0]
        assertEquals(60, bench.restSec)
        assertEquals(ExerciseOrigin.PLANNED, bench.origin)
        assertEquals(listOf(planned(50.0, 10), planned(50.0, 8)), bench.sets.map { it.target })
        assertEquals(bench.sets.map { it.target }, bench.sets.map { it.actual })
        assertTrue(w.exercises.flatMap { it.sets }.none { it.completed })
        assertEquals(3, w.totalSets)
        assertEquals(0, w.completedSets)
    }

    @Test
    fun fromTemplate_fillsGapsFromLastTimeByPosition() {
        val bench = exercise("bench")
        val t = templateOf(entry(bench, null, planned(weight = 50.0), planned(), planned(reps = 6)))
        val last = mapOf("bench" to listOf(planned(40.0, 12), planned(45.0, 10), planned(47.5, 9)))
        val w = Workout.fromTemplate(t, 0, last, idGen = counter())
        val targets = w.exercises.single().sets.map { it.target }
        // Template value wins, gaps come from the same set position last time.
        assertEquals(listOf(planned(50.0, 12), planned(45.0, 10), planned(47.5, 6)), targets)
    }

    @Test
    fun fromTemplate_lastTimeShorterThanTemplateLeavesGapsEmpty() {
        val t = templateOf(entry(exercise("bench"), null, planned(), planned()))
        val w = Workout.fromTemplate(t, 0, mapOf("bench" to listOf(planned(40.0, 12))), idGen = counter())
        assertEquals(listOf(planned(40.0, 12), planned()), w.exercises.single().sets.map { it.target })
    }

    @Test
    fun fromTemplate_fitsValuesToTrackingType() {
        val plank = exercise("plank", TrackingType.TIME)
        val t = templateOf(entry(plank, null, planned(weight = 20.0, reps = 5, duration = 45)))
        val w = Workout.fromTemplate(t, 0, mapOf("plank" to listOf(planned(1.0, 1, 1))), idGen = counter())
        val s = w.exercises.single().sets.single()
        assertEquals(planned(duration = 45), s.target)
        assertEquals(planned(duration = 45), s.actual)
    }

    @Test
    fun fromTemplate_usesTemplateNoteAndUniqueIds() {
        val t = templateOf(
            app.overfit.domain.TemplateExercise(exercise("a"), null, "careful", listOf(planned(1.0, 1), planned(1.0, 1))),
        )
        val w = Workout.fromTemplate(t, 0, idGen = counter())
        assertEquals("careful", w.exercises.single().note)
        val ids = w.exercises.map { it.id } + w.exercises.flatMap { it.sets }.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun added_usesLastTimeSets() {
        val e = Workout.added(exercise("bench"), listOf(planned(60.0, 8), planned(60.0, 6)), counter())
        assertEquals(ExerciseOrigin.ADDED, e.origin)
        assertNull(e.restSec)
        assertEquals(listOf(planned(60.0, 8), planned(60.0, 6)), e.sets.map { it.target })
        assertEquals(e.sets.map { it.target }, e.sets.map { it.actual })
    }

    @Test
    fun added_withoutLastTimeGivesThreeEmptySets() {
        val e = Workout.added(exercise("bench"), emptyList(), counter())
        assertEquals(Workout.DEFAULT_SETS, e.sets.size)
        assertEquals(3, e.sets.size)
        assertTrue(e.sets.all { it.target == SetValues() && it.actual == SetValues() && !it.completed })
    }

    @Test
    fun added_fitsLastTimeToTrackingType() {
        val e = Workout.added(exercise("run", TrackingType.TIME), listOf(planned(10.0, 5, 60)), counter())
        assertEquals(planned(duration = 60), e.sets.single().target)
    }

    // --- Sets ----------------------------------------------------------------------------------

    @Test
    fun completeSet_fillsEmptyActualFieldsFromTarget() {
        val w = Workout.empty("x", 0).addExercise(
            WorkoutExercise(
                "e", exercise("a"), null, "", ExerciseOrigin.PLANNED,
                sets = listOf(WorkoutSet("s", target = planned(50.0, 10), actual = planned(weight = 55.0))),
            ),
        )
        val done = w.completeSet("s", now = 5000)
        val s = done.exercises.single().sets.single()
        assertTrue(s.completed)
        assertEquals(5000L, s.completedAt)
        // User-entered weight stays, missing reps come from the target.
        assertEquals(planned(55.0, 10), s.actual)
        assertEquals(1, done.completedSets)
    }

    @Test
    fun uncompleteSet_clearsCompletionButKeepsValues() {
        val w = simple().completeSet("id1", 10).uncompleteSet("id1")
        val s = w.exercises[0].sets[0]
        assertFalse(s.completed)
        assertNull(s.completedAt)
        assertEquals(planned(50.0, 10), s.actual)
    }

    @Test
    fun addSet_copiesLastSetNotCompleted() {
        val w = simple().completeSet("id2", 1).addSet("id0", "new")
        // ids: entry id0 with sets id1, id2; entry id3 with set id4
        val sets = w.exercises[0].sets
        assertEquals(3, sets.size)
        val added = sets.last()
        assertEquals("new", added.id)
        assertEquals(planned(50.0, 8), added.target)
        assertEquals(planned(50.0, 8), added.actual)
        assertFalse(added.completed)
        assertNull(added.completedAt)
    }

    @Test
    fun addSet_toExerciseWithoutSetsGivesEmptySet() {
        val w = Workout.empty("x", 0).addExercise(
            WorkoutExercise("e", exercise("a"), null, "", ExerciseOrigin.ADDED, sets = emptyList()),
        ).addSet("e", "n")
        assertEquals(listOf(WorkoutSet("n")), w.exercises.single().sets)
    }

    @Test
    fun duplicateSet_insertsDirectlyBelowNotCompleted() {
        val base = simple()
        val firstSetId = base.exercises[0].sets[0].id
        val w = base.completeSet(firstSetId, 99).duplicateSet(firstSetId, "dup")
        val sets = w.exercises[0].sets
        assertEquals(listOf(firstSetId, "dup", base.exercises[0].sets[1].id), sets.map { it.id })
        assertTrue(sets[0].completed)
        assertFalse(sets[1].completed)
        assertNull(sets[1].completedAt)
        assertEquals(sets[0].actual, sets[1].actual)
        assertEquals(sets[0].target, sets[1].target)
        // Other exercises untouched.
        assertEquals(1, w.exercises[1].sets.size)
    }

    @Test
    fun removeSet_removesOnlyThatSet() {
        val base = simple()
        val id = base.exercises[0].sets[0].id
        val w = base.removeSet(id)
        assertEquals(1, w.exercises[0].sets.size)
        assertNull(w.locate(id))
        assertEquals(2, w.totalSets)
    }

    @Test
    fun setValues_replacesActual() {
        val base = simple()
        val id = base.exercises[0].sets[0].id
        val w = base.setValues(id, planned(70.0, 3))
        assertEquals(planned(70.0, 3), w.exercises[0].sets[0].actual)
        assertEquals(planned(50.0, 10), w.exercises[0].sets[0].target)
    }

    @Test
    fun locate_findsExerciseAndSetIndex() {
        val w = simple()
        assertEquals(1 to 0, w.locate(w.exercises[1].sets[0].id))
        assertEquals(0 to 1, w.locate(w.exercises[0].sets[1].id))
        assertNull(w.locate("nope"))
    }

    // --- Exercises -----------------------------------------------------------------------------

    @Test
    fun removeExercise_removesEntryWithItsSets() {
        val base = simple()
        val w = base.removeExercise(base.exercises[0].id)
        assertEquals(listOf("squat"), w.exercises.map { it.exercise.id })
        assertEquals(1, w.totalSets)
    }

    @Test
    fun replaceExercise_marksReplacedAndKeepsSets() {
        val base = simple().completeSet("id1", 5)
        val entryId = base.exercises[0].id
        val w = base.replaceExercise(entryId, exercise("incline"))
        val e = w.exercises[0]
        assertEquals("incline", e.exercise.id)
        assertEquals(ExerciseOrigin.REPLACED, e.origin)
        assertEquals("bench", e.replacedExerciseId)
        assertEquals(base.exercises[0].sets, e.sets)
        assertTrue(e.sets[0].completed)
        // Template rest belonged to the old exercise.
        assertNull(e.restSec)
    }

    @Test
    fun replaceExercise_twiceKeepsFirstOriginal() {
        val base = simple()
        val id = base.exercises[0].id
        val w = base.replaceExercise(id, exercise("incline")).replaceExercise(id, exercise("dips"))
        assertEquals("dips", w.exercises[0].exercise.id)
        assertEquals("bench", w.exercises[0].replacedExerciseId)
    }

    @Test
    fun replaceExercise_adaptsValuesToTrackingType() {
        val base = simple()
        val id = base.exercises[0].id
        val w = base.replaceExercise(id, exercise("plank", TrackingType.TIME))
        w.exercises[0].sets.forEach {
            assertEquals(SetValues(), it.target)
            assertEquals(SetValues(), it.actual)
        }
        val reps = base.replaceExercise(id, exercise("pull", TrackingType.REPS))
        assertEquals(planned(reps = 10), reps.exercises[0].sets[0].target)
        assertEquals(planned(reps = 10), reps.exercises[0].sets[0].actual)
    }

    @Test
    fun replaceExercise_withSameExerciseChangesNothing() {
        val base = simple()
        val w = base.replaceExercise(base.exercises[0].id, exercise("bench"))
        assertEquals(base, w)
    }

    @Test
    fun moveExercise_reorders() {
        val base = simple().addExercise(Workout.added(exercise("row"), emptyList(), counter("x")))
        val ids = base.exercises.map { it.exercise.id }
        assertEquals(listOf("bench", "squat", "row"), ids)
        assertEquals(listOf("squat", "row", "bench"), base.moveExercise(0, 2).exercises.map { it.exercise.id })
        assertEquals(listOf("row", "bench", "squat"), base.moveExercise(2, 0).exercises.map { it.exercise.id })
        assertEquals(listOf("squat", "bench", "row"), base.moveExercise(1, 0).exercises.map { it.exercise.id })
    }

    @Test
    fun moveExercise_invalidIndicesLeaveWorkoutUnchanged() {
        val base = simple()
        assertSame(base, base.moveExercise(-1, 0))
        assertSame(base, base.moveExercise(0, 5))
        assertSame(base, base.moveExercise(5, 0))
        assertSame(base, base.moveExercise(0, -1))
        assertSame(base, base.moveExercise(1, 1))
    }

    @Test
    fun exerciseRest_fallbackChain() {
        val e = WorkoutExercise("e", exercise("a", rest = 120), 45, "", ExerciseOrigin.PLANNED, sets = emptyList())
        assertEquals(45, e.restSec(90))
        assertEquals(120, e.copy(restSec = null).restSec(90))
        assertEquals(90, e.copy(restSec = null, exercise = exercise("a")).restSec(90))
        assertEquals(0, e.copy(restSec = 0).restSec(90))
    }

    // --- nextOpenSet ---------------------------------------------------------------------------

    @Test
    fun nextOpenSet_withoutArgumentIsFirstOpen() {
        val w = simple()
        assertEquals("id1", w.nextOpenSet()?.set?.id)
        assertEquals("id2", w.completeSet("id1", 1).nextOpenSet()?.set?.id)
        assertEquals("id1", w.completeSet("id2", 1).nextOpenSet()?.set?.id)
    }

    @Test
    fun nextOpenSet_afterASetGoesForwardAndAcrossExercises() {
        val w = simple()
        val ids = w.exercises.flatMap { it.sets }.map { it.id }
        assertEquals(ids[1], w.nextOpenSet(ids[0])?.set?.id)
        assertEquals(ids[2], w.nextOpenSet(ids[1])?.set?.id)
        val ref = w.nextOpenSet(ids[1])!!
        assertEquals("squat", ref.exercise.exercise.id)
        assertEquals(1, ref.number)
    }

    @Test
    fun nextOpenSet_wrapsAroundToStart() {
        val w = simple()
        val ids = w.exercises.flatMap { it.sets }.map { it.id }
        // Last set was just done, the first one is still open.
        val done = w.completeSet(ids[2], 1)
        val ref = done.nextOpenSet(ids[2])!!
        assertEquals(ids[0], ref.set.id)
        assertEquals(1, ref.number)
    }

    @Test
    fun nextOpenSet_skipsCompletedSets() {
        val w = simple()
        val ids = w.exercises.flatMap { it.sets }.map { it.id }
        val done = w.completeSet(ids[1], 1)
        assertEquals(ids[2], done.nextOpenSet(ids[0])?.set?.id)
    }

    @Test
    fun nextOpenSet_nullWhenAllDone() {
        var w = simple()
        w.exercises.flatMap { it.sets }.forEach { w = w.completeSet(it.id, 1) }
        assertNull(w.nextOpenSet())
        assertNull(w.nextOpenSet("id2"))
    }

    @Test
    fun nextOpenSet_unknownIdBehavesLikeNull() {
        val w = simple()
        assertEquals(w.nextOpenSet()?.set?.id, w.nextOpenSet("nope")?.set?.id)
    }

    @Test
    fun nextOpenSet_emptyWorkoutIsNull() {
        assertNull(Workout.empty("x", 0).nextOpenSet())
    }

    @Test
    fun setValuesFitTo_dropsUnusedFields() {
        val all = planned(10.0, 5, 30)
        assertEquals(planned(10.0, 5), all.fitTo(TrackingType.WEIGHT_REPS))
        assertEquals(planned(reps = 5), all.fitTo(TrackingType.REPS))
        assertEquals(planned(duration = 30), all.fitTo(TrackingType.TIME))
        assertEquals(planned(10.0, duration = 30), all.fitTo(TrackingType.WEIGHT_TIME))
    }

    // --- RestTimer -----------------------------------------------------------------------------

    @Test
    fun restTimer_start() {
        val r = RestTimer.start(now = 1000, durationSec = 90, afterSetId = "s")
        assertEquals(1000L, r.startedAt)
        assertEquals(91_000L, r.endsAt)
        assertEquals("s", r.afterSetId)
        assertEquals(90_000L, r.totalMs)
    }

    @Test
    fun restTimer_remainingAndIsOver() {
        val r = RestTimer.start(0, 60)
        assertEquals(60_000L, r.remainingMs(0))
        assertEquals(1L, r.remainingMs(59_999))
        assertEquals(0L, r.remainingMs(60_000))
        assertEquals(0L, r.remainingMs(120_000))
        assertFalse(r.isOver(59_999))
        assertTrue(r.isOver(60_000))
        assertTrue(r.isOver(60_001))
    }

    @Test
    fun restTimer_progress() {
        val r = RestTimer.start(1000, 100)
        assertEquals(0f, r.progress(1000))
        assertEquals(0.5f, r.progress(51_000))
        assertEquals(1f, r.progress(101_000))
        assertEquals(1f, r.progress(500_000))
        assertEquals(0f, r.progress(0))
        assertEquals(1f, RestTimer(5, 5).progress(5))
    }

    @Test
    fun restTimer_adjustAddsAndRemovesTime() {
        val r = RestTimer.start(0, 60, "s")
        val plus = r.adjust(15_000)
        assertEquals(75_000L, plus.endsAt)
        assertEquals(75_000L, plus.totalMs)
        assertEquals(0L, plus.startedAt)
        assertEquals("s", plus.afterSetId)
        val minus = r.adjust(-15_000)
        assertEquals(45_000L, minus.endsAt)
        assertTrue(minus.isOver(45_000))
        assertNotEquals(r, plus)
    }
}
