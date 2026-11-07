package app.overfit.fuel

/**
 * Real model names for the UI (docs/DECISIONS.md "Ehrliche Begriffe"): never a marketing tier like "Standard" or "Pro".
 * The live names come from `/v1/config` ([FuelTierInfo.label]); this table only names models of stored bookings and
 * answers when the config is not at hand.
 */
private val KNOWN = mapOf(
    "gemini-3.6-flash" to "Gemini 3.6 Flash (Google)",
    "claude-sonnet-5-5" to "Claude Sonnet 5.5 (Anthropic)",
    "@cf/meta/llama-3.3-70b-instruct-fp8-fast" to "Llama 3.3 70B (Cloudflare)",
    "@cf/meta/llama-3.1-8b-instruct" to "Llama 3.1 8B (Cloudflare)",
)

fun modelLabel(modelId: String): String = KNOWN[modelId] ?: modelId

/** Model name without the provider in brackets ("Gemini 3.6 Flash"). */
fun modelShortName(modelId: String): String = modelLabel(modelId).substringBefore(" (")

/**
 * Model of an internal tier ID (standard | pro | lite), for old bookings that only store the tier. Those bookings are from
 * before the lite switch to Llama 3.3 70B (new bookings store the model), so lite stays Llama 3.1 8B here.
 */
fun modelOfTier(tier: String): String? = when (tier) {
    "standard" -> "gemini-3.6-flash"
    "pro" -> "claude-sonnet-5-5"
    "lite" -> "@cf/meta/llama-3.1-8b-instruct"
    else -> null
}
