package app.overfit.coach

import app.overfit.share.AiPrompt
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CoachChatParserTest {
    private fun reply(text: String, workout: String) = buildJsonObject { put("reply", text); put("workout", workout) }.toString()

    @Test
    fun emptyWorkoutStringMeansNoWorkout() {
        val a = CoachChatParser.parse("""{"reply":"Hi","workout":""}""")
        assertEquals("Hi", a.text)
        assertNull(a.workoutJson)
    }

    @Test
    fun workoutAsJsonString() {
        val a = CoachChatParser.parse(reply("Hier dein Plan", AiPrompt.EXAMPLE))
        assertEquals("Hier dein Plan", a.text)
        val json = assertNotNull(a.workoutJson)
        assertEquals("Oberkörper A", assertNotNull(CoachChatParser.workout(json)).name)
    }

    @Test
    fun workoutAsNestedObject() {
        val nested = Json.parseToJsonElement(AiPrompt.EXAMPLE)
        val raw = buildJsonObject { put("reply", "ok"); put("workout", nested) }.toString()
        val a = CoachChatParser.parse(raw)
        assertEquals("ok", a.text)
        assertEquals("Oberkörper A", assertNotNull(CoachChatParser.workout(a.workoutJson)).name)
    }

    @Test
    fun invalidWorkoutStringIsNull() {
        val a = CoachChatParser.parse(reply("Text", "not a workout"))
        assertEquals("Text", a.text)
        assertNull(a.workoutJson)
        assertNull(CoachChatParser.parse("""{"reply":"Text","workout":"{\"v\":1}"}""").workoutJson)
    }

    @Test
    fun plainTextWithFencedBlockSplitsOff() {
        val raw = "Probier das:\n\n```overfit\n${AiPrompt.EXAMPLE}\n```\n\nViel Spaß!"
        val a = CoachChatParser.parse(raw)
        assertFalse(a.text.contains("```"))
        assertTrue(a.text.contains("Probier das:"))
        assertTrue(a.text.contains("Viel Spaß!"))
        assertEquals("Oberkörper A", assertNotNull(CoachChatParser.workout(a.workoutJson)).name)
    }

    @Test
    fun plainTextWithoutBlockStaysWhole() {
        val a = CoachChatParser.parse("  Einfach nur Text.  ")
        assertEquals("Einfach nur Text.", a.text)
        assertNull(a.workoutJson)
        val b = CoachChatParser.parse("Code:\n```\nval x = 1\n```")
        assertTrue(b.text.contains("val x = 1"))
        assertNull(b.workoutJson)
    }

    @Test
    fun workoutOfNullOrGarbageIsNull() {
        assertNull(CoachChatParser.workout(null))
        assertNull(CoachChatParser.workout("garbage"))
    }
}
