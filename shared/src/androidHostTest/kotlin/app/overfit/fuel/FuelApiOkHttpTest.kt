package app.overfit.fuel

import app.overfit.platform.createHttpClient
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import java.net.InetSocketAddress
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * FuelApi with the real OkHttp engine against a local server. Regression (8.11.): Ktor's OkHttp engine only takes over
 * the HttpTimeout values that are set; with only requestTimeoutMillis, OkHttp's 10 s read timeout cut off every answer
 * that took longer (Opus) and the app showed "No connection".
 */
class FuelApiOkHttpTest {
    private class FakeKey : DeviceKey {
        private val point = ByteArray(65) { if (it == 0) 4 else (it * 3).toByte() }
        override fun publicPoint() = point
        override fun sign(message: ByteArray): ByteArray = ByteArray(64) { 7 }
        override fun delete() = Unit
    }

    private var server: HttpServer? = null

    /** Answers /v1/devices at once and everything else after [delayMs]. */
    private fun serve(delayMs: Long): String {
        val s = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        s.executor = java.util.concurrent.Executors.newCachedThreadPool()
        s.createContext("/") { ex ->
            val path = ex.requestURI.path
            val body = when (path) {
                "/v1/devices" -> """{"accountId":"a1","balanceMicro":0}"""
                "/v1/messages" -> """{"content":[],"overfit":{"costMicro":1,"balanceMicro":2}}"""
                else -> """{"accountId":"a1","balanceMicro":4200000}"""
            }.encodeToByteArray()
            ex.requestBody.readBytes()
            try {
                if (path != "/v1/devices") Thread.sleep(delayMs)
                ex.responseHeaders.add("Content-Type", "application/json")
                ex.sendResponseHeaders(200, body.size.toLong())
                ex.responseBody.use { it.write(body) }
            } catch (_: Exception) {
                // client gave up
            } finally {
                ex.close()
            }
        }
        s.start()
        server = s
        return "http://127.0.0.1:${s.address.port}"
    }

    @AfterTest
    fun stop() { server?.stop(0) }

    @Test
    fun answerAfterMoreThanTenSecondsArrives() = runBlocking {
        val api = FuelApi(createHttpClient(), serve(delayMs = 11_500), RequestSigner(FakeKey()))
        assertEquals(2, api.messages("""{"model":"m","max_tokens":10,"messages":[]}""").balanceMicro)
    }

    @Test
    fun tooSlowAnswerIsTimeoutNotNetwork() = runBlocking {
        val api = FuelApi(createHttpClient(), serve(delayMs = 3_000), RequestSigner(FakeKey()), requestTimeoutMs = 500, messagesTimeoutMs = 500)
        assertEquals("timeout", assertFailsWith<FuelException> { api.account() }.code)
        assertEquals("timeout", assertFailsWith<FuelException> { api.messages("""{"model":"m","max_tokens":10,"messages":[]}""") }.code)
    }

    @Test
    fun nothingListeningIsNetwork() = runBlocking {
        val port = java.net.ServerSocket(0).use { it.localPort }
        val api = FuelApi(createHttpClient(), "http://127.0.0.1:$port", RequestSigner(FakeKey()))
        assertEquals("network", assertFailsWith<FuelException> { api.account() }.code)
    }
}
