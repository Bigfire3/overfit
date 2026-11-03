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
    val facts = aiProfileFacts(profile, weights, today, Language.EN).orEmpty()
    val units = if (profile.unit == WeightUnit.KG) "metric" else "imperial"
    val meta = listOf(
        "Reply language: ${LANGUAGE_NAMES.getValue(language)}",
        "Units: $units",
        "Today: $today",
    )
    return listOf(facts, meta.joinToString(" · "))
        .filter { it.isNotEmpty() }
        .joinToString("\n")
}

/**
 * Name, age and current body weight (latest entry, in the profile unit) as one line. Every AI path uses this one
 * function: coach (own key and Fuel), "Copy context", the AI template prompt, the Gem/GPT instructions and the chat
 * sync (the backend puts it into every MCP tool answer). Missing values are left out; null when nothing is known.
 * [language] only picks the labels and the decimal separator, so a pasted German prompt stays German.
 */
fun aiProfileFacts(profile: Profile, weights: List<WeightEntry>, today: LocalDate, language: Language): String? {
    val de = language == Language.DE
    val facts = buildList {
        profile.name.trim().takeIf { it.isNotEmpty() }?.let { add("Name: $it") }
        profile.birthYear?.let { ageFromBirthYear(it, today) }?.takeIf { it in AGE_MIN..AGE_MAX }?.let {
            add(if (de) "Alter: $it" else "Age: $it")
        }
        latestWeight(weights)?.let {
            val value = "${formatNumber(displayWeight(it.kg, profile.unit), language)} ${profile.unit.symbol}"
            add(if (de) "Körpergewicht: $value (Stand ${it.date})" else "Body weight: $value (as of ${it.date})")
        }
    }
    return facts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

/** [aiProfileFacts] as a leading "About me" line for texts the user pastes into a chat; null when nothing is known. */
fun aiProfileHeader(profile: Profile, weights: List<WeightEntry>, today: LocalDate, language: Language): String? =
    aiProfileFacts(profile, weights, today, language)?.let { if (language == Language.DE) "Über mich: $it" else "About me: $it" }

/** Puts [header] (from [aiProfileHeader]) above [text]. */
fun withAiProfile(header: String?, text: String): String = if (header == null) text else "$header\n\n$text"
