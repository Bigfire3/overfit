package app.overfit.coach

import app.overfit.domain.ExerciseOrigin
import app.overfit.domain.Template
import app.overfit.domain.TemplateExercise
import app.overfit.domain.Workout
import app.overfit.domain.WorkoutExercise
import app.overfit.domain.WorkoutSet
import app.overfit.domain.fitTo
import app.overfit.share.ShareMapper

/**
 * Applies a coach proposal (a whole workout in the share format, read as a draft template) to the workout that is
 * running right now. What the user already did never changes:
 * - an exercise of the proposal that is already in the workout keeps its done sets; its open sets become the
 *   proposal's remaining sets (proposal 4 sets, 1 done → 3 open sets with the proposed values),
 * - exercises of the proposal that are not there yet are added,
 * - exercises missing in the proposal are removed, unless sets of them are done (they stay, in front),
 * - exercises the app does not know (no catalogue match) are left out and returned in [Result.unknown].
 */
object RunningMerge {
    data class Result(val workout: Workout, val unknown: List<String>)

    fun apply(w: Workout, proposal: Template, idGen: () -> String = Workout::newId): Result {
        val remaining = w.exercises.toMutableList()
        val unknown = mutableListOf<String>()
        val merged = mutableListOf<WorkoutExercise>()
        for (te in proposal.exercises) {
            if (te.exercise.id.startsWith(ShareMapper.PENDING_PREFIX)) {
                // Not resolved by id, but the workout has it by name (e.g. a custom exercise): keep that one as it is.
                val sameName = remaining.indexOfFirst { it.exercise.name.equals(te.exercise.name, ignoreCase = true) }
                if (sameName >= 0) merged += remaining.removeAt(sameName) else unknown += te.exercise.name
                continue
            }
            val type = te.exercise.trackingType
            val index = remaining.indexOfFirst { it.exercise.id == te.exercise.id }
            if (index >= 0) {
                val e = remaining.removeAt(index)
                val done = e.sets.filter { it.completed }
                val openIds = e.sets.filter { !it.completed }.map { it.id }.iterator()
                val open = te.sets.drop(done.size).map { v ->
                    val target = v.fitTo(type)
                    WorkoutSet(if (openIds.hasNext()) openIds.next() else idGen(), target = target, actual = target)
                }
                merged += e.copy(sets = done + open, restSec = te.restSec ?: e.restSec, note = te.note?.takeIf { it.isNotBlank() } ?: e.note)
            } else {
                merged += WorkoutExercise(
                    id = idGen(),
                    exercise = te.exercise,
                    restSec = te.restSec,
                    note = te.note.orEmpty(),
                    origin = ExerciseOrigin.ADDED,
                    sets = te.sets.map { v -> val target = v.fitTo(type); WorkoutSet(idGen(), target = target, actual = target) },
                )
            }
        }
        val keptDone = remaining.filter { it.completedSets > 0 }.map { e -> e.copy(sets = e.sets.filter { it.completed }) }
        return Result(w.copy(exercises = keptDone + merged), unknown)
    }

    /** The running workout as a plan (done sets with their logged values, open ones with their targets), for the diff. */
    fun asTemplate(w: Workout): Template = Template(
        id = 0,
        name = w.name,
        note = null,
        exercises = w.exercises.map { e ->
            TemplateExercise(e.exercise, e.restSec, e.note.ifBlank { null }, e.sets.map { if (it.completed) it.actual else it.target })
        },
    )
}
