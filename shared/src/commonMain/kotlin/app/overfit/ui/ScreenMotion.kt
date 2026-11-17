package app.overfit.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.ui.unit.IntOffset
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import app.overfit.ui.theme.Motion

/**
 * How one screen follows another (DECISIONS "Bewegung"). The screens have places, and a change shows the way:
 * - **Push / back**: a screen that goes deeper comes in from the trailing edge and lies on top; the one it leaves
 *   moves a quarter aside and dims. Back is the same in reverse. The back gesture drives it with the finger
 *   (linear, see [predictivePopEnter]); released, it runs on or returns.
 * - **Cover**: the running workout (and the photo viewer) is a layer of its own: it rises from the bottom over the
 *   screen below, which steps back a little, and sinks again when minimized, to where the mini bar sits.
 * - **Tabs**: switching tabs swaps the screens at once (the bar never moves; the new content settles in softly inside
 *   [TabScreen]). Closing the coach story is instant as well.
 * - **Reduced motion**: every change is the short fade-through the app had before (old screen out, new one in).
 *
 * All of it runs on [Motion.screen]; nothing here has a duration of its own except the finger-driven back.
 */
internal object ScreenMotion {
    /**
     * Longest time taps are swallowed while two screens are visible, see [AppScaffold]: long enough for the second tap
     * of a double tap, shorter than the tail of the spring (the new screen is in place and usable by then).
     */
    const val MAX_BLOCK_MS = 300L

    /** Length of the finger-driven back transition. Linear, so the screen stays under the finger. */
    private const val GESTURE_MS = 320

    /** `swipeEdge` of a back gesture that started at the right edge (BackEventCompat.EDGE_RIGHT). */
    private const val EDGE_RIGHT = 1

    private const val FADE_THROUGH_OUT_MS = 90
    private const val FADE_THROUGH_IN_MS = 150

    /** How far the screen underneath moves along (a quarter of the width) and how dark it gets. */
    private const val PARALLAX = 4
    private const val DIMMED = 0.4f
    /** How far the screen under a cover steps back. */
    private const val STEPPED_BACK = 0.94f

    private fun NavDestination.isTab() = Tab.entries.any { hasRoute(it.routeClass) }
    private fun NavDestination.isStory() = hasRoute(CoachStoryRoute::class)
    /**
     * Layers of their own that rise from the bottom instead of being pushed from the side: the running workout, and
     * the two tasks that are opened to bring something back and then close again (pick exercises, fill in the
     * exercise form). Their header closes with ✕ / a chevron down, not with the back chevron.
     */
    private fun NavDestination.isCover() =
        hasRoute(WorkoutRoute::class) || hasRoute(ExercisePickerRoute::class) || hasRoute(CustomExerciseRoute::class)
    /** Full-screen pictures: they grow out of the screen and fall back into it. */
    private fun NavDestination.isViewer() = hasRoute(PhotoViewerRoute::class) || hasRoute(PhotoCompareRoute::class) || isStory()

    private enum class Kind { Instant, Push, Cover, Zoom, Fade }

    /** [deeper] = the screen that is further down the path: the target when going forward, the origin when going back. */
    private fun kind(from: NavDestination, to: NavDestination, back: Boolean): Kind {
        val deeper = if (back) from else to
        val shallower = if (back) to else from
        return when {
            (from.isTab() && to.isTab()) || (from.isStory() && to.isTab()) -> Kind.Instant
            deeper.isViewer() -> Kind.Zoom
            deeper.isCover() -> Kind.Cover
            // Forwards into a tab (workout closed, finish saved, onboarding done): that is going home, the stack
            // below is gone and there is nothing to slide over.
            !back && to.isTab() -> Kind.Fade
            // The finished workout turns into its summary in place; the summary is the end of the same layer.
            !back && from.isCover() && to.hasRoute(FinishRoute::class) -> Kind.Fade
            // "Save" on a new workout hands over to the same workout as a plan: one screen becoming the other, not a
            // second screen pushed in.
            !back && from.hasRoute(TemplateEditorRoute::class) && to.hasRoute(WorkoutPlanRoute::class) -> Kind.Fade
            else -> Kind.Push
        }
    }

    private fun <T> AnimatedContentTransitionScope<T>.towards(back: Boolean) = if (back) SlideDirection.End else SlideDirection.Start

    private fun fadeThroughIn() = fadeIn(tween(FADE_THROUGH_IN_MS, delayMillis = FADE_THROUGH_OUT_MS, easing = LinearOutSlowInEasing))
    private fun fadeThroughOut() = fadeOut(tween(FADE_THROUGH_OUT_MS, easing = LinearEasing))

