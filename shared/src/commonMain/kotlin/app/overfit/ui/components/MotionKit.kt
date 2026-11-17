package app.overfit.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.overfit.ui.theme.LocalReducedMotion
import app.overfit.ui.theme.Motion
import app.overfit.ui.theme.Theme
import kotlinx.coroutines.flow.collectLatest

// The building blocks of the app's motion (DESIGN.md "Motion"). Screens use these instead of raw animation APIs, so
// everything that appears, leaves, changes or moves does it the same way. Specs come from [Motion].

// ---------------------------------------------------------------------------
// Appearing and leaving

/**
 * A block in a column that comes and goes (a hint, an undo row, the body of a collapsible card): it grows and shrinks
 * vertically and fades, so what stands below slides instead of jumping. [from] = the edge that stays in place.
 */
@Composable
fun Reveal(
    visible: Boolean,
    modifier: Modifier = Modifier,
    from: Alignment.Vertical = Alignment.Top,
    content: @Composable () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = expandVertically(Motion.smooth(IntSize.VisibilityThreshold), expandFrom = from) + fadeIn(Motion.fadeIn()),
        exit = shrinkVertically(Motion.smooth(IntSize.VisibilityThreshold), shrinkTowards = from) + fadeOut(Motion.fadeOut()),
        label = "reveal",
    ) { content() }
}

/**
 * [Reveal] for content that hangs on a value which turns null when the block should go (an error text, the last
 * deleted item): while it shrinks away, the block keeps showing the last value.
 */
@Composable
fun <T : Any> RevealNotNull(
    value: T?,
    modifier: Modifier = Modifier,
    from: Alignment.Vertical = Alignment.Top,
    content: @Composable (T) -> Unit,
) {
    val last = remember { arrayOfNulls<Any?>(1) }
    if (value != null) last[0] = value
    Reveal(value != null, modifier, from) {
        @Suppress("UNCHECKED_CAST")
        (last[0] as T?)?.let { content(it) }
    }
}

/** Like [Reveal], for something that comes and goes inside a row (an extra icon in a header): grows and shrinks sideways. */
@Composable
fun RevealInRow(visible: Boolean, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = expandHorizontally(Motion.smooth(IntSize.VisibilityThreshold)) + fadeIn(Motion.fadeIn()),
        exit = shrinkHorizontally(Motion.smooth(IntSize.VisibilityThreshold)) + fadeOut(Motion.fadeOut()),
        label = "revealInRow",
    ) { content() }
}

/**
 * A small thing that pops up in place without pushing anything (badge, floating button, marker): fades and scales.
 * Its place in the layout is taken at once; use [Reveal] when the surroundings should make room gradually.
 */
@Composable
fun Appear(visible: Boolean, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(Motion.fadeIn()) + scaleIn(Motion.snappy(), initialScale = 0.82f),
        exit = fadeOut(Motion.fadeOut()) + scaleOut(Motion.snappy(), targetScale = 0.82f),
        label = "appear",
    ) { content() }
}

/** [Appear] with one small overshoot, for the moment something is achieved (the tick of a finished set). */
@Composable
fun PopIn(visible: Boolean, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(Motion.fadeIn()) + scaleIn(Motion.bouncy(), initialScale = 0.3f),
        exit = fadeOut(Motion.fadeOut()) + scaleOut(Motion.snappy(), targetScale = 0.6f),
        label = "popIn",
    ) { content() }
}

/**
 * Content that is replaced in place (a label, an icon, loading → loaded, step 1 → step 2): the old one fades out, the
 * new one fades in and the size follows smoothly. [contentKey] decides what counts as "the same content".
 */
@Composable
fun <T> Swap(
    state: T,
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.TopStart,
    contentKey: (T) -> Any? = { it },
    content: @Composable (T) -> Unit,
) {
    AnimatedContent(
        targetState = state,
        modifier = modifier,
        contentAlignment = contentAlignment,
        contentKey = contentKey,
        transitionSpec = {
            (fadeIn(Motion.fadeIn(delayMillis = Motion.FADE_OUT_MS / 2)) togetherWith fadeOut(Motion.fadeOut()))
                .using(SizeTransform(clip = false) { _, _ -> Motion.smooth(IntSize.VisibilityThreshold) })
        },
        label = "swap",
    ) { content(it) }
}

// ---------------------------------------------------------------------------
// Lists

