package app.overfit.data.db

import androidx.room.AutoMigration
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.immediateTransaction
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import androidx.room.useWriterConnection

@Database(
    entities = [
        ExerciseEntity::class,
        WorkoutTemplateEntity::class,
        TemplateExerciseEntity::class,
        TemplateSetEntity::class,
        WorkoutSessionEntity::class,
        SessionExerciseEntity::class,
        SetEntryEntity::class,
        WorkoutPhotoEntity::class,
        CoachReviewEntity::class,
        CoachMemoryEntity::class,
        CoachChatEntity::class,
        CoachChatMessageEntity::class,
        BodyWeightEntity::class,
        AiRequestEntity::class,
    ],
    version = 13,
    exportSchema = true,
    // v1 → v2 adds the workout tables, v2 → v3 the photo table, v3 → v4 the coach tables (nothing is dropped or rewritten).
    // v4 → v5 is manual ([MIGRATION_4_5]): free-exercise-db removed, exercise.note added.
    // v5 → v6 adds the coach chat tables (pure addition).
    // v6 → v7 adds the nullable columns servedModel / fallbackFrom to the coach review and chat message (pure addition).
    // v7 → v8 adds workout_session.sauna (NOT NULL, default 0 = no sauna; pure addition).
    // v8 → v9 makes workout_photo.sessionId nullable (photos on calendar days without a training). Room recreates the
    // table and copies every row; the foreign key (CASCADE) and the indices stay.
    // v9 → v10 adds the body_weight table (pure addition).
    // v10 → v11 adds the AI request log `ai_request` (pure addition).
    // v11 → v12 adds coach_chat.profileOff (NOT NULL, default 0) and coach_chat_message.attachments / providerId / model
    // (nullable) for the chat redesign (pure addition).
    // v12 → v13 is manual ([MIGRATION_12_13]): workout_photo.day – photos belong to a calendar day, not a training.
    autoMigrations = [
        AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3), AutoMigration(from = 3, to = 4),
        AutoMigration(from = 5, to = 6), AutoMigration(from = 6, to = 7),
        AutoMigration(from = 7, to = 8), AutoMigration(from = 8, to = 9),
        AutoMigration(from = 9, to = 10), AutoMigration(from = 10, to = 11), AutoMigration(from = 11, to = 12),
    ],
)
@TypeConverters(Converters::class)
@ConstructedBy(OverfitDatabaseConstructor::class)
abstract class OverfitDatabase : RoomDatabase() {
    abstract fun exerciseDao(): ExerciseDao
    abstract fun templateDao(): TemplateDao
    abstract fun workoutDao(): WorkoutDao
    abstract fun coachDao(): CoachDao
    abstract fun coachChatDao(): CoachChatDao
    abstract fun aiRequestDao(): AiRequestDao
    abstract fun bodyWeightDao(): BodyWeightDao

    companion object {
        const val NAME = "overfit.db"

        /**
         * Manual migrations. Rule (SPEC): never migrate destructively – every schema change gets a
         * migration (here or as an [AutoMigration] for pure additions) and a migration test.
         */
        val MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_4_5, MIGRATION_12_13)
    }
}

/**
 * v4 → v5: the free-exercise-db catalogue was removed (photos did not fit the design).
 * - `exercise.note` is added (execution note of custom exercises).
 * - free-exercise-db exercises that templates or workouts use (also as the replaced exercise) become custom exercises:
 *   same ID (no foreign key has to change, backups and coach texts keep matching), no image (→ initials tile).
 * - Unused free-exercise-db exercises are deleted. The new catalogue no longer contains them, so seeding cannot bring
 *   them back.
 */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE exercise ADD COLUMN note TEXT")
        connection.execSQL(
            """UPDATE exercise SET source = 'CUSTOM', imageRef = NULL
               WHERE source = 'FREE_DB' AND (
                   id IN (SELECT exerciseId FROM template_exercise)
                   OR id IN (SELECT exerciseId FROM session_exercise)
                   OR id IN (SELECT replacedExerciseId FROM session_exercise WHERE replacedExerciseId IS NOT NULL))""",
        )
        connection.execSQL("DELETE FROM exercise WHERE source = 'FREE_DB'")
    }
}

/**
 * v12 → v13: photos belong to a calendar day (`workout_photo.day`, ISO date), not to a training.
 * Nothing is dropped: `sessionId` stays as it was. Every existing photo gets its day:
 * - a photo of a training → the day the training started (where the training detail showed it, also when it was
 *   taken after midnight or added days later),
 * - a photo without a training → the day it was taken (where the calendar showed it).
 * Days are computed in the device time zone, like the calendar did until now.
 */
val MIGRATION_12_13: Migration = photoDayMigration(TimeZone.currentSystemDefault())

/** [MIGRATION_12_13] for a given [zone] (tests). */
fun photoDayMigration(zone: TimeZone): Migration = object : Migration(12, 13) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE workout_photo ADD COLUMN day TEXT NOT NULL DEFAULT ''")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_workout_photo_day` ON `workout_photo` (`day`)")
        val days = mutableListOf<Pair<String, String>>()
        val select = connection.prepare(
            "SELECT p.id, p.takenAt, s.startedAt FROM workout_photo p LEFT JOIN workout_session s ON s.id = p.sessionId",
        )
        try {
            while (select.step()) {
                val ms = if (select.isNull(2)) select.getLong(1) else select.getLong(2)
                days += select.getText(0) to photoDay(ms, zone)
            }
        } finally {
            select.close()
        }
        val update = connection.prepare("UPDATE workout_photo SET day = ? WHERE id = ?")
        try {
            days.forEach { (id, day) ->
                update.bindText(1, day)
                update.bindText(2, id)
                update.step()
                update.reset()
            }
        } finally {
            update.close()
        }
    }
}

/** ISO calendar day (`yyyy-MM-dd`) of an epoch-ms instant in [zone]. */
fun photoDay(epochMs: Long, zone: TimeZone = TimeZone.currentSystemDefault()): String =
    kotlin.time.Instant.fromEpochMilliseconds(epochMs).toLocalDateTime(zone).date.toString()

/** Generated by Room for every platform. */
@Suppress("NO_ACTUAL_FOR_EXPECT", "KotlinNoActualForExpect")
expect object OverfitDatabaseConstructor : RoomDatabaseConstructor<OverfitDatabase> {
    override fun initialize(): OverfitDatabase
}

/** Common builder configuration; the platform supplies file location and driver. */
fun RoomDatabase.Builder<OverfitDatabase>.configure(): OverfitDatabase =
    addMigrations(*OverfitDatabase.MIGRATIONS).build()

/** Runs [block] in one write transaction (DAO calls inside share the connection). */
suspend fun <R> OverfitDatabase.transaction(block: suspend () -> R): R =
    useWriterConnection { it.immediateTransaction { block() } }

class Converters {
    /** Key lists (muscle groups) as comma-separated text; keys never contain commas. */
    @TypeConverter
    fun fromList(value: List<String>): String = value.joinToString(",")

    @TypeConverter
    fun toList(value: String): List<String> = if (value.isEmpty()) emptyList() else value.split(',')
}
