package app.overfit.data.db

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import app.overfit.domain.ExerciseOrigin
import app.overfit.domain.ExerciseSource
import app.overfit.domain.TrackingType

/**
 * Exercise from a catalogue or created by the user.
 * [id] is stable and readable ("wg:bench-press", "custom:<uuid>") so templates, backups and the AI coach use the
 * same IDs. Former free-exercise-db exercises that were in use keep their "fedb:…" ID but are custom since v5.
 */
@Entity(tableName = "exercise", indices = [Index("name")])
data class ExerciseEntity(
    @PrimaryKey val id: String,
    val name: String,
    val source: ExerciseSource,
    /** Resource path ("catalog/wg/x.svg") or null (→ initials tile). */
    val imageRef: String?,
    /** Muscle keys, see [app.overfit.domain.Muscle]. */
    val primaryMuscles: List<String>,
    val secondaryMuscles: List<String>,
    /** Equipment key, see [app.overfit.domain.Equipment]. */
    val equipment: String?,
    val trackingType: TrackingType,
    /** Rest time of the exercise; null = global default from the settings. */
    val defaultRestSec: Int?,
    /** Hidden (e.g. a deleted custom exercise that is still referenced). */
    @ColumnInfo(defaultValue = "0") val isArchived: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
    /** Short execution note (custom exercises, e.g. filled in by the AI); since v5. */
    val note: String? = null,
)

/** Partial update for the catalogue import: user settings (tracking type, rest, archive) are kept. */
data class ExerciseCatalogFields(
    val id: String,
    val name: String,
    val source: ExerciseSource,
    val imageRef: String?,
    val primaryMuscles: List<String>,
    val secondaryMuscles: List<String>,
    val equipment: String?,
    val updatedAt: Long,
)

@Entity(tableName = "workout_template")
data class WorkoutTemplateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val note: String?,
    val position: Int,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "template_exercise",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutTemplateEntity::class,
            parentColumns = ["id"],
            childColumns = ["templateId"],
            onDelete = ForeignKey.CASCADE,
        ),
        // A referenced exercise must not be deleted (archive it instead).
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("templateId"), Index("exerciseId")],
)
data class TemplateExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val templateId: Long,
    val exerciseId: String,
    val position: Int,
    /** Rest time in this template; null = the exercise's rest time or the global default. */
    val restSec: Int?,
    val note: String?,
)

/** Planned set with target values. Which fields are used depends on the exercise's tracking type. */
@Entity(
    tableName = "template_set",
    foreignKeys = [
        ForeignKey(
            entity = TemplateExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["templateExerciseId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("templateExerciseId")],
)
data class TemplateSetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val templateExerciseId: Long,
    val position: Int,
    val targetWeightKg: Double?,
    val targetReps: Int?,
    val targetDurationSec: Int?,
)

// --- Relations for loading a complete template ------------------------------------------------

data class TemplateExerciseWithDetails(
    @Embedded val entry: TemplateExerciseEntity,
    @Relation(parentColumn = "exerciseId", entityColumn = "id")
    val exercise: ExerciseEntity,
    @Relation(parentColumn = "id", entityColumn = "templateExerciseId")
    val sets: List<TemplateSetEntity>,
)

data class TemplateWithExercises(
    @Embedded val template: WorkoutTemplateEntity,
    @Relation(entity = TemplateExerciseEntity::class, parentColumn = "id", entityColumn = "templateId")
    val exercises: List<TemplateExerciseWithDetails>,
)

/** Row for template lists: template plus exercise names in order. */
data class TemplateExerciseName(val templateId: Long, val name: String)

// --- Workouts (schema v2) ---------------------------------------------------------------------

enum class SessionStatus { ACTIVE, FINISHED }

enum class AiStatus { NONE, QUEUED, SENT, DONE, FAILED }

/**
 * A workout. At most one is [SessionStatus.ACTIVE]; its rest timer is stored as wall-clock times
 * so the workout continues seamlessly after a process kill or reboot of the app.
 * [templateId] is informational only (no foreign key): deleting a template keeps the history.
 */
@Entity(tableName = "workout_session", indices = [Index("status"), Index("startedAt")])
data class WorkoutSessionEntity(
    @PrimaryKey val id: String,
    val name: String,
    val templateId: Long?,
    val status: SessionStatus,
    val startedAt: Long,
    val endedAt: Long?,
    val note: String?,
    /** Feeling 1–5 (phase 3). */
    val feeling: Int?,
    val photoPath: String?,
    @ColumnInfo(defaultValue = "NONE") val aiStatus: AiStatus = AiStatus.NONE,
    val restStartedAt: Long?,
    val restEndsAt: Long?,
    val restAfterSetId: String?,
    val updatedAt: Long,
    /** "Sauna" checked after the workout (v8). Shown in the calendar and given to the AI. */
    @ColumnInfo(defaultValue = "0") val sauna: Boolean = false,
)

@Entity(
    tableName = "session_exercise",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
        // History must keep its exercises: custom exercises in use are archived, never deleted.
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("sessionId"), Index("exerciseId")],
)
data class SessionExerciseEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val exerciseId: String,
    val position: Int,
    val restSec: Int?,
    val note: String?,
    val origin: ExerciseOrigin,
    /** Set when [origin] is REPLACED: the exercise that was planned. No foreign key on purpose. */
    val replacedExerciseId: String?,
)

