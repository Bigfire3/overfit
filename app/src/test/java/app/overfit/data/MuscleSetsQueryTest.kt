package app.overfit.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.overfit.data.db.OverfitDatabase
import app.overfit.domain.Exercise
import app.overfit.domain.Muscle
import app.overfit.domain.MuscleSetRow
import app.overfit.domain.TrackingType
import app.overfit.domain.Workout
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** [WorkoutRepository.muscleSets]: only completed sets of finished workouts, one row per exercise entry. */
@RunWith(RobolectricTestRunner::class)
class MuscleSetsQueryTest {
    private lateinit var db: OverfitDatabase
    private lateinit var exercises: ExerciseRepository
    private lateinit var workouts: WorkoutRepository
    private var seq = 0

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder<OverfitDatabase>(ApplicationProvider.getApplicationContext()).allowMainThreadQueries().build()
        exercises = ExerciseRepository(db)
        workouts = WorkoutRepository(db, clock = { 123L }, photoFiles = InMemoryPhotoFiles())
    }

    @After
    fun tearDown() = db.close()

    private suspend fun custom(name: String, primary: List<Muscle>, secondary: List<Muscle> = emptyList()): Exercise =
        exercises.get(exercises.createCustom(CustomExerciseInput(name, TrackingType.REPS, 60, primary = primary, secondary = secondary)))!!

    /** Workout with [ex] added once per entry; [done] completed sets of each entry (of 3). */
    private fun workout(id: String, start: Long, vararg entries: Pair<Exercise, Int>): Workout {
        var w = Workout.empty(id, start, id = id)
        entries.forEach { (ex, _) -> w = w.addExercise(Workout.added(ex, emptyList()) { "g${seq++}" }) }
        entries.forEachIndexed { i, (_, done) -> w.exercises[i].sets.take(done).forEach { s -> w = w.completeSet(s.id, start + 1) } }
        return w
    }

    @Test
    fun countsOnlyCompletedSetsOfFinishedWorkouts() = runTest {
        val dips = custom("Dips", listOf(Muscle.TRICEPS), listOf(Muscle.CHEST))
        val curl = custom("Curl", listOf(Muscle.BICEPS))
        // Finished: dips twice (2 and 1 sets done), curl with nothing done.
        workouts.finish(workout("f", 1000, dips to 2, dips to 1, curl to 0), endedAt = 2000)
        // Still running: does not count.
        workouts.save(workout("a", 5000, curl to 3))

        val rows = workouts.muscleSets.first().sortedBy { it.sets }
        assertEquals(
            listOf(
                MuscleSetRow(1000, listOf(Muscle.TRICEPS), listOf(Muscle.CHEST), 1),
                MuscleSetRow(1000, listOf(Muscle.TRICEPS), listOf(Muscle.CHEST), 2),
            ),
            rows,
        )
    }
}
