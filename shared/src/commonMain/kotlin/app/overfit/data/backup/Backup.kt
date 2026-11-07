package app.overfit.data.backup

import app.overfit.data.AppData
import app.overfit.data.AppStore
import app.overfit.data.PersistedState
import app.overfit.data.db.AiStatus
import app.overfit.data.db.BodyWeightEntity
import app.overfit.data.db.ExerciseEntity
import app.overfit.data.db.SessionExerciseEntity
import app.overfit.data.db.SessionStatus
import app.overfit.data.db.SetEntryEntity
import app.overfit.data.db.TemplateExerciseEntity
import app.overfit.data.db.TemplateSetEntity
import app.overfit.data.db.WorkoutPhotoEntity
import app.overfit.data.db.WorkoutSessionEntity
import app.overfit.data.db.WorkoutTemplateEntity
import app.overfit.data.db.transaction
import app.overfit.data.isSafePhotoName
import app.overfit.domain.ExerciseOrigin
import app.overfit.domain.ExerciseSource
import app.overfit.domain.TrackingType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/**
 * Backup file (`overfit-backup-<date>.zip`): `backup.json` (all training data, profile and
 * settings) plus `photos/<file>.jpg`. Never contains API keys, the device key or Fuel tokens.
 * Format and decisions: docs/DECISIONS.md („Backup“).
 */
@Serializable
data class BackupJson(
    val format: String = FORMAT,
    val v: Int = VERSION,
    val createdAt: Long,
    val appVersion: String = "",
    val state: PersistedState? = null,
    val exercises: List<BExercise> = emptyList(),
    val templates: List<BTemplate> = emptyList(),
    val workouts: List<BWorkout> = emptyList(),
    val photos: List<BPhoto> = emptyList(),
    /**
     * Body-weight log (DB v10). Older backups have none; their weights are in [PersistedState.weights] and come back
     * from there. Newer backups carry both, so an older app still restores the weights into its profile.
     */
    val bodyWeights: List<BBodyWeight> = emptyList(),
    /** Data of other features (e.g. coach memory), see [BackupContributor]. */
    val extras: Map<String, JsonElement> = emptyMap(),
) {
    companion object {
        const val FORMAT = "overfit-backup"
        /** Newest version this app reads and writes. */
        const val VERSION = 2

        /**
         * v1: every photo belongs to a training. v2: photos without a training (calendar day, DB v9). An app that only
         * knows v1 cannot read a photo without `workoutId`; it says "update the app" instead of "unreadable". Backups
         * without such photos stay v1, so older apps keep reading them.
         */
        fun versionFor(photos: List<BPhoto>): Int = if (photos.any { it.workoutId == null }) 2 else 1
    }
}

@Serializable
data class BExercise(
    val id: String,
    val name: String,
    val source: String,
    val imageRef: String? = null,
    val primaryMuscles: List<String> = emptyList(),
    val secondaryMuscles: List<String> = emptyList(),
    val equipment: String? = null,
    val trackingType: String,
    val defaultRestSec: Int? = null,
    val isArchived: Boolean = false,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    /** Execution note (since database v5; older backups have none). */
    val note: String? = null,
)

@Serializable
data class BTemplate(
    val id: Long,
    val name: String,
    val note: String? = null,
    val position: Int = 0,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    val exercises: List<BTemplateExercise> = emptyList(),
)

@Serializable
data class BTemplateExercise(
    val exerciseId: String,
    val restSec: Int? = null,
    val note: String? = null,
    val sets: List<BSet> = emptyList(),
)

@Serializable
data class BWorkout(
    val id: String,
    val name: String,
    val templateId: Long? = null,
    val startedAt: Long,
    val endedAt: Long? = null,
    val note: String? = null,
    val feeling: Int? = null,
    /** Missing in older backups = no sauna. */
    val sauna: Boolean = false,
    val aiStatus: String = "NONE",
    val updatedAt: Long = 0,
    val exercises: List<BWorkoutExercise> = emptyList(),
)

@Serializable
data class BWorkoutExercise(
    val id: String,
    val exerciseId: String,
    val restSec: Int? = null,
    val note: String? = null,
    val origin: String = "PLANNED",
    val replacedExerciseId: String? = null,
    val sets: List<BSet> = emptyList(),
)

/** Set: target values (`t*`) and, for workouts, actual values. */
@Serializable
data class BSet(
    val id: String? = null,
    val tKg: Double? = null,
    val tReps: Int? = null,
    val tSec: Int? = null,
    val kg: Double? = null,
    val reps: Int? = null,
    val sec: Int? = null,
    val done: Boolean = false,
    val doneAt: Long? = null,
    val rpe: Int? = null,
)

