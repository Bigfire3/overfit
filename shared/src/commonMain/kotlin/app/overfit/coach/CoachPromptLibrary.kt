package app.overfit.coach

import app.overfit.i18n.CoachHubStrings

/**
 * A ready-made prompt of the Coach tab. [text] is exactly what "Copy" puts on the clipboard (shown in full on
 * screen); [chatAsk] is what "Use in chat" puts into the chat input (the in-app chat has the context already), null
 * when the prompt makes no sense inside the app (context, Gem instructions).
 */
data class PromptEntry(
    val id: String,
    val title: String,
    val hint: String,
    val text: String,
    val chatAsk: String?,
)

/** The texts every prompt is built from, in the app language. */
data class PromptSources(
    /** "Copy context": status plus how to answer with workouts. */
    val contextWithTask: String,
    /** The same status without the answer instructions, for prompts with their own question. */
    val contextOnly: String,
    /** Gem/GPT instructions with the profile line. */
    val gem: String,
    /** "Create a workout with AI" (the AI template prompt with the profile line). */
    val createWorkout: String,
)

object CoachPromptLibrary {
    const val CREATE_WORKOUT = "create_workout"
    const val CONTEXT = "context"
    const val GEM = "gem"
    const val REVIEW = "review"
    const val PROGRESS = "progress"
    const val WEEK = "week"
    const val PLATEAU = "plateau"

    fun all(h: CoachHubStrings, src: PromptSources): List<PromptEntry> {
        fun withContext(ask: String) = src.contextOnly.trimEnd() + "\n\n" + ask
        return listOf(
            PromptEntry(CREATE_WORKOUT, h.promptCreateWorkout, h.promptCreateWorkoutHint, src.createWorkout, h.promptCreateWorkoutAsk),
            PromptEntry(REVIEW, h.promptReview, h.promptReviewHint, withContext(h.promptReviewAsk), h.promptReviewAsk),
            PromptEntry(PROGRESS, h.promptProgress, h.promptProgressHint, withContext(h.promptProgressAsk), h.promptProgressAsk),
            PromptEntry(WEEK, h.promptWeek, h.promptWeekHint, withContext(h.promptWeekAsk), h.promptWeekAsk),
            PromptEntry(PLATEAU, h.promptPlateau, h.promptPlateauHint, withContext(h.promptPlateauAsk), h.promptPlateauAsk),
            PromptEntry(CONTEXT, h.promptContext, h.promptContextHint, src.contextWithTask, null),
            PromptEntry(GEM, h.promptGem, h.promptGemHint, src.gem, null),
        )
    }
}
