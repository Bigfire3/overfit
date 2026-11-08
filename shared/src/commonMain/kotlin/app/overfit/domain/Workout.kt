package app.overfit.domain

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * A running (or finished) workout. Pure data: every change goes through the functions below,
 * which return a new [Workout]. [WorkoutController][app.overfit.workout.WorkoutController] applies
 * them and persists the result after every change.
 *
 * IDs are UUID strings so sessions stay resolvable across devices (sharing, backup).
 */
data class Workout(
    val id: String,
    val name: String,
    val templateId: Long?,
    /** Epoch milliseconds. */
    val startedAt: Long,
    val exercises: List<WorkoutExercise>,
    /** Running rest timer, or null. Persisted so it survives a process kill. */
    val rest: RestTimer? = null,
    val note: String? = null,
) {
    val completedSets: Int get() = exercises.sumOf { e -> e.sets.count { it.completed } }

    /** Sets that count: all sets, but of a skipped exercise only the ones already done. */
    val totalSets: Int get() = exercises.sumOf { it.countedSets }

    /**
     * Nothing is open any more (every set done or its exercise skipped) and there was something to do: at least one
     * set or one skipped exercise (exercises whose sets were all removed do not count).
     */
    val allDone: Boolean get() = exercises.any { it.skipped || it.sets.isNotEmpty() } && nextOpenSet() == null

    fun exercise(id: String): WorkoutExercise? = exercises.firstOrNull { it.id == id }

    /** Position of a set: exercise index and set index, or null. */
    fun locate(setId: String): Pair<Int, Int>? {
        exercises.forEachIndexed { ei, e ->
            val si = e.sets.indexOfFirst { it.id == setId }
            if (si >= 0) return ei to si
        }
        return null
    }

    /**
     * The set to do next: the first open set after [afterSetId] (in list order, then wrapping
     * around to the start), or the first open set at all if [afterSetId] is null or unknown.
     */
    fun nextOpenSet(afterSetId: String? = null): SetRef? {
        val flat = exercises.flatMap { e -> e.sets.map { SetRef(e, it) } }
        val start = afterSetId?.let { id -> flat.indexOfFirst { it.set.id == id } } ?: -1
        val ordered = if (start < 0) flat else flat.drop(start + 1) + flat.take(start + 1)
        // Sets of a skipped exercise are not open: "Log next set" and the notification pass over them.
        return ordered.firstOrNull { !it.set.completed && !it.exercise.skipped }
    }

    fun withName(name: String) = copy(name = name)

    // --- Sets ----------------------------------------------------------------------------------

    fun updateSet(setId: String, transform: (WorkoutSet) -> WorkoutSet) =
        copy(exercises = exercises.map { e -> if (e.sets.any { it.id == setId }) e.copy(sets = e.sets.map { if (it.id == setId) transform(it) else it }) else e })

    fun setValues(setId: String, values: SetValues) = updateSet(setId) { it.copy(actual = values) }

    /**
     * Marks a set as done at [now]. Empty actual fields take the target values, so "check" on a
     * prefilled row logs exactly what is shown.
     */
    fun completeSet(setId: String, now: Long) = updateSet(setId) { s ->
        s.copy(actual = s.actual.orElse(s.target), completed = true, completedAt = now)
    }

    fun uncompleteSet(setId: String) = updateSet(setId) { it.copy(completed = false, completedAt = null) }

    /** New set at the end of the exercise, with the values of the last set (not completed). */
    fun addSet(exerciseId: String, newId: String = newId()) = updateExercise(exerciseId) { e ->
        val last = e.sets.lastOrNull()
        val set = WorkoutSet(newId, target = last?.target ?: SetValues(), actual = last?.actual ?: SetValues())
        e.copy(sets = e.sets + set)
    }

    /** Copy of a set directly below it (not completed). */
    fun duplicateSet(setId: String, newId: String = newId()) = copy(
        exercises = exercises.map { e ->
            val i = e.sets.indexOfFirst { it.id == setId }
            if (i < 0) e else {
                val copy = e.sets[i].copy(id = newId, completed = false, completedAt = null)
                e.copy(sets = e.sets.toMutableList().apply { add(i + 1, copy) })
            }
        },
    )

    fun removeSet(setId: String) =
        copy(exercises = exercises.map { e -> e.copy(sets = e.sets.filterNot { it.id == setId }) })

    // --- Exercises -----------------------------------------------------------------------------

    fun addExercise(exercise: WorkoutExercise) = copy(exercises = exercises + exercise)

    fun removeExercise(exerciseId: String) = copy(exercises = exercises.filterNot { it.id == exerciseId })

    /**
     * Replaces the exercise of an entry. Sets stay (completed ones too, they keep their values);
     * the entry remembers what it replaced. Values only carry over if the tracking type fits.
     */
    fun replaceExercise(exerciseId: String, replacement: Exercise) = updateExercise(exerciseId) { e ->
        if (e.exercise.id == replacement.id) return@updateExercise e
        val type = replacement.trackingType
        e.copy(
            exercise = replacement,
            origin = ExerciseOrigin.REPLACED,
            // Keep the very first original if replaced twice.
            replacedExerciseId = e.replacedExerciseId ?: e.exercise.id,
            restSec = null,
            // Something else entirely: no longer one of the plan's variants (no chips).
            variants = emptyList(),
            sets = e.sets.map { s -> s.copy(target = s.target.fitTo(type), actual = s.actual.fitTo(type)) },
        )
    }

    /** Moves the exercise at [from] to [to] (indices into [exercises]), e.g. after drag & drop. */
    fun moveExercise(from: Int, to: Int): Workout {
        if (from !in exercises.indices || to !in exercises.indices || from == to) return this
        val list = exercises.toMutableList()
        list.add(to, list.removeAt(from))
        return copy(exercises = list)
    }

    /**
     * "Skip today" ([skipped] = true) or undo it. The sets stay as they are (done ones keep counting), the open ones no
     * longer count as open, so the workout can be finished without them. The saved workout keeps the exercise.
     */
    fun setSkipped(exerciseId: String, skipped: Boolean) = updateExercise(exerciseId) { it.copy(skipped = skipped) }

    /**
     * Switches an exercise with alternatives to its variant [variant] (index into [WorkoutExercise.variants]): exercise,
     * rest, note and sets come from that variant (targets from the plan, gaps from [last] = its last time). Only while
     * none of its sets is done; otherwise (or for an unknown index) nothing changes.
     */
    fun switchVariant(exerciseId: String, variant: Int, last: List<SetValues>, idGen: () -> String = ::newId) =
        updateExercise(exerciseId) { e ->
            val v = e.variants.getOrNull(variant) ?: return@updateExercise e
            if (e.sets.any { it.completed } || v.exercise.id == e.exercise.id) return@updateExercise e
            e.copy(exercise = v.exercise, restSec = v.restSec, note = v.note.orEmpty(), sets = plannedSets(v, last, idGen))
        }

    fun setExerciseNote(exerciseId: String, note: String) = updateExercise(exerciseId) { it.copy(note = note) }

    fun setExerciseRest(exerciseId: String, restSec: Int?) = updateExercise(exerciseId) { it.copy(restSec = restSec) }

    private fun updateExercise(id: String, transform: (WorkoutExercise) -> WorkoutExercise) =
        copy(exercises = exercises.map { if (it.id == id) transform(it) else it })

    data class SetRef(val exercise: WorkoutExercise, val set: WorkoutSet) {
        /** 1-based number of the set within its exercise. */
        val number: Int get() = exercise.sets.indexOfFirst { it.id == set.id } + 1
    }

    companion object {
        @OptIn(ExperimentalUuidApi::class)
        fun newId(): String = Uuid.random().toString()

        /** Empty workout ("free training"). */
        fun empty(name: String, now: Long, id: String = newId()) =
            Workout(id = id, name = name, templateId = null, startedAt = now, exercises = emptyList())

        /**
         * Workout from a template. Target values come from the template; where the template leaves
         * a value empty, the value from last time ([lastTime] per exercise ID, by set position) is
         * used. The actual values start as the targets, so a set can be checked off directly.
         */
        fun fromTemplate(
            template: Template,
            now: Long,
            lastTime: Map<String, List<SetValues>> = emptyMap(),
            id: String = newId(),
            idGen: () -> String = ::newId,
            /** Chosen variant per exercise index (0 = the exercise, 1… = its alternatives); missing = 0. */
            variants: Map<Int, Int> = emptyMap(),
        ) = Workout(
            id = id,
            name = template.name,
            templateId = template.id,
            startedAt = now,
            exercises = template.exercises.mapIndexed { index, slot ->
                val all = slot.variants
                val te = all.getOrNull(variants[index] ?: 0) ?: all.first()
                WorkoutExercise(
                    id = idGen(),
                    exercise = te.exercise,
                    restSec = te.restSec,
                    note = te.note.orEmpty(),
                    origin = ExerciseOrigin.PLANNED,
                    sets = plannedSets(te, lastTime[te.exercise.id].orEmpty(), idGen),
                    variants = if (all.size > 1) all else emptyList(),
                )
            },
        )

        /** Sets of a planned exercise: targets from the plan, gaps from last time; actual = target (check off directly). */
        fun plannedSets(te: TemplateExercise, last: List<SetValues>, idGen: () -> String = ::newId): List<WorkoutSet> =
            te.sets.mapIndexed { i, planned ->
                val target = planned.orElse(last.getOrNull(i) ?: SetValues()).fitTo(te.exercise.trackingType)
                WorkoutSet(idGen(), target = target, actual = target)
            }

        /**
         * Entry for an exercise added during the workout: one set per set from last time (with
         * those values), otherwise [DEFAULT_SETS] empty sets.
         */
        fun added(
            exercise: Exercise,
            lastTime: List<SetValues>,
            idGen: () -> String = ::newId,
        ): WorkoutExercise {
            val values = lastTime.map { it.fitTo(exercise.trackingType) }.ifEmpty { List(DEFAULT_SETS) { SetValues() } }
            return WorkoutExercise(
                id = idGen(),
                exercise = exercise,
                restSec = null,
                note = "",
                origin = ExerciseOrigin.ADDED,
                sets = values.map { WorkoutSet(idGen(), target = it, actual = it) },
            )
        }

        const val DEFAULT_SETS = 3
    }
}

