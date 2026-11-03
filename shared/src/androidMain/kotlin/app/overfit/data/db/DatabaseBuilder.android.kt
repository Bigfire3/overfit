package app.overfit.data.db

import android.content.Context
import androidx.room.Room

/** Android: system SQLite (Room default), database in the app's private database directory. */
fun buildDatabase(context: Context): OverfitDatabase {
    val app = context.applicationContext
    return Room.databaseBuilder<OverfitDatabase>(app, app.getDatabasePath(OverfitDatabase.NAME).absolutePath).configure()
}
