package app.overfit.coach

import app.overfit.domain.SetValues
import app.overfit.domain.TrackingType
import app.overfit.domain.WorkoutExercise
import app.overfit.domain.WorkoutStats
import kotlin.math.abs

/** Today against the last time: better, same or worse. */
@kotlinx.serialization.Serializable
enum class Verdict { BETTER, SAME, WORSE }

/** Key numbers of one exercise in one session (completed sets only). */
data class ExerciseStats(
    /** Date of the session (YYYY-MM-DD). */
    val date: String,
    val bestSet: SetValues?,
    val setsDone: Int,
    /** kg × reps of sets with positive weight; 0 for body weight or timed exercises. */
    val volumeKg: Double,
    /** Best estimated 1RM (weight exercises with 1–12 reps), else null. */
    val e1rmKg: Double?,
    val maxReps: Int?,
    val maxDurationSec: Int?,
    /** The number the verdict is based on (e1RM, reps or seconds, see [ExerciseReviews.primary]). */
    val score: Double?,
)

/**
 * One exercise of a reviewed workout: the locally computed numbers (deterministic, also offline and in the demo)
 * plus what the coach said about it ([ai], null without AI).
 */
data class ExerciseComparison(
    val exerciseId: String,
    val name: String,
    val type: TrackingType,
    val today: ExerciseStats,
    /** The last finished session before this one that had this exercise; null the first time. */
    val last: ExerciseStats?,
    /** Verdict from the numbers alone (null the first time or without completed sets). */
    val localVerdict: Verdict?,
    val ai: ExerciseFeedback? = null,
) {
    /** What the screen shows: the coach's verdict, else the local one. */
    val verdict: Verdict? get() = ai?.verdict ?: localVerdict
    val volumeDelta: Double? get() = last?.let { l -> (today.volumeKg - l.volumeKg).takeIf { today.volumeKg > 0 || l.volumeKg > 0 } }
    val e1rmDelta: Double? get() = today.e1rmKg?.let { t -> last?.e1rmKg?.let { t - it } }
    val repsDelta: Int? get() = today.maxReps?.let { t -> last?.maxReps?.let { t - it } }
    val durationDelta: Int? get() = today.maxDurationSec?.let { t -> last?.maxDurationSec?.let { t - it } }
}

/** The coach's part per exercise (schema field `exercise_reviews`). */
data class ExerciseFeedback(
    val exerciseId: String,
    val verdict: Verdict?,
    val comment: String?,
    /** Concrete target for next time (one working set), e.g. 82.5 kg × 8. */
    val next: SuggestedSet?,
)

object ExerciseReviews {
    /** Below this relative change the primary number counts as "same" (rounding, plate steps of 1.25 kg). */
    const val SAME_RELATIVE = 0.01

    /**
     * Compares every exercise of [current] (in workout order, duplicates merged) with the last earlier session that
     * contains it. [lastOf] returns that session or null. Exercises without any completed set today are left out.
     */
    suspend fun compare(
        current: SessionRecord,
        dateOf: (Long) -> String,
        lastOf: suspend (exerciseId: String) -> SessionRecord?,
    ): List<ExerciseComparison> {
        val merged = current.exercises.groupBy { it.exercise.id }
        return merged.mapNotNull { (id, entries) ->
            val exercise = entries.first().exercise
            val today = stats(entries, dateOf(current.startedAt)) ?: return@mapNotNull null
            val prev = lastOf(id)
            val last = prev?.let { p -> stats(p.exercises.filter { it.exercise.id == id }, dateOf(p.startedAt)) }
            ExerciseComparison(id, exercise.name, exercise.trackingType, today, last, verdict(today, last))
        }
    }

    /** Puts the coach's feedback onto the comparisons (by exercise ID; unknown IDs are ignored). */
    fun withFeedback(list: List<ExerciseComparison>, feedback: List<ExerciseFeedback>): List<ExerciseComparison> {
        val byId = feedback.associateBy { it.exerciseId }
        return list.map { c -> byId[c.exerciseId]?.let { c.copy(ai = it) } ?: c }
    }

    fun stats(entries: List<WorkoutExercise>, date: String): ExerciseStats? {
        val type = entries.firstOrNull()?.exercise?.trackingType ?: return null
        val sets = entries.flatMap { e -> e.sets.filter { it.completed }.map { it.actual } }
        if (sets.isEmpty()) return null
        val bests = WorkoutStats.bests(sets, type)
        val best = sets.maxByOrNull { primary(it, type) ?: Double.NEGATIVE_INFINITY }
        return ExerciseStats(
            date = date,
            bestSet = best,
            setsDone = sets.size,
            volumeKg = WorkoutStats.volume(sets),
            e1rmKg = bests.bestE1rmKg,
            maxReps = bests.maxReps,
            maxDurationSec = bests.maxDurationSec,
            score = sets.mapNotNull { primary(it, type) }.maxOrNull(),
        )
    }

