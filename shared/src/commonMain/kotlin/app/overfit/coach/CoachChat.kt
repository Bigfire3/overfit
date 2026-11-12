package app.overfit.coach

import app.overfit.ai.ProviderId
import app.overfit.data.db.CoachChatDao
import app.overfit.data.db.CoachChatEntity
import app.overfit.data.db.CoachChatMessageEntity
import app.overfit.share.ShareFormat
import app.overfit.share.ShareParse
import app.overfit.share.SharedTraining
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** Status of a chat answer. */
enum class ChatMessageStatus { SENDING, DONE, FAILED }

/** A chat message for the UI; [workout] is the parsed proposal of [workoutJson]. */
data class ChatMessage(
    val id: String,
    val fromUser: Boolean,
    val text: String,
    val status: ChatMessageStatus,
    val createdAt: Long,
    val workoutJson: String?,
    val workout: SharedTraining?,
    val photoIds: List<String>,
    val costMicro: Long?,
    val errorKind: CoachErrorKind?,
    /** Failed answer: the credit server's error code (e.g. `upstream_rejected`) or the [CoachErrorKind] name. */
    val errorCode: String? = null,
    val demo: Boolean,
    /** Credit: model ID that answered, and the chosen model if a replacement answered. */
    val servedModel: String? = null,
    val fallbackFrom: String? = null,
    /** Question: what the user attached (cards above the bubble). */
    val attachments: List<ChatAttachment> = emptyList(),
    /** Answer: provider and model that were asked (own key: shown under the answer instead of a price). */
    val providerId: ProviderId? = null,
    val model: String? = null,
)

/** What one send carries: the text, the attachments (photos included) and whether the coach profile goes along. */
data class ChatSendRequest(
    val text: String,
    val attachments: List<ChatAttachment> = emptyList(),
    val includeProfile: Boolean = true,
)

/** What the system prompt of one question is built from: profile on/off and every attachment of the chat so far. */
data class ChatContextRequest(val includeProfile: Boolean, val attachments: List<ChatAttachment>)

/** Answer of the model, split into the visible text and an optional workout in the share format v1. */
data class ChatAnswer(val text: String, val workoutJson: String?)

/**
 * Output contract of the coach chat: `{"reply": "...", "workout": ""}`. The workout is a string (empty = none) that
 * holds share format v1 JSON; a string field works with strict structured outputs of every provider, a nested
 * nullable object does not.
 */
object CoachChatSchema {
    const val NAME = "coach_chat"
    const val MAX_TOKENS = 4_000

    val schema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("reply") { put("type", "string") }
            putJsonObject("workout") { put("type", "string") }
        }
        putJsonArray("required") { add(JsonPrimitive("reply")); add(JsonPrimitive("workout")) }
        put("additionalProperties", false)
    }

    const val RULES = """You are chatting with the user inside the app Overfit (coach tab). Their current training status follows in the system prompt.
How to answer:
- Answer like a coach in a chat: short, concrete, friendly. Plain text with simple Markdown (**bold**, lists) only.
- Use the user's data (workouts, progress, memory) instead of asking for it again. Do not invent numbers you do not have.
- When the user asks for a workout or a plan, or when a new workout clearly helps, propose ONE workout. Put it into the field "workout" as a JSON string in the Overfit share format v1 (see the example). Use exercise IDs from the list below in "id" whenever one fits, otherwise only "name". Weights in kg, durations in seconds.
- When a workout or a running workout is attached and the user wants it changed, put the COMPLETE changed workout into "workout" (all exercises in order, unchanged ones too, same name), never only the changed part. The app shows the difference and applies it to exactly that workout. Sets already done in a running workout stay as they are.
- In "reply" describe the workout in one or two sentences; do not repeat the JSON there. Without a proposal "workout" is an empty string.
Answer with exactly one JSON object: {"reply": "...", "workout": ""}."""
}

/** Reads the model's answer: the JSON contract, or (fallback without structured output) text with a fenced block. */
object CoachChatParser {
    private val json = Json { ignoreUnknownKeys = true }
    private val fence = Regex("```(?:overfit|json)?\\s*([\\s\\S]*?)```")

