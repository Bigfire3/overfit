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
        workouts.addPhoto(dayOf(1000), byteArrayOf(1, 2), takenAt = 5000)

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
        // Since v13 every new photo belongs to its day, never to a training.
        val first = workouts.addPhoto(dayOf(1000), byteArrayOf(1), takenAt = 5000)
        val second = workouts.addPhoto(dayOf(1000), byteArrayOf(2), takenAt = 7000)
        assertNull(first.sessionId)
        assertNull(second.sessionId)

        val diary = workouts.photos.first()
        assertEquals(2, diary.size)
        assertTrue(diary.all { it.workoutName == null && it.photo.date == dayOf(1000) })
        assertEquals(setOf(first.id, second.id), workouts.allPhotos().map { it.id }.toSet())
        // The training shows the photos of its day.
        assertEquals(listOf(first.id, second.id), workouts.getFinished("w1")!!.photos.map { it.id })
    }

    @Test
    fun deletingATrainingKeepsItsPhotosOnTheirDay() = runTest {
        val a = custom("A")
        var w = workoutOf("w1", a, PlannedSet(50.0, 10))
        w = w.completeSet(w.exercises[0].sets[0].id, 2000)
        workouts.finish(w, endedAt = 9000)
        // A legacy photo of the training (before v13).
        db.workoutDao().insertPhoto(app.overfit.data.db.WorkoutPhotoEntity("legacy", "w1", "legacy.jpg", 5000, 0, dayOf(1000).toString()))
        workouts.photoFiles.write("legacy.jpg", byteArrayOf(1, 2))
        val photo = workouts.allPhotos().single()

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

    @Test
    fun renamingAFinishedTrainingChangesOnlyThatTraining() = runTest {
        val a = custom("A")
        var w = workoutOf("w1", a, PlannedSet(50.0, 10))
        w = w.completeSet(w.exercises[0].sets[0].id, 2000)
        workouts.finish(w, endedAt = 9000)

        assertTrue(workouts.updateFinished(w.withName("  Abendtraining  "), note = null, feeling = null, sauna = false))
        assertEquals("Abendtraining", workouts.getFinished("w1")!!.workout.name)
        assertEquals("Abendtraining", workouts.history.first().single().name)
        assertEquals(1L, workouts.getFinished("w1")!!.workout.templateId)

        // An empty name keeps the old one instead of a nameless training.
        assertTrue(workouts.updateFinished(w.withName("   "), note = null, feeling = null, sauna = false))
        assertEquals("Abendtraining", workouts.getFinished("w1")!!.workout.name)
    }

    @Test
    fun lastPerformancePrefersThisWorkoutThenAnyAndSkipsUndone() = runTest {
        val a = custom("A")
        fun template(id: Long, name: String) = Template(id, name, null, listOf(TemplateExercise(a, 45, null, listOf(PlannedSet(50.0, 10), PlannedSet(50.0, 8)))))
        suspend fun train(id: String, templateId: Long, startedAt: Long, values: List<SetValues>, done: Boolean = true) {
            var w = Workout.fromTemplate(template(templateId, "T$templateId"), startedAt, id = id, idGen = { "g${seq++}" })
            w.exercises[0].sets.forEachIndexed { i, s ->
                w = w.setValues(s.id, values[i])
                if (done) w = w.completeSet(s.id, startedAt + 1)
            }
            workouts.finish(w, endedAt = startedAt + 10)
        }
        assertNull(workouts.lastPerformance(a.id, 1))

        train("old", templateId = 1, startedAt = 1_000, values = listOf(SetValues(60.0, 10), SetValues(62.5, 8)))
        train("other", templateId = 2, startedAt = 5_000, values = listOf(SetValues(70.0, 5), SetValues(70.0, 4)))
        // Newest, but nothing checked off: never counts as "last time".
        train("undone", templateId = 1, startedAt = 9_000, values = listOf(SetValues(99.0, 1), SetValues(99.0, 1)), done = false)

        val inWorkout = workouts.lastPerformance(a.id, 1)!!
        assertTrue(inWorkout.inThisWorkout)
        assertEquals(1_000L, inWorkout.startedAt)
        assertEquals(listOf(SetValues(60.0, 10), SetValues(62.5, 8)), inWorkout.sets)

        val anywhere = workouts.lastPerformance(a.id, 3)!!
        assertFalse(anywhere.inThisWorkout)
        assertEquals(5_000L, anywhere.startedAt)
        assertEquals(listOf(SetValues(70.0, 5), SetValues(70.0, 4)), anywhere.sets)
        // Same source as "last time" in the running workout.
        assertEquals(anywhere.sets, workouts.lastSets(a.id))
    }

    private fun dayOf(ms: Long) = kotlinx.datetime.LocalDate.parse(app.overfit.data.db.photoDay(ms))
}
