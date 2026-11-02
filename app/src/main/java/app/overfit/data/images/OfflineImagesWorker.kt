package app.overfit.data.images

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.overfit.OverfitApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/**
 * „Alle Bilder offline laden“: lädt die Bilder der free-exercise-db-Übungen dauerhaft in den
 * app-privaten Speicher. Bereits vorhandene Dateien werden übersprungen, einzelne Fehler brechen
 * den Lauf nicht ab (erneut starten lädt nur den Rest).
 */
class OfflineImagesWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val container = (applicationContext as OverfitApp).container
        container.catalogSeeder.seedIfNeeded()
        val refs = container.database.exerciseDao().remoteImages()
        val dir = ExerciseImages.offlineDir(applicationContext).apply { mkdirs() }
        val done = AtomicInteger(0)
        val failed = AtomicInteger(0)
        val total = refs.size
        setProgress(progress(0, total))
        val limit = Semaphore(PARALLEL)
        refs.map { ref ->
            async {
                limit.withPermit {
                    val target = ExerciseImages.offlineFile(applicationContext, ref.id)
                    if (!target.exists() && !download(container.okHttp, ref.imageRef, target, dir)) failed.incrementAndGet()
                    val n = done.incrementAndGet()
                    if (n % 10 == 0 || n == total) setProgress(progress(n, total))
                }
            }
        }.awaitAll()
        val output = workDataOf(KEY_DONE to total - failed.get(), KEY_TOTAL to total, KEY_FAILED to failed.get())
        if (failed.get() > 0) Result.failure(output) else Result.success(output)
    }

    private fun download(client: OkHttpClient, url: String, target: File, dir: File): Boolean = runCatching {
        val tmp = File.createTempFile("img", ".part", dir)
        try {
            client.newCall(Request.Builder().url(url).build()).execute().use { resp ->
                if (!resp.isSuccessful) return false
                tmp.outputStream().use { out -> resp.body.byteStream().copyTo(out) }
            }
            tmp.renameTo(target)
        } finally {
            tmp.delete()
        }
    }.getOrDefault(false)

    private fun progress(done: Int, total: Int) = workDataOf(KEY_DONE to done, KEY_TOTAL to total)

    companion object {
        const val WORK_NAME = "offline-images"
        const val KEY_DONE = "done"
        const val KEY_TOTAL = "total"
        const val KEY_FAILED = "failed"
        private const val PARALLEL = 6

        fun start(context: Context) {
            val request = OneTimeWorkRequestBuilder<OfflineImagesWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request)
        }

        fun countOffline(context: Context): Int =
            ExerciseImages.offlineDir(context).listFiles { f -> f.name.endsWith(".jpg") }?.size ?: 0
    }
}
