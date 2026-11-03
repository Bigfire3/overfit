package app.overfit

import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import app.overfit.share.ShareFormat
import app.overfit.share.ShareParse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/** Tapping an `.overfit` file (VIEW) or sharing it to Overfit (SEND) delivers its content to the import screen. */
@RunWith(RobolectricTestRunner::class)
class IncomingShareTest {
    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val json = """{"v":1,"kind":"template","name":"Datei-Test","exercises":[{"id":"wg:bench-press","sets":[{"kg":80,"reps":8}]}]}"""

    private fun file(): File = File.createTempFile("Push", ".overfit").apply { writeText(json); deleteOnExit() }

    private fun assertImportable(text: String?) {
        val parsed = ShareFormat.parseInput(text.orEmpty())
        assertTrue(parsed is ShareParse.Ok)
        assertEquals("Datei-Test", (parsed as ShareParse.Ok).training.name)
    }

    @Test fun viewIntentWithFileUri() = assertImportable(IncomingShare.textOf(context, Intent(Intent.ACTION_VIEW, Uri.fromFile(file()))))

    @Test fun sendIntentWithStream() {
        val intent = Intent(Intent.ACTION_SEND).setType("application/octet-stream").putExtra(Intent.EXTRA_STREAM, Uri.fromFile(file()))
        assertImportable(IncomingShare.textOf(context, intent))
    }

    @Test fun sendIntentWithLinkText() {
        val link = ShareFormat.toLink(ShareFormat.parseInput(json).let { (it as ShareParse.Ok).training })
        val intent = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "Schau mal: $link")
        assertImportable(IncomingShare.textOf(context, intent))
    }

    @Test fun viewIntentWithLinkAndUnknownActions() {
        val link = ShareFormat.toLink(ShareFormat.parseInput(json).let { (it as ShareParse.Ok).training })
        assertImportable(IncomingShare.textOf(context, Intent(Intent.ACTION_VIEW, Uri.parse(link))))
        assertNull(IncomingShare.textOf(context, Intent(Intent.ACTION_MAIN)))
        assertNull(IncomingShare.textOf(context, Intent(Intent.ACTION_VIEW, Uri.fromFile(File("/nonexistent/x.overfit")))))
    }
}
