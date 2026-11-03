package app.overfit.coach

import app.overfit.domain.Exercise
import app.overfit.domain.Language
import app.overfit.domain.PlannedSet
import app.overfit.domain.Template
import app.overfit.domain.TemplateExercise
import app.overfit.domain.TrackingType
import app.overfit.domain.WeightUnit
import app.overfit.domain.displayWeight
import app.overfit.domain.fitTo
import app.overfit.domain.formatNumber

/** One change of the coach's plan against the template; [key] identifies it for "accept individually". */
sealed interface PlanChange {
    val key: String
    val exercise: Exercise
    val reason: String?

    /** Exercise stays, sets and/or rest change. [index] = position in the template. */
    data class Modified(
        override val key: String,
        val index: Int,
        override val exercise: Exercise,
        val before: List<PlannedSet>,
        val after: List<PlannedSet>,
        val restBefore: Int?,
        val restAfter: Int?,
        override val reason: String?,
    ) : PlanChange {
        val setsChanged: Boolean get() = before != after
        val restChanged: Boolean get() = restBefore != restAfter
    }

    /** New exercise; [afterIndex] = template position it follows (-1 = at the start). */
    data class Added(
        override val key: String,
        val afterIndex: Int,
        override val exercise: Exercise,
        val sets: List<PlannedSet>,
        val restSec: Int?,
        val markedNew: Boolean,
        override val reason: String?,
    ) : PlanChange

    data class Removed(override val key: String, val index: Int, override val exercise: Exercise) : PlanChange {
        override val reason: String? get() = null
    }
}

data class PlanDiff(
    val changes: List<PlanChange>,
    /** IDs the model used that are not in the database (ignored). */
    val unknownIds: List<String>,
) {
    val isEmpty: Boolean get() = changes.isEmpty()
}

object PlanDiffs {

    /**
     * Compares [suggestion] with [template]. Exercises are matched by ID in order (the 2nd occurrence of an ID in
     * the suggestion matches the 2nd in the template). Unchanged exercises produce no change. [lookup] resolves IDs
     * of exercises that are new to the template.
     */
    fun diff(template: Template, suggestion: PlanSuggestion, lookup: (String) -> Exercise?): PlanDiff {
        val changes = mutableListOf<PlanChange>()
        val unknown = mutableListOf<String>()
        val used = BooleanArray(template.exercises.size)
        var lastTemplateIndex = -1
        suggestion.exercises.forEachIndexed { si, s ->
            val ti = template.exercises.indices.firstOrNull { !used[it] && template.exercises[it].exercise.id == s.exerciseId }
            if (ti != null) {
                used[ti] = true
                lastTemplateIndex = ti
                val te = template.exercises[ti]
                val after = s.sets.map { it.toPlanned(te.exercise.trackingType) }.ifEmpty { te.sets }
                val restAfter = s.restSec ?: te.restSec
                if (after != te.sets || restAfter != te.restSec) {
                    changes += PlanChange.Modified("m$ti", ti, te.exercise, te.sets, after, te.restSec, restAfter, s.reason)
                }
            } else {
                val exercise = lookup(s.exerciseId)
                if (exercise == null) {
                    unknown += s.exerciseId
                } else {
                    val sets = s.sets.map { it.toPlanned(exercise.trackingType) }
                        .ifEmpty { List(3) { PlannedSet() } }
                    changes += PlanChange.Added("a$si", lastTemplateIndex, exercise, sets, s.restSec, s.isNew, s.reason)
                }
            }
        }
        template.exercises.forEachIndexed { i, te ->
            if (!used[i]) changes += PlanChange.Removed("r$i", i, te.exercise)
        }
        return PlanDiff(changes, unknown)
    }

    /** The template with the changes in [accepted] (keys) applied; everything else stays as it was. */
    fun apply(template: Template, diff: PlanDiff, accepted: Set<String>): Template {
        val chosen = diff.changes.filter { it.key in accepted }
        val modified = chosen.filterIsInstance<PlanChange.Modified>().associateBy { it.index }
        val removed = chosen.filterIsInstance<PlanChange.Removed>().map { it.index }.toSet()
        val added = chosen.filterIsInstance<PlanChange.Added>().groupBy { it.afterIndex }
        val result = mutableListOf<TemplateExercise>()
        fun addAfter(index: Int) = added[index].orEmpty().forEach {
            result += TemplateExercise(it.exercise, it.restSec, note = null, sets = it.sets)
        }
        addAfter(-1)
        template.exercises.forEachIndexed { i, te ->
            if (i !in removed) {
                val m = modified[i]
                result += if (m == null) te else te.copy(sets = m.after, restSec = m.restAfter)
            }
            addAfter(i)
        }
        return template.copy(exercises = result)
    }

    /** New template from a suggestion (free workout without template). Unknown IDs are skipped. */
    fun toTemplate(name: String, suggestion: PlanSuggestion, lookup: (String) -> Exercise?): Template =
        Template(
            id = 0,
            name = name,
            note = suggestion.note,
            exercises = suggestion.exercises.mapNotNull { s ->
                val e = lookup(s.exerciseId) ?: return@mapNotNull null
                TemplateExercise(e, s.restSec, null, s.sets.map { it.toPlanned(e.trackingType) }.ifEmpty { List(3) { PlannedSet() } })
            },
        )

    private fun SuggestedSet.toPlanned(type: TrackingType) = PlannedSet(weightKg, reps, durationSec).fitTo(type)

    /**
     * Short text of a set list: "3 × 8 · 90 kg" when all sets are equal, otherwise "90 kg × 8 · 92,5 kg × 6".
     * Empty list → "–".
     */
    fun summary(sets: List<PlannedSet>, type: TrackingType, unit: WeightUnit, language: Language): String {
        if (sets.isEmpty()) return "–"
        fun weight(kg: Double?) = kg?.let { "${formatNumber(displayWeight(it, unit), language)} ${unit.symbol}" }
        fun value(s: PlannedSet) = when (type) {
            TrackingType.WEIGHT_REPS, TrackingType.REPS -> s.reps?.toString() ?: "?"
            TrackingType.TIME, TrackingType.WEIGHT_TIME -> "${s.durationSec ?: "?"} s"
        }
        val first = sets.first()
        return if (sets.all { it == first }) {
            listOfNotNull("${sets.size} × ${value(first)}", weight(first.weightKg).takeIf { type.usesWeight }).joinToString(" · ")
        } else {
            sets.joinToString(" · ") { s ->
                val w = weight(s.weightKg).takeIf { type.usesWeight }
                if (w != null) "$w × ${value(s)}" else value(s)
            }
        }
    }
}
