package app.overfit.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.overfit.data.backup.BackupError
import app.overfit.data.backup.BackupService
import app.overfit.data.backup.Zip
import app.overfit.data.db.OverfitDatabase
import app.overfit.domain.ExerciseSource
import app.overfit.domain.Muscle
import app.overfit.domain.PlannedSet
import app.overfit.domain.SetValues
import app.overfit.domain.Template
import app.overfit.domain.TemplateExercise
import app.overfit.domain.TrackingType
import app.overfit.domain.Workout
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class BackupTest {
    private lateinit var db: OverfitDatabase
    private lateinit var data: AppData
    private lateinit var store: AppStore
    private lateinit var photos: InMemoryPhotoFiles
    private lateinit var backup: BackupService
    private val secrets = InMemorySecretStore()

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder<OverfitDatabase>(ApplicationProvider.getApplicationContext())
            .allowMainThreadQueries()
            .build()
        photos = InMemoryPhotoFiles()
        data = AppData(
            db,
            InMemoryKeyValueStore(),
            readCatalog = { File("../shared/src/commonMain/composeResources/files/catalog/exercises.json").readBytes() },
            clock = { 1_000L },
            photoFiles = photos,
        )
        store = AppStore(InMemoryKeyValueStore(), secrets, DeviceDefaults(app.overfit.domain.Language.DE, app.overfit.domain.WeightUnit.KG))
        backup = BackupService(data, store, "1.0", clock = { 42L })
    }

    @After
    fun tearDown() = db.close()

    private suspend fun fill() {
        data.seedCatalog().getOrThrow()
        val bench = data.exercises.get("wg:bench-press")!!
        data.exercises.updateSettings(bench.id, TrackingType.WEIGHT_REPS, 150)
        val custom = data.exercises.get(data.exercises.createCustom(CustomExerciseInput("Handstand", TrackingType.TIME, 60)))!!
        val templateId = data.templates.save(
            Template(0, "Push", "n", listOf(TemplateExercise(bench, 120, null, listOf(PlannedSet(60.0, 8))), TemplateExercise(custom, null, null, listOf(PlannedSet(durationSec = 30))))),
        )
        val template = data.templates.get(templateId)!!
        var n = 0
        val w = Workout.fromTemplate(template, 10_000, id = "w1", idGen = { "id${n++}" })
        val first = w.exercises[0].sets[0].id
        val done = w.setValues(first, SetValues(62.5, 8)).completeSet(first, 11_000)
        data.workouts.save(done)
        data.workouts.finish(done, 20_000)
        data.workouts.updateDetails("w1", "Stark", 4)
        data.workouts.setSauna("w1", true)
        data.workouts.addPhoto("w1", byteArrayOf(9, 8, 7), takenAt = 21_000)
        store.setName("Fabian")
        store.completeOnboarding()
        store.connectAi(app.overfit.ai.ProviderId.ANTHROPIC, "sk-secret")
    }

    @Test
    fun exportDeleteImportGivesTheSameData() = runTest {
        fill()
        val file = backup.export()
        assertTrue("API key must not be in the backup", !Zip.read(file).getValue(BackupService.JSON_NAME).decodeToString().contains("sk-secret"))
        val before = snapshot()

        data.resetAll()
        store.resetAll()
        assertTrue(data.workouts.history.first().isEmpty())
        assertTrue(photos.files.isEmpty())

        val preview = backup.preview(file).getOrThrow()
        assertEquals(1, preview.workouts)
        assertEquals(1, preview.templates)
        assertEquals(1, preview.customExercises)
        assertEquals(1, preview.photos)
        backup.restore(preview)

        assertEquals(before, snapshot())
        assertTrue("sauna survives export and restore", data.workouts.getFinished("w1")!!.sauna)
        assertEquals("Fabian", store.state.value.profile.name)
        assertTrue(store.state.value.onboardingDone)
        // A second export is identical apart from nothing (same clock): stable format.
        assertEquals(Zip.read(file).getValue(BackupService.JSON_NAME).decodeToString(), Zip.read(backup.export()).getValue(BackupService.JSON_NAME).decodeToString())
    }

    @Test
    fun photoWithoutWorkoutSurvivesExportAndImport() = runTest {
        val photo = data.workouts.addPhoto(null, byteArrayOf(5, 4, 3), takenAt = 33_000)
        val file = backup.export()

        data.resetAll()
        store.resetAll()
        assertTrue(data.workouts.allPhotos().isEmpty())

        val preview = backup.preview(file).getOrThrow()
        assertEquals(1, preview.photos)
        backup.restore(preview)

        val restored = data.workouts.allPhotos().single()
        assertEquals(photo.id, restored.id)
        assertEquals(null, restored.sessionId)
        assertEquals(33_000L, restored.takenAt)
        assertEquals(listOf<Byte>(5, 4, 3), photos.read(restored.fileName)?.toList())
        val diary = data.workouts.photos.first().single()
        assertEquals(null, diary.workoutName)
    }

    private suspend fun snapshot(): List<Any?> {
        val finished = data.workouts.getFinished("w1")
        return listOf(
            finished?.workout, finished?.note, finished?.feeling, finished?.sauna, finished?.endedAt,
            finished?.photos?.map { it.id to it.takenAt }, finished?.photos?.map { photos.read(it.fileName)?.toList() },
            data.templates.templates.first(), data.templates.templates.first().map { data.templates.get(it.id) },
            data.exercises.get("wg:bench-press")?.defaultRestSec,
            db.exerciseDao().forBackup().map { it.copy(createdAt = 0, updatedAt = 0) },
        )
    }

    @Test
    fun brokenOrNewerFilesAreRejectedWithoutChangingData() = runTest {
        fill()
        assertTrue(backup.preview(ByteArray(10)).exceptionOrNull() is BackupError.Unreadable)
        assertTrue(backup.preview(Zip.write(listOf(Zip.Entry("backup.json", "{nope".encodeToByteArray())))).exceptionOrNull() is BackupError.Unreadable)
        val newer = Zip.write(listOf(Zip.Entry("backup.json", """{"format":"overfit-backup","v":99,"createdAt":1}""".encodeToByteArray())))
        assertTrue(backup.preview(newer).exceptionOrNull() is BackupError.TooNew)
        val other = Zip.write(listOf(Zip.Entry("backup.json", """{"format":"something","v":1,"createdAt":1}""".encodeToByteArray())))
        assertTrue(backup.preview(other).exceptionOrNull() is BackupError.Unreadable)
        assertEquals(1, data.workouts.history.first().size)
    }

    @Test
    fun oldMinimalBackupImports() = runTest {
        data.seedCatalog().getOrThrow()
        val minimal = Zip.write(
            listOf(
                Zip.Entry(
                    "backup.json",
                    """{"format":"overfit-backup","v":1,"createdAt":5,"futureField":true,"workouts":[{"id":"x","name":"Alt","startedAt":1,
                       "exercises":[{"id":"e","exerciseId":"gone:exercise","sets":[{"kg":20,"reps":5,"done":true}]}]}]}""".encodeToByteArray(),
                ),
            ),
        )
        backup.restore(backup.preview(minimal).getOrThrow())
        val w = data.workouts.getFinished("x")!!
        assertEquals(20.0, w.workout.exercises.single().sets.single().actual.weightKg)
        assertEquals(false, w.sauna) // older backups have no sauna field
    }
    @Test
    fun legacyFreeDbExerciseUsedByTemplateBecomesCustomWithoutImage() = runTest {
        data.seedCatalog().getOrThrow()
        val json = """{"format":"overfit-backup","v":1,"createdAt":5,
            "exercises":[
              {"id":"fedb:Barbell_Curl","name":"Barbell Curl","source":"FREE_DB","imageRef":"https://example.com/curl.jpg","primaryMuscles":["biceps"],"trackingType":"WEIGHT_REPS"},
              {"id":"fedb:Unused_Move","name":"Unused Move","source":"FREE_DB","imageRef":"https://example.com/unused.jpg","trackingType":"WEIGHT_REPS"}],
            "templates":[{"id":1,"name":"Arme","exercises":[{"exerciseId":"fedb:Barbell_Curl","sets":[{"tKg":20,"tReps":10}]}]}]}"""
        backup.restore(backup.preview(Zip.write(listOf(Zip.Entry("backup.json", json.encodeToByteArray())))).getOrThrow())

        val curl = data.exercises.get("fedb:Barbell_Curl")!!
        assertEquals(ExerciseSource.CUSTOM, curl.source)
        assertEquals(null, curl.imageRef)
        assertEquals("Barbell Curl", curl.name)
        assertEquals(listOf(Muscle.BICEPS), curl.primaryMuscles)
        assertEquals("fedb:Barbell_Curl", data.templates.get(data.templates.templates.first().first().id)!!.exercises.single().exercise.id)
        assertEquals(null, data.exercises.get("fedb:Unused_Move"))
    }

    @Test
    fun customExerciseNoteSurvivesExportAndRestore() = runTest {
        data.seedCatalog().getOrThrow()
        val id = data.exercises.createCustom(
            CustomExerciseInput("Fliegende", TrackingType.WEIGHT_REPS, 75, primary = listOf(Muscle.CHEST), note = "Ellbogen eng"),
        )
        val file = backup.export()
        data.resetAll()
        assertEquals(null, data.exercises.get(id))
        backup.restore(backup.preview(file).getOrThrow())
        val back = data.exercises.get(id)!!
        assertEquals("Ellbogen eng", back.note)
        assertEquals(75, back.defaultRestSec)
        assertEquals(ExerciseSource.CUSTOM, back.source)
    }
}
