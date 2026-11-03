package app.overfit.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Square QR module matrix; `true` = dark module. */
class QrMatrix(val size: Int, private val modules: BooleanArray) {
    operator fun get(x: Int, y: Int): Boolean = modules[y * size + x]
}

/**
 * Encodes [text] (UTF-8, byte mode) as a QR code with error correction level M.
 * Picks the smallest version 1..12 and the mask with the lowest penalty.
 * @throws IllegalArgumentException if the text does not fit into version 12.
 */
fun encodeQr(text: String): QrMatrix = QrEncoder.encode(text.encodeToByteArray(), null)

/** Test hook: like [encodeQr] but with a fixed mask (0..7) or null for automatic choice. */
internal fun encodeQrWithMask(text: String, mask: Int?): QrMatrix = QrEncoder.encode(text.encodeToByteArray(), mask)

/** Mask the automatic selection picks for [text]. */
internal fun chosenQrMask(text: String): Int = QrEncoder.lastMask(text.encodeToByteArray())

/** Draws [content] as a QR code: always black modules on a white background (scanners need dark on light). */
@Composable
fun QrCode(content: String, modifier: Modifier = Modifier, size: Dp = 240.dp) {
    val matrix = remember(content) { encodeQr(content) }
    Canvas(modifier.size(size)) {
        val quiet = 4
        val cell = minOf(this.size.width, this.size.height) / (matrix.size + 2 * quiet)
        drawRect(Color.White, Offset.Zero, this.size)
        for (y in 0 until matrix.size) for (x in 0 until matrix.size) {
            if (matrix[x, y]) {
                drawRect(
                    Color.Black,
                    Offset((x + quiet) * cell, (y + quiet) * cell),
                    // tiny overlap avoids hairline gaps between modules
                    Size(cell + 0.5f, cell + 0.5f),
                )
            }
        }
    }
}

private object QrEncoder {
    private const val MAX_VERSION = 12

