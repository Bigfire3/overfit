package app.overfit.platform

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.UIKit.UIGraphicsImageRenderer
import platform.UIKit.UIGraphicsImageRendererFormat
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import kotlin.math.max

@OptIn(ExperimentalForeignApi::class)
actual fun prepareJpeg(bytes: ByteArray, maxEdge: Int, quality: Int): ByteArray? = runCatching {
    val image = UIImage.imageWithData(bytes.toNSData()) ?: return null
    val (w, h) = image.size.useContents { width to height }
    if (w <= 0 || h <= 0) return null
    val longest = max(w, h)
    val scale = if (longest > maxEdge) maxEdge / longest else 1.0
    val tw = w * scale
    val th = h * scale
    // Drawing applies the EXIF orientation; scale 1 = pixels, not points.
    val format = UIGraphicsImageRendererFormat.defaultFormat().apply { setScale(1.0) }
    val renderer = UIGraphicsImageRenderer(size = CGSizeMake(tw, th), format = format)
    val scaled = renderer.imageWithActions { _ -> image.drawInRect(CGRectMake(0.0, 0.0, tw, th)) }
    UIImageJPEGRepresentation(scaled, quality.coerceIn(30, 100) / 100.0)?.toByteArray()
}.getOrNull()