    /** The screen that becomes visible. [back] = it lay underneath and returns. */
    private fun AnimatedContentTransitionScope<NavBackStackEntry>.entering(
        back: Boolean,
        reduced: Boolean,
        offset: FiniteAnimationSpec<IntOffset> = Motion.screen(IntOffset.VisibilityThreshold),
        float: FiniteAnimationSpec<Float> = Motion.screen(),
        direction: SlideDirection = towards(back),
    ): EnterTransition {
        val kind = kind(initialState.destination, targetState.destination, back)
        if (kind == Kind.Instant) return EnterTransition.None
        if (reduced || kind == Kind.Fade) return fadeThroughIn()
        return when (kind) {
            Kind.Push ->
                if (back) slideIntoContainer(direction, offset) { it / PARALLAX } + fadeIn(float, initialAlpha = DIMMED)
                else slideIntoContainer(direction, offset)
            Kind.Cover ->
                if (back) scaleIn(float, initialScale = STEPPED_BACK) + fadeIn(float, initialAlpha = DIMMED)
                else slideInVertically(offset) { it }
            Kind.Zoom ->
                if (back) fadeIn(float, initialAlpha = DIMMED)
                else fadeIn(Motion.fadeIn()) + scaleIn(float, initialScale = 0.92f)
            else -> fadeThroughIn()
        }
    }

    /** The screen that goes away. [back] = it lay on top and is closed. */
    private fun AnimatedContentTransitionScope<NavBackStackEntry>.leaving(
        back: Boolean,
        reduced: Boolean,
        offset: FiniteAnimationSpec<IntOffset> = Motion.screen(IntOffset.VisibilityThreshold),
        float: FiniteAnimationSpec<Float> = Motion.screen(),
        direction: SlideDirection = towards(back),
    ): ExitTransition {
        val kind = kind(initialState.destination, targetState.destination, back)
        if (kind == Kind.Instant) return ExitTransition.None
        if (reduced || kind == Kind.Fade) return fadeThroughOut()
        return when (kind) {
            Kind.Push ->
                if (back) slideOutOfContainer(direction, offset)
                else slideOutOfContainer(direction, offset) { it / PARALLAX } + fadeOut(float, targetAlpha = DIMMED)
            Kind.Cover ->
                if (back) slideOutVertically(offset) { it }
                else scaleOut(float, targetScale = STEPPED_BACK) + fadeOut(float, targetAlpha = DIMMED)
            Kind.Zoom ->
                if (back) fadeOut(Motion.fadeOut()) + scaleOut(float, targetScale = 0.92f)
                else fadeOut(float, targetAlpha = DIMMED)
            else -> fadeThroughOut()
        }
    }

    fun enter(scope: AnimatedContentTransitionScope<NavBackStackEntry>, reduced: Boolean): EnterTransition = scope.entering(back = false, reduced)
    fun exit(scope: AnimatedContentTransitionScope<NavBackStackEntry>, reduced: Boolean): ExitTransition = scope.leaving(back = false, reduced)
    fun popEnter(scope: AnimatedContentTransitionScope<NavBackStackEntry>, reduced: Boolean): EnterTransition = scope.entering(back = true, reduced)
    fun popExit(scope: AnimatedContentTransitionScope<NavBackStackEntry>, reduced: Boolean): ExitTransition = scope.leaving(back = true, reduced)

    /**
     * Back by gesture: the same picture as [popEnter]/[popExit], but linear in time, because the gesture's progress is
     * mapped onto it: the screen then moves with the finger instead of running ahead of it. A gesture from the right
     * edge moves the screen to the left, where the finger goes.
     */
    fun predictivePopEnter(scope: AnimatedContentTransitionScope<NavBackStackEntry>, swipeEdge: Int, reduced: Boolean): EnterTransition =
        scope.entering(back = true, reduced, tween(GESTURE_MS, easing = LinearEasing), tween(GESTURE_MS, easing = LinearEasing), edge(swipeEdge))

    fun predictivePopExit(scope: AnimatedContentTransitionScope<NavBackStackEntry>, swipeEdge: Int, reduced: Boolean): ExitTransition =
        scope.leaving(back = true, reduced, tween(GESTURE_MS, easing = LinearEasing), tween(GESTURE_MS, easing = LinearEasing), edge(swipeEdge))

    private fun edge(swipeEdge: Int) = if (swipeEdge == EDGE_RIGHT) SlideDirection.Left else SlideDirection.Right
}
