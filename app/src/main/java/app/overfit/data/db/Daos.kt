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

    @Query("SELECT COUNT(*) FROM template_exercise WHERE exerciseId = :id")
    suspend fun usageCount(id: String): Int

    @Query("DELETE FROM exercise WHERE id = :id")
    suspend fun delete(id: String)
}

data class ExerciseImageRef(val id: String, val imageRef: String)

@Dao
interface TemplateDao {
    @Query("SELECT * FROM workout_template ORDER BY position, name COLLATE NOCASE")
    fun observeTemplates(): Flow<List<WorkoutTemplateEntity>>

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

    @Query("DELETE FROM template_exercise WHERE templateId = :templateId")
    suspend fun deleteExercisesOf(templateId: Long)

    @Insert
    suspend fun insertExercise(entry: TemplateExerciseEntity): Long

    @Insert
    suspend fun insertSets(sets: List<TemplateSetEntity>)
}
