package app.overfit.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

// --- Body weight (schema v10) --------------------------------------------------------------------

/**
 * One body-weight measurement. At most one per day: [date] (ISO, e.g. "2026-11-07") is the key, a new entry on the
 * same day replaces the old one. Always stored in kg; the profile unit only changes the display.
 * [measuredAt] = when it was entered (epoch ms, optional time of day; null for entries taken over from the profile).
 */
@Entity(tableName = "body_weight")
data class BodyWeightEntity(
    @PrimaryKey val date: String,
    val weightKg: Double,
    val measuredAt: Long?,
    val updatedAt: Long,
)

@Dao
interface BodyWeightDao {
    @Query("SELECT * FROM body_weight ORDER BY date")
    fun observeAll(): Flow<List<BodyWeightEntity>>

    @Query("SELECT * FROM body_weight ORDER BY date")
    suspend fun all(): List<BodyWeightEntity>

    @Query("SELECT * FROM body_weight WHERE date = :date")
    suspend fun get(date: String): BodyWeightEntity?

    @Upsert
    suspend fun upsert(entry: BodyWeightEntity)

    @Upsert
    suspend fun upsertAll(entries: List<BodyWeightEntity>)

    @Query("DELETE FROM body_weight WHERE date = :date")
    suspend fun delete(date: String)

    @Query("DELETE FROM body_weight")
    suspend fun deleteAll()
}
