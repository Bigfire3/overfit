package app.overfit.ui.muscles

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import app.overfit.domain.Muscle

/**
 * One area of the body figure, in figure units (100 wide, about 180 high, centre line x = 50).
 * [muscle] null = neutral part (head, hands, feet, knees). Either a polygon ([points], drawn with rounded
 * corners) or an [oval].
 */
class BodyShape(val muscle: Muscle?, val points: List<Offset>, val oval: Rect? = null) {
    /** Point-in-shape test on the plain polygon (the rounded drawing lies just inside it). */
    fun contains(p: Offset): Boolean {
        oval?.let { o ->
            val dx = (p.x - o.center.x) / (o.width / 2)
            val dy = (p.y - o.center.y) / (o.height / 2)
            return dx * dx + dy * dy <= 1f
        }
        var inside = false
        var j = points.lastIndex
        for (i in points.indices) {
            val a = points[i]
            val b = points[j]
            if ((a.y > p.y) != (b.y > p.y) && p.x < (b.x - a.x) * (p.y - a.y) / (b.y - a.y) + a.x) inside = !inside
            j = i
        }
        return inside
    }

    val center: Offset
        get() = oval?.center ?: Offset(points.map { it.x }.average().toFloat(), points.map { it.y }.average().toFloat())
}

private class ShapeBuilder {
    val shapes = mutableListOf<BodyShape>()

    /** Polygon from x,y pairs on the left half of the figure; [mirror] adds the right side (x → 100 − x). */
    fun poly(muscle: Muscle?, vararg xy: Float, mirror: Boolean = true) {
        val pts = xy.toList().chunked(2) { Offset(it[0], it[1]) }
        shapes += BodyShape(muscle, pts)
        if (mirror) shapes += BodyShape(muscle, pts.map { Offset(100f - it.x, it.y) })
    }

    fun oval(muscle: Muscle?, cx: Float, cy: Float, rx: Float, ry: Float, mirror: Boolean) {
        shapes += BodyShape(muscle, emptyList(), Rect(cx - rx, cy - ry, cx + rx, cy + ry))
        if (mirror) shapes += BodyShape(muscle, emptyList(), Rect(100f - cx - rx, cy - ry, 100f - cx + rx, cy + ry))
    }
}

private fun buildShapes(block: ShapeBuilder.() -> Unit): List<BodyShape> = ShapeBuilder().apply(block).shapes

/**
 * Overfit's own stylised figure (front and back), drawn for this app – no external template, see DECISIONS
 * "Muskel-Heatmap". Regions follow the catalogue's muscle groups ([app.overfit.domain.MuscleMap.shown]).
 */
object BodyShapes {
    /** Visible part of the figure in units: x 19..81, y 3..179. */
    val viewport = Rect(19f, 3f, 81f, 179f)

    val front: List<BodyShape> = buildShapes {
        oval(null, 50f, 14f, 8f, 10f, mirror = false)
        poly(null, 23.6f, 88f, 27.8f, 88.6f, 27.6f, 95.5f, 24.2f, 97.8f, 21.8f, 93.4f)
        poly(null, 35.4f, 169.5f, 40.6f, 169f, 42.6f, 176.5f, 34f, 177.2f)
        oval(null, 41.2f, 127.8f, 2.8f, 2.6f, mirror = true)
        poly(Muscle.NECK, 45.8f, 24f, 54.2f, 24f, 54.8f, 30.2f, 45.2f, 30.2f, mirror = false)
        poly(Muscle.UPPER_BACK, 44.2f, 25.6f, 44.4f, 31f, 35f, 33f, 39.4f, 29.6f)
        poly(Muscle.SHOULDERS, 34.2f, 33.8f, 41.2f, 32.8f, 40f, 37.4f, 36f, 43.6f, 30.8f, 47.6f, 28.8f, 42f, 30.4f, 36.6f)
        poly(Muscle.CHEST, 42.4f, 32.6f, 49.4f, 33.2f, 49.4f, 46.4f, 45f, 49f, 39f, 48f, 36.8f, 45f, 41f, 37.6f)
        poly(Muscle.BICEPS, 29.4f, 49.2f, 35.6f, 45.4f, 36.4f, 52.4f, 34.8f, 60.4f, 30.4f, 63.8f, 27.6f, 57f)
        poly(Muscle.FOREARMS, 27.2f, 65.4f, 33f, 63.6f, 33.6f, 68.6f, 31f, 78.4f, 27.6f, 86.6f, 23.8f, 85.8f, 24.2f, 74f)
        poly(Muscle.CORE, 44.4f, 50.6f, 49.4f, 50.2f, 49.4f, 56.6f, 44.4f, 57f)
        poly(Muscle.CORE, 44.6f, 58.2f, 49.4f, 57.8f, 49.4f, 64f, 44.8f, 64.4f)
        poly(Muscle.CORE, 45f, 65.6f, 49.4f, 65.2f, 49.4f, 71.4f, 45.2f, 71.8f)
        poly(Muscle.CORE, 45.4f, 73f, 49.4f, 72.6f, 49.4f, 84f, 46.8f, 80f)
        poly(Muscle.CORE, 38.4f, 49.6f, 43.2f, 50.4f, 43.2f, 64f, 43.8f, 75f, 40.8f, 77f, 38f, 68.4f, 37.4f, 58f)
        poly(Muscle.ABDUCTORS, 36.6f, 78.4f, 40.6f, 78f, 41.8f, 83.4f, 37.4f, 93f, 35.6f, 86f)
        poly(Muscle.QUADS, 36.6f, 96f, 38.4f, 87.4f, 42.4f, 82.6f, 43.8f, 92f, 42.8f, 108f, 40.8f, 124.8f, 37f, 118f, 35.6f, 105f)
        poly(Muscle.QUADS, 44.8f, 90.6f, 47f, 103.4f, 45.8f, 115.6f, 44.2f, 123.4f, 42f, 124.8f, 44f, 108f)
        poly(Muscle.ADDUCTORS, 44.2f, 80.6f, 49f, 85.2f, 49.2f, 93f, 48.2f, 105f, 46.6f, 97f)
        poly(Muscle.CALVES, 36.8f, 132.8f, 40.4f, 131.2f, 41f, 138.4f, 39.8f, 152f, 38.8f, 165.4f, 36.4f, 165.4f, 35.2f, 150f, 35.2f, 140f)
        poly(Muscle.CALVES, 42.2f, 132.2f, 45f, 134.4f, 45f, 145.4f, 42.8f, 158.4f, 41.8f, 150f)
    }

