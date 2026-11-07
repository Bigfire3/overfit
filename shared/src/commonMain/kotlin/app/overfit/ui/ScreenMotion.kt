package app.overfit.ui

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute

/**
 * The one screen transition of the app (DECISIONS "Übergänge"):
 * - switching tabs is instant (like the tab bar itself, which never animates),
 * - every other screen change is a short fade-through: the old screen fades out in [OUT_MS], then the new
 *   one fades in. The two screens are never half-transparent on top of each other, nothing slides or jumps.
 */
internal object ScreenMotion {
    const val OUT_MS = 90
    const val IN_MS = 150

    private fun NavDestination.isTab() = Tab.entries.any { hasRoute(it.routeClass) }
    private fun NavDestination.isStory() = hasRoute(CoachStoryRoute::class)
    // Closing the story (auto, X, swipe, back) is instant: tab bar and Coach page appear in the same frame.
    private fun tabSwitch(from: NavDestination, to: NavDestination) =
        (from.isTab() && to.isTab()) || (from.isStory() && to.isTab())

    fun enter(from: NavDestination, to: NavDestination): EnterTransition =
        if (tabSwitch(from, to)) EnterTransition.None
        else fadeIn(tween(IN_MS, delayMillis = OUT_MS, easing = LinearOutSlowInEasing))

    fun exit(from: NavDestination, to: NavDestination): ExitTransition =
        if (tabSwitch(from, to)) ExitTransition.None
        else fadeOut(tween(OUT_MS, easing = LinearEasing))
}
