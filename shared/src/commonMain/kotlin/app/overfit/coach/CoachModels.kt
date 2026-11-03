package app.overfit.coach

import app.overfit.domain.WorkoutExercise

/** What the coach evaluates: one finished workout or the progress over the last weeks. */
@kotlinx.serialization.Serializable
enum class ReviewKind { SESSION, PROGRESS }

/**
 * Life cycle of a coach request. QUEUED requests are (re)sent automatically on app start and when the app comes
 * to the foreground; FAILED ones only by "Try again" (e.g. invalid key, no Fuel left, invalid answer twice).
 */
@kotlinx.serialization.Serializable
enum class ReviewStatus { QUEUED, SENDING, DONE, FAILED }

/** What the user did with the plan suggestion. */
@kotlinx.serialization.Serializable
enum class PlanStatus { NONE, OPEN, ACCEPTED, PARTIAL, REJECTED }

/** A finished workout as the coach sees it (read straight from the database). */
data class SessionRecord(
    val id: String,
    val name: String,
    val templateId: Long?,
    val startedAt: Long,
    val endedAt: Long?,
    val note: String?,
    /** 1–5 or null. */
    val feeling: Int?,
    val photoPath: String?,
    val exercises: List<WorkoutExercise>,
    /** Sauna right after the workout. */
    val sauna: Boolean = false,
) {
    val durationMin: Int? get() = endedAt?.let { ((it - startedAt) / 60_000).toInt() }
}

/** The structured answer of the coach (schema in [CoachSchema]). Weights always in kg. */
data class CoachReview(
    /** 1–10. */
    val rating: Int?,
    /** Short overall verdict; the details are in [exerciseReviews]. */
    val summary: String,
    val positives: List<String>,
    val improvements: List<String>,
    val tips: List<String>,
    val photoFeedback: String?,
    val nextSession: PlanSuggestion?,
    /** Complete new version of the coach memory; null/blank = unchanged. */
    val memory: String?,
    /** Verdict, comment and target for next time per exercise (SESSION; empty in older reviews). */
    val exerciseReviews: List<ExerciseFeedback> = emptyList(),
)

/** Suggested plan for the next session of the template: the complete exercise list in order. */
data class PlanSuggestion(val exercises: List<SuggestedExercise>, val note: String? = null)

data class SuggestedExercise(
    val exerciseId: String,
    /** True for an exercise that is not in the template yet (shown as marked suggestion). */
    val isNew: Boolean,
    val restSec: Int?,
    val sets: List<SuggestedSet>,
    val reason: String?,
)

data class SuggestedSet(val weightKg: Double?, val reps: Int?, val durationSec: Int?)
