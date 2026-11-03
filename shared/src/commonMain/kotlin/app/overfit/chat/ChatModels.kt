package app.overfit.chat

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** Wire models of the KI-Chat-Verbindung (docs/backend-api.md, "KI-Chat-Verbindung"). */
@Serializable
data class ChatConnection(
    val id: String,
    val clientName: String = "",
    val createdAt: Long = 0,
    val lastUsedAt: Long? = null,
)

@Serializable
data class ChatDocCounts(
    val profile: Int = 0,
    val memory: Int = 0,
    val template: Int = 0,
    val workout: Int = 0,
    val photo: Int = 0,
)

@Serializable
data class ChatStatus(
    val enabled: Boolean = false,
    val photos: Boolean = false,
    val enabledAt: Long? = null,
    val lastSyncAt: Long? = null,
    val docs: ChatDocCounts = ChatDocCounts(),
    val bytes: Long = 0,
    val inboxPending: Int = 0,
    val connections: List<ChatConnection> = emptyList(),
)

@Serializable
data class PairingCode(val code: String, val expiresAt: Long = 0, val ttlSec: Int = 600)

@Serializable
data class SyncResult(val applied: Int = 0, val skipped: Int = 0, val deleted: Int = 0, val lastSyncAt: Long = 0)

/** One document of the sync (kind: profile | memory | template | workout). [updatedAt] in milliseconds. */
data class SyncDoc(val kind: String, val id: String, val updatedAt: Long, val data: JsonObject) {
    val key: String get() = "$kind/$id"
}

data class SyncDelete(val kind: String, val id: String) {
    val key: String get() = "$kind/$id"
}

object DocKind {
    const val PROFILE = "profile"
    const val MEMORY = "memory"
    const val TEMPLATE = "template"
    const val WORKOUT = "workout"
}

/** Entry of the inbox the chat filled (a template proposal or a note). */
sealed interface InboxItem {
    val id: String
    val clientName: String
    val createdAt: Long
}

data class InboxTemplate(
    override val id: String,
    override val clientName: String,
    override val createdAt: Long,
    /** The template in the share format (docs/share-format.md) as JSON text, ready for the import preview. */
    val templateJson: String,
    val replacesTemplateId: String?,
    val message: String?,
) : InboxItem

data class InboxNote(
    override val id: String,
    override val clientName: String,
    override val createdAt: Long,
    val title: String?,
    val text: String,
    val workoutId: String?,
    val rating: Int?,
) : InboxItem

/** Parsed inbox answer; entries of unknown kinds are skipped (and still acknowledged so they do not pile up). */
data class InboxPage(val items: List<InboxItem>, val unknownIds: List<String>)

internal fun JsonElement.asObject(): JsonObject = this as JsonObject
