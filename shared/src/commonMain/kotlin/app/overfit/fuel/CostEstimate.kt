package app.overfit.fuel

/** What a typical request of a task costs with a model; see docs/DECISIONS.md "Kostenschätzung". */
enum class FuelTask(val key: String) { COACH("coach"), CHAT("chat"), AUTOFILL("autofill") }

/**
 * Typical token amounts per task if the backend does not send any (old backend): measured on the real prompts, same
 * numbers as `USAGE_PROFILES` in backend/src/providers.ts.
 */
val DEFAULT_USAGE_PROFILES: Map<String, UsageProfile> = mapOf(
    "coach" to UsageProfile(8_200, 1_400),
    "chat" to UsageProfile(6_700, 500),
    "autofill" to UsageProfile(900, 120),
)

object CostEstimate {
    /**
     * Estimated cost in µ€ of one typical [task] with [tier]: list price (µ€ per million tokens) × typical tokens
     * (+ thinking tokens for coach and chat) × (1 + markup), rounded up. Integer maths like the backend, so the
     * estimate matches a real booking with the typical token amounts exactly.
     */
    fun micro(tier: FuelTierInfo, markupBp: Int, task: FuelTask, profiles: Map<String, UsageProfile> = emptyMap()): Long {
        val p = profiles[task.key] ?: DEFAULT_USAGE_PROFILES.getValue(task.key)
        val thinking = if (task == FuelTask.AUTOFILL) 0 else tier.thinkingTokens
        val sum = p.inputTokens * tier.price.input + (p.outputTokens + thinking) * tier.price.out
        val bp = 10_000L
        val num = sum * (bp + markupBp)
        val den = 1_000_000L * bp
        return (num + den - 1) / den
    }

    /** How many typical [task]s the [balanceMicro] pays for (rounded down). */
    fun affordable(balanceMicro: Long, perTaskMicro: Long): Long = if (perTaskMicro <= 0) 0 else (balanceMicro.coerceAtLeast(0)) / perTaskMicro
}