    fun parse(raw: String): ChatAnswer {
        val text = raw.trim()
        val obj = CoachParser.extractJsonObject(text)?.let { runCatching { json.parseToJsonElement(it) as? JsonObject }.getOrNull() }
        val reply = (obj?.get("reply") as? JsonPrimitive)?.contentOrNull
        if (obj != null && reply != null) {
            val workout = when (val w = obj["workout"]) {
                is JsonPrimitive -> w.contentOrNull
                is JsonObject -> w.toString()
                else -> null
            }
            return ChatAnswer(reply.trim(), workout?.let(::normalizedWorkout))
        }
        // Plain text: a fenced block with a share JSON is the proposal, the rest is the text.
        val block = fence.findAll(text).firstNotNullOfOrNull { m -> normalizedWorkout(m.groupValues[1])?.let { m to it } }
        if (block != null) {
            val visible = text.removeRange(block.first.range).trim()
            return ChatAnswer(visible, block.second)
        }
        return ChatAnswer(text, null)
    }

    /** Share JSON in a canonical form, or null when it is no valid workout. */
    fun normalizedWorkout(text: String): String? {
        if (text.isBlank()) return null
        val parsed = ShareFormat.fromJson(text.trim()) as? ShareParse.Ok ?: return null
        if (parsed.training.exercises.isEmpty()) return null
        return ShareFormat.toJson(parsed.training)
    }

    fun workout(json: String?): SharedTraining? = json?.let { (ShareFormat.fromJson(it) as? ShareParse.Ok)?.training }
}

/** What the system prompt is built from; collected fresh for every question. */
data class ChatPromptParts(
    /** Coach role and rules (same as the coach's instructions). */
    val instructions: String,
    /** Profile block: coach profile, facts and memory when the profile goes along; reply language, units, today always. */
    val profileBlock: String,
    /** Text of the attachments (training, workout, history, exercise); empty when nothing is attached. */
    val context: String,
    /** id → name of the exercises the chat may use. */
    val exerciseList: List<Pair<String, String>>,
)

object CoachChatPrompt {
    /** Earlier messages sent along (the newest ones). */
    const val MAX_HISTORY = 20

    fun build(parts: ChatPromptParts, history: List<PromptTurn>, question: String, images: List<CoachImage> = emptyList()): CoachPrompt {
        val static = buildString {
            appendLine(parts.instructions.trim())
            appendLine()
            appendLine(CoachChatSchema.RULES)
            appendLine()
            appendLine(WeightRules.TEXT)
            appendLine()
            appendLine("Example of a workout in the share format v1 (as the string value of \"workout\"):")
            appendLine(app.overfit.share.AiPrompt.EXAMPLE)
            appendLine()
            appendLine("Exercise IDs you may use (id | name):")
            parts.exerciseList.forEach { (id, name) -> appendLine("$id | $name") }
        }.trimEnd()
        val dynamic = buildString {
            appendLine(parts.profileBlock.trim())
            if (parts.context.isNotBlank()) {
                appendLine()
                append(parts.context.trim())
            } else {
                appendLine()
                append("Nothing else is attached to this chat. If the question needs training data, ask the user to attach it with \"+\".")
            }
        }
        return CoachPrompt(
            staticSystem = static,
            dynamicSystem = dynamic,
            user = question,
            images = images,
            schema = CoachChatSchema.schema,
            schemaName = CoachChatSchema.NAME,
            maxTokens = CoachChatSchema.MAX_TOKENS,
            history = history.takeLast(MAX_HISTORY),
        )
    }

