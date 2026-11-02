package app.overfit.domain

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

@Serializable
enum class Language { DE, EN }

@Serializable
enum class WeightUnit(val symbol: String) { KG("kg"), LB("lb") }

@Serializable
enum class ThemeMode { SYSTEM, DARK, LIGHT }

@Serializable
data class Profile(
    /** How the user wants to be called. */
    val name: String = "",
    /** Stored instead of an age so it stays correct over time. */
    val birthYear: Int? = null,
    val unit: WeightUnit = WeightUnit.KG,
)

/** A single weight measurement. Always stored in kg. */
@Serializable
data class WeightEntry(
    /** ISO date, e.g. "2026-11-02". */
    val date: String,
    val kg: Double,
)

const val AGE_MIN = 13
const val AGE_MAX = 110
const val WEIGHT_KG_MIN = 25.0
const val WEIGHT_KG_MAX = 350.0
private const val LB_PER_KG = 2.20462262

fun kgToLb(kg: Double): Double = kg * LB_PER_KG
fun lbToKg(lb: Double): Double = lb / LB_PER_KG
fun round1(value: Double): Double = (value * 10).roundToInt() / 10.0

private val DECIMAL = Regex("""^\d+([.,]\d+)?$""")
private val AGE = Regex("""^\d{1,3}$""")

/** Parses user input like "82,4" or "82.4". Returns null for anything else. */
fun parseDecimal(input: String): Double? {
    val trimmed = input.trim()
    if (!DECIMAL.matches(trimmed)) return null
    return trimmed.replace(',', '.').toDouble()
}

fun parseAge(input: String): Int? {
    val trimmed = input.trim()
    if (!AGE.matches(trimmed)) return null
    return trimmed.toInt().takeIf { it in AGE_MIN..AGE_MAX }
}

/** Converts a weight input in the given unit to kg, or null if invalid or implausible. */
fun parseWeightToKg(input: String, unit: WeightUnit): Double? {
    val value = parseDecimal(input) ?: return null
    val kg = if (unit == WeightUnit.KG) value else lbToKg(value)
    if (kg < WEIGHT_KG_MIN || kg > WEIGHT_KG_MAX) return null
    return round1(kg)
}

fun birthYearFromAge(age: Int, today: LocalDate): Int = today.year - age

/** Approximate age from the birth year (may be off by one before the birthday). */
fun ageFromBirthYear(birthYear: Int, today: LocalDate): Int = today.year - birthYear

fun displayWeight(kg: Double, unit: WeightUnit): Double =
    round1(if (unit == WeightUnit.KG) kg else kgToLb(kg))

/** "82.4" / "82,4" / "82" without trailing ".0". */
fun formatNumber(value: Double, language: Language): String {
    val rounded = round1(value)
    val text = if (rounded == rounded.toLong().toDouble()) rounded.toLong().toString() else rounded.toString()
    return if (language == Language.DE) text.replace('.', ',') else text
}

fun formatWeight(kg: Double, unit: WeightUnit, language: Language): String =
    "${formatNumber(displayWeight(kg, unit), language)} ${unit.symbol}"

fun latestWeight(entries: List<WeightEntry>): WeightEntry? = entries.maxByOrNull { it.date }

/** Adds a weight entry; a second entry on the same day replaces the first. */
fun upsertWeight(entries: List<WeightEntry>, entry: WeightEntry): List<WeightEntry> =
    (entries.filter { it.date != entry.date } + entry).sortedBy { it.date }
