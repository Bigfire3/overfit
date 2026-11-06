package app.overfit.ui

import androidx.compose.ui.graphics.Color
import app.overfit.ui.theme.DarkColors
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertTrue

/** WCAG contrast of the button pairs (text on fill): at least 4.5:1. */
class ButtonContrastTest {
    private fun lin(v: Float) = if (v <= 0.03928f) v / 12.92f else ((v + 0.055f) / 1.055f).toDouble().pow(2.4).toFloat()
    private fun lum(c: Color) = 0.2126f * lin(c.red) + 0.7152f * lin(c.green) + 0.0722f * lin(c.blue)
    private fun ratio(a: Color, b: Color): Float {
        val (hi, lo) = lum(a).let { x -> lum(b).let { y -> maxOf(x, y) to minOf(x, y) } }
        return (hi + 0.05f) / (lo + 0.05f)
    }
    private val c = DarkColors

    @Test fun primaryBlackOnWhite() = assertTrue(ratio(c.inverseForeground, c.inverseBackground) >= 4.5f)
    @Test fun whiteOnDarkGrey() = assertTrue(ratio(c.foreground, c.surface) >= 4.5f)
    @Test fun whiteOnDarkGreyInPanel() = assertTrue(ratio(c.foreground, c.surfacePressed) >= 4.5f)
    @Test fun mutedOnDarkGrey() = assertTrue(ratio(c.muted, c.surface) >= 4.5f)
    @Test fun whiteOnArmedFill() = assertTrue(ratio(c.foreground, c.armedFill) >= 4.5f)
    @Test fun whiteOnConfirmFill() = assertTrue(ratio(c.foreground, c.confirmFill) >= 4.5f)
    @Test fun redOnSubtleRed() = assertTrue(ratio(c.negative, c.negativeSubtle) >= 4.5f)
}
