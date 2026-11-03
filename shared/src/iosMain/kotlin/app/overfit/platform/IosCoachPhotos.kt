package app.overfit.platform

import app.overfit.coach.CoachPhotoLoader
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.UIKit.UIGraphicsImageRenderer
import platform.UIKit.UIGraphicsImageRendererFormat
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import kotlin.math.max

/** Reads a photo from the app container and scales it to [maxEdge] px on the long edge (JPEG 0.85). */
@OptIn(ExperimentalForeignApi::class)
class IosCoachPhotos(private val maxEdge: Double = 1500.0) : CoachPhotoLoader {
    override suspend fun load(path: String): ByteArray? {
        val image = UIImage.imageWithContentsOfFile(path) ?: return null
        val (w, h) = image.size.useContents { width to height }
        val longEdge = max(w, h)
        if (longEdge <= 0.0) return null
        val scale = if (longEdge > maxEdge) maxEdge / longEdge else 1.0
        val size = CGSizeMake(w * scale, h * scale)
        val format = UIGraphicsImageRendererFormat.defaultFormat().apply { setScale(1.0) }
        val renderer = UIGraphicsImageRenderer(size = size, format = format)
        val scaled = renderer.imageWithActions { _ ->
            image.drawInRect(CGRectMake(0.0, 0.0, w * scale, h * scale))
        }
        val data = UIImageJPEGRepresentation(scaled, 0.85) ?: return null
        return data.toByteArray()
    }
}
