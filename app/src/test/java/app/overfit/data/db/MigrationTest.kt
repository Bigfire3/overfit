package app.overfit.data.db

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import android.content.Context
import androidx.sqlite.SQLiteConnection
import app.overfit.data.toDomain
import androidx.sqlite.execSQL
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlinx.coroutines.runBlocking
import java.io.File

/**
 * Migrationstests auf Basis der exportierten Schemas (shared/schemas, per Gradle als Test-Assets eingebunden).
 * Künftig kommt je Migration ein Test dazu: Datenbank in Version N-1 erzeugen, Testdaten
 * einfügen, mit runMigrationsAndValidate auf N migrieren und die Daten prüfen.
 */
@RunWith(RobolectricTestRunner::class)
class MigrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val dbFile: File = context.getDatabasePath(TEST_DB)
    private val driver = AndroidSQLiteDriver()

    @Test
    fun version1OpensWithAllMigrationsAndPassesSchemaValidation() = runBlocking<Unit> {
        val helper = MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            dbFile,
            driver,
            OverfitDatabase::class,
            { OverfitDatabaseConstructor.initialize() },
        )
        helper.createDatabase(1).close()
        val db = Room.databaseBuilder<OverfitDatabase>(context, dbFile.absolutePath)
            .setDriver(driver)
            .addMigrations(*OverfitDatabase.MIGRATIONS)
            .build()
        try {
            db.exerciseDao().count() // öffnet die DB: löst Migration und Schema-Validierung aus
        } finally {
            db.close()
        }
    }

    @Test
    fun migration1to2KeepsDataAndAddsEmptyWorkoutTables() = runBlocking<Unit> {
        val helper = MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            dbFile,
            driver,
            OverfitDatabase::class,
            { OverfitDatabaseConstructor.initialize() },
        )
        val v1 = helper.createDatabase(1)
        v1.execSQL(
            "INSERT INTO exercise (id, name, source, imageRef, primaryMuscles, secondaryMuscles, equipment, trackingType, defaultRestSec, isArchived, createdAt, updatedAt) " +
                "VALUES ('ex1', 'Bench', 'CUSTOM', NULL, 'chest,triceps', '', 'barbell', 'WEIGHT_REPS', 90, 0, 1, 2)",
        )
        v1.execSQL("INSERT INTO workout_template (id, name, note, position, createdAt, updatedAt) VALUES (1, 'Push', 'n', 0, 1, 2)")
        v1.execSQL("INSERT INTO template_exercise (id, templateId, exerciseId, position, restSec, note) VALUES (1, 1, 'ex1', 0, 60, NULL)")
        v1.execSQL("INSERT INTO template_set (id, templateExerciseId, position, targetWeightKg, targetReps, targetDurationSec) VALUES (1, 1, 0, 50.0, 8, NULL)")
        v1.close()

        // Validates the migrated schema against schemas/.../2.json.
        val v2 = helper.runMigrationsAndValidate(2, OverfitDatabase.MIGRATIONS.toList())
        try {
            assertEquals(1L, v2.count("exercise"))
            assertEquals(1L, v2.count("workout_template"))
            assertEquals(1L, v2.count("template_exercise"))
            assertEquals(1L, v2.count("template_set"))
            listOf("workout_session", "session_exercise", "set_entry").forEach {
                assertEquals("$it exists and is empty", 0L, v2.count(it))
            }
        } finally {
            v2.close()
        }

        val db = Room.databaseBuilder<OverfitDatabase>(context, dbFile.absolutePath)
            .setDriver(driver)
            .addMigrations(*OverfitDatabase.MIGRATIONS)
            .build()
        try {
            val ex = db.exerciseDao().get("ex1")
            assertNotNull(ex)
            assertEquals("Bench", ex!!.name)
            assertEquals(90, ex.defaultRestSec)
            val template = db.templateDao().getWithExercises(1)!!
            assertEquals("Push", template.template.name)
            assertEquals(60, template.exercises.single().entry.restSec)
            assertEquals(8, template.exercises.single().sets.single().targetReps)
            assertNull(db.workoutDao().getActive())
            // The new tables are usable: a workout can reference the migrated exercise.
            val repo = app.overfit.data.WorkoutRepository(db)
            repo.save(
                app.overfit.domain.Workout.empty("T", 1, "w1").addExercise(
                    app.overfit.domain.Workout.added(db.exerciseDao().get("ex1")!!.toDomain(), emptyList()),
                ),
            )
            assertEquals("w1", repo.loadActive()!!.id)
        } finally {
            db.close()
        }
    }

    @Test
    fun migration2to3KeepsWorkoutsAndAddsPhotoTable() = runBlocking<Unit> {
        val helper = MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            dbFile,
            driver,
            OverfitDatabase::class,
            { OverfitDatabaseConstructor.initialize() },
        )
        val v2 = helper.createDatabase(2)
        v2.execSQL(
            "INSERT INTO exercise (id, name, source, imageRef, primaryMuscles, secondaryMuscles, equipment, trackingType, defaultRestSec, isArchived, createdAt, updatedAt) " +
                "VALUES ('ex1', 'Bench', 'CUSTOM', NULL, 'chest', '', NULL, 'WEIGHT_REPS', NULL, 0, 1, 2)",
        )
        v2.execSQL(
            "INSERT INTO workout_session (id, name, templateId, status, startedAt, endedAt, note, feeling, photoPath, aiStatus, restStartedAt, restEndsAt, restAfterSetId, updatedAt) " +
                "VALUES ('w1', 'Push', NULL, 'FINISHED', 1000, 5000, 'gut', 4, NULL, 'NONE', NULL, NULL, NULL, 5000)",
        )
        v2.execSQL("INSERT INTO session_exercise (id, sessionId, exerciseId, position, restSec, note, origin, replacedExerciseId) VALUES ('se1', 'w1', 'ex1', 0, 90, NULL, 'PLANNED', NULL)")
        v2.execSQL(
            "INSERT INTO set_entry (id, sessionExerciseId, position, targetWeightKg, targetReps, targetDurationSec, weightKg, reps, durationSec, completed, completedAt, rpe) " +
                "VALUES ('s1', 'se1', 0, 60.0, 8, NULL, 62.5, 8, NULL, 1, 2000, NULL)",
        )
        v2.close()

        val v3 = helper.runMigrationsAndValidate(3, OverfitDatabase.MIGRATIONS.toList())
        try {
            assertEquals(1L, v3.count("workout_session"))
            assertEquals(1L, v3.count("set_entry"))
            assertEquals(0L, v3.count("workout_photo"))
        } finally {
            v3.close()
        }

        val db = Room.databaseBuilder<OverfitDatabase>(context, dbFile.absolutePath)
            .setDriver(driver)
            .addMigrations(*OverfitDatabase.MIGRATIONS)
            .build()
        try {
            val repo = app.overfit.data.WorkoutRepository(db)
            val finished = repo.getFinished("w1")!!
            assertEquals("gut", finished.note)
            assertEquals(4, finished.feeling)
            assertEquals(62.5, finished.workout.exercises.single().sets.single().actual.weightKg)
            repo.addPhoto("w1", byteArrayOf(1, 2, 3), takenAt = 6000)
            assertEquals(1, repo.photosOf("w1").size)
        } finally {
            db.close()
        }
    }

    /**
     * v5 → v6: the coach chat tables are added (pure addition). Existing data (here a workout and a coach memory)
     * stays, the new tables are empty and work with cascading delete.
     */
    @Test
    fun migration5to6KeepsDataAndAddsEmptyCoachChatTables() = runBlocking<Unit> {
        val helper = MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            dbFile,
            driver,
            OverfitDatabase::class,
            { OverfitDatabaseConstructor.initialize() },
        )
        val v5 = helper.createDatabase(5)
        v5.execSQL(
            "INSERT INTO workout_session (id, name, templateId, status, startedAt, endedAt, note, feeling, photoPath, aiStatus, restStartedAt, restEndsAt, restAfterSetId, updatedAt) " +
                "VALUES ('w1', 'Push', NULL, 'FINISHED', 1000, 5000, NULL, NULL, NULL, 'NONE', NULL, NULL, NULL, 5000)",
        )
        v5.execSQL("INSERT INTO coach_memory (text, createdAt, source, reviewId) VALUES ('Ziel: Kraft', 1, 'USER', NULL)")
        v5.close()

        val v6 = helper.runMigrationsAndValidate(6, OverfitDatabase.MIGRATIONS.toList())
        assertEquals(1L, v6.count("workout_session"))
        assertEquals(1L, v6.count("coach_memory"))
        assertEquals(0L, v6.count("coach_chat"))
        assertEquals(0L, v6.count("coach_chat_message"))
        v6.close()

        val db = Room.databaseBuilder<OverfitDatabase>(context, dbFile.absolutePath)
            .setDriver(driver)
            .addMigrations(*OverfitDatabase.MIGRATIONS)
            .build()
        try {
            assertEquals("Ziel: Kraft", db.coachDao().currentMemory()?.text)
            val dao = db.coachChatDao()
            dao.upsertChat(CoachChatEntity("c1", "Frage", 10, 10))
            dao.upsertMessage(CoachChatMessageEntity("m1", "c1", fromUser = true, text = "Frage", status = "DONE", createdAt = 10))
            dao.upsertMessage(CoachChatMessageEntity("m2", "c1", fromUser = false, text = "Antwort", status = "DONE", createdAt = 11, costMicro = 1200))
            assertEquals(listOf("m1", "m2"), dao.messages("c1").map { it.id })
            dao.deleteChat("c1")
            assertEquals(0, dao.allMessages().size)
        } finally {
            db.close()
        }
    }

    private suspend fun SQLiteConnection.count(table: String): Long =
        prepare("SELECT COUNT(*) FROM $table").use { it.step(); it.getLong(0) }

    private companion object {
        const val TEST_DB = "migration-test"
    }

    @Test
    fun migration3to4KeepsPhotosAndAddsEmptyCoachTables() = runBlocking<Unit> {
        val helper = MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            dbFile,
            driver,
            OverfitDatabase::class,
            { OverfitDatabaseConstructor.initialize() },
        )
        val v3 = helper.createDatabase(3)
        v3.execSQL(
            "INSERT INTO workout_session (id, name, templateId, status, startedAt, endedAt, note, feeling, photoPath, aiStatus, restStartedAt, restEndsAt, restAfterSetId, updatedAt) " +
                "VALUES ('w1', 'Push', NULL, 'FINISHED', 1, 2, NULL, NULL, NULL, 'NONE', NULL, NULL, NULL, 3)",
        )
        v3.execSQL("INSERT INTO workout_photo (id, sessionId, fileName, takenAt, position) VALUES ('p1', 'w1', 'p1.jpg', 2, 0)")
        v3.close()

        // Validates the migrated schema against schemas/.../4.json.
        val v4 = helper.runMigrationsAndValidate(4, OverfitDatabase.MIGRATIONS.toList())
        try {
            assertEquals(1L, v4.count("workout_session"))
            assertEquals(1L, v4.count("workout_photo"))
            assertEquals("coach_review exists and is empty", 0L, v4.count("coach_review"))
            assertEquals("coach_memory exists and is empty", 0L, v4.count("coach_memory"))
        } finally {
            v4.close()
        }
    }

    /**
     * v4 → v5: free-exercise-db removed. Used `fedb:*` exercises (template, workout, replaced exercise) become custom
     * exercises with the same ID and no image; unused ones are deleted; Workout Guide and custom exercises stay as
     * they are; the new `note` column is empty. Seeding the new catalogue afterwards does not bring `fedb:*` back.
     */
    @Test
    fun migration4to5ConvertsUsedFreeDbExercisesAndDeletesUnusedOnes() = runBlocking<Unit> {
        val helper = MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            dbFile,
            driver,
            OverfitDatabase::class,
            { OverfitDatabaseConstructor.initialize() },
        )
        val v4 = helper.createDatabase(4)
        fun exercise(id: String, name: String, source: String, image: String?) = v4.execSQL(
            "INSERT INTO exercise (id, name, source, imageRef, primaryMuscles, secondaryMuscles, equipment, trackingType, defaultRestSec, isArchived, createdAt, updatedAt) " +
                "VALUES ('$id', '$name', '$source', ${image?.let { "'$it'" } ?: "NULL"}, 'biceps', 'forearms', 'barbell', 'WEIGHT_REPS', 75, 0, 1, 2)",
        )
        exercise("fedb:Barbell_Curl", "Barbell Curl", "FREE_DB", "https://raw.githubusercontent.com/x/Barbell_Curl/0.jpg")
        exercise("fedb:Zercher_Squat", "Zercher Squat", "FREE_DB", "https://raw.githubusercontent.com/x/Zercher/0.jpg")
        exercise("fedb:Old_Row", "Old Row", "FREE_DB", "https://raw.githubusercontent.com/x/Old_Row/0.jpg")
        exercise("fedb:Unused", "Unused", "FREE_DB", "https://raw.githubusercontent.com/x/Unused/0.jpg")
        exercise("wg:bench-press", "Bench Press", "WORKOUT_GUIDE", "catalog/wg/bench-press.svg")
        exercise("custom:1", "Mine", "CUSTOM", null)
        // Template uses the curl, a finished workout the squat and – as replaced exercise – the old row.
        v4.execSQL("INSERT INTO workout_template (id, name, note, position, createdAt, updatedAt) VALUES (1, 'Arms', NULL, 0, 1, 2)")
        v4.execSQL("INSERT INTO template_exercise (id, templateId, exerciseId, position, restSec, note) VALUES (1, 1, 'fedb:Barbell_Curl', 0, NULL, NULL)")
        v4.execSQL("INSERT INTO template_set (id, templateExerciseId, position, targetWeightKg, targetReps, targetDurationSec) VALUES (1, 1, 0, 30.0, 10, NULL)")
        v4.execSQL(
            "INSERT INTO workout_session (id, name, templateId, status, startedAt, endedAt, note, feeling, photoPath, aiStatus, restStartedAt, restEndsAt, restAfterSetId, updatedAt) " +
                "VALUES ('w1', 'Legs', NULL, 'FINISHED', 1000, 5000, NULL, NULL, NULL, 'NONE', NULL, NULL, NULL, 5000)",
        )
        v4.execSQL("INSERT INTO session_exercise (id, sessionId, exerciseId, position, restSec, note, origin, replacedExerciseId) VALUES ('se1', 'w1', 'fedb:Zercher_Squat', 0, 90, NULL, 'REPLACED', 'fedb:Old_Row')")
        v4.execSQL(
            "INSERT INTO set_entry (id, sessionExerciseId, position, targetWeightKg, targetReps, targetDurationSec, weightKg, reps, durationSec, completed, completedAt, rpe) " +
                "VALUES ('s1', 'se1', 0, 60.0, 8, NULL, 62.5, 8, NULL, 1, 2000, NULL)",
        )
        v4.close()

        // Validates the migrated schema against schemas/.../5.json.
        helper.runMigrationsAndValidate(5, OverfitDatabase.MIGRATIONS.toList()).close()

        val db = Room.databaseBuilder<OverfitDatabase>(context, dbFile.absolutePath)
            .setDriver(driver)
            .addMigrations(*OverfitDatabase.MIGRATIONS)
            .build()
        try {
            val dao = db.exerciseDao()
            listOf("fedb:Barbell_Curl", "fedb:Zercher_Squat", "fedb:Old_Row").forEach { id ->
                val e = dao.get(id)
                assertNotNull("$id is kept", e)
                assertEquals(app.overfit.domain.ExerciseSource.CUSTOM, e!!.source)
                assertNull("$id has no image (initials tile)", e.imageRef)
                assertEquals(listOf("biceps"), e.primaryMuscles)
                assertEquals(75, e.defaultRestSec)
                assertNull(e.note)
            }
            assertEquals("Barbell Curl", dao.get("fedb:Barbell_Curl")!!.name)
            assertNull("unused fedb exercise is deleted", dao.get("fedb:Unused"))
            val bench = dao.get("wg:bench-press")!!
            assertEquals(app.overfit.domain.ExerciseSource.WORKOUT_GUIDE, bench.source)
            assertEquals("catalog/wg/bench-press.svg", bench.imageRef)
            assertEquals(app.overfit.domain.ExerciseSource.CUSTOM, dao.get("custom:1")!!.source)
            assertEquals(5, dao.count()) // 6 inserted, the unused fedb one deleted

            // Template and workout still load with the converted exercises.
            val template = db.templateDao().getWithExercises(1)!!
            assertEquals("fedb:Barbell_Curl", template.exercises.single().exercise.id)
            assertEquals(10, template.exercises.single().sets.single().targetReps)
            val finished = app.overfit.data.WorkoutRepository(db).getFinished("w1")!!
            assertEquals(62.5, finished.workout.exercises.single().sets.single().actual.weightKg)

            // The note column is usable.
            dao.upsert(dao.get("custom:1")!!.copy(note = "Langsam ablassen"))
            assertEquals("Langsam ablassen", dao.get("custom:1")!!.note)

            // Seeding the new catalogue (Workout Guide only) never brings free-exercise-db back.
            val catalogFile = File("../shared/src/commonMain/composeResources/files/catalog/exercises.json")
            val seeder = app.overfit.data.catalog.CatalogSeeder(db, app.overfit.data.InMemoryKeyValueStore()) { catalogFile.readBytes() }
            seeder.seedIfNeeded(now = 10)
            assertNull(dao.get("fedb:Unused"))
            assertEquals(app.overfit.domain.ExerciseSource.CUSTOM, dao.get("fedb:Barbell_Curl")!!.source)
            assertNotNull(dao.get("wg:squat"))
            val all = dao.getActive()
            assertEquals(0, all.count { it.source == app.overfit.domain.ExerciseSource.FREE_DB })
            assertEquals(3, all.count { it.id.startsWith("fedb:") })
        } finally {
            db.close()
        }
    }
}
