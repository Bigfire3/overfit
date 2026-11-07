package app.overfit.platform

/**
 * Vibration patterns of the app. Kept few and distinct so each one means one thing:
 * - [Tick]: short, light confirmation (set checked off).
 * - [RestEnd]: three short pulses (rest is over).
 */
enum class HapticPattern { Tick, RestEnd }

/** Platform vibration: Android Vibrator/VibrationEffect, iOS UIFeedbackGenerator. */
interface Haptics {
    fun play(pattern: HapticPattern)
}

object NoHaptics : Haptics {
    override fun play(pattern: HapticPattern) = Unit
}

/** Records what was played (tests). */
class RecordingHaptics : Haptics {
    val played = mutableListOf<HapticPattern>()
    override fun play(pattern: HapticPattern) { played += pattern }
}
