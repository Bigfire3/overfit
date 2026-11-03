package app.overfit.platform

import app.overfit.data.PhotoFiles
import app.overfit.data.isSafePhotoName
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.writeToFile

/** Photos in Application Support/photos: private to the app, not in the photo library. */
@OptIn(ExperimentalForeignApi::class)
class IosPhotoFiles : PhotoFiles {
    private val dir: String by lazy {
        val base = NSSearchPathForDirectoriesInDomains(NSApplicationSupportDirectory, NSUserDomainMask, true).first() as String
        val d = "$base/photos"
        NSFileManager.defaultManager.createDirectoryAtPath(d, withIntermediateDirectories = true, attributes = null, error = null)
        d
    }

    private fun file(name: String): String {
        require(isSafePhotoName(name)) { "Invalid photo name" }
        return "$dir/$name"
    }

    override fun write(name: String, bytes: ByteArray) {
        bytes.toNSData().writeToFile(file(name), atomically = true)
    }

    override fun read(name: String): ByteArray? = runCatching { NSData.dataWithContentsOfFile(file(name))?.toByteArray() }.getOrNull()

    override fun delete(name: String) {
        runCatching { NSFileManager.defaultManager.removeItemAtPath(file(name), error = null) }
    }

    override fun list(): List<String> =
        NSFileManager.defaultManager.contentsOfDirectoryAtPath(dir, error = null)?.map { it as String }?.sorted().orEmpty()

    override fun path(name: String): String = file(name)
}
