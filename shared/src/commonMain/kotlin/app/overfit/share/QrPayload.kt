package app.overfit.share

import kotlin.math.roundToInt

/**
 * App-internal QR content for a training: `OF1:` + Base45 of a compact binary form (optionally deflated).
 * Only Overfit reads it; the shared *link* stays the https URL (see docs/share-format.md). Compared with the link
 * (JSON → deflate → Base64url, byte mode, ECC M) this needs far fewer modules, see docs/DECISIONS.md.
 *
 * Binary layout (all integers LEB128 varints, strings = varint length + UTF-8):
 * ```
 * (catalogue IDs `wg:…` are stored as a 24-bit hash, refKind 3)
 * byte   flags: 0 = raw body, 1 = body is deflated
 * body:  version(1) kind(0 template,1 workout) name note("" = none)
 *        exerciseCount { refKind(0 id,1 name,2 custom) hasRest hasNote … }
 * ```
 * Sets are run-length grouped (4 identical sets = one group), weights in 1/100 kg.
 */
object QrPayload {
    const val PREFIX = "OF1:"
    /** Short code over the backend: only an ID in the QR, see docs/backend-api.md. */
    const val SHORT_PREFIX = "OFS:"

    fun isQrText(text: String) = text.trim().let { it.startsWith(PREFIX) || it.startsWith(SHORT_PREFIX) }

    fun encode(t: SharedTraining): String {
        val raw = Writer().apply { body(t) }.toByteArray()
        val packed = Deflate.compress(raw)
        val out = if (packed.size + 0 < raw.size) byteArrayOf(1) + packed else byteArrayOf(0) + raw
        return PREFIX + Base45.encode(out)
    }

    /** Null if [text] is not a valid `OF1:` content. Never throws. */
    fun decode(text: String): SharedTraining? {
        val t = text.trim()
        if (!t.startsWith(PREFIX)) return null
        val bytes = Base45.decode(t.removePrefix(PREFIX)) ?: return null
        if (bytes.isEmpty()) return null
        return runCatching {
            val body = when (bytes[0].toInt()) {
                0 -> bytes.copyOfRange(1, bytes.size)
                1 -> Deflate.decompress(bytes.copyOfRange(1, bytes.size), ShareFormat.MAX_JSON_BYTES)
                else -> return null
            }
            Reader(body).training()
        }.getOrNull()
    }

    /** Short-code ID of an `OFS:` text, else null. */
    fun shortId(text: String): String? =
        text.trim().takeIf { it.startsWith(SHORT_PREFIX) }?.removePrefix(SHORT_PREFIX)?.takeIf { SHORT_ID.matches(it) }

    private val SHORT_ID = Regex("[0-9A-Z]{4,16}")

    /** Catalogue IDs (`wg:…`) travel as 3 bytes: the first 24 bits of FNV-1a. The receiver maps them back through its own catalogue. */
    const val HASHED_ID_PREFIX = "#"
    private const val CATALOG_PREFIX = "wg:"

    fun idHash(id: String): Int {
        var h = 0x811C9DC5.toInt()
        for (b in id.encodeToByteArray()) h = (h xor (b.toInt() and 0xFF)) * 0x01000193
        return h and 0xFFFFFF
    }

    fun hashedId(id: String): String = HASHED_ID_PREFIX + idHash(id).toString(16).padStart(6, '0')

    private val TRACKING = listOf("WEIGHT_REPS", "REPS", "TIME", "WEIGHT_TIME")

    private class Writer {
        private val buf = ArrayList<Byte>()
        fun toByteArray() = buf.toByteArray()
        fun byte(v: Int) { buf.add(v.toByte()) }
        fun varint(v: Int) { var x = v; while (x >= 0x80) { byte((x and 0x7F) or 0x80); x = x ushr 7 }; byte(x) }
        fun str(s: String?) { val b = (s ?: "").encodeToByteArray(); varint(b.size); b.forEach { buf.add(it) } }
        fun strings(l: List<String>) { varint(l.size); l.forEach { str(it) } }

        fun body(t: SharedTraining) {
            byte(1)
            byte(if (t.kind == ShareFormat.KIND_WORKOUT) 1 else 0)
            str(t.name); str(t.note)
            varint(t.exercises.size)
            t.exercises.forEach { e ->
                val hashed = e.ref == null && e.id?.startsWith(CATALOG_PREFIX) == true
                val kind = when { e.ref != null -> 2; hashed -> 3; e.id != null -> 0; else -> 1 }
                byte(kind or (if (e.restSec != null) 4 else 0) or (if (e.note != null) 8 else 0))
                if (hashed) { val h = idHash(e.id!!); byte(h ushr 16); byte(h ushr 8); byte(h) } else str(e.ref ?: e.id ?: e.name ?: "")
                e.restSec?.let { varint(it) }
                e.note?.let { str(it) }
                val groups = ArrayList<Pair<SharedSet, Int>>()
                e.sets.forEach { s -> if (groups.isNotEmpty() && groups.last().first == s) groups[groups.size - 1] = s to groups.last().second + 1 else groups.add(s to 1) }
                varint(groups.size)
                groups.forEach { (s, n) ->
                    varint(n)
                    byte((if (s.kg != null) 1 else 0) or (if (s.reps != null) 2 else 0) or (if (s.sec != null) 4 else 0))
                    s.kg?.let { varint(zigzag((it * 100).roundToInt())) }
                    s.reps?.let { varint(it) }
                    s.sec?.let { varint(it) }
                }
            }
            varint(t.custom.size)
            t.custom.forEach { c ->
                str(c.ref); str(c.name)
                byte(TRACKING.indexOf(c.trackingType).coerceAtLeast(0))
                byte((if (c.equipment != null) 1 else 0) or (if (c.restSec != null) 2 else 0) or (if (c.note != null) 4 else 0))
                strings(c.primary); strings(c.secondary)
                c.equipment?.let { str(it) }; c.restSec?.let { varint(it) }; c.note?.let { str(it) }
            }
        }
    }

