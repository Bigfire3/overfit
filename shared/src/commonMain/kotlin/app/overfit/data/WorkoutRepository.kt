package app.overfit.data

import app.overfit.data.db.ExerciseSetRow
import app.overfit.data.db.HistoryRow
import app.overfit.data.db.OverfitDatabase
import app.overfit.data.db.WorkoutPhotoEntity
import app.overfit.domain.LoggedSet
import app.overfit.domain.WorkoutStats
import app.overfit.domain.WorkoutSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import app.overfit.data.db.SessionExerciseEntity
import app.overfit.data.db.SessionStatus
import app.overfit.data.db.SessionWithExercises
import app.overfit.data.db.SetEntryEntity
import app.overfit.data.db.WorkoutSessionEntity
import app.overfit.data.db.transaction
import app.overfit.domain.RestTimer
import app.overfit.domain.SetValues
import app.overfit.domain.Workout
import app.overfit.domain.WorkoutExercise
import app.overfit.domain.WorkoutSet
import app.overfit.workout.WorkoutStore
import kotlin.time.Clock

/** Room implementation of [WorkoutStore]. */
class WorkoutRepository(
    private val db: OverfitDatabase,
    private val clock: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    val photoFiles: PhotoFiles = InMemoryPhotoFiles(),
) : WorkoutStore {
    private val dao = db.workoutDao()

    /**
     * Emits whenever workouts, sets or photos change (also once right away). Other features
     * (AI chat sync, coach) listen to this instead of polling.
     */
    val changes: Flow<Unit> =
        db.invalidationTracker.createFlow("workout_session", "session_exercise", "set_entry", "workout_photo").map { }

    // --- History (finished workouts) ---------------------------------------------------------

    /** Finished workouts, newest first. */
    val history: Flow<List<HistoryRow>> = dao.observeHistory()

    /** A finished workout with note, feeling and photos; null if missing or still running. */
    suspend fun getFinished(id: String): FinishedWorkout? {
        val row = dao.get(id) ?: return null
        if (row.session.status != SessionStatus.FINISHED) return null
        return FinishedWorkout(
            workout = row.toDomain(),
            endedAt = row.session.endedAt ?: row.session.updatedAt,
            note = row.session.note,
            feeling = row.session.feeling,
            photos = photosOf(id),
            sauna = row.session.sauna,
        )
    }

    /** Summary with records against all earlier finished workouts. */
    suspend fun summary(id: String): WorkoutSummary? {
        val finished = getFinished(id) ?: return null
        val w = finished.workout
        val ids = w.exercises.map { it.exercise.id }.distinct()
        val previous = if (ids.isEmpty()) emptyList() else dao.setsBefore(ids, w.startedAt, w.id).map { it.toLogged() }
        return WorkoutStats.summarize(w, finished.endedAt, previous)
    }

    /** Note (blank = none) and feeling 1–5 (null = none) after finishing. */
    suspend fun updateDetails(id: String, note: String?, feeling: Int?) {
        dao.updateDetails(id, note?.trim()?.ifEmpty { null }, feeling?.coerceIn(1, 5), clock())
        onChanged?.invoke(id, true)
    }

    /** "Sauna after the workout" (finish flow and history detail). */
    suspend fun setSauna(id: String, sauna: Boolean) {
        dao.updateSauna(id, sauna, clock())
        onChanged?.invoke(id, true)
    }

    suspend fun rename(id: String, name: String) {
        val trimmed = name.trim()
        if (trimmed.isNotEmpty()) dao.rename(id, trimmed.take(80), clock())
    }

    /**
     * Deletes a finished workout with its sets. Its photos stay on their calendar day (they become photos
     * without a training): a photo is removed only on its own, never as a side effect.
     */
    suspend fun deleteWorkout(id: String) {
        db.transaction {
            dao.detachPhotos(id)
            dao.delete(id)
        }
        onChanged?.invoke(id, true)
    }

    /** Completed sets of an exercise in finished workouts, newest workout first. */
    fun exerciseSets(exerciseId: String): Flow<List<ExerciseSetRow>> = dao.observeExerciseSets(exerciseId)

    /** Completed sets per exercise of all finished workouts with their muscle groups (body map in the calendar). */
    val muscleSets: Flow<List<app.overfit.domain.MuscleSetRow>> = dao.observeMuscleSets().map { rows ->
        rows.map { r ->
            app.overfit.domain.MuscleSetRow(
                r.startedAt,
                r.primaryMuscles.mapNotNull { app.overfit.domain.Muscle.fromKey(it) },
                r.secondaryMuscles.mapNotNull { app.overfit.domain.Muscle.fromKey(it) },
                r.sets,
            )
        }
    }

    // --- Photos ------------------------------------------------------------------------------

    /** Photos of one workout in order (AI chat sync, coach). */
    suspend fun photosOf(sessionId: String): List<WorkoutPhoto> = dao.photosOf(sessionId).map { it.toPhoto() }

    fun observePhotosOf(sessionId: String): Flow<List<WorkoutPhoto>> = dao.observePhotosOf(sessionId).map { l -> l.map { it.toPhoto() } }

    /** All photos for the diary, newest first. */
    val photos: Flow<List<DiaryPhoto>> = dao.observePhotos().map { rows ->
        rows.map { DiaryPhoto(WorkoutPhoto(it.id, it.sessionId, it.fileName, it.takenAt, photoFiles.path(it.fileName)), it.sessionName) }
    }

    /** Stores an already prepared JPEG for [sessionId] (null = no training, just the day) in the private photo folder. */
    suspend fun addPhoto(sessionId: String?, jpeg: ByteArray, takenAt: Long = clock()): WorkoutPhoto {
        val id = Workout.newId()
        val name = "$id.jpg"
        photoFiles.write(name, jpeg)
        val entity = WorkoutPhotoEntity(id, sessionId, name, takenAt, dao.nextPhotoPosition(sessionId))
        try {
            dao.insertPhoto(entity)
        } catch (e: Exception) {
            photoFiles.delete(name)
            throw e
        }
        return entity.toPhoto()
    }

    suspend fun deletePhoto(id: String) {
        val photo = dao.getPhoto(id) ?: return
        dao.deletePhoto(id)
        photoFiles.delete(photo.fileName)
    }

    fun readPhoto(photo: WorkoutPhoto): ByteArray? = photoFiles.read(photo.fileName)

    /** All photos, oldest first (AI chat sync). */
    suspend fun allPhotos(): List<WorkoutPhoto> = dao.allPhotos().map { it.toPhoto() }

    /** JPEG of a photo by id, null if it is gone. */
    suspend fun photoJpeg(id: String): ByteArray? = dao.getPhoto(id)?.let { photoFiles.read(it.fileName) }

    private fun WorkoutPhotoEntity.toPhoto() = WorkoutPhoto(id, sessionId, fileName, takenAt, photoFiles.path(fileName))
    /** Called after a workout was saved, finished or deleted (KI-Chat sync); `finished` = ended or discarded. */
    var onChanged: ((id: String, finished: Boolean) -> Unit)? = null

    override suspend fun loadActive(): Workout? = dao.getActive()?.toDomain()

    suspend fun get(id: String): Workout? = dao.get(id)?.toDomain()

    /** One workout with the facts the domain model does not carry (end time, feeling); null if it does not exist. */
    suspend fun record(id: String): WorkoutRecord? = dao.get(id)?.toRecord()

    /** All workouts (running and finished) for the KI-Chat sync. */
    suspend fun allRecords(): List<WorkoutRecord> = dao.getAll().map { it.toRecord() }

    suspend fun status(id: String): SessionStatus? = dao.get(id)?.session?.status

    /**
     * Writes the whole workout in one transaction: the session row is upserted, exercises and
     * sets are rewritten. A workout has a few dozen rows, so this stays fast and leaves no
     * half-saved state behind if the process dies.
     */
    override suspend fun save(workout: Workout) {
        db.transaction { write(workout) }
        onChanged?.invoke(workout.id, false)
    }

    private suspend fun write(workout: Workout) {
        val existing = dao.get(workout.id)?.session
        // A finished workout is never turned back into an active one by a late write.
        if (existing?.status == SessionStatus.FINISHED) return
        writeSession(workout, existing)
        writeExercises(workout)
    }

    private suspend fun writeSession(workout: Workout, existing: WorkoutSessionEntity?) {
        dao.upsertSession(
            WorkoutSessionEntity(
                id = workout.id,
                name = workout.name,
                templateId = workout.templateId,
                status = SessionStatus.ACTIVE,
                startedAt = workout.startedAt,
                endedAt = null,
                note = workout.note,
                feeling = existing?.feeling,
                sauna = existing?.sauna ?: false,
                photoPath = existing?.photoPath,
                restStartedAt = workout.rest?.startedAt,
                restEndsAt = workout.rest?.endsAt,
                restAfterSetId = workout.rest?.afterSetId,
                updatedAt = clock(),
            ),
        )
    }

    /** Rewrites exercises and sets of [workout] (the session row must exist). */
    private suspend fun writeExercises(workout: Workout) {
        dao.deleteExercisesOf(workout.id)
        dao.insertExercises(
            workout.exercises.mapIndexed { i, e ->
                SessionExerciseEntity(
                    id = e.id,
                    sessionId = workout.id,
                    exerciseId = e.exercise.id,
                    position = i,
                    restSec = e.restSec,
                    note = e.note.trim().ifEmpty { null },
                    origin = e.origin,
                    replacedExerciseId = e.replacedExerciseId,
                    skipped = e.skipped,
                )
            },
        )
        dao.insertSets(
            workout.exercises.flatMap { e ->
                e.sets.mapIndexed { i, s ->
                    SetEntryEntity(
                        id = s.id,
                        sessionExerciseId = e.id,
                        position = i,
                        targetWeightKg = s.target.weightKg,
                        targetReps = s.target.reps,
                        targetDurationSec = s.target.durationSec,
                        weightKg = s.actual.weightKg,
                        reps = s.actual.reps,
                        durationSec = s.actual.durationSec,
                        completed = s.completed,
                        completedAt = s.completedAt,
                        rpe = null,
                    )
                }
            },
        )
    }

    /**
     * Saves a finished workout edited afterwards (calendar → training → edit): sets, note, feeling
     * and sauna. Status, start/end, name and AI status stay; photos are untouched. Statistics, bests
     * and the coach context are read from these rows, so they follow; stored coach reviews do not
     * change. False if the workout is missing or not finished.
     */
    suspend fun updateFinished(workout: Workout, note: String?, feeling: Int?, sauna: Boolean): Boolean {
        val ok = db.transaction {
            val existing = dao.get(workout.id)?.session
            if (existing?.status != SessionStatus.FINISHED) return@transaction false
            dao.upsertSession(
                existing.copy(
                    // Renaming a past training changes only this training, never its saved workout.
                    name = workout.name.trim().take(80).ifEmpty { existing.name },
                    note = note?.trim()?.ifEmpty { null },
                    feeling = feeling?.coerceIn(1, 5),
                    sauna = sauna,
                    updatedAt = clock(),
                ),
            )
            writeExercises(workout)
            true
        }
        if (ok) onChanged?.invoke(workout.id, true)
        return ok
    }

    override suspend fun finish(workout: Workout, endedAt: Long) {
        db.transaction {
            write(workout)
            dao.finish(workout.id, endedAt)
        }
        onChanged?.invoke(workout.id, true)
    }

    override suspend fun delete(id: String) {
        dao.delete(id)
        onChanged?.invoke(id, true)
    }

    override suspend fun lastSets(exerciseId: String): List<SetValues> =
        dao.lastCompletedSets(exerciseId).map { SetValues(it.weightKg, it.reps, it.durationSec) }

    /**
     * "Last time" for the workout plan: the completed sets of [exerciseId] from the last training of the saved workout
     * [templateId], otherwise from the last training at all ([LastPerformance.inThisWorkout] false). null = never done.
     */
    suspend fun lastPerformance(exerciseId: String, templateId: Long?): LastPerformance? {
        val inWorkout = templateId?.let { dao.lastPerformance(exerciseId, it) }
        val row = inWorkout ?: dao.lastPerformance(exerciseId, null) ?: return null
        val sets = dao.completedSetsOf(row.sessionExerciseId).map { SetValues(it.weightKg, it.reps, it.durationSec) }
        return LastPerformance(row.startedAt, sets, inThisWorkout = inWorkout != null)
    }
}

