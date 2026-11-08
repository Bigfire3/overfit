package app.overfit.domain

/**
 * Key numbers of a finished workout and personal records. Pure functions (common tests).
 *
 * Volume counts kg × reps of completed sets with positive weight (assisted sets with negative
 * weight and body-weight sets without extra weight add nothing).
 */
data class WorkoutSummary(
    val durationMs: Long,
    val exerciseCount: Int,
    val completedSets: Int,
    val totalSets: Int,
    val volumeKg: Double,
    /** Planned sets left open (not counting exercises skipped on purpose, see [skippedExercises]). */
    val skippedSets: Int,
    /** Exercises marked "skip today". */
    val skippedExercises: Int = 0,
    val addedExercises: Int,
    val replacedExercises: Int,
    val records: List<PersonalRecord>,
)

enum class RecordKind { WEIGHT, E1RM, REPS, DURATION }

/** A new best: [value] beats [previous] (kg, reps or seconds depending on [kind]). */
data class PersonalRecord(
    val exerciseId: String,
    val exerciseName: String,
    val kind: RecordKind,
    val value: Double,
    val previous: Double,
)

/** A completed set of one exercise (history, best values). */
data class LoggedSet(val exerciseId: String, val values: SetValues)

/** Best values of one exercise. Null = never logged for this metric. */
data class ExerciseBests(
    val maxWeightKg: Double? = null,
    val bestE1rmKg: Double? = null,
    val maxReps: Int? = null,
    val maxDurationSec: Int? = null,
)

object WorkoutStats {
    /** Reps above this make the e1RM estimate meaningless; such sets do not count for it. */
    const val MAX_E1RM_REPS = 12

    /** Estimated one-rep max (Epley); null without positive weight and 1–[MAX_E1RM_REPS] reps. */
    fun e1rm(weightKg: Double?, reps: Int?): Double? {
        if (weightKg == null || reps == null || weightKg <= 0.0 || reps < 1 || reps > MAX_E1RM_REPS) return null
        return if (reps == 1) weightKg else weightKg * (1 + reps / 30.0)
    }

    fun volume(sets: List<SetValues>): Double = sets.sumOf { s ->
        val w = s.weightKg ?: 0.0
        val r = s.reps ?: 0
        if (w > 0 && r > 0) w * r else 0.0
    }

    fun bests(sets: List<SetValues>, type: TrackingType): ExerciseBests {
        if (sets.isEmpty()) return ExerciseBests()
        val usesWeight = type == TrackingType.WEIGHT_REPS || type == TrackingType.WEIGHT_TIME
        val usesReps = type == TrackingType.WEIGHT_REPS || type == TrackingType.REPS
        val usesTime = type == TrackingType.TIME || type == TrackingType.WEIGHT_TIME
        return ExerciseBests(
            maxWeightKg = if (usesWeight) sets.mapNotNull { it.weightKg }.maxOrNull() else null,
            bestE1rmKg = if (type == TrackingType.WEIGHT_REPS) sets.mapNotNull { e1rm(it.weightKg, it.reps) }.maxOrNull() else null,
            maxReps = if (usesReps) sets.mapNotNull { it.reps }.maxOrNull() else null,
            maxDurationSec = if (usesTime) sets.mapNotNull { it.durationSec }.maxOrNull() else null,
        )
    }

    /**
     * Summary of [workout] ended at [endedAt]. [previous] are the completed sets of the same
     * exercises from earlier finished workouts; a record needs an earlier value to beat (the
     * first time an exercise is logged is not a record).
     */
    fun summarize(workout: Workout, endedAt: Long, previous: List<LoggedSet>): WorkoutSummary {
        val done = workout.exercises.associate { e -> e.exercise.id to (e to e.sets.filter { it.completed }.map { it.actual }) }
        val before = previous.groupBy({ it.exerciseId }, { it.values })
        val records = mutableListOf<PersonalRecord>()
        // Same exercise twice in a workout counts once (merged sets).
        val merged = workout.exercises.groupBy { it.exercise.id }
        merged.forEach { (id, entries) ->
            val exercise = entries.first().exercise
            val now = bests(entries.flatMap { e -> e.sets.filter { it.completed }.map { it.actual } }, exercise.trackingType)
            val old = bests(before[id].orEmpty(), exercise.trackingType)
            fun check(kind: RecordKind, new: Double?, prev: Double?) {
                if (new != null && prev != null && new > prev + 1e-9) records += PersonalRecord(id, exercise.name, kind, new, prev)
            }
            check(RecordKind.WEIGHT, now.maxWeightKg, old.maxWeightKg)
            check(RecordKind.E1RM, now.bestE1rmKg, old.bestE1rmKg)
            check(RecordKind.REPS, now.maxReps?.toDouble(), old.maxReps?.toDouble())
            check(RecordKind.DURATION, now.maxDurationSec?.toDouble(), old.maxDurationSec?.toDouble())
        }
        return WorkoutSummary(
            durationMs = (endedAt - workout.startedAt).coerceAtLeast(0),
            exerciseCount = workout.exercises.size,
            completedSets = workout.completedSets,
            totalSets = workout.totalSets,
            volumeKg = volume(done.values.flatMap { it.second }),
            skippedSets = workout.exercises.filterNot { it.skipped }.sumOf { e -> e.sets.count { !it.completed } },
            skippedExercises = workout.exercises.count { it.skipped },
            addedExercises = workout.exercises.count { it.origin == ExerciseOrigin.ADDED },
            replacedExercises = workout.exercises.count { it.origin == ExerciseOrigin.REPLACED },
            records = records,
        )
    }
}
