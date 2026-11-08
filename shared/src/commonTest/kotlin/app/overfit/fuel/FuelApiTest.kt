package app.overfit.fuel

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

private class FakeKey : DeviceKey {
    private val point = ByteArray(65) { if (it == 0) 4 else (it * 3).toByte() }
    var signed = mutableListOf<String>()
    override fun publicPoint() = point
    override fun sign(message: ByteArray): ByteArray { signed += message.decodeToString(); return ByteArray(64) { 7 } }
    override fun delete() = Unit
}

private val json = headersOf(HttpHeaders.ContentType, "application/json")
// Contains fields an older backend still sends (expiring, invite): ignored.
private const val ACCOUNT = """{"accountId":"a1","balanceMicro":4200000,"expiring":[{"amountMicro":1000000,"expiresAt":1777777777}],"displayName":"Fabian","hasRecoveryCode":false,"invite":{"invitedThisMonth":2,"monthlyLimit":10,"redeemedStarter":false}}"""
private fun err(code: String, details: String = "{}") = """{"error":{"code":"$code","message":"m","details":$details}}"""

class FuelApiTest {
    private fun api(
        key: FakeKey = FakeKey(),
        log: MutableList<HttpRequestData> = mutableListOf(),
        handler: suspend (HttpRequestData) -> Pair<HttpStatusCode, String>,
    ): FuelApi {
        var n = 0
        val engine = MockEngine { req ->
            log += req
            val (status, body) = handler(req)
            respond(body, status, json)
        }
        return FuelApi(HttpClient(engine), "https://fuel.test/", RequestSigner(key), nowSec = { 1_762_128_000 }, nonce = { "nonce-${++n}-aaaaaaaaaaaaaaaa" })
    }

    @Test
    fun registersOnceThenSignsRequests() = runTest {
        val key = FakeKey()
        val log = mutableListOf<HttpRequestData>()
        val api = api(key, log = log) { req ->
            if (req.url.encodedPath == "/v1/devices") HttpStatusCode.Created to """{"deviceId":"d",$ACCOUNT_BODY}""" else HttpStatusCode.OK to ACCOUNT
        }
        val a = api.account()
        api.account()
        assertEquals(4_200_000, a.balanceMicro)
        assertEquals(listOf("/v1/devices", "/v1/account", "/v1/account"), log.map { it.url.encodedPath })
        val get = log[1]
        assertEquals(deviceIdOf(key.publicPoint()), get.headers[FuelHeaders.DEVICE])
        assertEquals("1762128000", get.headers[FuelHeaders.TIMESTAMP])
        assertTrue(Regex("[A-Za-z0-9_-]{16,64}").matches(get.headers[FuelHeaders.NONCE]!!))
        assertEquals(86, get.headers[FuelHeaders.SIGNATURE]!!.length) // 64 bytes base64url
        val canonical = key.signed[1]
        assertEquals("OVERFIT-V1\nGET\n/v1/account\n1762128000\n${get.headers[FuelHeaders.NONCE]}\ne3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", canonical)
        // registration body carries the public key and is signed with the same canonical scheme
        assertEquals("""{"publicKey":"${key.publicPoint().toBase64Url()}"}""", log[0].body.toByteArray().decodeToString())
        assertTrue(key.signed[0].startsWith("OVERFIT-V1\nPOST\n/v1/devices\n"))
        assertTrue(log[1].headers[FuelHeaders.NONCE] != log[2].headers[FuelHeaders.NONCE])
    }

    @Test
    fun reRegistersOnUnknownDeviceAndRetriesOnce() = runTest {
        var accountCalls = 0
        val log = mutableListOf<HttpRequestData>()
        val api = api(log = log) { req ->
            when (req.url.encodedPath) {
                "/v1/devices" -> HttpStatusCode.OK to """{$ACCOUNT_BODY}"""
                else -> if (++accountCalls == 1) HttpStatusCode.Unauthorized to err("unknown_device") else HttpStatusCode.OK to ACCOUNT
            }
        }
        assertEquals("a1", api.account().accountId)
        assertEquals(listOf("/v1/devices", "/v1/account", "/v1/devices", "/v1/account"), log.map { it.url.encodedPath })
    }

    @Test
    fun correctsClockSkewFromServerTime() = runTest {
        var calls = 0
        val log = mutableListOf<HttpRequestData>()
        val api = api(log = log) { req ->
            when {
                req.url.encodedPath == "/v1/devices" -> HttpStatusCode.OK to """{$ACCOUNT_BODY}"""
                ++calls == 1 -> HttpStatusCode.Unauthorized to err("timestamp_out_of_range", """{"serverTime":1762129000}""")
                else -> HttpStatusCode.OK to ACCOUNT
            }
        }
        api.account()
        assertEquals("1762129000", log.last().headers[FuelHeaders.TIMESTAMP])
    }

