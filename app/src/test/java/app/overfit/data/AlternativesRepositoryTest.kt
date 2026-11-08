package app.overfit.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.overfit.data.db.OverfitDatabase
import app.overfit.domain.Exercise
import app.overfit.domain.ExerciseOrigin
import app.overfit.domain.PlannedSet
import app.overfit.domain.Template
import app.overfit.domain.TemplateExercise
import app.overfit.domain.TrackingType
import app.overfit.domain.Workout
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Exercise alternatives (A/B, DB v14) in the repositories. */
@RunWith(RobolectricTestRunner::class)
class AlternativesRepositoryTest {
    private lateinit var db: OverfitDatabase
    private lateinit var exercises: ExerciseRepository
    private lateinit var templates: TemplateRepository
    private lateinit var workouts: WorkoutRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder<OverfitDatabase>(ApplicationProvider.getApplicationContext())
            .allowMainThreadQueries()
            .build()
        exercises = ExerciseRepository(db)
        templates = TemplateRepository(db)
        workouts = WorkoutRepository(db, clock = { 123L })
    }

    @After
    fun tearDown() = db.close()

    private suspend fun custom(name: String): Exercise =
        exercises.get(exercises.createCustom(CustomExerciseInput(name, TrackingType.WEIGHT_REPS, 60)))!!

    private fun te(e: Exercise, rest: Int?, vararg reps: Int, alternatives: List<TemplateExercise> = emptyList()) =
        TemplateExercise(e, restSec = rest, note = null, sets = reps.map { PlannedSet(weightKg = 40.0, reps = it) }, alternatives = alternatives)

    private var seq = 0
    private fun ids(): () -> String = { "g${seq++}" }

    /** Squat, then bench with the alternatives dumbbell press and fly. */
    private suspend fun abTemplate(): Template {
        val squat = custom("Squat")
        val bench = custom("Bench")
        val dumbbell = custom("Dumbbell")
        val fly = custom("Fly")
        val t = Template(
            0, "AB", "n",
            listOf(
                te(squat, null, 5),
                te(bench, 120, 10, 8, alternatives = listOf(te(dumbbell, 90, 12), te(fly, null, 15, 15, 15))),
            ),
        )
        return t.copy(id = templates.save(t))
    }

    @Test
    fun saveAndGetRoundTripWithAlternatives() = runTest {
        val t = abTemplate()
        val loaded = templates.get(t.id)!!
        assertEquals(t, loaded)
        assertEquals(listOf("Dumbbell", "Fly"), loaded.exercises[1].alternatives.map { it.exercise.name })
        assertEquals(listOf(90, null), loaded.exercises[1].alternatives.map { it.restSec })
        assertEquals(listOf(3), loaded.exercises[1].alternatives.drop(1).map { it.sets.size })

        // Saving again (e.g. alternative removed) leaves no old rows behind.
        val fewer = loaded.copy(exercises = listOf(loaded.exercises[1].copy(alternatives = loaded.exercises[1].alternatives.take(1)), loaded.exercises[0]))
        templates.save(fewer)
        assertEquals(fewer, templates.get(t.id))
        assertEquals(3, count("template_exercise"))
    }

    @Test
    fun templateListNamesOnlyTheMainExercises() = runTest {
        val t = abTemplate()
        val summary = templates.templates.first().single { it.id == t.id }
        assertEquals(listOf("Squat", "Bench"), summary.exerciseNames)
    }

    @Test
    fun lastVariantIgnoresSkippedAndOtherTemplatesAndRunningOnes() = runTest {
        val t = abTemplate()
        val variantIds = t.exercises[1].variants.map { it.exercise.id }
        assertNull(workouts.lastVariant(t.id, variantIds))

        // Oldest: dumbbell done.
        var first = Workout.fromTemplate(t, 1_000, id = "w1", idGen = ids(), variants = mapOf(1 to 1))
        first = first.completeSet(first.exercises[1].sets[0].id, 1_500)
        workouts.save(first)
        workouts.finish(first, 2_000)
        assertEquals(variantIds[1], workouts.lastVariant(t.id, variantIds))

        // Newer, fly but skipped today: does not count.
        var skipped = Workout.fromTemplate(t, 3_000, id = "w2", idGen = ids(), variants = mapOf(1 to 2))
        skipped = skipped.setSkipped(skipped.exercises[1].id, true)
        workouts.save(skipped)
        workouts.finish(skipped, 4_000)
        assertEquals(variantIds[1], workouts.lastVariant(t.id, variantIds))

        // Newer, fly planned but not a single set of it done (training cut short): does not count either.
        val untouched = Workout.fromTemplate(t, 4_500, id = "w2b", idGen = ids(), variants = mapOf(1 to 2))
        workouts.save(untouched)
        workouts.finish(untouched, 4_600)
        assertEquals(variantIds[1], workouts.lastVariant(t.id, variantIds))

        // Newest of another template with bench: ignored.
        var other = Workout.fromTemplate(t.copy(id = t.id + 100), 5_000, id = "w3", idGen = ids())
        other = other.completeSet(other.exercises[1].sets[0].id, 5_500)
        workouts.save(other)
        workouts.finish(other, 6_000)
        assertEquals(variantIds[1], workouts.lastVariant(t.id, variantIds))

        // Running (not finished) with fly: ignored.
        workouts.save(Workout.fromTemplate(t, 7_000, id = "w4", idGen = ids(), variants = mapOf(1 to 2)))
        assertEquals(variantIds[1], workouts.lastVariant(t.id, variantIds))

        assertNull(workouts.lastVariant(t.id, emptyList()))
    }

    @Test
    fun loadActiveReattachesVariants() = runTest {
        val t = templates.get(abTemplate().id)!!
        val w = Workout.fromTemplate(t, 1_000, id = "w1", idGen = ids(), variants = mapOf(1 to 1))
        workouts.save(w)
        val loaded = workouts.loadActive()!!
        assertEquals(w, loaded)
        assertEquals(listOf("Bench", "Dumbbell", "Fly"), loaded.exercises[1].variants.map { it.exercise.name })
        assertEquals(1, loaded.exercises[1].variantIndex)
        assertTrue(loaded.exercises[0].variants.isEmpty())
    }

    @Test
    fun loadActiveGivesNoVariantsToAReplacedExercise() = runTest {
        val t = templates.get(abTemplate().id)!!
        var w = Workout.fromTemplate(t, 1_000, id = "w1", idGen = ids())
        w = w.replaceExercise(w.exercises[1].id, custom("Cable"))
        workouts.save(w)
        val loaded = workouts.loadActive()!!
        assertEquals(ExerciseOrigin.REPLACED, loaded.exercises[1].origin)
        assertTrue(loaded.exercises[1].variants.isEmpty())
    }

    @Test
    fun adoptAfterTrainingVariantB_keepsTheAlternativesInTheDatabase() = runTest {
        val t = templates.get(abTemplate().id)!!
        var w = Workout.fromTemplate(t, 1_000, id = "w1", idGen = ids(), variants = mapOf(1 to 1))
        w = w.addSet(w.exercises[1].id)
        templates.adopt(w)
        val adopted = templates.get(t.id)!!
        assertEquals(listOf("Dumbbell", "Fly"), adopted.exercises[1].alternatives.map { it.exercise.name })
        assertEquals(2, adopted.exercises[1].alternatives[0].sets.size)
        assertEquals(t.exercises[1].sets, adopted.exercises[1].sets)
    }

    private fun count(table: String): Int =
        db.query("SELECT COUNT(*) FROM $table", null).use {
            assertTrue(it.moveToFirst())
            it.getInt(0)
        }
}
