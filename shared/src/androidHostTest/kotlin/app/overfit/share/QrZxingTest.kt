package app.overfit.share

import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import kotlin.test.Test
import kotlin.test.assertEquals

/** The generator is checked against the library the Android scanner uses. */
class QrZxingTest {
    private fun decode(qr: QrCode): String {
        val scale = 4
        val quiet = 4
        val dim = (qr.size + 2 * quiet) * scale
        val pixels = IntArray(dim * dim) { i ->
            val x = i % dim / scale - quiet
            val y = i / dim / scale - quiet
            if (qr[x, y]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
        }
        return QRCodeReader().decode(BinaryBitmap(HybridBinarizer(RGBLuminanceSource(dim, dim, pixels)))).text
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