@Serializable
data class BPhoto(
    val id: String,
    /** null = photo of a calendar day without a training (app v9 and later). */
    val workoutId: String? = null,
    val file: String,
    val takenAt: Long,
    val position: Int = 0,
)

@Serializable
data class BBodyWeight(
    /** ISO date, one entry per day. */
    val date: String,
    val kg: Double,
    val at: Long? = null,
    val updatedAt: Long = 0,
)

/** Lets another feature (coach memory, AI chat settings) put its data into the backup. */
interface BackupContributor {
    /** Key in [BackupJson.extras]. */
    val key: String
    suspend fun export(): JsonElement?
    /** Called after the database was restored; [value] is null if the backup has none. */
    suspend fun import(value: JsonElement?)
}

/** What the import screen shows before replacing anything. */
data class BackupPreview(
    val createdAt: Long,
    val appVersion: String,
    val workouts: Int,
    val templates: Int,
    val customExercises: Int,
    val photos: Int,
    val hasProfile: Boolean,
    internal val json: BackupJson,
    internal val photoBytes: Map<String, ByteArray>,
)

sealed class BackupError(message: String) : Exception(message) {
    /** Not a ZIP or no `backup.json`, or the JSON is broken. */
    class Unreadable(cause: Throwable? = null) : BackupError("Unreadable backup: ${cause?.message}")
    /** Made by a newer app version. */
    class TooNew(val version: Int) : BackupError("Backup version $version")
    /** A workout is running; finish or discard it first. */
    class WorkoutRunning : BackupError("Workout running")
}

