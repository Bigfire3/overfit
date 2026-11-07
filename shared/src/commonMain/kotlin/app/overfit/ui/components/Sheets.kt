package app.overfit.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.overfit.ui.theme.Space
import app.overfit.ui.theme.Theme

/** Large top corners of a [BottomSheet] and all corners of a [CenterPopup] (like the buttons). */
val SheetShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
val PopupShape = RoundedCornerShape(24.dp)

/** Black scrim behind a sheet or popup: a tap on it (or system back) closes. */
@Composable
private fun Scrim(onDismiss: () -> Unit, closeLabel: String) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .semantics { contentDescription = closeLabel }
            .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button, onClick = onDismiss),
    )
}

/** Swallows taps on the sheet itself, so they do not reach the scrim underneath. */
private fun Modifier.absorbTaps(): Modifier = pointerInput(Unit) { detectTapGestures { } }

/**
 * Bottom sheet as an overlay inside the layout (no window, no dialog): black scrim (~60 %), a [Colors.surface] area
 * at the bottom with large top corners, a grab handle, scrollable [content] and an optional fixed [footer] (the one
 * primary button). No border, no shadow. A tap on the scrim or system back calls [onDismiss]. Put it last in a
 * full-size Box so it lies above the screen.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun BottomSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    closeLabel: String,
    modifier: Modifier = Modifier,
    footer: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    BackHandler(enabled = visible, onBack = onDismiss)
    AnimatedVisibility(visible, enter = fadeIn(tween(180)), exit = fadeOut(tween(150))) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val maxSheet = maxHeight * 0.88f
            Scrim(onDismiss, closeLabel)
            Column(
                modifier
                    .align(Alignment.BottomCenter)
                    .animateEnterExit(enter = slideInVertically(tween(220)) { it / 3 }, exit = slideOutVertically(tween(150)) { it / 3 })
                    .fillMaxWidth()
                    .heightIn(max = maxSheet)
                    .clip(SheetShape)
                    .background(Theme.colors.surface)
                    .absorbTaps()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(horizontal = Space.m),
            ) {
                // Grab handle.
                Box(Modifier.fillMaxWidth().padding(top = Space.s, bottom = Space.s), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(width = 36.dp, height = 4.dp).clip(RoundedCornerShape(2.dp)).background(Theme.colors.faint))
                }
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), content = content)
                if (footer != null) {
                    Spacer(Modifier.height(Space.m))
                    footer()
                }
                Spacer(Modifier.height(Space.m))
            }
        }
    }
}

/**
 * Popup in the middle of the screen as an overlay inside the layout: same scrim as [BottomSheet], a centred
 * [Colors.surface] area with 24 dp corners and a maximum width, scrollable [content]. No border, no shadow.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun CenterPopup(
    visible: Boolean,
    onDismiss: () -> Unit,
    closeLabel: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    BackHandler(enabled = visible, onBack = onDismiss)
    AnimatedVisibility(visible, enter = fadeIn(tween(180)), exit = fadeOut(tween(150))) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val maxPopup = maxHeight * 0.9f
            Scrim(onDismiss, closeLabel)
            Column(
                modifier
                    .align(Alignment.Center)
                    .animateEnterExit(enter = scaleIn(tween(180), initialScale = 0.96f), exit = scaleOut(tween(150), targetScale = 0.96f))
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(Space.m)
                    .widthIn(max = 440.dp)
                    .fillMaxWidth()
                    .heightIn(max = maxPopup)
                    .clip(PopupShape)
                    .background(Theme.colors.surface)
                    .absorbTaps()
                    .verticalScroll(rememberScrollState())
                    .padding(Space.l),
                content = content,
            )
        }
    }
}