    /** History turns from stored messages: only finished, real exchanges (no demo, no failures). */
    fun history(messages: List<CoachChatMessageEntity>): List<PromptTurn> {
        val done = messages.filter { it.fromUser || (it.status == ChatMessageStatus.DONE.name && !it.demo) }
        // A question without an answer (failed) is dropped, so turns keep alternating.
        val turns = mutableListOf<PromptTurn>()
        done.forEachIndexed { i, m ->
            if (m.fromUser) {
                val next = done.getOrNull(i + 1)
                if (next != null && !next.fromUser) turns += PromptTurn(true, m.text)
            } else {
                val text = m.workoutJson?.let { "${m.text}\n\n```overfit\n$it\n```" } ?: m.text
                turns += PromptTurn(false, text)
            }
        }
        return turns
    }
}

/**
 * The coach chat: questions and answers stored in `coach_chat_message` (only on this device), sent with the user's own
 * key (straight to the provider) or credit (through the backend) through the same transports as the coach. Every send
 * runs in [scope] (the app scope), so leaving the chat never cancels an answer. Without AI access the demo answers with
 * a fixed text (and a sample workout).
 *
 * Context: the coach profile goes along unless the user removed the profile chip for this chat; attachments (training,
 * workout, history, exercise) of every question of the chat so far go into the system prompt, photos only with the
 * question they were attached to.
 */
