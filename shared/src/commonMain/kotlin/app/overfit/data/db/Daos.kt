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

    /** Exercises a backup must carry: custom ones, changed settings and everything referenced. */
    @Query(
        """SELECT * FROM exercise WHERE source = 'CUSTOM' OR defaultRestSec IS NOT NULL OR isArchived = 1
             OR id IN (SELECT exerciseId FROM template_exercise) OR id IN (SELECT exerciseId FROM session_exercise)""",
    )
    suspend fun forBackup(): List<ExerciseEntity>
}

@Dao
interface TemplateDao {
    @Query("SELECT * FROM workout_template ORDER BY position, name COLLATE NOCASE")
    fun observeTemplates(): Flow<List<WorkoutTemplateEntity>>

    @Query("SELECT id FROM workout_template ORDER BY position, name COLLATE NOCASE")
    suspend fun getIds(): List<Long>

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

    @Transaction
    @Query("SELECT * FROM workout_template ORDER BY position")
    suspend fun allWithExercises(): List<TemplateWithExercises>

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

    /** All workouts, oldest first (KI-Chat sync). */
    @Transaction
    @Query("SELECT * FROM workout_session ORDER BY startedAt")
    suspend fun getAll(): List<SessionWithExercises>

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
    suspend fun deleteAllSessions()

    /** Photos without a training (v9) have no session to cascade from: delete them explicitly. */
    @Query("DELETE FROM workout_photo")
    suspend fun deleteAllPhotos()

    /** All workouts and all photos (photo files are removed by the caller). */
    @Transaction
    suspend fun deleteAll() {
        deleteAllPhotos()
        deleteAllSessions()
    }

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

    /**
     * The last finished performance of [exerciseId] with at least one completed set; with [templateId] only within
     * trainings of that saved workout. Feeds "last time" in the workout plan (and, via [lastCompletedSets], the
     * running workout).
     */
    @Query(
        """SELECT se2.id AS sessionExerciseId, ws.startedAt AS startedAt FROM session_exercise se2
           JOIN workout_session ws ON ws.id = se2.sessionId
           WHERE se2.exerciseId = :exerciseId AND ws.status = 'FINISHED'
             AND (:templateId IS NULL OR ws.templateId = :templateId)
             AND EXISTS (SELECT 1 FROM set_entry s2 WHERE s2.sessionExerciseId = se2.id AND s2.completed = 1)
           ORDER BY ws.startedAt DESC, se2.position DESC LIMIT 1""",
    )
    suspend fun lastPerformance(exerciseId: String, templateId: Long?): LastPerformanceRow?

    @Query("SELECT * FROM set_entry WHERE sessionExerciseId = :sessionExerciseId AND completed = 1 ORDER BY position")
    suspend fun completedSetsOf(sessionExerciseId: String): List<SetEntryEntity>

    // --- History (phase 3) ---------------------------------------------------------------------

    @Query(
        """SELECT ws.id AS id, ws.name AS name, ws.startedAt AS startedAt, ws.endedAt AS endedAt, ws.feeling AS feeling,
             (SELECT COUNT(*) FROM session_exercise se WHERE se.sessionId = ws.id) AS exerciseCount,
             (SELECT COUNT(*) FROM set_entry s JOIN session_exercise se ON se.id = s.sessionExerciseId
                WHERE se.sessionId = ws.id AND s.completed = 1) AS completedSets,
             (SELECT COALESCE(SUM(s.weightKg * s.reps), 0.0) FROM set_entry s JOIN session_exercise se ON se.id = s.sessionExerciseId
                WHERE se.sessionId = ws.id AND s.completed = 1 AND s.weightKg > 0 AND s.reps > 0) AS volumeKg,
             (SELECT COUNT(*) FROM workout_photo p WHERE p.sessionId = ws.id) AS photoCount,
             ws.sauna AS sauna
           FROM workout_session ws WHERE ws.status = 'FINISHED' ORDER BY ws.startedAt DESC""",
    )
    fun observeHistory(): Flow<List<HistoryRow>>

    /** Start of the last finished workout per saved workout ("last time 3 days ago"). */
    @Query(
        """SELECT templateId, MAX(startedAt) AS lastStartedAt FROM workout_session
           WHERE status = 'FINISHED' AND templateId IS NOT NULL GROUP BY templateId""",
    )
    fun observeLastDone(): Flow<List<TemplateLastDone>>

    @Transaction
    @Query("SELECT * FROM workout_session WHERE status = 'FINISHED' ORDER BY startedAt")
    suspend fun allFinished(): List<SessionWithExercises>

    @Query("UPDATE workout_session SET note = :note, feeling = :feeling, updatedAt = :now WHERE id = :id")
    suspend fun updateDetails(id: String, note: String?, feeling: Int?, now: Long)

