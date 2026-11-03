package app.overfit.share

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.put
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.math.roundToInt

/** Format of shared trainings, see docs/share-format.md. */
object ShareFormat {
    const val VERSION = 1
    const val KIND_TEMPLATE = "template"
    /** Reserved for finished workouts (phase 3). */
    const val KIND_WORKOUT = "workout"
    const val WEB_LINK_BASE = "https://bigfire3.github.io/overfit/t"
    const val APP_LINK_BASE = "overfit://t"
    const val FILE_EXTENSION = "overfit"
    const val MIME_TYPE = "application/x-overfit"
    /** Up to this link length a QR code is still easy to scan. */
    const val MAX_QR_LINK_LENGTH = 1000

    const val MAX_EXERCISES = 50
    const val MAX_SETS = 30
    const val MAX_JSON_BYTES = 200 * 1024
    const val MAX_NAME = 80
    const val MAX_NOTE = 500
    const val MAX_REST_SEC = 3600

    private val json = Json { prettyPrint = false }

    // ------------------------------------------------------------------ encode

    fun toJson(t: SharedTraining): String = json.encodeToString(JsonElement.serializer(), toElement(t))

    fun toElement(t: SharedTraining): JsonObject = buildJsonObject {
        put("v", t.v)
        put("kind", t.kind)
        put("name", t.name)
        t.note?.let { put("note", it) }
        put("exercises", buildJsonArray {
            t.exercises.forEach { e ->
                add(buildJsonObject {
                    e.ref?.let { put("ref", it) } ?: e.id?.let { put("id", it) } ?: e.name?.let { put("name", it) }
                    e.restSec?.let { put("restSec", it) }
                    e.note?.let { put("note", it) }
                    put("sets", buildJsonArray {
                        e.sets.forEach { s ->
                            add(buildJsonObject {
                                s.kg?.let { put("kg", it) }
                                s.reps?.let { put("reps", it) }
                                s.sec?.let { put("sec", it) }
                            })
                        }
                    })
                })
            }
        })
        if (t.custom.isNotEmpty()) {
            put("custom", buildJsonArray {
                t.custom.forEach { c ->
                    add(buildJsonObject {
                        put("ref", c.ref)
                        put("name", c.name)
                        put("trackingType", c.trackingType)
                        // "muscle" (first primary muscle) stays for app versions before "primary" existed.
                        (c.primary.firstOrNull() ?: c.muscle)?.let { put("muscle", it) }
                        if (c.primary.isNotEmpty()) put("primary", buildJsonArray { c.primary.forEach { add(JsonPrimitive(it)) } })
                        if (c.secondary.isNotEmpty()) put("secondary", buildJsonArray { c.secondary.forEach { add(JsonPrimitive(it)) } })
                        c.equipment?.let { put("equipment", it) }
                        c.restSec?.let { put("restSec", it) }
                        c.note?.let { put("note", it) }
                    })
                }
            })
        }
    }

    @OptIn(ExperimentalEncodingApi::class)
    fun toPayload(t: SharedTraining): String =
        Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode(Deflate.compress(toJson(t).encodeToByteArray()))

    fun toLink(t: SharedTraining, base: String = WEB_LINK_BASE): String = "$base#${toPayload(t)}"

    // ------------------------------------------------------------------ decode

    /** Reads JSON text. Tolerant to sloppy values (numbers as text, "8-12" → 8), strict on structure. */
    fun fromJson(text: String): ShareParse {
        if (text.length > MAX_JSON_BYTES) return ShareParse.Fail(ShareFailure.TOO_LARGE)
        val root = try {
            Json.parseToJsonElement(text.trim().removePrefix("﻿"))
        } catch (_: Exception) {
            return ShareParse.Fail(ShareFailure.NOT_OVERFIT)
        } as? JsonObject ?: return ShareParse.Fail(ShareFailure.NOT_OVERFIT)
        return try {
            fromElement(root)
        } catch (_: Exception) {
            ShareParse.Fail(ShareFailure.INVALID)
        }
    }

