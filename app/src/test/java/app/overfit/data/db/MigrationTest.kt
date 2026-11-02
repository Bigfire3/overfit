package app.overfit.data.db

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Migrationstests auf Basis der exportierten Schemas (app/schemas).
 * Künftig kommt je Migration ein Test dazu: Datenbank in Version N-1 erzeugen, Testdaten
 * einfügen, mit runMigrationsAndValidate auf N migrieren und die Daten prüfen.
 */
@RunWith(RobolectricTestRunner::class)
class MigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), OverfitDatabase::class.java)

    @Test
    fun version1OpensWithAllMigrationsAndPassesSchemaValidation() {
        helper.createDatabase(TEST_DB, 1).close()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        Room.databaseBuilder(context, OverfitDatabase::class.java, TEST_DB)
            .addMigrations(*OverfitDatabase.MIGRATIONS)
            .build()
            .apply {
                openHelper.writableDatabase // löst Migration und Schema-Validierung aus
                close()
            }
    }

    private companion object {
        const val TEST_DB = "migration-test"
    }
}
