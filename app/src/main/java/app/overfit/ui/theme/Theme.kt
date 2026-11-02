package app.overfit.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val OverfitColors = darkColorScheme(
    primary = Accent,
    onPrimary = OnAccent,
    primaryContainer = AccentContainer,
    onPrimaryContainer = OnAccentContainer,
    secondary = Done,
    onSecondary = Background,
    secondaryContainer = DoneContainer,
    onSecondaryContainer = Done,
    background = Background,
    onBackground = OnSurface,
    surface = Surface,
    onSurface = OnSurface,
    surfaceVariant = SurfaceHigh,
    onSurfaceVariant = OnSurfaceVariant,
    surfaceContainerLowest = Background,
    surfaceContainerLow = Surface,
    surfaceContainer = Surface,
    surfaceContainerHigh = SurfaceHigh,
    surfaceContainerHighest = SurfaceHighest,
    outline = Outline,
    outlineVariant = SurfaceHighest,
    error = Error,
)

/** Overfit ist bewusst immer dunkel (SPEC: dunkles Theme). */
@Composable
fun OverfitTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = OverfitColors,
        typography = OverfitTypography,
        content = content,
    )
}
