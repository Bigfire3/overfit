package app.overfit.platform

import androidx.compose.runtime.Composable

/**
 * Whether the system asks for less motion: iOS "Reduce Motion" (Bedienungshilfen → Bewegung), Android with
 * animations switched off (animator duration scale 0). Read again whenever the app comes back to the foreground.
 */
@Composable
expect fun systemReducedMotion(): Boolean
