package app.overfit.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

// --- Coach chat (schema v6) ----------------------------------------------------------------------

/** One conversation with the coach in the Coach tab. [title] is the start of the first question. */
@kotlinx.serialization.Serializable
@Entity(tableName = "coach_chat", indices = [Index("updatedAt")])
data class CoachChatEntity(
    @PrimaryKey val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
)

/**
 * One message of a coach chat. A question is stored at once ([fromUser]); the answer row exists from the moment the
 * question is sent ([status] SENDING) so the chat shows a loading line, and turns DONE or FAILED. [workoutJson] is a
 * proposed workout in the share format v1 (shown as "Use workout" card); [photoIds] the photos the user explicitly
 * attached (comma-separated). [costMicro]: what the answer cost with credit.
 */
@kotlinx.serialization.Serializable
@Entity(
    tableName = "coach_chat_message",
    foreignKeys = [ForeignKey(entity = CoachChatEntity::class, parentColumns = ["id"], childColumns = ["chatId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("chatId"), Index("createdAt")],
)
data class CoachChatMessageEntity(
    @PrimaryKey val id: String,
    val chatId: String,
    val fromUser: Boolean,
    val text: String,
    val status: String,
    val createdAt: Long,
    val workoutJson: String? = null,
    val photoIds: String? = null,
    val costMicro: Long? = null,
    /** Credit: model ID that answered, and the chosen model if a replacement answered (the chosen model's price was charged). */
    val servedModel: String? = null,
    val fallbackFrom: String? = null,
    val errorKind: String? = null,
    /** Answer of the demo mode (no AI access): shown with a hint, never sent to a provider as history. */
    val demo: Boolean = false,
)

@Dao
interface CoachChatDao {
    @Query("SELECT * FROM coach_chat ORDER BY updatedAt DESC")
    fun observeChats(): Flow<List<CoachChatEntity>>

    @Query("SELECT * FROM coach_chat ORDER BY updatedAt DESC LIMIT 1")
    suspend fun latestChat(): CoachChatEntity?

    @Query("SELECT * FROM coach_chat WHERE id = :id")
    suspend fun chat(id: String): CoachChatEntity?

    @Upsert
    suspend fun upsertChat(chat: CoachChatEntity)

    @Query("DELETE FROM coach_chat WHERE id = :id")
    suspend fun deleteChat(id: String)

    @Query("DELETE FROM coach_chat")
    suspend fun deleteAllChats()

    @Query("SELECT * FROM coach_chat_message WHERE chatId = :chatId ORDER BY createdAt, fromUser DESC")
    fun observeMessages(chatId: String): Flow<List<CoachChatMessageEntity>>

    @Query("SELECT * FROM coach_chat_message WHERE chatId = :chatId ORDER BY createdAt, fromUser DESC")
    suspend fun messages(chatId: String): List<CoachChatMessageEntity>

    @Query("SELECT * FROM coach_chat_message WHERE id = :id")
    suspend fun message(id: String): CoachChatMessageEntity?

    @Upsert
    suspend fun upsertMessage(message: CoachChatMessageEntity)

    @Query("SELECT * FROM coach_chat ORDER BY createdAt")
    suspend fun allChats(): List<CoachChatEntity>

    @Query("SELECT * FROM coach_chat_message ORDER BY createdAt")
    suspend fun allMessages(): List<CoachChatMessageEntity>

    /** Answers left SENDING when the app was killed: they fail and offer "Try again". */
    @Query("UPDATE coach_chat_message SET status = 'FAILED', errorKind = 'NETWORK' WHERE status = 'SENDING'")
    suspend fun failInterrupted()
}