    private fun fromElement(root: JsonObject): ShareParse {
        val version = root.num("v")?.toInt() ?: return ShareParse.Fail(ShareFailure.NOT_OVERFIT)
        if (version < 1) return ShareParse.Fail(ShareFailure.INVALID)
        if (version > VERSION) return ShareParse.Fail(ShareFailure.TOO_NEW)
        val kind = root.str("kind") ?: KIND_TEMPLATE
        if (kind != KIND_TEMPLATE && kind != KIND_WORKOUT) return ShareParse.Fail(ShareFailure.UNSUPPORTED_KIND)
        val name = root.str("name")?.trim().orEmpty().take(MAX_NAME)
        if (name.isEmpty()) return ShareParse.Fail(ShareFailure.INVALID)
        val exercises = (root["exercises"] as? JsonArray) ?: return ShareParse.Fail(ShareFailure.INVALID)
        if (exercises.size > MAX_EXERCISES) return ShareParse.Fail(ShareFailure.TOO_LARGE)
        val custom = (root["custom"] as? JsonArray).orEmpty().mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            val ref = o.str("ref") ?: return@mapNotNull null
            val cname = o.str("name")?.trim()?.take(MAX_NAME)?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val muscle = o.str("muscle")
            val primary = o.strings("primary").ifEmpty { listOfNotNull(muscle) }
            SharedCustom(
                ref = ref,
                name = cname,
                trackingType = o.str("trackingType") ?: "WEIGHT_REPS",
                muscle = muscle ?: primary.firstOrNull(),
                equipment = o.str("equipment"),
                primary = primary,
                secondary = o.strings("secondary"),
                restSec = o.num("restSec")?.toInt()?.takeIf { it in 0..MAX_REST_SEC },
                note = o.str("note")?.trim()?.take(MAX_NOTE)?.takeIf { it.isNotEmpty() },
            )
        }
        val parsed = exercises.mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            val ref = o.str("ref")?.takeIf { it.isNotBlank() }
            val id = o.str("id")?.trim()?.takeIf { it.isNotEmpty() }
            val ename = o.str("name")?.trim()?.take(MAX_NAME)?.takeIf { it.isNotEmpty() }
            if (ref == null && id == null && ename == null) return@mapNotNull null
            val sets = (o["sets"] as? JsonArray).orEmpty().take(MAX_SETS).mapNotNull { s ->
                val so = s as? JsonObject ?: return@mapNotNull null
                SharedSet(so.num("kg"), so.num("reps")?.roundToInt()?.coerceIn(0, 10_000), so.num("sec")?.roundToInt()?.coerceIn(0, 86_400))
            }
            SharedExercise(id, ename, ref, sets, o.num("restSec")?.roundToInt()?.coerceIn(0, 3600), o.str("note")?.takeIf { it.isNotBlank() })
        }
        return ShareParse.Ok(SharedTraining(version, kind, name, root.str("note")?.takeIf { it.isNotBlank() }, parsed, custom))
    }

    @OptIn(ExperimentalEncodingApi::class)
    fun fromPayload(payload: String): ShareParse {
        val clean = payload.filterNot { it.isWhitespace() }.trimEnd('=').replace('+', '-').replace('/', '_')
        if (clean.isEmpty()) return ShareParse.Fail(ShareFailure.EMPTY)
        val bytes = try {
            val packed = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL).decode(clean)
            Deflate.decompress(packed, MAX_JSON_BYTES)
        } catch (e: Exception) {
            return ShareParse.Fail(if (e.message == "Output too large") ShareFailure.TOO_LARGE else ShareFailure.NOT_OVERFIT)
        }
        return fromJson(bytes.decodeToString())
    }

    /**
     * Finds a training in free text: an Overfit link, a bare payload, or JSON (also inside a
     * ```json block with text around it, as chat assistants answer).
     */
    fun parseInput(text: String): ShareParse {
        if (text.isBlank()) return ShareParse.Fail(ShareFailure.EMPTY)
        val candidates = buildList<() -> ShareParse> {
            // App-internal QR content (and short codes, which need the backend and are resolved by the caller).
            QR_REGEX.find(text)?.let { m ->
                add { QrPayload.decode(m.value)?.let { ShareParse.Ok(it) } ?: ShareParse.Fail(ShareFailure.NOT_OVERFIT) }
            }
            LINK_REGEX.find(text)?.let { m -> add { fromPayload(m.groupValues[1]) } }
            FENCE_REGEX.findAll(text).forEach { m -> add { fromJson(m.groupValues[1]) } }
            val first = text.indexOf('{')
            val last = text.lastIndexOf('}')
            if (first in 0 until last) add { fromJson(text.substring(first, last + 1)) }
            val trimmed = text.trim()
            if (!trimmed.contains('{') && trimmed.length >= 8) add { fromPayload(trimmed) }
        }
        var firstFail: ShareParse.Fail? = null
        for (c in candidates) {
            when (val r = c()) {
                is ShareParse.Ok -> return r
                is ShareParse.Fail -> if (firstFail == null || firstFail.reason == ShareFailure.NOT_OVERFIT) firstFail = r
            }
        }
        return firstFail ?: ShareParse.Fail(ShareFailure.NOT_OVERFIT)
    }

    private val QR_REGEX = Regex("""OF1:[0-9A-Z \$%*+\-./:]+""")
    private val LINK_REGEX = Regex("""(?:https?://[^\s#]*/t|overfit://t/?)#([A-Za-z0-9_\-+/=]+)""")
    private val FENCE_REGEX = Regex("""```[A-Za-z]*\s*\n?(.*?)```""", RegexOption.DOT_MATCHES_ALL)

    /** String array (unknown entries are skipped), at most 10. */
    private fun JsonObject.strings(key: String): List<String> =
        (this[key] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content?.trim()?.takeIf { s -> s.isNotEmpty() } }
            .distinct().take(10)

    private fun JsonObject.str(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull

    /** Numbers, numeric text and the first number in text like "8-12" or "60 kg". */
    private fun JsonObject.num(key: String): Double? {
        val p = this[key] as? JsonPrimitive ?: return null
        if (p is JsonNull) return null
        p.doubleOrNull?.let { return it.takeIf { v -> v.isFinite() } }
        val m = NUMBER_REGEX.find(p.contentOrNull ?: return null) ?: return null
        return m.value.replace(',', '.').toDoubleOrNull()
    }

    private val NUMBER_REGEX = Regex("""-?\d+(?:[.,]\d+)?""")
}

