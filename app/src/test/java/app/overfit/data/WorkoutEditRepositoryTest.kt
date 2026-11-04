package app.overfit.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.overfit.data.db.OverfitDatabase
import app.overfit.data.db.SessionStatus
import app.overfit.domain.Exercise
import app.overfit.domain.PlannedSet
import app.overfit.domain.SetValues
import app.overfit.domain.Template
import app.overfit.domain.TemplateExercise
import app.overfit.domain.TrackingType
import app.overfit.domain.Workout
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Editing a finished workout afterwards ([WorkoutRepository.updateFinished]) and photos without a training. */
@RunWith(RobolectricTestRunner::class)
class WorkoutEditRepositoryTest {
    private lateinit var db: OverfitDatabase
    private lateinit var exercises: ExerciseRepository
    private lateinit var workouts: WorkoutRepository
    private var seq = 0

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder<OverfitDatabase>(ApplicationProvider.getApplicationContext())
            .allowMainThreadQueries()
            .build()
        exercises = ExerciseRepository(db)
        workouts = WorkoutRepository(db, clock = { 123L }, photoFiles = InMemoryPhotoFiles())
    }

    @After
    fun tearDown() = db.close()

    private suspend fun custom(name: String): Exercise =
        exercises.get(exercises.createCustom(CustomExerciseInput(name, TrackingType.WEIGHT_REPS, 60)))!!

    private fun workoutOf(id: String, ex: Exercise, vararg sets: PlannedSet): Workout =
        Workout.fromTemplate(
            Template(1, "T-$id", null, listOf(TemplateExercise(ex, 45, null, sets.toList()))),
            1000, id = id, idGen = { "g${seq++}" },
        )

    @Test
    fun updateFinishedReplacesSetsAndDetailsButKeepsStatusNameAndEnd() = runTest {
        val a = custom("A")
        var w = workoutOf("w1", a, PlannedSet(50.0, 10), PlannedSet(52.5, 8), PlannedSet(55.0, 6))
        w.exercises[0].sets.forEach { w = w.completeSet(it.id, 2000) }
        workouts.finish(w, endedAt = 9000)
        workouts.addPhoto("w1", byteArrayOf(1, 2), takenAt = 5000)

        val sets = w.exercises[0].sets
        var edited = w.setValues(sets[0].id, SetValues(60.0, 10))
            .removeSet(sets[2].id)
            .addSet(w.exercises[0].id, "added")
        edited = edited.setValues("added", SetValues(70.0, 5)).completeSet("added", 9000)

        assertTrue(workouts.updateFinished(edited, note = "x", feeling = 3, sauna = true))

        val f = workouts.getFinished("w1")!!
        assertEquals("x", f.note)
        assertEquals(3, f.feeling)
        assertTrue(f.sauna)
        assertEquals(9000L, f.endedAt)
        assertEquals("T-w1", f.workout.name)
        assertEquals(SessionStatus.FINISHED, workouts.status("w1"))
        assertEquals(
            listOf(60.0 to 10, 52.5 to 8, 70.0 to 5),
            f.workout.exercises[0].sets.map { it.actual.weightKg to it.actual.reps },
        )
        assertTrue(f.workout.exercises[0].sets.all { it.completed })
        assertEquals(1, f.photos.size)

        val row = workouts.history.first().single()
        assertEquals(3, row.completedSets)
        assertEquals(60.0 * 10 + 52.5 * 8 + 70.0 * 5, row.volumeKg, 0.001)
        assertTrue(row.sauna)
        assertEquals(1, row.photoCount)

        val logged = workouts.exerciseSets(a.id).first()
        assertEquals(listOf(60.0, 52.5, 70.0), logged.map { it.weightKg })
    }

    @Test
    fun updateFinishedOnActiveWorkoutReturnsFalseAndChangesNothing() = runTest {
        val a = custom("A")
        val w = workoutOf("w1", a, PlannedSet(50.0, 10))
        workouts.save(w)

        val changed = w.setValues(w.exercises[0].sets[0].id, SetValues(99.0, 1)).copy(name = "other")
        assertFalse(workouts.updateFinished(changed, note = "x", feeling = 3, sauna = true))
        assertFalse(workouts.updateFinished(changed.copy(id = "missing"), note = null, feeling = null, sauna = false))

        assertEquals(SessionStatus.ACTIVE, workouts.status("w1"))
        assertEquals(w, workouts.loadActive())
        assertNull(workouts.getFinished("w1"))
        assertFalse(db.workoutDao().get("w1")!!.session.sauna)
    }

    @Test
    fun photoWithoutTrainingShowsInDiaryWithoutNameAndInAllPhotos() = runTest {
        val a = custom("A")
        val w = workoutOf("w1", a, PlannedSet(50.0, 10))
        workouts.finish(w, endedAt = 9000)
        val withTraining = workouts.addPhoto("w1", byteArrayOf(1), takenAt = 5000)
        val free = workouts.addPhoto(null, byteArrayOf(2), takenAt = 7000)
        assertNull(free.sessionId)

        val diary = workouts.photos.first()
        assertEquals(2, diary.size)
        val freeRow = diary.single { it.photo.id == free.id }
        assertNull(freeRow.workoutName)
        assertEquals("T-w1", diary.single { it.photo.id == withTraining.id }.workoutName)
        assertEquals(setOf(free.id, withTraining.id), workouts.allPhotos().map { it.id }.toSet())
        assertEquals(1, workouts.history.first().single().photoCount)
    }

    @Test
    fun deletingATrainingKeepsItsPhotosOnTheirDay() = runTest {
        val a = custom("A")
        var w = workoutOf("w1", a, PlannedSet(50.0, 10))
        w = w.completeSet(w.exercises[0].sets[0].id, 2000)
        workouts.finish(w, endedAt = 9000)
        val photo = workouts.addPhoto("w1", byteArrayOf(1, 2), takenAt = 5000)

        workouts.deleteWorkout("w1")

        assertNull(workouts.getFinished("w1"))
        val left = workouts.allPhotos().single()
        assertEquals(photo.id, left.id)
        assertNull(left.sessionId)
        assertEquals(5000L, left.takenAt)
        // The file is still there.
        assertTrue(workouts.readPhoto(left)!!.contentEquals(byteArrayOf(1, 2)))
        assertNull(workouts.photos.first().single().workoutName)
    }
}
