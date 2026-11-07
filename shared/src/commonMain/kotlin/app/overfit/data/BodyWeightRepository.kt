package app.overfit.data

import app.overfit.data.db.BodyWeightEntity
import app.overfit.data.db.OverfitDatabase
import app.overfit.data.db.transaction
import app.overfit.domain.WeightEntry
import app.overfit.domain.round1
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate

/**
 * Body-weight log (table `body_weight`, DB v10): at most one entry per day, a new one replaces the old one, kg inside.
 * The table is the source of truth. [AppStore.weights] is only a mirror of it (kept by [syncProfileWeights]) so the
 * existing AI paths (coach profile block, chat sync, "copy context") keep reading the weight synchronously.
 */
class BodyWeightRepository(private val database: OverfitDatabase, private val clock: () -> Long) {
    private val dao get() = database.bodyWeightDao()

    /** All entries, oldest first. */
    val entries: Flow<List<WeightEntry>> = dao.observeAll().map { list -> list.map { it.toEntry() } }

    suspend fun all(): List<BodyWeightEntity> = dao.all()

    suspend fun get(date: LocalDate): WeightEntry? = dao.get(date.toString())?.toEntry()

    /** Enters [kg] for [date]; replaces an existing entry of that day. */
    suspend fun set(date: LocalDate, kg: Double) {
        val now = clock()
        // Two decimals: a value typed in lb (stored as kg) shows the same lb again.
        dao.upsert(BodyWeightEntity(date.toString(), kotlin.math.round(kg * 100) / 100, measuredAt = now, updatedAt = now))
    }

    suspend fun delete(date: LocalDate) = dao.delete(date.toString())

    /** Adds [entries] whose day has no entry yet (taking over the old profile list, old backups). */
    suspend fun importMissing(entries: List<WeightEntry>) {
        val existing = dao.all().map { it.date }.toSet()
        val now = clock()
        val add = entries.filter { it.date !in existing && runCatching { LocalDate.parse(it.date) }.isSuccess }
            .distinctBy { it.date }
            .map { BodyWeightEntity(it.date, round1(it.kg), measuredAt = null, updatedAt = now) }
        if (add.isNotEmpty()) dao.upsertAll(add)
    }

    /** Replaces everything with [entries] (backup restore). */
    suspend fun replaceAll(entries: List<BodyWeightEntity>) = database.transaction {
        dao.deleteAll()
        if (entries.isNotEmpty()) dao.upsertAll(entries.distinctBy { it.date })
    }

    suspend fun deleteAll() = dao.deleteAll()

    private fun BodyWeightEntity.toEntry() = WeightEntry(date, weightKg)

    companion object {
        /** Set once the profile weight list was copied into the table. */
        const val KEY_PROFILE_IMPORTED = "body_weight.profile_imported_v1"
    }
}

/**
 * Runs for the app's lifetime: once copies the weights from the profile (before DB v10 the weight lived only there)
 * into the table, then keeps [AppStore.weights] equal to the table.
 */
suspend fun syncProfileWeights(data: AppData, store: AppStore, kv: KeyValueStore) {
    if (kv.get(BodyWeightRepository.KEY_PROFILE_IMPORTED) == null) {
        data.bodyWeights.importMissing(store.state.value.weights)
        kv.put(BodyWeightRepository.KEY_PROFILE_IMPORTED, "1")
    }
    data.bodyWeights.entries.collect { store.mirrorWeights(it) }
}