/** A set: target (planned) vs. actual values. Which fields count depends on the tracking type. */
@Entity(
    tableName = "set_entry",
    foreignKeys = [
        ForeignKey(
            entity = SessionExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionExerciseId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sessionExerciseId")],
)
data class SetEntryEntity(
    @PrimaryKey val id: String,
    val sessionExerciseId: String,
    val position: Int,
    val targetWeightKg: Double?,
    val targetReps: Int?,
    val targetDurationSec: Int?,
    val weightKg: Double?,
    val reps: Int?,
    val durationSec: Int?,
    val completed: Boolean,
    val completedAt: Long?,
    /** Optional RPE (setting, off by default). */
    val rpe: Int?,
)

data class SessionExerciseWithDetails(
    @Embedded val entry: SessionExerciseEntity,
    @Relation(parentColumn = "exerciseId", entityColumn = "id")
    val exercise: ExerciseEntity,
    @Relation(parentColumn = "id", entityColumn = "sessionExerciseId")
    val sets: List<SetEntryEntity>,
)

data class SessionWithExercises(
    @Embedded val session: WorkoutSessionEntity,
    @Relation(entity = SessionExerciseEntity::class, parentColumn = "id", entityColumn = "sessionId")
    val exercises: List<SessionExerciseWithDetails>,
)

// --- Photos (schema v3) -----------------------------------------------------------------------

/**
 * Body/progress photo of a finished workout (also the photo diary). The file lives only in the
 * app-private photo directory ([app.overfit.data.AppFiles]); [fileName] is relative to it.
 */
@Entity(
    tableName = "workout_photo",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sessionId"), Index("takenAt")],
)
data class WorkoutPhotoEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val fileName: String,
    val takenAt: Long,
    val position: Int,
)

/** Row of the history list: a finished workout with its key numbers. */
/** Query result: when a saved workout was last trained (epoch ms of the start). */
data class TemplateLastDone(val templateId: Long, val lastStartedAt: Long)

data class HistoryRow(
    val id: String,
    val name: String,
    val startedAt: Long,
    val endedAt: Long?,
    val feeling: Int?,
    val exerciseCount: Int,
    val completedSets: Int,
    /** Sum of kg × reps over completed sets with positive weight. */
    val volumeKg: Double,
    val photoCount: Int,
    val sauna: Boolean = false,
)

/** Photo with the workout it belongs to (photo diary). */
data class PhotoRow(
    val id: String,
    val sessionId: String,
    val fileName: String,
    val takenAt: Long,
    val position: Int,
    val sessionName: String,
)

/** A completed set of one exercise in a finished workout (progress, best values). */
data class ExerciseSetRow(
    val sessionId: String,
    val startedAt: Long,
    val sessionName: String,
    val exerciseId: String,
    val weightKg: Double?,
    val reps: Int?,
    val durationSec: Int?,
    val position: Int,
)
