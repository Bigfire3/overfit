package app.overfit.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.overfit.data.db.OverfitDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/** Push/Pull/Legs are created once (first start and existing installs), can be deleted and do not come back. */
@RunWith(RobolectricTestRunner::class)
class SamplesSeedTest {
    private lateinit var db: OverfitDatabase
    private lateinit var kv: InMemoryKeyValueStore
    private lateinit var data: AppData

    @Before
    fun setUp() = runTest {
        db = Room.inMemoryDatabaseBuilder<OverfitDatabase>(ApplicationProvider.getApplicationContext()).allowMainThreadQueries().build()
        kv = InMemoryKeyValueStore()
        data = AppData(
            db, kv,
            readCatalog = { File("../shared/src/commonMain/composeResources/files/catalog/exercises.json").readBytes() },
            readSample = { File("../shared/src/commonMain/composeResources/files/samples/$it.json").readBytes() },
        )
        data.seedCatalog()
    }

    @After
    fun tearDown() = db.close()

    private suspend fun names() = data.templates.templates.first().map { it.name }

    @Test
    fun seedsOnceAndDeletedWorkoutsStayGone() = runTest {
        assertEquals(3, data.seedSamplesOnce().getOrThrow())
        assertEquals(listOf("Push", "Pull", "Legs"), names())
        data.templates.delete(data.templates.templates.first().first { it.name == "Pull" }.id)
        assertEquals(0, data.seedSamplesOnce().getOrThrow()) // flag set: nothing comes back
        assertEquals(listOf("Push", "Legs"), names())
        // The explicit "Add sample workouts" entry still brings them back on request.
        assertEquals(1, data.importer.addSamples())
        assertEquals(3, names().size)
    }

    @Test
    fun existingInstallGetsThemExactlyOnce() = runTest {
        // An existing install: own workout, no flag yet.
        data.importer.addSamples()
        data.templates.delete(data.templates.templates.first().first { it.name == "Push" }.id)
        assertEquals(1, data.seedSamplesOnce().getOrThrow())
        assertEquals(0, data.seedSamplesOnce().getOrThrow())
        assertEquals(3, names().size)
    }
}
