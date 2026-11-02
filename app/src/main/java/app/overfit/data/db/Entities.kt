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
 * Übung aus einem Katalog oder selbst angelegt.
 * [id] ist stabil und lesbar ("wg:bench-press", "fedb:Air_Bike", "custom:<uuid>"), damit Vorlagen,
 * Backups und der KI-Coach dieselben IDs verwenden.
 */
@Entity(tableName = "exercise", indices = [Index("name")])
data class ExerciseEntity(
    @PrimaryKey val id: String,
    val name: String,
    val source: ExerciseSource,
    /** Asset-Pfad ("catalog/wg/x.svg"), http(s)-URL oder null (→ Initialen-Kachel). */
    val imageRef: String?,
    /** Muskel-Schlüssel, siehe [app.overfit.domain.Muscle]. */
    val primaryMuscles: List<String>,
    val secondaryMuscles: List<String>,
    /** Equipment-Schlüssel, siehe [app.overfit.domain.Equipment]. */
    val equipment: String?,
    val trackingType: TrackingType,
    /** Pausenzeit der Übung; null = globaler Standard aus den Einstellungen. */
    val defaultRestSec: Int?,
    /** Ausgeblendet (z. B. gelöschte eigene Übung, die noch referenziert wird). */
    @ColumnInfo(defaultValue = "0") val isArchived: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
)

/** Teil-Update beim Katalog-Import: Nutzer-Einstellungen (Tracking-Typ, Pause, Archiv) bleiben erhalten. */
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
        // Eine referenzierte Übung darf nicht gelöscht werden (stattdessen archivieren).
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
    /** Pausenzeit in dieser Vorlage; null = Pausenzeit der Übung bzw. globaler Standard. */
    val restSec: Int?,
    val note: String?,
)

/** Geplanter Satz mit Zielwerten. Welche Felder genutzt werden, hängt vom Tracking-Typ der Übung ab. */
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

// --- Relationen zum Laden einer kompletten Vorlage -------------------------------------------

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

/** Zeile für Vorlagenlisten: Vorlage plus Übungsnamen in Reihenfolge. */
data class TemplateExerciseName(val templateId: Long, val name: String)
