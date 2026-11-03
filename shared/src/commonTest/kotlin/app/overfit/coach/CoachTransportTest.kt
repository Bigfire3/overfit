package app.overfit.coach

import app.overfit.ai.FuelProvider
import app.overfit.fuel.FuelAccount
import app.overfit.fuel.FuelConfig
import app.overfit.fuel.FuelException
import app.overfit.fuel.FuelMessageResult
import app.overfit.fuel.FuelService
import app.overfit.fuel.LedgerPage
import app.overfit.fuel.SpotRedeemResult
import app.overfit.fuel.SpotToken
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.ByteArrayContent
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

class CoachTransportTest {
    private val prompt = CoachPrompt("STATIC", "DYNAMIC", "USER")
    private val anthropicOk = """{"content":[{"type":"text","text":"{\"a\":1}"}],"stop_reason":"end_turn"}"""
    private val chatOk = """{"choices":[{"message":{"content":"hello"},"finish_reason":"stop"}]}"""

    private class Recorder {
        val requests = mutableListOf<HttpRequestData>()
        val bodies = mutableListOf<JsonObject>()
        val last: HttpRequestData get() = requests.last()
        val body: JsonObject get() = bodies.last()
    }

    private fun bodyText(r: HttpRequestData): String = when (val b = r.body) {
        is TextContent -> b.text
        is ByteArrayContent -> b.bytes().decodeToString()
        else -> fail("unexpected body type ${b::class}")
    }

    private fun client(rec: Recorder = Recorder(), status: HttpStatusCode = HttpStatusCode.OK, response: String): HttpClient =
        HttpClient(MockEngine { request ->
            rec.requests += request
            rec.bodies += Json.parseToJsonElement(bodyText(request)).jsonObject
            respond(response, status, headersOf(HttpHeaders.ContentType, "application/json"))
        })

    private suspend fun errorKind(block: suspend () -> Unit): CoachErrorKind {
        try {
            block()
        } catch (e: CoachException) {
            return e.kind
        }
        fail("expected CoachException")
    }

    // ---- Anthropic ----

