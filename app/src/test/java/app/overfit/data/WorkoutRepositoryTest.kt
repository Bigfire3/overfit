package app.overfit.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.overfit.data.db.OverfitDatabase
import app.overfit.data.db.SessionStatus
import app.overfit.domain.Exercise
import app.overfit.domain.ExerciseOrigin
import app.overfit.domain.PlannedSet
import app.overfit.domain.RestTimer
import app.overfit.domain.Template
import app.overfit.domain.TemplateExercise
import app.overfit.domain.TrackingType
import app.overfit.domain.Workout
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class WorkoutRepositoryTest {
    private lateinit var db: OverfitDatabase
    private lateinit var exercises: ExerciseRepository
    private lateinit var workouts: WorkoutRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder<OverfitDatabase>(ApplicationProvider.getApplicationContext())
            .allowMainThreadQueries()
            .build()
        exercises = ExerciseRepository(db)
        workouts = WorkoutRepository(db, clock = { 123L })
    }

    @After
    fun tearDown() = db.close()

    private suspend fun custom(name: String, type: TrackingType = TrackingType.WEIGHT_REPS): Exercise =
        exercises.get(exercises.createCustom(CustomExerciseInput(name, type, 60)))!!

    private var seq = 0
    private fun ids(): () -> String = { "g${seq++}" }

    private fun planned(w: Double? = null, r: Int? = null, d: Int? = null) = PlannedSet(w, r, d)

    private fun workoutOf(id: String, vararg entries: Pair<Exercise, List<PlannedSet>>, startedAt: Long = 1000): Workout {
        val t = Template(
            1, "T-$id", null,
            entries.map { (e, sets) -> TemplateExercise(e, 45, "note", sets) },
        )
        return Workout.fromTemplate(t, startedAt, id = id, idGen = ids())
    }

    // --- save / loadActive ---------------------------------------------------------------------

    @Test
    fun saveAndLoadActiveRoundtrip() = runTest {
        val a = custom("A")
        val b = custom("B", TrackingType.TIME)
        var w = workoutOf("w1", a to listOf(planned(50.0, 10), planned(52.5, 8)), b to listOf(planned(d = 45)))
        val firstSet = w.exercises[0].sets[0].id
        w = w.completeSet(firstSet, 5000).setValues(w.exercises[0].sets[1].id, planned(55.0, 7))
            .copy(rest = RestTimer(5000, 65_000, firstSet), note = "felt good")
            .replaceExercise(w.exercises[1].id, exercises.get(custom("C", TrackingType.REPS).id)!!)

        workouts.save(w)
        val loaded = workouts.loadActive()!!
        assertEquals(w, loaded)
        assertEquals(listOf("A", "C"), loaded.exercises.map { it.exercise.name })
        assertEquals(ExerciseOrigin.REPLACED, loaded.exercises[1].origin)
        assertEquals(b.id, loaded.exercises[1].replacedExerciseId)
        assertEquals(RestTimer(5000, 65_000, firstSet), loaded.rest)
        assertEquals(SessionStatus.ACTIVE, workouts.status("w1"))
    }

    @Test
    fun loadActiveIsNullWithoutWorkout() = runTest {
        assertNull(workouts.loadActive())
    }

    @Test
    fun saveAgainAfterReorderAndRemoveLeavesNoOldRows() = runTest {
        val a = custom("A")
        val b = custom("B")
        val c = custom("C")
        val w = workoutOf("w1", a to listOf(planned(1.0, 1), planned(1.0, 2)), b to listOf(planned(2.0, 2)), c to listOf(planned(3.0, 3)))
        workouts.save(w)
        val changed = w.moveExercise(2, 0).removeExercise(w.exercises[1].id)
            .let { it.removeSet(it.exercises[1].sets[0].id) }
            .let { it.copy(rest = null) }
        workouts.save(changed)

        val loaded = workouts.loadActive()!!
        assertEquals(changed, loaded)
        assertEquals(listOf("C", "A"), loaded.exercises.map { it.exercise.name })
        assertEquals(listOf(2), loaded.exercises[1].sets.map { it.actual.reps })
        assertEquals(1, count("workout_session"))
        assertEquals(2, count("session_exercise"))
        assertEquals(2, count("set_entry"))
    }

    @Test
    fun saveClearsRestWhenItEnded() = runTest {
        val a = custom("A")
        val w = workoutOf("w1", a to listOf(planned(1.0, 1))).copy(rest = RestTimer(0, 1000, null))
        workouts.save(w)
        assertNotNull(workouts.loadActive()!!.rest)
        workouts.save(w.copy(rest = null))
        assertNull(workouts.loadActive()!!.rest)
    }

    // --- finish / delete -----------------------------------------------------------------------

    @Test
    fun finishMarksFinishedAndLateSaveDoesNotReactivate() = runTest {
        val a = custom("A")
        val w = workoutOf("w1", a to listOf(planned(1.0, 1))).copy(rest = RestTimer(0, 1000, null))
        workouts.save(w)
        workouts.finish(w, endedAt = 9000)
        assertNull(workouts.loadActive())
        assertEquals(SessionStatus.FINISHED, workouts.status("w1"))
        val stored = workouts.get("w1")!!
        assertNull(stored.rest)

        // A write that was still queued when the workout finished must not revive it.
        workouts.save(w.copy(name = "late"))
        assertNull(workouts.loadActive())
        assertEquals(SessionStatus.FINISHED, workouts.status("w1"))
        assertEquals("T-w1", workouts.get("w1")!!.name)
    }

    @Test
    fun finishStoresEndTimeAndCompletedState() = runTest {
        val a = custom("A")
        val w = workoutOf("w1", a to listOf(planned(1.0, 1))).let { it.completeSet(it.exercises[0].sets[0].id, 4000) }
        workouts.finish(w, endedAt = 9000)
        val row = db.workoutDao().get("w1")!!.session
        assertEquals(9000L, row.endedAt)
        assertEquals(1, workouts.get("w1")!!.completedSets)
    }

    @Test
    fun deleteCascadesToAllChildren() = runTest {
        val a = custom("A")
        workouts.save(workoutOf("w1", a to listOf(planned(1.0, 1), planned(1.0, 1))))
        workouts.save(workoutOf("w2", a to listOf(planned(1.0, 1))))
        workouts.delete("w1")
        assertNull(workouts.get("w1"))
        assertNotNull(workouts.get("w2"))
        assertEquals(1, count("workout_session"))
        assertEquals(1, count("session_exercise"))
        assertEquals(1, count("set_entry"))
    }

    // --- lastSets ------------------------------------------------------------------------------

    @Test
    fun lastSetsReturnsOnlyCompletedSetsOfLastFinishedWorkoutInOrder() = runTest {
        val a = custom("A")
        val b = custom("B")
        // Older finished workout.
        var old = workoutOf("old", a to listOf(planned(40.0, 10)), startedAt = 1000)
        old = old.completeSet(old.exercises[0].sets[0].id, 1)
        workouts.finish(old, 2000)

        // Newer finished workout: 3 sets, the middle one not completed.
        var recent = workoutOf("recent", a to listOf(planned(50.0, 8), planned(55.0, 6), planned(60.0, 4)), b to listOf(planned(9.0, 9)), startedAt = 5000)
        val s = recent.exercises[0].sets
        recent = recent.completeSet(s[0].id, 1).completeSet(s[2].id, 2)
        workouts.finish(recent, 6000)

        // Active workout with the exercise does not count.
        var active = workoutOf("active", a to listOf(planned(99.0, 1)), startedAt = 9000)
        active = active.completeSet(active.exercises[0].sets[0].id, 1)
        workouts.save(active)

        assertEquals(listOf(planned(50.0, 8), planned(60.0, 4)), workouts.lastSets(a.id))
        // B was in the workout but never checked off.
        assertTrue(workouts.lastSets(b.id).isEmpty())
    }

    @Test
    fun lastSetsIgnoresWorkoutsWhereNothingWasCompleted() = runTest {
        val a = custom("A")
        var done = workoutOf("done", a to listOf(planned(50.0, 8)), startedAt = 1000)
        done = done.completeSet(done.exercises[0].sets[0].id, 1)
        workouts.finish(done, 2000)
        // Newer workout contains the exercise but nothing was checked off.
        workouts.finish(workoutOf("skipped", a to listOf(planned(70.0, 3)), startedAt = 5000), 6000)
        assertEquals(listOf(planned(50.0, 8)), workouts.lastSets(a.id))
    }

    @Test
    fun lastSetsEmptyForUnknownOrUnusedExercise() = runTest {
        val a = custom("A")
        assertTrue(workouts.lastSets(a.id).isEmpty())
        assertTrue(workouts.lastSets("nope").isEmpty())
    }

    @Test
    fun lastSetsUsesActualValuesNotTargets() = runTest {
        val a = custom("A")
        var w = workoutOf("w", a to listOf(planned(50.0, 8)))
        val id = w.exercises[0].sets[0].id
        w = w.setValues(id, planned(52.5, 7)).completeSet(id, 1)
        workouts.finish(w, 2)
        assertEquals(listOf(planned(52.5, 7)), workouts.lastSets(a.id))
    }

    // --- deleteCustom --------------------------------------------------------------------------

    @Test
    fun deleteCustomUsedOnlyInAWorkoutIsArchivedAndHistoryKeepsIt() = runTest {
        val x = custom("X")
        workouts.save(workoutOf("w1", x to listOf(planned(1.0, 1))))
        assertEquals(ExerciseRepository.DeleteResult.ARCHIVED, exercises.deleteCustom(x.id))
        assertNotNull(exercises.get(x.id))
        assertEquals("X", workouts.loadActive()!!.exercises.single().exercise.name)
    }

    @Test
    fun deleteCustomUsedOnlyInFinishedWorkoutIsArchived() = runTest {
        val x = custom("X")
        workouts.finish(workoutOf("w1", x to listOf(planned(1.0, 1))), 10)
        assertEquals(ExerciseRepository.DeleteResult.ARCHIVED, exercises.deleteCustom(x.id))
        assertNotNull(exercises.get(x.id))
    }

    @Test
    fun deleteCustomAfterWorkoutWasDiscardedIsDeleted() = runTest {
        val x = custom("X")
        workouts.save(workoutOf("w1", x to listOf(planned(1.0, 1))))
        workouts.delete("w1")
        assertEquals(ExerciseRepository.DeleteResult.DELETED, exercises.deleteCustom(x.id))
        assertNull(exercises.get(x.id))
    }

    // --- AppData.resetAll ----------------------------------------------------------------------

    @Test
    fun resetAllRemovesWorkouts() = runTest {
        val data = AppData(
            db,
            InMemoryKeyValueStore(),
            readCatalog = { File("../shared/src/commonMain/composeResources/files/catalog/exercises.json").readBytes() },
        )
        val x = custom("X")
        data.workouts.save(workoutOf("w1", x to listOf(planned(1.0, 1))))
        data.workouts.finish(workoutOf("w2", x to listOf(planned(1.0, 1))), 5)
        assertEquals(2, count("workout_session"))
        data.resetAll()
        assertEquals(0, count("workout_session"))
        assertEquals(0, count("session_exercise"))
        assertEquals(0, count("set_entry"))
        assertNull(data.workouts.loadActive())
    }

    private fun count(table: String): Int =
        db.query("SELECT COUNT(*) FROM $table", null).use {
            assertTrue(it.moveToFirst())
            it.getInt(0)
        }
}
