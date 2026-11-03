package app.overfit.domain

import app.overfit.chat.ChatContext
import app.overfit.chat.ContextInput
import app.overfit.chat.ChatDocs
import app.overfit.coach.CoachContext
import app.overfit.coach.CoachPromptBuilder
import app.overfit.coach.ReviewKind
import app.overfit.i18n.ChatEnglish
import app.overfit.i18n.German
import app.overfit.share.AiPrompt
import app.overfit.ui.share.aiTemplateText
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class ModelContextTest {
    private val today = LocalDate(2026, 11, 2)

    @Test
    fun rendersFullBlock() {
        val block = buildProfileBlock(
            profile = Profile(name = "Fabi", birthYear = 1995, unit = WeightUnit.KG),
            weights = listOf(WeightEntry("2026-10-01", 84.0), WeightEntry("2026-11-02", 82.0)),
            language = Language.DE,
            today = today,
        )
        assertEquals(
            "Name: Fabi · Age: 31 · Body weight: 82 kg (as of 2026-11-02)\n" +
                "Reply language: German · Units: metric · Today: 2026-11-02",
            block,
        )
    }

    @Test
    fun usesDisplayUnitAndSkipsMissingFields() {
        val block = buildProfileBlock(
            profile = Profile(name = "Sam", birthYear = null, unit = WeightUnit.LB),
            weights = listOf(WeightEntry("2026-11-02", 81.6)),
            language = Language.EN,
            today = today,
        )
        assertEquals(
            "Name: Sam · Body weight: 179.9 lb (as of 2026-11-02)\n" +
                "Reply language: English · Units: imperial · Today: 2026-11-02",
            block,
        )
    }

    @Test
    fun blockWithoutAnyFactsHasOnlyMetaLine() {
        val block = buildProfileBlock(Profile(name = "", birthYear = null), emptyList(), Language.EN, today)
        assertEquals("Reply language: English · Units: metric · Today: 2026-11-02", block)
    }

    // ---- aiProfileFacts ----

    private val weights = listOf(WeightEntry("2026-10-01", 84.0), WeightEntry("2026-11-02", 82.5))

    @Test
    fun factsEnglishKg() {
        val f = aiProfileFacts(Profile(name = "Fabi", birthYear = 1995), weights, today, Language.EN)
        assertEquals("Name: Fabi · Age: 31 · Body weight: 82.5 kg (as of 2026-11-02)", f)
    }

    @Test
    fun factsGermanUsesDecimalCommaAndGermanLabels() {
        val f = aiProfileFacts(Profile(name = "Fabi", birthYear = 1995), weights, today, Language.DE)
        assertEquals("Name: Fabi · Alter: 31 · Körpergewicht: 82,5 kg (Stand 2026-11-02)", f)
    }

    @Test
    fun factsInPoundsUseLatestEntry() {
        val f = aiProfileFacts(Profile(name = "Sam", birthYear = 1990, unit = WeightUnit.LB), weights, today, Language.EN)!!
        assertTrue(f.contains("Body weight: ${formatNumber(displayWeight(82.5, WeightUnit.LB), Language.EN)} lb (as of 2026-11-02)"), f)
        assertTrue(f.contains("lb"))
        assertFalse(f.contains("kg"))
    }

    @Test
    fun missingValuesAreLeftOutWithoutPlaceholders() {
        val onlyName = aiProfileFacts(Profile(name = "  Eva ", birthYear = null), emptyList(), today, Language.EN)
        assertEquals("Name: Eva", onlyName)
        val onlyWeight = aiProfileFacts(Profile(name = "", birthYear = null), weights, today, Language.DE)!!
        assertEquals("Körpergewicht: 82,5 kg (Stand 2026-11-02)", onlyWeight)
        val onlyAge = aiProfileFacts(Profile(name = "", birthYear = 1995), emptyList(), today, Language.EN)
        assertEquals("Age: 31", onlyAge)
        listOf(onlyName, onlyWeight, onlyAge).forEach { assertFalse(it!!.contains("null")) }
    }

    @Test
    fun implausibleAgeIsLeftOut() {
        assertNull(aiProfileFacts(Profile(name = "", birthYear = 2026), emptyList(), today, Language.EN))
        assertNull(aiProfileFacts(Profile(name = "", birthYear = 1800), emptyList(), today, Language.EN))
    }

    @Test
    fun allEmptyIsNull() {
        assertNull(aiProfileFacts(Profile(name = "", birthYear = null), emptyList(), today, Language.EN))
        assertNull(aiProfileHeader(Profile(name = " ", birthYear = null), emptyList(), today, Language.DE))
    }

    @Test
    fun headerAndWithAiProfile() {
        val p = Profile(name = "Fabi", birthYear = 1995)
        assertEquals("About me: Name: Fabi · Age: 31", aiProfileHeader(p, emptyList(), today, Language.EN))
        assertEquals("Über mich: Name: Fabi · Alter: 31", aiProfileHeader(p, emptyList(), today, Language.DE))
        assertEquals("H\n\nbody", withAiProfile("H", "body"))
        assertEquals("body", withAiProfile(null, "body"))
    }

    @Test
    fun blockContainsFactsLine() {
        val p = Profile(name = "Fabi", birthYear = 1995)
        val facts = aiProfileFacts(p, weights, today, Language.EN)!!
        val block = buildProfileBlock(p, weights, Language.DE, today)
        assertTrue(block.lines().first() == facts)
    }
}

