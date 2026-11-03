package app.overfit.chat

import app.overfit.data.WorkoutRecord
import app.overfit.domain.Profile
import app.overfit.domain.Template
import app.overfit.domain.TrackingType
import app.overfit.domain.Workout
import app.overfit.domain.WorkoutExercise
import app.overfit.domain.WorkoutSet
import app.overfit.domain.ExerciseOrigin
import app.overfit.domain.Language
import app.overfit.domain.SetValues
import app.overfit.fuel.FuelException
import app.overfit.workout.exercise
import app.overfit.workout.entry
import app.overfit.workout.planned
import app.overfit.workout.templateOf

internal fun record(
    id: String,
    startedAt: Long,
    ended: Boolean = true,
    exerciseName: String = "Bench",
    sets: List<SetValues> = listOf(SetValues(60.0, 8, null)),
    note: String? = null,
): WorkoutRecord {
    val ex = exercise("e-$exerciseName", TrackingType.WEIGHT_REPS, name = exerciseName)
    val w = Workout(
        id = id, name = "Push", templateId = 7, startedAt = startedAt, note = note,
        exercises = listOf(
            WorkoutExercise(
                id = "we-$id", exercise = ex, restSec = 90, note = "", origin = ExerciseOrigin.PLANNED,
                sets = sets.mapIndexed { i, v -> WorkoutSet("s$id$i", target = v, actual = v, completed = true, completedAt = startedAt + 1000L * (i + 1)) },
            ),
        ),
    )
    return WorkoutRecord(w, endedAt = if (ended) startedAt + 3_600_000 else null, feeling = 4, updatedAt = startedAt + 10)
}

internal fun template(id: Long = 7, name: String = "Push"): Template =
    templateOf(entry(exercise("e1", name = "Bench"), 120, planned(60.0, 8), planned(60.0, 8)), name = name).copy(id = id)

internal class FakeSource(
    var templates: List<Template> = emptyList(),
    var workouts: List<WorkoutRecord> = emptyList(),
    var memory: String? = null,
    var photos: List<ChatPhotoRef> = emptyList(),
) : ChatDataSource {
    var profileName = "Fabian"
    override suspend fun templates() = templates
    override suspend fun workouts() = workouts
    override suspend fun workout(id: String) = workouts.firstOrNull { it.workout.id == id }
    override fun profile(now: Long) = ChatDocs.profile(Profile(name = profileName, birthYear = 1990), emptyList(), Language.DE, "Europe/Berlin", now)
    override fun memory() = memory
    override suspend fun photos() = photos
    override suspend fun photoJpeg(id: String) = byteArrayOf(1, 2, 3)
}

internal class FakeChat : ChatService {
    data class Call(val reset: Boolean, val upserts: List<SyncDoc>, val deletes: List<SyncDelete>)
    val calls = mutableListOf<Call>()
    val photoPuts = mutableListOf<String>()
    val photoDeletes = mutableListOf<String>()
    var failWith: FuelException? = null
    var enabledWith: Boolean? = null
    var disabled = false
    val acked = mutableListOf<String>()
    var inbox = InboxPage(emptyList(), emptyList())

    override suspend fun status() = ChatStatus(enabled = enabledWith != null)
    override suspend fun enable(photos: Boolean): ChatStatus { enabledWith = photos; return ChatStatus(enabled = true, photos = photos) }
    override suspend fun disable() { disabled = true }
    override suspend fun sync(reset: Boolean, upserts: List<SyncDoc>, deletes: List<SyncDelete>): SyncResult {
        failWith?.let { throw it }
        calls += Call(reset, upserts, deletes)
        return SyncResult(applied = upserts.size, deleted = deletes.size, lastSyncAt = 1_762_128_500)
    }
    override suspend fun putPhoto(id: String, takenAt: Long, workoutId: String?, jpeg: ByteArray) { photoPuts += id }
    override suspend fun deletePhoto(id: String) { photoDeletes += id }
    override suspend fun pairing() = PairingCode("K7QM3XWP", 0, 600)
    override suspend fun revoke(grantId: String) = Unit
    override suspend fun inbox() = inbox
    override suspend fun ack(ids: List<String>) { acked += ids }
}
