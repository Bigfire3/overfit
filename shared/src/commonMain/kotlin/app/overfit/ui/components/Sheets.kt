package app.overfit.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animate
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import app.overfit.ui.theme.LocalReducedMotion
import app.overfit.ui.theme.Motion
import app.overfit.ui.theme.Space
import app.overfit.ui.theme.Theme
import kotlinx.coroutines.launch

/** Large top corners of a [BottomSheet] and all corners of a [CenterPopup] (like the buttons). */
val SheetShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
val PopupShape = RoundedCornerShape(24.dp)

/** Darkest the scrim behind a sheet or popup gets. */
private const val SCRIM_ALPHA = 0.6f

/**
 * Black scrim behind a sheet or popup: a tap on it (or system back) closes. [alpha] is read while drawing.
 * Not [enabled] (the sheet is on its way out): it only fades, taps go through to the screen underneath.
 */
@Composable
private fun Scrim(onDismiss: () -> Unit, closeLabel: String, enabled: Boolean = true, alpha: () -> Float = { SCRIM_ALPHA }) {
    Box(
        Modifier
            .fillMaxSize()
            .drawBehind { drawRect(Color.Black, alpha = alpha().coerceIn(0f, 1f)) }
            .then(
                if (!enabled) Modifier else Modifier
                    .semantics { contentDescription = closeLabel }
                    .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button, onClick = onDismiss),
            ),
    )
}

/** Swallows taps on the sheet itself, so they do not reach the scrim underneath. */
internal fun Modifier.absorbTaps(): Modifier = pointerInput(Unit) { detectTapGestures { } }

/** Takes every touch before the children see it: what is on its way out no longer reacts (no second tap on an entry). */
internal fun Modifier.ignoreTouches(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope { while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() } }
}

/** Share of its height a sheet has to be pulled down so that letting go closes it; a quick flick down closes it earlier. */
private const val SHEET_DISMISS_FRACTION = 0.3f