/** Every AI path carries the same profile text (name, age, latest weight). */
class AiProfileInAllPathsTest {
    private val today = LocalDate(2026, 11, 3)
    private val profile = Profile(name = "Fabian", birthYear = 1990)
    private val weights = listOf(WeightEntry("2026-10-30", 81.5))
    private val facts = aiProfileFacts(profile, weights, today, Language.EN)!!

    private fun assertHasProfile(text: String, de: Boolean = false) {
        assertTrue(text.contains("Fabian"), text)
        assertTrue(text.contains(if (de) "Alter: 36" else "Age: 36"), text)
        assertTrue(text.contains(if (de) "81,5 kg" else "81.5 kg"), text)
    }

    @Test
    fun coachPromptDynamicSystem() {
        val ctx = CoachContext(
            kind = ReviewKind.PROGRESS, language = Language.EN, instructions = "coach", profileBlock = buildProfileBlock(profile, weights, Language.EN, today), memory = null,
            exerciseList = emptyList(), current = null, template = null, sameTemplate = emptyList(), lastOverall = null,
            timeZone = TimeZone.UTC,
        )
        val p = CoachPromptBuilder.build(ctx)
        assertTrue(p.dynamicSystem.contains(facts))
        assertHasProfile(p.dynamicSystem)
    }

    @Test
    fun chatContext() {
        val input = ContextInput(profile, weights, null, emptyList(), Language.DE, today, TimeZone.UTC)
        val text = ChatContext.build(input, app.overfit.i18n.ChatGerman, AiPrompt.EXAMPLE)
        assertTrue(text.contains(aiProfileFacts(profile, weights, today, Language.DE)!!))
        assertHasProfile(text, de = true)
    }

    @Test
    fun chatDocsProfileSummary() {
        val now = Instant.parse("2026-11-03T12:00:00Z").toEpochMilliseconds()
        val doc = ChatDocs.profile(profile, weights, Language.EN, "UTC", now)
        assertEquals(facts, doc.data["summary"]!!.jsonPrimitive.content)
        val empty = ChatDocs.profile(Profile(name = "", birthYear = null), emptyList(), Language.EN, "UTC", now)
        assertFalse(empty.data.containsKey("summary"))
    }

    @Test
    fun aiTemplatePromptHasHeaderAndStaysImportable() {
        val header = aiProfileHeader(profile, weights, today, Language.DE)
        val text = aiTemplateText(German.share, header)
        assertTrue(text.startsWith("Über mich: "))
        assertHasProfile(text, de = true)
        assertTrue(text.contains(AiPrompt.EXAMPLE))
        assertEquals(German.share.aiTemplatePrompt(AiPrompt.EXAMPLE), aiTemplateText(German.share, null))
    }

    @Test
    fun gemInstructionsComposition() {
        // ChatFeature.gemText needs the store and data sources; it is exactly this composition.
        val text = withAiProfile(aiProfileHeader(profile, weights, today, Language.EN), ChatEnglish.gemInstructions(AiPrompt.EXAMPLE))
        assertTrue(text.startsWith("About me: "))
        assertHasProfile(text)
        assertTrue(text.contains(AiPrompt.EXAMPLE))
    }
}
