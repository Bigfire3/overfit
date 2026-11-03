package app.overfit.share

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kotlinx.coroutines.flow.MutableStateFlow

/** What the platform shell provides for sharing: share sheet, clipboard, files. */
interface PlatformShare {
    /** Opens the system share sheet with plain text (WhatsApp, Quick Share, AirDrop, …). */
    fun shareText(text: String)

    /** Shares [content] as a file named [fileName] through the share sheet. */
    fun shareFile(fileName: String, content: String)

    /** Shares binary content (backup ZIP) as a file through the share sheet / "Save to Files". */
    fun shareBytes(fileName: String, bytes: ByteArray, mimeType: String) = Unit

    fun copyText(text: String)

    /** Current clipboard text, if any. */
    fun pasteText(): String?

    /** True if the app can scan QR codes itself (Android). On iOS the camera app opens the link. */
    val canScanInApp: Boolean
}

/** For tests and previews. */
class NoopPlatformShare : PlatformShare {
    var clipboard: String? = null
    override fun shareText(text: String) = Unit
    override fun shareFile(fileName: String, content: String) = Unit
    override fun copyText(text: String) { clipboard = text }
    override fun pasteText(): String? = clipboard
    override val canScanInApp: Boolean get() = false
}

/**
 * Text that arrives from outside (opened link, `.overfit` file, QR scan) and waits for the import
 * screen. The shells call [offer]; the UI consumes it.
 */
class ImportInbox {
    val pending = MutableStateFlow<String?>(null)
    fun offer(text: String) { if (text.isNotBlank()) pending.value = text }
    fun consume(): String? = pending.value.also { pending.value = null }
}

/** Live camera view that reports the first QR code it reads (Android only, see [PlatformShare.canScanInApp]). */
@Composable
expect fun QrScanner(modifier: Modifier, onScanned: (String) -> Unit, onPermissionDenied: () -> Unit)
