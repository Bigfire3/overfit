package app.overfit.platform

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.math.max


actual fun prepareJpeg(bytes: ByteArray, maxEdge: Int, quality: Int): ByteArray? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxEdge) sample *= 2
    val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
        ?: return null
    val rotation = runCatching {
        when (ExifInterface(ByteArrayInputStream(bytes)).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
    }.getOrDefault(0f)
    val longest = max(decoded.width, decoded.height)
    val scale = if (longest > maxEdge) maxEdge.toFloat() / longest else 1f
    val matrix = Matrix().apply { if (scale != 1f) postScale(scale, scale); if (rotation != 0f) postRotate(rotation) }
    val out = if (scale != 1f || rotation != 0f) {
        Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
    } else decoded
    ByteArrayOutputStream().use { stream ->
        out.compress(Bitmap.CompressFormat.JPEG, quality.coerceIn(30, 100), stream)
        stream.toByteArray()
    }.also { if (out !== decoded) decoded.recycle() }
}.getOrNull()

