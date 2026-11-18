package app.overfit.ui.workout

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.overfit.domain.RestTimer
import app.overfit.i18n.LocalStrings
import app.overfit.ui.components.ButtonHeight
import app.overfit.ui.components.ButtonShape
import app.overfit.ui.components.PressScale
import app.overfit.ui.components.RollingText
import app.overfit.ui.components.pressScale
import app.overfit.ui.components.rememberHaptics
import app.overfit.ui.theme.Motion
import app.overfit.ui.theme.Sizes
import app.overfit.ui.theme.Space
import app.overfit.ui.theme.Theme
import app.overfit.ui.theme.TouchTarget
import app.overfit.workout.WorkoutFormat

/** Test tags of the rest timer above the main button of the running workout and of its "skip" label. */
const val REST_BAR_TAG = "rest-bar"
const val REST_SKIP_LABEL_TAG = "rest-bar:skip-label"

/** From this font scale on "skip" moves below the countdown: side by side it would be cut off on a 360 dp phone. */
private const val STACKED_FONT_SCALE = 1.15f

/** The widest countdowns the island makes room for (tabular digits: every digit is as wide as a 0). */
private const val COUNTDOWN_SHORT = "0:00"
private const val COUNTDOWN_LONG = "00:00"

/**
 * Rest timer of the running workout: one compact island above the main button, never full screen, the list above
 * stays scrollable and editable. It shows what a rest needs and nothing else (owner: enlarging the time was
 * useless): the countdown, big and in the "now" colour, framed by −15 / +15, and "skip" over the rest of the width.
 * The island itself fills from the left as the rest passes, so the share that is over is visible without reading.
 * What comes next stands in the main button below; a tap on the countdown scrolls the list to that set.
 */
@Composable
fun RestBar(
    rest: RestTimer,
    /** The workout clock (ticks every 250 ms). Read here, so only the island follows it. */
    now: State<Long>,
    /** "Next: Bench press · set 2 · 60 kg × 8" for screen readers; null when nothing is open. */
    nextLine: String?,
    onAdjust: (deltaSec: Int) -> Unit,
    onSkip: () -> Unit,
    /** Tap on the countdown: show the set that is next. Null = there is none. */
    onShowNext: (() -> Unit)?,
) {
    val t = LocalStrings.current
    val c = Theme.colors
    // The text changes once a second: only then the island recomposes.
    val countdown by remember(rest, now) { derivedStateOf { WorkoutFormat.countdown(rest.remainingMs(now.value)) } }
    // The workout clock ticks every 250 ms; between two ticks the fill glides on (and rests before the next one).
    // Driven outside composition and read while drawing: the island is only redrawn.
    val progress = remember(now) { Animatable(rest.progress(now.value)) }
    LaunchedEffect(rest, now) {
        snapshotFlow { rest.progress(now.value) }.collect { progress.animateTo(it, Motion.tick()) }
    }
    // Side by side as long as it fits: with large type, or a rest of ten minutes and more (a fifth character), "skip"
    // gets its own line instead of being cut off. Decided once per rest (as it begins): a countdown that crosses
    // 10:00 (+15 tapped twice at 9:50) must not move the buttons under the finger.
    val fontScale = LocalDensity.current.fontScale
    val stacked = remember(rest.startedAt, fontScale) {
        fontScale >= STACKED_FONT_SCALE || WorkoutFormat.countdown(rest.totalMs).length > 4
    }

    // The countdown has a fixed width for the whole rest, so −15 / +15 never move: stacked it has room for "10:00";
    // side by side "skip" needs that room, and a countdown that grows past 10:00 there (+15 tapped at 9:50) is drawn a
    // little smaller instead.
    val titleStyle = Theme.type.tabular(Theme.type.title)
    val measurer = rememberTextMeasurer(cacheSize = 2)
    val density = LocalDensity.current
    val countdownWidth = remember(titleStyle, density, stacked) {
        // Piece by piece, as [RollingText] sets it (every digit and every run between digits is a text of its own).
        val widest = if (stacked) COUNTDOWN_LONG else COUNTDOWN_SHORT
        with(density) { widest.sumOf { measurer.measure(it.toString(), titleStyle, maxLines = 1, softWrap = false).size.width }.toDp() }
    }

    @Composable
    fun RowScope.Stepper() {
        StepButton(t.restMinusShort, t.restMinus) { onAdjust(-15) }
        val interaction = remember { MutableInteractionSource() }
        val described = listOfNotNull(t.rest, countdown, nextLine).joinToString(", ")
        Box(
            Modifier
                .heightIn(min = TouchTarget.min)
                .width(countdownWidth + 4.dp)
                .then(
                    if (onShowNext != null) Modifier.pressScale(interaction, PressScale.button)
                        .clickable(interaction, indication = null, role = Role.Button, onClick = onShowNext)
                    else Modifier,
                )
                .padding(horizontal = 2.dp)
                .semantics(mergeDescendants = true) { contentDescription = described },
            contentAlignment = Alignment.Center,
        ) {
            // Rolls down second by second; tabular digits keep the buttons beside it in place.
            RollingText(countdown, Modifier.scaleDownToFit(), style = Theme.type.title, color = c.accent, countsDown = true)
        }
        StepButton(t.restPlusShort, t.restPlus) { onAdjust(15) }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .testTag(REST_BAR_TAG)
            .clip(ButtonShape)
            .background(c.surface)
            .drawBehind { drawRect(c.accentSubtle, size = Size(size.width * progress.value, size.height)) }
            .padding(horizontal = Space.xs, vertical = 6.dp),
    ) {
        if (stacked) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Stepper()
            }
            SkipButton(t.restSkip, onSkip, Modifier.fillMaxWidth().padding(horizontal = 2.dp))
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Stepper()
                Spacer(Modifier.width(Space.xs))
                // The action used most gets the rest of the width: easy to hit with a tired thumb.
                SkipButton(t.restSkip, onSkip, Modifier.weight(1f).padding(end = 2.dp))
            }
        }
    }
}

