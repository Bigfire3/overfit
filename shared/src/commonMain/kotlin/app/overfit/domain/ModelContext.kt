package app.overfit.domain

import kotlinx.datetime.LocalDate

private val LANGUAGE_NAMES = mapOf(Language.DE to "German", Language.EN to "English")

/**
 * The small profile block sent to the model with every request.
 * Regenerated per request so age, latest weight and today's date are always current.
 */
fun buildProfileBlock(
    profile: Profile,
    weights: List<WeightEntry>,
    language: Language,
    today: LocalDate,
): String {
    val facts = buildList {
        if (profile.name.isNotBlank()) add("User: ${profile.name}")
        profile.birthYear?.let { add("Age: ${ageFromBirthYear(it, today)}") }
        latestWeight(weights)?.let {
            val value = formatNumber(displayWeight(it.kg, profile.unit), Language.EN)
            add("Weight: $value ${profile.unit.symbol} (as of ${it.date})")
        }
    }
    val units = if (profile.unit == WeightUnit.KG) "metric" else "imperial"
    val meta = listOf(
        "Reply language: ${LANGUAGE_NAMES.getValue(language)}",
        "Units: $units",
        "Today: $today",
    )
    return listOf(facts.joinToString(" · "), meta.joinToString(" · "))
        .filter { it.isNotEmpty() }
        .joinToString("\n")
}
