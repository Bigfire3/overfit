package app.overfit.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RestTicksTest {
    @Test
    fun startAt_threeSecondsBeforeTheEnd() {
        assertEquals(57_000L, RestTicks.startAt(endsAt = 60_000, enabled = true, now = 0))
        // Exactly 3 s left: the ticks start right away.
        assertEquals(57_000L, RestTicks.startAt(endsAt = 60_000, enabled = true, now = 57_000))
    }

    @Test
    fun startAt_noneWhenOffOrTooLittleLeft() {
        assertNull(RestTicks.startAt(endsAt = 60_000, enabled = false, now = 0))
        assertNull(RestTicks.startAt(endsAt = 60_000, enabled = true, now = 57_001))
        assertNull(RestTicks.startAt(endsAt = 60_000, enabled = true, now = 70_000))
    }

    @Test
    fun onTime_toleratesASlightlyLateAlarmOnly() {
        assertTrue(RestTicks.onTime(10_000, 10_000))
        assertTrue(RestTicks.onTime(10_000, 10_000 + RestTicks.MAX_LATE_MS))
        assertFalse(RestTicks.onTime(10_000, 10_000 + RestTicks.MAX_LATE_MS + 1))
        assertFalse(RestTicks.onTime(10_000, 60_000))
    }

    @Test
    fun pcm_hasThreeTicksOneSecondApartAndSilenceBetween() {
        val pcm = RestTicks.pcm()
        val rate = RestTicks.SAMPLE_RATE
        assertEquals((rate * RestTicks.durationMs / 1000).toInt(), pcm.size)
        assertTrue(RestTicks.durationMs < 3_000, "ends before the rest does")
        for (i in 0 until RestTicks.COUNT) {
            val start = i * rate
            val peak = (start until start + rate / 100).maxOf { kotlin.math.abs(pcm[it].toInt()) }
            assertTrue(peak > 8_000, "tick $i is audible: $peak")
            if (i > 0) {
                val quiet = (start - rate / 2 until start).maxOf { kotlin.math.abs(pcm[it].toInt()) }
                assertEquals(0, quiet, "silence before tick $i")
            }
        }
    }

    @Test
    fun wav_isLinearPcmMono16Bit() {
        val wav = RestTicks.wav()
        fun ascii(at: Int) = (at until at + 4).map { wav[it].toInt().toChar() }.joinToString("")
        fun le(at: Int, n: Int) = (0 until n).sumOf { (wav[at + it].toInt() and 0xFF) shl (8 * it) }
        assertEquals("RIFF", ascii(0))
        assertEquals("WAVE", ascii(8))
        assertEquals("fmt ", ascii(12))
        assertEquals(1, le(20, 2)) // PCM
        assertEquals(1, le(22, 2)) // mono
        assertEquals(RestTicks.SAMPLE_RATE, le(24, 4))
        assertEquals(16, le(34, 2))
        assertEquals("data", ascii(36))
        assertEquals(RestTicks.pcm().size * 2, le(40, 4))
        assertEquals(44 + le(40, 4), wav.size)
        assertEquals(wav.size - 8, le(4, 4))
    }
}
