package app.overfit.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import app.overfit.R
import kotlinx.serialization.Serializable
import kotlin.reflect.KClass

// Typsichere Routen für Navigation Compose.
@Serializable data object StartRoute
@Serializable data object HistoryRoute
@Serializable data object ExercisesRoute
@Serializable data object PhotosRoute
@Serializable data object SettingsRoute
@Serializable data object CoachMemoryRoute
@Serializable data object AboutRoute

/** Ziele der unteren Navigationsleiste. */
enum class TopLevelDestination(
    val route: Any,
    @StringRes val label: Int,
    val icon: ImageVector,
) {
    START(StartRoute, R.string.nav_start, Icons.Filled.Home),
    HISTORY(HistoryRoute, R.string.nav_history, Icons.Filled.DateRange),
    EXERCISES(ExercisesRoute, R.string.nav_exercises, Icons.AutoMirrored.Filled.List),
    PHOTOS(PhotosRoute, R.string.nav_photos, Icons.Filled.Face),
    SETTINGS(SettingsRoute, R.string.nav_settings, Icons.Filled.Settings);

    val routeClass: KClass<*> get() = route::class
}