/** Values of a set; which fields count depends on the exercise's [TrackingType]. Weight in kg. */
typealias SetValues = PlannedSet

/** Fields of [this] that are null are taken from [other]. */
fun SetValues.orElse(other: SetValues) = SetValues(
    weightKg = weightKg ?: other.weightKg,
    reps = reps ?: other.reps,
    durationSec = durationSec ?: other.durationSec,
)

/** Drops the fields the tracking type does not use. */
fun SetValues.fitTo(type: TrackingType) = SetValues(
    weightKg = weightKg.takeIf { type.usesWeight },
    reps = reps.takeIf { type.usesReps },
    durationSec = durationSec.takeIf { type.usesTime },
)

val SetValues.isEmpty: Boolean get() = weightKg == null && reps == null && durationSec == null

enum class ExerciseOrigin { PLANNED, ADDED, REPLACED }

data class WorkoutExercise(
    val id: String,
    val exercise: Exercise,
    /** Rest from the template; null = the exercise's rest or the global default. */
    val restSec: Int?,
    val note: String,
    val origin: ExerciseOrigin,
    /** For [ExerciseOrigin.REPLACED]: the exercise that was planned here. */
    val replacedExerciseId: String? = null,
    val sets: List<WorkoutSet>,
    /** "Skip today": the open sets of this exercise do not count; stored with the training (v13). */
    val skipped: Boolean = false,
    /**
     * All variants of this planned exercise (the exercise and its alternatives, from the saved workout) when it has
     * alternatives, else empty. Not stored with the training: a running one gets them again from its workout.
     */
    val variants: List<TemplateExercise> = emptyList(),
) {
    /** Index of the current exercise in [variants], or -1. */
    val variantIndex: Int get() = variants.indexOfFirst { it.exercise.id == exercise.id }

    val completedSets: Int get() = sets.count { it.completed }

    /** Sets that count towards the workout: of a skipped exercise only the ones already done. */
    val countedSets: Int get() = if (skipped) completedSets else sets.size

    /** Rest after a set of this exercise: template → exercise → global default. */
    fun restSec(globalDefault: Int): Int = restSec ?: exercise.defaultRestSec ?: globalDefault
}