class CoachChatService(
    private val dao: CoachChatDao,
    private val access: () -> CoachAccess,
    private val transport: (ProviderId) -> CoachTransport,
    private val model: (ProviderId) -> String,
    private val parts: suspend (ChatContextRequest) -> ChatPromptParts,
    /** Explicitly attached photos (ids) → downscaled JPEGs with their date. */
    private val photos: suspend (List<String>) -> List<CoachImage>,
    /** Demo answer in the app language. */
    private val demo: () -> ChatAnswer,
    private val scope: CoroutineScope,
    private val clock: () -> Long,
) {
    val chats: Flow<List<CoachChatEntity>> = dao.observeChats()

    fun messages(chatId: String): Flow<List<ChatMessage>> = dao.observeMessages(chatId).map { list -> list.map { it.toMessage() } }

    fun chat(chatId: String): Flow<CoachChatEntity?> = dao.observeChat(chatId)

    suspend fun latestChatId(): String? = dao.latestChat()?.id

    fun available(): Boolean = access() is CoachAccess.Ready

    /** Answers that were on their way when the app was killed. */
    fun recover(): Job = scope.launch { runCatching { dao.failInterrupted() } }

    /**
     * Sends [request] into [chatId], or into a new chat when null. Returns the chat id right away; question and answer
     * appear through [messages].
     */
    fun send(chatId: String?, request: ChatSendRequest): Pair<String, Job> {
        val id = chatId ?: newId()
        val job = scope.launch {
            try { sendNow(id, request) } catch (e: CancellationException) { throw e } catch (_: Exception) { }
        }
        return id to job
    }

    /** Old call shape: text and photos only (profile on). */
    fun send(chatId: String?, text: String, photoIds: List<String> = emptyList()): Pair<String, Job> =
        send(chatId, ChatSendRequest(text, photoIds.map { ChatAttachment.Photo(it) }))

    /** Same as [send] but suspends until the answer is stored (tests). */
    suspend fun sendNow(chatId: String, request: ChatSendRequest) {
        val question = request.text.trim()
        if (question.isEmpty()) return
        val earlier = dao.messages(chatId)
        // Strictly increasing times keep question → answer order even with a coarse clock.
        val now = maxOf(clock(), (earlier.maxOfOrNull { it.createdAt } ?: 0L) + 1)
        val chat = dao.chat(chatId)
        val profileOff = !request.includeProfile
        dao.upsertChat(
            chat?.copy(updatedAt = now, profileOff = profileOff)
                ?: CoachChatEntity(chatId, question.lineSequence().first().take(TITLE_LENGTH), now, now, profileOff = profileOff),
        )
        val history = CoachChatPrompt.history(earlier)
        val attachments = request.attachments.distinctBy { it.key() }
        val photoIds = attachments.filterIsInstance<ChatAttachment.Photo>().map { it.photoId }
        val questionRow = CoachChatMessageEntity(
            id = newId(), chatId = chatId, fromUser = true, text = question, status = ChatMessageStatus.DONE.name,
            createdAt = now, photoIds = photoIds.takeIf { it.isNotEmpty() }?.joinToString(","),
            attachments = ChatAttachment.encode(attachments),
        )
        dao.upsertMessage(questionRow)
        val answer = CoachChatMessageEntity(
            id = newId(), chatId = chatId, fromUser = false, text = "", status = ChatMessageStatus.SENDING.name, createdAt = now + 1,
        )
        dao.upsertMessage(answer)
        answer(answer, history, question, contextOf(earlier + questionRow, request.includeProfile), photoIds)
    }

    /** Old call shape (tests). */
    suspend fun sendNow(chatId: String, text: String, photoIds: List<String> = emptyList()) =
        sendNow(chatId, ChatSendRequest(text, photoIds.map { ChatAttachment.Photo(it) }))

    /**
     * Input tokens the next question of [chatId] (null = new chat) would send with [request]: built with the same
     * system prompt, attachments of the chat so far, history and photos as [sendNow], without sending anything. The
     * question text of [request] counts too; the UI adds what is typed later through [TokenEstimate.plusText].
     */
    suspend fun estimate(chatId: String?, request: ChatSendRequest): TokenEstimate {
        val earlier = chatId?.takeIf { it.isNotEmpty() }?.let { dao.messages(it) }.orEmpty()
        val attachments = request.attachments.distinctBy { it.key() }
        val photos = attachments.count { it is ChatAttachment.Photo }
        val virtual = CoachChatMessageEntity(
            id = "estimate", chatId = chatId.orEmpty(), fromUser = true, text = request.text, status = ChatMessageStatus.DONE.name,
            createdAt = 0, attachments = ChatAttachment.encode(attachments),
        )
        val prompt = CoachChatPrompt.build(parts(contextOf(earlier + virtual, request.includeProfile)), CoachChatPrompt.history(earlier), request.text)
        return ChatTokens.estimate(prompt, photos)
    }

    /** Profile chip of an existing chat. */
    fun setProfileIncluded(chatId: String, included: Boolean): Job = scope.launch {
        dao.chat(chatId)?.let { dao.upsertChat(it.copy(profileOff = !included)) }
    }

    /** "Try again" on a failed answer: asks the question before it again. */
    fun retry(answerId: String): Job = scope.launch {
        val answer = dao.message(answerId) ?: return@launch
        val all = dao.messages(answer.chatId)
        val index = all.indexOfFirst { it.id == answerId }
        val question = all.take(index).lastOrNull { it.fromUser } ?: return@launch
        val before = all.take(all.indexOf(question))
        val history = CoachChatPrompt.history(before)
        val sending = answer.copy(status = ChatMessageStatus.SENDING.name, errorKind = null, text = "")
        dao.upsertMessage(sending)
        val profile = dao.chat(answer.chatId)?.profileOff != true
        try {
            answer(sending, history, question.text, contextOf(before + question, profile), question.photoIds?.split(',').orEmpty().filter { it.isNotEmpty() })
        } catch (e: CancellationException) { throw e } catch (_: Exception) { }
    }

    fun delete(chatId: String): Job = scope.launch { dao.deleteChat(chatId) }

    /** Profile flag and every non-photo attachment of the questions so far (oldest first, without duplicates). */
    private fun contextOf(questions: List<CoachChatMessageEntity>, includeProfile: Boolean): ChatContextRequest {
        val all = questions.filter { it.fromUser }.flatMap { ChatAttachment.decode(it.attachments) }
            .filter { it !is ChatAttachment.Photo }
            .distinctBy { it.key() }
        return ChatContextRequest(includeProfile, all)
    }

    private suspend fun answer(row: CoachChatMessageEntity, history: List<PromptTurn>, question: String, context: ChatContextRequest, photoIds: List<String>) {
        val ready = access() as? CoachAccess.Ready
        if (ready == null) {
            val d = demo()
            dao.upsertMessage(row.copy(text = d.text, workoutJson = d.workoutJson, status = ChatMessageStatus.DONE.name, demo = true))
            return
        }
        val modelId = model(ready.providerId)
        val asked = row.copy(providerId = ready.providerId.name, model = modelId)
        dao.upsertMessage(asked)
        try {
            val images = if (photoIds.isEmpty()) emptyList() else photos(photoIds)
            val prompt = CoachChatPrompt.build(parts(context), history, question, images)
            val reply = exchange(transport(ready.providerId), prompt, modelId)
            val parsed = CoachChatParser.parse(reply.text)
            dao.upsertMessage(
                asked.copy(
                    text = parsed.text, workoutJson = parsed.workoutJson, status = ChatMessageStatus.DONE.name,
                    costMicro = reply.costMicro, servedModel = reply.servedModel, fallbackFrom = reply.fallbackFrom, errorKind = null,
                ),
            )
        } catch (e: CancellationException) {
            // The row stays SENDING; [recover] marks it failed on the next start.
            throw e
        } catch (e: CoachException) {
            dao.upsertMessage(asked.copy(status = ChatMessageStatus.FAILED.name, errorKind = e.serverCode ?: e.kind.name))
        } catch (e: Exception) {
            dao.upsertMessage(asked.copy(status = ChatMessageStatus.FAILED.name, errorKind = CoachErrorKind.SERVER.name))
        }
    }

    /** One request; a 400 with structured output is repeated once without it (some OpenRouter models). */
    private suspend fun exchange(transport: CoachTransport, prompt: CoachPrompt, model: String): CoachReply {
        val reply = try {
            transport.send(prompt, model, structured = true)
        } catch (e: CoachException) {
            if (!e.retryWithoutStructured) throw e
            transport.send(prompt, model, structured = false)
        }
        if (reply.refused || reply.text.isBlank()) throw CoachException(CoachErrorKind.INVALID_ANSWER)
        return reply
    }

    // --- Backup ------------------------------------------------------------------------------------

    suspend fun exportAll(): Pair<List<CoachChatEntity>, List<CoachChatMessageEntity>> = dao.allChats() to dao.allMessages()

    suspend fun importAll(chats: List<CoachChatEntity>, messages: List<CoachChatMessageEntity>) {
        dao.deleteAllChats()
        chats.forEach { dao.upsertChat(it) }
        val ids = chats.map { it.id }.toSet()
        messages.filter { it.chatId in ids }.forEach { dao.upsertMessage(it) }
    }

    suspend fun resetAll() = dao.deleteAllChats()

    companion object {
        const val TITLE_LENGTH = 60

        @OptIn(ExperimentalUuidApi::class)
        fun newId() = Uuid.random().toString()
    }
}

