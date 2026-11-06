package app.overfit.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.ime
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalViewConfiguration

/** Remembers whether the touch that is just being handled began on a text field (see [textInputArea]). */
@Stable
class TextInputTouch {
    internal var onField = false
}

val LocalTextInputTouch = staticCompositionLocalOf { TextInputTouch() }

/**
 * Marks a text field: a touch that starts here keeps the focus (cursor placement, selection). Every touch that
 * starts anywhere else clears the focus ([clearFocusOutsideTextInput]). Put it on every `BasicTextField`.
 */
fun Modifier.textInputArea(): Modifier = composed {
    val touch = LocalTextInputTouch.current
    Modifier.pointerInput(touch) {
        awaitEachGesture {
            // Initial pass: before the root's final pass checks the flag, nothing is consumed.
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            touch.onField = true
        }
    }
}

/**
 * Whole app, once (in the theme): a touch outside any text field (empty area, list, another button) removes the focus,
 * so the keyboard closes and the cursor stops blinking; so does scrolling (also when the finger started on a field,
 * before a long press could start a text selection) and the keyboard being closed with Back.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Modifier.clearFocusOutsideTextInput(focus: FocusManager = LocalFocusManager.current): Modifier {
    val touch = LocalTextInputTouch.current
    val config = LocalViewConfiguration.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    val imeVisible = WindowInsets.ime.getBottom(density) > 0
    var wasVisible by remember { mutableStateOf(false) }
    LaunchedEffect(imeVisible) {
        // Keyboard closed by Back (or by hand): the field would otherwise keep a blinking cursor.
        if (wasVisible && !imeVisible) focus.clearFocus()
        wasVisible = imeVisible
    }
    return this.pointerInput(touch, focus) {
        awaitEachGesture {
            // Final pass: children (buttons, fields) have had their turn; the flag says whether a field was touched.
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Final)
            val onField = touch.onField
            touch.onField = false
            val slop = config.touchSlop
            while (true) {
                val change = awaitPointerEvent(PointerEventPass.Final).changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) {
                    // Cleared on release, not on touch: closing the keyboard moves the layout (imePadding) and would
                    // make a tap on a button miss. The button's click has already run in the main pass.
                    if (!onField) focus.clearFocus()
                    break
                }
                // A field touch may become a text selection after a long press, so only an early drag counts there.
                if (onField && change.uptimeMillis - down.uptimeMillis >= config.longPressTimeoutMillis) continue
                if ((change.position - down.position).getDistance() > slop) { focus.clearFocus(); break }
            }
        }
    }
}
