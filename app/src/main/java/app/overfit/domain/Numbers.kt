package app.overfit.domain

import kotlin.math.abs
import kotlin.math.roundToLong

/** Zahlen-Ein-/Ausgabe im deutschen Format (Komma als Dezimaltrenner, Punkt wird ebenfalls akzeptiert). */
object Numbers {
    /** "92,5" / "92.5" / "-10" → Double; leer oder ungültig → null. Auf 0,01 kg gerundet. */
    fun parseWeight(text: String): Double? {
        val t = text.trim().replace(',', '.')
        if (t.isEmpty() || t == "-" || t == ".") return null
        val v = t.toDoubleOrNull() ?: return null
        if (v.isNaN() || v.isInfinite() || abs(v) > 10_000) return null
        return (v * 100).roundToLong() / 100.0
    }

    /** 92.5 → "92,5", 90.0 → "90", -10.25 → "-10,25". */
    fun formatWeight(kg: Double): String {
        val rounded = (kg * 100).roundToLong()
        val whole = rounded / 100
        val frac = abs(rounded % 100)
        val sign = if (rounded < 0 && whole == 0L) "-" else ""
        return when {
            frac == 0L -> "$sign$whole"
            frac % 10 == 0L -> "$sign$whole,${frac / 10}"
            else -> "$sign$whole,${frac.toString().padStart(2, '0')}"
        }
    }

    /** Ganze, nicht negative Zahl (Wiederholungen); leer oder ungültig → null. */
    fun parseCount(text: String): Int? = text.trim().toIntOrNull()?.takeIf { it in 0..9_999 }

    /** Dauer: "90" (Sekunden) oder "1:30" (Minuten:Sekunden) → Sekunden. */
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
}
