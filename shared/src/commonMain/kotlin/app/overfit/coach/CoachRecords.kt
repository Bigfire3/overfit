package app.overfit.coach

import app.overfit.data.db.CoachReviewEntity
import app.overfit.data.db.SessionWithExercises
import app.overfit.data.toDomain
import app.overfit.domain.SetValues
import app.overfit.domain.WorkoutExercise
import app.overfit.domain.WorkoutSet

/** Database row → [SessionRecord] (all exercises and sets in order). */
fun SessionWithExercises.toRecord(): SessionRecord = SessionRecord(
    id = session.id,
    name = session.name,
    templateId = session.templateId,
    startedAt = session.startedAt,
    endedAt = session.endedAt,
    note = session.note,
    feeling = session.feeling,
    photoPath = session.photoPath,
    exercises = exercises.sortedBy { it.entry.position }.map { e ->
        WorkoutExercise(
            id = e.entry.id,
            exercise = e.exercise.toDomain(),
            restSec = e.entry.restSec,
            note = e.entry.note.orEmpty(),
            origin = e.entry.origin,
            replacedExerciseId = e.entry.replacedExerciseId,
            sets = e.sets.sortedBy { it.position }.map { s ->
                WorkoutSet(
                    id = s.id,
                    target = SetValues(s.targetWeightKg, s.targetReps, s.targetDurationSec),
                    actual = SetValues(s.weightKg, s.reps, s.durationSec),
                    completed = s.completed,
                    completedAt = s.completedAt,
                )
            },
        )
    },
)

/** A coach review as the UI sees it. [review] is null until the answer is there. */
data class ReviewItem(
    val id: String,
    val sessionId: String?,
    val kind: ReviewKind,
    val status: ReviewStatus,
    val createdAt: Long,
    val review: CoachReview?,
    val errorKind: CoachErrorKind?,
    val errorMessage: String?,
    val planStatus: PlanStatus,
    val acceptedChanges: Set<String>,
    val costMicro: Long?,
    val balanceMicro: Long?,
    val providerId: String?,
    val model: String?,
    val rawResponse: String?,
    val isDemo: Boolean = false,
    /** Credit: model ID that answered, and the chosen model if a replacement answered. */
    val servedModel: String? = null,
    val fallbackFrom: String? = null,
)

fun CoachReviewEntity.toItem(): ReviewItem = ReviewItem(
    id = id,
    sessionId = sessionId,
    kind = kind,
    status = status,
    createdAt = createdAt,
    review = reviewJson?.let { (CoachParser.parse(it) as? ParseResult.Ok)?.review },
    errorKind = errorKind?.let { k -> CoachErrorKind.entries.firstOrNull { it.name == k } },
    errorMessage = errorMessage,
    planStatus = planStatus,
    acceptedChanges = acceptedChanges?.split(',')?.filter { it.isNotEmpty() }?.toSet().orEmpty(),
    costMicro = costMicro,
    balanceMicro = balanceMicro,
    providerId = providerId,
    model = model,
    rawResponse = rawResponse,
    servedModel = servedModel,
    fallbackFrom = fallbackFrom,
)
