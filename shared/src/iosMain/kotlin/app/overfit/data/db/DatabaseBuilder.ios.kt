package app.overfit.data.db

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

/** iOS: bundled SQLite, database in the app's Documents directory (backed up, not visible to the user). */
@OptIn(ExperimentalForeignApi::class)
fun buildDatabase(name: String = OverfitDatabase.NAME, fresh: Boolean = false): OverfitDatabase {
    val dir = NSFileManager.defaultManager.URLForDirectory(NSDocumentDirectory, NSUserDomainMask, null, true, null)
    val path = requireNotNull(dir?.path) { "No documents directory" } + "/" + name
    // UI tests start from an empty database (also removes the WAL/SHM side files).
    if (fresh) listOf("", "-wal", "-shm").forEach { NSFileManager.defaultManager.removeItemAtPath(path + it, null) }
    return Room.databaseBuilder<OverfitDatabase>(name = path)
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .configure()
}