    @Query("UPDATE workout_session SET sauna = :sauna, updatedAt = :now WHERE id = :id")
    suspend fun updateSauna(id: String, sauna: Boolean, now: Long)

    @Query("UPDATE workout_session SET name = :name, updatedAt = :now WHERE id = :id")
    suspend fun rename(id: String, name: String, now: Long)

    /** Completed sets per exercise of every finished workout, with the exercise's muscle groups (body map). */
    @Query(
        """SELECT ws.startedAt AS startedAt, e.primaryMuscles AS primaryMuscles, e.secondaryMuscles AS secondaryMuscles,
             COUNT(*) AS sets
           FROM set_entry s JOIN session_exercise se ON se.id = s.sessionExerciseId
           JOIN workout_session ws ON ws.id = se.sessionId
           JOIN exercise e ON e.id = se.exerciseId
           WHERE ws.status = 'FINISHED' AND s.completed = 1
           GROUP BY se.id""",
    )
    fun observeMuscleSets(): Flow<List<MuscleSetEntityRow>>

    /** Completed sets of [exerciseId] in finished workouts, newest workout first. */
    @Query(
        """SELECT ws.id AS sessionId, ws.startedAt AS startedAt, ws.name AS sessionName, se.exerciseId AS exerciseId,
             s.weightKg AS weightKg, s.reps AS reps, s.durationSec AS durationSec, s.position AS position
           FROM set_entry s JOIN session_exercise se ON se.id = s.sessionExerciseId
           JOIN workout_session ws ON ws.id = se.sessionId
           WHERE se.exerciseId = :exerciseId AND ws.status = 'FINISHED' AND s.completed = 1
           ORDER BY ws.startedAt DESC, se.position, s.position""",
    )
    fun observeExerciseSets(exerciseId: String): Flow<List<ExerciseSetRow>>

    /** Completed sets of [exerciseIds] in finished workouts other than [excludeId] that started before [before]. */
    @Query(
        """SELECT ws.id AS sessionId, ws.startedAt AS startedAt, ws.name AS sessionName, se.exerciseId AS exerciseId,
             s.weightKg AS weightKg, s.reps AS reps, s.durationSec AS durationSec, s.position AS position
           FROM set_entry s JOIN session_exercise se ON se.id = s.sessionExerciseId
           JOIN workout_session ws ON ws.id = se.sessionId
           WHERE se.exerciseId IN (:exerciseIds) AND ws.status = 'FINISHED' AND s.completed = 1
             AND ws.id != :excludeId AND ws.startedAt < :before""",
    )
    suspend fun setsBefore(exerciseIds: List<String>, before: Long, excludeId: String): List<ExerciseSetRow>

    // --- Photos --------------------------------------------------------------------------------

    @Insert
    suspend fun insertPhoto(photo: WorkoutPhotoEntity)

    @Insert
    suspend fun insertPhotos(photos: List<WorkoutPhotoEntity>)

    /** Photos of a training that is about to be deleted stay as photos of their day (v9). */
    @Query("UPDATE workout_photo SET sessionId = NULL WHERE sessionId = :sessionId")
    suspend fun detachPhotos(sessionId: String)

    @Query("SELECT * FROM workout_photo WHERE id = :id")
    suspend fun getPhoto(id: String): WorkoutPhotoEntity?

    @Query("DELETE FROM workout_photo WHERE id = :id")
    suspend fun deletePhoto(id: String)

    /** Photos of one calendar day (ISO date), in the order they were added. */
    @Query("SELECT * FROM workout_photo WHERE day = :day ORDER BY takenAt, position")
    suspend fun photosOfDay(day: String): List<WorkoutPhotoEntity>

    @Query("SELECT * FROM workout_photo WHERE day = :day ORDER BY takenAt, position")
    fun observePhotosOfDay(day: String): Flow<List<WorkoutPhotoEntity>>

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM workout_photo WHERE day = :day")
    suspend fun nextPhotoPosition(day: String): Int

    @Query(
        """SELECT p.id AS id, p.sessionId AS sessionId, p.fileName AS fileName, p.takenAt AS takenAt, p.position AS position,
             ws.name AS sessionName, p.day AS day
           FROM workout_photo p LEFT JOIN workout_session ws ON ws.id = p.sessionId
           ORDER BY p.takenAt DESC, p.position DESC""",
    )
    fun observePhotos(): Flow<List<PhotoRow>>

    @Query("SELECT * FROM workout_photo ORDER BY takenAt")
    suspend fun allPhotos(): List<WorkoutPhotoEntity>
}

/** Where an exercise was done last: the exercise entry of that training and when it started. */
data class LastPerformanceRow(val sessionExerciseId: String, val startedAt: Long)
