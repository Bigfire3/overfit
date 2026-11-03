package app.overfit.platform

/**
 * Vibration patterns of the app. Kept few and distinct so each one means one thing:
 * - [Tick]: short, light confirmation (set checked off).
 * - [RestEnd]: two firm pulses (rest is over).
 * - [Reveal]: rising three-step pattern for a moment of reveal (prepared for the later
 *   Fuel/Spot session, not used yet).
 */
enum class HapticPattern { Tick, RestEnd, Reveal }

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
