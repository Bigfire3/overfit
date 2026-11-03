package app.overfit.fuel

import app.overfit.domain.Language

const val MICRO_PER_EURO = 1_000_000L

/** Whole cents from µ€, rounded half up (display only; the backend always calculates in µ€). */
fun microToCents(micro: Long): Long = if (micro >= 0) (micro + 5_000) / 10_000 else -((-micro + 5_000) / 10_000)

/** "4,20 €" (German) / "€4.20" (English). Amounts are integer µ€, never floats. */
fun formatEuro(micro: Long, language: Language): String {
    val cents = microToCents(micro)
    val abs = if (cents < 0) -cents else cents
    val whole = abs / 100
    val frac = (abs % 100).toString().padStart(2, '0')
    val sign = if (cents < 0) "−" else ""
    return when (language) {
        Language.DE -> "$sign$whole,$frac €"
        Language.EN -> "$sign€$whole.$frac"
    }
}

/** Cost of one request: sub-cent amounts are shown with more precision ("0,0070 €") so small costs do not read as zero. */
fun formatCost(micro: Long, language: Language): String {
    val abs = if (micro < 0) -micro else micro
    if (abs >= 10_000 || abs == 0L) return formatEuro(micro, language)
    val tenthsOfCent = (abs + 50) / 100 // 4 decimals in euro
    val whole = tenthsOfCent / 10_000
    val frac = (tenthsOfCent % 10_000).toString().padStart(4, '0')
    return when (language) {
        Language.DE -> "${if (micro < 0) "−" else ""}$whole,$frac €"
        Language.EN -> "${if (micro < 0) "−" else ""}€$whole.$frac"
    }
}

/** Euro amount counted up for the Spot reveal: always two decimals, e.g. "0,37" (no currency sign; the UI adds "€"). */
fun formatRevealNumber(micro: Long, language: Language): String {
    val cents = microToCents(micro).coerceAtLeast(0)
    val frac = (cents % 100).toString().padStart(2, '0')
    return if (language == Language.DE) "${cents / 100},$frac" else "${cents / 100}.$frac"
}

/**
 * Estimated cost in cents for the model setting: "1,5 ct" (from 1 ct, one decimal), "0,02 ct" (below, two decimals),
 * "< 0,01 ct"; from 1 € as euro. Integer maths only.
 */
fun formatCents(micro: Long, language: Language): String {
    if (micro >= MICRO_PER_EURO) return formatEuro(micro, language)
    val unit = if (language == Language.DE) "ct" else "¢"
    fun num(whole: Long, frac: String) = if (language == Language.DE) "$whole,$frac" else "$whole.$frac"
    return when {
        micro >= 10_000 -> { val tenths = (micro + 500) / 1_000; "${num(tenths / 10, (tenths % 10).toString())} $unit" }
        (micro + 50) / 100 == 0L -> "< ${num(0, "01")} $unit"
        else -> { val h = (micro + 50) / 100; "${num(h / 100, (h % 100).toString().padStart(2, '0'))} $unit" }
    }
}
