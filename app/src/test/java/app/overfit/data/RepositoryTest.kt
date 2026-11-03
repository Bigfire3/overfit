package app.overfit.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.overfit.data.db.OverfitDatabase
import app.overfit.domain.PlannedSet
import app.overfit.domain.Template
import app.overfit.domain.TemplateExercise
import app.overfit.domain.TrackingType
import kotlinx.coroutines.flow.first
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

@RunWith(RobolectricTestRunner::class)
class RepositoryTest {
    private lateinit var db: OverfitDatabase
    private lateinit var exercises: ExerciseRepository
    private lateinit var templates: TemplateRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder<OverfitDatabase>(ApplicationProvider.getApplicationContext())
            .allowMainThreadQueries()
            .build()
        exercises = ExerciseRepository(db)
        templates = TemplateRepository(db)
    }

    @After
    fun tearDown() = db.close()

    private fun input(name: String) = CustomExerciseInput(name, TrackingType.WEIGHT_REPS, 60, null, null)

    private suspend fun custom(name: String) = exercises.get(exercises.createCustom(input(name)))!!

    private fun te(e: app.overfit.domain.Exercise, vararg reps: Int) =
        TemplateExercise(e, restSec = null, note = null, sets = reps.map { PlannedSet(weightKg = 50.0, reps = it) })

    @Test
    fun saveNewAndGetKeepsOrder() = runTest {
        val a = custom("A")
        val b = custom("B")
        val id = templates.save(Template(0, "Push", null, listOf(te(b, 10, 8, 6), te(a, 12))))
        val t = templates.get(id)!!
        assertEquals("Push", t.name)
        assertEquals(listOf("B", "A"), t.exercises.map { it.exercise.name })
        assertEquals(listOf(10, 8, 6), t.exercises[0].sets.map { it.reps })
        assertEquals(listOf(12), t.exercises[1].sets.map { it.reps })
    }

    @Test
    fun updateReplacesExercisesAndSets() = runTest {
        val a = custom("A")
        val b = custom("B")
        val id = templates.save(Template(0, "T", null, listOf(te(a, 10, 10))))
        templates.save(Template(id, "T2", "note", listOf(te(b, 5))))
        val t = templates.get(id)!!
        assertEquals("T2", t.name)
        assertEquals("note", t.note)
        assertEquals(listOf("B"), t.exercises.map { it.exercise.name })
        assertEquals(listOf(5), t.exercises.single().sets.map { it.reps })
        assertEquals(1, templates.templates.first().size)
    }

    @Test
    fun deleteCascadesToChildren() = runTest {
        val a = custom("A")
        val id = templates.save(Template(0, "T", null, listOf(te(a, 10))))
        templates.delete(id)
        assertNull(templates.get(id))
        listOf("template_exercise", "template_set").forEach { table ->
            db.query("SELECT COUNT(*) FROM $table", null).use {
                assertTrue(it.moveToFirst())
                assertEquals(table, 0, it.getInt(0))
            }
        }
    }

    @Test
    fun templatesFlowListsNamesInOrder() = runTest {
        val a = custom("A")
        val b = custom("B")
        templates.save(Template(0, "First", null, listOf(te(b, 1), te(a, 1))))
        templates.save(Template(0, "Second", null, emptyList()))
        val list = templates.templates.first()
        assertEquals(listOf("First", "Second"), list.map { it.name })
        assertEquals(listOf("B", "A"), list[0].exerciseNames)
        assertTrue(list[1].exerciseNames.isEmpty())
    }

    @Test
    fun createAndUpdateCustom() = runTest {
        val id = exercises.createCustom(input("  Hollow Hold "))
        val created = exercises.get(id)!!
        assertEquals("Hollow Hold", created.name)
        assertTrue(created.isCustom)
        exercises.updateCustom(id, input("Hollow Rock").copy(trackingType = TrackingType.TIME, restSec = 30))
        val updated = exercises.get(id)!!
        assertEquals("Hollow Rock", updated.name)
        assertEquals(TrackingType.TIME, updated.trackingType)
        assertEquals(30, updated.defaultRestSec)
    }

    @Test
    fun deleteUnusedCustomIsDeleted() = runTest {
        val id = exercises.createCustom(input("X"))
        assertEquals(ExerciseRepository.DeleteResult.DELETED, exercises.deleteCustom(id))
        assertNull(exercises.get(id))
        assertTrue(exercises.exercises.first().isEmpty())
    }

    @Test
    fun deleteUsedCustomIsArchived() = runTest {
        val x = custom("X")
        val id = templates.save(Template(0, "T", null, listOf(te(x, 10))))
        assertEquals(ExerciseRepository.DeleteResult.ARCHIVED, exercises.deleteCustom(x.id))
        assertTrue(exercises.exercises.first().none { it.id == x.id })
        assertNotNull(exercises.get(x.id))
        assertEquals("X", templates.get(id)!!.exercises.single().exercise.name)
    }

    @Test
    fun adopt_emptyStartBecomesSavedWorkout_changesFlowBack_deleteKeepsHistory() = runTest {
        val a = custom("A")
        val b = custom("B")
        val workouts = WorkoutRepository(db, clock = { 5_000L })

        // Started empty, one exercise added → new saved workout, session linked to it
        var w = app.overfit.domain.Workout.empty("Workout vom 3. Nov.", 1_000L)
        w = w.addExercise(app.overfit.domain.Workout.added(a, listOf(PlannedSet(40.0, 10))))
        val adopted = templates.adopt(w)
        val id = adopted.templateId!!
        workouts.finish(adopted, 2_000L)
        val created = templates.get(id)!!
        assertEquals("Workout vom 3. Nov.", created.name)
        assertEquals(listOf("A"), created.exercises.map { it.exercise.name })
        assertEquals(mapOf(id to 1_000L), templates.lastDone.first())

        // Started again from it, exercise B added and a set added → the saved workout follows
        var again = app.overfit.domain.Workout.fromTemplate(created, 3_000L)
        again = again.addSet(again.exercises[0].id)
        again = again.addExercise(app.overfit.domain.Workout.added(b, emptyList()))
        assertEquals(again, templates.adopt(again))
        val updated = templates.get(id)!!
        assertEquals(listOf("A", "B"), updated.exercises.map { it.exercise.name })
        assertEquals(2, updated.exercises[0].sets.size)

        // Deleted: history stays, the next workout does not bring it back
        templates.delete(id)
        assertNotNull(workouts.getFinished(adopted.id))
        templates.adopt(again)
        assertTrue(templates.templates.first().isEmpty())
    }

    @Test
    fun rename_changesNameOnly() = runTest {
        val a = custom("A")
        val id = templates.save(Template(0, "Push", null, listOf(te(a, 10))))
        templates.rename(id, "  Push schwer ")
        assertEquals("Push schwer", templates.get(id)!!.name)
        assertEquals(listOf(10), templates.get(id)!!.exercises.single().sets.map { it.reps })
    }
}
