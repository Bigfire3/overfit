package app.overfit.coach

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * JSON schema of the coach answer, used for structured outputs (Anthropic `output_config.format`, OpenAI/OpenRouter
 * `response_format`). Only the subset both providers accept in strict mode: every object lists all properties as
 * required and has `additionalProperties: false`; optional values are `anyOf [type, null]`; no numeric limits
 * (ranges are checked by [CoachParser]).
 */
object CoachSchema {
    const val NAME = "coach_review"

    private fun type(t: String) = buildJsonObject { put("type", t) }
    private fun nullable(t: String) = buildJsonObject {
        putJsonArray("anyOf") { add(type(t)); add(type("null")) }
    }
    private fun nullable(schema: JsonObject) = buildJsonObject {
        putJsonArray("anyOf") { add(schema); add(type("null")) }
    }
    private fun stringArray() = buildJsonObject { put("type", "array"); put("items", type("string")) }

    private fun obj(vararg props: Pair<String, JsonElement>) = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") { props.forEach { (k, v) -> put(k, v) } }
        put("required", JsonArray(props.map { JsonPrimitive(it.first) }))
        put("additionalProperties", false)
    }

    private val set = obj(
        "weight_kg" to nullable("number"),
        "reps" to nullable("integer"),
        "duration_sec" to nullable("integer"),
    )

    private val exercise = obj(
        "exercise_id" to type("string"),
        "is_new" to type("boolean"),
        "rest_sec" to nullable("integer"),
        "sets" to buildJsonObject { put("type", "array"); put("items", set) },
        "reason" to nullable("string"),
    )

    private val exerciseReview = obj(
        "exercise_id" to type("string"),
        "verdict" to buildJsonObject {
            put("type", "string")
            putJsonArray("enum") { add(JsonPrimitive("better")); add(JsonPrimitive("same")); add(JsonPrimitive("worse")) }
        },
        "comment" to type("string"),
        "next" to nullable(set),
    )

    private val nextSession = obj(
        "exercises" to buildJsonObject { put("type", "array"); put("items", exercise) },
        "note" to nullable("string"),
    )

    val schema: JsonObject = obj(
        "rating" to type("integer"),
        "summary" to type("string"),
        "exercise_reviews" to buildJsonObject { put("type", "array"); put("items", exerciseReview) },
        "positives" to stringArray(),
        "improvements" to stringArray(),
        "tips" to stringArray(),
        "photo_feedback" to nullable("string"),
        "next_session" to nullable(nextSession),
        "memory" to type("string"),
    )

    /** Example answer used in the prompt so models without structured outputs still know the shape. */
    val example: String = """{"rating":7,"summary":"…","exercise_reviews":[{"exercise_id":"wg:bench-press","verdict":"better",""" +
        """"comment":"…","next":{"weight_kg":82.5,"reps":8,"duration_sec":null}}],"positives":["…"],"improvements":["…"],"tips":["…"],""" +
        """"photo_feedback":null,"next_session":{"exercises":[{"exercise_id":"wg:bench-press","is_new":false,""" +
        """"rest_sec":120,"sets":[{"weight_kg":82.5,"reps":8,"duration_sec":null}],"reason":"…"}],"note":null},""" +
        """"memory":"…"}"""
}
