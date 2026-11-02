package app.overfit.data.catalog

import android.content.Context
import androidx.room.withTransaction
import app.overfit.data.db.ExerciseCatalogFields
import app.overfit.data.db.ExerciseEntity
import app.overfit.data.db.OverfitDatabase
import app.overfit.domain.ExerciseSource
import app.overfit.domain.TrackingType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Format von assets/catalog/exercises.json (erzeugt von tools/catalog/import_catalog.py). */
@Serializable
data class CatalogFile(val version: String, val exercises: List<CatalogExercise>)

@Serializable
data class CatalogExercise(
    val id: String,
    val name: String,
    val source: ExerciseSource,
    val image: String? = null,
    val primary: List<String> = emptyList(),
    val secondary: List<String> = emptyList(),
    val equipment: String? = null,
    val tracking: TrackingType,
    /** IDs zusammengeführter Dubletten aus free-exercise-db (nur zur Nachvollziehbarkeit). */
    val merged: List<String> = emptyList(),
)

/**
 * Spielt den gebündelten Katalog in die Datenbank ein, sobald sich seine Version ändert.
 * Neue Übungen werden eingefügt; bestehende bekommen nur die Katalogfelder (Name, Bild, Muskeln,
 * Equipment) aktualisiert. Tracking-Typ, Pausenzeit und Archiv-Status, die ich selbst ändern kann,
 * bleiben erhalten. Übungen, die aus dem Katalog verschwinden, werden nie gelöscht (sie können in
 * Vorlagen und Trainings stecken).
 */
class CatalogSeeder(
    private val context: Context,
    private val db: OverfitDatabase,
    private val assetPath: String = ASSET_PATH,
) {
    private val prefs = context.getSharedPreferences("catalog", Context.MODE_PRIVATE)

    suspend fun seedIfNeeded(now: Long = System.currentTimeMillis()): Boolean {
        val catalog = load()
        if (prefs.getString(KEY_VERSION, null) == catalog.version && db.exerciseDao().count() > 0) return false
        seed(catalog, now)
        prefs.edit().putString(KEY_VERSION, catalog.version).apply()
        return true
    }

    fun load(): CatalogFile = load(context, assetPath)

    suspend fun seed(catalog: CatalogFile, now: Long) {
        val dao = db.exerciseDao()
        db.withTransaction {
            dao.insertIgnore(
                catalog.exercises.map {
                    ExerciseEntity(
                        id = it.id,
                        name = it.name,
                        source = it.source,
                        imageRef = it.image,
                        primaryMuscles = it.primary,
                        secondaryMuscles = it.secondary,
                        equipment = it.equipment,
                        trackingType = it.tracking,
                        defaultRestSec = null,
                        createdAt = now,
                        updatedAt = now,
                    )
                },
            )
            dao.updateCatalogFields(
                catalog.exercises.map {
                    ExerciseCatalogFields(
                        id = it.id,
                        name = it.name,
                        source = it.source,
                        imageRef = it.image,
                        primaryMuscles = it.primary,
                        secondaryMuscles = it.secondary,
                        equipment = it.equipment,
                        updatedAt = now,
                    )
                },
            )
        }
    }

    companion object {
        const val ASSET_PATH = "catalog/exercises.json"
        private const val KEY_VERSION = "version"
        private val json = Json { ignoreUnknownKeys = true }

        /** Liest den gebündelten Katalog (ohne Datenbank, z. B. für Tests). */
        fun load(context: Context, assetPath: String = ASSET_PATH): CatalogFile =
            context.assets.open(assetPath).bufferedReader().use { json.decodeFromString(CatalogFile.serializer(), it.readText()) }
    }
}
