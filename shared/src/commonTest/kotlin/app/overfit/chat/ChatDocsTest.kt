package app.overfit.chat

import app.overfit.domain.Language
import app.overfit.domain.Profile
import app.overfit.domain.SetValues
import app.overfit.domain.WeightEntry
import app.overfit.domain.WeightUnit
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ChatDocsTest {
    @Test
    fun finishedWorkoutDocument() {
        val doc = ChatDocs.workout(record("w1", 1_000_000, sets = listOf(SetValues(60.0, 8, null), SetValues(62.5, 6, null))))
        assertEquals("workout/w1", doc.key)
        assertEquals(1_000_010, doc.updatedAt) // milliseconds
        val d = doc.data
        assertEquals(1_000_000L, d["startedAt"]!!.jsonPrimitive.long)
        assertEquals(4_600_000L, d["endedAt"]!!.jsonPrimitive.long)
        assertEquals("7", d["templateId"]!!.jsonPrimitive.content)
        assertEquals(4, d["feeling"]!!.jsonPrimitive.int)
        val ex = d["exercises"]!!.jsonArray[0].jsonObject
        assertEquals("e-Bench", ex["exerciseId"]!!.jsonPrimitive.content)
        assertEquals("Bench", ex["name"]!!.jsonPrimitive.content)
        assertEquals("WEIGHT_REPS", ex["trackingType"]!!.jsonPrimitive.content)
        assertEquals("planned", ex["origin"]!!.jsonPrimitive.content)
        val set = ex["sets"]!!.jsonArray[1].jsonObject
        assertEquals(62.5, set["kg"]!!.jsonPrimitive.double)
        assertEquals(6, set["reps"]!!.jsonPrimitive.int)
        assertEquals(true, set["completed"]!!.jsonPrimitive.content.toBoolean())
        assertEquals(62.5, set["planned"]!!.jsonObject["kg"]!!.jsonPrimitive.double)
    }

    @Test
    fun saunaIsSentOnlyWhenChecked() {
        assertEquals(true, ChatDocs.workout(record("w3", 5).copy(sauna = true)).data["sauna"]!!.jsonPrimitive.content.toBoolean())
        assertEquals(null, ChatDocs.workout(record("w4", 5)).data["sauna"]) // missing = no sauna (backend reads false)
    }

    @Test
    fun runningWorkoutHasEndedAtNull() {
        val doc = ChatDocs.workout(record("w2", 5, ended = false))
        assertEquals(JsonNull, doc.data["endedAt"])
    }

    @Test
    fun templateDocument() {
        val doc = ChatDocs.template(template(id = 12), now = 99)
        assertEquals("template/12", doc.key)
        val ex = doc.data["exercises"]!!.jsonArray[0].jsonObject
        assertEquals(120, ex["restSec"]!!.jsonPrimitive.int)
        assertEquals(2, ex["sets"]!!.jsonArray.size)
    }

    @Test
    fun profileDocumentUsesKgAndLanguageCodes() {
        val doc = ChatDocs.profile(
            Profile("Fabian", 1990, WeightUnit.LB),
            listOf(WeightEntry("2026-09-02", 81.0), WeightEntry("2026-09-01", 80.2)),
            Language.EN, "Europe/Berlin", 5,
        )
        assertEquals("profile/me", doc.key)
        assertEquals("lb", doc.data["unit"]!!.jsonPrimitive.content)
        assertEquals("en", doc.data["language"]!!.jsonPrimitive.content)
        val bw = doc.data["bodyweight"]!!.jsonArray
        assertEquals("2026-09-01", bw[0].jsonObject["date"]!!.jsonPrimitive.content)
        assertEquals(80.2, bw[0].jsonObject["kg"]!!.jsonPrimitive.double)
    }

    @Test
    fun fingerprintIgnoresUpdatedAtButSeesContent() {
        val a = ChatDocs.template(template(), now = 1)
        assertEquals(ChatDocs.fingerprint(a), ChatDocs.fingerprint(a.copy(updatedAt = 2)))
        assertNotEquals(ChatDocs.fingerprint(a), ChatDocs.fingerprint(ChatDocs.template(template(name = "Pull"), now = 1)))
    }

    @Test
    fun batchesOfAtMost500() {
        val sizes = ChatDocs.batches((1..1203).toList()).map { it.size }
        assertEquals(listOf(500, 500, 203), sizes)
        assertTrue(ChatDocs.batches(emptyList<Int>()).isEmpty())
    }

    @Test
    fun syncBodyShape() {
        val body = ChatApi.syncBody(true, listOf(ChatDocs.memory("hi", 7)), listOf(SyncDelete("template", "3")))
        assertEquals(true, body["reset"]!!.jsonPrimitive.content.toBoolean())
        val up = body["upserts"] as JsonArray
        assertEquals("memory", up[0].jsonObject["kind"]!!.jsonPrimitive.content)
        assertEquals(7L, up[0].jsonObject["updatedAt"]!!.jsonPrimitive.long)
        assertEquals("hi", up[0].jsonObject["data"]!!.jsonObject["text"]!!.jsonPrimitive.content)
        assertEquals(JsonPrimitive("3"), (body["deletes"] as JsonArray)[0].jsonObject["id"])
        assertTrue(body is JsonObject)
    }
}