/**
 * Content wider than the room it gets is drawn smaller as a whole (centred) instead of pushing its neighbours aside or
 * being cut; content that fits is left as it is.
 */
private fun Modifier.scaleDownToFit(): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minWidth = 0, maxWidth = Constraints.Infinity))
    val width = (if (constraints.hasBoundedWidth) minOf(placeable.width, constraints.maxWidth) else placeable.width)
        .coerceAtLeast(constraints.minWidth)
    val scale = if (placeable.width > width) width.toFloat() / placeable.width else 1f
    layout(width, placeable.height) {
        placeable.placeWithLayer((width - placeable.width) / 2, 0) { scaleX = scale; scaleY = scale }
    }
}

/** Round −15 / +15 button beside the countdown: [Sizes.stepButton] circle in a 48 dp touch area. */
@Composable
private fun StepButton(label: String, description: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val haptics = rememberHaptics()
    Box(
        Modifier
            .size(TouchTarget.min)
            .semantics(mergeDescendants = true) { contentDescription = description }
            .pressScale(interaction, PressScale.icon)
            .clickable(interaction, indication = null, role = Role.Button) { haptics.tick(); onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(Sizes.stepButton).background(Theme.colors.surfacePressed, CircleShape), contentAlignment = Alignment.Center) {
            BasicText(
                label,
                style = Theme.type.caption.copy(color = Theme.colors.foreground, fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

/**
 * "Skip" on the island: a pill one shade lighter than the island, 44 dp visible in a 48 dp touch area like the
 * compact buttons, but with slimmer sides, so the label keeps a readable size next to the countdown on a 360 dp phone.
 */
@Composable
private fun SkipButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier
            .pressScale(interaction)
            .clickable(interaction, indication = null, role = Role.Button, onClick = onClick)
            .padding(vertical = 2.dp)
            .heightIn(min = ButtonHeight.compact)
            .background(Theme.colors.surfacePressed, ButtonShape)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            label,
            Modifier.testTag(REST_SKIP_LABEL_TAG),
            style = Theme.type.label.copy(color = Theme.colors.foreground, fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            autoSize = TextAutoSize.StepBased(minFontSize = 12.sp, maxFontSize = 16.sp),
        )
    }
}
