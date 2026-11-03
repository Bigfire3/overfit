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
}
