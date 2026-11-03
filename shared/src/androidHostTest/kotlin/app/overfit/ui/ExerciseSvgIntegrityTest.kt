package app.overfit.ui

import java.awt.Color
import java.awt.RenderingHints
import java.awt.geom.AffineTransform
import java.awt.geom.Path2D
import java.awt.geom.Rectangle2D
import java.awt.image.BufferedImage
import java.io.File
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Guards the bundled Workout Guide SVGs (catalog/wg). Until 0.1.257 the import script rounded path data with a regex
 * and merged compact number runs such as `8.002.392` into `8.4`, which broke almost every image. These checks would
 * have caught it:
 *  1. every file parses strictly (commands, argument counts, arc flags),
 *  2. the path stays inside the viewBox,
 *  3. a sample is rasterised and compared with the unrounded source frames (`svg-reference/`, verbatim from the pinned
 *     Workout Guide commit).
 */
class ExerciseSvgIntegrityTest {
    private val dir = File("src/commonMain/composeResources/files/catalog/wg")
    private val refDir = File("src/androidHostTest/svg-reference")

    @Test
    fun everyFileParsesAndStaysInsideViewBox() {
        val files = dir.listFiles { f -> f.name.endsWith(".svg") }!!.sortedBy { it.name }
        assertEquals(906, files.size, "302 exercises x 3 frames")
        val problems = mutableListOf<String>()
        for (f in files) {
            try {
                val svg = Svg.read(f.readText())
                val b = svg.path.bounds2D
                val tol = 2.0
                if (b.isEmpty || b.minX < svg.viewBox.minX - tol || b.minY < svg.viewBox.minY - tol ||
                    b.maxX > svg.viewBox.maxX + tol || b.maxY > svg.viewBox.maxY + tol
                ) problems += "${f.name}: bounds $b outside viewBox ${svg.viewBox}"
            } catch (e: IllegalArgumentException) {
                problems += "${f.name}: ${e.message}"
            }
        }
        assertTrue(problems.isEmpty(), problems.take(20).joinToString("\n"))
    }

    @Test
    fun sampleRendersLikeTheUnroundedSource() {
        val refs = refDir.listFiles { f -> f.name.endsWith(".svg") }!!.sortedBy { it.name }
        assertEquals(12, refs.size)
        for (ref in refs) {
            val (slug, frame) = Regex("(.+)-frame-(\\d)\\.svg").matchEntire(ref.name)!!.destructured
            val bundled = File(dir, if (frame == "1") "$slug.svg" else "$slug-$frame.svg")
            val diff = mismatch(render(Svg.read(ref.readText())), render(Svg.read(bundled.readText())))
            assertTrue(diff < 0.005, "${bundled.name} differs from the source in ${"%.2f".format(diff * 100)} % of pixels")
        }
    }

    /** Self-check: the comparison is sensitive enough to tell two frames of one exercise apart. */
    @Test
    fun comparisonDetectsDifferentImages() {
        val a = render(Svg.read(File(refDir, "pull-up-frame-1.svg").readText()))
        val b = render(Svg.read(File(refDir, "pull-up-frame-3.svg").readText()))
        assertTrue(mismatch(a, b) > 0.02)
    }

    private fun render(svg: Svg, size: Int = 128): BufferedImage {
        val img = BufferedImage(size, size, BufferedImage.TYPE_BYTE_GRAY)
        val g = img.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.color = Color.BLACK
        g.fillRect(0, 0, size, size)
        g.transform(AffineTransform.getScaleInstance(size / svg.viewBox.width, size / svg.viewBox.height))
        g.translate(-svg.viewBox.x, -svg.viewBox.y)
        g.color = Color.WHITE
        g.fill(svg.path)
        g.dispose()
        return img
    }

    /** Share of pixels whose grey value differs by more than a quarter (anti-aliasing noise stays below). */
    private fun mismatch(a: BufferedImage, b: BufferedImage): Double {
        var n = 0
        for (y in 0 until a.height) for (x in 0 until a.width) {
            if (abs((a.getRGB(x, y) and 0xff) - (b.getRGB(x, y) and 0xff)) > 64) n++
        }
        return n.toDouble() / (a.width * a.height)
    }
}

