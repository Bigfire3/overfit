package app.overfit.ui.templates

import app.overfit.domain.Exercise
import app.overfit.domain.Language
import app.overfit.domain.Numbers
import app.overfit.domain.PlannedSet
import app.overfit.domain.Template
import app.overfit.domain.TemplateExercise
import app.overfit.domain.WeightUnit

/**
 * Editing state of a template. Numbers stay text so intermediate input ("92,") survives typing;
 * only [toTemplate] parses them. Weights are text in the user's unit. All operations are pure.
 */
data class TemplateDraft(
    val id: Long = 0,
    val name: String = "",
    val note: String = "",
    val exercises: List<DraftExercise> = emptyList(),
    private val nextKey: Long = 1,
) {
    data class DraftExercise(
        val key: Long,
        val exercise: Exercise,
        val rest: String,
        val note: String,
        val sets: List<DraftSet>,
        /** Alternatives (A/B): edited like the exercise itself, keys are unique across the whole draft. */
        val alternatives: List<DraftExercise> = emptyList(),
    )

    data class DraftSet(val key: Long, val weight: String = "", val reps: String = "", val duration: String = "")

    val isNew: Boolean get() = id == 0L

    fun withName(name: String) = copy(name = name)

    fun addExercises(list: List<Exercise>): TemplateDraft {
        var k = nextKey
        val added = list.map { exercise ->
            val sets = List(DEFAULT_SETS) { DraftSet(k++) }
            DraftExercise(k++, exercise, rest = exercise.defaultRestSec?.toString().orEmpty(), note = "", sets = sets)
        }
        return copy(exercises = exercises + added, nextKey = k)
    }

    /** Removes an exercise (with its alternatives) or a single alternative. */
    fun removeExercise(key: Long) = copy(
        exercises = exercises.filterNot { it.key == key }.map { e ->
            if (e.alternatives.any { it.key == key }) e.copy(alternatives = e.alternatives.filterNot { it.key == key }) else e
        },
    )

    /** Adds [exercise] as an alternative of the exercise [key] (same default sets as a new exercise). */
    fun addAlternative(key: Long, exercise: Exercise): TemplateDraft {
        var k = nextKey
        val sets = List(DEFAULT_SETS) { DraftSet(k++) }
        val alt = DraftExercise(k++, exercise, rest = exercise.defaultRestSec?.toString().orEmpty(), note = "", sets = sets)
        val updated = copy(exercises = exercises.map { if (it.key == key) it.copy(alternatives = it.alternatives + alt) else it })
        return updated.copy(nextKey = k)
    }

    /** All exercises and alternatives (validation, lookups). */
    val allExercises: List<DraftExercise> get() = exercises.flatMap { listOf(it) + it.alternatives }

    /** Moves an exercise by [delta] positions (−1 = up). */
    fun moveExercise(key: Long, delta: Int): TemplateDraft {
        val from = exercises.indexOfFirst { it.key == key }
        if (from < 0) return this
        val to = (from + delta).coerceIn(0, exercises.lastIndex)
        if (to == from) return this
        val list = exercises.toMutableList()
        list.add(to, list.removeAt(from))
        return copy(exercises = list)
    }

    /** Replaces the exercise, keeps sets, rest and note. */
    fun replaceExercise(key: Long, exercise: Exercise) = updateExercise(key) { it.copy(exercise = exercise) }

    fun setRest(key: Long, rest: String) = updateExercise(key) { it.copy(rest = rest.filter(Char::isDigit).take(4)) }

    fun setNote(key: Long, note: String) = updateExercise(key) { it.copy(note = note) }

    /** A new set copies the values of the last set. */
    fun addSet(key: Long): TemplateDraft {
        var k = nextKey
        val updated = updateExercise(key) { e ->
            val last = e.sets.lastOrNull()
            e.copy(sets = e.sets + (last?.copy(key = k++) ?: DraftSet(k++)))
        }
        return updated.copy(nextKey = k)
    }

    fun removeSet(exerciseKey: Long, setKey: Long) =
        updateExercise(exerciseKey) { e -> e.copy(sets = e.sets.filterNot { it.key == setKey }) }

    fun updateSet(exerciseKey: Long, setKey: Long, transform: (DraftSet) -> DraftSet) =
        updateExercise(exerciseKey) { e -> e.copy(sets = e.sets.map { if (it.key == setKey) transform(it) else it }) }

    /** Changes the exercise or alternative with [key]. */
    private fun updateExercise(key: Long, transform: (DraftExercise) -> DraftExercise) = copy(
        exercises = exercises.map { e ->
            when {
                e.key == key -> transform(e)
                e.alternatives.any { it.key == key } -> e.copy(alternatives = e.alternatives.map { if (it.key == key) transform(it) else it })
                else -> e
            }
        },
    )

    /** Why the draft cannot be saved, or null. */
    sealed interface Problem {
        data object MissingName : Problem
        /** Invalid value in set [set] (1-based) of [exercise]. */
        data class InvalidValue(val exercise: String, val set: Int) : Problem
    }

    fun validate(unit: WeightUnit): Problem? {
        if (name.isBlank()) return Problem.MissingName
        allExercises.forEach { e ->
            e.sets.forEachIndexed { i, s ->
                val bad = (s.weight.isNotBlank() && Numbers.parseWeight(s.weight, unit) == null) ||
                    (s.reps.isNotBlank() && Numbers.parseCount(s.reps) == null) ||
                    (s.duration.isNotBlank() && Numbers.parseDuration(s.duration) == null)
                if (bad) return Problem.InvalidValue(e.exercise.name, i + 1)
            }
        }
        return null
    }

    /** Only fields that match the exercise's tracking type are kept. Weights are converted to kg. */
    fun toTemplate(unit: WeightUnit = WeightUnit.KG): Template = Template(
        id = id,
        name = name.trim(),
        note = note.trim().ifEmpty { null },
        exercises = exercises.map { e -> e.toPlanned(unit).copy(alternatives = e.alternatives.map { it.toPlanned(unit) }) },
    )

    private fun DraftExercise.toPlanned(unit: WeightUnit): TemplateExercise {
        val type = exercise.trackingType
        return TemplateExercise(
            exercise = exercise,
            restSec = rest.toIntOrNull(),
            note = note.trim().ifEmpty { null },
            sets = sets.map { s ->
                PlannedSet(
                    weightKg = if (type.usesWeight) Numbers.parseWeight(s.weight, unit) else null,
                    reps = if (type.usesReps) Numbers.parseCount(s.reps) else null,
                    durationSec = if (type.usesTime) Numbers.parseDuration(s.duration) else null,
                )
            },
        )
    }

    companion object {
        const val DEFAULT_SETS = 3

        fun from(template: Template, unit: WeightUnit = WeightUnit.KG, language: Language = Language.DE): TemplateDraft {
            var k = 1L
            fun draft(te: TemplateExercise): DraftExercise {
                val sets = te.sets.map { s ->
                    DraftSet(
                        key = k++,
                        weight = s.weightKg?.let { Numbers.formatWeight(it, unit, language) }.orEmpty(),
                        reps = s.reps?.toString().orEmpty(),
                        duration = s.durationSec?.toString().orEmpty(),
                    )
                }
                return DraftExercise(k++, te.exercise, te.restSec?.toString().orEmpty(), te.note.orEmpty(), sets)
            }
            val exercises = template.exercises.map { te -> draft(te).copy(alternatives = te.alternatives.map(::draft)) }
            return TemplateDraft(template.id, template.name, template.note.orEmpty(), exercises, k)
        }
    }
}
