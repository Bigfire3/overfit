package app.overfit.share

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

/** Share sheet (ACTION_SEND), clipboard and `.overfit` files for Android. */
class AndroidPlatformShare(private val context: Context) : PlatformShare {

    override fun shareText(text: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        launchChooser(send)
    }

    override fun shareFile(fileName: String, content: String) {
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        val file = File(dir, fileName).apply { writeText(content) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = ShareFormat.MIME_TYPE
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        launchChooser(send)
    }

    private fun launchChooser(send: Intent) {
        val chooser = Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    private val clipboard get() = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    override fun copyText(text: String) {
        clipboard.setPrimaryClip(ClipData.newPlainText("Overfit", text))
    }

    override fun pasteText(): String? = runCatching {
        clipboard.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()
    }.getOrNull()

    override val canScanInApp: Boolean get() = true
}
