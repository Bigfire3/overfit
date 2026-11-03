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
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class ExerciseRepository(
    db: OverfitDatabase,
    private val clock: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
    private val dao = db.exerciseDao()

    /** All exercises that are not archived, alphabetically. */
    val exercises: Flow<List<Exercise>> = dao.observeActive().map { list -> list.map { it.toDomain() } }

    /** One-shot snapshot of [exercises]. */
    suspend fun getActive(): List<Exercise> = dao.getActive().map { it.toDomain() }

    suspend fun get(id: String): Exercise? = dao.get(id)?.toDomain()

    suspend fun getAll(ids: List<String>): List<Exercise> {
        val byId = dao.getAll(ids).associateBy { it.id }
        return ids.mapNotNull { byId[it]?.toDomain() }
    }

    /** New custom exercise; returns its ID. */
    @OptIn(ExperimentalUuidApi::class)
    suspend fun createCustom(input: CustomExerciseInput): String {
        val now = clock()
        val id = "custom:${Uuid.random()}"
        dao.upsert(input.toEntity(id, createdAt = now, now = now))
        return id
    }

    suspend fun updateCustom(id: String, input: CustomExerciseInput) {
        val existing = dao.get(id) ?: return
        require(existing.source == ExerciseSource.CUSTOM) { "Only custom exercises are fully editable" }
        dao.upsert(input.toEntity(id, createdAt = existing.createdAt, now = clock()))
    }

    /** Editable for catalogue exercises too: tracking type (e.g. weighted pull-ups) and rest time. */
    suspend fun updateSettings(id: String, trackingType: TrackingType, restSec: Int?) =
        dao.updateSettings(id, trackingType, restSec, clock())

    /**
     * Deletes a custom exercise. If a template still uses it, it is only archived (hidden) so that
     * no data is lost.
     */
    suspend fun deleteCustom(id: String): DeleteResult {
        val existing = dao.get(id) ?: return DeleteResult.DELETED
        require(existing.source == ExerciseSource.CUSTOM) { "Catalogue exercises cannot be deleted" }
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

/** Everything a custom exercise consists of (form, AI autofill, share import). */
data class CustomExerciseInput(
    val name: String,
    val trackingType: TrackingType,
    val restSec: Int?,
    val primary: List<Muscle> = emptyList(),
    val equipment: Equipment? = null,
    val secondary: List<Muscle> = emptyList(),
    val note: String? = null,
) {
    fun toEntity(id: String, createdAt: Long, now: Long) = ExerciseEntity(
        id = id,
        name = name.trim(),
        source = ExerciseSource.CUSTOM,
        imageRef = null,
        primaryMuscles = primary.distinct().map { it.key },
        secondaryMuscles = secondary.distinct().filter { it !in primary }.map { it.key },
        equipment = equipment?.key,
        trackingType = trackingType,
        defaultRestSec = restSec,
        createdAt = createdAt,
        updatedAt = now,
        note = note?.trim()?.takeIf { it.isNotEmpty() },
    )

    /** The exercise as it will look once created (import preview, share). */
    fun toExercise(id: String) = Exercise(
        id = id,
        name = name.trim(),
        source = ExerciseSource.CUSTOM,
        imageRef = null,
        primaryMuscles = primary.distinct(),
        secondaryMuscles = secondary.distinct().filter { it !in primary },
        equipment = equipment,
        trackingType = trackingType,
        defaultRestSec = restSec,
        note = note?.trim()?.takeIf { it.isNotEmpty() },
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
    note = note,
)
