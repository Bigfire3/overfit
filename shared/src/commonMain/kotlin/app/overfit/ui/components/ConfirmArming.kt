package app.overfit.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import kotlinx.coroutines.delay

/**
 * At most one armed confirm button ([ConfirmButton], [HeaderDeleteButton]) in the whole app. Arming
 * one disarms the other; [ConfirmArea] disarms on a tap anywhere outside the armed button.
 */
@Stable
class ConfirmArming {
    /** Token of the armed button, null = nothing armed. */
    internal var armed by mutableStateOf<Any?>(null)
    /** Window bounds of the armed button: a tap inside them is the confirming tap, not "somewhere else". */
    internal var bounds: Rect? = null

    fun disarm() { armed = null; bounds = null }
}

/** Fallback for screens rendered without [ConfirmArea] (previews, single-screen tests). */
val LocalConfirmArming = staticCompositionLocalOf { ConfirmArming() }

/** Wraps the whole app once: a tap anywhere outside the armed confirm button, or any scroll/drag, disarms it. */
@Composable
fun ConfirmArea(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val arming = remember { ConfirmArming() }
    val origin = remember { arrayOf(Offset.Zero) }
    CompositionLocalProvider(LocalConfirmArming provides arming) {
        Box(
            modifier
                .onGloballyPositioned { origin[0] = it.positionInWindow() }
                .pointerInput(arming) {
                    awaitEachGesture {
                        // Initial pass: seen before the children handle the tap, nothing is consumed.
                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        if (arming.armed != null && arming.bounds?.contains(down.position + origin[0]) != true) arming.disarm()
                        // A finger that moves (scrolling, even when it started on the armed button) disarms too.
                        val slop = viewConfiguration.touchSlop
                        while (true) {
                            val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            if (arming.armed != null && (change.position - down.position).getDistance() > slop) arming.disarm()
                        }
                    }
                },
        ) { content() }
    }
}

/** State of one confirm button: armed or not, plus the modifier that reports its bounds while armed. */
internal class ConfirmArm(private val arming: ConfirmArming, private val token: Any) {
    val armed: Boolean get() = arming.armed === token
    fun arm() { arming.armed = token; arming.bounds = null }
    fun disarm() { if (armed) arming.disarm() }
    val modifier: Modifier = Modifier.onGloballyPositioned { if (armed) arming.bounds = it.boundsInWindow() }
}

/** How long a confirm button stays armed without the second tap. */
const val CONFIRM_ARMED_MILLIS = 3000L

/** Arms for at most [CONFIRM_ARMED_MILLIS]. */
@Composable
internal fun rememberConfirmArm(): ConfirmArm {
    val arming = LocalConfirmArming.current
    val arm = remember(arming) { ConfirmArm(arming, Any()) }
    val armed = arm.armed
    LaunchedEffect(armed) {
        if (armed) { delay(CONFIRM_ARMED_MILLIS); arm.disarm() }
    }
    DisposableEffect(arm) { onDispose { arm.disarm() } }
    return arm
}
