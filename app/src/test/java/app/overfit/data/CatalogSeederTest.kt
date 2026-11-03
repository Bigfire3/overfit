package app.overfit.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.overfit.data.catalog.CatalogFile
import app.overfit.data.catalog.CatalogSeeder
import app.overfit.data.catalog.CatalogExercise
import app.overfit.data.db.OverfitDatabase
import app.overfit.domain.Equipment
import app.overfit.domain.ExerciseSource
import app.overfit.domain.Muscle
import app.overfit.domain.TrackingType
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class CatalogSeederTest {
    private lateinit var db: OverfitDatabase
    private lateinit var seeder: CatalogSeeder
    private val kv = InMemoryKeyValueStore()
    private val readCatalog: suspend () -> ByteArray = { File(CATALOG_DIR + "exercises.json").readBytes() }

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder<OverfitDatabase>(ApplicationProvider.getApplicationContext())
            .allowMainThreadQueries()
            .build()
        seeder = CatalogSeeder(db, kv, readCatalog)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun bundledCatalogIsConsistent() = runTest {
        val catalog = seeder.load()
        // Workout Guide only since free-exercise-db was removed (3.11.2026).
        assertTrue("Katalog hat ${catalog.exercises.size} Übungen", catalog.exercises.size >= 300)
        assertTrue("nur Workout Guide", catalog.exercises.all { it.source == ExerciseSource.WORKOUT_GUIDE && it.id.startsWith("wg:") })
        assertEquals(catalog.exercises.size, catalog.exercises.map { it.id }.toSet().size)

        val problems = mutableListOf<String>()
        for (e in catalog.exercises) {
            if (e.source == ExerciseSource.WORKOUT_GUIDE) {
                val image = e.image
                if (image == null) problems += "${e.id}: kein Bild"
                else if (!File(RESOURCES_DIR + image).isFile) problems += "${e.id}: Bild fehlt ($image)"
            }
            (e.primary + e.secondary).filter { Muscle.fromKey(it) == null }.forEach { problems += "${e.id}: Muskel $it" }
            if (e.equipment != null && Equipment.fromKey(e.equipment) == null) problems += "${e.id}: Equipment ${e.equipment}"
        }
        assertTrue(problems.take(20).joinToString("\n") + "\n(${problems.size} Probleme)", problems.isEmpty())
    }

    @Test
    fun seedTwiceKeepsCount() = runTest {
        val catalog = seeder.load()
        seeder.seed(catalog, now = 1)
        val first = db.exerciseDao().count()
        assertEquals(catalog.exercises.size, first)
        seeder.seed(catalog, now = 2)
        assertEquals(first, db.exerciseDao().count())
    }

    @Test
    fun reseedKeepsUserSettingsButUpdatesName() = runTest {
        fun catalog(name: String) = CatalogFile(
            version = "t",
            exercises = listOf(
                CatalogExercise("wg:a", name, ExerciseSource.WORKOUT_GUIDE, tracking = TrackingType.WEIGHT_REPS),
                CatalogExercise("wg:b", "Other", ExerciseSource.WORKOUT_GUIDE, tracking = TrackingType.WEIGHT_REPS),
            ),
        )
        val dao = db.exerciseDao()
        seeder.seed(catalog("Old Name"), now = 1)
        ExerciseRepository(db).updateSettings("wg:a", TrackingType.TIME, 75)
        dao.archive("wg:b", now = 5)

        seeder.seed(catalog("New Name"), now = 10)

        val a = dao.get("wg:a")!!
        assertEquals("New Name", a.name)
        assertEquals(TrackingType.TIME, a.trackingType)
        assertEquals(75, a.defaultRestSec)
        assertTrue(dao.get("wg:b")!!.isArchived)
    }

    @Test
    fun seedIfNeededOnlyOnce() = runTest {
        assertTrue(seeder.seedIfNeeded(now = 1))
        assertNotNull(db.exerciseDao().get(seeder.load().exercises.first().id))
        assertEquals(seeder.load().version, kv.get(CatalogSeeder.KEY_VERSION))
        assertFalse(seeder.seedIfNeeded(now = 2))
    }

    @Test
    fun seedIfNeededSeedsAgainWhenVersionKeyIsGone() = runTest {
        assertTrue(seeder.seedIfNeeded(now = 1))
        kv.remove(CatalogSeeder.KEY_VERSION)
        assertTrue(seeder.seedIfNeeded(now = 2))
    }

    private companion object {
        const val RESOURCES_DIR = "../shared/src/commonMain/composeResources/files/"
        const val CATALOG_DIR = RESOURCES_DIR + "catalog/"
    }
}
