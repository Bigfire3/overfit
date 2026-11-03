package app.overfit.share

/**
 * Minimal QR code generator (byte mode, error correction level M, versions 1–30) in pure Kotlin,
 * so the same code draws the QR on Android and iOS. Follows ISO/IEC 18004.
 */
class QrCode private constructor(val size: Int, private val modules: BooleanArray) {
    /** True = dark module. Outside the symbol everything is light. */
    operator fun get(x: Int, y: Int): Boolean = x in 0 until size && y in 0 until size && modules[y * size + x]

    companion object {
        /** Versions above this are not used (verified against ZXing up to here; links stay far below). */
        private const val MAX_VERSION = 30

        fun fits(text: String): Boolean = runCatching { versionFor(text.encodeToByteArray().size) }.isSuccess

        fun encode(text: String): QrCode {
            val data = text.encodeToByteArray()
            val version = versionFor(data.size)
            val codewords = interleave(dataCodewords(data, version), version)
            return Builder(version).build(codewords)
        }

        // ECC level M only. Index = version - 1.
        private val ECC_PER_BLOCK = intArrayOf(
            10, 16, 26, 18, 24, 16, 18, 22, 22, 26, 30, 22, 22, 24, 24, 28, 28, 26, 26, 26,
            26, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28,
        )
        private val BLOCKS = intArrayOf(
            1, 1, 1, 2, 2, 4, 4, 4, 5, 5, 5, 8, 9, 9, 10, 10, 11, 13, 14, 16,
            17, 17, 18, 20, 21, 23, 25, 26, 28, 29, 31, 33, 35, 37, 38, 40, 43, 45, 47, 49,
        )

        private fun rawModules(ver: Int): Int {
            var result = (16 * ver + 128) * ver + 64
            if (ver >= 2) {
                val numAlign = ver / 7 + 2
                result -= (25 * numAlign - 10) * numAlign - 55
                if (ver >= 7) result -= 36
            }
            return result
        }

        private fun dataCapacity(ver: Int) = rawModules(ver) / 8 - ECC_PER_BLOCK[ver - 1] * BLOCKS[ver - 1]

        private fun versionFor(byteCount: Int): Int {
            for (ver in 1..MAX_VERSION) {
                val countBits = if (ver <= 9) 8 else 16
                if (4 + countBits + 8 * byteCount <= dataCapacity(ver) * 8) return ver
            }
            throw IllegalArgumentException("Data too long for a QR code")
        }

        private fun dataCodewords(data: ByteArray, ver: Int): ByteArray {
            val bits = ArrayList<Boolean>()
            fun append(value: Int, n: Int) { for (i in n - 1 downTo 0) bits.add(((value ushr i) and 1) == 1) }
            append(0b0100, 4)
            append(data.size, if (ver <= 9) 8 else 16)
            data.forEach { append(it.toInt() and 0xFF, 8) }
            val capBits = dataCapacity(ver) * 8
            append(0, minOf(4, capBits - bits.size))
            while (bits.size % 8 != 0) bits.add(false)
            val out = ByteArray(capBits / 8)
            for (i in 0 until bits.size) if (bits[i]) out[i / 8] = (out[i / 8].toInt() or (1 shl (7 - i % 8))).toByte()
            var pad = 0xEC
            for (i in bits.size / 8 until out.size) { out[i] = pad.toByte(); pad = pad xor (0xEC xor 0x11) }
            return out
        }

        private fun interleave(data: ByteArray, ver: Int): ByteArray {
            val blocks = BLOCKS[ver - 1]
            val eccLen = ECC_PER_BLOCK[ver - 1]
            val rawCodewords = rawModules(ver) / 8
            val shortBlocks = blocks - rawCodewords % blocks
            val shortLen = rawCodewords / blocks - eccLen
            val generator = rsGenerator(eccLen)
            val dataBlocks = ArrayList<ByteArray>()
            val eccBlocks = ArrayList<ByteArray>()
            var k = 0
            for (i in 0 until blocks) {
                val len = shortLen + if (i < shortBlocks) 0 else 1
                val block = data.copyOfRange(k, k + len)
                k += len
                dataBlocks.add(block)
                eccBlocks.add(rsRemainder(block, generator))
            }
            val out = ArrayList<Byte>()
            for (i in 0..shortLen) for (j in 0 until blocks) {
                if (i < dataBlocks[j].size) out.add(dataBlocks[j][i])
            }
            for (i in 0 until eccLen) for (j in 0 until blocks) out.add(eccBlocks[j][i])
            return out.toByteArray()
        }

        private fun gfMul(x: Int, y: Int): Int {
            var z = 0
            for (i in 7 downTo 0) {
                z = (z shl 1) xor ((z ushr 7) * 0x11D)
                z = z xor (((y ushr i) and 1) * x)
            }
            return z
        }

        private fun rsGenerator(degree: Int): IntArray {
            val result = IntArray(degree)
            result[degree - 1] = 1
            var root = 1
            for (i in 0 until degree) {
                for (j in 0 until degree) {
                    result[j] = gfMul(result[j], root)
                    if (j + 1 < degree) result[j] = result[j] xor result[j + 1]
                }
                root = gfMul(root, 0x02)
            }
            return result
        }

        private fun rsRemainder(data: ByteArray, generator: IntArray): ByteArray {
            val result = IntArray(generator.size)
            for (b in data) {
                val factor = (b.toInt() and 0xFF) xor result[0]
                for (i in 0 until result.size - 1) result[i] = result[i + 1]
                result[result.size - 1] = 0
                for (i in result.indices) result[i] = result[i] xor gfMul(generator[i], factor)
            }
            return ByteArray(result.size) { result[it].toByte() }
        }
    }

