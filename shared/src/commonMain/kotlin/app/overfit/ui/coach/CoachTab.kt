package app.overfit.ui.coach

import app.overfit.coach.CoachChatService
import app.overfit.data.db.CoachChatEntity
import app.overfit.data.db.CoachChatMessageEntity
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

// The Coach tab itself (chat) lives in ui/coach/chat (CoachChatTabScreen).

/** The coach chats go into the backup (key "coachChat"). */
class CoachChatBackup(private val chat: CoachChatService) : app.overfit.data.backup.BackupContributor {
    override val key = "coachChat"
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Serializable
    private data class Content(
        val chats: List<CoachChatEntity> = emptyList(),
        val messages: List<CoachChatMessageEntity> = emptyList(),
    )

    override suspend fun export(): JsonElement? {
        val (chats, messages) = chat.exportAll()
        return json.encodeToJsonElement(Content.serializer(), Content(chats, messages))
    }

    override suspend fun import(value: JsonElement?) {
        val content = value?.let { runCatching { json.decodeFromJsonElement(Content.serializer(), it) }.getOrNull() } ?: Content()
        chat.importAll(content.chats, content.messages)
    }
}