data class WorkoutSet(
    val id: String,
    val target: SetValues = SetValues(),
    val actual: SetValues = SetValues(),
    val completed: Boolean = false,
    /** Epoch milliseconds. */
    val completedAt: Long? = null,
)

/**
 * Rest timer as wall-clock times (epoch ms), so it keeps running while the app is closed and
 * can be restored after a process kill. [afterSetId] is the set whose completion started it.
 */
data class RestTimer(
    val startedAt: Long,
    val endsAt: Long,
    val afterSetId: String? = null,
) {
    val totalMs: Long get() = endsAt - startedAt

    fun remainingMs(now: Long): Long = (endsAt - now).coerceAtLeast(0)

    fun isOver(now: Long): Boolean = now >= endsAt

    /** Share of the rest that is over, 0..1. */
    fun progress(now: Long): Float =
        if (totalMs <= 0) 1f else ((now - startedAt).toFloat() / totalMs).coerceIn(0f, 1f)

    /**
     * Adds (or with a negative value removes) time; the total changes with it. The result may
     * already be over, then the caller ends the rest.
     */
    fun adjust(deltaMs: Long): RestTimer = copy(endsAt = endsAt + deltaMs)

    companion object {
        fun start(now: Long, durationSec: Int, afterSetId: String? = null) =
            RestTimer(startedAt = now, endsAt = now + durationSec * 1000L, afterSetId = afterSetId)
    }
}
