package app.overfit.share

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Regression for the QR size: Push/Pull/Legs must stay small (module counts are documented in docs/DECISIONS.md). */
class QrSizeTest {
    private val dir = "src/commonMain/composeResources/files/samples/"

    private fun sample(key: String) =
        (ShareFormat.fromJson(File("$dir$key.json").readText()) as ShareParse.Ok).training

    private fun oldModules(t: SharedTraining) = QrCode.sizeFor(ShareFormat.toLink(t), QrCode.Level.M)!!

    @Test fun printAndCheckModuleCounts() {
        for (key in listOf("push", "pull", "legs")) {
            val t = sample(key)
            val old = oldModules(t)
            val text = QrPayload.encode(t)
            val now = QrCode.sizeFor(text)!!
            println("QRSIZE $key old=${old}x$old (link ${ShareFormat.toLink(t).length} chars, M) new=${now}x$now (${text.length} chars, L)")
            assertTrue(now <= MAX_MODULES, "$key: $now > $MAX_MODULES")
            assertTrue(now < old)
            assertEquals(t.exercises.size, QrPayload.decode(text)!!.exercises.size)
        }
    }

    @Test fun newQrDecodesWithZxing() {
        for (key in listOf("push", "pull", "legs")) {
            val text = QrPayload.encode(sample(key))
            assertEquals(text, decode(QrCode.encode(text)))
        }
    }

    private fun decode(qr: QrCode): String = QrZxingDecode.decode(qr)

    companion object { const val MAX_MODULES = 57 }
}
