package app.overfit.platform

import android.content.Context
import app.overfit.data.PhotoFiles
import app.overfit.data.isSafePhotoName
import java.io.File

class AndroidPhotoFiles(context: Context) : PhotoFiles {
    private val dir = File(context.filesDir, "photos")

    private fun file(name: String): File {
        require(isSafePhotoName(name)) { "Invalid photo name" }
        return File(dir, name)
    }

    override fun write(name: String, bytes: ByteArray) {
        dir.mkdirs()
        val target = file(name)
        val tmp = File(dir, "$name.tmp")
        tmp.writeBytes(bytes)
        if (!tmp.renameTo(target)) { target.writeBytes(bytes); tmp.delete() }
    }

    override fun read(name: String): ByteArray? = runCatching { file(name).takeIf { it.isFile }?.readBytes() }.getOrNull()

    override fun delete(name: String) { runCatching { file(name).delete() } }

    override fun list(): List<String> = dir.listFiles()?.filter { it.isFile && !it.name.endsWith(".tmp") }?.map { it.name }?.sorted().orEmpty()

    override fun path(name: String): String = file(name).absolutePath
}