    @Test
    fun mapsErrorCodes() = runTest {
        val api = api { req ->
            if (req.url.encodedPath == "/v1/devices") HttpStatusCode.OK to """{$ACCOUNT_BODY}"""
            else HttpStatusCode.PaymentRequired to err("insufficient_funds", """{"requiredMicro":10053,"availableMicro":200}""")
        }
        val e = assertFailsWith<FuelException> { api.messages("""{"model":"m","max_tokens":10,"messages":[]}""") }
        assertEquals("insufficient_funds", e.code)
        assertEquals(402, e.status)
        assertEquals(10053L, e.details["requiredMicro"])
    }

    @Test
    fun messagesReturnsCostAndBalance() = runTest {
        val log = mutableListOf<HttpRequestData>()
        val api = api(log = log) { req ->
            if (req.url.encodedPath == "/v1/devices") HttpStatusCode.OK to """{$ACCOUNT_BODY}"""
            else HttpStatusCode.OK to """{"id":"msg_1","content":[],"overfit":{"costMicro":6956,"balanceMicro":7863414,"currency":"EUR","model":"claude-sonnet-5-5","requestedModel":"gemini-3.6-flash","fallback":true}}"""
        }
        val body = """{"model":"claude-sonnet-5-5","max_tokens":100,"messages":[{"role":"user","content":"hi"}]}"""
        val r = api.messages(body)
        assertEquals(6_956, r.costMicro)
        assertEquals(7_863_414, r.balanceMicro)
        assertEquals("claude-sonnet-5-5", r.servedModel)
        assertEquals("gemini-3.6-flash", r.chosenModel)
        val sent = log.last()
        assertEquals(body, sent.body.toByteArray().decodeToString()) // byte-identical to what was hashed
        assertTrue(sent.headers[FuelHeaders.SIGNATURE] != null)
    }

    @Test
    fun networkFailureBecomesNetworkError() = runTest {
        val engine = MockEngine { throw RuntimeException("offline") }
        val api = FuelApi(HttpClient(engine), "https://fuel.test", RequestSigner(FakeKey()))
        assertEquals("network", assertFailsWith<FuelException> { api.account() }.code)
    }

    @Test
    fun slowAnswerBecomesTimeoutNotNetwork() = runTest {
        val engine = MockEngine { req ->
            if (req.url.encodedPath == "/v1/devices") respond("""{$ACCOUNT_BODY}""", HttpStatusCode.OK, json)
            else { kotlinx.coroutines.delay(5_000); respond("{}", HttpStatusCode.OK, json) }
        }
        val api = FuelApi(HttpClient(engine), "https://fuel.test", RequestSigner(FakeKey()), requestTimeoutMs = 50, messagesTimeoutMs = 50)
        assertEquals("timeout", assertFailsWith<FuelException> { api.account() }.code)
        assertEquals("timeout", assertFailsWith<FuelException> { api.messages("""{"model":"m","max_tokens":10,"messages":[]}""") }.code)
    }

    @Test
    fun messagesGetTheirOwnLongerTimeout() = runTest {
        val engine = MockEngine { req ->
            when (req.url.encodedPath) {
                "/v1/devices" -> respond("""{$ACCOUNT_BODY}""", HttpStatusCode.OK, json)
                "/v1/messages" -> { kotlinx.coroutines.delay(300); respond("""{"content":[],"overfit":{"costMicro":1,"balanceMicro":2}}""", HttpStatusCode.OK, json) }
                else -> { kotlinx.coroutines.delay(300); respond(ACCOUNT, HttpStatusCode.OK, json) }
            }
        }
        val api = FuelApi(HttpClient(engine), "https://fuel.test", RequestSigner(FakeKey()), requestTimeoutMs = 100, messagesTimeoutMs = 5_000)
        assertEquals(2, api.messages("""{"model":"m","max_tokens":10,"messages":[]}""").balanceMicro)
        assertEquals("timeout", assertFailsWith<FuelException> { api.account() }.code)
    }

    @Test
    fun connectTimeoutIsTimeout() = runTest {
        val engine = MockEngine { throw io.ktor.client.network.sockets.ConnectTimeoutException("connect timed out") }
        val api = FuelApi(HttpClient(engine), "https://fuel.test", RequestSigner(FakeKey()))
        assertEquals("timeout", assertFailsWith<FuelException> { api.account() }.code)
    }