/** The completed sets of an exercise from one earlier training ([startedAt]); see [WorkoutRepository.lastPerformance]. */
data class LastPerformance(val startedAt: Long, val sets: List<SetValues>, val inThisWorkout: Boolean)

/** A finished workout as shown in the history detail. */
data class FinishedWorkout(
    val workout: Workout,
    val endedAt: Long,
    val note: String?,
    val feeling: Int?,
    val photos: List<WorkoutPhoto>,
    val sauna: Boolean = false,
)

/** Photo of a workout; [path] is the absolute file path for the image loader. */
data class WorkoutPhoto(
    val id: String,
    /** null = photo of a calendar day without a training. */
    val sessionId: String?,
    val fileName: String,
    val takenAt: Long,
    val path: String,
)

/** [workoutName] is null for a photo without a training. */
data class DiaryPhoto(val photo: WorkoutPhoto, val workoutName: String?)

internal fun ExerciseSetRow.toLogged() = LoggedSet(exerciseId, SetValues(weightKg, reps, durationSec))

/** A workout plus what the sync needs: [endedAt] is null while it runs. */
data class WorkoutRecord(val workout: Workout, val endedAt: Long?, val feeling: Int?, val updatedAt: Long, val sauna: Boolean = false)

private fun SessionWithExercises.toRecord() = WorkoutRecord(
    workout = toDomain(),
    endedAt = if (session.status == SessionStatus.FINISHED) session.endedAt else null,
    feeling = session.feeling,
    updatedAt = session.updatedAt,
    sauna = session.sauna,
)

internal fun SessionWithExercises.toDomain(): Workout {
    val restStart = session.restStartedAt
    val restEnd = session.restEndsAt
    return Workout(
        id = session.id,
        name = session.name,
        templateId = session.templateId,
        startedAt = session.startedAt,
        note = session.note,
        rest = if (restStart != null && restEnd != null) RestTimer(restStart, restEnd, session.restAfterSetId) else null,
        exercises = exercises.sortedBy { it.entry.position }.map { e ->
            WorkoutExercise(
                id = e.entry.id,
                exercise = e.exercise.toDomain(),
                restSec = e.entry.restSec,
                note = e.entry.note.orEmpty(),
                origin = e.entry.origin,
                replacedExerciseId = e.entry.replacedExerciseId,
                skipped = e.entry.skipped,
                sets = e.sets.sortedBy { it.position }.map { s ->
                    WorkoutSet(
                        id = s.id,
                        target = SetValues(s.targetWeightKg, s.targetReps, s.targetDurationSec),
                        actual = SetValues(s.weightKg, s.reps, s.durationSec),
                        completed = s.completed,
                        completedAt = s.completedAt,
                    )
                },
            )
        },
    )
}
