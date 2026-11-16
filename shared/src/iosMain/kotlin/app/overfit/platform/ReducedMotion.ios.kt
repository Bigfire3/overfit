package app.overfit.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.LifecycleResumeEffect
import platform.UIKit.UIAccessibilityIsReduceMotionEnabled

@Composable
actual fun systemReducedMotion(): Boolean {
    var reduced by remember { mutableStateOf(UIAccessibilityIsReduceMotionEnabled()) }
    LifecycleResumeEffect(Unit) {
        reduced = UIAccessibilityIsReduceMotionEnabled()
        onPauseOrDispose { }
    }
    return reduced
}
