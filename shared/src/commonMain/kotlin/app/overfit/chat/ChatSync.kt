package app.overfit.chat

import app.overfit.data.KeyValueStore
import app.overfit.data.WorkoutRecord
import app.overfit.domain.Template
import app.overfit.fuel.FuelException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

/** Where [ChatSync] reads the app's data from (database, profile, coach memory). */
interface ChatDataSource {
    suspend fun templates(): List<Template>
    suspend fun workouts(): List<WorkoutRecord>
    suspend fun workout(id: String): WorkoutRecord?
    fun profile(now: Long): SyncDoc
    /** The coach memory text (phase 4), null or blank = none yet. */
    fun memory(): String? = null
    /** Body photos (phase 3). Empty until the photo diary exists. */
    suspend fun photos(): List<ChatPhotoRef> = emptyList()
    /** The photo as JPEG, long edge about 1500 px; null if it is gone. */
    suspend fun photoJpeg(id: String): ByteArray? = null
}

data class ChatPhotoRef(val id: String, val takenAt: Long, val workoutId: String?)

enum class ChatSyncProblem { NONE, OFFLINE, QUOTA, FAILED }

data class ChatSyncState(
    val enabled: Boolean = false,
    val photos: Boolean = false,
    val busy: Boolean = false,
    /** Progress of the first (full) upload; both 0 when none is running. */
    val done: Int = 0,
    val total: Int = 0,
    val problem: ChatSyncProblem = ChatSyncProblem.NONE,
    /** Epoch milliseconds of the last successful upload. */
    val lastSyncAt: Long? = null,
)

/**
 * Keeps the KI-Chat copy of the training data on the server up to date (docs/backend-api.md). Works by comparing
 * fingerprints of the documents with what was uploaded last, so nothing is lost when the phone is offline: a failed
 * upload just leaves the fingerprints as they were and the next run sends the difference.
 *
 * Changes are bundled: the first change starts a [throttleMs] window, everything marked within it goes out together.
 * [flush] (workout finished, app to background) sends right away.
 */
