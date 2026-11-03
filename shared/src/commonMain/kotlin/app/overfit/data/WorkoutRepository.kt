package app.overfit.data

import app.overfit.data.db.OverfitDatabase
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
) : WorkoutStore {
    private val dao = db.workoutDao()

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
                photoPath = existing?.photoPath,
                restStartedAt = workout.rest?.startedAt,
                restEndsAt = workout.rest?.endsAt,
                restAfterSetId = workout.rest?.afterSetId,
                updatedAt = clock(),
            ),
        )
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
}

/** A workout plus what the sync needs: [endedAt] is null while it runs. */
data class WorkoutRecord(val workout: Workout, val endedAt: Long?, val feeling: Int?, val updatedAt: Long)

private fun SessionWithExercises.toRecord() = WorkoutRecord(
    workout = toDomain(),
    endedAt = if (session.status == SessionStatus.FINISHED) session.endedAt else null,
    feeling = session.feeling,
    updatedAt = session.updatedAt,
)

private fun SessionWithExercises.toDomain(): Workout {
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
