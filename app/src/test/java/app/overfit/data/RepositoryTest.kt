package app.overfit.data

import android.content.Context
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
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, OverfitDatabase::class.java).allowMainThreadQueries().build()
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
        val cursor = db.openHelper.writableDatabase
        listOf("template_exercise", "template_set").forEach { table ->
            cursor.query("SELECT COUNT(*) FROM $table").use {
                it.moveToFirst()
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
}
