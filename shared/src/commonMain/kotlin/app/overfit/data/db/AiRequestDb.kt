package app.overfit.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * One AI request (coach review, chat, autofill …), for the "Abrechnung" list: when, what for, which model, tokens, cost,
 * status. Only metadata, never prompt or answer. Written on the device for every provider; credit requests also come
 * from the server log (`GET /v1/account/requests`, [serverId]), so requests of other devices of the account show up too.
 */
@Entity(tableName = "ai_request", indices = [Index("startedAt"), Index(value = ["serverId"], unique = true)])
data class AiRequestEntity(
    @PrimaryKey val id: String,
    /** Start of the request (epoch ms). */
    val startedAt: Long,
    val durationMs: Long? = null,
    /** [app.overfit.ai.ProviderId] name (FUEL = credit). */
    val providerId: String,
    /** review | chat | autofill | plan | other */
    val purpose: String,
    /** Model asked for (credit: model of the chosen tier). */
    val requestedModel: String,
    /** Model that answered; differs from [requestedModel] when a replacement answered. Null when the request failed. */
    val servedModel: String? = null,
    /** OK | FAILED */
    val status: String,
    /** [app.overfit.coach.CoachErrorKind] name or server error code. */
    val errorKind: String? = null,
    /** Why the first model did not answer (credit fallback; free of charge), e.g. "HTTP 529: Overloaded". */
    val fallbackReason: String? = null,
    val inputTokens: Long? = null,
    val outputTokens: Long? = null,
    val cacheReadTokens: Long? = null,
    val cacheWriteTokens: Long? = null,
    /** Charged credit in µ€ (credit only; null for own keys). */
    val costMicro: Long? = null,
    /** Paid with a gifted review (invite bonus). */
    val gift: Boolean = false,
    /** ID of the server log entry (credit), null for own keys and requests that never reached the server. */
    val serverId: Long? = null,
)

@Dao
interface AiRequestDao {
    @Query("SELECT * FROM ai_request ORDER BY startedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int = 500): Flow<List<AiRequestEntity>>

    @Query("SELECT * FROM ai_request WHERE id = :id")
    suspend fun get(id: String): AiRequestEntity?

    @Query("SELECT * FROM ai_request WHERE serverId = :serverId")
    suspend fun byServerId(serverId: Long): AiRequestEntity?

    @Upsert
    suspend fun upsert(entry: AiRequestEntity)

    /** Keeps the list small: everything older than [beforeMs] goes. */
    @Query("DELETE FROM ai_request WHERE startedAt < :beforeMs")
    suspend fun deleteOlderThan(beforeMs: Long)

    @Query("DELETE FROM ai_request")
    suspend fun clear()
}
