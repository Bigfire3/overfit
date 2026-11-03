package app.overfit.coach

import app.overfit.ai.FuelProvider
import app.overfit.ai.ProviderId
import app.overfit.fuel.FuelException
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/** Raw answer of a provider: the text that should contain the JSON, plus what it cost (Fuel only). */
data class CoachReply(
    val text: String,
    /** Complete response body, stored with the review. */
    val raw: String,
    val costMicro: Long? = null,
    val balanceMicro: Long? = null,
    /** Output was cut off (max tokens). */
    val truncated: Boolean = false,
    /** The model declined (safety). */
    val refused: Boolean = false,
)

enum class CoachErrorKind(val retryable: Boolean) {
    NETWORK(true), TIMEOUT(true), RATE_LIMIT(true), SERVER(true),
    NO_ACCESS(false), AUTH(false), NO_FUEL(false), BAD_REQUEST(false), INVALID_ANSWER(false),
}

class CoachException(val kind: CoachErrorKind, message: String? = null) : Exception(message ?: kind.name)

/** Sends a [CoachPrompt] to one provider. */
interface CoachTransport {
    val providerId: ProviderId
    suspend fun send(prompt: CoachPrompt, model: String, structured: Boolean = true): CoachReply
}

object CoachModels {
    const val ANTHROPIC = "claude-sonnet-5-5"
    const val OPENAI = "gpt-5"
    const val OPENROUTER = "anthropic/claude-sonnet-5.5"
    /** The Fuel backend only accepts this model (see backend/src/config.ts). */
    const val FUEL = "claude-sonnet-5-5"
    const val MAX_TOKENS = 8_000

    fun default(id: ProviderId) = when (id) {
        ProviderId.ANTHROPIC -> ANTHROPIC
        ProviderId.OPENAI -> OPENAI
        ProviderId.OPENROUTER -> OPENROUTER
        ProviderId.FUEL -> FUEL
    }
}

private val json = Json { ignoreUnknownKeys = true }

@OptIn(ExperimentalEncodingApi::class)
private fun ByteArray.base64() = Base64.encode(this)

/**
 * Anthropic Messages body (also sent through Fuel). The static system block carries `cache_control`, so rules and
 * exercise list are cached; profile block and workout follow after the break. Structured outputs
 * (`output_config.format`) guarantee the JSON shape without forcing a tool (forced tool_choice is rejected by the
 * Claude 5.5 models).
 */
fun anthropicBody(prompt: CoachPrompt, model: String, structured: Boolean): JsonObject = buildJsonObject {
    put("model", model)
    put("max_tokens", CoachModels.MAX_TOKENS)
    putJsonArray("system") {
        add(buildJsonObject {
            put("type", "text"); put("text", prompt.staticSystem)
            putJsonObject("cache_control") { put("type", "ephemeral") }
        })
        add(buildJsonObject { put("type", "text"); put("text", prompt.dynamicSystem) })
    }
    putJsonArray("messages") {
        add(buildJsonObject {
            put("role", "user")
            putJsonArray("content") {
                prompt.images.forEach { img ->
                    add(buildJsonObject { put("type", "text"); put("text", "Photo: ${img.label}") })
                    add(buildJsonObject {
                        put("type", "image")
                        putJsonObject("source") {
                            put("type", "base64"); put("media_type", "image/jpeg"); put("data", img.jpeg.base64())
                        }
                    })
                }
                add(buildJsonObject { put("type", "text"); put("text", prompt.user) })
            }
        })
    }
    val effort = SUPPORTS_EFFORT.containsMatchIn(model)
    if (structured || effort) putJsonObject("output_config") {
        if (effort) put("effort", "medium")
        if (structured) putJsonObject("format") { put("type", "json_schema"); put("schema", CoachSchema.schema) }
    }
}

private val SUPPORTS_EFFORT = Regex("""claude-(opus|sonnet|haiku|fable)-5""")

/** Text of an Anthropic answer: text blocks joined, or the input of a tool_use block if the model used a tool. */
fun anthropicReply(body: JsonObject, raw: String): CoachReply {
    val content = body["content"] as? JsonArray ?: JsonArray(emptyList())
    val tool = content.firstOrNull { (it as? JsonObject)?.get("type")?.jsonPrimitive?.contentOrNull == "tool_use" } as? JsonObject
    val text = tool?.get("input")?.toString() ?: content.mapNotNull { block ->
        val o = block as? JsonObject ?: return@mapNotNull null
        if (o["type"]?.jsonPrimitive?.contentOrNull == "text") o["text"]?.jsonPrimitive?.contentOrNull else null
    }.joinToString("")
    val stop = body["stop_reason"]?.jsonPrimitive?.contentOrNull
    return CoachReply(text, raw, truncated = stop == "max_tokens", refused = stop == "refusal")
}

