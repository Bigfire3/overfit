package app.overfit.data.images

import app.overfit.data.ExerciseRepository
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * "Download all images": loads the free-exercise-db photos into Coil's disk cache so they are
 * available offline. Workout Guide graphics are bundled anyway. Runs while the app is open;
 * already cached images are skipped, so starting again only loads the rest.
 */
class OfflineImages(private val scope: CoroutineScope, private val exercises: ExerciseRepository) {
    data class Status(val running: Boolean = false, val done: Int = 0, val total: Int = 0, val stored: Int = 0)

    private val _status = MutableStateFlow(Status())
    val status: StateFlow<Status> = _status.asStateFlow()
    private var job: Job? = null

    /** Counts the images that are already on disk. */
    fun refresh(context: PlatformContext) {
        if (job?.isActive == true) return
        scope.launch {
            val urls = exercises.remoteImageUrls()
            _status.value = Status(total = urls.size, stored = urls.count { isCached(context, it) })
        }
    }

    fun start(context: PlatformContext) {
        if (job?.isActive == true) return
        job = scope.launch {
            val urls = exercises.remoteImageUrls()
            val loader = SingletonImageLoader.get(context)
            _status.value = Status(running = true, total = urls.size)
            val limit = Semaphore(PARALLEL)
            urls.map { url ->
                async {
                    limit.withPermit {
                        if (!isCached(context, url)) {
                            val request = ImageRequest.Builder(context)
                                .data(url)
                                .memoryCachePolicy(CachePolicy.DISABLED)
                                .size(64) // only the file matters; keep decoding cheap
                                .build()
                            loader.execute(request) is SuccessResult
                        }
                        _status.update { it.copy(done = it.done + 1) }
                    }
                }
            }.awaitAll()
            _status.value = Status(total = urls.size, stored = urls.count { isCached(context, it) })
        }
    }

    private fun isCached(context: PlatformContext, url: String): Boolean {
        val cache = SingletonImageLoader.get(context).diskCache ?: return false
        return cache.openSnapshot(url)?.also { it.close() } != null
    }

    private companion object {
        const val PARALLEL = 6
    }
}
