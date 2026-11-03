package app.overfit.data

/**
 * App-private photo directory (Android `filesDir/photos`, iOS Application Support/photos).
 * Photos never go to the gallery and are never shared; only the backup contains them.
 * Names are plain file names without directories.
 */
interface PhotoFiles {
    fun write(name: String, bytes: ByteArray)
    fun read(name: String): ByteArray?
    fun delete(name: String)
    fun list(): List<String>
    /** Absolute path for the image loader. */
    fun path(name: String): String
}

class InMemoryPhotoFiles : PhotoFiles {
    val files = mutableMapOf<String, ByteArray>()
    override fun write(name: String, bytes: ByteArray) { files[name] = bytes.copyOf() }
    override fun read(name: String) = files[name]?.copyOf()
    override fun delete(name: String) { files.remove(name) }
    override fun list() = files.keys.sorted()
    override fun path(name: String) = "/memory/photos/$name"
}

/** Only simple names (no paths) are accepted, e.g. from a backup file. */
fun isSafePhotoName(name: String): Boolean =
    name.isNotEmpty() && name.length <= 100 && name.all { it.isLetterOrDigit() || it == '-' || it == '_' || it == '.' } &&
        !name.startsWith(".")