    private class Reader(private val b: ByteArray) {
        private var p = 0
        fun byte(): Int { if (p >= b.size) throw IllegalStateException("eof"); return b[p++].toInt() and 0xFF }
        fun varint(): Int { var r = 0; var shift = 0; while (true) { val x = byte(); r = r or ((x and 0x7F) shl shift); if (x < 0x80) return r; shift += 7; if (shift > 28) throw IllegalStateException("varint") } }
        fun str(): String { val n = varint(); if (n < 0 || p + n > b.size) throw IllegalStateException("eof"); return b.decodeToString(p, p + n).also { p += n } }
        fun hashedId3(): String = HASHED_ID_PREFIX + ((byte() shl 16) or (byte() shl 8) or byte()).toString(16).padStart(6, '0')
        fun strings(): List<String> = List(varint().also { require(it <= 20) }) { str() }

        fun training(): SharedTraining {
            require(byte() == 1)
            val kind = if (byte() == 1) ShareFormat.KIND_WORKOUT else ShareFormat.KIND_TEMPLATE
            val name = str(); val note = str().ifEmpty { null }
            val n = varint(); require(n <= ShareFormat.MAX_EXERCISES)
            val exercises = List(n) {
                val flags = byte()
                val ref = if (flags and 3 == 3) hashedId3() else str()
                val rest = if (flags and 4 != 0) varint() else null
                val enote = if (flags and 8 != 0) str() else null
                val groups = varint()
                val sets = ArrayList<SharedSet>()
                repeat(groups) {
                    val count = varint(); val f = byte()
                    val kg = if (f and 1 != 0) unzigzag(varint()) / 100.0 else null
                    val reps = if (f and 2 != 0) varint() else null
                    val sec = if (f and 4 != 0) varint() else null
                    require(sets.size + count <= ShareFormat.MAX_SETS)
                    repeat(count) { sets.add(SharedSet(kg, reps, sec)) }
                }
                when (flags and 3) {
                    0 -> SharedExercise(id = ref, sets = sets, restSec = rest, note = enote)
                    3 -> SharedExercise(id = ref, sets = sets, restSec = rest, note = enote)
                    2 -> SharedExercise(ref = ref, sets = sets, restSec = rest, note = enote)
                    else -> SharedExercise(name = ref, sets = sets, restSec = rest, note = enote)
                }
            }
            val custom = List(varint().also { require(it <= ShareFormat.MAX_EXERCISES) }) {
                val ref = str(); val cname = str()
                val tracking = TRACKING.getOrElse(byte()) { "WEIGHT_REPS" }
                val f = byte()
                val primary = strings(); val secondary = strings()
                val equipment = if (f and 1 != 0) str() else null
                val rest = if (f and 2 != 0) varint() else null
                val cnote = if (f and 4 != 0) str() else null
                SharedCustom(ref, cname, tracking, primary.firstOrNull(), equipment, primary, secondary, rest, cnote)
            }
            return SharedTraining(1, kind, name, note, exercises, custom)
        }
    }

    private fun zigzag(v: Int) = (v shl 1) xor (v shr 31)
    private fun unzigzag(v: Int) = (v ushr 1) xor -(v and 1)
}

/**
 * Backend for short codes (not deployed yet, see docs/backend-api.md): a training is stored under a short ID so the QR
 * only carries `OFS:<ID>` (version 1–2, about 25 modules). Offline or without backend [upload] returns null and the
 * caller falls back to the full `OF1:` content.
 */
interface ShortCodeService {
    suspend fun upload(training: SharedTraining): String?
    suspend fun fetch(id: String): SharedTraining?
}

/** Default until the backend is deployed. */
object NoShortCodes : ShortCodeService {
    override suspend fun upload(training: SharedTraining): String? = null
    override suspend fun fetch(id: String): SharedTraining? = null
}

/** QR text to show for a training: short code if the backend gives one, else the full offline content. */
suspend fun qrTextFor(training: SharedTraining, shortCodes: ShortCodeService = NoShortCodes): String =
    runCatching { shortCodes.upload(training) }.getOrNull()?.let { QrPayload.SHORT_PREFIX + it } ?: QrPayload.encode(training)
