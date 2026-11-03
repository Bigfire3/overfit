package app.overfit.data.db

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import android.content.Context
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

    private companion object {
        const val TEST_DB = "migration-test"
    }
}
