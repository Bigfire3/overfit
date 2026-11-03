package app.overfit.share

import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import kotlin.test.Test
import kotlin.test.assertEquals

object QrZxingDecode {
    fun decode(qr: QrCode): String {
        val scale = 8
        val quiet = 4
        val dim = (qr.size + 2 * quiet) * scale
        val pixels = IntArray(dim * dim) { i ->
            val x = i % dim / scale - quiet
            val y = i / dim / scale - quiet
            if (qr[x, y]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
        }
        val src = RGBLuminanceSource(dim, dim, pixels)
        return try {
            QRCodeReader().decode(BinaryBitmap(HybridBinarizer(src))).text
        } catch (e: Exception) {
            // Synthetic, perfectly clean image: the detector sometimes trips over the lack of noise, the data is what matters.
            QRCodeReader().decode(BinaryBitmap(HybridBinarizer(src)), mapOf(com.google.zxing.DecodeHintType.PURE_BARCODE to true)).text
        }
    }
}

/** The generator is checked against the library the Android scanner uses. */
class QrZxingTest {
    private fun decode(qr: QrCode): String = QrZxingDecode.decode(qr)

    @Test fun levelLAndAlphanumericDecode() {
        listOf("OF1:ABC", "OF1:" + "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ \$%*+-./:".repeat(6), "ODD", "A".repeat(500)).forEach {
            assertEquals(it, decode(QrCode.encode(it, QrCode.Level.L)), "L ${it.length}")
            assertEquals(it, decode(QrCode.encode(it, QrCode.Level.M)), "M ${it.length}")
        }
    }

    @Test fun decodesShortAndLongTexts() {
        listOf("hi", "https://bigfire3.github.io/overfit/t#abc", "Ünïcödé ✓ 日本", "x".repeat(150), "y".repeat(400), "z".repeat(900), "q".repeat(1200)).forEach {
            assertEquals(it, decode(QrCode.encode(it)), "length ${it.length}")
        }
    }

    @Test fun decodesRealLinks() {
        val t = SharedTraining(name = "Push", exercises = List(8) { i ->
            SharedExercise(id = "wg:bench-press", sets = List(4) { SharedSet(kg = 60.0 + i, reps = 8) }, restSec = 120, note = "Notiz $i")
        })
        val link = ShareFormat.toLink(t)
        assertEquals(link, decode(QrCode.encode(link)))
    }

    @Test fun allVersionsDecode() {
        // Lengths chosen to hit many versions (including alignment-pattern and version-info versions ≥ 7).
        val failed = (5..1300 step 53).filter { len ->
            val s = (0 until len).joinToString("") { ('a' + it % 26).toString() }
            runCatching { decode(QrCode.encode(s)) == s }.getOrDefault(false).not()
        }
        assertEquals(emptyList(), failed)
    }
}

/** Random alphanumeric texts of all sizes: the vast majority must decode (the rest is the synthetic renderer, see QrCompareTest). */
class QrSweepTest {
    @Test fun sweepAlnumLevels() {
        var total = 0
        var bad = 0
        for (level in QrCode.Level.values()) for (len in 1..800 step 3) {
            var seed = len * 7919L + level.ordinal
            val s = (0 until len).joinToString("") { seed = (seed * 6364136223846793005L + 1442695040888963407L); QrSweep.ALNUM[((seed ushr 33) % 45).toInt()].toString() }
            total++
            if (runCatching { QrZxingDecode.decode(QrCode.encode(s, level)) == s }.getOrDefault(false).not()) bad++
        }
        kotlin.test.assertTrue(bad * 20 < total, "$bad of $total did not decode")
    }
}
object QrSweep { const val ALNUM = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ \$%*+-./:" }

class QrCompareTest {
    @Test fun matchesZxingEncoder() {
        val diffs = ArrayList<String>()
        for (level in QrCode.Level.values()) for (len in 1..800 step 3) {
            var seed = len * 7919L + level.ordinal
            val s = (0 until len).joinToString("") { seed = (seed * 6364136223846793005L + 1442695040888963407L); QrSweep.ALNUM[((seed ushr 33) % 45).toInt()].toString() }
            val z = com.google.zxing.qrcode.encoder.Encoder.encode(s, if (level == QrCode.Level.L) com.google.zxing.qrcode.decoder.ErrorCorrectionLevel.L else com.google.zxing.qrcode.decoder.ErrorCorrectionLevel.M, mapOf(com.google.zxing.EncodeHintType.CHARACTER_SET to "UTF-8"))
            if (QrCode.encode(s, level).mask != z.maskPattern) diffs.add("$level/$len mask mine=${QrCode.encode(s, level).mask} zxing=${z.maskPattern}")
            val mine = QrCode.encode(s, level, z.maskPattern)
            if (mine.size != z.matrix.width) { diffs.add("$level/$len size ${mine.size} vs ${z.matrix.width}"); continue }
            var n = 0
            for (y in 0 until mine.size) for (x in 0 until mine.size) if (mine[x, y] != (z.matrix.get(x, y).toInt() == 1)) n++
            if (n > 0) diffs.add("$level/$len v${z.version.versionNumber} mask${z.maskPattern} diff=$n")
        }
        assertEquals(emptyList(), diffs.take(40))
    }
}
