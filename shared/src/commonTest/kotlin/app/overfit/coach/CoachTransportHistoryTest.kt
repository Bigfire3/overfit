package app.overfit.coach

import app.overfit.ai.ProviderId
import io.ktor.client.HttpClient
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

class CoachTransportHistoryTest {
    private val prompt = CoachPrompt(
        "STATIC", "DYNAMIC", "NOW",
        history = listOf(PromptTurn(true, "Q1"), PromptTurn(false, "A1")),
    )

    private fun roles(messages: kotlinx.serialization.json.JsonArray) = messages.map { it.jsonObject["role"]!!.jsonPrimitive.content }

    @Test
    fun anthropicSendsHistoryBeforeTheQuestion() {
        val messages = anthropicBody(prompt, "claude-sonnet-5-5", structured = true)["messages"]!!.jsonArray
        assertEquals(listOf("user", "assistant", "user"), roles(messages))
        assertEquals("Q1", messages[0].jsonObject["content"]!!.jsonPrimitive.content)
        assertEquals("A1", messages[1].jsonObject["content"]!!.jsonPrimitive.content)
        val last = messages[2].jsonObject["content"]!!.jsonArray
        assertEquals("NOW", last.last().jsonObject["text"]!!.jsonPrimitive.content)
    }

    @Test
    fun chatCompletionsSendsSystemHistoryAndQuestion() {
        val coach = OpenAiCoach(HttpClient(), { "k" })
        val messages = coach.body(prompt, "gpt-5", structured = true)["messages"]!!.jsonArray
        assertEquals(listOf("system", "user", "assistant", "user"), roles(messages))
        assertEquals("Q1", messages[1].jsonObject["content"]!!.jsonPrimitive.content)
        assertEquals("A1", messages[2].jsonObject["content"]!!.jsonPrimitive.content)
        val last = messages[3].jsonObject["content"]!!.jsonArray
        assertEquals("NOW", last.last().jsonObject["text"]!!.jsonPrimitive.content)
        assertEquals(ProviderId.OPENAI, coach.providerId)
    }

    @Test
    fun withoutHistoryOnlyTheQuestionIsSent() {
        val messages = anthropicBody(prompt.copy(history = emptyList()), "m", structured = false)["messages"]!!.jsonArray
        assertEquals(listOf("user"), roles(messages))
    }
}
