package app.overfit.ui.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusTarget
import app.overfit.ui.components.clearFocusOutsideTextInput
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.overfit.shared.resources.Res
import app.overfit.shared.resources.inter_medium
import app.overfit.shared.resources.inter_regular
import app.overfit.shared.resources.inter_semibold
import org.jetbrains.compose.resources.Font

/**
 * Black & white design system. No decorative accent colour: hierarchy comes from
 * typography, whitespace and a few greys. Dark only: there is no light mode (DECISIONS "Nur Dunkelmodus").
 *
 * Palette (see docs/DECISIONS.md, "Farbpalette"):
 * - Neutrals: [background], [foreground], [muted], [faint], [hairline], inverse pair. Contrast floor (ContrastTest):
 *   [muted] text ≥ 4.5:1 on background, [surface] and [surfacePressed]; [faint] (placeholders, empty calendar days,
 *   side notes on black) ≥ 4.5:1 on background and ≥ 3:1 on [surface]. Never [faint] text on [surfacePressed].
 * - Grey highlight: [surface] (highlighted row, chip, selected item, input field fill),
 *   [surfacePressed] (pressed / dragged state on top of a surface).
 * - Signal colours, ONLY for meaning in data, never decoration: [positive] (better than
 *   before, new record, completed) and [negative] (worse, missed, destructive confirmation).
 *   The *Subtle variants are low-contrast fills behind such values (e.g. a delta badge).
 * - [armed]: the one deliberate colour exception in the UI chrome: red icon/label/border of a destructive
 *   two-tap button after the first tap ("tap again to confirm"). Nowhere else.
 * - [confirmFill]: green fill of an armed two-tap button that completes something (finish workout). Red = delete, green = finish.
 *   Also the fill of a checked-off set row in the running workout (the whole row turns green).
 * - [inputFill]: translucent white of a number field, so it stands slightly lighter on any row (grey or green).
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
    /** Grey fill for highlighted rows, chips and selected items. */
    val surface: Color,
    /** Pressed or dragged state on top of [surface]. */
    val surfacePressed: Color,
    /** Green: improvement, record, done. Only for data, never decoration. */
    val positive: Color,
    val positiveSubtle: Color,
    /** Red: decline, missed, destructive. Only for data and confirmations. Light enough for text on every surface. */
    val negative: Color,
    val negativeSubtle: Color,
    /** Red of an armed two-tap confirm button (first tap done). The only accent in the chrome. */
    val armed: Color,
    /** Fill of an armed text button: darker red so white text on it reaches 4.5:1. */
    val armedFill: Color,
    /** Fill of an armed button that finishes something (finish workout): green, white text on it reaches 4.5:1. */
    val confirmFill: Color,
    /** Fill of a set row's number field: translucent white, slightly lighter than the row below (grey or green). */
    val inputFill: Color,
)

val DarkColors = Colors(
    background = Color(0xFF000000),
    foreground = Color(0xFFFFFFFF),
    muted = Color(0xFF98989D),
    faint = Color(0xFF757579),
    hairline = Color(0xFF262628),
    inverseBackground = Color(0xFFFFFFFF),
    inverseForeground = Color(0xFF000000),
    surface = Color(0xFF1C1C1E),
    surfacePressed = Color(0xFF2C2C2E),
    positive = Color(0xFF30D158),
    positiveSubtle = Color(0xFF0F2A17),
    negative = Color(0xFFFF6961),
    negativeSubtle = Color(0xFF331311),
    armed = Color(0xFFFF453A),
    armedFill = Color(0xFFD32F2F),
    confirmFill = Color(0xFF1E7D34),
    inputFill = Color(0x1FFFFFFF),
)

/**
 * Tabular figures (DESIGN.md "The Tabular Rule") for the big number styles. Running text keeps proportional figures
 * (tabular "1"s widen labels like "±15 s" and cut "Überspringen" off on 360 dp); a ticking value in body/label/caption
 * uses [Typography.tabular].
 */
private const val TNUM = "tnum"

/** Only a handful of sizes on purpose. */
@Immutable
data class Typography(
    val hero: TextStyle,
    val title: TextStyle,
    val body: TextStyle,
    val label: TextStyle,
    val caption: TextStyle,
    /** Numbers in input fields (set rows): large, tabular digits. */
    val number: TextStyle,
) {
    /** [style] with tabular figures, for values that change while visible (countdown, elapsed time). */
    fun tabular(style: TextStyle): TextStyle = style.copy(fontFeatureSettings = TNUM)
}

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

/** The app is always dark, whatever the system setting (Fabian: "Nimm den Light mode raus"). */
@Composable
fun OverfitTheme(content: @Composable () -> Unit) {
    val family = inter()
    val typography = Typography(
        hero = TextStyle(fontFamily = family, fontWeight = FontWeight.SemiBold, fontSize = 56.sp, lineHeight = 62.sp, letterSpacing = (-1.5).sp, fontFeatureSettings = TNUM),
        title = TextStyle(fontFamily = family, fontWeight = FontWeight.SemiBold, fontSize = 34.sp, lineHeight = 40.sp, letterSpacing = (-0.8).sp, fontFeatureSettings = TNUM),
        body = TextStyle(fontFamily = family, fontWeight = FontWeight.Normal, fontSize = 17.sp, lineHeight = 24.sp),
        label = TextStyle(fontFamily = family, fontWeight = FontWeight.Medium, fontSize = 17.sp, lineHeight = 24.sp),
        caption = TextStyle(fontFamily = family, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp),
        number = TextStyle(fontFamily = family, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp, fontFeatureSettings = TNUM),
    )
    CompositionLocalProvider(
        LocalColors provides DarkColors,
        LocalTypography provides typography,
    ) {
        Box(Modifier.clearFocusOutsideTextInput()) {
            // Focus sink: after "Done" a text field clears its focus; Android then hands focus back to the
            // root, which would put it on the first text field again (cursor + keyboard return). This
            // invisible first focus target takes it instead, so the cursor really goes away.
            Box(Modifier.size(1.dp).focusTarget())
            content()
        }
    }
}
