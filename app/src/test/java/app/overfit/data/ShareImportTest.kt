package app.overfit.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.overfit.data.catalog.CatalogSeeder
import app.overfit.data.db.OverfitDatabase
import app.overfit.share.ShareFormat
import app.overfit.share.ShareMapper
import app.overfit.share.ShareParse
import app.overfit.share.TemplateImporter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class ShareImportTest {
    private lateinit var db: OverfitDatabase
    private lateinit var exercises: ExerciseRepository
    private lateinit var templates: TemplateRepository
    private lateinit var importer: TemplateImporter

    @Before
    fun setUp() = runTest {
        db = Room.inMemoryDatabaseBuilder<OverfitDatabase>(ApplicationProvider.getApplicationContext())
            .allowMainThreadQueries()
            .build()
        CatalogSeeder(db, InMemoryKeyValueStore()) { File(CATALOG_DIR + "exercises.json").readBytes() }.seedIfNeeded(1)
        exercises = ExerciseRepository(db)
        templates = TemplateRepository(db)
        importer = TemplateImporter(exercises, templates) { File(SAMPLES_DIR + "$it.json").readBytes() }
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun samplesLoadAndAreIdempotent() = runTest {
        assertEquals(3, importer.addSamples())
        assertEquals(0, importer.addSamples())
        val all = templates.templates.first()
        assertEquals(listOf("Push", "Pull", "Legs"), all.map { it.name })
        all.forEach { summary ->
            val t = templates.get(summary.id)!!
            assertTrue("${t.name}: ${t.exercises.size} Übungen", t.exercises.size in 5..6)
            t.exercises.forEach { e ->
                assertTrue("${t.name}/${e.exercise.name}: Sätze", e.sets.size >= 3)
                assertTrue("${t.name}/${e.exercise.name}: Pause", e.restSec != null)
                assertTrue("${t.name}/${e.exercise.name}: kein Katalog-Treffer", !e.exercise.isCustom)
            }
        }
        // No sample created an own exercise: all IDs resolved.
        assertTrue(exercises.exercises.first().none { it.isCustom })
    }

    @Test
    fun shareThenImportGivesSameTemplate() = runTest {
        importer.addSamples()
        val original = templates.get(templates.templates.first().first { it.name == "Pull" }.id)!!
        val link = ShareFormat.toLink(ShareMapper.fromTemplate(original))
        assertTrue("Link ${link.length} Zeichen", link.length < ShareFormat.MAX_QR_LINK_LENGTH)
        val training = (ShareFormat.parseInput(link) as ShareParse.Ok).training
        val preview = importer.preview(training)
        assertTrue(preview.newExerciseNames.isEmpty())
        val id = importer.accept(preview)
        val copy = templates.get(id)!!
        assertEquals(original.name, copy.name)
        assertEquals(original.note, copy.note)
        assertEquals(original.exercises.map { it.exercise.id }, copy.exercises.map { it.exercise.id })
        assertEquals(original.exercises.map { it.sets }, copy.exercises.map { it.sets })
        assertEquals(original.exercises.map { it.restSec }, copy.exercises.map { it.restSec })
        assertEquals(original.exercises.map { it.note }, copy.exercises.map { it.note })
    }

    @Test
    fun compactQrContentImportsWithCatalogueIds() = runTest {
        importer.addSamples()
        val original = templates.get(templates.templates.first().first { it.name == "Legs" }.id)!!
        val text = app.overfit.share.QrPayload.encode(ShareMapper.fromTemplate(original))
        val training = (ShareFormat.parseInput(text) as ShareParse.Ok).training
        val preview = importer.preview(training)
        assertTrue(preview.newExerciseNames.isEmpty())
        val copy = templates.get(importer.accept(preview))!!
        assertEquals(original.exercises.map { it.exercise.id }, copy.exercises.map { it.exercise.id })
        assertEquals(original.exercises.map { it.sets }, copy.exercises.map { it.sets })
    }

    @Test
    fun catalogueIdHashesAreUnique() = runTest {
        val ids = exercises.exercises.first().map { it.id }.filter { it.startsWith("wg:") }
        assertTrue(ids.size > 250)
        assertEquals(ids.size, ids.map { app.overfit.share.QrPayload.idHash(it) }.toSet().size)
    }

    @Test
    fun customExercisesAreCreatedOnceAndMatchedByName() = runTest {
        val json = """{"v":1,"kind":"template","name":"Neu","exercises":[
            {"ref":"c1","sets":[{"sec":30}]},{"ref":"c2","sets":[{"kg":10,"reps":5}]}],
            "custom":[{"ref":"c1","name":"Handstand","trackingType":"TIME"},{"ref":"c2","name":"Zauberkugel","trackingType":"WEIGHT_REPS"}]}"""
        val training = (ShareFormat.parseInput(json) as ShareParse.Ok).training
        val first = importer.accept(importer.preview(training))
        val second = importer.accept(importer.preview(training)) // exercises exist now → matched by name
        assertEquals(2, exercises.exercises.first().count { it.isCustom })
        assertEquals(
            templates.get(first)!!.exercises.map { it.exercise.id },
            templates.get(second)!!.exercises.map { it.exercise.id },
        )
    }

    @Test
    fun aiStyleAnswerWithNamesImports() = runTest {
        val answer = "Hier dein Plan:\n```json\n" + app.overfit.share.AiPrompt.EXAMPLE + "\n```\nViel Erfolg!"
        val training = (ShareFormat.parseInput(answer) as ShareParse.Ok).training
        val preview = importer.preview(training)
        assertEquals(listOf("wg:bench-press", "wg:pull-up", "wg:plank"), preview.exercises.map { it.exercise?.id })
        val saved = templates.get(importer.accept(preview))!!
        assertEquals(3, saved.exercises.size)
        assertEquals(150, saved.exercises[0].restSec)
    }

    private companion object {
        const val CATALOG_DIR = "../shared/src/commonMain/composeResources/files/catalog/"
        const val SAMPLES_DIR = "../shared/src/commonMain/composeResources/files/samples/"
    }
}