private fun statusError(status: Int, body: String): CoachException = CoachException(
    when {
        status == 401 || status == 403 -> CoachErrorKind.AUTH
        status == 402 -> CoachErrorKind.NO_FUEL
        status == 408 -> CoachErrorKind.TIMEOUT
        status == 429 -> CoachErrorKind.RATE_LIMIT
        status >= 500 -> CoachErrorKind.SERVER
        else -> CoachErrorKind.BAD_REQUEST
    },
    "HTTP $status: ${body.take(300)}",
)

/** Common HTTP handling: timeout, network errors → [CoachException]. */
private suspend fun postJson(http: HttpClient, url: String, body: JsonObject, headers: Map<String, String>): String {
    val response = try {
        http.post(url) {
            headers.forEach { (k, v) -> header(k, v) }
            contentType(ContentType.Application.Json)
            setBody(body.toString())
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: HttpRequestTimeoutException) {
        throw CoachException(CoachErrorKind.TIMEOUT, e.message)
    } catch (e: Exception) {
        throw CoachException(CoachErrorKind.NETWORK, e.message)
    }
    val text = try { response.bodyAsText() } catch (e: CancellationException) { throw e } catch (e: Exception) {
        throw CoachException(CoachErrorKind.NETWORK, e.message)
    }
    if (!response.status.isSuccess()) throw statusError(response.status.value, text)
    return text
}

private fun parseObject(text: String): JsonObject = runCatching { json.parseToJsonElement(text) as JsonObject }
    .getOrElse { throw CoachException(CoachErrorKind.SERVER, "Not JSON: ${text.take(200)}") }

private fun HttpClient.withTimeout() = config { install(HttpTimeout) { requestTimeoutMillis = TIMEOUT_MS; connectTimeoutMillis = 20_000 } }
private const val TIMEOUT_MS = 150_000L

class AnthropicCoach(http: HttpClient, private val apiKey: () -> String?) : CoachTransport {
    private val http = http.withTimeout()
    override val providerId = ProviderId.ANTHROPIC
    override suspend fun send(prompt: CoachPrompt, model: String, structured: Boolean): CoachReply {
        val key = apiKey() ?: throw CoachException(CoachErrorKind.NO_ACCESS)
        val raw = postJson(
            http, "https://api.anthropic.com/v1/messages", anthropicBody(prompt, model, structured),
            mapOf("x-api-key" to key, "anthropic-version" to "2023-06-01"),
        )
        return anthropicReply(parseObject(raw), raw)
    }
}

/**
 * OpenAI Chat Completions; OpenRouter speaks the same format. Structured outputs via `response_format` with the same
 * strict schema. OpenRouter passes `cache_control` on the system part on to Anthropic models; OpenAI caches long
 * prefixes automatically.
 */
open class ChatCompletionsCoach(
    http: HttpClient,
    private val apiKey: () -> String?,
    override val providerId: ProviderId,
    private val url: String,
) : CoachTransport {
    private val http = http.withTimeout()

    fun body(prompt: CoachPrompt, model: String, structured: Boolean): JsonObject = buildJsonObject {
        put("model", model)
        if (providerId == ProviderId.OPENAI) put("max_completion_tokens", CoachModels.MAX_TOKENS) else put("max_tokens", CoachModels.MAX_TOKENS)
        putJsonArray("messages") {
            add(buildJsonObject {
                put("role", "system")
                putJsonArray("content") {
                    add(buildJsonObject {
                        put("type", "text"); put("text", prompt.staticSystem)
                        if (providerId == ProviderId.OPENROUTER) putJsonObject("cache_control") { put("type", "ephemeral") }
                    })
                    add(buildJsonObject { put("type", "text"); put("text", prompt.dynamicSystem) })
                }
            })
            add(buildJsonObject {
                put("role", "user")
                putJsonArray("content") {
                    prompt.images.forEach { img ->
                        add(buildJsonObject { put("type", "text"); put("text", "Photo: ${img.label}") })
                        add(buildJsonObject {
                            put("type", "image_url")
                            putJsonObject("image_url") { put("url", "data:image/jpeg;base64,${img.jpeg.base64()}") }
                        })
                    }
                    add(buildJsonObject { put("type", "text"); put("text", prompt.user) })
                }
            })
        }
        if (structured) putJsonObject("response_format") {
            put("type", "json_schema")
            putJsonObject("json_schema") { put("name", CoachSchema.NAME); put("strict", true); put("schema", CoachSchema.schema) }
        }
    }

    override suspend fun send(prompt: CoachPrompt, model: String, structured: Boolean): CoachReply {
        val key = apiKey() ?: throw CoachException(CoachErrorKind.NO_ACCESS)
        val raw = postJson(http, url, body(prompt, model, structured), mapOf(HttpHeaders.Authorization to "Bearer $key"))
        return chatReply(parseObject(raw), raw)
    }

    companion object {
        fun chatReply(body: JsonObject, raw: String): CoachReply {
            // OpenRouter reports upstream errors with HTTP 200 and an "error" object.
            (body["error"] as? JsonObject)?.let { err ->
                val code = (err["code"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull() ?: 500
                throw statusError(code, err.toString())
            }
            val choice = (body["choices"] as? JsonArray)?.firstOrNull() as? JsonObject
                ?: throw CoachException(CoachErrorKind.SERVER, "No choices")
            val message = choice["message"] as? JsonObject
            val content = message?.get("content")
            val text = when (content) {
                is JsonPrimitive -> content.contentOrNull.orEmpty()
                is JsonArray -> content.mapNotNull { ((it as? JsonObject)?.get("text") as? JsonPrimitive)?.contentOrNull }.joinToString("")
                else -> ""
            }
            val refusal = (message?.get("refusal") as? JsonPrimitive)?.contentOrNull
            val finish = (choice["finish_reason"] as? JsonPrimitive)?.contentOrNull
            return CoachReply(text, raw, truncated = finish == "length", refused = !refusal.isNullOrBlank() && text.isBlank())
        }
    }
}

class OpenAiCoach(http: HttpClient, apiKey: () -> String?) :
    ChatCompletionsCoach(http, apiKey, ProviderId.OPENAI, "https://api.openai.com/v1/chat/completions")

class OpenRouterCoach(http: HttpClient, apiKey: () -> String?) :
    ChatCompletionsCoach(http, apiKey, ProviderId.OPENROUTER, "https://openrouter.ai/api/v1/chat/completions")

/** Fuel: the Anthropic body through our backend, which reports cost and the new balance. */
class FuelCoach(private val fuel: FuelProvider) : CoachTransport {
    override val providerId = ProviderId.FUEL
    override suspend fun send(prompt: CoachPrompt, model: String, structured: Boolean): CoachReply {
        if (!fuel.available) throw CoachException(CoachErrorKind.NO_ACCESS)
        val result = try {
            fuel.messages(anthropicBody(prompt, CoachModels.FUEL, structured).toString())
        } catch (e: CancellationException) {
            throw e
        } catch (e: FuelException) {
            throw CoachException(
                when {
                    e.code == "network" -> CoachErrorKind.NETWORK
                    e.code == "insufficient_funds" -> CoachErrorKind.NO_FUEL
                    e.code == "not_configured" -> CoachErrorKind.NO_ACCESS
                    e.code == "rate_limited" || e.status == 429 -> CoachErrorKind.RATE_LIMIT
                    e.status >= 500 || e.code == "upstream_unavailable" -> CoachErrorKind.SERVER
                    e.status == 401 || e.status == 403 -> CoachErrorKind.AUTH
                    else -> CoachErrorKind.BAD_REQUEST
                },
                e.message,
            )
        } catch (e: Exception) {
            throw CoachException(CoachErrorKind.NETWORK, e.message)
        }
        val body = result.body as? JsonObject ?: throw CoachException(CoachErrorKind.SERVER, "Not an object")
        return anthropicReply(body, body.toString()).copy(costMicro = result.costMicro, balanceMicro = result.balanceMicro)
    }
}

/** For tests and the demo: answers with a fixed text. */
class FakeCoachTransport(
    override val providerId: ProviderId = ProviderId.ANTHROPIC,
    private val answer: suspend (CoachPrompt, Boolean) -> CoachReply,
) : CoachTransport {
    val prompts = mutableListOf<CoachPrompt>()
    override suspend fun send(prompt: CoachPrompt, model: String, structured: Boolean): CoachReply {
        prompts += prompt
        return answer(prompt, structured)
    }
}
