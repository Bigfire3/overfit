package app.overfit.ui.theme

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * The motion of the whole app (DESIGN.md "Motion", DECISIONS "Bewegung"). Like sizes and colours, motion only comes
 * from these tokens: no per-screen durations or easings.
 *
 * Rules:
 * - Springs, not durations: a spring can be retargeted at any moment (a second tap, a finger that turns around)
 *   without a jump. Nothing here bounces except [bouncy], which is for the rare moment of emphasis.
 * - Everything settles within ~350 ms and never blocks input.
 * - Nothing loops. An animation that is driven by the 250 ms clock tick of a running workout uses [tick], which ends
 *   before the next tick: a self-retriggering animation never lets the UI (and the UI tests) come to rest.
 * - With "reduce motion" ([LocalReducedMotion]) slides become fades and rolling digits just change.
 */
object Motion {
    /** Small state changes: fills, toggles, icons, the release of a pressed button. Settles in ~180 ms. */
    fun <T> snappy(visibilityThreshold: T? = null): SpringSpec<T> =
        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 1400f, visibilityThreshold = visibilityThreshold)

    /** Layout: something grows, shrinks, moves to a new place; sheets. Settles in ~300 ms. */
    fun <T> smooth(visibilityThreshold: T? = null): SpringSpec<T> =
        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 420f, visibilityThreshold = visibilityThreshold)

    /** A whole screen sliding in or out: a little stiffer than [smooth], the screen is usable after ~250 ms. */
    fun <T> screen(visibilityThreshold: T? = null): SpringSpec<T> =
        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 560f, visibilityThreshold = visibilityThreshold)

    /** Rare emphasis (the tick of a finished set, a new record): one small overshoot. */
    fun <T> bouncy(visibilityThreshold: T? = null): SpringSpec<T> =
        spring(dampingRatio = 0.58f, stiffness = 520f, visibilityThreshold = visibilityThreshold)

    const val FADE_IN_MS = 160
    const val FADE_OUT_MS = 110

    /** Something becomes visible in place. */
    fun <T> fadeIn(delayMillis: Int = 0): TweenSpec<T> = tween(FADE_IN_MS, delayMillis, LinearOutSlowInEasing)

    /** Something goes away in place: shorter than [fadeIn], what leaves should not linger. */
    fun <T> fadeOut(): TweenSpec<T> = tween(FADE_OUT_MS, easing = LinearEasing)

    /** Longest animation that may hang on the 250 ms workout clock (progress of the rest timer). */
    const val TICK_MS = 200

    /** Linear step between two clock ticks, see the rule above. */
    fun <T> tick(): TweenSpec<T> = tween(TICK_MS, easing = LinearEasing)

    /** How long a pressed button stays pressed at least, so a quick tap is still seen (and felt). */
    const val MIN_PRESS_MS = 70

    /** Count-up of a number that has just appeared (finish summary). */
    const val COUNT_UP_MS = 700
}

/**
 * True when the system asks for less motion (iOS "Reduce Motion", Android animations off). Screen slides turn into
 * fades and decorative movement is left out; state still changes visibly.
 */
val LocalReducedMotion = staticCompositionLocalOf { false }
