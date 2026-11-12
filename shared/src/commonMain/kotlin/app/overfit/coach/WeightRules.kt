package app.overfit.coach

import app.overfit.domain.Equipment

/**
 * How the AI must read logged weights (Fabian, 8.11.: the coach called 0 kg "forgotten" and 70 kg on a machine biceps
 * curl "wrong" and made up its own numbers). Part of every coach prompt (chat and review), not editable by the user,
 * so no edited instruction can drop it. Prompt text, therefore English and in code like the other output rules.
 */
object WeightRules {
    const val TEXT = """How to read the logged weights (always follow these rules):
- Every weight is exactly what the user set on the machine, cable or bar/dumbbell. For machines and cables it is the value on the weight stack or scale, never converted to free-weight load. Machines differ a lot, so a number is never "too high", "too low" or "unrealistic".
- Never call a logged weight wrong, forgotten, a typo or unrealistic, and never replace it with numbers of your own. It is the user's benchmark: calculate from it (e.g. 70 kg × 10 today → 75 kg next time).
- 0 kg ("BW" in the data) is a valid entry: bodyweight, assisted or without added weight. It is not a missing entry. Negative weights mean assistance.
- Judge progress only relative to earlier sets of the same exercise (same machine). Never compare weights across different exercises or machines, or with free-weight norms.
- Suggest increases in the steps the user already uses for that exercise (look at the jumps in their history); if unknown, the smallest usual step in the user's unit (about 2.5 kg or 5 lb, on a machine the next plate of the stack).
- The data names each exercise's equipment in parentheses: (machine), (cable), (free weight), (bodyweight), (band)."""

    /** Short equipment tag for the AI data ("machine", "cable", "free weight", "bodyweight", "band"); null = unknown/other. */
    fun tag(equipment: Equipment?): String? = when (equipment) {
        Equipment.MACHINE -> "machine"
        Equipment.CABLE -> "cable"
        Equipment.BARBELL, Equipment.EZ_BAR, Equipment.DUMBBELL, Equipment.KETTLEBELL, Equipment.PLATE -> "free weight"
        Equipment.BODYWEIGHT, Equipment.PULLUP_BAR -> "bodyweight"
        Equipment.BAND -> "band"
        else -> null
    }

    /** "Biceps Curl (machine)"; the name alone when the equipment is unknown. */
    fun name(name: String, equipment: Equipment?): String = tag(equipment)?.let { "$name ($it)" } ?: name
}
