package app.overfit.data

import app.overfit.coach.CoachSettingsStore
import app.overfit.data.catalog.CatalogSeeder
import app.overfit.data.db.OverfitDatabase
import app.overfit.data.db.transaction
import app.overfit.share.TemplateImporter
import app.overfit.shared.resources.Res
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.time.Clock

/** Training data: database, repositories, exercise catalogue and offline images. */
class AppData(
    val database: OverfitDatabase,
    private val kv: KeyValueStore,
    readCatalog: suspend () -> ByteArray = { Res.readBytes(CatalogSeeder.RESOURCE_PATH) },
    readSample: suspend (String) -> ByteArray = { Res.readBytes("files/samples/$it.json") },
    private val clock: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    /** App-private photo folder (body photos, photo diary). */
    photoFiles: PhotoFiles = InMemoryPhotoFiles(),
) {
    /** Lives as long as the app; for work that must not stop when a screen closes. */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val exercises = ExerciseRepository(database, clock)
    val templates = TemplateRepository(database, clock)
    val workouts = WorkoutRepository(database, clock, photoFiles)
    val importer = TemplateImporter(exercises, templates, readSample)
    val catalog = CatalogSeeder(database, kv, readCatalog)
    val coachSettings = CoachSettingsStore(kv)

    /**
     * Emits whenever any training data changes (workouts, sets, photos, templates, exercises),
     * once right away too. For features that mirror the data elsewhere (AI chat sync).
     */
    val dataChanged: Flow<Unit> = database.invalidationTracker.createFlow(
        "workout_session", "session_exercise", "set_entry", "workout_photo",
        "workout_template", "template_exercise", "template_set", "exercise",
    ).map { }

    /** Loads the bundled catalogue if it changed. Failures are not fatal (the list just stays empty). */
    suspend fun seedCatalog(): Result<Boolean> = runCatching { catalog.seedIfNeeded(clock()) }

    /**
     * Adds Push/Pull/Legs once: at the first start and, once, on existing installs (flag in the key-value store).
     * Afterwards they are normal workouts: deleting them keeps them gone; "Add sample workouts" brings them back on request.
     */
    suspend fun seedSamplesOnce(): Result<Int> = runCatching {
        if (kv.get(KEY_SAMPLES_SEEDED) != null) return@runCatching 0
        val added = importer.addSamples()
        kv.put(KEY_SAMPLES_SEEDED, "1")
        added
    }

    /** "Delete all data": workouts, templates and custom exercises go, the catalogue is loaded again. */
    suspend fun resetAll() {
        database.transaction {
            database.coachDao().deleteAll()
            database.coachDao().deleteMemory()
            database.coachChatDao().deleteAllChats()
            database.workoutDao().deleteAll()
            database.templateDao().deleteAll()
            database.exerciseDao().deleteAll()
        }
        coachSettings.reset()
        workouts.photoFiles.list().forEach { workouts.photoFiles.delete(it) }
        kv.remove(CatalogSeeder.KEY_VERSION)
        kv.remove(KEY_SAMPLES_SEEDED)
        seedCatalog()
    }

    companion object {
        const val KEY_SAMPLES_SEEDED = "samples_seeded_v1"
    }
}
