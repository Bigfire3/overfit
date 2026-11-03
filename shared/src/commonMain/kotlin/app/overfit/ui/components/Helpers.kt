package app.overfit.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Width of [text] in [style], plus room for the cursor. Lets an input hug its content. */
@Composable
internal fun IntrinsicTextWidth(text: String, style: TextStyle): Dp {
    val measurer = rememberTextMeasurer()
    val px = remember(text, style) { measurer.measure(text, style).size.width }
    return with(LocalDensity.current) { px.toDp() } + 4.dp
}

/** Calls [onLost] when the element loses focus after having had it. */
@Composable
internal fun Modifier.onFocusLost(onLost: () -> Unit): Modifier {
    var hadFocus by remember { mutableStateOf(false) }
    return onFocusChanged { state ->
        if (hadFocus && !state.isFocused) onLost()
        hadFocus = state.isFocused
    }
}
