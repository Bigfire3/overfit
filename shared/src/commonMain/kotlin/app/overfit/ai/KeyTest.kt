package app.overfit.ai

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** Result of "Test" in the model sheet (own key). */
sealed interface KeyTestResult {
    /** The key works; [models] are the provider's chat models (suggestions for the model field). */
    data class Ok(val models: List<String>) : KeyTestResult
    /** The provider rejected the key (401/403; Google: 400 API_KEY_INVALID). */
    data object Invalid : KeyTestResult
    /** The key is valid but has no access / no credit left at the provider (402, 403 with billing hint). */
    data object NoCredit : KeyTestResult
    data object RateLimited : KeyTestResult
    /** No connection or timeout. */
    data object Network : KeyTestResult
    /** Anything else (5xx, unexpected answer). */
    data class Failed(val status: Int) : KeyTestResult
    /** Nothing entered. */
    data object Empty : KeyTestResult
}

/**
 * Tests an own API key for free by listing the provider's models, straight from the device (never through the Overfit
 * backend, so OwnKeyPrivacyTest stays green):
 * - Anthropic `GET /v1/models`, OpenAI `GET /v1/models`, Google `models.list` (`GET /v1beta/models`),
 * - OpenRouter: the model list is public, so the key itself is checked with `GET /api/v1/key` first, then the list is
 *   fetched from `GET /api/v1/models`.
 * Listing models costs nothing at any of these providers.
 */
class KeyTester(private val http: HttpClient) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun test(provider: ProviderId, apiKey: String): KeyTestResult {
        val key = apiKey.trim()
        if (key.isEmpty()) return KeyTestResult.Empty
        return try {
            when (provider) {
                ProviderId.ANTHROPIC -> list(
                    http.get("https://api.anthropic.com/v1/models") {
                        header("x-api-key", key)
                        header("anthropic-version", "2023-06-01")
                        parameter("limit", 1000)
                    },
                ) { root -> ids(root, "data", "id") }
                ProviderId.OPENAI -> list(
                    http.get("https://api.openai.com/v1/models") { header(HttpHeaders.Authorization, "Bearer $key") },
                ) { root -> ids(root, "data", "id").filter(::isOpenAiChatModel) }
                ProviderId.GEMINI -> list(
                    http.get("https://generativelanguage.googleapis.com/v1beta/models") {
                        header("x-goog-api-key", key)
                        parameter("pageSize", 1000)
                    },
                    invalidOnBadRequest = true,
                ) { root -> geminiModels(root) }
                ProviderId.OPENROUTER -> {
                    val check = http.get("https://openrouter.ai/api/v1/key") { header(HttpHeaders.Authorization, "Bearer $key") }
                    if (!check.status.isSuccess()) return failure(check.status)
                    list(http.get("https://openrouter.ai/api/v1/models") { header(HttpHeaders.Authorization, "Bearer $key") }) { root ->
                        ids(root, "data", "id")
                    }
                }
                ProviderId.FUEL -> KeyTestResult.Failed(0)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            KeyTestResult.Network
        }
    }

    private suspend fun list(
        response: HttpResponse,
        invalidOnBadRequest: Boolean = false,
        parse: (JsonObject) -> List<String>,
    ): KeyTestResult {
        if (!response.status.isSuccess()) return failure(response.status, invalidOnBadRequest)
        val root = runCatching { json.parseToJsonElement(response.bodyAsText()) as? JsonObject }.getOrNull()
            ?: return KeyTestResult.Failed(response.status.value)
        return KeyTestResult.Ok(parse(root).distinct().sortedWith(modelOrder))
    }

    private fun failure(status: HttpStatusCode, invalidOnBadRequest: Boolean = false): KeyTestResult = when {
        status == HttpStatusCode.Unauthorized || status == HttpStatusCode.Forbidden -> KeyTestResult.Invalid
        invalidOnBadRequest && status == HttpStatusCode.BadRequest -> KeyTestResult.Invalid
        status == HttpStatusCode.PaymentRequired -> KeyTestResult.NoCredit
        status == HttpStatusCode.TooManyRequests -> KeyTestResult.RateLimited
        else -> KeyTestResult.Failed(status.value)
    }

    private fun ids(root: JsonObject, array: String, field: String): List<String> =
        (root[array] as? JsonArray).orEmpty().mapNotNull { ((it as? JsonObject)?.get(field) as? JsonPrimitive)?.contentOrNull }

    /** Google lists embedding and image models too: only models that can generate text, without the "models/" prefix. */
    private fun geminiModels(root: JsonObject): List<String> =
        (root["models"] as? JsonArray).orEmpty().mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            val methods = (o["supportedGenerationMethods"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            if (methods.isNotEmpty() && "generateContent" !in methods) return@mapNotNull null
            (o["name"] as? JsonPrimitive)?.contentOrNull?.removePrefix("models/")
        }

    companion object {
        /** OpenAI lists audio, image, embedding and moderation models too; only chat models are useful here. */
        fun isOpenAiChatModel(id: String): Boolean {
            val chat = id.startsWith("gpt-") || id.startsWith("chatgpt-") || Regex("^o\\d").containsMatchIn(id)
            val other = listOf("audio", "realtime", "transcribe", "tts", "image", "embedding", "search", "moderation", "instruct")
            return chat && other.none { it in id }
        }

        /** Newest-looking first: longer version numbers sort later alphabetically, so a descending sort works well. */
        private val modelOrder = compareByDescending<String> { it }
    }
}
