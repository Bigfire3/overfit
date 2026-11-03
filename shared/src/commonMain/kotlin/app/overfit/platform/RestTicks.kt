package app.overfit.platform

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The ticks in the last seconds of a rest (see docs/DECISIONS.md, "Ticken vor Pausenende").
 *
 * One sound file holds all three ticks one second apart, so each platform only has to start one
 * sound at [startAt] (Android: an exact alarm, iOS: a local notification with this sound, or the
 * app itself in the foreground). The sound is synthesized here, no third-party audio.
 */
object RestTicks {
    const val COUNT = 3
    /** The first tick sounds this long before the end of the rest. */
    const val LEAD_MS = COUNT * 1_000L
    /** Starting later than this (a late alarm) would put the ticks out of step: they are skipped. */
    const val MAX_LATE_MS = 700L
    const val SAMPLE_RATE = 44_100
    private const val TICK_MS = 45
    private const val FREQ_HZ = 1_800.0

    /**
     * When the ticks start for a rest ending at [endsAt], or null for none: setting off, or less
     * than [LEAD_MS] left at [now] (a shortened rest gets no half countdown).
     */
    fun startAt(endsAt: Long, enabled: Boolean, now: Long): Long? {
        if (!enabled) return null
        val at = endsAt - LEAD_MS
        return at.takeIf { it >= now }
    }

    fun startAt(alert: RestAlert, now: Long): Long? = startAt(alert.endsAt, alert.ticks, now)

    /** Whether ticks due at [startAt] may still be played at [now]. */
    fun onTime(startAt: Long, now: Long): Boolean = now - startAt in -MAX_LATE_MS..MAX_LATE_MS

    /** Length of the sound in milliseconds (last tick plus its decay). */
    val durationMs: Long get() = (COUNT - 1) * 1_000L + TICK_MS + 15

    /**
     * Mono 16-bit PCM: three short wooden-sounding clicks (a sine burst at 1.8 kHz with a fast
     * exponential decay), the first at 0 s, then every second. Quiet enough to sit on top of music.
     */
    fun pcm(): ShortArray {
        val total = (SAMPLE_RATE * durationMs / 1_000).toInt()
        val out = ShortArray(total)
        val tickSamples = SAMPLE_RATE * TICK_MS / 1_000
        val attack = SAMPLE_RATE / 1_000 // 1 ms fade-in against a click at the start
        for (i in 0 until COUNT) {
            val offset = i * SAMPLE_RATE
            for (n in 0 until tickSamples) {
                val t = n.toDouble() / SAMPLE_RATE
                val envelope = exp(-t * 90.0) * (if (n < attack) n.toDouble() / attack else 1.0)
                val v = sin(2 * PI * FREQ_HZ * t) * envelope * 0.55
                out[offset + n] = (v * Short.MAX_VALUE).roundToInt().toShort()
            }
        }
        return out
    }

    /** [pcm] as a RIFF/WAVE file (linear PCM, playable by iOS notifications and AVAudioPlayer). */
    fun wav(): ByteArray {
        val samples = pcm()
        val dataSize = samples.size * 2
        val out = ByteArray(44 + dataSize)
        var p = 0
        fun ascii(s: String) { s.forEach { out[p++] = it.code.toByte() } }
        fun le(v: Int, bytes: Int) { repeat(bytes) { out[p++] = (v shr (8 * it) and 0xFF).toByte() } }
        ascii("RIFF"); le(36 + dataSize, 4); ascii("WAVE")
        ascii("fmt "); le(16, 4); le(1, 2); le(1, 2); le(SAMPLE_RATE, 4); le(SAMPLE_RATE * 2, 4); le(2, 2); le(16, 2)
        ascii("data"); le(dataSize, 4)
        samples.forEach { le(it.toInt(), 2) }
        return out
    }
}
