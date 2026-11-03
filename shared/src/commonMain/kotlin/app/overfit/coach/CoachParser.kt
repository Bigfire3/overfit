package app.overfit.coach

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import kotlin.math.roundToInt

sealed interface ParseResult {
    data class Ok(val review: CoachReview) : ParseResult
    data class Invalid(val reason: String) : ParseResult
}

/**
 * Turns the model's answer text into a [CoachReview]. Structured outputs should deliver exact JSON, but the parser
 * does not rely on it: it also accepts JSON inside ```json fences or prose, camelCase keys, numbers as strings
 * ("82,5", "8/10"), single strings instead of lists, a wrapping object and trailing commas. Values out of range are
 * clamped or dropped instead of failing the whole review. Only an answer without any usable content is invalid.
 */
object CoachParser {
    @OptIn(ExperimentalSerializationApi::class)
    private val json = Json { isLenient = true; allowTrailingComma = true; allowComments = true }

    fun parse(text: String): ParseResult {
        val candidate = extractJsonObject(text) ?: return ParseResult.Invalid("no_json")
        val root = runCatching { json.parseToJsonElement(candidate) }.getOrNull() as? JsonObject
            ?: return ParseResult.Invalid("bad_json")
        val obj = unwrap(root)
        val review = CoachReview(
            rating = obj.field("rating")?.number()?.roundToInt()?.coerceIn(1, 10),
            summary = obj.field("summary")?.text().orEmpty(),
            positives = obj.field("positives")?.texts().orEmpty(),
            improvements = obj.field("improvements")?.texts().orEmpty(),
            tips = obj.field("tips")?.texts().orEmpty(),
            photoFeedback = obj.field("photo_feedback")?.text()?.takeIf { it.isNotBlank() },
            nextSession = (obj.field("next_session") as? JsonObject)?.let(::plan),
            memory = obj.field("memory")?.text()?.takeIf { it.isNotBlank() },
            exerciseReviews = (obj.field("exercise_reviews") as? JsonArray).orEmpty()
                .mapNotNull { (it as? JsonObject)?.let(::exerciseReview) }
                .distinctBy { it.exerciseId },
        )
        val empty = review.summary.isBlank() && review.tips.isEmpty() && review.exerciseReviews.isEmpty() && review.positives.isEmpty() &&
            review.improvements.isEmpty() && review.nextSession == null
        return if (empty) ParseResult.Invalid("empty") else ParseResult.Ok(review)
    }

    private fun plan(obj: JsonObject): PlanSuggestion? {
        val list = obj.field("exercises") as? JsonArray ?: return null
        val exercises = list.mapNotNull { (it as? JsonObject)?.let(::exercise) }
        if (exercises.isEmpty()) return null
        return PlanSuggestion(exercises, obj.field("note")?.text()?.takeIf { it.isNotBlank() })
    }

    private fun exerciseReview(obj: JsonObject): ExerciseFeedback? {
        val id = (obj.field("exercise_id") ?: obj.field("id"))?.text()?.trim().orEmpty()
        if (id.isEmpty()) return null
        val verdict = when (obj.field("verdict")?.text()?.lowercase()) {
            "better", "besser", "improved", "up" -> Verdict.BETTER
            "same", "gleich", "equal", "unchanged" -> Verdict.SAME
            "worse", "schlechter", "down" -> Verdict.WORSE
            else -> null
        }
        val next = (obj.field("next") as? JsonObject)?.let(::set)
        return ExerciseFeedback(id, verdict, obj.field("comment")?.text()?.takeIf { it.isNotBlank() }, next)
    }

    private fun set(o: JsonObject): SuggestedSet? = SuggestedSet(
        weightKg = o.field("weight_kg")?.number()?.let { (it.coerceAtLeast(0.0) * 100).roundToInt() / 100.0 }, // weights are never negative
        reps = o.field("reps")?.number()?.roundToInt()?.takeIf { it in 0..1000 },
        durationSec = o.field("duration_sec")?.number()?.roundToInt()?.takeIf { it in 0..86_400 },
    ).takeUnless { it.weightKg == null && it.reps == null && it.durationSec == null }