    private class Builder(val version: Int) {
        val size = version * 4 + 17
        val modules = BooleanArray(size * size)
        val isFunction = BooleanArray(size * size)

        fun set(x: Int, y: Int, dark: Boolean) { modules[y * size + x] = dark; isFunction[y * size + x] = true }

        fun build(codewords: ByteArray): QrCode {
            drawFunctionPatterns()
            placeCodewords(codewords)
            var best = 0
            var bestPenalty = Int.MAX_VALUE
            for (mask in 0 until 8) {
                applyMask(mask)
                drawFormat(mask)
                val p = penalty()
                if (p < bestPenalty) { bestPenalty = p; best = mask }
                applyMask(mask) // undo
            }
            applyMask(best)
            drawFormat(best)
            return QrCode(size, modules)
        }

        private fun drawFunctionPatterns() {
            for (i in 0 until size) { set(6, i, i % 2 == 0); set(i, 6, i % 2 == 0) }
            finder(3, 3); finder(size - 4, 3); finder(3, size - 4)
            val pos = alignmentPositions()
            for (i in pos.indices) for (j in pos.indices) {
                if ((i == 0 && j == 0) || (i == 0 && j == pos.size - 1) || (i == pos.size - 1 && j == 0)) continue
                alignment(pos[i], pos[j])
            }
            drawFormat(0) // reserve
            drawVersion()
        }

        private fun finder(cx: Int, cy: Int) {
            for (dy in -4..4) for (dx in -4..4) {
                val x = cx + dx
                val y = cy + dy
                if (x in 0 until size && y in 0 until size) {
                    val dist = maxOf(kotlin.math.abs(dx), kotlin.math.abs(dy))
                    set(x, y, dist != 2 && dist != 4)
                }
            }
        }

        private fun alignment(cx: Int, cy: Int) {
            for (dy in -2..2) for (dx in -2..2) set(cx + dx, cy + dy, maxOf(kotlin.math.abs(dx), kotlin.math.abs(dy)) != 1)
        }

