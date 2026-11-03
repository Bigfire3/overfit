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
enum class Icon { Back, Check, Home, Training, History, Exercises, Photos, Calendar, Settings, Plus, Close, Search, ChevronDown, ChevronUp, ChevronRight, Drag, Coach, Share, Delete, Edit, More }

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
            // Lightning bolt: the Training tab.
            Icon.Training -> path(13.5f to 3f, 5.5f to 13.5f, 11.5f to 13.5f, 10.5f to 21f, 18.5f to 10.5f, 12.5f to 10.5f, close = true)
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
            // Calendar tab: sheet with a header line, two rings and one dot for a day.
            Icon.Calendar -> {
                drawRoundRect(color, p(4f, 5.5f), Size(16f * u, 14.5f * u), style = stroke)
                line(4f, 10f, 20f, 10f)
                line(8.5f, 3.5f, 8.5f, 7f)
                line(15.5f, 3.5f, 15.5f, 7f)
                drawCircle(color, 1.4f * u, p(12f, 14.75f))
            }
            Icon.Plus -> {
                line(12f, 5f, 12f, 19f)
                line(5f, 12f, 19f, 12f)
            }
            Icon.Close -> {
                line(6.5f, 6.5f, 17.5f, 17.5f)
                line(17.5f, 6.5f, 6.5f, 17.5f)
            }
            Icon.Search -> {
                circle(10.5f, 10.5f, 6f)
                line(15f, 15f, 19.5f, 19.5f)
            }
            Icon.ChevronDown -> path(6f to 9.5f, 12f to 15.5f, 18f to 9.5f)
            Icon.ChevronRight -> path(9.5f to 6f, 15.5f to 12f, 9.5f to 18f)
            Icon.ChevronUp -> path(6f to 14.5f, 12f to 8.5f, 18f to 14.5f)
            // Speech bubble with two text lines: the Coach tab (chat with the coach).
            Icon.Coach -> {
                path(8f to 17f, 4.75f to 17f, 4f to 16.25f, 4f to 5.75f, 4.75f to 5f, 19.25f to 5f, 20f to 5.75f, 20f to 16.25f, 19.25f to 17f, 13f to 17f, 8f to 20.5f, close = true)
                line(8f, 9.5f, 16f, 9.5f)
                line(8f, 12.75f, 13f, 12.75f)
            }
            Icon.Drag -> {
                line(5f, 9f, 19f, 9f)
                line(5f, 15f, 19f, 15f)
            }
            // Box with an arrow out of it: share.
            Icon.Share -> {
                path(8.5f to 10f, 6f to 10f, 6f to 20f, 18f to 20f, 18f to 10f, 15.5f to 10f)
                line(12f, 3.5f, 12f, 14f)
                path(8.5f to 7f, 12f to 3.5f, 15.5f to 7f)
            }
            // Trash can: delete.
            Icon.Delete -> {
                line(4.5f, 6.5f, 19.5f, 6.5f)
                path(9.5f to 6.5f, 9.5f to 4f, 14.5f to 4f, 14.5f to 6.5f)
                path(6.5f to 6.5f, 7.5f to 20f, 16.5f to 20f, 17.5f to 6.5f)
                line(10.25f, 10f, 10.25f, 16.5f)
                line(13.75f, 10f, 13.75f, 16.5f)
            }
            // Pencil: edit.
            Icon.Edit -> {
                path(15.5f to 4.5f, 19.5f to 8.5f, 8.5f to 19.5f, 4.5f to 19.5f, 4.5f to 15.5f, close = true)
                line(13f, 7f, 17f, 11f)
            }
            // Three dots in a column: more actions.
            Icon.More -> {
                drawCircle(color, 1.4f * u, p(12f, 5.5f))
                drawCircle(color, 1.4f * u, p(12f, 12f))
                drawCircle(color, 1.4f * u, p(12f, 18.5f))
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
