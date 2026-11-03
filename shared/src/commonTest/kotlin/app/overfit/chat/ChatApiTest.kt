package app.overfit.chat

import app.overfit.fuel.DeviceKey
import app.overfit.fuel.FuelApi
import app.overfit.fuel.FuelException
import app.overfit.fuel.RequestSigner
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

private class Key : DeviceKey {
    private val point = ByteArray(65) { if (it == 0) 4 else (it * 3).toByte() }
    val signed = mutableListOf<String>()
    override fun publicPoint() = point
    override fun sign(message: ByteArray): ByteArray { signed += message.decodeToString(); return ByteArray(64) { 7 } }
    override fun delete() = Unit
}

private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

class ChatApiTest {
    private fun api(key: Key = Key(), log: MutableList<HttpRequestData> = mutableListOf(), handler: (HttpRequestData) -> Pair<HttpStatusCode, String>): ChatApi {
        var n = 0
        val engine = MockEngine { req ->
            log += req
            if (req.url.encodedPath == "/v1/devices") respond("""{"deviceId":"d"}""", HttpStatusCode.OK, jsonHeaders)
            else { val (s, b) = handler(req); respond(b, s, jsonHeaders) }
        }
        return ChatApi(FuelApi(HttpClient(engine), "https://x.test", RequestSigner(key), nowSec = { 1_762_128_000 }, nonce = { "nonce-${++n}-aaaaaaaaaaaaaaaa" }))
    }

    @Test
    fun syncSendsSignedBodyAndParsesResult() = runTest {
        val log = mutableListOf<HttpRequestData>()
        val key = Key()
        val chat = api(key, log) { HttpStatusCode.OK to """{"applied":1,"skipped":0,"deleted":1,"lastSyncAt":1762128500}""" }
        val r = chat.sync(true, listOf(ChatDocs.memory("x", 5)), listOf(SyncDelete("template", "9")))
        assertEquals(SyncResult(1, 0, 1, 1_762_128_500), r)
        val req = log.last()
        assertEquals("/v1/chat/sync", req.url.encodedPath)
        assertEquals(HttpMethod.Post, req.method)
        val body = Json.parseToJsonElement(req.body.toByteArray().decodeToString()).jsonObject
        assertEquals("true", body["reset"]!!.jsonPrimitive.content)
        assertEquals("9", body["deletes"]!!.jsonArray[0].jsonObject["id"]!!.jsonPrimitive.content)
        assertTrue(key.signed.last().startsWith("OVERFIT-V1\nPOST\n/v1/chat/sync\n"))
    }

    @Test
    fun photoPutSignsTheQueryAndSendsJpeg() = runTest {
        val log = mutableListOf<HttpRequestData>()
        val key = Key()
        val chat = api(key, log) { HttpStatusCode.OK to """{"id":"p1"}""" }
        chat.putPhoto("p1", 1_700_000_000_000, "w-1", byteArrayOf(9, 8, 7))
        val req = log.last()
        assertEquals(HttpMethod.Put, req.method)
        assertEquals("/v1/chat/photos/p1", req.url.encodedPath)
        assertEquals("takenAt=1700000000000&workoutId=w-1", req.url.encodedQuery)
        assertEquals(listOf<Byte>(9, 8, 7), req.body.toByteArray().toList())
        assertEquals("image/jpeg", req.body.contentType.toString())
        assertTrue(key.signed.last().contains("\nPUT\n/v1/chat/photos/p1?takenAt=1700000000000&workoutId=w-1\n"))
    }

    @Test
    fun statusEnableRevokeAndPairing() = runTest {
        val log = mutableListOf<HttpRequestData>()
        val chat = api(log = log) { req ->
            when (req.url.encodedPath) {
                "/v1/chat/pairing" -> HttpStatusCode.OK to """{"code":"K7QM-3XWP","expiresAt":1762128600,"ttlSec":600}"""
                "/v1/chat" -> HttpStatusCode.OK to """{"enabled":true,"photos":false,"docs":{"workout":2},"inboxPending":1,"connections":[{"id":"g_1","clientName":"Claude","createdAt":1,"lastUsedAt":null}],"extra":1}"""
                else -> HttpStatusCode.OK to """{"enabled":true,"photos":true}"""
            }
        }
        val status = chat.status()
        assertEquals("Claude", status.connections.single().clientName)
        assertEquals(1, status.inboxPending)
        assertEquals(2, status.docs.workout)
        assertEquals("K7QM-3XWP", chat.pairing().code)
        assertTrue(chat.enable(true).photos)
        assertEquals("""{"photos":true}""", log.last().body.toByteArray().decodeToString())
        chat.revoke("g_1")
        assertEquals("""{"grantId":"g_1"}""", log.last().body.toByteArray().decodeToString())
    }

    @Test
    fun errorsCarryTheApiCodeAndDetails() = runTest {
        val chat = api { HttpStatusCode.PayloadTooLarge to """{"error":{"code":"sync_quota_exceeded","message":"m","details":{"maxDocs":5000}}}""" }
        val e = assertFailsWith<FuelException> { chat.sync(false, emptyList(), emptyList()) }
        assertEquals("sync_quota_exceeded", e.code)
        assertEquals(5000L, e.details["maxDocs"])
    }

    @Test
    fun inboxParsesTemplatesNotesAndUnknownKinds() = runTest {
        val body = """{"items":[
          {"id":"in_1","kind":"template","createdAt":1762128400,"source":{"clientName":"Claude"},
           "template":{"v":1,"kind":"template","name":"Push B","exercises":[{"name":"Bench Press","sets":[{"kg":60,"reps":8}]}]},
           "link":"https://x/t#abc","replacesTemplateId":"7","message":"Mehr Volumen"},
          {"id":"in_2","kind":"note","createdAt":1762128450,"source":{"clientName":"ChatGPT"},
           "note":{"title":null,"text":"## Gut\n- Bank 80","workoutId":"w1","rating":8}},
          {"id":"in_3","kind":"video","createdAt":1}
        ]}"""
        val log = mutableListOf<HttpRequestData>()
        val chat = api(log = log) { HttpStatusCode.OK to body }
        val page = chat.inbox()
        assertEquals(listOf("in_3"), page.unknownIds)
        val t = assertIs<InboxTemplate>(page.items[0])
        assertEquals("7", t.replacesTemplateId)
        assertEquals("Mehr Volumen", t.message)
        assertEquals("Claude", t.clientName)
        assertTrue(t.templateJson.contains("Push B"))
        val n = assertIs<InboxNote>(page.items[1])
        assertEquals("w1", n.workoutId)
        assertEquals(8, n.rating)
        assertEquals(null, n.title)
        chat.ack(listOf("in_1", "in_2"))
        assertEquals("""{"ids":["in_1","in_2"]}""", log.last().body.toByteArray().decodeToString())
    }
}