    /**
     * The number "better/worse" is decided on: weight × reps → e1RM (Epley; above 12 reps weight first, then reps),
     * body weight → reps (extra weight breaks ties), reps → reps, time → seconds, weight × time → weight, then time.
     */
    fun primary(v: SetValues, type: TrackingType): Double? = when (type) {
        TrackingType.WEIGHT_REPS -> {
            val w = v.weightKg ?: 0.0
            val r = v.reps ?: return null
            when {
                w > 0 -> WorkoutStats.e1rm(w, r) ?: (w * (1 + WorkoutStats.MAX_E1RM_REPS / 30.0) + (r - WorkoutStats.MAX_E1RM_REPS) * 0.01)
                else -> r + w / 1000.0
            }
        }
        TrackingType.REPS -> v.reps?.toDouble()
        TrackingType.TIME -> v.durationSec?.toDouble()
        TrackingType.WEIGHT_TIME -> v.durationSec?.let { (v.weightKg ?: 0.0) * 10_000 + it }
    }

    /**
     * Weighted exercises (score = e1RM in kg) need a change of more than [SAME_RELATIVE]; reps and seconds at least 1.
     * With the same top number, more than 5 % volume counts as better, more than 5 % less as worse.
     */
    fun verdict(today: ExerciseStats, last: ExerciseStats?): Verdict? {
        val t = today.score ?: return null
        val l = last?.score ?: return null
        val weighted = (today.bestSet?.weightKg ?: 0.0) > 0 && today.e1rmKg != null && last.e1rmKg != null
        val limit = if (weighted) abs(l) * SAME_RELATIVE else 0.5
        val diff = t - l
        return when {
            diff > limit -> Verdict.BETTER
            diff < -limit -> Verdict.WORSE
            today.volumeKg > 0 && last.volumeKg > 0 && today.volumeKg > last.volumeKg * 1.05 -> Verdict.BETTER
            today.volumeKg > 0 && last.volumeKg > 0 && today.volumeKg < last.volumeKg * 0.95 -> Verdict.WORSE
            else -> Verdict.SAME
        }
    }

    /**
     * Simple progression from today's best set, for the demo only (the real target comes from the coach): better or
     * same → a bit more (2.5 kg, else 1 rep or 5 s), worse → repeat today's best set.
     */
    fun simpleNext(c: ExerciseComparison): SuggestedSet? {
        val best = c.today.bestSet ?: return null
        val up = c.verdict != Verdict.WORSE
        val w = best.weightKg
        return when (c.type) {
            TrackingType.WEIGHT_REPS -> if (w != null && w > 0) SuggestedSet(if (up) w + 2.5 else w, best.reps, null)
                else SuggestedSet(w, (best.reps ?: 0) + if (up) 1 else 0, null)
            TrackingType.REPS -> SuggestedSet(null, (best.reps ?: 0) + if (up) 1 else 0, null)
            TrackingType.TIME -> SuggestedSet(null, null, (best.durationSec ?: 0) + if (up) 5 else 0)
            TrackingType.WEIGHT_TIME -> SuggestedSet(w, null, (best.durationSec ?: 0) + if (up) 5 else 0)
        }
    }

    /** Compact text for the prompt: the coach sees the same numbers as the user. */
    fun promptLines(list: List<ExerciseComparison>): List<String> = list.map { c ->
        val today = statsText(c.today, c.type)
        val last = c.last?.let { "${it.date}: ${statsText(it, c.type)}" } ?: "first time"
        "${c.name} [${c.exerciseId}]: today $today | last $last | by the numbers: ${c.localVerdict?.name?.lowercase() ?: "n/a"}"
    }

    private fun statsText(s: ExerciseStats, type: TrackingType): String = listOfNotNull(
        s.bestSet?.let { "best ${CoachPromptBuilder.formatSet(it, type)}" },
        "${s.setsDone} sets",
        s.volumeKg.takeIf { it > 0 }?.let { "volume ${it.toLong()}kg" },
        s.e1rmKg?.let { "e1RM ${(it * 10).toLong() / 10.0}kg" },
    ).joinToString(", ")
}