    val back: List<BodyShape> = buildShapes {
        oval(null, 50f, 14f, 8f, 10f, mirror = false)
        poly(null, 23.6f, 88f, 27.8f, 88.6f, 27.6f, 95.5f, 24.2f, 97.8f, 21.8f, 93.4f)
        poly(null, 36f, 169.5f, 41.2f, 169f, 41.6f, 176.6f, 36.4f, 177f)
        poly(Muscle.UPPER_BACK, 45.8f, 23.4f, 49.3f, 25.6f, 49.3f, 53.4f, 45.2f, 45.4f, 41.4f, 37.8f, 35.2f, 33.4f, 42.8f, 29.6f)
        poly(Muscle.SHOULDERS, 34.2f, 34.6f, 40.2f, 36f, 38.8f, 40.6f, 34.8f, 45.6f, 30.6f, 47.8f, 29f, 41.6f, 30.8f, 37f)
        poly(Muscle.TRICEPS, 29.8f, 49.4f, 35.2f, 46.8f, 36.6f, 53f, 35.4f, 60.4f, 32f, 64f, 28.2f, 58f)
        poly(Muscle.FOREARMS, 27.2f, 65.4f, 33f, 63.8f, 33.6f, 68.6f, 31f, 78.4f, 27.6f, 86.6f, 23.8f, 85.8f, 24.2f, 74f)
        poly(Muscle.LATS, 39.8f, 41.6f, 44f, 47.2f, 47.4f, 55.6f, 46.2f, 66.4f, 41.2f, 73f, 38.4f, 62.4f, 37f, 50f)
        poly(Muscle.LOWER_BACK, 48.6f, 57.2f, 49.3f, 57.4f, 49.3f, 77.8f, 44.2f, 78.6f, 43.8f, 74f, 47f, 67.8f)
        poly(Muscle.ABDUCTORS, 37.4f, 75.4f, 42.6f, 75.8f, 39.2f, 79.8f, 36.6f, 86.6f, 35.4f, 81f)
        poly(Muscle.GLUTES, 40.6f, 80.6f, 44.8f, 79.8f, 49.3f, 81.4f, 49.3f, 95.8f, 45f, 99.6f, 39.4f, 97.6f, 37.6f, 89.4f)
        poly(Muscle.HAMSTRINGS, 37.4f, 101f, 41.4f, 101.2f, 41.8f, 112f, 40.8f, 124.4f, 37.8f, 118.4f, 36.2f, 108f)
        poly(Muscle.HAMSTRINGS, 42.8f, 101.4f, 46.6f, 102f, 47.2f, 110f, 45.4f, 122.4f, 42f, 124.6f, 43f, 112f)
        poly(Muscle.ADDUCTORS, 48f, 102.6f, 49.4f, 101.2f, 49.4f, 110f, 48.4f, 114f)
        poly(Muscle.CALVES, 36.8f, 131.4f, 41f, 129.4f, 43f, 131.8f, 42.2f, 142.4f, 39.6f, 153f, 37f, 148.8f, 35.4f, 139.4f)
        poly(Muscle.CALVES, 44f, 131.4f, 46.8f, 133.4f, 47f, 141.8f, 44.2f, 151.8f, 43.2f, 144.4f)
    }

    /** Muscle at [p] (figure units): a direct hit, else the nearest region centre within 5 units (small areas). */
    fun hit(shapes: List<BodyShape>, p: Offset): Muscle? {
        val muscles = shapes.filter { it.muscle != null }
        muscles.lastOrNull { it.contains(p) }?.let { return it.muscle }
        if (shapes.any { it.muscle == null && it.contains(p) }) return null
        return muscles.minByOrNull { (it.center - p).getDistance() }?.takeIf { (it.center - p).getDistance() <= 5f }?.muscle
    }
}
