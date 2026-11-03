package app.overfit.chat

import app.overfit.data.AppData
import app.overfit.data.AppStore
import app.overfit.data.KeyValueStore
import app.overfit.domain.Language
import app.overfit.domain.Profile
import app.overfit.domain.Template
import app.overfit.domain.WeightEntry
import app.overfit.fuel.FuelAccess
import app.overfit.fuel.SignedTransport
import app.overfit.i18n.ChatStrings
import app.overfit.share.AiPrompt
import app.overfit.domain.aiProfileHeader
import app.overfit.domain.withAiProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

/** The app's data for [ChatSync] and [ChatContext]. */
class AppChatSource(
    private val data: AppData,
    private val store: AppStore,
    private val memory: () -> String? = { null },
    private val clock: () -> Long,
) : ChatDataSource {
    override suspend fun templates(): List<Template> = data.templates.all()
    override suspend fun workouts() = data.workouts.allRecords()
    override suspend fun workout(id: String) = data.workouts.record(id)
    override fun profile(now: Long): SyncDoc {
        val s = store.state.value
        return ChatDocs.profile(s.profile, s.weights, s.language, TimeZone.currentSystemDefault().id, now)
    }
    override fun memory(): String? = memory.invoke()
    override suspend fun photos(): List<ChatPhotoRef> =
        data.workouts.allPhotos().map { ChatPhotoRef(it.id, it.takenAt, it.sessionId) }
    override suspend fun photoJpeg(id: String): ByteArray? = data.workouts.photoJpeg(id)
}

/**
 * Everything of the KI-Chat-Verbindung the UI needs, built once per process. Without a backend URL
 * ([configured] = false) the chat ways show "coming soon", but "copy context" still works.
 */
class ChatFeature(
    private val fuel: FuelAccess,
    private val data: AppData,
    private val store: AppStore,
    kv: KeyValueStore,
    /** Lives as long as the app: uploads, saving a proposal and disconnecting must not stop with a screen. */
    val scope: CoroutineScope,
    private val clock: () -> Long,
    private val memory: () -> String? = { null },
) {
    private var cached: Pair<SignedTransport, ChatService>? = null

    /** The chat API of the current backend, or null when no backend URL is configured (or in the Fuel demo). */
    fun service(): ChatService? {
        val transport = fuel.service() as? SignedTransport ?: return null
        cached?.takeIf { it.first === transport }?.let { return it.second }
        return ChatApi(transport).also { cached = transport to it }
    }

    val configured: Boolean get() = service() != null

    /** `<backend>/mcp`, the address the chat platforms need; null while no backend is configured. */
    fun serverUrl(): String? = fuel.url.value.takeIf { it.isNotEmpty() }?.let { "$it/mcp" }

    val notes = ChatNotes(kv)
    private val source = AppChatSource(data, store, memory, clock)
    val sync = ChatSync(::service, kv, source, scope, clock)
    val inbox = ChatInbox(::service, { sync.state.value.enabled }, data.importer, data.templates, notes, kv)

    init {
        data.workouts.onChanged = { id, finished ->
            sync.markWorkout(id)
            if (finished) sync.flush()
        }
        data.templates.onChanged = { sync.markTemplates() }
        scope.launch {
            store.state.map { Triple(it.profile, it.weights, it.language) }.distinctUntilChanged().drop(1).collect { sync.markProfile() }
        }
    }

    /** App start / back in the foreground: send what is left, fetch the inbox. */
    fun onForeground() {
        sync.flush()
        scope.launch { inbox.refresh() }
    }

    /** App goes to the background: a running workout is sent now. */
    fun onBackground() = sync.flush()

    /** "Disconnect AI chat": the server deletes everything, local state goes with it. */
    suspend fun disable() {
        sync.disable()
        inbox.reset()
    }

    /** Text for "Copy context" in the app language. Works offline and without a backend. */
    suspend fun contextText(t: ChatStrings, today: LocalDate): String {
        val s = store.state.value
        val input = ContextInput(
            profile = s.profile,
            weights = s.weights,
            memory = memory(),
            workouts = data.workouts.allRecords(),
            language = s.language,
            today = today,
        )
        return ChatContext.build(input, t, AiPrompt.EXAMPLE)
    }

    /** Gem/GPT instructions with the profile line on top (name, age, weight as of copying). */
    fun gemText(t: ChatStrings, today: LocalDate): String {
        val s = store.state.value
        return withAiProfile(aiProfileHeader(s.profile, s.weights, today, s.language), t.gemInstructions(AiPrompt.EXAMPLE))
    }
}
