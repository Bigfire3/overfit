package app.overfit.coach

import app.overfit.ai.ProviderId
import app.overfit.data.KeyValueStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Coach settings. Kept apart from the profile state ([app.overfit.data.AppStore]) under their own key. */
@Serializable
data class CoachSettings(
    /** Model per provider (provider name → model id); missing = [CoachModels.default]. */
    val models: Map<String, String> = emptyMap(),
    /** Consent: body photos may be sent to the AI provider (default off). */
    val sendPhotos: Boolean = false,
    /** Edited coach role and rules; null = the default text in the app language. */
    val instructions: String? = null,
) {
    fun model(id: ProviderId): String = if (id == ProviderId.FUEL) CoachModels.FUEL
    else models[id.name]?.takeIf { it.isNotBlank() } ?: CoachModels.default(id)
}

class CoachSettingsStore(private val kv: KeyValueStore) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val _state = MutableStateFlow(
        kv.get(KEY)?.let { runCatching { json.decodeFromString<CoachSettings>(it) }.getOrNull() } ?: CoachSettings(),
    )
    val state: StateFlow<CoachSettings> = _state.asStateFlow()

    private fun edit(block: (CoachSettings) -> CoachSettings) {
        _state.update(block)
        kv.put(KEY, json.encodeToString(CoachSettings.serializer(), _state.value))
    }

    fun setModel(id: ProviderId, model: String) = edit {
        val trimmed = model.trim()
        it.copy(models = if (trimmed.isEmpty() || trimmed == CoachModels.default(id)) it.models - id.name else it.models + (id.name to trimmed))
    }
    fun setSendPhotos(on: Boolean) = edit { it.copy(sendPhotos = on) }
    fun setInstructions(text: String?) = edit { it.copy(instructions = text?.takeIf { t -> t.isNotBlank() }) }
    fun reset() { kv.remove(KEY); _state.value = CoachSettings() }

    companion object { const val KEY = "overfit.coach.v1" }
}
