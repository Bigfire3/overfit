package app.overfit.coach

import app.overfit.data.KeyValueStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
enum class TrainingGoal { MUSCLE, STRENGTH, FAT_LOSS, FITNESS }

@Serializable
enum class TrainingExperience { BEGINNER, INTERMEDIATE, ADVANCED }

/**
 * The coach profile ("Dein Profil" in the chat): what a personal trainer asks first. Sent with every chat question
 * unless the user removes the profile chip for that chat. Body weight is not stored here: it comes from the calendar.
 */
@Serializable
data class AthleteProfile(
    val goal: TrainingGoal? = null,
    val experience: TrainingExperience? = null,
    val daysPerWeek: Int? = null,
    val heightCm: Int? = null,
    val equipment: String = "",
    val limitations: String = "",
) {
    /** English facts line for the system prompt (prompts are English, the answer language is set separately). */
    fun promptLine(): String? = buildList {
        goal?.let {
            add(
                "Goal: " + when (it) {
                    TrainingGoal.MUSCLE -> "muscle gain"
                    TrainingGoal.STRENGTH -> "strength"
                    TrainingGoal.FAT_LOSS -> "fat loss"
                    TrainingGoal.FITNESS -> "general fitness"
                },
            )
        }
        experience?.let { add("Experience: " + it.name.lowercase()) }
        daysPerWeek?.let { add("Training days per week: $it") }
        heightCm?.let { add("Height: $it cm") }
        equipment.trim().takeIf { it.isNotEmpty() }?.let { add("Equipment: $it") }
        limitations.trim().takeIf { it.isNotEmpty() }?.let { add("Limitations: $it") }
    }.takeIf { it.isNotEmpty() }?.joinToString(" · ")

    companion object {
        const val DAYS_MIN = 1
        const val DAYS_MAX = 7
        const val HEIGHT_MIN = 100
        const val HEIGHT_MAX = 250
        const val TEXT_MAX = 300
    }
}

/**
 * Settings of the coach chat, apart from [CoachSettings] (own key, so both can change independently):
 * - [profile]: the coach profile,
 * - [addition]: "Deine Ergänzung", appended to the system prompt of every request (every model, own key too).
 *
 * Until 8.11. there was `autoReview` (a review chat sent by itself after every training). It is gone: no AI request
 * leaves without a tap of the user. Stored prefs that still carry the key are rewritten without it on load.
 */
@Serializable
data class CoachChatPrefs(
    val profile: AthleteProfile = AthleteProfile(),
    val addition: String = "",
) {
    companion object {
        const val ADDITION_MAX = 2_000
    }
}

class CoachChatPrefsStore(private val kv: KeyValueStore) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val _state = MutableStateFlow(load())
    val state: StateFlow<CoachChatPrefs> = _state.asStateFlow()

    private fun load(): CoachChatPrefs {
        val raw = kv.get(KEY) ?: return CoachChatPrefs()
        val prefs = runCatching { json.decodeFromString<CoachChatPrefs>(raw) }.getOrNull() ?: return CoachChatPrefs()
        // Migration: drop the removed "automatic review" switch from the stored JSON (everything else stays).
        if (raw.contains("\"$LEGACY_AUTO_REVIEW\"")) kv.put(KEY, json.encodeToString(CoachChatPrefs.serializer(), prefs))
        return prefs
    }

    private fun edit(block: (CoachChatPrefs) -> CoachChatPrefs) {
        _state.update(block)
        kv.put(KEY, json.encodeToString(CoachChatPrefs.serializer(), _state.value))
    }

    fun setProfile(profile: AthleteProfile) = edit {
        it.copy(
            profile = profile.copy(
                daysPerWeek = profile.daysPerWeek?.coerceIn(AthleteProfile.DAYS_MIN, AthleteProfile.DAYS_MAX),
                heightCm = profile.heightCm?.takeIf { h -> h in AthleteProfile.HEIGHT_MIN..AthleteProfile.HEIGHT_MAX },
                equipment = profile.equipment.trim().take(AthleteProfile.TEXT_MAX),
                limitations = profile.limitations.trim().take(AthleteProfile.TEXT_MAX),
            ),
        )
    }

    fun setAddition(text: String) = edit { it.copy(addition = text.trim().take(CoachChatPrefs.ADDITION_MAX)) }
    fun replace(prefs: CoachChatPrefs) = edit { prefs }
    fun reset() { kv.remove(KEY); _state.value = CoachChatPrefs() }

    /**
     * Before the chat redesign the coach instructions could be edited as a whole ([CoachSettings.instructions]). That
     * text moves into [addition] once, so nothing the user wrote is lost and the new base instruction applies.
     */
    fun migrateLegacyInstructions(settings: CoachSettingsStore) {
        val legacy = settings.state.value.instructions?.trim().orEmpty()
        if (legacy.isEmpty()) return
        val current = state.value.addition
        if (!current.contains(legacy)) {
            // Not cut to ADDITION_MAX here: nothing the user wrote is lost (the field shows all of it and they can shorten it).
            val merged = listOf(legacy, current).filter { it.isNotBlank() }.joinToString("\n\n")
            edit { it.copy(addition = merged) }
        }
        settings.setInstructions(null)
    }

    companion object {
        const val KEY = "overfit.coachChat.v1"
        /** Key of the removed automatic-review switch (older app versions and backups). */
        const val LEGACY_AUTO_REVIEW = "autoReview"
    }
}

/** System prompt instructions: the Overfit base text plus the user's addition (if any). */
fun composeInstructions(base: String, addition: String): String {
    val extra = addition.trim()
    if (extra.isEmpty()) return base.trim()
    return base.trim() + "\n\nAdditional instructions from the user (follow them unless they conflict with the output format):\n" + extra
}
