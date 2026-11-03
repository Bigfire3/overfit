package app.overfit.share

import app.overfit.platform.toNSData

import platform.Foundation.NSString
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.writeToFile
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIPasteboard
import platform.UIKit.UIViewController

/** Share sheet (UIActivityViewController, includes AirDrop), pasteboard and `.overfit` files for iOS. */
@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
class IosPlatformShare : PlatformShare {

    override fun shareText(text: String) = present(listOf(text))

    override fun shareFile(fileName: String, content: String) {
        val path = NSTemporaryDirectory().trimEnd('/') + "/" + fileName
        NSString.create(string = content).writeToFile(path, atomically = true, encoding = NSUTF8StringEncoding, error = null)
        present(listOf(NSURL.fileURLWithPath(path)))
    }

    override fun shareBytes(fileName: String, bytes: ByteArray, mimeType: String) {
        val path = NSTemporaryDirectory().trimEnd('/') + "/" + fileName
        bytes.toNSData().writeToFile(path, atomically = true)
        present(listOf(NSURL.fileURLWithPath(path)))
    }

    private fun present(items: List<Any>) {
        val controller = UIActivityViewController(activityItems = items, applicationActivities = null)
        topViewController()?.presentViewController(controller, animated = true, completion = null)
    }

    private fun topViewController(): UIViewController? {
        var top = UIApplication.sharedApplication.keyWindow?.rootViewController
        while (top?.presentedViewController != null) top = top.presentedViewController
        return top
    }

    override fun copyText(text: String) {
        UIPasteboard.generalPasteboard.string = text
    }

    override fun pasteText(): String? = UIPasteboard.generalPasteboard.string

    // In-app scanner with AVFoundation (QrScanner.ios.kt): the compact OF1 code is not a link.
    override val canScanInApp: Boolean get() = true
}