/**
 * Item of a lazy list that moves to its new place, fades in when added and out when removed. Needs a stable `key`.
 * Not for the item being dragged by [reorderableItem].
 */
fun LazyItemScope.motionItem(): Modifier = Modifier.animateItem(
    fadeInSpec = Motion.fadeIn(),
    placementSpec = Motion.smooth(IntOffset.VisibilityThreshold),
    fadeOutSpec = Motion.fadeOut(),
)

private class ItemSlot<T>(val key: Any, item: T, index: Int, visible: Boolean) {
    var item by mutableStateOf(item)
    var index by mutableIntStateOf(index)
    val visibility = MutableTransitionState(visible).apply { targetState = true }
}

/**
 * The children of a plain (not lazy) column that are added and removed over time: sets of an exercise, rows of a
 * panel. A new item grows in, a removed one shrinks away while the others slide; what is there at the start just is
 * there. [key] must be stable and unique. [spacing] goes below every item (not between: it has to shrink with the
 * item), so the column itself must not space its children.
 */
@Composable
fun <T> AnimatedItems(
    items: List<T>,
    key: (T) -> Any,
    spacing: Dp = 0.dp,
    content: @Composable (index: Int, item: T) -> Unit,
) {
    val slots = remember { ArrayList<ItemSlot<T>>() }
    val first = remember { booleanArrayOf(true) }
    val keys = items.map(key)
    // Reconcile during composition: the slot list is plain data derived from [items]; the transition states inside
    // are snapshot state, so a finished exit recomposes this and drops its slot.
    slots.forEach { if (it.key !in keys) it.visibility.targetState = false }
    slots.removeAll { !it.visibility.targetState && !it.visibility.currentState }
    var insertAt = 0
    items.forEachIndexed { i, item ->
        val at = slots.indexOfFirst { it.key == keys[i] }
        if (at >= 0) {
            slots[at].also { it.item = item; it.index = i; it.visibility.targetState = true }
            insertAt = at + 1
        } else {
            slots.add(insertAt, ItemSlot(keys[i], item, i, visible = first[0]))
            insertAt++
        }
    }
    first[0] = false
    slots.toList().forEach { slot ->
        key(slot.key) {
            AnimatedVisibility(
                visibleState = slot.visibility,
                enter = expandVertically(Motion.smooth(IntSize.VisibilityThreshold), expandFrom = Alignment.Top) + fadeIn(Motion.fadeIn()),
                exit = shrinkVertically(Motion.smooth(IntSize.VisibilityThreshold), shrinkTowards = Alignment.Top) + fadeOut(Motion.fadeOut()),
                label = "item",
            ) {
                Box(Modifier.padding(bottom = spacing)) { content(slot.index, slot.item) }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Values that change

/**
 * A number that changes while you look at it (countdown, elapsed time, counter): every digit that changes rolls to
 * its new value, the others stand still. Always tabular figures. [countsDown]: new digits come from above.
 * Screen readers and tests see one text.
 *
 * For short values on one line ("2:30", "3/20 erledigt", "4 Sätze · Pause 2:30"): it does not wrap. A sentence that
 * may need a second line is a `Swap(text) { Text(it) }`.
 */
@Composable
fun RollingText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = Theme.type.body,
    tone: Tone = Tone.Default,
    color: Color = Color.Unspecified,
    countsDown: Boolean = false,
) {
    val resolved = style.copy(color = if (color.isSpecified) color else tone.color(), fontFeatureSettings = "tnum")
    if (LocalReducedMotion.current) {
        BasicText(text, modifier, style = resolved, maxLines = 1, softWrap = false)
        return
    }
    Row(modifier.clearAndSetSemantics { this.text = AnnotatedString(text) }) {
        val n = text.length
        var i = 0
        while (i < n) {
            val c = text[i]
            // Columns are counted from the end: "9:59" → "10:00" keeps every digit in its place.
            if (c.isDigit()) {
                key(n - i) {
                    AnimatedContent(
                        targetState = c,
                        transitionSpec = {
                            val enter = slideInVertically(Motion.snappy(IntOffset.VisibilityThreshold)) { h -> (if (countsDown) -h else h) * 3 / 5 } + fadeIn(Motion.fadeIn())
                            val exit = slideOutVertically(Motion.snappy(IntOffset.VisibilityThreshold)) { h -> (if (countsDown) h else -h) * 3 / 5 } + fadeOut(Motion.fadeOut())
                            enter togetherWith exit
                        },
                        label = "digit",
                    ) { digit -> BasicText(digit.toString(), style = resolved, maxLines = 1, softWrap = false) }
                }
                i++
            } else {
                // Everything between two digits is one piece of text: words keep their normal letter spacing.
                var end = i
                while (end < n && !text[end].isDigit()) end++
                key(n - i) { BasicText(text.substring(i, end), style = resolved, maxLines = 1, softWrap = false) }
                i = end
            }
        }
    }
}

/**
 * A value that counts up from zero once when it first appears (the numbers of the finish summary): the returned state
 * runs to [target] in [Motion.COUNT_UP_MS]. Read it where the text is built, so only that text recomposes.
 */
@Composable
fun rememberCountUp(target: Float): State<Float> {
    val reduced = LocalReducedMotion.current
    val value = remember { Animatable(if (reduced) target else 0f) }
    LaunchedEffect(target, reduced) {
        if (reduced) value.snapTo(target) else value.animateTo(target, tween(Motion.COUNT_UP_MS, easing = FastOutSlowInEasing))
    }
    return value.asState()
}

/** A colour that follows its state ([target]) softly instead of flipping (selected chip, armed button, done row). */
@Composable
fun motionColor(target: Color, label: String = "color"): State<Color> =
    animateColorAsState(target, Motion.snappy(), label = label)

/** Thin progress bar whose filled part moves to a new value instead of jumping. [progress] 0..1. */
@Composable
fun ProgressLine(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = Theme.colors.foreground,
    track: Color = Theme.colors.hairline,
    height: Dp = 2.dp,
    spec: AnimationSpec<Float> = Motion.smooth(),
) {
    val shown by animateFloatAsState(progress.coerceIn(0f, 1f), spec, label = "progress")
    Box(
        modifier.fillMaxWidth().height(height).drawBehind {
            val r = CornerRadius(size.height / 2)
            drawRoundRect(track, cornerRadius = r)
            if (shown > 0f) drawRoundRect(color, size = Size(size.width * shown, size.height), cornerRadius = r)
        },
    )
}

// ---------------------------------------------------------------------------
// Touch

/**
 * How far [interaction] is pressed, 0..1, for press feedback drawn by hand ([pressScale], [pressableRow]). Unlike a
 * plain "is pressed" flag it never misses a quick tap: the way down ([press]) is always finished before the release
 * ([release]) starts, so every tap is seen for at least [Motion.MIN_PRESS_MS].
 */
@Composable
internal fun rememberPressProgress(
    interaction: InteractionSource,
    press: AnimationSpec<Float>,
    release: AnimationSpec<Float>,
): State<Float> {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(interaction) {
        interaction.interactions.collectLatest { event ->
            when (event) {
                is PressInteraction.Press -> progress.animateTo(1f, press)
                is PressInteraction.Release, is PressInteraction.Cancel -> {
                    if (progress.value < 1f) progress.animateTo(1f, press)
                    progress.animateTo(0f, release)
                }
            }
        }
    }
    return progress.asState()
}

/**
 * The haptic signals of the UI, by meaning. Each one belongs to one kind of event, so the hand learns them:
 * [tick] = a choice moved (chip, segment, stepper), [toggle] = a switch flipped, [press] = a main action was tapped,
 * [arm] = a two-tap button is armed, [confirm] = something is done or saved, [threshold] = a drag passed the point
 * where letting go acts (swipe to delete, sheet dismiss, picking up a card), [reject] = not possible.
 * The system setting for touch feedback decides whether they are felt.
 */
class UiHaptics internal constructor(private val feedback: HapticFeedback) {
    fun tick() = feedback.performHapticFeedback(HapticFeedbackType.SegmentTick)
    fun toggle(on: Boolean) = feedback.performHapticFeedback(if (on) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
    fun press() = feedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    fun arm() = feedback.performHapticFeedback(HapticFeedbackType.ContextClick)
    fun confirm() = feedback.performHapticFeedback(HapticFeedbackType.Confirm)
    fun threshold() = feedback.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
    fun reject() = feedback.performHapticFeedback(HapticFeedbackType.Reject)
}

@Composable
fun rememberHaptics(): UiHaptics {
    val feedback = LocalHapticFeedback.current
    return remember(feedback) { UiHaptics(feedback) }
}
