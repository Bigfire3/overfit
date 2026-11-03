package app.overfit.workout

import app.overfit.data.FinishedWorkout
import app.overfit.domain.SetValues
import app.overfit.domain.Workout

/**
 * Draft of a finished training edited afterwards (calendar → training → edit). Pure state, the
 * screen keeps one and compares it with the loaded original ([dirty]). Sets added here count as
 * done (they happened, they were only forgotten); values are never negative.
 */
data class FinishedEdit(
    val workout: Workout,
    val note: String,
    val feeling: Int?,
    val sauna: Boolean,
    /** Completion time for sets that are checked off or added afterwards. */
    val endedAt: Long,
) {
    fun setValues(setId: String, values: SetValues) = copy(workout = workout.setValues(setId, values.nonNegative()))

    fun toggleSet(setId: String): FinishedEdit {
        val (ei, si) = workout.locate(setId) ?: return this
        val set = workout.exercises[ei].sets[si]
        return copy(workout = if (set.completed) workout.uncompleteSet(setId) else workout.completeSet(setId, endedAt))
    }

    fun addSet(exerciseId: String, newId: String = Workout.newId()): FinishedEdit {
        if (workout.exercise(exerciseId) == null) return this
        return copy(workout = workout.addSet(exerciseId, newId).completeSet(newId, endedAt))
    }

    fun removeSet(setId: String) = copy(workout = workout.removeSet(setId))

    fun withNote(text: String) = copy(note = text.take(2000))
    fun withFeeling(value: Int?) = copy(feeling = value?.coerceIn(1, 5))
    fun withSauna(value: Boolean) = copy(sauna = value)

    companion object {
        fun of(finished: FinishedWorkout) = FinishedEdit(
            workout = finished.workout,
            note = finished.note.orEmpty(),
            feeling = finished.feeling,
            sauna = finished.sauna,
            endedAt = finished.endedAt,
        )
    }
}

/** Weight, reps and time below zero make no sense; the inputs cannot type a minus, this is the backstop. */
fun SetValues.nonNegative() = SetValues(
    weightKg = weightKg?.coerceAtLeast(0.0),
    reps = reps?.coerceAtLeast(0),
    durationSec = durationSec?.coerceAtLeast(0),
)
