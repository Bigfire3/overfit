package app.overfit.ai

import app.overfit.fuel.FuelException
import app.overfit.fuel.FuelMessageResult
import app.overfit.fuel.FuelService
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
enum class ProviderId { OPENROUTER, ANTHROPIC, OPENAI, FUEL, GEMINI }

enum class KeyValidation { OK, INVALID, NETWORK }

/**
 * Everything the app needs from an LLM backend. Implementations:
 * - direct providers with the user's own key (BYOK), below
 * - Fuel: credits proxy via our own backend ([FuelProvider])
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
    /** False for Fuel: no key of the user, the device identity pays from the Fuel balance. */
    val usesKey: Boolean get() = true
    /** Checks the key without spending tokens. */
    suspend fun validateKey(apiKey: String): KeyValidation
}

private suspend fun check(invalidOnBadRequest: Boolean = false, request: suspend () -> HttpResponse): KeyValidation = try {
    val status = request().status
    when {
        status.isSuccess() -> KeyValidation.OK
        status == HttpStatusCode.Unauthorized || status == HttpStatusCode.Forbidden -> KeyValidation.INVALID
        invalidOnBadRequest && status == HttpStatusCode.BadRequest -> KeyValidation.INVALID
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

/**
 * Google AI Studio key (Gemini API), via the OpenAI-compatible endpoint (see docs/DECISIONS.md). Google answers an
 * invalid key with 400 API_KEY_INVALID instead of 401, so 400 counts as invalid here.
 */
class GeminiProvider(private val http: HttpClient) : LlmProvider {
    override val id = ProviderId.GEMINI
    override val label = "Gemini"
    override val keyUrl = "https://aistudio.google.com/apikey"
    override val keyPlaceholder = "AIza…"
    override suspend fun validateKey(apiKey: String) = check(invalidOnBadRequest = true) {
        http.get("$GEMINI_OPENAI_BASE/models") { header(HttpHeaders.Authorization, "Bearer $apiKey") }
    }
}

/** OpenAI-compatible base URL of the Gemini API. */
const val GEMINI_OPENAI_BASE = "https://generativelanguage.googleapis.com/v1beta/openai"

/**
 * Fuel: coach credit through our own backend (POST /v1/messages) instead of the user's own key.
 * "Validating" means: the backend is configured and reachable with this device's identity. The coach (phase 4)
 * sends its requests with [messages].
 */
class FuelProvider(private val service: () -> FuelService?) : LlmProvider {
    override val id = ProviderId.FUEL
    override val label = "Fuel"
    override val keyUrl = ""
    override val keyPlaceholder = ""
    override val usesKey = false

    val available: Boolean get() = service() != null

    override suspend fun validateKey(apiKey: String): KeyValidation {
        val s = service() ?: return KeyValidation.NETWORK
        return try {
            s.account()
            KeyValidation.OK
        } catch (e: CancellationException) {
            throw e
        } catch (e: FuelException) {
            if (e.code == "network" || e.status >= 500) KeyValidation.NETWORK else KeyValidation.INVALID
        } catch (_: Exception) {
            KeyValidation.NETWORK
        }
    }

    /** Sends an Anthropic Messages request through Fuel; the result carries cost and new balance. */
    suspend fun messages(bodyJson: String): FuelMessageResult =
        (service() ?: throw FuelException("not_configured", 503)).messages(bodyJson)
}

class Providers(val http: HttpClient, fuelService: () -> FuelService? = { null }) {
    /** Providers that need the user's own key. */
    val all: List<LlmProvider> = listOf(OpenRouterProvider(http), AnthropicProvider(http), OpenAiProvider(http), GeminiProvider(http))
    val fuel = FuelProvider(fuelService)
    fun get(id: ProviderId): LlmProvider = if (id == ProviderId.FUEL) fuel else all.first { it.id == id }
}
