package app.overfit.ai

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProvidersTest {
    private fun providers(status: HttpStatusCode, seen: MutableList<io.ktor.client.request.HttpRequestData>? = null) =
        Providers(HttpClient(MockEngine { request -> seen?.add(request); respond("{}", status) }))

    @Test
    fun mapsStatusCodes() = runTest {
        assertEquals(KeyValidation.OK, providers(HttpStatusCode.OK).get(ProviderId.OPENROUTER).validateKey("k"))
        assertEquals(KeyValidation.INVALID, providers(HttpStatusCode.Unauthorized).get(ProviderId.OPENAI).validateKey("k"))
        assertEquals(KeyValidation.INVALID, providers(HttpStatusCode.Forbidden).get(ProviderId.ANTHROPIC).validateKey("k"))
        assertEquals(KeyValidation.NETWORK, providers(HttpStatusCode.InternalServerError).get(ProviderId.OPENAI).validateKey("k"))
    }

    @Test
    fun networkFailureIsReported() = runTest {
        val p = Providers(HttpClient(MockEngine { error("offline") }))
        assertEquals(KeyValidation.NETWORK, p.get(ProviderId.OPENROUTER).validateKey("k"))
    }

    @Test
    fun anthropicUsesApiKeyHeaders() = runTest {
        val seen = mutableListOf<io.ktor.client.request.HttpRequestData>()
        providers(HttpStatusCode.OK, seen).get(ProviderId.ANTHROPIC).validateKey("sk-ant-x")
        val request = seen.single()
        assertEquals("https://api.anthropic.com/v1/models?limit=1", request.url.toString())
        assertEquals("sk-ant-x", request.headers["x-api-key"])
        assertEquals("2023-06-01", request.headers["anthropic-version"])
    }

    @Test
    fun bearerProvidersSendAuthorization() = runTest {
        val seen = mutableListOf<io.ktor.client.request.HttpRequestData>()
        providers(HttpStatusCode.OK, seen).get(ProviderId.OPENROUTER).validateKey("sk-or-x")
        assertEquals("Bearer sk-or-x", seen.single().headers["Authorization"])
    }

    @Test
    fun geminiIsListedAndMapsStatusCodes() = runTest {
        assertTrue(ProviderId.GEMINI in providers(HttpStatusCode.OK).all.map { it.id })
        assertEquals(KeyValidation.OK, providers(HttpStatusCode.OK).get(ProviderId.GEMINI).validateKey("k"))
        // Google answers an invalid key with 400 API_KEY_INVALID.
        assertEquals(KeyValidation.INVALID, providers(HttpStatusCode.BadRequest).get(ProviderId.GEMINI).validateKey("k"))
        assertEquals(KeyValidation.INVALID, providers(HttpStatusCode.Unauthorized).get(ProviderId.GEMINI).validateKey("k"))
        assertEquals(KeyValidation.NETWORK, providers(HttpStatusCode.InternalServerError).get(ProviderId.GEMINI).validateKey("k"))
        // 400 stays a network/other problem for the other providers
        assertEquals(KeyValidation.NETWORK, providers(HttpStatusCode.BadRequest).get(ProviderId.OPENAI).validateKey("k"))
    }

    @Test
    fun geminiRequestGoesToOpenAiCompatibleModelsWithBearer() = runTest {
        val seen = mutableListOf<io.ktor.client.request.HttpRequestData>()
        providers(HttpStatusCode.OK, seen).get(ProviderId.GEMINI).validateKey("AIza-x")
        val request = seen.single()
        assertEquals("https://generativelanguage.googleapis.com/v1beta/openai/models", request.url.toString())
        assertEquals("Bearer AIza-x", request.headers["Authorization"])
    }
}
