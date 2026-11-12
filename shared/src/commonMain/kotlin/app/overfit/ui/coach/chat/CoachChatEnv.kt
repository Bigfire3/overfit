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
    /** How the next question is priced (credit, own key, demo); follows access and model live. */
    val pricing: StateFlow<app.overfit.coach.ChatPricing>,
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
    /** Clears [request] once the Coach tab has taken it; a newer request that arrived meanwhile stays pending. */
    fun consumed(request: ChatOpenRequest) { pending.compareAndSet(request, null) }
}

/**
 * The coach opened from a screen (coach icon top right): the same chat as the Coach tab, as a layer over that screen,
 * with what the screen shows attached as chips ([attachments], removable) and, where it makes sense, the [target] a
 * proposed workout applies to.
 */
data class CoachOverlayRequest(val attachments: List<ChatAttachment>, val target: ApplyTarget? = null)

/** What "Apply" on a proposal changes in an opened coach. */
sealed interface ApplyTarget {
    /** A saved workout: the proposal opens in its editor (save overwrites it). */
    data class Template(val templateId: Long, val name: String) : ApplyTarget

    /** The workout that runs right now: applied directly, done sets stay, "Undo" restores it. */
    data class Running(val sessionId: String, val name: String) : ApplyTarget
}

/** Result of "Apply" that stays in the chat: [undo] restores the old state, [note] says what was left out. */
class AppliedChange(val undo: () -> Unit, val note: String? = null)

/**
 * How proposal cards of an opened coach show and apply a change: [title] ("Änderung für „Push“"), [diff] (lines what
 * changes, from the proposal's share JSON) and [apply] (null result = the app navigated away, e.g. into the editor).
 */
class ProposalHandler(
    val title: String,
    val diff: suspend (workoutJson: String) -> List<String>,
    val apply: suspend (workoutJson: String) -> AppliedChange?,
)

/** App-wide holder of the opened coach (null = closed). */
class CoachOverlay {
    val request = MutableStateFlow<CoachOverlayRequest?>(null)
    fun open(r: CoachOverlayRequest) { request.value = r }
    fun close() { request.value = null }
}