private fun CoachChatMessageEntity.toMessage() = ChatMessage(
    id = id,
    fromUser = fromUser,
    text = text,
    status = runCatching { ChatMessageStatus.valueOf(status) }.getOrDefault(ChatMessageStatus.FAILED),
    createdAt = createdAt,
    workoutJson = workoutJson,
    workout = CoachChatParser.workout(workoutJson),
    photoIds = photoIds?.split(',')?.filter { it.isNotEmpty() }.orEmpty(),
    costMicro = costMicro,
    errorKind = errorKind?.let { k -> CoachErrorKind.entries.firstOrNull { it.name == k } },
    errorCode = errorKind,
    demo = demo,
    servedModel = servedModel,
    fallbackFrom = fallbackFrom,
    attachments = ChatAttachment.decode(attachments),
    providerId = providerId?.let { p -> ProviderId.entries.firstOrNull { it.name == p } },
    model = model,
)

/** Identity of an attachment without its label (the same training attached twice counts once). */
internal fun ChatAttachment.key(): String = when (this) {
    is ChatAttachment.Training -> "t:$sessionId"
    is ChatAttachment.Plan -> "p:$templateId"
    is ChatAttachment.History -> "h:$weeks"
    is ChatAttachment.Exercise -> "e:$exerciseId"
    is ChatAttachment.Photo -> "f:$photoId"
    is ChatAttachment.Running -> "r:$sessionId"
    is ChatAttachment.Day -> "d:$date"
}