class ChatSync(
    private val service: () -> ChatService?,
    private val kv: KeyValueStore,
    private val source: ChatDataSource,
    private val scope: CoroutineScope,
    private val clock: () -> Long,
    private val throttleMs: Long = 10_000,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val lock = Mutex()
    private val _state = MutableStateFlow(
        ChatSyncState(
            enabled = kv.get(K_ENABLED) == "1",
            photos = kv.get(K_PHOTOS) == "1",
            lastSyncAt = kv.get(K_LAST)?.toLongOrNull(),
        ),
    )
    val state: StateFlow<ChatSyncState> = _state.asStateFlow()

    private var hashes: MutableMap<String, String> = loadHashes()
    private var uploadedPhotos: MutableSet<String> = kv.get(K_PHOTO_IDS)?.split(',')?.filter { it.isNotEmpty() }?.toMutableSet() ?: mutableSetOf()

    // What changed since the last run. Marking is idempotent; a mark that races with a run is picked up by the next one.
    private data class Dirty(
        val workouts: Set<String> = emptySet(),
        val templates: Boolean = false,
        val profile: Boolean = false,
        val memory: Boolean = false,
    )
    private val dirty = MutableStateFlow(Dirty())
    /** True when the next run has to compare everything (after a failure, after start, after enabling). */
    // After a process start everything is compared once: marks from before the process died are gone.
    private var full = kv.get(K_FULL) == "1" || kv.get(K_ENABLED) == "1"
    private var pending: Job? = null

    // --- Triggers ---------------------------------------------------------------------------------------------

    fun markWorkout(id: String) = mark { it.copy(workouts = it.workouts + id) }
    fun markTemplates() = mark { it.copy(templates = true) }
    fun markProfile() = mark { it.copy(profile = true) }
    fun markMemory() = mark { it.copy(memory = true) }

    private fun mark(change: (Dirty) -> Dirty) {
        if (!_state.value.enabled) return
        dirty.update(change)
        schedule()
    }

    /** Sends everything that changed right now (workout finished, app goes to the background, app returns). */
    fun flush() {
        if (!_state.value.enabled) return
        schedule(immediate = true)
    }

    private fun schedule(immediate: Boolean = false) {
        val running = pending
        if (running != null && running.isActive) {
            // A window is already open; an immediate request replaces its waiting time.
            if (!immediate) return
            running.cancel()
        }
        pending = scope.launch {
            if (!immediate) delay(throttleMs)
            runOnce()
        }
    }

    // --- Switching on and off ---------------------------------------------------------------------------------

    /** Switches the connection on (consent given) and uploads everything. Throws [FuelException] if enabling fails. */
    suspend fun enable(photos: Boolean) {
        val api = service() ?: throw FuelException("not_configured", 503)
        api.enable(photos)
        kv.put(K_ENABLED, "1")
        setPhotos(photos)
        lock.withLock {
            hashes = mutableMapOf()
            uploadedPhotos = mutableSetOf()
            saveHashes()
            savePhotoIds()
            full = true
            kv.put(K_FULL, "1")
            kv.put(K_RESET, "1")
        }
        _state.update { it.copy(enabled = true, problem = ChatSyncProblem.NONE) }
        runOnce()
    }

    /** Changes the photo consent afterwards; off deletes the photos on the server. */
    suspend fun setPhotosEnabled(on: Boolean) {
        val api = service() ?: throw FuelException("not_configured", 503)
        api.enable(on)
        setPhotos(on)
        if (!on) { uploadedPhotos = mutableSetOf(); savePhotoIds() } else flush()
    }

    /** "KI-Chat trennen": the server deletes everything; local state is reset. */
    suspend fun disable() {
        pending?.cancel()
        service()?.disable()
        forgetLocally()
    }

    /** The server says the connection is off (e.g. switched off from another phone of the account). */
    fun serverSaysOff() { pending?.cancel(); forgetLocally() }

    private fun forgetLocally() {
        listOf(K_ENABLED, K_PHOTOS, K_HASHES, K_PHOTO_IDS, K_FULL, K_RESET, K_LAST).forEach(kv::remove)
        hashes = mutableMapOf()
        uploadedPhotos = mutableSetOf()
        dirty.value = Dirty()
        full = false
        _state.value = ChatSyncState()
    }

    private fun setPhotos(on: Boolean) {
        if (on) kv.put(K_PHOTOS, "1") else kv.remove(K_PHOTOS)
        _state.update { it.copy(photos = on) }
    }

    // --- The upload -------------------------------------------------------------------------------------------

    /** One sync run: collects what changed, uploads it in packages. Safe to call any time; runs never overlap. */
    suspend fun runOnce() = lock.withLock {
        if (!_state.value.enabled) return@withLock
        val api = service() ?: return@withLock
        val wasFull = full
        val d = dirty.getAndUpdate { Dirty() }
        val workoutIds = d.workouts
        val templates = d.templates
        val profile = d.profile
        val memory = d.memory
        val reset = kv.get(K_RESET) == "1"
        _state.update { it.copy(busy = true, problem = ChatSyncProblem.NONE) }
        try {
            val now = clock()
            val ops = mutableListOf<Op>()
            val seen = mutableSetOf<String>()
            fun consider(doc: SyncDoc) { seen += doc.key; if (hashes[doc.key] != ChatDocs.fingerprint(doc)) ops += Op.Up(doc) }
            fun missing(kind: String) = hashes.keys.filter { it.startsWith("$kind/") && it !in seen }

            if (wasFull || profile) consider(source.profile(now))
            if (wasFull || memory) {
                val text = source.memory()?.takeIf { it.isNotBlank() }
                if (text != null) consider(ChatDocs.memory(text, now))
                else hashes.keys.filter { it.startsWith("${DocKind.MEMORY}/") }.forEach { ops += Op.Del(SyncDelete(DocKind.MEMORY, it.substringAfter('/'))) }
            }
            if (wasFull || templates) {
                source.templates().forEach { consider(ChatDocs.template(it, now)) }
                missing(DocKind.TEMPLATE).forEach { ops += Op.Del(SyncDelete(DocKind.TEMPLATE, it.substringAfter('/'))) }
            }
            if (wasFull) {
                source.workouts().forEach { consider(ChatDocs.workout(it)) }
                missing(DocKind.WORKOUT).forEach { ops += Op.Del(SyncDelete(DocKind.WORKOUT, it.substringAfter('/'))) }
            } else {
                for (id in workoutIds) {
                    val record = source.workout(id)
                    if (record != null) consider(ChatDocs.workout(record))
                    else if ("${DocKind.WORKOUT}/$id" in hashes) ops += Op.Del(SyncDelete(DocKind.WORKOUT, id))
                }
            }

            val packages = ChatDocs.batches(ops)
            val total = packages.sumOf { it.size }
            _state.update { it.copy(done = 0, total = if (reset) total else 0) }
            var done = 0
            var lastSync = _state.value.lastSyncAt
            // A reset needs a first package even if there is nothing to send (it empties the server copy).
            val toSend = if (packages.isEmpty() && reset) listOf(emptyList()) else packages
            toSend.forEachIndexed { index, pack ->
                val result = api.sync(
                    reset = reset && index == 0,
                    upserts = pack.filterIsInstance<Op.Up>().map { it.doc },
                    deletes = pack.filterIsInstance<Op.Del>().map { it.delete },
                )
                if (reset && index == 0) kv.remove(K_RESET)
                pack.forEach { op ->
                    when (op) {
                        is Op.Up -> hashes[op.doc.key] = ChatDocs.fingerprint(op.doc)
                        is Op.Del -> hashes.remove(op.delete.key)
                    }
                }
                saveHashes()
                done += pack.size
                lastSync = result.lastSyncAt.takeIf { it > 0 }?.let { it * 1000 } ?: now
                _state.update { it.copy(done = done) }
            }
            if (reset && toSend.isEmpty()) kv.remove(K_RESET)

            if (_state.value.photos) syncPhotos(api)

            full = false
            kv.remove(K_FULL)
            lastSync?.let { kv.put(K_LAST, it.toString()) }
            _state.update { it.copy(busy = false, done = 0, total = 0, lastSyncAt = lastSync) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Keep everything as it was and compare again next time; nothing is lost while offline.
            full = true
            kv.put(K_FULL, "1")
            val code = (e as? FuelException)?.code
            val problem = when {
                code == "sync_quota_exceeded" || code == "doc_too_large" -> ChatSyncProblem.QUOTA
                code == "network" || ((e as? FuelException)?.status ?: 0) >= 500 -> ChatSyncProblem.OFFLINE
                else -> ChatSyncProblem.FAILED
            }
            if (code == "chat_not_enabled") { serverSaysOff(); return@withLock }
            _state.update { it.copy(busy = false, done = 0, total = 0, problem = problem) }
        }
    }

    private suspend fun syncPhotos(api: ChatService) {
        val photos = source.photos()
        for (p in photos) {
            if (p.id in uploadedPhotos) continue
            val bytes = source.photoJpeg(p.id) ?: continue
            api.putPhoto(p.id, p.takenAt, p.workoutId, bytes)
            uploadedPhotos += p.id
            savePhotoIds()
        }
        val current = photos.map { it.id }.toSet()
        for (gone in uploadedPhotos - current) {
            api.deletePhoto(gone)
            uploadedPhotos -= gone
            savePhotoIds()
        }
    }

    private sealed interface Op {
        data class Up(val doc: SyncDoc) : Op
        data class Del(val delete: SyncDelete) : Op
    }

    private fun loadHashes(): MutableMap<String, String> =
        kv.get(K_HASHES)?.let { raw ->
            runCatching { json.parseToJsonElement(raw).jsonObject.mapValues { (it.value as? JsonPrimitive)?.contentOrNull.orEmpty() } }.getOrNull()
        }?.toMutableMap() ?: mutableMapOf()

    private fun saveHashes() = kv.put(K_HASHES, JsonObject(hashes.mapValues { JsonPrimitive(it.value) }).toString())
    private fun savePhotoIds() = kv.put(K_PHOTO_IDS, uploadedPhotos.joinToString(","))

    companion object {
        const val K_ENABLED = "overfit.chat.enabled"
        const val K_PHOTOS = "overfit.chat.photos"
        const val K_HASHES = "overfit.chat.hashes"
        const val K_PHOTO_IDS = "overfit.chat.photoIds"
        const val K_FULL = "overfit.chat.full"
        const val K_RESET = "overfit.chat.reset"
        const val K_LAST = "overfit.chat.lastSync"
    }
}