/**
 * Bottom sheet as an overlay inside the layout (no window, no dialog): black scrim (~60 %), a [Colors.surface] area
 * at the bottom with large top corners, a grab handle, scrollable [content] and an optional fixed [footer] (the one
 * primary button). No border, no shadow. A tap on the scrim or system back calls [onDismiss]. Put it last in a
 * full-size Box so it lies above the screen.
 *
 * Motion: the sheet slides up on a spring and the scrim darkens with it (reduced motion: it fades in place instead). It follows the finger: pulled down by the
 * handle, by its content once that is scrolled to the top, or by the footer, it comes along, the scrim lightens, and
 * letting go either closes it (pulled far enough or flicked) or lets it spring back, keeping the finger's speed.
 * On its way out it takes no part any more: it cannot be grabbed back, its entries do not react a second time and a
 * tap next to it reaches the screen underneath.
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
    // 0 = fully open, 1 = fully below the screen. Entering, leaving and settling after a drag all move this one value.
    val hidden = remember { Animatable(1f) }
    // In the composition while visible and until it has slid out.
    var present by remember { mutableStateOf(false) }
    // Finger offset during a drag (px, ≥ 0); handed over to [hidden] on release.
    var dragPx by remember { mutableFloatStateOf(0f) }
    var sheetHeight by remember { mutableIntStateOf(0) }
    val dismiss by rememberUpdatedState(onDismiss)
    val open by rememberUpdatedState(visible)
    // Flying out after a pull or a flick: the caller only hears of it at the end ([onDismiss]).
    var leaving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val flick = with(LocalDensity.current) { 900.dp.toPx() }
    val reducedMotion = LocalReducedMotion.current

    LaunchedEffect(visible) {
        if (visible) {
            leaving = false
            present = true
            hidden.animateTo(0f, Motion.smooth())
        } else if (present) {
            try {
                hidden.animateTo(1f, Motion.smooth())
            } finally {
                // Also when the slide-out is cut short: a sheet that is closed must not stay in the composition.
                if (!open) { present = false; dragPx = 0f; leaving = false }
            }
        }
    }
    if (!visible && !present) return
    // Closed by the caller or flying out: no input any more, see the KDoc.
    val interactive = visible && !leaving

    fun fraction(): Float = (hidden.value + if (sheetHeight > 0) dragPx / sheetHeight else 0f).coerceIn(0f, 1f)

    // Returns how much of [dy] moved the sheet.
    fun drag(dy: Float): Float {
        if (!open || leaving) return 0f
        val before = dragPx
        val limit = sheetHeight * SHEET_DISMISS_FRACTION
        dragPx = (before + dy).coerceIn(0f, sheetHeight.toFloat())
        // One tick the moment letting go would close the sheet.
        if (before < limit && dragPx >= limit) haptics.threshold()
        return dragPx - before
    }

    fun settle(velocity: Float) {
        if (!open || leaving) return
        if (dragPx == 0f && hidden.value == 0f) return
        val h = sheetHeight.coerceAtLeast(1).toFloat()
        val close = velocity > flick || (dragPx / h > SHEET_DISMISS_FRACTION && velocity > -flick)
        if (close) leaving = true
        scope.launch {
            if (reducedMotion) {
                // Reduced motion: the sheet stays where the finger left it and fades out, or goes back up to it.
                if (close) {
                    hidden.animateTo(1f, Motion.smooth())
                    dismiss()
                } else {
                    animate(dragPx, 0f, animationSpec = Motion.smooth()) { value, _ -> dragPx = value }
                }
                return@launch
            }
            // Same place on screen, now owned by the spring; then on with the finger's speed.
            hidden.snapTo((hidden.value + dragPx / h).coerceIn(0f, 1f))
            dragPx = 0f
            if (close) {
                hidden.animateTo(1f, Motion.smooth(), initialVelocity = velocity / h)
                dismiss()
            } else {
                hidden.animateTo(0f, Motion.smooth(), initialVelocity = velocity / h)
            }
        }
    }

    // The scrollable content hands over what it cannot use: at its top a pull down moves the sheet, and a sheet
    // that is pulled down comes back up before the content scrolls.
    val connection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset =
                if (source == NestedScrollSource.UserInput && available.y < 0f && dragPx > 0f) Offset(0f, drag(available.y)) else Offset.Zero

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset =
                if (source == NestedScrollSource.UserInput && available.y > 0f) Offset(0f, drag(available.y)) else Offset.Zero

            override suspend fun onPreFling(available: Velocity): Velocity =
                if (dragPx > 0f) { settle(available.y); available } else Velocity.Zero
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val maxSheet = maxHeight * 0.88f
        Scrim(onDismiss, closeLabel, enabled = interactive) { SCRIM_ALPHA * (1f - fraction()) }
        Column(
            modifier
                .align(Alignment.BottomCenter)
                .onSizeChanged { sheetHeight = it.height }
                // Reduced motion: opening and closing fade in place; only the finger moves the sheet.
                .graphicsLayer {
                    if (reducedMotion) { translationY = dragPx; alpha = 1f - hidden.value }
                    else translationY = fraction() * size.height
                }
                .fillMaxWidth()
                .heightIn(max = maxSheet)
                .clip(SheetShape)
                .background(Theme.colors.surface)
                .then(if (interactive) Modifier.absorbTaps() else Modifier.ignoreTouches())
                // Handle, footer and everything that does not scroll: the sheet follows the finger directly.
                .draggable(rememberDraggableState { drag(it) }, Orientation.Vertical, enabled = interactive, onDragStopped = { settle(it) })
                .nestedScroll(connection)
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

/**
 * Popup in the middle of the screen as an overlay inside the layout: same scrim as [BottomSheet], a centred
 * [Colors.surface] area with 24 dp corners and a maximum width, scrollable [content]. No border, no shadow.
 * It grows out of the middle on a spring and fades; closing is a little quicker.
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
    AnimatedVisibility(visible, enter = fadeIn(Motion.fadeIn()), exit = fadeOut(Motion.fadeOut())) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val maxPopup = maxHeight * 0.9f
            Scrim(onDismiss, closeLabel)
            Column(
                modifier
                    .align(Alignment.Center)
                    .animateEnterExit(enter = scaleIn(Motion.smooth(), initialScale = 0.92f), exit = scaleOut(Motion.snappy(), targetScale = 0.96f))
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
