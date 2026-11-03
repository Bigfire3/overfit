package app.overfit.data

import app.overfit.ai.ProviderId
import app.overfit.domain.Language
import app.overfit.domain.Profile
import app.overfit.domain.ThemeMode
import app.overfit.domain.WeightEntry
import app.overfit.domain.WeightUnit
import app.overfit.domain.upsertWeight
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class Settings(
    val language: Language,
    val theme: ThemeMode = ThemeMode.DARK,
)

/** Everything except the API key, which lives in [SecretStore]. */
@Serializable
data class PersistedState(
    val version: Int = 1,
    val profile: Profile,
    val weights: List<WeightEntry> = emptyList(),
    val settings: Settings,
    val providerId: ProviderId = ProviderId.OPENROUTER,
    val onboardingDone: Boolean = false,
)

data class AppState(
    val persisted: PersistedState,
    val hasApiKey: Boolean,
) {
    val profile get() = persisted.profile
    val weights get() = persisted.weights
    val settings get() = persisted.settings
    val language get() = persisted.settings.language
    val providerId get() = persisted.providerId
    val onboardingDone get() = persisted.onboardingDone
}

/** Defaults derived from the device. */
data class DeviceDefaults(val language: Language, val unit: WeightUnit)

/**
 * Single source of truth for profile and settings. Persists as JSON after every change.
 * Profile data is small; workout data will get its own database (Room) later.
 */
class AppStore(
    private val kv: KeyValueStore,
    private val secrets: SecretStore,
    private val defaults: DeviceDefaults,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val _state = MutableStateFlow(load())
    val state: StateFlow<AppState> = _state.asStateFlow()

    private fun initial() = PersistedState(
        profile = Profile(unit = defaults.unit),
        settings = Settings(language = defaults.language),
    )

    private fun load(): AppState {
        val persisted = kv.get(KEY)?.let { raw ->
            runCatching { json.decodeFromString<PersistedState>(raw) }.getOrNull()
        } ?: initial()
        val hasKey = runCatching { !secrets.load().isNullOrEmpty() }.getOrDefault(false)
        return AppState(persisted, hasKey)
    }

    private fun edit(block: (PersistedState) -> PersistedState) {
        _state.update { it.copy(persisted = block(it.persisted)) }
        kv.put(KEY, json.encodeToString(PersistedState.serializer(), _state.value.persisted))
    }

    fun setName(name: String) = edit { it.copy(profile = it.profile.copy(name = name.trim())) }
    fun setBirthYear(year: Int) = edit { it.copy(profile = it.profile.copy(birthYear = year)) }
    fun setUnit(unit: WeightUnit) = edit { it.copy(profile = it.profile.copy(unit = unit)) }
    fun logWeight(kg: Double, date: LocalDate) =
        edit { it.copy(weights = upsertWeight(it.weights, WeightEntry(date.toString(), kg))) }
    fun setLanguage(language: Language) = edit { it.copy(settings = it.settings.copy(language = language)) }
    fun setTheme(theme: ThemeMode) = edit { it.copy(settings = it.settings.copy(theme = theme)) }
    fun completeOnboarding() = edit { it.copy(onboardingDone = true) }

    fun apiKey(): String? = secrets.load()

    fun connectAi(providerId: ProviderId, apiKey: String) {
        secrets.save(apiKey)
        _state.update { it.copy(hasApiKey = true) }
        edit { it.copy(providerId = providerId) }
    }

    fun disconnectAi() {
        secrets.delete()
        _state.update { it.copy(hasApiKey = false) }
    }

    fun resetAll() {
        runCatching { secrets.delete() }
        kv.remove(KEY)
        _state.value = AppState(initial(), hasApiKey = false)
    }

    private companion object {
        const val KEY = "overfit.state.v1"
    }
}
