package app.overfit.platform

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/** Haptics via [Vibrator]: predefined click where available, otherwise short waveforms. */
class AndroidHaptics(context: Context) : Haptics {
    private val vibrator: Vibrator? =
        if (Build.VERSION.SDK_INT >= 31) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }

    override fun play(pattern: HapticPattern) {
        val v = vibrator?.takeIf { it.hasVibrator() } ?: return
        runCatching { v.vibrate(effect(pattern, v.hasAmplitudeControl())) }
    }

    private fun effect(pattern: HapticPattern, amplitudes: Boolean): VibrationEffect = when (pattern) {
        HapticPattern.Tick ->
            if (Build.VERSION.SDK_INT >= 29) VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
            else VibrationEffect.createOneShot(TICK_MS, VibrationEffect.DEFAULT_AMPLITUDE)
        HapticPattern.RestEnd -> waveform(REST_END_TIMINGS, REST_END_AMPLITUDES, amplitudes)
        HapticPattern.Reveal -> waveform(REVEAL_TIMINGS, REVEAL_AMPLITUDES, amplitudes)
    }

    private fun waveform(timings: LongArray, amps: IntArray, amplitudes: Boolean): VibrationEffect =
        if (amplitudes) VibrationEffect.createWaveform(timings, amps, -1) else VibrationEffect.createWaveform(timings, -1)

    internal companion object {
        const val TICK_MS = 15L
        /** Two firm pulses. Also used for the notification channel at the end of a rest. */
        val REST_END_TIMINGS = longArrayOf(0, 180, 120, 180)
        val REST_END_AMPLITUDES = intArrayOf(0, 255, 0, 255)
        /** Three rising steps. */
        val REVEAL_TIMINGS = longArrayOf(0, 30, 70, 40, 70, 70)
        val REVEAL_AMPLITUDES = intArrayOf(0, 70, 0, 150, 0, 255)
    }
}
