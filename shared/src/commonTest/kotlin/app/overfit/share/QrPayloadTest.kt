package app.overfit.share

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class QrPayloadTest {
    @Test fun base45MatchesRfc9285Vectors() {
        assertEquals("BB8", Base45.encode("AB".encodeToByteArray()))
        assertEquals("%69 VD92EX0", Base45.encode("Hello!!".encodeToByteArray()))
        assertEquals("UJCLQE7W581", Base45.encode("base-45".encodeToByteArray()))
        assertEquals("QED8WEX0", Base45.encode("ietf!".encodeToByteArray()))
        assertEquals("ietf!", Base45.decode("QED8WEX0")!!.decodeToString())
        assertEquals("Hello!!", Base45.decode("%69 VD92EX0")!!.decodeToString())
    }

    @Test fun base45RoundTripsAllLengthsAndRejectsGarbage() {
        for (n in 0..40) {
            val data = ByteArray(n) { (it * 37 + n).toByte() }
            assertEquals(data.toList(), Base45.decode(Base45.encode(data))!!.toList(), "len $n")
        }
        assertNull(Base45.decode("abc")) // lower case
        assertNull(Base45.decode("A")) // impossible length
        assertNull(Base45.decode(":::")) // > 0xFFFF
    }

    private fun sample() = SharedTraining(
        kind = ShareFormat.KIND_WORKOUT,
        name = "Push – Ünïcode",
        note = "Notiz",
        exercises = listOf(
            SharedExercise(id = "wg:bench-press", restSec = 150, note = "langsam", sets = List(3) { SharedSet(kg = 80.0, reps = 8) } + SharedSet(kg = 82.5, reps = 6)),
            SharedExercise(name = "Klimmzug", sets = listOf(SharedSet(kg = -20.0, reps = 5))),
            SharedExercise(ref = "c1", sets = listOf(SharedSet(sec = 45), SharedSet(sec = 45))),
        ),
        custom = listOf(SharedCustom(ref = "c1", name = "Handstand", trackingType = "TIME", muscle = "shoulders", primary = listOf("shoulders"), secondary = listOf("core"), equipment = "none", restSec = 60, note = "Wand")),
    )

    /** Catalogue IDs travel as hashes (resolved against the catalogue on import), everything else is unchanged. */
    private fun SharedTraining.hashed() = copy(exercises = exercises.map { e -> e.id?.takeIf { it.startsWith("wg:") }?.let { e.copy(id = QrPayload.hashedId(it)) } ?: e })

    @Test fun payloadRoundTrips() {
        val t = sample()
        val text = QrPayload.encode(t)
        assertTrue(text.startsWith("OF1:"))
        assertTrue(text.all { it in Base45.ALPHABET || it == 'O' }) // QR alphanumeric alphabet only
        assertEquals(t.hashed(), QrPayload.decode(text))
    }

    @Test fun payloadIsReadByParseInputInsideSurroundingText() {
        val t = sample()
        val parsed = ShareFormat.parseInput("Mein Workout:\n${QrPayload.encode(t)}\n")
        assertEquals(t.hashed(), (parsed as ShareParse.Ok).training)
    }

    @Test fun brokenPayloadFailsWithoutCrash() {
        assertNull(QrPayload.decode("OF1:"))
        assertNull(QrPayload.decode("OF1:QED8WEX0"))
        assertNull(QrPayload.decode("OF1:::::"))
        assertTrue(ShareFormat.parseInput("OF1:QED8WEX0") is ShareParse.Fail)
    }

    @Test fun shortCodeIdsAndFallback() = kotlinx.coroutines.test.runTest {
        assertEquals("AB12CD34", QrPayload.shortId("OFS:AB12CD34"))
        assertNull(QrPayload.shortId("OFS:x"))
        assertTrue(qrTextFor(sample()).startsWith("OF1:")) // no backend → full content offline
        val svc = object : ShortCodeService {
            override suspend fun upload(training: SharedTraining) = "AB12CD34"
            override suspend fun fetch(id: String): SharedTraining? = null
        }
        assertEquals("OFS:AB12CD34", qrTextFor(sample(), svc))
    }
}
