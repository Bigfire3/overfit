package app.overfit.coach

import app.overfit.i18n.GermanCoachHub
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CoachPromptLibraryTest {
    private val h = GermanCoachHub
    private val src = PromptSources(
        contextWithTask = "CTX+TASK",
        contextOnly = "CTX ONLY\n\n",
        gem = "GEM TEXT",
        createWorkout = "CREATE SOURCE",
    )
    private val entries = CoachPromptLibrary.all(h, src)
    private fun entry(id: String) = entries.first { it.id == id }

    @Test
    fun hasSevenEntriesWithUniqueIds() {
        assertEquals(7, entries.size)
        assertEquals(7, entries.map { it.id }.toSet().size)
        assertEquals(
            setOf(
                CoachPromptLibrary.CREATE_WORKOUT, CoachPromptLibrary.CONTEXT, CoachPromptLibrary.GEM, CoachPromptLibrary.REVIEW,
                CoachPromptLibrary.PROGRESS, CoachPromptLibrary.WEEK, CoachPromptLibrary.PLATEAU,
            ),
            entries.map { it.id }.toSet(),
        )
    }

    @Test
    fun reviewTextIsContextPlusAsk() {
        val r = entry(CoachPromptLibrary.REVIEW)
        assertTrue(r.text.startsWith(src.contextOnly.trimEnd()))
        assertTrue(r.text.endsWith(h.promptReviewAsk))
        assertEquals(h.promptReviewAsk, r.chatAsk)
        assertEquals(src.contextOnly.trimEnd() + "\n\n" + h.promptReviewAsk, r.text)
    }

    @Test
    fun contextAndGemHaveNoChatAsk() {
        assertNull(entry(CoachPromptLibrary.CONTEXT).chatAsk)
        assertNull(entry(CoachPromptLibrary.GEM).chatAsk)
        assertEquals("CTX+TASK", entry(CoachPromptLibrary.CONTEXT).text)
        assertEquals("GEM TEXT", entry(CoachPromptLibrary.GEM).text)
    }

    @Test
    fun createWorkoutUsesItsSource() {
        val c = entry(CoachPromptLibrary.CREATE_WORKOUT)
        assertEquals(src.createWorkout, c.text)
        assertEquals(h.promptCreateWorkoutAsk, assertNotNull(c.chatAsk))
    }
}