/** Minimal strict SVG reader for the Workout Guide files: one viewBox, `<path d=…>` elements, SVG 1.1 path grammar. */
private class Svg(val viewBox: Rectangle2D, val path: Path2D) {
    companion object {
        fun read(text: String): Svg {
            val vb = Regex("viewBox=\"([^\"]+)\"").find(text)?.groupValues?.get(1)
                ?: throw IllegalArgumentException("no viewBox")
            val v = vb.trim().split(Regex("[\\s,]+")).map { it.toDouble() }
            val path = Path2D.Double(Path2D.WIND_EVEN_ODD)
            val ds = Regex("\\sd=\"([^\"]*)\"").findAll(text).map { it.groupValues[1] }.toList()
            require(ds.isNotEmpty()) { "no path data" }
            require(Regex("fill-rule=\"evenodd\"").containsMatchIn(text)) { "expected fill-rule evenodd" }
            ds.forEach { PathParser(it, path).parse() }
            return Svg(Rectangle2D.Double(v[0], v[1], v[2], v[3]), path)
        }
    }
}

private class PathParser(private val d: String, private val p: Path2D) {
    private var i = 0
    private var x = 0.0
    private var y = 0.0
    private var sx = 0.0
    private var sy = 0.0
    private var cx = 0.0 // last control point (for S/T reflection)
    private var cy = 0.0
    private var last = ' '

    fun parse() {
        var cmd: Char? = null
        while (true) {
            skipSep()
            if (i >= d.length) return
            val c = d[i]
            if (c.isLetter()) {
                require(c.uppercaseChar() in ARGS) { "unknown command '$c' at $i" }
                cmd = c; i++
                if (c.uppercaseChar() == 'Z') { close(); continue }
            } else {
                require(cmd != null && cmd.uppercaseChar() != 'Z') { "number without command at $i" }
            }
            val a = DoubleArray(ARGS.getValue(cmd.uppercaseChar())) { k ->
                if (cmd.uppercaseChar() == 'A' && (k == 3 || k == 4)) flag() else number()
            }
            segment(cmd, a)
            if (cmd == 'M') cmd = 'L' else if (cmd == 'm') cmd = 'l'
        }
    }

    private fun skipSep() { while (i < d.length && (d[i].isWhitespace() || d[i] == ',')) i++ }

    private fun flag(): Double {
        skipSep()
        require(i < d.length && (d[i] == '0' || d[i] == '1')) { "arc flag expected at $i" }
        return (d[i++] - '0').toDouble()
    }

    private fun number(): Double {
        skipSep()
        val m = NUMBER.matchAt(d, i) ?: throw IllegalArgumentException("number expected at $i: '${d.drop(i).take(20)}'")
        i = m.range.last + 1
        return m.value.toDouble()
    }

    private fun close() { p.closePath(); x = sx; y = sy; last = 'Z' }

