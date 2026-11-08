package app.overfit.domain

/**
 * Saved workouts (internally [Template]) follow what was trained: after a workout its structure
 * (exercises, order, number of sets) becomes the saved workout's structure, without asking.
 *
 * Target values are not copied from the session: sets that stay where they were keep their
 * planned values (e.g. what the coach set), new sets and exercises get empty targets, which the
 * next start fills from "last time" ([Workout.fromTemplate]).
 */
object WorkoutPlan {
    /**
     * Which variant of an exercise with alternatives to do: the one after the variant done last time in this workout
     * ([lastDoneId], round robin: A → B → A, or A → B → C → A), the first one if none of them was done yet.
     */
    fun nextVariant(variantIds: List<String>, lastDoneId: String?): Int {
        val last = variantIds.indexOf(lastDoneId)
        return if (last < 0) 0 else (last + 1) % variantIds.size
    }

    /** [template] with the structure and name of [workout]; [template] itself if nothing changed. */
    fun adopt(template: Template, workout: Workout): Template {
        if (workout.exercises.isEmpty()) return template
        val unmatched = template.exercises.toMutableList()
        val exercises = workout.exercises.mapNotNull { we ->
            // The plan's exercise this one was: by any of its variants, or (replaced during the training) by the one
            // it replaced, if that one has alternatives (they must not get lost).
            var index = unmatched.indexOfFirst { slot -> slot.variants.any { it.exercise.id == we.exercise.id } }
            var doneId = we.exercise.id
            if (index < 0 && we.replacedExerciseId != null) {
                index = unmatched.indexOfFirst { it.alternatives.isNotEmpty() && it.variants.any { v -> v.exercise.id == we.replacedExerciseId } }
                if (index >= 0) doneId = we.replacedExerciseId
            }
            val match = if (index >= 0) unmatched.removeAt(index) else null
            // Skipped today: the plan keeps the exercise exactly as it was (its sets were not trained). An exercise
            // added (or swapped in) during the training and then skipped does not join the plan.
            if (we.skipped) return@mapNotNull match
            val v = match?.variants?.indexOfFirst { it.exercise.id == doneId } ?: -1
            val planned = match?.variants?.getOrNull(v)
            val updated = TemplateExercise(
                exercise = we.exercise,
                restSec = we.restSec ?: planned?.restSec,
                // Exercise notes typed during a workout belong to that session; the plan keeps its own.
                note = planned?.note,
                sets = we.sets.indices.map { i -> planned?.sets?.getOrNull(i) ?: PlannedSet() },
            )
            // Only the variant that was trained changes; the others stay as planned.
            when {
                match == null -> updated
                v <= 0 -> updated.copy(alternatives = match.alternatives)
                else -> match.copy(alternatives = match.alternatives.toMutableList().also { it[v - 1] = updated })
            }
        }
        val adopted = template.copy(name = workout.name.trim().ifEmpty { template.name }, exercises = exercises)
        return if (adopted == template) template else adopted
    }

    /** A new saved workout built from [workout] (started empty); null if it has no exercises. */
    fun create(workout: Workout, name: String = workout.name): Template? {
        if (workout.exercises.isEmpty()) return null
        return Template(
            id = 0,
            name = name.trim().ifEmpty { workout.name },
            note = null,
            exercises = workout.exercises.map { we ->
                TemplateExercise(we.exercise, we.restSec, note = null, sets = we.sets.map { PlannedSet() })
            },
        )
    }
}
