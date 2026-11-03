package app.overfit.platform

/**
 * Decodes a photo (JPEG/HEIC/PNG from camera or library), applies its orientation, scales the
 * longest edge down to [maxEdge] and encodes it as JPEG. Null if the bytes are not an image.
 */
expect fun prepareJpeg(bytes: ByteArray, maxEdge: Int = 1600, quality: Int = 85): ByteArray?
