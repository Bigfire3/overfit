package app.overfit.data.db

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import app.overfit.domain.ExerciseSource
import app.overfit.domain.TrackingType

/**
 * Exercise from a catalogue or created by the user.
 * [id] is stable and readable ("wg:bench-press", "fedb:Air_Bike", "custom:<uuid>") so templates,
 * backups and the AI coach use the same IDs.
 */
@Entity(tableName = "exercise", indices = [Index("name")])
data class ExerciseEntity(
    @PrimaryKey val id: String,
    val name: String,
    val source: ExerciseSource,
    /** Resource path ("catalog/wg/x.svg"), http(s) URL or null (→ initials tile). */
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
