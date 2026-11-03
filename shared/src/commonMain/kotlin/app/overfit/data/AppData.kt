package app.overfit.data

import app.overfit.coach.CoachSettingsStore
import app.overfit.data.catalog.CatalogSeeder
import app.overfit.data.db.OverfitDatabase
import app.overfit.data.db.transaction
import app.overfit.data.images.OfflineImages
import app.overfit.share.TemplateImporter
import app.overfit.shared.resources.Res
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlin.time.Clock

/** Training data: database, repositories, exercise catalogue and offline images. */
class AppData(
    val database: OverfitDatabase,
    private val kv: KeyValueStore,
    readCatalog: suspend () -> ByteArray = { Res.readBytes(CatalogSeeder.RESOURCE_PATH) },
    readSample: suspend (String) -> ByteArray = { Res.readBytes("files/samples/$it.json") },
    private val clock: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
    /** Lives as long as the app; for work that must not stop when a screen closes. */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val exercises = ExerciseRepository(database, clock)
    val templates = TemplateRepository(database, clock)
    val workouts = WorkoutRepository(database, clock)
    val importer = TemplateImporter(exercises, templates, readSample)
    val catalog = CatalogSeeder(database, kv, readCatalog)
    val offlineImages = OfflineImages(scope, exercises)
    val coachSettings = CoachSettingsStore(kv)

    /** Loads the bundled catalogue if it changed. Failures are not fatal (the list just stays empty). */
    suspend fun seedCatalog(): Result<Boolean> = runCatching { catalog.seedIfNeeded(clock()) }

    /** "Delete all data": workouts, templates and custom exercises go, the catalogue is loaded again. */
    suspend fun resetAll() {
        database.transaction {
            database.coachDao().deleteAll()
            database.coachDao().deleteMemory()
            database.workoutDao().deleteAll()
            database.templateDao().deleteAll()
            database.exerciseDao().deleteAll()
        }
        coachSettings.reset()
        kv.remove(CatalogSeeder.KEY_VERSION)
        seedCatalog()
    }
}