        private fun alignmentPositions(): IntArray {
            if (version == 1) return IntArray(0)
            val num = version / 7 + 2
            val step = if (version == 32) 26 else (version * 4 + num * 2 + 1) / (num * 2 - 2) * 2
            val result = IntArray(num)
            result[0] = 6
            var pos = size - 7
            for (i in num - 1 downTo 1) { result[i] = pos; pos -= step }
            return result
        }

        private fun drawFormat(mask: Int) {
            val data = (0 shl 3) or mask // ECC level M = 00
            var rem = data
            repeat(10) { rem = (rem shl 1) xor ((rem ushr 9) * 0x537) }
            val bits = ((data shl 10) or rem) xor 0x5412
            fun bit(i: Int) = ((bits ushr i) and 1) != 0
            for (i in 0..5) set(8, i, bit(i))
            set(8, 7, bit(6)); set(8, 8, bit(7)); set(7, 8, bit(8))
            for (i in 9..14) set(14 - i, 8, bit(i))
            for (i in 0..7) set(size - 1 - i, 8, bit(i))
            for (i in 8..14) set(8, size - 15 + i, bit(i))
            set(8, size - 8, true)
        }

        private fun drawVersion() {
            if (version < 7) return
            var rem = version
            repeat(12) { rem = (rem shl 1) xor ((rem ushr 11) * 0x1F25) }
            val bits = (version shl 12) or rem
            for (i in 0 until 18) {
                val dark = ((bits ushr i) and 1) != 0
                val a = size - 11 + i % 3
                val b = i / 3
                set(a, b, dark); set(b, a, dark)
            }
        }

        private fun placeCodewords(data: ByteArray) {
            var i = 0
            var right = size - 1
            while (right >= 1) {
                if (right == 6) right = 5
                for (vert in 0 until size) for (j in 0..1) {
                    val x = right - j
                    val upward = ((right + 1) and 2) == 0
                    val y = if (upward) size - 1 - vert else vert
                    if (!isFunction[y * size + x] && i < data.size * 8) {
                        modules[y * size + x] = ((data[i ushr 3].toInt() ushr (7 - (i and 7))) and 1) != 0
                        i++
                    }
                }
                right -= 2
            }
        }

        private fun applyMask(mask: Int) {
            for (y in 0 until size) for (x in 0 until size) {
                if (isFunction[y * size + x]) continue
                val invert = when (mask) {
                    0 -> (x + y) % 2 == 0
                    1 -> y % 2 == 0
                    2 -> x % 3 == 0
                    3 -> (x + y) % 3 == 0
                    4 -> (x / 3 + y / 2) % 2 == 0
                    5 -> x * y % 2 + x * y % 3 == 0
                    6 -> (x * y % 2 + x * y % 3) % 2 == 0
                    else -> ((x + y) % 2 + x * y % 3) % 2 == 0
                }
                if (invert) modules[y * size + x] = !modules[y * size + x]
            }
        }

        /** Simplified penalty (runs, 2×2 blocks, balance): any mask is valid, this just picks a good one. */
        private fun penalty(): Int {
            var result = 0
            for (horizontal in listOf(true, false)) for (a in 0 until size) {
                var run = 1
                for (b in 1 until size) {
                    val cur = if (horizontal) modules[a * size + b] else modules[b * size + a]
                    val prev = if (horizontal) modules[a * size + b - 1] else modules[(b - 1) * size + a]
                    if (cur == prev) { run++; if (run == 5) result += 3 else if (run > 5) result++ } else run = 1
                }
            }
            for (y in 0 until size - 1) for (x in 0 until size - 1) {
                val c = modules[y * size + x]
                if (c == modules[y * size + x + 1] && c == modules[(y + 1) * size + x] && c == modules[(y + 1) * size + x + 1]) result += 3
            }
            val dark = modules.count { it }
            result += kotlin.math.abs(dark * 20 - size * size * 10) / (size * size) * 10
            return result
        }
    }
}
