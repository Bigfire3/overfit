package app.overfit.platform

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import app.overfit.coach.CoachPhotoLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

/** Reads a photo from app-private storage and scales it to [maxEdge] px on the long edge (JPEG 85). */
class AndroidCoachPhotos(private val maxEdge: Int = 1500) : CoachPhotoLoader {
    override suspend fun load(path: String): ByteArray? = withContext(Dispatchers.IO) {
        runCatching {
            val file = File(path)
            if (!file.isFile) return@runCatching null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            val longEdge = max(bounds.outWidth, bounds.outHeight)
            if (longEdge <= 0) return@runCatching null
            var sample = 1
            while (longEdge / (sample * 2) >= maxEdge) sample *= 2
            val decoded = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
                ?: return@runCatching null
            val scale = maxEdge.toFloat() / max(decoded.width, decoded.height)
            val bitmap = if (scale < 1f) {
                Bitmap.createScaledBitmap(decoded, (decoded.width * scale).roundToInt(), (decoded.height * scale).roundToInt(), true)
            } else decoded
            ByteArrayOutputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
                out.toByteArray()
            }
        }.getOrNull()
    }
}
