package app.overfit.chat

import app.overfit.data.KeyValueStore
import app.overfit.data.TemplateRepository
import app.overfit.domain.Template
import app.overfit.share.ImportPreview
import app.overfit.share.ShareFormat
import app.overfit.share.ShareParse
import app.overfit.share.TemplateImporter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class ChatInboxState(
    val items: List<InboxItem> = emptyList(),
    val loading: Boolean = false,
    /** The server holds the maximum of open entries: the chat cannot leave new ones until some are handled. */
    val full: Boolean = false,
    val failed: Boolean = false,
)

/** What a template proposal looks like to the user: the import preview and, when it replaces one, the existing template. */
data class TemplateProposal(val preview: ImportPreview, val replaces: Template?)

/**
 * The inbox the chat fills (templates, notes). Fetched when the app opens and returns to the foreground; every handled
 * entry (accepted or discarded) is acknowledged. An acknowledgement that cannot be sent is kept and retried, so nothing
 * comes back twice.
 */
class ChatInbox(
    private val service: () -> ChatService?,
    private val enabled: () -> Boolean,
    private val importer: TemplateImporter,
    private val templates: TemplateRepository,
    private val notes: ChatNotes,
    private val kv: KeyValueStore,
) {
    private val lock = Mutex()
    private val _state = MutableStateFlow(ChatInboxState())
    val state: StateFlow<ChatInboxState> = _state.asStateFlow()

    private var pendingAcks: MutableSet<String> =
        kv.get(K_ACKS)?.split(',')?.filter { it.isNotEmpty() }?.toMutableSet() ?: mutableSetOf()

    /** Loads the inbox (no-op while the connection is off). Errors are swallowed: it is a background refresh. */
    suspend fun refresh() = lock.withLock {
        val api = service()
        if (api == null || !enabled()) { _state.value = ChatInboxState(); return@withLock }
        _state.update { it.copy(loading = true, failed = false) }
        try {
            sendAcks(api)
            val page = api.inbox()
            if (page.unknownIds.isNotEmpty()) addAcks(page.unknownIds).also { sendAcks(api) }
            val items = page.items.filter { it.id !in pendingAcks }
            _state.value = ChatInboxState(items = items, full = page.items.size + page.unknownIds.size >= FULL)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            _state.update { it.copy(loading = false, failed = true) }
        }
    }

    /** Parses a template entry; null if its content is not a valid template. */
    suspend fun proposal(item: InboxTemplate): TemplateProposal? {
        val training = (ShareFormat.fromJson(item.templateJson) as? ShareParse.Ok)?.training ?: return null
        val preview = importer.preview(training)
        if (preview.exercises.isEmpty()) return null
        val replaces = item.replacesTemplateId?.toLongOrNull()?.let { templates.get(it) }
        return TemplateProposal(preview, replaces)
    }

    /** Saves a proposal as a new template or, with [replace], over the template it was made for. Returns the template ID. */
    suspend fun acceptTemplate(item: InboxTemplate, proposal: TemplateProposal, replace: Boolean): Long {
        val id = importer.accept(proposal.preview, replaceId = if (replace) proposal.replaces?.id else null)
        done(item.id)
        return id
    }

    suspend fun acceptNote(item: InboxNote) {
        notes.add(item.toChatNote())
        done(item.id)
    }

    suspend fun discard(item: InboxItem) = done(item.id)

    private suspend fun done(id: String) {
        _state.update { s -> s.copy(items = s.items.filterNot { it.id == id }, full = false) }
        addAcks(listOf(id))
        val api = service() ?: return
        lock.withLock { runCatching { sendAcks(api) } }
    }

    private fun addAcks(ids: List<String>) { pendingAcks += ids; saveAcks() }

    private suspend fun sendAcks(api: ChatService) {
        if (pendingAcks.isEmpty()) return
        api.ack(pendingAcks.toList())
        pendingAcks.clear()
        saveAcks()
    }

    private fun saveAcks() = kv.put(K_ACKS, pendingAcks.joinToString(","))

    fun reset() { pendingAcks.clear(); kv.remove(K_ACKS); _state.value = ChatInboxState() }

    companion object {
        const val K_ACKS = "overfit.chat.pendingAcks"
        const val FULL = 50
    }
}
