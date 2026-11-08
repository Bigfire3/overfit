package app.overfit.ai

import app.overfit.coach.CoachErrorKind
import app.overfit.coach.CoachException
import app.overfit.coach.CoachPrompt
import app.overfit.coach.CoachReply
import app.overfit.domain.Equipment
import app.overfit.domain.Language
import app.overfit.domain.Muscle
import app.overfit.domain.TrackingType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlin.math.roundToInt

/**
 * One request to the AI the user set up (own key or Fuel), with the provider and model of the coach.
 * Implemented by [app.overfit.coach.CoachService.ai]; tests use a fake.
 */
interface AiGateway {
    fun available(): Boolean
    /** [light]: a simple task; with Fuel it goes to the cheap tier `lite` (own keys: unchanged). */
    suspend fun send(prompt: CoachPrompt, structured: Boolean, light: Boolean = false): CoachReply
}

/** What the AI suggests for a new exercise. Everything stays editable in the form. */
data class ExerciseSuggestion(
    val trackingType: TrackingType,
    val primary: List<Muscle>,
    val secondary: List<Muscle>,
    val equipment: Equipment?,
    val restSec: Int?,
    val note: String?,
)

/**
 * "Fill in with AI": the user only types the name, the AI answers with structured output (tracking type, muscles,
 * equipment, rest, short execution note). Only the app's muscle and equipment keys are allowed (schema enums), and
 * the parser drops anything else.
 */
class ExerciseAutofill(private val gateway: AiGateway) {

    val available: Boolean get() = gateway.available()

    /**
     * Throws [CoachException] (no access, network, no Fuel, invalid answer …). A 400 with structured output is
     * repeated once without it (some OpenRouter models do not support it); an answer that does not parse is asked
     * again once.
     */
    suspend fun fill(name: String, language: Language): ExerciseSuggestion {
        val clean = name.trim()
        require(clean.isNotEmpty()) { "Name is empty" }
        var structured = true
        repeat(3) { attempt ->
            val reply = try {
                gateway.send(ExerciseAutofillPrompt.build(clean, language, structured), structured, light = true)
            } catch (e: CoachException) {
                if (e.retryWithoutStructured && structured && attempt == 0) {
                    structured = false
                    return@repeat
                }
                throw e
            }
            if (!reply.refused && !reply.truncated) ExerciseAutofillParser.parse(reply.text)?.let { return it }
            if (attempt >= 1) throw CoachException(CoachErrorKind.INVALID_ANSWER, reply.text.take(200))
        }
        throw CoachException(CoachErrorKind.INVALID_ANSWER)
    }
}

object ExerciseAutofillSchema {
    const val NAME = "overfit_exercise"
    const val MAX_TOKENS = 1_000

    val schema: JsonObject = buildJsonObject {
        put("type", "object")
        put("additionalProperties", false)
        putJsonArray("required") {
            listOf("trackingType", "primaryMuscles", "secondaryMuscles", "equipment", "restSec", "note").forEach { add(JsonPrimitive(it)) }
        }
        putJsonObject("properties") {
            putJsonObject("trackingType") { put("type", "string"); enum(TrackingType.entries.map { it.name }) }
            putJsonObject("primaryMuscles") { put("type", "array"); putJsonObject("items") { put("type", "string"); enum(Muscle.entries.map { it.key }) } }
            putJsonObject("secondaryMuscles") { put("type", "array"); putJsonObject("items") { put("type", "string"); enum(Muscle.entries.map { it.key }) } }
            putJsonObject("equipment") { put("type", "string"); enum(Equipment.entries.map { it.key }) }
            putJsonObject("restSec") { put("type", "integer") }
            putJsonObject("note") { put("type", "string") }
        }
    }

    private fun kotlinx.serialization.json.JsonObjectBuilder.enum(values: List<String>) =
        putJsonArray("enum") { values.forEach { add(JsonPrimitive(it)) } }
}

object ExerciseAutofillPrompt {
    private val SYSTEM = buildString {
        appendLine("You complete exercise entries for the strength-training app Overfit. The user only gives the name of an")
        appendLine("exercise (German or English). Fill in:")
        appendLine("- trackingType: WEIGHT_REPS (external load and reps: barbell, dumbbell, machine, cable, kettlebell, also weighted")
        appendLine("  or assisted bodyweight moves), REPS (bodyweight reps), TIME (holds, stretches, cardio by duration),")
        appendLine("  WEIGHT_TIME (loaded holds or carries).")
        appendLine("- primaryMuscles: 1 to 2 keys, the main movers.")
        appendLine("- secondaryMuscles: 0 to 3 keys, never repeating a primary muscle.")
        appendLine("- equipment: exactly one key, the main equipment.")
        appendLine("- restSec: typical rest between sets in seconds (heavy compound 120-180, other compound 90-120, isolation 60-90,")
        appendLine("  core and holds 45-60).")
        appendLine("- note: one or two short sentences with the most important execution cues, at most 200 characters, no")
        appendLine("  safety disclaimers, no markdown.")
        appendLine("Use only these keys.")
        appendLine("Muscle keys: " + Muscle.entries.joinToString(", ") { "${it.key} (${it.en})" } + ".")
        appendLine("Equipment keys: " + Equipment.entries.joinToString(", ") { "${it.key} (${it.en})" } + ".")
        append("If the name is unusual, make the best guess from its words.")
    }

    fun build(name: String, language: Language, structured: Boolean): CoachPrompt = CoachPrompt(
        staticSystem = SYSTEM,
        dynamicSystem = "Write the note in ${if (language == Language.DE) "German" else "English"}." +
            if (structured) "" else " Answer with only a JSON object with the fields trackingType, primaryMuscles, secondaryMuscles, equipment, restSec and note.",
        user = "Exercise name: ${name.take(80)}",
        schema = ExerciseAutofillSchema.schema,
        schemaName = ExerciseAutofillSchema.NAME,
        maxTokens = ExerciseAutofillSchema.MAX_TOKENS,
        purpose = app.overfit.coach.AiPurpose.AUTOFILL,
    )
}

object ExerciseAutofillParser {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** Null if the text holds no usable answer (no JSON object or no valid tracking type). */
    fun parse(text: String): ExerciseSuggestion? {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        val o = runCatching { json.parseToJsonElement(text.substring(start, end + 1)) as? JsonObject }.getOrNull() ?: return null
        val tracking = o.str("trackingType")?.let { s -> TrackingType.entries.firstOrNull { it.name.equals(s.trim(), ignoreCase = true) } }
            ?: return null
        val primary = o.muscles("primaryMuscles").take(3)
        val secondary = o.muscles("secondaryMuscles").filter { it !in primary }.take(4)
        val equipment = o.str("equipment")?.let { Equipment.fromKey(it.trim().lowercase()) }
        val rest = (o["restSec"] as? JsonPrimitive)?.let { it.intOrNull ?: it.contentOrNull?.toDoubleOrNull()?.roundToInt() }
            ?.coerceIn(15, 600)?.let { ((it + 7) / 15) * 15 }
        val note = o.str("note")?.trim()?.replace(Regex("\\s+"), " ")?.take(300)?.takeIf { it.isNotEmpty() }
        return ExerciseSuggestion(tracking, primary, secondary, equipment, rest, note)
    }

    private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

    private fun JsonObject.muscles(key: String): List<Muscle> =
        (this[key] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.trim()?.lowercase()?.let(Muscle::fromKey) }.distinct()
}
