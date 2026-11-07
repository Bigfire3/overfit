package app.overfit.fuel

/**
 * Real model names for the UI (docs/DECISIONS.md "Ehrliche Begriffe"): never a marketing tier like "Standard" or "Pro".
 * The server decides which provider and model answer (config switch per tier), so the names it sends
 * (`/v1/config` → `tiers`, `modelNames`, fallbacks; `/v1/messages` → `overfit.modelName`) win, see [ModelNames].
 * This table is only the offline fallback for models of stored bookings when no server name is known.
 */
private val KNOWN = mapOf(
    "claude-haiku-5-5" to "Claude Haiku 5.5 (Anthropic)",
    "claude-sonnet-5-5" to "Claude Sonnet 5.5 (Anthropic)",
    "claude-opus-5-5" to "Claude Opus 5.5 (Anthropic)",
    "gemini-3.6-flash" to "Gemini 3.6 Flash (Google)",
    "@cf/meta/llama-3.3-70b-instruct-fp8-fast" to "Llama 3.3 70B (Cloudflare)",
    "@cf/meta/llama-3.1-8b-instruct" to "Llama 3.1 8B (Cloudflare)",
)

/**
 * Model names learned from the server: model ID → "Display name (Vendor)". Copy-on-write map behind a volatile
 * reference: reads never lock; two registrations racing may drop one of them, which only costs a name until the next
 * config load.
 */
object ModelNames {
    @kotlin.concurrent.Volatile
    private var learned: Map<String, String> = emptyMap()

    @kotlin.concurrent.Volatile
    private var tierModels: Map<String, String> = emptyMap()

    /** The learned label of [modelId], or null. */
    fun label(modelId: String): String? = learned[modelId]

    /** Model the server last named for credit [tier] (standard | pro | lite), or null. */
    fun modelOfTier(tier: String): String? = tierModels[tier]

    /** Remembers "[displayName] ([vendor])" for [modelId]; blank IDs or names are ignored. */
    fun register(modelId: String?, displayName: String?, vendor: String?) {
        val id = modelId?.trim().orEmpty()
        val name = displayName?.trim().orEmpty()
        if (id.isEmpty() || name.isEmpty()) return
        val v = vendor?.trim().orEmpty()
        val label = if (v.isEmpty()) name else "$name ($v)"
        val current = learned
        if (current[id] != label) learned = current + (id to label)
    }

    /** All names of a `/v1/config`: `modelNames`, every tier's model and its fallback model. */
    fun register(config: FuelConfig) {
        config.modelNames.forEach { (id, n) -> register(id, n.displayName, n.vendor) }
        config.tiers.values.forEach { t ->
            register(t.model, t.displayName, t.vendor)
            t.fallbackModel?.let { register(it.model, it.displayName, it.vendor) }
        }
        val models = config.tiers.mapNotNull { (tier, t) -> t.model.takeIf { it.isNotBlank() }?.let { tier to it } }.toMap()
        if (models.isNotEmpty()) tierModels = models
    }

    /** Names of an answer of `/v1/messages` (served and requested model). */
    fun register(result: FuelMessageResult) {
        register(result.servedModel, result.servedModelName, result.servedVendor)
        register(result.chosenModel, result.chosenModelName, result.chosenVendor)
    }

    /** Forgets every learned name (tests). */
    fun clear() {
        learned = emptyMap()
        tierModels = emptyMap()
    }
}

/** "Display name (Vendor)": the name the server sent, else the offline table, else the ID itself. */
fun modelLabel(modelId: String): String = ModelNames.label(modelId) ?: KNOWN[modelId] ?: modelId

/** Model name without the provider in brackets ("Gemini 3.6 Flash"). */
fun modelShortName(modelId: String): String = modelLabel(modelId).substringBefore(" (")

/**
 * Model of an internal tier ID (standard | pro | lite), for old bookings that only store the tier (from before the
 * tiers stored their model, i.e. the Gemini/Sonnet/Llama time). Newer bookings store the model itself.
 */
fun modelOfTier(tier: String): String? = when (tier) {
    "standard" -> "gemini-3.6-flash"
    "pro" -> "claude-sonnet-5-5"
    "lite" -> "@cf/meta/llama-3.1-8b-instruct"
    else -> null
}

/** From this moment (7.11.2026, 00:00 UTC) every credit tier is a Claude model (Haiku/Sonnet/Opus 5.5). */
const val CLAUDE_ONLY_SINCE_SEC = 1_794_009_600L

/** Model whose price a booking of [tier] at [tsSec] was charged at: the Claude tiers since the switch, the old table before. */
fun modelOfTierAt(tier: String, tsSec: Long): String? =
    if (tsSec < CLAUDE_ONLY_SINCE_SEC) modelOfTier(tier)
    else when (tier) {
        "lite" -> "claude-haiku-5-5"
        "standard" -> "claude-sonnet-5-5"
        "pro" -> "claude-opus-5-5"
        else -> null
    }
