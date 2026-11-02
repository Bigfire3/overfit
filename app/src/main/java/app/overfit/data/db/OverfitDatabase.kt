package app.overfit.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration

@Database(
    entities = [
        ExerciseEntity::class,
        WorkoutTemplateEntity::class,
        TemplateExerciseEntity::class,
        TemplateSetEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class OverfitDatabase : RoomDatabase() {
    abstract fun exerciseDao(): ExerciseDao
    abstract fun templateDao(): TemplateDao

    companion object {
        const val NAME = "overfit.db"

        /**
         * Alle Migrationen. Regel (SPEC): niemals destruktiv migrieren – jede Schemaänderung bekommt
         * hier eine Migration und einen Migrationstest (MigrationTest).
         */
        val MIGRATIONS: Array<Migration> = arrayOf()

        fun build(context: Context): OverfitDatabase =
            Room.databaseBuilder(context, OverfitDatabase::class.java, NAME)
                .addMigrations(*MIGRATIONS)
                .build()
    }
}

class Converters {
    /** Schlüssel-Listen (Muskelgruppen) als kommagetrennter Text; Schlüssel enthalten keine Kommas. */
    @TypeConverter
    fun fromList(value: List<String>): String = value.joinToString(",")

    @TypeConverter
    fun toList(value: String): List<String> = if (value.isEmpty()) emptyList() else value.split(',')
}
