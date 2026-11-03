package app.overfit.coach

import app.overfit.data.db.CoachChatMessageEntity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CoachChatPromptTest {
    private val parts = ChatPromptParts(
        instructions = "  COACH ROLE  ",
        profileBlock = "PROFILE BLOCK",
        context = "CONTEXT BLOCK",
        exerciseList = listOf("wg:bench" to "Bench Press", "wg:row" to "Row"),
    )

    private fun msg(id: String, fromUser: Boolean, text: String, status: String = "DONE", demo: Boolean = false, workoutJson: String? = null, at: Long = 0) =
        CoachChatMessageEntity(
            id = id, chatId = "c", fromUser = fromUser, text = text, status = status, createdAt = at,
            workoutJson = workoutJson, demo = demo,
        )

    @Test
    fun buildSplitsStaticAndDynamicSystem() {
        val p = CoachChatPrompt.build(parts, emptyList(), "Frage?")
        assertTrue(p.staticSystem.startsWith("COACH ROLE"))
        assertTrue(p.staticSystem.contains(CoachChatSchema.RULES))
        assertTrue(p.staticSystem.contains("wg:bench | Bench Press"))
        assertTrue(p.staticSystem.contains("wg:row | Row"))
        assertFalse(p.staticSystem.contains("PROFILE BLOCK"))
        assertFalse(p.staticSystem.contains("CONTEXT BLOCK"))
        assertTrue(p.dynamicSystem.contains("PROFILE BLOCK"))
        assertTrue(p.dynamicSystem.contains("CONTEXT BLOCK"))
        assertTrue(p.dynamicSystem.indexOf("PROFILE BLOCK") < p.dynamicSystem.indexOf("CONTEXT BLOCK"))
        assertEquals("Frage?", p.user)
    }

    @Test
    fun buildSetsSchemaValues() {
        val p = CoachChatPrompt.build(parts, emptyList(), "q")
        assertEquals(CoachChatSchema.schema, p.outputSchema)
        assertEquals(CoachChatSchema.NAME, p.outputSchemaName)
        assertEquals(CoachChatSchema.MAX_TOKENS, p.outputMaxTokens)
        assertTrue(p.history.isEmpty())
    }

    @Test
    fun historyIsCappedToTheNewestTurns() {
        val turns = (0 until CoachChatPrompt.MAX_HISTORY + 6).map { PromptTurn(it % 2 == 0, "t$it") }
        val p = CoachChatPrompt.build(parts, turns, "q")
        assertEquals(CoachChatPrompt.MAX_HISTORY, p.history.size)
        assertEquals(turns.takeLast(CoachChatPrompt.MAX_HISTORY), p.history)
    }

    @Test
    fun historyKeepsFinishedExchanges() {
        val turns = CoachChatPrompt.history(
            listOf(msg("1", true, "Q1"), msg("2", false, "A1"), msg("3", true, "Q2"), msg("4", false, "A2")),
        )
        assertEquals(listOf(true to "Q1", false to "A1", true to "Q2", false to "A2"), turns.map { it.fromUser to it.text })
    }

    @Test
    fun historyDropsDemoAnswersAndTheirQuestion() {
        val turns = CoachChatPrompt.history(
            listOf(msg("1", true, "Q1"), msg("2", false, "demo", demo = true), msg("3", true, "Q2"), msg("4", false, "A2")),
        )
        // Q1 loses its answer, so it is dropped as well; Q2/A2 stay.
        assertEquals(listOf(true to "Q2", false to "A2"), turns.map { it.fromUser to it.text })
    }

    @Test
    fun historyDropsFailedAndSendingAnswersAndTheQuestionBefore() {
        val turns = CoachChatPrompt.history(
            listOf(
                msg("1", true, "Q1"), msg("2", false, "", status = "FAILED"),
                msg("3", true, "Q2"), msg("4", false, "A2"),
                msg("5", true, "Q3"), msg("6", false, "", status = "SENDING"),
            ),
        )
        assertEquals(listOf(true to "Q2", false to "A2"), turns.map { it.fromUser to it.text })
    }

    @Test
    fun historyAppendsWorkoutAsOverfitBlock() {
        val json = """{"v":1,"kind":"template"}"""
        val turns = CoachChatPrompt.history(
            listOf(msg("1", true, "Q"), msg("2", false, "Plan", workoutJson = json)),
        )
        assertEquals(2, turns.size)
        assertTrue(turns[1].text.startsWith("Plan"))
        assertTrue(turns[1].text.contains("```overfit"))
        assertTrue(turns[1].text.contains(json))
    }
}
