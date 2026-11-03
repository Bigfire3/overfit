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
    /** [template] with the structure and name of [workout]; [template] itself if nothing changed. */
    fun adopt(template: Template, workout: Workout): Template {
        if (workout.exercises.isEmpty()) return template
        val unmatched = template.exercises.toMutableList()
        val exercises = workout.exercises.map { we ->
            val index = unmatched.indexOfFirst { it.exercise.id == we.exercise.id }
            val match = if (index >= 0) unmatched.removeAt(index) else null
            TemplateExercise(
                exercise = we.exercise,
                restSec = we.restSec ?: match?.restSec,
                // Exercise notes typed during a workout belong to that session; the plan keeps its own.
                note = match?.note,
                sets = we.sets.indices.map { i -> match?.sets?.getOrNull(i) ?: PlannedSet() },
            )
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