    // Level M: EC codewords per block, then (blocks, data codewords) of group 1 and group 2.
    private val EC_PER_BLOCK = intArrayOf(0, 10, 16, 26, 18, 24, 16, 18, 22, 22, 26, 30, 22)
    private val G1_BLOCKS = intArrayOf(0, 1, 1, 1, 2, 2, 4, 4, 2, 3, 4, 1, 6)
    private val G1_DATA = intArrayOf(0, 16, 28, 44, 32, 43, 27, 31, 38, 36, 43, 50, 36)
    private val G2_BLOCKS = intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 2, 2, 1, 4, 2)

    private val ALIGN = arrayOf(
        intArrayOf(), intArrayOf(), intArrayOf(6, 18), intArrayOf(6, 22), intArrayOf(6, 26),
        intArrayOf(6, 30), intArrayOf(6, 34), intArrayOf(6, 22, 38), intArrayOf(6, 24, 42),
        intArrayOf(6, 26, 46), intArrayOf(6, 28, 50), intArrayOf(6, 30, 54), intArrayOf(6, 32, 58),
    )

    private val EXP = IntArray(512)
    private val LOG = IntArray(256)

    init {
        var x = 1
        for (i in 0 until 255) {
            EXP[i] = x
            LOG[x] = i
            x = x shl 1
            if (x >= 256) x = x xor 0x11D
        }
        for (i in 255 until 512) EXP[i] = EXP[i - 255]
    }

    private fun mul(a: Int, b: Int): Int = if (a == 0 || b == 0) 0 else EXP[LOG[a] + LOG[b]]

    private fun rawCodewords(v: Int): Int {
        var r = (16 * v + 128) * v + 64
        if (v >= 2) {
            val n = v / 7 + 2
            r -= (25 * n - 10) * n - 55
            if (v >= 7) r -= 36
        }
        return r / 8
    }

    private fun dataCodewords(v: Int) = G1_BLOCKS[v] * G1_DATA[v] + G2_BLOCKS[v] * (G1_DATA[v] + 1)

    private fun rsDivisor(degree: Int): IntArray {
        var poly = intArrayOf(1)
        for (i in 0 until degree) {
            val next = IntArray(poly.size + 1)
            for (j in poly.indices) {
                next[j] = next[j] xor poly[j]
                next[j + 1] = next[j + 1] xor mul(poly[j], EXP[i])
            }
            poly = next
        }
        return poly.copyOfRange(1, poly.size) // leading coefficient 1 is implicit
    }

    private fun rsRemainder(data: IntArray, divisor: IntArray): IntArray {
        val rem = IntArray(divisor.size)
        for (b in data) {
            val factor = b xor rem[0]
            for (i in 0 until rem.size - 1) rem[i] = rem[i + 1]
            rem[rem.size - 1] = 0
            for (i in rem.indices) rem[i] = rem[i] xor mul(divisor[i], factor)
        }
        return rem
    }

    private fun pickVersion(len: Int): Int {
        for (v in 1..MAX_VERSION) {
            val ccBits = if (v <= 9) 8 else 16
            if (4 + ccBits + 8 * len <= dataCodewords(v) * 8) return v
        }
        throw IllegalArgumentException("Text too long for QR version $MAX_VERSION ($len bytes)")
    }

    private fun buildCodewords(bytes: ByteArray, v: Int): IntArray {
        val bits = ArrayList<Boolean>()
        fun put(value: Int, count: Int) {
            for (i in count - 1 downTo 0) bits.add(((value shr i) and 1) == 1)
        }
        put(0b0100, 4)
        put(bytes.size, if (v <= 9) 8 else 16)
        for (b in bytes) put(b.toInt() and 0xFF, 8)
        val capacity = dataCodewords(v) * 8
        put(0, minOf(4, capacity - bits.size))
        while (bits.size % 8 != 0) bits.add(false)
        var pad = 0xEC
        while (bits.size < capacity) {
            put(pad, 8)
            pad = pad xor (0xEC xor 0x11)
        }
        val data = IntArray(bits.size / 8)
        for (i in bits.indices) if (bits[i]) data[i / 8] = data[i / 8] or (1 shl (7 - i % 8))

        // Split into blocks, add EC, interleave.
        val ecLen = EC_PER_BLOCK[v]
        val numBlocks = G1_BLOCKS[v] + G2_BLOCKS[v]
        val divisor = rsDivisor(ecLen)
        val blocks = ArrayList<IntArray>()
        val ecs = ArrayList<IntArray>()
        var k = 0
        for (i in 0 until numBlocks) {
            val len = G1_DATA[v] + if (i >= G1_BLOCKS[v]) 1 else 0
            val block = data.copyOfRange(k, k + len)
            k += len
            blocks.add(block)
            ecs.add(rsRemainder(block, divisor))
        }
        val out = ArrayList<Int>()
        for (i in 0..G1_DATA[v]) for (b in blocks) if (i < b.size) out.add(b[i])
        for (i in 0 until ecLen) for (e in ecs) out.add(e[i])
        check(out.size == rawCodewords(v))
        return out.toIntArray()
    }

    private class Grid(val n: Int) {
        val dark = BooleanArray(n * n)
        val fn = BooleanArray(n * n)
        fun set(x: Int, y: Int, d: Boolean) { dark[y * n + x] = d; fn[y * n + x] = true }
        fun at(x: Int, y: Int) = dark[y * n + x]
    }

    private fun drawFunctionPatterns(g: Grid, v: Int) {
        val n = g.n
        for (i in 0 until n) {
            g.set(6, i, i % 2 == 0)
            g.set(i, 6, i % 2 == 0)
        }
        for ((cx, cy) in listOf(3 to 3, n - 4 to 3, 3 to n - 4)) {
            for (dy in -4..4) for (dx in -4..4) {
                val x = cx + dx
                val y = cy + dy
                if (x !in 0 until n || y !in 0 until n) continue
                val d = maxOf(kotlin.math.abs(dx), kotlin.math.abs(dy))
                g.set(x, y, d != 2 && d != 4)
            }
        }
        val pos = ALIGN[v]
        for (i in pos.indices) for (j in pos.indices) {
            if ((i == 0 && j == 0) || (i == 0 && j == pos.size - 1) || (i == pos.size - 1 && j == 0)) continue
            for (dy in -2..2) for (dx in -2..2) {
                g.set(pos[i] + dx, pos[j] + dy, maxOf(kotlin.math.abs(dx), kotlin.math.abs(dy)) != 1)
            }
        }
        drawFormat(g, 0) // reserve
        if (v >= 7) {
            var rem = v
            for (i in 0 until 12) rem = (rem shl 1) xor ((rem ushr 11) * 0x1F25)
            val bits = (v shl 12) or rem
            for (i in 0 until 18) {
                val bit = ((bits shr i) and 1) == 1
                val a = n - 11 + i % 3
                val b = i / 3
                g.set(a, b, bit)
                g.set(b, a, bit)
            }
        }
    }

    private fun drawFormat(g: Grid, mask: Int) {
        val n = g.n
        val data = (0b00 shl 3) or mask // EC level M = 00
        var rem = data
        for (i in 0 until 10) rem = (rem shl 1) xor ((rem ushr 9) * 0x537)
        val bits = ((data shl 10) or rem) xor 0x5412
        fun bit(i: Int) = ((bits shr i) and 1) == 1
        for (i in 0..5) g.set(8, i, bit(i))
        g.set(8, 7, bit(6))
        g.set(8, 8, bit(7))
        g.set(7, 8, bit(8))
        for (i in 9..14) g.set(14 - i, 8, bit(i))
        for (i in 0..7) g.set(n - 1 - i, 8, bit(i))
        for (i in 8..14) g.set(8, n - 15 + i, bit(i))
        g.set(8, n - 8, true)
    }

    private fun placeData(g: Grid, codewords: IntArray) {
        val n = g.n
        var i = 0
        val total = codewords.size * 8
        var right = n - 1
        while (right >= 1) {
            if (right == 6) right = 5
            for (vert in 0 until n) for (j in 0..1) {
                val x = right - j
                val upward = ((right + 1) and 2) == 0
                val y = if (upward) n - 1 - vert else vert
                if (!g.fn[y * n + x] && i < total) {
                    g.dark[y * n + x] = ((codewords[i ushr 3] shr (7 - (i and 7))) and 1) == 1
                    i++
                }
            }
            right -= 2
        }
    }

    private fun applyMask(g: Grid, mask: Int) {
        val n = g.n
        for (y in 0 until n) for (x in 0 until n) {
            if (g.fn[y * n + x]) continue
            val invert = when (mask) {
                0 -> (x + y) % 2 == 0
                1 -> y % 2 == 0
                2 -> x % 3 == 0
                3 -> (x + y) % 3 == 0
                4 -> (x / 3 + y / 3) % 2 == 0
                5 -> x * y % 2 + x * y % 3 == 0
                6 -> (x * y % 2 + x * y % 3) % 2 == 0
                else -> ((x + y) % 2 + x * y % 3) % 2 == 0
            }
            if (invert) g.dark[y * n + x] = !g.dark[y * n + x]
        }
    }

    private fun penalty(g: Grid): Int {
        val n = g.n
        var score = 0
        // N1 (runs) and N3 (finder-like) in rows and columns.
        for (dir in 0..1) for (a in 0 until n) {
            fun m(b: Int) = if (dir == 0) g.at(b, a) else g.at(a, b)
            var run = 1
            for (b in 1 until n) {
                if (m(b) == m(b - 1)) {
                    run++
                } else {
                    if (run >= 5) score += 3 + (run - 5)
                    run = 1
                }
            }
            if (run >= 5) score += 3 + (run - 5)
            val pat = booleanArrayOf(true, false, true, true, true, false, true)
            for (b in 0..n - 7) {
                var ok = true
                for (t in 0 until 7) if (m(b + t) != pat[t]) { ok = false; break }
                if (!ok) continue
                // Border counts as light, so patterns touching the edge also match.
                var lightBefore = true
                for (t in 1..4) if (b - t >= 0 && m(b - t)) lightBefore = false
                var lightAfter = true
                for (t in 0..3) if (b + 7 + t < n && m(b + 7 + t)) lightAfter = false
                if (lightBefore || lightAfter) score += 40
            }
        }
        // N2 (2x2 blocks)
        for (y in 0 until n - 1) for (x in 0 until n - 1) {
            val c = g.at(x, y)
            if (c == g.at(x + 1, y) && c == g.at(x, y + 1) && c == g.at(x + 1, y + 1)) score += 3
        }
        // N4 (balance)
        var darkCount = 0
        for (d in g.dark) if (d) darkCount++
        val percent = darkCount * 100 / (n * n)
        val dev = kotlin.math.abs(percent - 50)
        score += (dev / 5) * 10
        return score
    }

    private fun build(bytes: ByteArray, forced: Int?): Pair<QrMatrix, Int> {
        val v = pickVersion(bytes.size)
        val n = 17 + 4 * v
        val codewords = buildCodewords(bytes, v)
        val base = Grid(n)
        drawFunctionPatterns(base, v)
        placeData(base, codewords)
        var bestMask = 0
        var bestScore = Int.MAX_VALUE
        val masks = if (forced != null) listOf(forced) else (0..7).toList()
        for (mask in masks) {
            val g = Grid(n)
            base.dark.copyInto(g.dark)
            base.fn.copyInto(g.fn)
            applyMask(g, mask)
            drawFormat(g, mask)
            val s = penalty(g)
            if (s < bestScore) { bestScore = s; bestMask = mask }
        }
        val g = Grid(n)
        base.dark.copyInto(g.dark)
        base.fn.copyInto(g.fn)
        applyMask(g, bestMask)
        drawFormat(g, bestMask)
        return QrMatrix(n, g.dark) to bestMask
    }

    fun encode(bytes: ByteArray, forced: Int?): QrMatrix = build(bytes, forced).first
    fun lastMask(bytes: ByteArray): Int = build(bytes, null).second
}
