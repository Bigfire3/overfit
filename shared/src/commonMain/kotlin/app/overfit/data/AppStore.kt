package app.overfit.data

import app.overfit.ai.ProviderId
import app.overfit.domain.Language
import app.overfit.domain.Profile
import app.overfit.domain.WeightEntry
import app.overfit.domain.WeightUnit
import app.overfit.domain.upsertWeight
import app.overfit.workout.WorkoutPrefs
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
    /** Rest when neither template nor exercise sets one. */
    val defaultRestSec: Int = WorkoutPrefs.DEFAULT_REST_SEC,
    val vibration: Boolean = true,
    /** Short ticks at 3, 2 and 1 s before a rest ends (also in the background). */
    val restTicks: Boolean = true,
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
 * Profile data is small; workouts live in the database (Room).
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
    /** Mirror of the body-weight table ([BodyWeightRepository] is the source of truth). */
    fun mirrorWeights(entries: List<WeightEntry>) {
        val sorted = entries.sortedBy { it.date }
        if (sorted != _state.value.persisted.weights) edit { it.copy(weights = sorted) }
    }
    fun setLanguage(language: Language) = edit { it.copy(settings = it.settings.copy(language = language)) }
    fun setDefaultRest(sec: Int) =
        edit { it.copy(settings = it.settings.copy(defaultRestSec = sec.coerceIn(0, MAX_REST_SEC))) }
    fun setVibration(on: Boolean) = edit { it.copy(settings = it.settings.copy(vibration = on)) }
    fun setRestTicks(on: Boolean) = edit { it.copy(settings = it.settings.copy(restTicks = on)) }
    fun completeOnboarding() = edit { it.copy(onboardingDone = true) }

    /** The Coach story opens by itself on the first visit of the Coach tab, afterwards only on tap. */
    fun coachStorySeen(): Boolean = kv.get(KEY_COACH_STORY_SEEN) != null
    fun markCoachStorySeen() = kv.put(KEY_COACH_STORY_SEEN, "1")

    fun apiKey(): String? = secrets.load()

    fun connectAi(providerId: ProviderId, apiKey: String) {
        secrets.save(apiKey)
        _state.update { it.copy(hasApiKey = true) }
        edit { it.copy(providerId = providerId) }
    }

    /** Use Fuel (backend credit) as the coach's AI access; an existing own key stays stored. */
    fun useFuel() = edit { it.copy(providerId = ProviderId.FUEL) }

    fun disconnectAi() {
        secrets.delete()
        _state.update { it.copy(hasApiKey = false) }
    }

    /** Profile and settings from a backup; onboarding counts as done, the API key stays. */
    fun restore(persisted: PersistedState) = edit { persisted.copy(onboardingDone = true) }

    fun resetAll() {
        runCatching { secrets.delete() }
        kv.remove(KEY)
        kv.remove(KEY_COACH_STORY_SEEN) // after "delete all data" the story comes again on the first Coach visit
        _state.value = AppState(initial(), hasApiKey = false)
    }

    companion object {
        private const val KEY = "overfit.state.v1"
        const val KEY_COACH_STORY_SEEN = "overfit.coachStorySeen"
        const val MAX_REST_SEC = 3_600
    }
}
