package app.overfit.data.backup

import app.overfit.share.Deflate

/**
 * Minimal ZIP (PKWARE APPNOTE) writer and reader for backups: method 0 (stored, for JPEGs) and
 * 8 (raw deflate, for the JSON). No ZIP64, no encryption, no multi-disk. Any standard unzip tool
 * can open the file; the reader also refuses anything it does not understand instead of crashing.
 */
object Zip {
    class Entry(val name: String, val data: ByteArray, val compress: Boolean = false)

    class Error(message: String) : Exception(message)

    private const val LOCAL = 0x04034b50
    private const val CENTRAL = 0x02014b50
    private const val END = 0x06054b50
    private const val FLAG_UTF8 = 0x0800
    // 1980-01-01 00:00 (DOS date): fixed so the same data gives the same file.
    private const val DOS_DATE = (0 shl 9) or (1 shl 5) or 1

    fun write(entries: List<Entry>): ByteArray {
        val out = Bytes()
        val central = Bytes()
        entries.forEach { e ->
            val name = e.name.encodeToByteArray()
            val crc = Crc32.of(e.data)
            val packed = if (e.compress) Deflate.compress(e.data) else e.data
            val method = if (e.compress) 8 else 0
            val offset = out.size
            out.int(LOCAL); out.short(20); out.short(FLAG_UTF8); out.short(method); out.short(0); out.short(DOS_DATE)
            out.int(crc); out.int(packed.size); out.int(e.data.size); out.short(name.size); out.short(0)
            out.bytes(name); out.bytes(packed)

            central.int(CENTRAL); central.short(20); central.short(20); central.short(FLAG_UTF8); central.short(method)
            central.short(0); central.short(DOS_DATE); central.int(crc); central.int(packed.size); central.int(e.data.size)
            central.short(name.size); central.short(0); central.short(0); central.short(0); central.short(0); central.int(0)
            central.int(offset); central.bytes(name)
        }
        val cdOffset = out.size
        val cd = central.toByteArray()
        out.bytes(cd)
        out.int(END); out.short(0); out.short(0); out.short(entries.size); out.short(entries.size)
        out.int(cd.size); out.int(cdOffset); out.short(0)
        return out.toByteArray()
    }

    /** All entries as name → content. Throws [Error] on anything malformed. */
    fun read(zip: ByteArray, maxEntrySize: Int = 64 shl 20): Map<String, ByteArray> {
        val end = (zip.size - 22 downTo maxOf(0, zip.size - 22 - 65_535)).firstOrNull { zip.int(it) == END }
            ?: throw Error("No zip")
        val count = zip.short(end + 10)
        var p = zip.int(end + 16)
        val result = LinkedHashMap<String, ByteArray>()
        repeat(count) {
            if (p < 0 || p + 46 > zip.size || zip.int(p) != CENTRAL) throw Error("Bad central directory")
            val method = zip.short(p + 10)
            val crc = zip.int(p + 16)
            val packedSize = zip.int(p + 20)
            val size = zip.int(p + 24)
            val nameLen = zip.short(p + 28)
            val extraLen = zip.short(p + 30)
            val commentLen = zip.short(p + 32)
            val local = zip.int(p + 42)
            if (p + 46 + nameLen > zip.size) throw Error("Bad name")
            val name = zip.copyOfRange(p + 46, p + 46 + nameLen).decodeToString()
            p += 46 + nameLen + extraLen + commentLen
            if (name.endsWith("/")) return@repeat
            if (size < 0 || size > maxEntrySize || packedSize < 0) throw Error("Entry too large")
            if (local < 0 || local + 30 > zip.size || zip.int(local) != LOCAL) throw Error("Bad local header")
            val start = local + 30 + zip.short(local + 26) + zip.short(local + 28)
            if (start + packedSize > zip.size || start + packedSize < start) throw Error("Truncated")
            val packed = zip.copyOfRange(start, start + packedSize)
            val data = when (method) {
                0 -> packed
                8 -> runCatching { Deflate.decompress(packed, maxOutput = maxOf(size, 1)) }.getOrElse { throw Error("Bad deflate") }
                else -> throw Error("Unsupported method $method")
            }
            if (data.size != size || Crc32.of(data) != crc) throw Error("Checksum mismatch in $name")
            result[name] = data
        }
        return result
    }

    private fun ByteArray.int(i: Int): Int {
        if (i < 0 || i + 4 > size) throw Error("Truncated")
        return (this[i].toInt() and 0xff) or ((this[i + 1].toInt() and 0xff) shl 8) or
            ((this[i + 2].toInt() and 0xff) shl 16) or ((this[i + 3].toInt() and 0xff) shl 24)
    }

    private fun ByteArray.short(i: Int): Int {
        if (i < 0 || i + 2 > size) throw Error("Truncated")
        return (this[i].toInt() and 0xff) or ((this[i + 1].toInt() and 0xff) shl 8)
    }

    private class Bytes {
        private var buf = ByteArray(1 shl 16)
        var size = 0
            private set

        private fun ensure(n: Int) {
            if (size + n <= buf.size) return
            var cap = buf.size
            while (cap < size + n) cap *= 2
            buf = buf.copyOf(cap)
        }

        fun short(v: Int) { ensure(2); buf[size++] = v.toByte(); buf[size++] = (v ushr 8).toByte() }
        fun int(v: Int) { short(v and 0xffff); short(v ushr 16) }
        fun bytes(b: ByteArray) { ensure(b.size); b.copyInto(buf, size); size += b.size }
        fun toByteArray() = buf.copyOf(size)
    }
}

internal object Crc32 {
    private val table = IntArray(256) { n ->
        var c = n
        repeat(8) { c = if (c and 1 != 0) (c ushr 1) xor 0xEDB88320.toInt() else c ushr 1 }
        c
    }

    fun of(data: ByteArray): Int {
        var c = -1
        for (b in data) c = table[(c xor b.toInt()) and 0xff] xor (c ushr 8)
        return c.inv()
    }
}
