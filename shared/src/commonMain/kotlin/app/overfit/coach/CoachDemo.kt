package app.overfit.coach

import app.overfit.domain.Template
import app.overfit.i18n.CoachStrings

/**
 * Sample review for users without AI access, built from their own workout so the flow is visible. Clearly marked
 * as demo in the UI; nothing is stored and the plan is never applied.
 */
object CoachDemo {
    fun review(workoutName: String, template: Template?, t: CoachStrings): CoachReview {
        var bumped = false
        val plan = template?.let { tpl ->
            PlanSuggestion(
                tpl.exercises.map { te ->
                    val bump = !bumped && te.exercise.trackingType.usesWeight && te.sets.any { (it.weightKg ?: 0.0) > 0 }
                    if (bump) bumped = true
                    SuggestedExercise(
                        exerciseId = te.exercise.id,
                        isNew = false,
                        restSec = te.restSec,
                        sets = te.sets.map { s ->
                            SuggestedSet(
                                weightKg = s.weightKg?.let { if (bump && it > 0) it + 2.5 else it },
                                reps = s.reps,
                                durationSec = s.durationSec,
                            )
                        },
                        reason = if (bump) t.demoReason else null,
                    )
                },
            )
        }
        return CoachReview(
            rating = 8,
            summary = t.demoSummary(workoutName),
            positives = t.demoPositives,
            improvements = t.demoImprovements,
            tips = t.demoTips,
            photoFeedback = null,
            nextSession = plan,
            memory = null,
        )
    }
}