class BackupService(
    private val data: AppData,
    private val store: AppStore,
    private val appVersion: String,
    private val clock: () -> Long,
    private val contributors: () -> List<BackupContributor> = { emptyList() },
) {
    private val db get() = data.database

    /** Builds the backup file. */
    suspend fun export(): ByteArray {
        val wDao = db.workoutDao()
        val photos = wDao.allPhotos()
        val bPhotos = photos.map { BPhoto(it.id, it.sessionId, it.fileName, it.takenAt, it.position) }
        val json = BackupJson(
            createdAt = clock(),
            appVersion = appVersion,
            state = store.state.value.persisted,
            exercises = db.exerciseDao().forBackup().sortedBy { it.id }.map { it.toBackup() },
            templates = db.templateDao().allWithExercises().map { t ->
                BTemplate(
                    id = t.template.id, name = t.template.name, note = t.template.note, position = t.template.position,
                    createdAt = t.template.createdAt, updatedAt = t.template.updatedAt,
                    exercises = t.exercises.sortedBy { it.entry.position }.map { e ->
                        BTemplateExercise(
                            exerciseId = e.entry.exerciseId, restSec = e.entry.restSec, note = e.entry.note,
                            sets = e.sets.sortedBy { it.position }.map { BSet(tKg = it.targetWeightKg, tReps = it.targetReps, tSec = it.targetDurationSec) },
                        )
                    },
                )
            },
            workouts = wDao.allFinished().map { w ->
                val s = w.session
                BWorkout(
                    id = s.id, name = s.name, templateId = s.templateId, startedAt = s.startedAt, endedAt = s.endedAt,
                    note = s.note, feeling = s.feeling, sauna = s.sauna, aiStatus = s.aiStatus.name, updatedAt = s.updatedAt,
                    exercises = w.exercises.sortedBy { it.entry.position }.map { e ->
                        BWorkoutExercise(
                            id = e.entry.id, exerciseId = e.entry.exerciseId, restSec = e.entry.restSec, note = e.entry.note,
                            origin = e.entry.origin.name, replacedExerciseId = e.entry.replacedExerciseId,
                            sets = e.sets.sortedBy { it.position }.map {
                                BSet(
                                    id = it.id, tKg = it.targetWeightKg, tReps = it.targetReps, tSec = it.targetDurationSec,
                                    kg = it.weightKg, reps = it.reps, sec = it.durationSec, done = it.completed,
                                    doneAt = it.completedAt, rpe = it.rpe,
                                )
                            },
                        )
                    },
                )
            },
            photos = bPhotos,
            bodyWeights = data.bodyWeights.all().map { BBodyWeight(it.date, it.weightKg, it.measuredAt, it.updatedAt) },
            v = BackupJson.versionFor(bPhotos),
            extras = buildMap { contributors().forEach { c -> c.export()?.let { put(c.key, it) } } },
        )
        val entries = mutableListOf(Zip.Entry(JSON_NAME, codec.encodeToString(BackupJson.serializer(), json).encodeToByteArray(), compress = true))
        photos.forEach { p -> data.workouts.photoFiles.read(p.fileName)?.let { entries += Zip.Entry("$PHOTO_DIR${p.fileName}", it) } }
        return Zip.write(entries)
    }

    /** Reads and checks a backup without changing anything. */
    fun preview(bytes: ByteArray): Result<BackupPreview> = runCatching {
        val files = try { Zip.read(bytes) } catch (e: Exception) { throw BackupError.Unreadable(e) }
        val raw = files[JSON_NAME] ?: throw BackupError.Unreadable()
        val head = try { codec.decodeFromString(Header.serializer(), raw.decodeToString()) } catch (e: Exception) { throw BackupError.Unreadable(e) }
        if (head.format != BackupJson.FORMAT) throw BackupError.Unreadable()
        if (head.v > BackupJson.VERSION) throw BackupError.TooNew(head.v)
        val json = try { codec.decodeFromString(BackupJson.serializer(), raw.decodeToString()) } catch (e: Exception) { throw BackupError.Unreadable(e) }
        val photos = files.filterKeys { it.startsWith(PHOTO_DIR) }.mapKeys { it.key.removePrefix(PHOTO_DIR) }
            .filterKeys { isSafePhotoName(it) }
        BackupPreview(
            createdAt = json.createdAt,
            appVersion = json.appVersion,
            workouts = json.workouts.size,
            templates = json.templates.size,
            customExercises = json.exercises.count { it.source == ExerciseSource.CUSTOM.name },
            photos = json.photos.count { photos.containsKey(it.file) },
            hasProfile = json.state != null,
            json = json,
            photoBytes = photos,
        )
    }

    /**
     * Replaces all data with the backup: workouts, templates, custom exercises, photos, profile
     * and settings. The API key and Fuel identity stay as they are.
     */
    suspend fun restore(preview: BackupPreview) {
        if (db.workoutDao().getActive() != null) throw BackupError.WorkoutRunning()
        val json = preview.json
        // Clean slate: same as "delete all data" (catalogue is loaded again), then insert.
        data.resetAll(reseedSamples = false)
        val now = clock()
        val exerciseDao = db.exerciseDao()
        db.transaction {
            val referencedIds = (json.templates.flatMap { t -> t.exercises.map { it.exerciseId } } +
                json.workouts.flatMap { w -> w.exercises.flatMap { listOfNotNull(it.exerciseId, it.replacedExerciseId) } }).toSet()
            json.exercises.forEach { e ->
                // free-exercise-db is gone (database v5): only exercises the backup uses come back, as custom ones.
                if (e.source == ExerciseSource.FREE_DB.name && e.id !in referencedIds) return@forEach
                val existing = exerciseDao.get(e.id)
                val type = enumOr(e.trackingType, TrackingType.WEIGHT_REPS)
                if (existing != null && existing.source != ExerciseSource.CUSTOM) {
                    exerciseDao.updateSettings(e.id, type, e.defaultRestSec, e.updatedAt)
                    if (e.isArchived) exerciseDao.archive(e.id, e.updatedAt)
                } else {
                    exerciseDao.upsert(e.toEntity(type))
                }
            }
            // Anything referenced but missing (e.g. a catalogue exercise that no longer exists).
            val referenced = (json.templates.flatMap { t -> t.exercises.map { it.exerciseId } } +
                json.workouts.flatMap { w -> w.exercises.map { it.exerciseId } }).distinct()
            val missing = referenced - exerciseDao.getAll(referenced).map { it.id }.toSet()
            missing.forEach { id ->
                exerciseDao.upsert(
                    ExerciseEntity(
                        id = id, name = id.substringAfter(':').replace('_', ' ').replace('-', ' '), source = ExerciseSource.CUSTOM,
                        imageRef = null, primaryMuscles = emptyList(), secondaryMuscles = emptyList(), equipment = null,
                        trackingType = TrackingType.WEIGHT_REPS, defaultRestSec = null, isArchived = true, createdAt = now, updatedAt = now,
                    ),
                )
            }
            val tDao = db.templateDao()
            json.templates.forEach { t ->
                val id = tDao.insertTemplate(WorkoutTemplateEntity(t.id, t.name, t.note, t.position, t.createdAt, t.updatedAt))
                t.exercises.forEachIndexed { i, e ->
                    val eid = tDao.insertExercise(TemplateExerciseEntity(templateId = id, exerciseId = e.exerciseId, position = i, restSec = e.restSec, note = e.note))
                    tDao.insertSets(e.sets.mapIndexed { si, s -> TemplateSetEntity(templateExerciseId = eid, position = si, targetWeightKg = s.tKg, targetReps = s.tReps, targetDurationSec = s.tSec) })
                }
            }
            val wDao = db.workoutDao()
            json.workouts.forEach { w ->
                wDao.upsertSession(
                    WorkoutSessionEntity(
                        id = w.id, name = w.name, templateId = w.templateId, status = SessionStatus.FINISHED,
                        startedAt = w.startedAt, endedAt = w.endedAt ?: w.startedAt, note = w.note, feeling = w.feeling,
                        photoPath = null, aiStatus = enumOr(w.aiStatus, AiStatus.NONE), restStartedAt = null, restEndsAt = null,
                        restAfterSetId = null, updatedAt = w.updatedAt, sauna = w.sauna,
                    ),
                )
                wDao.insertExercises(
                    w.exercises.mapIndexed { i, e ->
                        SessionExerciseEntity(e.id, w.id, e.exerciseId, i, e.restSec, e.note, enumOr(e.origin, ExerciseOrigin.PLANNED), e.replacedExerciseId)
                    },
                )
                wDao.insertSets(
                    w.exercises.flatMap { e ->
                        e.sets.mapIndexed { i, s ->
                            SetEntryEntity(
                                id = s.id ?: "${e.id}-$i", sessionExerciseId = e.id, position = i,
                                targetWeightKg = s.tKg, targetReps = s.tReps, targetDurationSec = s.tSec,
                                weightKg = s.kg, reps = s.reps, durationSec = s.sec, completed = s.done, completedAt = s.doneAt, rpe = s.rpe,
                            )
                        }
                    },
                )
            }
            val workoutIds = json.workouts.map { it.id }.toSet()
            wDao.insertPhotos(
                json.photos.filter { (it.workoutId == null || it.workoutId in workoutIds) && preview.photoBytes.containsKey(it.file) }
                    .distinctBy { it.id }
                    .map { WorkoutPhotoEntity(it.id, it.workoutId, it.file, it.takenAt, it.position) },
            )
        }
        val files = data.workouts.photoFiles
        json.photos.forEach { p -> preview.photoBytes[p.file]?.let { files.write(p.file, it) } }
        json.state?.let { store.restore(it) }
        // Body weight: the log of the backup plus profile weights of days it lacks (backups before DB v10 only have those).
        val weights = data.bodyWeights
        weights.replaceAll(
            json.bodyWeights.filter { runCatching { kotlinx.datetime.LocalDate.parse(it.date) }.isSuccess }
                .map { BodyWeightEntity(it.date, it.kg, it.at, it.updatedAt) },
        )
        json.state?.weights?.let { weights.importMissing(it) }
        store.mirrorWeights(weights.all().map { app.overfit.domain.WeightEntry(it.date, it.weightKg) })
        contributors().forEach { c -> c.import(json.extras[c.key]) }
    }

    @Serializable
    private data class Header(val format: String = "", val v: Int = 0)

    companion object {
        const val JSON_NAME = "backup.json"
        const val PHOTO_DIR = "photos/"
        const val MIME_TYPE = "application/zip"

        fun fileName(date: String) = "overfit-backup-$date.zip"

        internal val codec = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }
    }
}

private inline fun <reified E : Enum<E>> enumOr(name: String, fallback: E): E =
    enumValues<E>().firstOrNull { it.name == name } ?: fallback

private fun ExerciseEntity.toBackup() = BExercise(
    id, name, source.name, imageRef, primaryMuscles, secondaryMuscles, equipment, trackingType.name,
    defaultRestSec, isArchived, createdAt, updatedAt, note,
)

private fun BExercise.toEntity(type: TrackingType): ExerciseEntity {
    val legacy = source == ExerciseSource.FREE_DB.name
    return ExerciseEntity(
        id = id, name = name, source = if (legacy) ExerciseSource.CUSTOM else enumOr(source, ExerciseSource.CUSTOM),
        imageRef = if (legacy) null else imageRef,
        primaryMuscles = primaryMuscles, secondaryMuscles = secondaryMuscles, equipment = equipment, trackingType = type,
        defaultRestSec = defaultRestSec, isArchived = isArchived, createdAt = createdAt, updatedAt = updatedAt, note = note,
    )
}