data class SharedSet(val kg: Double? = null, val reps: Int? = null, val sec: Int? = null)

data class SharedExercise(
    val id: String? = null,
    val name: String? = null,
    val ref: String? = null,
    val sets: List<SharedSet> = emptyList(),
    val restSec: Int? = null,
    val note: String? = null,
)

/**
 * Own exercise of the sender. [muscle] is the old single-muscle field (still written as the first primary muscle so
 * older app versions keep reading it); [primary], [secondary], [restSec] and [note] were added in November 2026.
 */
data class SharedCustom(
    val ref: String,
    val name: String,
    val trackingType: String = "WEIGHT_REPS",
    val muscle: String? = null,
    val equipment: String? = null,
    val primary: List<String> = listOfNotNull(muscle),
    val secondary: List<String> = emptyList(),
    val restSec: Int? = null,
    val note: String? = null,
)

data class SharedTraining(
    val v: Int = ShareFormat.VERSION,
    val kind: String = ShareFormat.KIND_TEMPLATE,
    val name: String,
    val note: String? = null,
    val exercises: List<SharedExercise>,
    val custom: List<SharedCustom> = emptyList(),
)

enum class ShareFailure { EMPTY, NOT_OVERFIT, TOO_NEW, UNSUPPORTED_KIND, INVALID, TOO_LARGE }

sealed interface ShareParse {
    data class Ok(val training: SharedTraining) : ShareParse
    data class Fail(val reason: ShareFailure) : ShareParse
}
