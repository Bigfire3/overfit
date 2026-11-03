package app.overfit.chat

import app.overfit.data.KeyValueStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** A note the chat left for the user (Markdown), optionally attached to a workout. */
@Serializable
data class ChatNote(
    val id: String,
    val title: String? = null,
    val text: String,
    val workoutId: String? = null,
    val rating: Int? = null,
    val clientName: String = "",
    val createdAt: Long = 0,
)

/**
 * Accepted notes from the chat. Kept in the key-value store (small, no database change): the list "Notes from the chat"
 * and, for notes with a [ChatNote.workoutId], the note on that workout in the history.
 */
class ChatNotes(private val kv: KeyValueStore) {
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(ChatNote.serializer())
    private val _notes = MutableStateFlow(load())
    val notes: StateFlow<List<ChatNote>> = _notes.asStateFlow()

    private fun load(): List<ChatNote> =
        kv.get(KEY)?.let { runCatching { json.decodeFromString(serializer, it) }.getOrNull() }.orEmpty()

    fun add(note: ChatNote) = save((_notes.value.filterNot { it.id == note.id } + note).sortedByDescending { it.createdAt })
    fun remove(id: String) = save(_notes.value.filterNot { it.id == id })
    fun forWorkout(workoutId: String): List<ChatNote> = _notes.value.filter { it.workoutId == workoutId }
    fun clear() = save(emptyList())

    private fun save(list: List<ChatNote>) {
        _notes.value = list
        kv.put(KEY, json.encodeToString(serializer, list))
    }

    companion object { const val KEY = "overfit.chat.notes" }
}

fun InboxNote.toChatNote() = ChatNote(id, title, text, workoutId, rating, clientName, createdAt)
