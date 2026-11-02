package app.overfit.data

import app.overfit.data.db.ExerciseEntity
import app.overfit.data.db.OverfitDatabase
import app.overfit.domain.Equipment
import app.overfit.domain.Exercise
import app.overfit.domain.ExerciseSource
import app.overfit.domain.Muscle
import app.overfit.domain.TrackingType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

class ExerciseRepository(private val db: OverfitDatabase, private val clock: () -> Long = System::currentTimeMillis) {
    private val dao = db.exerciseDao()

    /** Alle nicht archivierten Übungen, alphabetisch. */
    val exercises: Flow<List<Exercise>> = dao.observeActive().map { list -> list.map { it.toDomain() } }

    suspend fun get(id: String): Exercise? = dao.get(id)?.toDomain()

    suspend fun getAll(ids: List<String>): List<Exercise> {
        val byId = dao.getAll(ids).associateBy { it.id }
        return ids.mapNotNull { byId[it]?.toDomain() }
    }

    /** Neue eigene Übung; gibt die ID zurück. */
    suspend fun createCustom(input: CustomExerciseInput): String {
        val now = clock()
        val id = "custom:${UUID.randomUUID()}"
        dao.upsert(input.toEntity(id, createdAt = now, now = now))
        return id
    }

    suspend fun updateCustom(id: String, input: CustomExerciseInput) {
        val existing = dao.get(id) ?: return
        require(existing.source == ExerciseSource.CUSTOM) { "Nur eigene Übungen sind komplett bearbeitbar" }
        dao.upsert(input.toEntity(id, createdAt = existing.createdAt, now = clock()))
    }

    /** Für Katalogübungen änderbar: Tracking-Typ (z. B. Klimmzug mit Zusatzgewicht) und Pausenzeit. */
    suspend fun updateSettings(id: String, trackingType: TrackingType, restSec: Int?) =
        dao.updateSettings(id, trackingType, restSec, clock())

    /**
     * Löscht eine eigene Übung. Steckt sie noch in einer Vorlage, wird sie nur archiviert
     * (ausgeblendet), damit keine Daten verloren gehen.
     */
    suspend fun deleteCustom(id: String): DeleteResult {
        val existing = dao.get(id) ?: return DeleteResult.DELETED
        require(existing.source == ExerciseSource.CUSTOM) { "Katalogübungen können nicht gelöscht werden" }
        return if (dao.usageCount(id) > 0) {
            dao.archive(id, clock())
            DeleteResult.ARCHIVED
        } else {
            dao.delete(id)
            DeleteResult.DELETED
        }
    }

    enum class DeleteResult { DELETED, ARCHIVED }
}

data class CustomExerciseInput(
    val name: String,
    val trackingType: TrackingType,
    val restSec: Int?,
    val muscle: Muscle?,
    val equipment: Equipment?,
) {
    fun toEntity(id: String, createdAt: Long, now: Long) = ExerciseEntity(
        id = id,
        name = name.trim(),
        source = ExerciseSource.CUSTOM,
        imageRef = null,
        primaryMuscles = listOfNotNull(muscle?.key),
        secondaryMuscles = emptyList(),
        equipment = equipment?.key,
        trackingType = trackingType,
        defaultRestSec = restSec,
        createdAt = createdAt,
        updatedAt = now,
    )
}

fun ExerciseEntity.toDomain() = Exercise(
    id = id,
    name = name,
    source = source,
    imageRef = imageRef,
    primaryMuscles = primaryMuscles.mapNotNull(Muscle::fromKey),
    secondaryMuscles = secondaryMuscles.mapNotNull(Muscle::fromKey),
    equipment = Equipment.fromKey(equipment),
    trackingType = trackingType,
    defaultRestSec = defaultRestSec,
)
