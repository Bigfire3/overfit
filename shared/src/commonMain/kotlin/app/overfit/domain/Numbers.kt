package app.overfit.domain

import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Number input/output for sets. Weights are always stored in kg; input and display follow the
 * user's unit (kg/lb) and language (decimal comma in German, point in English; both are accepted).
 */
object Numbers {
    private const val MAX_ABS_KG = 2_000.0

    /** "92,5" / "92.5" / "-10" in [unit] → kg; empty or invalid → null. */
    fun parseWeight(text: String, unit: WeightUnit = WeightUnit.KG): Double? {
        val t = text.trim().replace(',', '.')
        if (t.isEmpty() || t == "-" || t == ".") return null
        val v = t.toDoubleOrNull() ?: return null
        if (v.isNaN() || v.isInfinite()) return null
        val kg = if (unit == WeightUnit.KG) round(v, 100) else round(lbToKg(v), 10_000)
        return kg.takeIf { abs(it) <= MAX_ABS_KG }
    }

    /** kg → text in [unit] with at most two decimals: 92.5 → "92,5" (DE), 90.0 → "90", -10.25 → "-10,25". */
    fun formatWeight(kg: Double, unit: WeightUnit = WeightUnit.KG, language: Language = Language.DE): String {
        val value = if (unit == WeightUnit.KG) kg else kgToLb(kg)
        val rounded = (value * 100).roundToLong()
        val whole = abs(rounded) / 100
        val frac = abs(rounded) % 100
        val sign = if (rounded < 0) "-" else ""
        val sep = if (language == Language.DE) ',' else '.'
        return when {
            frac == 0L -> "$sign$whole"
            frac % 10 == 0L -> "$sign$whole$sep${frac / 10}"
            else -> "$sign$whole$sep${frac.toString().padStart(2, '0')}"
        }
    }

    /** Whole, non-negative number (reps); empty or invalid → null. */
    fun parseCount(text: String): Int? = text.trim().toIntOrNull()?.takeIf { it in 0..9_999 }

    /** Duration: "90" (seconds) or "1:30" (minutes:seconds) → seconds. */
    fun parseDuration(text: String): Int? {
        val t = text.trim()
        if (t.isEmpty()) return null
        val parts = t.split(':')
        return when (parts.size) {
            1 -> parts[0].toIntOrNull()
            2 -> {
                val m = parts[0].ifEmpty { "0" }.toIntOrNull()
                val s = parts[1].toIntOrNull()?.takeIf { it in 0..59 }
                if (m == null || s == null) null else m * 60 + s
            }
            else -> null
        }?.takeIf { it in 0..86_400 }
    }

    /** 90 → "1:30", 45 → "0:45". */
    fun formatDuration(sec: Int): String = "${sec / 60}:${(sec % 60).toString().padStart(2, '0')}"

    private fun round(v: Double, factor: Int): Double = (v * factor).roundToLong() / factor.toDouble()
}