    @Test
    fun anthropicRequestShape() = runTest {
        val rec = Recorder()
        val reply = AnthropicCoach(client(rec, response = anthropicOk)) { "sk-ant" }.send(prompt, "claude-sonnet-5-5")
        assertEquals("""{"a":1}""", reply.text)
        assertEquals("https://api.anthropic.com/v1/messages", rec.last.url.toString())
        assertEquals("sk-ant", rec.last.headers["x-api-key"])
        assertEquals("2023-06-01", rec.last.headers["anthropic-version"])
        val b = rec.body
        assertEquals("claude-sonnet-5-5", b["model"]!!.jsonPrimitive.content)
        assertEquals(8000, b["max_tokens"]!!.jsonPrimitive.content.toInt())
        val system = b["system"]!!.jsonArray
        assertEquals("ephemeral", system[0].jsonObject["cache_control"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals("STATIC", system[0].jsonObject["text"]!!.jsonPrimitive.content)
        assertFalse(system[1].jsonObject.containsKey("cache_control"))
        val oc = b["output_config"]!!.jsonObject
        assertEquals("json_schema", oc["format"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals("medium", oc["effort"]!!.jsonPrimitive.content)
        assertFalse(b.containsKey("tool_choice"))
    }

    @Test
    fun anthropicEffortOnlyForNewModels() = runTest {
        val rec = Recorder()
        AnthropicCoach(client(rec, response = anthropicOk)) { "k" }.send(prompt, "claude-haiku-4-5")
        assertFalse(rec.body["output_config"]!!.jsonObject.containsKey("effort"))
        assertTrue(rec.body["output_config"]!!.jsonObject.containsKey("format"))
    }

    @Test
    fun anthropicUnstructuredOmitsFormat() = runTest {
        val rec = Recorder()
        AnthropicCoach(client(rec, response = anthropicOk)) { "k" }.send(prompt, "claude-haiku-4-5", structured = false)
        assertFalse(rec.body.containsKey("output_config"))
        AnthropicCoach(client(rec, response = anthropicOk)) { "k" }.send(prompt, "claude-sonnet-5-5", structured = false)
        val oc = rec.body["output_config"]!!.jsonObject
        assertFalse(oc.containsKey("format"))
        assertEquals("medium", oc["effort"]!!.jsonPrimitive.content)
    }

    @Test
    fun anthropicImagesAreBase64Blocks() = runTest {
        val rec = Recorder()
        val p = prompt.copy(images = listOf(CoachImage("today", byteArrayOf(1, 2, 3))))
        AnthropicCoach(client(rec, response = anthropicOk)) { "k" }.send(p, "claude-sonnet-5-5")
        val content = rec.body["messages"]!!.jsonArray[0].jsonObject["content"]!!.jsonArray
        assertEquals("Photo: today", content[0].jsonObject["text"]!!.jsonPrimitive.content)
        val img = content[1].jsonObject
        assertEquals("image", img["type"]!!.jsonPrimitive.content)
        val src = img["source"]!!.jsonObject
        assertEquals("base64", src["type"]!!.jsonPrimitive.content)
        assertEquals("image/jpeg", src["media_type"]!!.jsonPrimitive.content)
        assertEquals("AQID", src["data"]!!.jsonPrimitive.content)
        assertEquals("USER", content[2].jsonObject["text"]!!.jsonPrimitive.content)
    }

    @Test
    fun anthropicReplyParsing() = runTest {
        suspend fun reply(body: String) = AnthropicCoach(client(response = body)) { "k" }.send(prompt, "m")
        assertEquals("ab", reply("""{"content":[{"type":"text","text":"a"},{"type":"text","text":"b"}]}""").text)
        assertEquals("""{"rating":5}""", reply("""{"content":[{"type":"tool_use","input":{"rating":5}}]}""").text)
        val cut = reply("""{"content":[{"type":"text","text":"{"}],"stop_reason":"max_tokens"}""")
        assertTrue(cut.truncated)
        assertFalse(cut.refused)
        assertTrue(reply("""{"content":[],"stop_reason":"refusal"}""").refused)
    }

    @Test
    fun anthropicStatusErrors() = runTest {
        suspend fun kind(status: HttpStatusCode) = errorKind {
            AnthropicCoach(client(status = status, response = """{"error":"x"}""")) { "k" }.send(prompt, "m")
        }
        assertEquals(CoachErrorKind.AUTH, kind(HttpStatusCode.Unauthorized))
        assertEquals(CoachErrorKind.RATE_LIMIT, kind(HttpStatusCode.TooManyRequests))
        assertEquals(CoachErrorKind.SERVER, kind(HttpStatusCode.InternalServerError))
        assertEquals(CoachErrorKind.BAD_REQUEST, kind(HttpStatusCode.BadRequest))
    }

    @Test
    fun networkFailureAndMissingKey() = runTest {
        val failing = HttpClient(MockEngine { throw IllegalStateException("offline") })
        assertEquals(CoachErrorKind.NETWORK, errorKind { AnthropicCoach(failing) { "k" }.send(prompt, "m") })
        assertEquals(CoachErrorKind.NO_ACCESS, errorKind { AnthropicCoach(client(response = anthropicOk)) { null }.send(prompt, "m") })
        assertEquals(CoachErrorKind.NO_ACCESS, errorKind { OpenAiCoach(client(response = chatOk)) { null }.send(prompt, "m") })
    }

    // ---- OpenAI / OpenRouter ----

    @Test
    fun openAiRequestAndReply() = runTest {
        val rec = Recorder()
        val reply = OpenAiCoach(client(rec, response = chatOk)) { "sk-x" }.send(prompt, "gpt-5")
        assertEquals("hello", reply.text)
        assertFalse(reply.truncated)
        assertEquals("https://api.openai.com/v1/chat/completions", rec.last.url.toString())
        assertEquals("Bearer sk-x", rec.last.headers[HttpHeaders.Authorization])
        val b = rec.body
        assertEquals(8000, b["max_completion_tokens"]!!.jsonPrimitive.content.toInt())
        assertFalse(b.containsKey("max_tokens"))
        val rf = b["response_format"]!!.jsonObject
        assertEquals("json_schema", rf["type"]!!.jsonPrimitive.content)
        assertEquals("true", rf["json_schema"]!!.jsonObject["strict"]!!.jsonPrimitive.content)
        val sys = b["messages"]!!.jsonArray[0].jsonObject["content"]!!.jsonArray[0].jsonObject
        assertFalse(sys.containsKey("cache_control"))
    }

    @Test
    fun openAiFinishLengthIsTruncated() = runTest {
        val body = """{"choices":[{"message":{"content":"{"},"finish_reason":"length"}]}"""
        assertTrue(OpenAiCoach(client(response = body)) { "k" }.send(prompt, "gpt-5").truncated)
    }

    @Test
    fun openRouterRequestAndBodyError() = runTest {
        val rec = Recorder()
        OpenRouterCoach(client(rec, response = chatOk)) { "k" }.send(prompt, "anthropic/claude-sonnet-5.5")
        assertEquals("https://openrouter.ai/api/v1/chat/completions", rec.last.url.toString())
        assertEquals(8000, rec.body["max_tokens"]!!.jsonPrimitive.content.toInt())
        assertFalse(rec.body.containsKey("max_completion_tokens"))
        val sys = rec.body["messages"]!!.jsonArray[0].jsonObject["content"]!!.jsonArray[0].jsonObject
        assertEquals("ephemeral", sys["cache_control"]!!.jsonObject["type"]!!.jsonPrimitive.content)

        val err = """{"error":{"code":429,"message":"slow down"}}"""
        assertEquals(CoachErrorKind.RATE_LIMIT, errorKind { OpenRouterCoach(client(response = err)) { "k" }.send(prompt, "m") })
    }

    // ---- Fuel ----

    private class FakeFuel(private val onMessages: (String) -> FuelMessageResult) : FuelService {
        var lastBody: String? = null
        override val isDemo = false
        override suspend fun config(): FuelConfig = error("unused")
        override suspend fun account(): FuelAccount = error("unused")
        override suspend fun setDisplayName(name: String?): String? = error("unused")
        override suspend fun ledger(before: Long?, limit: Int): LedgerPage = error("unused")
        override suspend fun createRecoveryCode(): String = error("unused")
        override suspend fun restore(code: String): FuelAccount = error("unused")
        override suspend fun spotToken(): SpotToken = error("unused")
        override suspend fun redeemSpot(token: String): SpotRedeemResult = error("unused")
        override suspend fun messages(bodyJson: String): FuelMessageResult {
            lastBody = bodyJson
            return onMessages(bodyJson)
        }
    }

    private val fuelAnswer = buildJsonObject {
        putJsonArray("content") { add(buildJsonObject { put("type", "text"); put("text", "fuel text") }) }
        put("stop_reason", JsonPrimitive("end_turn"))
    }

    @Test
    fun fuelSuccessUsesFixedModelAndReportsCost() = runTest {
        val fake = FakeFuel { FuelMessageResult(fuelAnswer, 30_000, 4_170_000) }
        val reply = FuelCoach(FuelProvider { fake }).send(prompt, "claude-haiku-4-5")
        assertEquals("fuel text", reply.text)
        assertEquals(30_000L, reply.costMicro)
        assertEquals(4_170_000L, reply.balanceMicro)
        val sent = Json.parseToJsonElement(fake.lastBody!!).jsonObject
        assertEquals("claude-sonnet-5-5", sent["model"]!!.jsonPrimitive.content)
    }

    @Test
    fun fuelErrors() = runTest {
        suspend fun kind(e: Exception) = errorKind {
            FuelCoach(FuelProvider { FakeFuel { throw e } }).send(prompt, "m")
        }
        assertEquals(CoachErrorKind.NO_FUEL, kind(FuelException("insufficient_funds", 402)))
        assertEquals(CoachErrorKind.NETWORK, kind(FuelException("network")))
        assertEquals(CoachErrorKind.NETWORK, kind(RuntimeException("boom")))
        assertEquals(CoachErrorKind.RATE_LIMIT, kind(FuelException("x", 429)))
        assertEquals(CoachErrorKind.SERVER, kind(FuelException("x", 502)))
        assertEquals(CoachErrorKind.AUTH, kind(FuelException("x", 401)))
        assertEquals(CoachErrorKind.BAD_REQUEST, kind(FuelException("x", 400)))
    }

    @Test
    fun fuelWithoutServiceIsNoAccess() = runTest {
        assertEquals(CoachErrorKind.NO_ACCESS, errorKind { FuelCoach(FuelProvider { null }).send(prompt, "m") })
    }
}