    private fun segment(cmd: Char, a: DoubleArray) {
        val rel = cmd.isLowerCase()
        val ox = if (rel) x else 0.0
        val oy = if (rel) y else 0.0
        val up = cmd.uppercaseChar()
        when (up) {
            'M' -> { x = ox + a[0]; y = oy + a[1]; sx = x; sy = y; p.moveTo(x, y) }
            'L' -> { x = ox + a[0]; y = oy + a[1]; p.lineTo(x, y) }
            'H' -> { x = ox + a[0]; p.lineTo(x, y) }
            'V' -> { y = oy + a[0]; p.lineTo(x, y) }
            'C' -> { p.curveTo(ox + a[0], oy + a[1], ox + a[2], oy + a[3], ox + a[4], oy + a[5]); cx = ox + a[2]; cy = oy + a[3]; x = ox + a[4]; y = oy + a[5] }
            'S' -> {
                val (rx, ry) = if (last == 'C' || last == 'S') 2 * x - cx to 2 * y - cy else x to y
                p.curveTo(rx, ry, ox + a[0], oy + a[1], ox + a[2], oy + a[3]); cx = ox + a[0]; cy = oy + a[1]; x = ox + a[2]; y = oy + a[3]
            }
            'Q' -> { p.quadTo(ox + a[0], oy + a[1], ox + a[2], oy + a[3]); cx = ox + a[0]; cy = oy + a[1]; x = ox + a[2]; y = oy + a[3] }
            'T' -> {
                val (rx, ry) = if (last == 'Q' || last == 'T') 2 * x - cx to 2 * y - cy else x to y
                p.quadTo(rx, ry, ox + a[0], oy + a[1]); cx = rx; cy = ry; x = ox + a[0]; y = oy + a[1]
            }
            'A' -> { arc(a[0], a[1], a[2], a[3] != 0.0, a[4] != 0.0, ox + a[5], oy + a[6]) }
        }
        last = up
    }

    /** Endpoint arc → centre parameterisation (SVG 1.1 implementation notes F.6.5), flattened into short lines. */
    private fun arc(rx0: Double, ry0: Double, deg: Double, large: Boolean, sweep: Boolean, x2: Double, y2: Double) {
        var rx = abs(rx0)
        var ry = abs(ry0)
        if (rx == 0.0 || ry == 0.0 || (x == x2 && y == y2)) { p.lineTo(x2, y2); x = x2; y = y2; return }
        val phi = deg * PI / 180
        val cp = cos(phi)
        val sp = sin(phi)
        val dx = (x - x2) / 2
        val dy = (y - y2) / 2
        val x1p = cp * dx + sp * dy
        val y1p = -sp * dx + cp * dy
        val lambda = x1p * x1p / (rx * rx) + y1p * y1p / (ry * ry)
        if (lambda > 1) { rx *= sqrt(lambda); ry *= sqrt(lambda) }
        val num = rx * rx * ry * ry - rx * rx * y1p * y1p - ry * ry * x1p * x1p
        val den = rx * rx * y1p * y1p + ry * ry * x1p * x1p
        var coef = sqrt(maxOf(0.0, num / den))
        if (large == sweep) coef = -coef
        val cxp = coef * rx * y1p / ry
        val cyp = -coef * ry * x1p / rx
        val ccx = cp * cxp - sp * cyp + (x + x2) / 2
        val ccy = sp * cxp + cp * cyp + (y + y2) / 2
        fun angle(ux: Double, uy: Double, vx: Double, vy: Double): Double {
            val a = acos(((ux * vx + uy * vy) / (sqrt(ux * ux + uy * uy) * sqrt(vx * vx + vy * vy))).coerceIn(-1.0, 1.0))
            return if (ux * vy - uy * vx < 0) -a else a
        }
        val t1 = angle(1.0, 0.0, (x1p - cxp) / rx, (y1p - cyp) / ry)
        var dt = angle((x1p - cxp) / rx, (y1p - cyp) / ry, (-x1p - cxp) / rx, (-y1p - cyp) / ry)
        if (!sweep && dt > 0) dt -= 2 * PI else if (sweep && dt < 0) dt += 2 * PI
        val n = maxOf(4, ceil(abs(dt) / (PI / 32)).toInt())
        for (k in 1..n) {
            val t = t1 + dt * k / n
            p.lineTo(ccx + rx * cos(t) * cp - ry * sin(t) * sp, ccy + rx * cos(t) * sp + ry * sin(t) * cp)
        }
        x = x2; y = y2
    }

    companion object {
        val ARGS = mapOf('M' to 2, 'L' to 2, 'H' to 1, 'V' to 1, 'C' to 6, 'S' to 4, 'Q' to 4, 'T' to 2, 'A' to 7, 'Z' to 0)
        val NUMBER = Regex("[-+]?(?:\\d+\\.?\\d*|\\.\\d+)(?:[eE][-+]?\\d+)?")
    }
}
