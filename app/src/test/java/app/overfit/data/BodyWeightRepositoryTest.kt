package app.overfit.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.overfit.data.db.OverfitDatabase
import app.overfit.domain.Language
import app.overfit.domain.WeightEntry
import app.overfit.domain.WeightUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BodyWeightRepositoryTest {
    private lateinit var db: OverfitDatabase
    private lateinit var data: AppData
    private val kv = InMemoryKeyValueStore()
    private var now = 1_000L

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder<OverfitDatabase>(ApplicationProvider.getApplicationContext())
            .allowMainThreadQueries()
            .build()
        data = AppData(db, kv, readCatalog = { ByteArray(0) }, clock = { now })
    }

    @After
    fun tearDown() = db.close()

    private val day = LocalDate(2026, 11, 7)

    @Test
    fun setReplacesTheEntryOfTheSameDayAndDeleteRemovesIt() = runTest {
        val repo = data.bodyWeights
        repo.set(day, 82.44)
        now = 2_000L
        repo.set(day, 81.96)
        repo.set(LocalDate(2026, 11, 1), 83.0)
        assertEquals(listOf(WeightEntry("2026-11-01", 83.0), WeightEntry("2026-11-07", 81.96)), repo.entries.first())
        val row = repo.all().last()
        assertEquals(2_000L, row.measuredAt)

        repo.delete(day)
        assertNull(repo.get(day))
        assertEquals(1, repo.all().size)
    }

    @Test
    fun resetAllDeletesBodyWeight() = runTest {
        data.bodyWeights.set(day, 80.0)
        data.resetAll(reseedSamples = false)
        assertEquals(0, data.bodyWeights.all().size)
    }

    @Test
    fun profileWeightsAreTakenOverOnceThenTheProfileMirrorsTheTable() = runTest {
        val store = AppStore(InMemoryKeyValueStore(), InMemorySecretStore(), DeviceDefaults(Language.DE, WeightUnit.KG))
        store.logWeight(84.0, LocalDate(2026, 10, 1)) // weight from onboarding, before DB v10
        data.bodyWeights.set(LocalDate(2026, 10, 1), 85.0) // the table wins on the same day
        val job = launch { syncProfileWeights(data, store, kv) }
        val deadline0 = System.currentTimeMillis() + 5_000
        while (store.state.value.weights.firstOrNull()?.kg != 85.0 && System.currentTimeMillis() < deadline0) {
            testScheduler.advanceUntilIdle(); Thread.sleep(10)
        }
        assertEquals(listOf(WeightEntry("2026-10-01", 85.0)), store.state.value.weights)

        data.bodyWeights.set(day, 82.0)
        // Room's flow re-emits off the test scheduler; wait for the mirror.
        val deadline = System.currentTimeMillis() + 5_000
        while (store.state.value.weights.size < 2 && System.currentTimeMillis() < deadline) {
            testScheduler.advanceUntilIdle(); Thread.sleep(10)
        }
        assertEquals(listOf("2026-10-01", "2026-11-07"), store.state.value.weights.map { it.date })
        job.cancel()

        // Second start: no second import (a profile weight deleted in the calendar does not come back).
        data.bodyWeights.delete(LocalDate(2026, 10, 1))
        store.logWeight(70.0, LocalDate(2026, 9, 1))
        val again = launch { syncProfileWeights(data, store, kv) }
        val deadline2 = System.currentTimeMillis() + 5_000
        while (store.state.value.weights.map { it.date } != listOf("2026-11-07") && System.currentTimeMillis() < deadline2) {
            testScheduler.advanceUntilIdle(); Thread.sleep(10)
        }
        assertEquals(listOf("2026-11-07"), store.state.value.weights.map { it.date })
        again.cancel()
    }
}
