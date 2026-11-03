package app.overfit.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.overfit.domain.ThemeMode
import app.overfit.shared.resources.Res
import app.overfit.shared.resources.inter_medium
import app.overfit.shared.resources.inter_regular
import app.overfit.shared.resources.inter_semibold
import org.jetbrains.compose.resources.Font

/**
 * Black & white design system. No accent colour: hierarchy comes from
 * typography, whitespace and a few greys only.
 */
@Immutable
data class Colors(
    val background: Color,
    val foreground: Color,
    val muted: Color,
    val faint: Color,
    val hairline: Color,
    /** Inverted surfaces, e.g. the primary button. */
    val inverseBackground: Color,
    val inverseForeground: Color,
    val isDark: Boolean,
)

val DarkColors = Colors(
    background = Color(0xFF000000),
    foreground = Color(0xFFFFFFFF),
    muted = Color(0xFF8E8E93),
    faint = Color(0xFF48484A),
    hairline = Color(0xFF262628),
    inverseBackground = Color(0xFFFFFFFF),
    inverseForeground = Color(0xFF000000),
    isDark = true,
)

val LightColors = Colors(
    background = Color(0xFFFFFFFF),
    foreground = Color(0xFF000000),
    muted = Color(0xFF6E6E73),
    faint = Color(0xFFC7C7CC),
    hairline = Color(0xFFE5E5EA),
    inverseBackground = Color(0xFF000000),
    inverseForeground = Color(0xFFFFFFFF),
    isDark = false,
)

/** Only a handful of sizes on purpose. */
@Immutable
data class Typography(
    val hero: TextStyle,
    val title: TextStyle,
    val body: TextStyle,
    val label: TextStyle,
    val caption: TextStyle,
)

object Space {
    val xs = 4.dp
    val s = 8.dp
    val m = 16.dp
    val l = 24.dp
    val xl = 40.dp
    val xxl = 64.dp
}

val LocalColors = staticCompositionLocalOf { DarkColors }
val LocalTypography = staticCompositionLocalOf<Typography> { error("No typography provided") }

object Theme {
    val colors: Colors @Composable get() = LocalColors.current
    val type: Typography @Composable get() = LocalTypography.current
}

@Composable
private fun inter() = FontFamily(
    Font(Res.font.inter_regular, FontWeight.Normal),
    Font(Res.font.inter_medium, FontWeight.Medium),
    Font(Res.font.inter_semibold, FontWeight.SemiBold),
)

@Composable
fun ThemeMode.isDark(): Boolean = when (this) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.DARK -> true
    ThemeMode.LIGHT -> false
}

@Composable
fun OverfitTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = mode.isDark()
    val family = inter()
    val typography = Typography(
        hero = TextStyle(fontFamily = family, fontWeight = FontWeight.SemiBold, fontSize = 56.sp, lineHeight = 62.sp, letterSpacing = (-1.5).sp),
        title = TextStyle(fontFamily = family, fontWeight = FontWeight.SemiBold, fontSize = 34.sp, lineHeight = 40.sp, letterSpacing = (-0.8).sp),
        body = TextStyle(fontFamily = family, fontWeight = FontWeight.Normal, fontSize = 17.sp, lineHeight = 24.sp),
        label = TextStyle(fontFamily = family, fontWeight = FontWeight.Medium, fontSize = 17.sp, lineHeight = 24.sp),
        caption = TextStyle(fontFamily = family, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp),
    )
    CompositionLocalProvider(
        LocalColors provides if (dark) DarkColors else LightColors,
        LocalTypography provides typography,
        content = content,
    )
}
