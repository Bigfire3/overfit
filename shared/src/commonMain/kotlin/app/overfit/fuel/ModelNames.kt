package app.overfit.fuel

/**
 * Real model names for the UI (docs/DECISIONS.md "Ehrliche Begriffe"): never a marketing tier like "Standard" or "Pro".
 * The live names come from `/v1/config` ([FuelTierInfo.label]); this table only names models of stored bookings and
 * answers when the config is not at hand.
 */
private val KNOWN = mapOf(
    "claude-haiku-5-5" to "Claude Haiku 5.5 (Anthropic)",
    "claude-sonnet-5-5" to "Claude Sonnet 5.5 (Anthropic)",
    "claude-opus-5-5" to "Claude Opus 5.5 (Anthropic)",
    "gemini-3.6-flash" to "Gemini 3.6 Flash (Google)",
    "@cf/meta/llama-3.3-70b-instruct-fp8-fast" to "Llama 3.3 70B (Cloudflare)",
    "@cf/meta/llama-3.1-8b-instruct" to "Llama 3.1 8B (Cloudflare)",
)

fun modelLabel(modelId: String): String = KNOWN[modelId] ?: modelId

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
