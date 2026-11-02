package app.overfit.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontWeight

private val Base = Typography()

/** Etwas kräftigere Überschriften, sonst Material-3-Standard. */
val OverfitTypography = Typography(
    headlineLarge = Base.headlineLarge.copy(fontWeight = FontWeight.Bold),
    headlineMedium = Base.headlineMedium.copy(fontWeight = FontWeight.Bold),
    titleLarge = Base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = Base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
)
