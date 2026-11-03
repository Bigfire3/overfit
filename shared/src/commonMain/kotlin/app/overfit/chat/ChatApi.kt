package app.overfit.chat

import app.overfit.fuel.SignedTransport
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/** What the app does with the KI-Chat-Verbindung of the backend. Errors are `FuelException` with the API error code. */
interface ChatService {
    suspend fun status(): ChatStatus
    suspend fun enable(photos: Boolean): ChatStatus
    suspend fun disable()
    suspend fun sync(reset: Boolean, upserts: List<SyncDoc>, deletes: List<SyncDelete>): SyncResult
    suspend fun putPhoto(id: String, takenAt: Long, workoutId: String?, jpeg: ByteArray)
    suspend fun deletePhoto(id: String)
    suspend fun pairing(): PairingCode
    suspend fun revoke(grantId: String)
    suspend fun inbox(): InboxPage
    suspend fun ack(ids: List<String>)
}

/** Implementation on top of the signed transport (device identity of Fuel). */
class ChatApi(private val transport: SignedTransport) : ChatService {
    private val json = Json { ignoreUnknownKeys = true }

    private suspend fun get(path: String) = json.parseToJsonElement(transport.request("GET", path))
    private suspend fun post(path: String, body: JsonObject = JsonObject(emptyMap())) =
        json.parseToJsonElement(transport.request("POST", path, body.toString().encodeToByteArray()))

    override suspend fun status(): ChatStatus = json.decodeFromJsonElement(get("/v1/chat"))

    override suspend fun enable(photos: Boolean): ChatStatus =
        json.decodeFromJsonElement(post("/v1/chat/enable", buildJsonObject { put("photos", photos) }))

    override suspend fun disable() { post("/v1/chat/disable") }

    override suspend fun sync(reset: Boolean, upserts: List<SyncDoc>, deletes: List<SyncDelete>): SyncResult =
        json.decodeFromJsonElement(post("/v1/chat/sync", syncBody(reset, upserts, deletes)))

    override suspend fun putPhoto(id: String, takenAt: Long, workoutId: String?, jpeg: ByteArray) {
        val query = buildString {
            append("?takenAt=").append(takenAt)
            if (workoutId != null) append("&workoutId=").append(workoutId)
        }
        transport.request("PUT", "/v1/chat/photos/$id$query", jpeg, "image/jpeg")
    }

    override suspend fun deletePhoto(id: String) { transport.request("DELETE", "/v1/chat/photos/$id") }

    override suspend fun pairing(): PairingCode = json.decodeFromJsonElement(post("/v1/chat/pairing"))

    override suspend fun revoke(grantId: String) { post("/v1/chat/connections/revoke", buildJsonObject { put("grantId", grantId) }) }

    override suspend fun inbox(): InboxPage = parseInbox(get("/v1/chat/inbox"))

    override suspend fun ack(ids: List<String>) {
        if (ids.isEmpty()) return
        post("/v1/chat/inbox/ack", buildJsonObject { put("ids", buildJsonArray { ids.forEach { add(JsonPrimitive(it)) } }) })
    }

    companion object {
        const val MAX_BATCH = 500

        fun syncBody(reset: Boolean, upserts: List<SyncDoc>, deletes: List<SyncDelete>): JsonObject = buildJsonObject {
            put("reset", reset)
            put("upserts", buildJsonArray {
                upserts.forEach { d ->
                    add(buildJsonObject {
                        put("kind", d.kind)
                        put("id", d.id)
                        put("updatedAt", d.updatedAt)
                        put("data", d.data)
                    })
                }
            })
            put("deletes", buildJsonArray {
                deletes.forEach { d -> add(buildJsonObject { put("kind", d.kind); put("id", d.id) }) }
            })
        }

        fun parseInbox(root: kotlinx.serialization.json.JsonElement): InboxPage {
            val items = mutableListOf<InboxItem>()
            val unknown = mutableListOf<String>()
            val array = (root as? JsonObject)?.get("items") as? JsonArray ?: JsonArray(emptyList())
            for (el in array) {
                val o = el as? JsonObject ?: continue
                val id = o.str("id") ?: continue
                val client = (o["source"] as? JsonObject)?.str("clientName").orEmpty()
                val created = o["createdAt"]?.jsonPrimitive?.longOrNull ?: 0L
                when (o.str("kind")) {
                    "template" -> {
                        val template = o["template"] as? JsonObject
                        if (template == null) unknown += id else {
                            items += InboxTemplate(id, client, created, template.toString(), o.str("replacesTemplateId"), o.str("message"))
                        }
                    }
                    "note" -> {
                        val note = o["note"] as? JsonObject
                        val text = note?.str("text")
                        if (note == null || text == null) unknown += id else {
                            items += InboxNote(
                                id, client, created, note.str("title"), text, note.str("workoutId"),
                                (note["rating"] as? JsonPrimitive)?.intOrNull,
                            )
                        }
                    }
                    else -> unknown += id
                }
            }
            return InboxPage(items, unknown)
        }

        private fun JsonObject.str(key: String): String? =
            (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull?.takeIf { it.isNotEmpty() }
    }
}
