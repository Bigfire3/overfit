package app.overfit.chat

import app.overfit.domain.Language
import app.overfit.domain.Profile
import app.overfit.domain.SetValues
import app.overfit.domain.WeightEntry
import app.overfit.i18n.ChatEnglish
import app.overfit.i18n.ChatGerman
import app.overfit.share.AiPrompt
import app.overfit.share.ShareFormat
import app.overfit.share.ShareParse
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChatContextTest {
    private val day = 86_400_000L
    private val base = 1_760_000_000_000L // 2025-10-09 in UTC
    private fun input(workouts: List<app.overfit.data.WorkoutRecord>, language: Language = Language.DE, memory: String? = "Knie schonen") = ContextInput(
        profile = Profile("Fabian", 1990),
        weights = listOf(WeightEntry("2026-10-30", 81.5)),
        memory = memory,
        workouts = workouts,
        language = language,
        today = LocalDate(2026, 11, 3),
        timeZone = TimeZone.UTC,
    )

    @Test
    fun contextContainsProfileMemoryRecentWorkoutsAndProgress() {
        val workouts = (0 until 12).map { i ->
            record("w$i", base + i * 7 * day, sets = listOf(SetValues(60.0 + i, 8, null), SetValues(60.0 + i, 7, null)), note = if (i == 11) "Gut drauf" else null)
        } + record("running", base + 100 * day, ended = false)
        val text = ChatContext.build(input(workouts), ChatGerman, AiPrompt.EXAMPLE)
        assertTrue(text.contains("Fabian · 36 Jahre · Gewicht 81,5 kg (Stand 2026-10-30)"))
        assertTrue(text.contains("Knie schonen"))
        // newest first, only 10 workouts, running one excluded
        val recent = text.lines().count { it.startsWith("- ") }
        assertEquals(10, recent)
        assertTrue(text.contains("Bench: 71×8, 71×7")) // newest workout i=11
        assertFalse(text.contains("Bench: 60×8, 60×7")) // oldest (i=0) not among the last 10
        assertTrue(text.contains("Gut drauf"))
        assertTrue(text.contains("Fortschritt der Hauptübungen"))
        assertTrue(text.lines().any { it.startsWith("Bench: ") && it.contains(" → ") })
        // the answer instructions with the share example come last
        assertTrue(text.trimEnd().endsWith("Höchstens zwei Sätze Erklärung außerhalb des Blocks."))
    }

    @Test
    fun exampleInTheContextImportsAgain() {
        val text = ChatContext.build(input(emptyList(), Language.EN, memory = null), ChatEnglish, AiPrompt.EXAMPLE)
        assertTrue(text.contains("No finished workouts yet."))
        assertFalse(text.contains("Coach memory"))
        assertTrue(ShareFormat.parseInput(text) is ShareParse.Ok) // the ```json block inside is the share format
    }

    @Test
    fun hugeHistoryStaysUnderTheLimitAndKeepsTheTask() {
        val many = (0 until 10).map { i -> record("w$i", base + i * day, sets = List(400) { SetValues(60.0, 8, null) }) }
        val text = ChatContext.build(input(many), ChatGerman, AiPrompt.EXAMPLE)
        assertTrue(text.length <= ChatContext.MAX_CHARS + 50)
        assertTrue(text.trimEnd().endsWith("außerhalb des Blocks."))
    }

    @Test
    fun gemInstructionsContainTheFormat() {
        val g = ChatEnglish.gemInstructions(AiPrompt.EXAMPLE)
        assertTrue(g.contains("\"kind\": \"template\""))
        assertTrue(ShareFormat.parseInput(g) is ShareParse.Ok)
    }
}