    private fun exercise(obj: JsonObject): SuggestedExercise? {
        val id = (obj.field("exercise_id") ?: obj.field("id"))?.text()?.trim().orEmpty()
        if (id.isEmpty()) return null
        val sets = (obj.field("sets") as? JsonArray).orEmpty().mapNotNull { s ->
            (s as? JsonObject)?.let(::set)
        }.take(MAX_SETS)
        return SuggestedExercise(
            exerciseId = id,
            isNew = obj.field("is_new")?.bool() ?: false,
            restSec = obj.field("rest_sec")?.number()?.roundToInt()?.takeIf { it in 0..3_600 },
            sets = sets,
            reason = obj.field("reason")?.text()?.takeIf { it.isNotBlank() },
        )
    }

    /** `{"coach_review": {...}}` or `{"input": {...}}` → the inner object. */
    private fun unwrap(root: JsonObject): JsonObject {
        if (root.field("summary") != null || root.field("rating") != null) return root
        val inner = root.values.singleOrNull() as? JsonObject
        return if (inner != null) unwrap(inner) else root
    }

    /** Finds `key` as snake_case or camelCase. */
    private fun JsonObject.field(key: String): JsonElement? {
        this[key]?.let { return it.takeUnless { e -> e is JsonNull } }
        val camel = key.split('_').mapIndexed { i, p -> if (i == 0) p else p.replaceFirstChar(Char::uppercase) }.joinToString("")
        return this[camel]?.takeUnless { it is JsonNull }
    }

    private fun JsonElement.text(): String? = when (this) {
        is JsonPrimitive -> content.trim()
        is JsonArray -> texts().joinToString("\n").ifEmpty { null }
        else -> null
    }

    private fun JsonElement.texts(): List<String> = when (this) {
        is JsonArray -> mapNotNull { (it as? JsonPrimitive)?.content?.trim()?.takeIf(String::isNotEmpty) }
        is JsonPrimitive -> listOfNotNull(content.trim().takeIf { it.isNotEmpty() && this !is JsonNull })
        else -> emptyList()
    }

    private fun JsonElement.number(): Double? {
        val p = this as? JsonPrimitive ?: return null
        if (!p.isString) return p.doubleOrNull ?: p.longOrNull?.toDouble()
        val m = NUMBER.find(p.content) ?: return null
        return m.value.replace(',', '.').toDoubleOrNull()
    }

    private fun JsonElement.bool(): Boolean? {
        val p = this as? JsonPrimitive ?: return null
        return p.booleanOrNull ?: when (p.content.lowercase()) { "yes", "ja", "1" -> true; "no", "nein", "0" -> false; else -> null }
    }

    private val NUMBER = Regex("""-?\d+(?:[.,]\d+)?""")
    private const val MAX_SETS = 20

    /**
     * The first balanced `{...}` in [text] (string-aware), preferring a ```json fence. Null if there is none.
     */
    fun extractJsonObject(text: String): String? {
        val fence = Regex("```(?:json)?\\s*([\\s\\S]*?)```").find(text)?.groupValues?.get(1)
        if (fence != null) balanced(fence)?.let { return it }
        return balanced(text)
    }

    private fun balanced(text: String): String? {
        var start = text.indexOf('{')
        while (start >= 0) {
            var depth = 0
            var inString = false
            var escape = false
            for (i in start until text.length) {
                val c = text[i]
                if (inString) {
                    when {
                        escape -> escape = false
                        c == '\\' -> escape = true
                        c == '"' -> inString = false
                    }
                    continue
                }
                when (c) {
                    '"' -> inString = true
                    '{' -> depth++
                    '}' -> {
                        depth--
                        if (depth == 0) return text.substring(start, i + 1)
                    }
                }
            }
            // Unbalanced (e.g. cut off): no object.
            start = -1
        }
        return null
    }
}
