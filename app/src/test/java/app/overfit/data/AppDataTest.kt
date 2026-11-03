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
import java.io.File

@RunWith(RobolectricTestRunner::class)
class AppDataTest {
    private lateinit var db: OverfitDatabase
    private lateinit var data: AppData

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder<OverfitDatabase>(ApplicationProvider.getApplicationContext())
            .allowMainThreadQueries()
            .build()
        data = AppData(
            db,
            InMemoryKeyValueStore(),
            readCatalog = { File("../shared/src/commonMain/composeResources/files/catalog/exercises.json").readBytes() },
        )
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun resetAllRemovesUserDataAndRestoresCatalog() = runTest {
        assertTrue(data.seedCatalog().getOrThrow())
        val catalogSize = db.exerciseDao().count()
        assertTrue(catalogSize > 0)

        val customId = data.exercises.createCustom(CustomExerciseInput("Mine", TrackingType.WEIGHT_REPS, 60, null, null))
        val custom = data.exercises.get(customId)!!
        val sets = listOf(PlannedSet(weightKg = 50.0, reps = 5))
        data.templates.save(Template(0, "T", null, listOf(TemplateExercise(custom, null, null, sets))))
        assertEquals(1, data.templates.templates.first().size)
        assertEquals(catalogSize + 1, db.exerciseDao().count())

        data.resetAll()

        assertTrue(data.templates.templates.first().isEmpty())
        assertNull(data.exercises.get(customId))
        assertEquals(catalogSize, db.exerciseDao().count())
        assertNotNull(db.exerciseDao().get(data.catalog.load().exercises.first().id))
    }
}
