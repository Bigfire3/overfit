package app.overfit.ai

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable

@Serializable
enum class ProviderId { OPENROUTER, ANTHROPIC, OPENAI }

enum class KeyValidation { OK, INVALID, NETWORK }

/**
 * Everything the app needs from an LLM backend. Implementations:
 * - direct providers with the user's own key (BYOK), below
 * - later: a credits proxy via our own backend (issue #13)
 *
 * Kotlin Multiplatform has no official Anthropic SDK for iOS, so all providers use plain HTTP via Ktor.
 */
interface LlmProvider {
    val id: ProviderId
    val label: String
    /** Where users create a key. */
    val keyUrl: String
    /** Example prefix shown as input placeholder. */
    val keyPlaceholder: String
    /** Checks the key without spending tokens. */
    suspend fun validateKey(apiKey: String): KeyValidation
}

private suspend fun check(request: suspend () -> HttpResponse): KeyValidation = try {
    val status = request().status
    when {
        status.isSuccess() -> KeyValidation.OK
        status == HttpStatusCode.Unauthorized || status == HttpStatusCode.Forbidden -> KeyValidation.INVALID
        else -> KeyValidation.NETWORK
    }
} catch (e: CancellationException) {
    throw e
} catch (_: Exception) {
    KeyValidation.NETWORK
}

class OpenRouterProvider(private val http: HttpClient) : LlmProvider {
    override val id = ProviderId.OPENROUTER
    override val label = "OpenRouter"
    override val keyUrl = "https://openrouter.ai/settings/keys"
    override val keyPlaceholder = "sk-or-…"
    override suspend fun validateKey(apiKey: String) = check {
        http.get("https://openrouter.ai/api/v1/key") { header(HttpHeaders.Authorization, "Bearer $apiKey") }
    }
}

class AnthropicProvider(private val http: HttpClient) : LlmProvider {
    override val id = ProviderId.ANTHROPIC
    override val label = "Anthropic"
    override val keyUrl = "https://platform.claude.com/settings/keys"
    override val keyPlaceholder = "sk-ant-…"
    override suspend fun validateKey(apiKey: String) = check {
        http.get("https://api.anthropic.com/v1/models") {
            header("x-api-key", apiKey)
            header("anthropic-version", "2023-06-01")
            parameter("limit", 1)
        }
    }
}

class OpenAiProvider(private val http: HttpClient) : LlmProvider {
    override val id = ProviderId.OPENAI
    override val label = "OpenAI"
    override val keyUrl = "https://platform.openai.com/api-keys"
    override val keyPlaceholder = "sk-…"
    override suspend fun validateKey(apiKey: String) = check {
        http.get("https://api.openai.com/v1/models") { header(HttpHeaders.Authorization, "Bearer $apiKey") }
    }
}

class Providers(http: HttpClient) {
    val all: List<LlmProvider> = listOf(OpenRouterProvider(http), AnthropicProvider(http), OpenAiProvider(http))
    fun get(id: ProviderId): LlmProvider = all.first { it.id == id }
}