    @Test
    fun accountReadsReservation() = runTest {
        val with = api { req ->
            if (req.url.encodedPath == "/v1/devices") HttpStatusCode.OK to """{$ACCOUNT_BODY}"""
            else HttpStatusCode.OK to """{"accountId":"a1","balanceMicro":3590000,"reservedMicro":610000,"hasRecoveryCode":true}"""
        }.account()
        assertEquals(3_590_000, with.balanceMicro)
        assertEquals(610_000, with.reservedMicro)
        val without = api { req ->
            if (req.url.encodedPath == "/v1/devices") HttpStatusCode.OK to """{$ACCOUNT_BODY}""" else HttpStatusCode.OK to ACCOUNT
        }.account()
        assertEquals(0, without.reservedMicro)
    }

    @Test
    fun ledgerPageReadsBalanceAndReservation() = runTest {
        val row = """{"id":12,"ts":1,"type":"usage","amountMicro":-6956}"""
        val with = api { req ->
            if (req.url.encodedPath == "/v1/devices") HttpStatusCode.OK to """{$ACCOUNT_BODY}"""
            else HttpStatusCode.OK to """{"entries":[$row],"next":null,"balanceMicro":3590000,"reservedMicro":610000}"""
        }.ledger(null)
        assertEquals(3_590_000L, with.balanceMicro)
        assertEquals(610_000, with.reservedMicro)
        assertEquals(1, with.entries.size)
        val without = api { req ->
            if (req.url.encodedPath == "/v1/devices") HttpStatusCode.OK to """{$ACCOUNT_BODY}"""
            else HttpStatusCode.OK to """{"entries":[$row],"next":null}"""
        }.ledger(null)
        assertEquals(null, without.balanceMicro)
        assertEquals(0, without.reservedMicro)
    }

    @Test
    fun ledgerQueryIsPartOfTheSignedPath() = runTest {
        val key = FakeKey()
        val api = api(key) { req ->
            if (req.url.encodedPath == "/v1/devices") HttpStatusCode.OK to """{$ACCOUNT_BODY}"""
            else HttpStatusCode.OK to """{"entries":[{"id":12,"ts":1,"type":"usage","amountMicro":-6956,"model":"m","inputTokens":1000,"outputTokens":500}],"next":null}"""
        }
        val page = api.ledger(before = 30, limit = 20)
        assertEquals(-6_956, page.entries.single().amountMicro)
        assertEquals("/v1/account/ledger?limit=20&before=30", key.signed.last().lines()[2])
    }

    // Fabian 8.11.: "Transactions" said the credit can't be reached although the balance showed 3.50 €. D1 sends
    // `fallback` as 0/1 and NULL tokens for credit rows; the page has to read the rows exactly as the server sends them.
    @Test
    fun ledgerReadsRowsAsD1SendsThem() = runTest {
        val api = api { req ->
            if (req.url.encodedPath == "/v1/devices") HttpStatusCode.OK to """{$ACCOUNT_BODY}"""
            else HttpStatusCode.OK to """{"entries":[
              {"id":3,"ts":1794146000,"type":"usage","amountMicro":-6956,"model":"claude-haiku-5-5","tier":"lite","source":null,"inputTokens":1000,"cacheReadTokens":0,"cacheWriteTokens":0,"outputTokens":500,"fallback":1,"requestedModel":"claude-sonnet-5-5","reviews":null,"category":"used"},
              {"id":2,"ts":1794140000,"type":"test_credit","amountMicro":1000000,"model":null,"tier":null,"source":null,"inputTokens":null,"cacheReadTokens":null,"cacheWriteTokens":null,"outputTokens":null,"fallback":0,"requestedModel":null,"reviews":null,"category":"gifted"}
            ],"next":null}"""
        }
        val page = api.ledger(before = null, limit = 50)
        assertEquals(2, page.entries.size)
        assertEquals(true, page.entries[0].fallback)
        assertEquals(false, page.entries[1].fallback)
        assertEquals(0, page.entries[1].inputTokens)
    }

    @Test
    fun accessUsesOverrideThenBuildUrl() {
        val kv = app.overfit.data.InMemoryKeyValueStore()
        val access = FuelAccess(kv, "") { DemoFuelService() }
        assertEquals(null, access.service())
        access.setOverride("https://dev.example/")
        assertEquals("https://dev.example", access.url.value)
        assertTrue(access.service() != null)
        access.setOverride("")
        assertEquals(null, access.service())
        assertEquals("https://build.example", FuelAccess(kv, "https://build.example") { DemoFuelService() }.url.value)
    }
}

private const val ACCOUNT_BODY = """"accountId":"a1","balanceMicro":0"""
