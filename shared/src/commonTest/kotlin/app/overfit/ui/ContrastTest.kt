package app.overfit.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import app.overfit.ui.theme.DarkColors
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * WCAG contrast floor of the text colours on every surface they appear on (DESIGN.md "The Contrast Floor Rule"):
 * text ≥ 4.5:1, placeholders ≥ 3:1. Button pairs are in [ButtonContrastTest].
 */
class ContrastTest {
    private fun lin(v: Float) = if (v <= 0.03928f) v / 12.92f else ((v + 0.055f) / 1.055f).toDouble().pow(2.4).toFloat()
    private fun lum(c: Color) = 0.2126f * lin(c.red) + 0.7152f * lin(c.green) + 0.0722f * lin(c.blue)
    private fun ratio(a: Color, b: Color): Float {
        val (hi, lo) = lum(a).let { x -> lum(b).let { y -> maxOf(x, y) to minOf(x, y) } }
        return (hi + 0.05f) / (lo + 0.05f)
    }
    private val c = DarkColors
    private fun check(name: String, fg: Color, bg: Color, min: Float) {
        val r = ratio(fg, bg)
        assertTrue(r >= min, "$name: ${(r * 100).toInt() / 100f}:1 < $min:1")
    }

    @Test fun mutedTextOnEverySurface() {
        check("muted/background", c.muted, c.background, 4.5f)
        check("muted/surface", c.muted, c.surface, 4.5f)
        // Chips, segments and small buttons inside a panel: grey caption on the lighter fill.
        check("muted/surfacePressed", c.muted, c.surfacePressed, 4.5f)
    }

    @Test fun faintTextOnBlackAndPlaceholdersOnSurface() {
        // Empty calendar days, exercise side notes, catalogue count stand on black.
        check("faint/background", c.faint, c.background, 4.5f)
        // Placeholders in panel inputs (row inputs, text areas).
        check("faint/surface", c.faint, c.surface, 3f)
    }

    @Test fun faintStaysBelowMuted() {
        // Hierarchy: entered values / secondary text (muted) must stay distinguishable from placeholders (faint).
        assertTrue(ratio(c.muted, c.background) - ratio(c.faint, c.background) >= 2f)
    }

    @Test fun redTextOnEverySurface() {
        // Destructive menu entries sit on surfacePressed, deltas on surface or black.
        check("negative/background", c.negative, c.background, 4.5f)
        check("negative/surface", c.negative, c.surface, 4.5f)
        check("negative/surfacePressed", c.negative, c.surfacePressed, 4.5f)
    }

    @Test fun greenTextOnEverySurface() {
        check("positive/surface", c.positive, c.surface, 4.5f)
        check("positive/surfacePressed", c.positive, c.surfacePressed, 4.5f)
    }

    @Test fun whiteOnSwipeToDelete() = check("white/armedFill", Color.White, c.armedFill, 4.5f)

    @Test fun accentOnEverySurface() {
        // "Now" colour: countdown on the rest island (surface, also over its filled part), set number on a row,
        // marks on black.
        check("accent/background", c.accent, c.background, 4.5f)
        check("accent/surface", c.accent, c.surface, 4.5f)
        check("accent/surfacePressed", c.accent, c.surfacePressed, 4.5f)
        check("accent/accentSubtle", c.accent, c.accentSubtle, 4.5f)
        // Buttons on the filled part of the rest island keep their white label readable.
        check("white/surfacePressed", c.foreground, c.surfacePressed, 4.5f)
    }

    @Test fun accentIsNeitherGreenNorRed() {
        // Three meanings, three hues: done (green), now (yellow), delete (red) must not be mistaken for each other.
        fun hue(x: Color): Float {
            val max = maxOf(x.red, x.green, x.blue); val min = minOf(x.red, x.green, x.blue)
            val d = (max - min).takeIf { it > 0f } ?: return 0f
            val h = when (max) {
                x.red -> ((x.green - x.blue) / d) % 6f
                x.green -> (x.blue - x.red) / d + 2f
                else -> (x.red - x.green) / d + 4f
            } * 60f
            return (h + 360f) % 360f
        }
        fun apart(a: Color, b: Color) = kotlin.math.abs(hue(a) - hue(b)).let { minOf(it, 360f - it) }
        assertTrue(apart(c.accent, c.positive) >= 40f, "accent too close to green")
        assertTrue(apart(c.accent, c.armed) >= 40f, "accent too close to red")
    }

    @Test fun numberFieldOnRows() {
        // Set field: translucent white over the grey row and over the green (checked-off) row.
        // The digits are large text (20 sp semibold ≥ 14 pt bold): 3:1 is the WCAG floor, the grey row clears 4.5:1 anyway.
        check("white/inputFill on row", c.foreground, c.inputFill.compositeOver(c.surfacePressed), 4.5f)
        check("white/inputFill on green row", c.foreground, c.inputFill.compositeOver(c.confirmFill), 3f)
        check("muted placeholder/inputFill on row", c.muted, c.inputFill.compositeOver(c.surfacePressed), 3f)
    }
}
