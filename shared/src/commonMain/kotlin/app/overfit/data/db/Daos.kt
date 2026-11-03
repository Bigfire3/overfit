package app.overfit.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import app.overfit.domain.TrackingType
import kotlinx.coroutines.flow.Flow

@Dao
interface ExerciseDao {
    @Query("SELECT * FROM exercise WHERE isArchived = 0 ORDER BY name COLLATE NOCASE")
    fun observeActive(): Flow<List<ExerciseEntity>>

    @Query("SELECT * FROM exercise WHERE isArchived = 0 ORDER BY name COLLATE NOCASE")
    suspend fun getActive(): List<ExerciseEntity>

    @Query("SELECT * FROM exercise WHERE id = :id")
    suspend fun get(id: String): ExerciseEntity?

    @Query("SELECT * FROM exercise WHERE id IN (:ids)")
    suspend fun getAll(ids: List<String>): List<ExerciseEntity>

    @Query("SELECT id, imageRef FROM exercise WHERE isArchived = 0 AND imageRef LIKE 'http%'")
    suspend fun remoteImages(): List<ExerciseImageRef>

    @Query("SELECT COUNT(*) FROM exercise")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(items: List<ExerciseEntity>)

    @Update(entity = ExerciseEntity::class)
    suspend fun updateCatalogFields(items: List<ExerciseCatalogFields>)

    @Upsert
    suspend fun upsert(item: ExerciseEntity)

    @Query(
        "UPDATE exercise SET trackingType = :trackingType, defaultRestSec = :restSec, updatedAt = :now WHERE id = :id",
    )
    suspend fun updateSettings(id: String, trackingType: TrackingType, restSec: Int?, now: Long)

    @Query("UPDATE exercise SET isArchived = 1, updatedAt = :now WHERE id = :id")
    suspend fun archive(id: String, now: Long)

    /** References from templates and workouts (both block deleting the exercise). */
    @Query(
        """SELECT (SELECT COUNT(*) FROM template_exercise WHERE exerciseId = :id) +
                  (SELECT COUNT(*) FROM session_exercise WHERE exerciseId = :id)""",
    )
    suspend fun usageCount(id: String): Int

    @Query("DELETE FROM exercise WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM exercise")
    suspend fun deleteAll()
}

data class ExerciseImageRef(val id: String, val imageRef: String)

@Dao
interface TemplateDao {
    @Query("SELECT * FROM workout_template ORDER BY position, name COLLATE NOCASE")
    fun observeTemplates(): Flow<List<WorkoutTemplateEntity>>

    @Query("SELECT name FROM workout_template")
    suspend fun getNames(): List<String>

    @Query(
        """SELECT te.templateId AS templateId, e.name AS name FROM template_exercise te
           JOIN exercise e ON e.id = te.exerciseId ORDER BY te.templateId, te.position""",
    )
    fun observeTemplateExerciseNames(): Flow<List<TemplateExerciseName>>

    @Transaction
    @Query("SELECT * FROM workout_template WHERE id = :id")
    suspend fun getWithExercises(id: Long): TemplateWithExercises?

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM workout_template")
    suspend fun nextPosition(): Int

    @Insert
    suspend fun insertTemplate(template: WorkoutTemplateEntity): Long

    @Update
    suspend fun updateTemplate(template: WorkoutTemplateEntity)

    @Query("DELETE FROM workout_template WHERE id = :id")
    suspend fun deleteTemplate(id: Long)

    /** Children go with them (CASCADE). */
    @Query("DELETE FROM workout_template")
    suspend fun deleteAll()

    @Query("DELETE FROM template_exercise WHERE templateId = :templateId")
    suspend fun deleteExercisesOf(templateId: Long)

    @Insert
    suspend fun insertExercise(entry: TemplateExerciseEntity): Long

    @Insert
    suspend fun insertSets(sets: List<TemplateSetEntity>)
}

@Dao
interface WorkoutDao {
    @Transaction
    @Query("SELECT * FROM workout_session WHERE status = 'ACTIVE' ORDER BY startedAt DESC LIMIT 1")
    suspend fun getActive(): SessionWithExercises?

    @Query("SELECT id FROM workout_session WHERE status = 'ACTIVE' LIMIT 1")
    fun observeActiveId(): Flow<String?>

    @Transaction
    @Query("SELECT * FROM workout_session WHERE id = :id")
    suspend fun get(id: String): SessionWithExercises?

    @Upsert
    suspend fun upsertSession(session: WorkoutSessionEntity)

    @Query("DELETE FROM session_exercise WHERE sessionId = :sessionId")
    suspend fun deleteExercisesOf(sessionId: String)

    @Insert
    suspend fun insertExercises(entries: List<SessionExerciseEntity>)

    @Insert
    suspend fun insertSets(sets: List<SetEntryEntity>)

    @Query("UPDATE workout_session SET status = 'FINISHED', endedAt = :endedAt, restStartedAt = NULL, restEndsAt = NULL, restAfterSetId = NULL, updatedAt = :endedAt WHERE id = :id")
    suspend fun finish(id: String, endedAt: Long)

    /** Children go with it (CASCADE). */
    @Query("DELETE FROM workout_session WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM workout_session")
    suspend fun deleteAll()

    /**
     * Completed sets of [exerciseId] from the most recent finished workout that has any, in order.
     * Feeds "last time" and the prefill of new sets.
     */
    @Query(
        """SELECT s.* FROM set_entry s
           JOIN session_exercise se ON se.id = s.sessionExerciseId
           WHERE se.id = (
               SELECT se2.id FROM session_exercise se2
               JOIN workout_session ws ON ws.id = se2.sessionId
               WHERE se2.exerciseId = :exerciseId AND ws.status = 'FINISHED'
                 AND EXISTS (SELECT 1 FROM set_entry s2 WHERE s2.sessionExerciseId = se2.id AND s2.completed = 1)
               ORDER BY ws.startedAt DESC, se2.position DESC LIMIT 1
           ) AND s.completed = 1
           ORDER BY s.position""",
    )
    suspend fun lastCompletedSets(exerciseId: String): List<SetEntryEntity>
}
