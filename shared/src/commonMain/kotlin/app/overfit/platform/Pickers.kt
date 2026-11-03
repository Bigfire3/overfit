package app.overfit.platform

import androidx.compose.runtime.Composable

/** Camera and photo library for body photos. Results are raw image bytes (see [prepareJpeg]). */
class PhotoPickerLauncher(
    val takePhoto: () -> Unit,
    val pickFromLibrary: () -> Unit,
    /** False if the device has no camera (simulator) – then only the library is offered. */
    val canTakePhoto: Boolean,
)

/**
 * Android: system camera (TakePicture into the app cache, never the gallery) and the system
 * photo picker. iOS: UIImagePickerController (camera / library). [onPicked] runs on the main thread.
 */
@Composable
expect fun rememberPhotoPicker(onPicked: (ByteArray) -> Unit): PhotoPickerLauncher

/** Opens the system file picker (backup import). [onPicked] gets the file content. */
@Composable
expect fun rememberFilePicker(onPicked: (ByteArray) -> Unit): () -> Unit
