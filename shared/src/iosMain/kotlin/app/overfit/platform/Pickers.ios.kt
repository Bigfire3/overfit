package app.overfit.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSData
import platform.Foundation.NSURL
import platform.Foundation.dataWithContentsOfURL
import platform.UIKit.UIApplication
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIImagePickerController
import platform.UIKit.UIImagePickerControllerDelegateProtocol
import platform.UIKit.UIImagePickerControllerOriginalImage
import platform.UIKit.UIImagePickerControllerSourceType
import platform.UIKit.UINavigationControllerDelegateProtocol
import platform.UIKit.UIViewController
import platform.UniformTypeIdentifiers.UTTypeData
import platform.darwin.NSObject

private fun topViewController(): UIViewController? {
    var top = UIApplication.sharedApplication.keyWindow?.rootViewController
    while (top?.presentedViewController != null) top = top.presentedViewController
    return top
}

/** Keeps the result handler; UIKit holds delegates weakly, so the composable keeps this alive. */
private class ImagePickerDelegate(val onImage: (ByteArray) -> Unit) :
    NSObject(), UIImagePickerControllerDelegateProtocol, UINavigationControllerDelegateProtocol {

    override fun imagePickerController(picker: UIImagePickerController, didFinishPickingMediaWithInfo: Map<Any?, *>) {
        val image = didFinishPickingMediaWithInfo[UIImagePickerControllerOriginalImage] as? UIImage
        picker.dismissViewControllerAnimated(true, completion = null)
        image?.let { UIImageJPEGRepresentation(it, 0.92)?.toByteArray() }?.let(onImage)
    }

    override fun imagePickerControllerDidCancel(picker: UIImagePickerController) {
        picker.dismissViewControllerAnimated(true, completion = null)
    }
}

@Composable
actual fun rememberPhotoPicker(onPicked: (ByteArray) -> Unit): PhotoPickerLauncher {
    val callback by rememberUpdatedState(onPicked)
    val delegate = remember { ImagePickerDelegate { callback(it) } }
    return remember {
        fun open(source: UIImagePickerControllerSourceType) {
            if (!UIImagePickerController.isSourceTypeAvailable(source)) return
            val picker = UIImagePickerController()
            picker.sourceType = source
            picker.delegate = delegate
            topViewController()?.presentViewController(picker, animated = true, completion = null)
        }
        PhotoPickerLauncher(
            takePhoto = { open(UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera) },
            pickFromLibrary = { open(UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypePhotoLibrary) },
            canTakePhoto = UIImagePickerController.isSourceTypeAvailable(UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera),
        )
    }
}

private class DocumentPickerDelegate(val onFile: (ByteArray) -> Unit) : NSObject(), UIDocumentPickerDelegateProtocol {
    @OptIn(ExperimentalForeignApi::class)
    override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
        val url = didPickDocumentsAtURLs.firstOrNull() as? NSURL ?: return
        NSData.dataWithContentsOfURL(url)?.toByteArray()?.let(onFile)
    }
}

@Composable
actual fun rememberFilePicker(onPicked: (ByteArray) -> Unit): () -> Unit {
    val callback by rememberUpdatedState(onPicked)
    val delegate = remember { DocumentPickerDelegate { callback(it) } }
    return remember {
        {
            val picker = UIDocumentPickerViewController(forOpeningContentTypes = listOf(UTTypeData), asCopy = true)
            picker.delegate = delegate
            topViewController()?.presentViewController(picker, animated = true, completion = null)
            Unit
        }
    }
}
