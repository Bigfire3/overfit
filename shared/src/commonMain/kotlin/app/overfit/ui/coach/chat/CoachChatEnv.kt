package app.overfit.ui.coach.chat

import app.overfit.coach.ChatAttachment
import app.overfit.coach.CoachChatPrefsStore
import app.overfit.coach.CoachChatService
import app.overfit.data.DiaryPhoto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Everything the Coach tab chat needs from the app; wired in App.kt ([createCoachChatEnv]). */
class CoachChatEnv(
    val chat: CoachChatService,
    val prefs: CoachChatPrefsStore,
    /** The newest finished training, label ready ("Push · heute"); null = none yet. */
    val lastTraining: Flow<ChatAttachment.Training?>,
    /** Finished trainings, newest first, label ready ("Push · 05.11."). */
    val trainings: Flow<List<ChatAttachment.Training>>,
    val plans: Flow<List<ChatAttachment.Plan>>,
    /** Trained exercises with a sub line (best value). */
    val exercises: suspend () -> List<Pair<ChatAttachment.Exercise, String>>,
    val photos: Flow<List<DiaryPhoto>>,
    /** Names of the saved workouts: a proposal with one of these names is a change of that workout. */
    val templateNames: Flow<Set<String>>,
    /** Body weight from the calendar for the profile popup ("82,4 kg · Stand 05.11."), null = none. */
    val latestWeight: () -> String?,
    /** Text of the model chip ("Claude Sonnet 5.5", "Kein KI-Zugang"). */
    val modelLabel: StateFlow<String>,
    /** Estimated cost of the next question ("≈ 2 ct"), null = nothing shown. */
    val costEstimate: StateFlow<String?>,
)

/**
 * What the Coach tab should open next: [chatId] empty = new chat, [draft] goes into the input, [attachments] become
 * chips, [send] sends right away (automatic review after a training).
 */
data class ChatOpenRequest(
    val chatId: String = "",
    val draft: String = "",
    val attachments: List<ChatAttachment> = emptyList(),
    val send: Boolean = false,
)

/** App-wide holder of the next [ChatOpenRequest] (set from anywhere, consumed by the Coach tab). */
class CoachChatRequests {
    val pending = MutableStateFlow<ChatOpenRequest?>(null)

    fun open(request: ChatOpenRequest) { pending.value = request }
    fun consumed() { pending.value = null }
}
