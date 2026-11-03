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
private const val ACCOUNT = """{"accountId":"a1","balanceMicro":4200000,"expiring":[{"amountMicro":1000000,"expiresAt":1777777777}],"displayName":"Fabian","hasRecoveryCode":false,"spot":{"invitedThisMonth":2,"monthlyLimit":10,"redeemedStarter":false}}"""
private fun err(code: String, details: String = "{}") = """{"error":{"code":"$code","message":"m","details":$details}}"""

class FuelApiTest {
    private fun api(
        key: FakeKey = FakeKey(),
        attestor: Attestor = NoAttestor,
        log: MutableList<HttpRequestData> = mutableListOf(),
        handler: suspend (HttpRequestData) -> Pair<HttpStatusCode, String>,
    ): FuelApi {
        var n = 0
        val engine = MockEngine { req ->
            log += req
            val (status, body) = handler(req)
            respond(body, status, json)
        }
        return FuelApi(HttpClient(engine), "https://fuel.test/", RequestSigner(key), attestor, nowSec = { 1_762_128_000 }, nonce = { "nonce-${++n}-aaaaaaaaaaaaaaaa" })
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
        assertEquals("Fabian", a.displayName)
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
            else HttpStatusCode.OK to """{"id":"msg_1","content":[],"overfit":{"costMicro":6956,"balanceMicro":7863414,"currency":"EUR"}}"""
        }
        val body = """{"model":"claude-sonnet-5-5","max_tokens":100,"messages":[{"role":"user","content":"hi"}]}"""
        val r = api.messages(body)
        assertEquals(6_956, r.costMicro)
        assertEquals(7_863_414, r.balanceMicro)
        val sent = log.last()
        assertEquals(body, sent.body.toByteArray().decodeToString()) // byte-identical to what was hashed
        assertTrue(sent.headers[FuelHeaders.SIGNATURE] != null)
    }

    @Test
    fun spotRedeemUsesChallengeAndAttestation() = runTest {
        val log = mutableListOf<HttpRequestData>()
        val attestor = object : Attestor { override suspend fun attest(challenge: String) = Attestation("google", "tok-for-$challenge") }
        val api = api(attestor = attestor, log = log) { req ->
            when (req.url.encodedPath) {
                "/v1/devices" -> HttpStatusCode.OK to """{$ACCOUNT_BODY}"""
                "/v1/spot/challenge" -> HttpStatusCode.OK to """{"challenge":"ch1","expiresAt":1}"""
                else -> HttpStatusCode.OK to """{"grantedMicro":1000000,"expiresAt":5,"inviter":{"displayName":"Fabian"},"balanceMicro":1000000}"""
            }
        }
        val r = api.redeemSpot("tokenvalue123")
        assertEquals(1_000_000, r.grantedMicro)
        assertEquals("Fabian", r.inviter.displayName)
        assertEquals(
            """{"token":"tokenvalue123","attestation":{"platform":"google","token":"tok-for-ch1","challenge":"ch1"}}""",
            log.last().body.toByteArray().decodeToString(),
        )
    }

    @Test
    fun spotRedeemWithoutAttestationIsUnavailable() = runTest {
        val api = api { req ->
            when (req.url.encodedPath) {
                "/v1/spot/challenge" -> HttpStatusCode.OK to """{"challenge":"ch1"}"""
                else -> HttpStatusCode.OK to """{$ACCOUNT_BODY}"""
            }
        }
        assertEquals("attestation_unavailable", assertFailsWith<FuelException> { api.redeemSpot("tokenvalue123") }.code)
    }

    @Test
    fun networkFailureBecomesNetworkError() = runTest {
        val engine = MockEngine { throw RuntimeException("offline") }
        val api = FuelApi(HttpClient(engine), "https://fuel.test", RequestSigner(FakeKey()))
        assertEquals("network", assertFailsWith<FuelException> { api.account() }.code)
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
