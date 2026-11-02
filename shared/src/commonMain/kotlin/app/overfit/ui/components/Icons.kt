package app.overfit.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.overfit.ui.theme.Theme

/** Thin line icons, drawn on a 24×24 grid. No icon font, no Material icons. */
enum class Icon { Back, Check, Home, History, Exercises, Photos, Settings }

@Composable
fun LineIcon(icon: Icon, size: Dp = 24.dp, color: Color = Theme.colors.foreground) {
    val background = Theme.colors.background
    Canvas(Modifier.size(size)) {
        val u = this.size.width / 24f
        val stroke = Stroke(width = 1.5f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
        fun p(x: Float, y: Float) = Offset(x * u, y * u)
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
            drawLine(color, p(x1, y1), p(x2, y2), strokeWidth = stroke.width, cap = StrokeCap.Round)
        fun path(vararg points: Pair<Float, Float>, close: Boolean = false) {
            val path = Path().apply {
                points.forEachIndexed { i, (x, y) -> if (i == 0) moveTo(x * u, y * u) else lineTo(x * u, y * u) }
                if (close) close()
            }
            drawPath(path, color, style = stroke)
        }
        fun DrawScope.circle(cx: Float, cy: Float, r: Float, fill: Color? = null) {
            if (fill != null) drawCircle(fill, r * u, p(cx, cy))
            drawCircle(color, r * u, p(cx, cy), style = stroke)
        }

        when (icon) {
            Icon.Back -> path(15f to 5f, 8f to 12f, 15f to 19f)
            Icon.Check -> path(5f to 12.5f, 9.5f to 17f, 19f to 7.5f)
            Icon.Home -> path(4f to 11f, 12f to 4.5f, 20f to 11f, 20f to 20f, 4f to 20f, close = true)
            Icon.History -> {
                circle(12f, 12f, 8f)
                path(12f to 8f, 12f to 12f, 15f to 14f)
            }
            Icon.Exercises -> {
                line(7f, 12f, 17f, 12f)
                drawRoundRect(color, p(4f, 8f), Size(3f * u, 8f * u), style = stroke)
                drawRoundRect(color, p(17f, 8f), Size(3f * u, 8f * u), style = stroke)
            }
            Icon.Photos -> {
                drawRoundRect(color, p(4f, 6f), Size(16f * u, 13f * u), style = stroke)
                circle(12f, 12.5f, 3.25f)
            }
            Icon.Settings -> {
                line(4f, 8f, 20f, 8f)
                line(4f, 16f, 20f, 16f)
                // Knobs are filled with the background so they cut through the lines.
                circle(9f, 8f, 2.25f, fill = background)
                circle(15f, 16f, 2.25f, fill = background)
            }
        }
    }
}
