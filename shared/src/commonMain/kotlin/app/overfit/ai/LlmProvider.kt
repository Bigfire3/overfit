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
import app.overfit.data.KeyValueStore
import app.overfit.fuel.FuelConfig
import app.overfit.fuel.FuelTierInfo
import app.overfit.fuel.ModelNames
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeMark
import kotlin.time.TimeSource

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
class FuelProvider(
    /** Keeps the last loaded `/v1/config` (offline fallback, model names after a restart); null = memory only. */
    private val kv: KeyValueStore? = null,
    private val service: () -> FuelService?,
) : LlmProvider {
    override val id = ProviderId.FUEL
    override val label = "Guthaben"
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

    private var cached: Triple<FuelService, FuelConfig, TimeMark>? = null

    init {
        // Names of the models the server used last time, so old answers and bookings show them before it answers.
        stored()?.let { ModelNames.register(it) }
    }

    private fun stored(): FuelConfig? = kv?.get(CONFIG_KEY)?.let { raw ->
        runCatching { storeJson.decodeFromString(FuelConfig.serializer(), raw) }.getOrNull()
    }

    /**
     * `/v1/config` of the backend (cached for 10 minutes). When the server cannot be reached, the last config that
     * loaded (stored in [kv]) answers instead. Null = unknown (no backend, or never loaded). Demo configs are not stored.
     */
    suspend fun config(): FuelConfig? {
        val s = service() ?: return null
        cached?.let { (svc, cfg, mark) -> if (svc === s && mark.elapsedNow() < 10.minutes) return cfg }
        return try {
            s.config().also { cfg ->
                cached = Triple(s, cfg, TimeSource.Monotonic.markNow())
                ModelNames.register(cfg)
                if (!s.isDemo) kv?.put(CONFIG_KEY, storeJson.encodeToString(FuelConfig.serializer(), cfg))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            if (s.isDemo) null else stored()
        }
    }

    /** Forgets the cached config, e.g. after the backend said a tier is not available (keys changed on the server). */
    fun invalidateConfig() {
        cached = null
    }

    /** "Delete all data": also drops the stored config. */
    fun resetAll() {
        cached = null
        kv?.remove(CONFIG_KEY)
    }

    /** Current credit in µ€ (null = unknown). */
    suspend fun balance(): Long? {
        val s = service() ?: return null
        return try {
            s.account().balanceMicro
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    /** Models of the backend (name, provider, price, availability). Null = unknown; empty = old backend without tiers. */
    suspend fun tiers(): Map<String, FuelTierInfo>? = config()?.tiers

    /**
     * The `model` value to send for [wanted] (standard | pro | lite): the wanted tier when the backend offers it,
     * otherwise the next one (lite → standard → pro, pro ↔ standard). Old backend: its only model.
     */
    suspend fun resolveTier(wanted: String): String {
        val tiers = tiers() ?: return wanted
        if (tiers.isEmpty()) return LEGACY_MODEL
        val order = when (wanted) {
            TIER_LITE -> listOf(TIER_LITE, TIER_STANDARD)
            TIER_PRO -> listOf(TIER_PRO, TIER_STANDARD, TIER_LITE)
            // Never silently up to the expensive model.
            else -> listOf(TIER_STANDARD, TIER_LITE)
        }
        return order.firstOrNull { tiers[it]?.available == true } ?: wanted
    }

    /** Highest `max_tokens` the backend accepts (`/v1/config`; the test server allows fewer than production). Null = unknown. */
    suspend fun maxTokensCap(): Int? = config()?.maxTokensCap?.takeIf { it > 0 }

    /** Sends an Anthropic Messages request through Fuel; the result carries cost and new balance. */
    suspend fun messages(bodyJson: String): FuelMessageResult =
        (service() ?: throw FuelException("not_configured", 503)).messages(bodyJson).also { ModelNames.register(it) }

    companion object {
        /** Last loaded `/v1/config` as JSON (not personal; removed by "delete all data"). */
        const val CONFIG_KEY = "overfit.fuel.config.v1"
        private val storeJson = Json { ignoreUnknownKeys = true }
    }
}

/** Fuel tiers (backend/src/providers.ts). */
const val TIER_STANDARD = "standard"
const val TIER_PRO = "pro"
const val TIER_LITE = "lite"
/** The only model of backends before the tiers. */
private const val LEGACY_MODEL = "claude-sonnet-5-5"

class Providers(
    val http: HttpClient,
    /** Where the credit config is kept for offline use; null = memory only (tests). */
    kv: KeyValueStore? = null,
    fuelService: () -> FuelService? = { null },
) {
    /** Providers that need the user's own key. */
    val all: List<LlmProvider> = listOf(OpenRouterProvider(http), AnthropicProvider(http), OpenAiProvider(http), GeminiProvider(http))
    val fuel = FuelProvider(kv, fuelService)
    fun get(id: ProviderId): LlmProvider = if (id == ProviderId.FUEL) fuel else all.first { it.id == id }
}
