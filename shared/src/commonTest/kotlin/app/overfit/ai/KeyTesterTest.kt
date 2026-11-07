package app.overfit.ai

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** "Test" in the model picker: free model list straight from the provider, never through any other host. */
class KeyTesterTest {
    private val seen = mutableListOf<HttpRequestData>()
    private val json = headersOf(HttpHeaders.ContentType, "application/json")

    private fun tester(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) =
        KeyTester(HttpClient(MockEngine { req -> seen += req; handler(req) }))

    private fun MockRequestHandleScope.ok(body: String) = respond(body, HttpStatusCode.OK, json)

    private fun assertOnlyHosts(vararg hosts: String) =
        assertTrue(seen.all { it.url.host in hosts }, "unexpected host in ${seen.map { it.url.host }}")

    @Test
    fun anthropicListsModelsWithTheKeyHeader() = runTest {
        val r = tester { ok("""{"data":[{"id":"claude-sonnet-5-5"},{"id":"claude-haiku-5-5"}]}""") }.test(ProviderId.ANTHROPIC, " sk-ant-1 ")
        assertEquals(KeyTestResult.Ok(listOf("claude-sonnet-5-5", "claude-haiku-5-5")), r)
        val req = seen.single()
        assertEquals("api.anthropic.com", req.url.host)
        assertEquals("/v1/models", req.url.encodedPath)
        assertEquals("sk-ant-1", req.headers["x-api-key"])
        assertEquals("2023-06-01", req.headers["anthropic-version"])
        assertOnlyHosts("api.anthropic.com")
    }

    @Test
    fun openAiKeepsOnlyChatModels() = runTest {
        val r = tester {
            ok("""{"data":[{"id":"whisper-1"},{"id":"text-embedding-3-large"},{"id":"gpt-5.5"},{"id":"gpt-image-1"},{"id":"o4-mini"}]}""")
        }.test(ProviderId.OPENAI, "sk-1")
        val models = (r as KeyTestResult.Ok).models
        assertTrue("gpt-5.5" in models)
        assertTrue("o4-mini" in models)
        assertTrue(models.none { it.startsWith("whisper") || it.startsWith("text-embedding") || it.contains("image") }, models.toString())
        assertEquals("Bearer sk-1", seen.single().headers[HttpHeaders.Authorization])
        assertOnlyHosts("api.openai.com")
    }

    @Test
    fun googleFiltersByGenerationMethodAndDropsThePrefix() = runTest {
        val r = tester {
            ok(
                """{"models":[
                {"name":"models/gemini-3.6-flash","supportedGenerationMethods":["generateContent","countTokens"]},
                {"name":"models/text-embedding-005","supportedGenerationMethods":["embedContent"]}
                ]}""",
            )
        }.test(ProviderId.GEMINI, "g-1")
        assertEquals(KeyTestResult.Ok(listOf("gemini-3.6-flash")), r)
        assertEquals("g-1", seen.single().headers["x-goog-api-key"])
        assertOnlyHosts("generativelanguage.googleapis.com")
    }

    @Test
    fun googleBadRequestMeansInvalidKey() = runTest {
        assertEquals(KeyTestResult.Invalid, tester { respond("""{"error":{"status":"INVALID_ARGUMENT"}}""", HttpStatusCode.BadRequest, json) }.test(ProviderId.GEMINI, "bad"))
    }

    @Test
    fun openRouterChecksTheKeyFirst() = runTest {
        assertEquals(
            KeyTestResult.Invalid,
            tester { req -> if (req.url.encodedPath == "/api/v1/key") respond("{}", HttpStatusCode.Unauthorized, json) else ok("""{"data":[{"id":"x"}]}""") }
                .test(ProviderId.OPENROUTER, "or-bad"),
        )
        assertEquals(listOf("/api/v1/key"), seen.map { it.url.encodedPath })
        seen.clear()
        val r = tester { req ->
            if (req.url.encodedPath == "/api/v1/key") ok("""{"data":{"label":"k"}}""") else ok("""{"data":[{"id":"anthropic/claude-sonnet-5.5"},{"id":"openai/gpt-5.5"}]}""")
        }.test(ProviderId.OPENROUTER, "or-ok")
        assertEquals(2, (r as KeyTestResult.Ok).models.size)
        assertEquals(listOf("/api/v1/key", "/api/v1/models"), seen.map { it.url.encodedPath })
        assertOnlyHosts("openrouter.ai")
    }

    @Test
    fun mapsStatusCodes() = runTest {
        assertEquals(KeyTestResult.Invalid, tester { respond("{}", HttpStatusCode.Unauthorized, json) }.test(ProviderId.ANTHROPIC, "k"))
        assertEquals(KeyTestResult.Invalid, tester { respond("{}", HttpStatusCode.Forbidden, json) }.test(ProviderId.OPENAI, "k"))
        assertEquals(KeyTestResult.NoCredit, tester { respond("{}", HttpStatusCode.PaymentRequired, json) }.test(ProviderId.ANTHROPIC, "k"))
        assertEquals(KeyTestResult.RateLimited, tester { respond("{}", HttpStatusCode.TooManyRequests, json) }.test(ProviderId.OPENAI, "k"))
        assertEquals(KeyTestResult.Failed(500), tester { respond("{}", HttpStatusCode.InternalServerError, json) }.test(ProviderId.ANTHROPIC, "k"))
    }

    @Test
    fun exceptionMeansNetwork() = runTest {
        assertEquals(KeyTestResult.Network, tester { error("offline") }.test(ProviderId.ANTHROPIC, "k"))
    }

    @Test
    fun emptyKeySendsNothing() = runTest {
        assertEquals(KeyTestResult.Empty, tester { ok("{}") }.test(ProviderId.ANTHROPIC, "   "))
        assertTrue(seen.isEmpty())
    }

    @Test
    fun everyProviderOnlyTalksToItsOwnHost() = runTest {
        val hosts = mapOf(
            ProviderId.ANTHROPIC to "api.anthropic.com",
            ProviderId.OPENAI to "api.openai.com",
            ProviderId.GEMINI to "generativelanguage.googleapis.com",
            ProviderId.OPENROUTER to "openrouter.ai",
        )
        hosts.forEach { (provider, host) ->
            seen.clear()
            tester { ok("""{"data":[],"models":[]}""") }.test(provider, "k")
            assertTrue(seen.isNotEmpty())
            assertOnlyHosts(host)
        }
    }
}
