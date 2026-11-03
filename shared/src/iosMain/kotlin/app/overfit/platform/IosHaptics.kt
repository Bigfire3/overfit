package app.overfit.platform

import platform.UIKit.UIImpactFeedbackGenerator
import platform.UIKit.UIImpactFeedbackStyle
import platform.UIKit.UINotificationFeedbackGenerator
import platform.UIKit.UINotificationFeedbackType
import platform.darwin.DISPATCH_TIME_NOW
import platform.darwin.dispatch_after
import platform.darwin.dispatch_get_main_queue
import platform.darwin.dispatch_time

/** Haptics via UIFeedbackGenerator (Taptic Engine). Call on the main thread. */
class IosHaptics : Haptics {
    override fun play(pattern: HapticPattern) {
        when (pattern) {
            HapticPattern.Tick -> impact(UIImpactFeedbackStyle.UIImpactFeedbackStyleLight)
            HapticPattern.RestEnd ->
                UINotificationFeedbackGenerator().notificationOccurred(UINotificationFeedbackType.UINotificationFeedbackTypeSuccess)
            HapticPattern.Reveal -> {
                // Three rising steps, like the Android waveform.
                impact(UIImpactFeedbackStyle.UIImpactFeedbackStyleLight)
                later(100) { impact(UIImpactFeedbackStyle.UIImpactFeedbackStyleMedium) }
                later(210) { impact(UIImpactFeedbackStyle.UIImpactFeedbackStyleHeavy) }
            }
        }
    }

    private fun impact(style: UIImpactFeedbackStyle) = UIImpactFeedbackGenerator(style).impactOccurred()

    private fun later(ms: Long, block: () -> Unit) =
        dispatch_after(dispatch_time(DISPATCH_TIME_NOW, ms * 1_000_000), dispatch_get_main_queue(), block)
}
