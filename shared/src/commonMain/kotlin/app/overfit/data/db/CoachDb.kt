package app.overfit.data.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import app.overfit.coach.PlanStatus
import app.overfit.coach.ReviewKind
import app.overfit.coach.ReviewStatus
import kotlinx.coroutines.flow.Flow

// --- Coach (schema v4) ---------------------------------------------------------------------------

/**
 * A coach request and its answer (SPEC "AiReview"). The row exists from the moment the user sends: it doubles as
 * the queue entry ([status] QUEUED) until the answer is there. [reviewJson] is the extracted JSON of the answer
 * (parsed again with CoachParser when shown), [rawResponse] the complete provider response for troubleshooting.
 * [sessionId] has no foreign key on purpose: like the session's templateId it is informational, and progress
 * reviews have none.
 */
@kotlinx.serialization.Serializable
@Entity(tableName = "coach_review", indices = [Index("sessionId"), Index("status"), Index("createdAt")])
data class CoachReviewEntity(
    @PrimaryKey val id: String,
    val sessionId: String?,
    val kind: ReviewKind,
    val status: ReviewStatus,
    val createdAt: Long,
    val updatedAt: Long,
    /** Provider and model of the last attempt. */
    val providerId: String?,
    val model: String?,
    val attempts: Int,
    /** CoachErrorKind of the last failed attempt. */
    val errorKind: String?,
    val errorMessage: String?,
    val rawResponse: String?,
    val reviewJson: String?,
    val planStatus: PlanStatus,
    /** Keys of the plan changes taken over (comma-separated). */
    val acceptedChanges: String?,
    /** Fuel: what the request cost and the balance afterwards (micro-euro). */
    val costMicro: Long?,
    val balanceMicro: Long?,
    /** The user allowed body photos for this request. */
    @ColumnInfo(defaultValue = "0") val withPhotos: Boolean = false,
)

/** One version of the coach memory; the newest row is the current text. */
@kotlinx.serialization.Serializable
@Entity(tableName = "coach_memory", indices = [Index("createdAt")])
data class CoachMemoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    val createdAt: Long,
    /** "COACH" (from a review) or "USER" (edited by hand). */
    val source: String,
    /** Review that wrote this version (COACH). */
    val reviewId: String?,
)

/** A body photo (file in the app-private photo directory) and when its workout started. */
data class CoachPhotoRow(val fileName: String, val startedAt: Long)

@Dao
interface CoachDao {
    @Upsert
    suspend fun upsert(review: CoachReviewEntity)

    @Query("SELECT * FROM coach_review WHERE id = :id")
    suspend fun get(id: String): CoachReviewEntity?

    @Query("SELECT * FROM coach_review WHERE id = :id")
    fun observe(id: String): Flow<CoachReviewEntity?>

    @Query("SELECT * FROM coach_review ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<CoachReviewEntity>>

    @Query("SELECT * FROM coach_review WHERE sessionId = :sessionId ORDER BY createdAt DESC LIMIT 1")
    suspend fun latestForSession(sessionId: String): CoachReviewEntity?

    @Query("SELECT * FROM coach_review WHERE sessionId = :sessionId ORDER BY createdAt DESC LIMIT 1")
    fun observeLatestForSession(sessionId: String): Flow<CoachReviewEntity?>

    @Query("SELECT * FROM coach_review WHERE status IN ('QUEUED', 'SENDING') ORDER BY createdAt")
    suspend fun pending(): List<CoachReviewEntity>

    @Query("DELETE FROM coach_review WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT * FROM coach_review ORDER BY createdAt")
    suspend fun allReviews(): List<CoachReviewEntity>

    @Query("SELECT * FROM coach_memory ORDER BY createdAt, id")
    suspend fun allMemory(): List<CoachMemoryEntity>

    @Query("DELETE FROM coach_review")
    suspend fun deleteAll()

    // Memory

    @Insert
    suspend fun insertMemory(memory: CoachMemoryEntity): Long

    @Query("SELECT * FROM coach_memory ORDER BY createdAt DESC, id DESC LIMIT 1")
    fun observeMemory(): Flow<CoachMemoryEntity?>

    @Query("SELECT * FROM coach_memory ORDER BY createdAt DESC, id DESC LIMIT 1")
    suspend fun currentMemory(): CoachMemoryEntity?

    @Query("SELECT * FROM coach_memory ORDER BY createdAt DESC, id DESC")
    fun observeMemoryVersions(): Flow<List<CoachMemoryEntity>>

    @Query("DELETE FROM coach_memory")
    suspend fun deleteMemory()

    // Reading the training history for the request

    @Transaction
    @Query("SELECT * FROM workout_session WHERE id = :id")
    suspend fun session(id: String): SessionWithExercises?

    @Transaction
    @Query("SELECT * FROM workout_session WHERE status = 'FINISHED' AND templateId = :templateId AND id != :excludeId ORDER BY startedAt DESC LIMIT :limit")
    suspend fun finishedOfTemplate(templateId: Long, excludeId: String, limit: Int): List<SessionWithExercises>

    @Transaction
    @Query("SELECT * FROM workout_session WHERE status = 'FINISHED' AND id != :excludeId ORDER BY startedAt DESC LIMIT 1")
    suspend fun latestFinishedExcept(excludeId: String): SessionWithExercises?

    @Transaction
    @Query("SELECT * FROM workout_session WHERE status = 'FINISHED' AND startedAt >= :since ORDER BY startedAt DESC LIMIT :limit")
    suspend fun finishedSince(since: Long, limit: Int): List<SessionWithExercises>

    /** The last finished session before [before] that contains [exerciseId] (per-exercise review, "last time"). */
    @Transaction
    @Query(
        """SELECT * FROM workout_session WHERE status = 'FINISHED' AND id != :excludeId AND startedAt < :before
           AND id IN (SELECT sessionId FROM session_exercise WHERE exerciseId = :exerciseId)
           ORDER BY startedAt DESC LIMIT 1""",
    )
    suspend fun lastFinishedWith(exerciseId: String, before: Long, excludeId: String): SessionWithExercises?

    @Query("SELECT * FROM workout_session WHERE status = 'FINISHED' ORDER BY startedAt DESC LIMIT 1")
    fun observeLatestFinished(): Flow<WorkoutSessionEntity?>

    /** Newest photo of a finished workout started before [before] (comparison photo). */
    @Query(
        """SELECT p.fileName AS fileName, s.startedAt AS startedAt FROM workout_photo p
           JOIN workout_session s ON s.id = p.sessionId
           WHERE s.status = 'FINISHED' AND s.startedAt <= :before
           ORDER BY s.startedAt DESC, p.position LIMIT 1""",
    )
    suspend fun latestPhotoBefore(before: Long): CoachPhotoRow?

    @Query(
        """SELECT p.fileName AS fileName, s.startedAt AS startedAt FROM workout_photo p
           JOIN workout_session s ON s.id = p.sessionId
           WHERE p.sessionId = :sessionId ORDER BY p.position LIMIT 1""",
    )
    suspend fun firstPhotoOf(sessionId: String): CoachPhotoRow?

    @Query("UPDATE workout_session SET aiStatus = :status WHERE id = :sessionId")
    suspend fun setAiStatus(sessionId: String, status: AiStatus)

    /** Exercises that appear in templates or workouts (they always belong in the coach's exercise list). */
    @Query("SELECT exerciseId FROM template_exercise UNION SELECT exerciseId FROM session_exercise")
    suspend fun usedExerciseIds(): List<String>
}
