package app.overfit.share

/**
 * Raw Deflate (RFC 1951) in pure Kotlin so that sharing works the same on Android and iOS and the
 * web page can read it with any standard inflate (`DecompressionStream('deflate-raw')`, pako).
 *
 * [compress] writes one block with fixed Huffman codes and LZ77 matches (plenty for a few hundred
 * bytes of JSON); [decompress] understands all block types, so foreign encoders work too.
 */
internal object Deflate {

    private val LEN_BASE = intArrayOf(3, 4, 5, 6, 7, 8, 9, 10, 11, 13, 15, 17, 19, 23, 27, 31, 35, 43, 51, 59, 67, 83, 99, 115, 131, 163, 195, 227, 258)
    private val LEN_EXTRA = intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 2, 2, 2, 2, 3, 3, 3, 3, 4, 4, 4, 4, 5, 5, 5, 5, 0)
    private val DIST_BASE = intArrayOf(1, 2, 3, 4, 5, 7, 9, 13, 17, 25, 33, 49, 65, 97, 129, 193, 257, 385, 513, 769, 1025, 1537, 2049, 3073, 4097, 6145, 8193, 12289, 16385, 24577)
    private val DIST_EXTRA = intArrayOf(0, 0, 0, 0, 1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 6, 7, 7, 8, 8, 9, 9, 10, 10, 11, 11, 12, 12, 13, 13)

    // ---------------------------------------------------------------- compress

    private class BitWriter {
        private var buf = ByteArray(256)
        private var size = 0
        private var acc = 0
        private var count = 0

        /** Writes [n] bits of [value], least significant bit first. */
        fun bits(value: Int, n: Int) {
            acc = acc or (value shl count)
            count += n
            while (count >= 8) {
                put(acc and 0xFF)
                acc = acc ushr 8
                count -= 8
            }
        }

        /** Huffman codes go out most significant bit first. */
        fun code(code: Int, n: Int) {
            var reversed = 0
            for (i in 0 until n) reversed = reversed or (((code ushr i) and 1) shl (n - 1 - i))
            bits(reversed, n)
        }

        private fun put(b: Int) {
            if (size == buf.size) buf = buf.copyOf(size * 2)
            buf[size++] = b.toByte()
        }

        fun finish(): ByteArray {
            if (count > 0) put(acc and 0xFF)
            return buf.copyOf(size)
        }
    }

    private fun BitWriter.literalLength(symbol: Int) {
        when {
            symbol <= 143 -> code(0x30 + symbol, 8)
            symbol <= 255 -> code(0x190 + symbol - 144, 9)
            symbol <= 279 -> code(symbol - 256, 7)
            else -> code(0xC0 + symbol - 280, 8)
        }
    }

    fun compress(data: ByteArray): ByteArray {
        val w = BitWriter()
        w.bits(1, 1) // BFINAL
        w.bits(1, 2) // BTYPE = fixed Huffman
        val n = data.size
        val head = IntArray(HASH_SIZE) { -1 }
        val prev = IntArray(maxOf(n, 1))
        fun hash(i: Int) =
            (((data[i].toInt() and 0xFF) shl 10) xor ((data[i + 1].toInt() and 0xFF) shl 5) xor (data[i + 2].toInt() and 0xFF)) and (HASH_SIZE - 1)
        fun insert(i: Int) {
            if (i + 2 < n) {
                val h = hash(i)
                prev[i] = head[h]
                head[h] = i
            }
        }

        var i = 0
        while (i < n) {
            var bestLen = 0
            var bestDist = 0
            if (i + 2 < n) {
                var candidate = head[hash(i)]
                var chain = MAX_CHAIN
                val maxLen = minOf(258, n - i)
                while (candidate >= 0 && chain-- > 0 && i - candidate <= WINDOW) {
                    var len = 0
                    while (len < maxLen && data[candidate + len] == data[i + len]) len++
                    if (len > bestLen) {
                        bestLen = len
                        bestDist = i - candidate
                        if (len == maxLen) break
                    }
                    candidate = prev[candidate]
                }
            }
            if (bestLen >= 3) {
                var li = LEN_BASE.size - 1
                while (LEN_BASE[li] > bestLen) li--
                w.literalLength(257 + li)
                w.bits(bestLen - LEN_BASE[li], LEN_EXTRA[li])
                var di = DIST_BASE.size - 1
                while (DIST_BASE[di] > bestDist) di--
                w.code(di, 5)
                w.bits(bestDist - DIST_BASE[di], DIST_EXTRA[di])
                for (k in 0 until bestLen) insert(i + k)
                i += bestLen
            } else {
                w.literalLength(data[i].toInt() and 0xFF)
                insert(i)
                i++
            }
        }
        w.literalLength(256)
        return w.finish()
    }

    private const val HASH_SIZE = 1 shl 15
    private const val WINDOW = 32768
    private const val MAX_CHAIN = 48

    // -------------------------------------------------------------- decompress

    private class Huffman(lengths: IntArray, offset: Int, n: Int) {
        val count = IntArray(16)
        val symbol = IntArray(n)

        init {
            for (s in 0 until n) count[lengths[offset + s]]++
            val offs = IntArray(16)
            for (l in 1 until 15) offs[l + 1] = offs[l] + count[l]
            for (s in 0 until n) {
                val l = lengths[offset + s]
                if (l != 0) symbol[offs[l]++] = s
            }
        }
    }

    private class Reader(val src: ByteArray, val maxOut: Int) {
        var pos = 0
        var acc = 0
        var cnt = 0
        var out = ByteArray(maxOf(64, minOf(src.size * 4, maxOut)))
        var outSize = 0

        fun bits(need: Int): Int {
            var v = acc
            while (cnt < need) {
                require(pos < src.size) { "Unexpected end of data" }
                v = v or ((src[pos++].toInt() and 0xFF) shl cnt)
                cnt += 8
            }
            acc = v ushr need
            cnt -= need
            return v and ((1 shl need) - 1)
        }

        fun decode(h: Huffman): Int {
            var code = 0
            var first = 0
            var index = 0
            for (len in 1..15) {
                code = code or bits(1)
                val c = h.count[len]
                if (code - c < first) return h.symbol[index + (code - first)]
                index += c
                first += c
                first = first shl 1
                code = code shl 1
            }
            throw IllegalArgumentException("Invalid Huffman code")
        }

        fun emit(b: Int) {
            require(outSize < maxOut) { "Output too large" }
            if (outSize == out.size) out = out.copyOf(minOf(out.size * 2, maxOut))
            out[outSize++] = b.toByte()
        }
    }

    fun decompress(data: ByteArray, maxOutput: Int = 1 shl 20): ByteArray {
        val r = Reader(data, maxOutput)
        do {
            val last = r.bits(1)
            when (r.bits(2)) {
                0 -> stored(r)
                1 -> codes(r, fixedLit, fixedDist)
                2 -> {
                    val nlen = r.bits(5) + 257
                    val ndist = r.bits(5) + 1
                    val ncode = r.bits(4) + 4
                    require(nlen <= 286 && ndist <= 30) { "Bad counts" }
                    val lengths = IntArray(320)
                    for (i in 0 until ncode) lengths[ORDER[i]] = r.bits(3)
                    val lencode = Huffman(lengths, 0, 19)
                    val all = IntArray(nlen + ndist)
                    var idx = 0
                    while (idx < nlen + ndist) {
                        val sym = r.decode(lencode)
                        if (sym < 16) {
                            all[idx++] = sym
                        } else {
                            var len = 0
                            val repeat: Int
                            when (sym) {
                                16 -> { require(idx > 0) { "No previous length" }; len = all[idx - 1]; repeat = 3 + r.bits(2) }
                                17 -> repeat = 3 + r.bits(3)
                                else -> repeat = 11 + r.bits(7)
                            }
                            require(idx + repeat <= nlen + ndist) { "Too many lengths" }
                            repeat(repeat) { all[idx++] = len }
                        }
                    }
                    require(all[256] != 0) { "No end-of-block code" }
                    codes(r, Huffman(all, 0, nlen), Huffman(all, nlen, ndist))
                }
                else -> throw IllegalArgumentException("Invalid block type")
            }
        } while (last == 0)
        return r.out.copyOf(r.outSize)
    }

    private fun stored(r: Reader) {
        r.acc = 0
        r.cnt = 0
        require(r.pos + 4 <= r.src.size) { "Unexpected end of data" }
        val len = (r.src[r.pos].toInt() and 0xFF) or ((r.src[r.pos + 1].toInt() and 0xFF) shl 8)
        val nlen = (r.src[r.pos + 2].toInt() and 0xFF) or ((r.src[r.pos + 3].toInt() and 0xFF) shl 8)
        require(len == nlen.inv() and 0xFFFF) { "Stored length mismatch" }
        r.pos += 4
        require(r.pos + len <= r.src.size) { "Unexpected end of data" }
        for (i in 0 until len) r.emit(r.src[r.pos + i].toInt() and 0xFF)
        r.pos += len
    }

    private fun codes(r: Reader, lit: Huffman, dist: Huffman) {
        while (true) {
            var sym = r.decode(lit)
            if (sym < 256) {
                r.emit(sym)
            } else if (sym == 256) {
                return
            } else {
                sym -= 257
                require(sym < 29) { "Invalid length symbol" }
                val len = LEN_BASE[sym] + r.bits(LEN_EXTRA[sym])
                val ds = r.decode(dist)
                require(ds < 30) { "Invalid distance symbol" }
                val d = DIST_BASE[ds] + r.bits(DIST_EXTRA[ds])
                require(d <= r.outSize) { "Distance too far back" }
                repeat(len) { r.emit(r.out[r.outSize - d].toInt() and 0xFF) }
            }
        }
    }

    private val ORDER = intArrayOf(16, 17, 18, 0, 8, 7, 9, 6, 10, 5, 11, 4, 12, 3, 13, 2, 14, 1, 15)

    private val fixedLit = Huffman(
        IntArray(288) { if (it < 144) 8 else if (it < 256) 9 else if (it < 280) 7 else 8 }, 0, 288,
    )
    private val fixedDist = Huffman(IntArray(30) { 5 }, 0, 30)
}
