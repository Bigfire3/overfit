package app.overfit.chat

import app.overfit.domain.Language
import app.overfit.domain.PlannedSet
import app.overfit.domain.Template
import app.overfit.domain.WeightUnit
import app.overfit.domain.Numbers
import app.overfit.i18n.ChatStrings
import app.overfit.share.ImportPreview

/** Difference between an existing template and an incoming proposal, as readable lines for "Update template". */
object TemplateDiff {
    fun lines(old: Template, new: ImportPreview, t: ChatStrings, unit: WeightUnit, language: Language): List<String> {
        val out = mutableListOf<String>()
        if (!old.name.trim().equals(new.name.trim(), ignoreCase = false)) out += t.diffRenamed(old.name, new.name)
        // Exercises are matched by position of their first occurrence per name (a template may repeat an exercise).
        val oldByName = old.exercises.groupBy { it.exercise.name.lowercase() }.mapValues { it.value.toMutableList() }
        val newByName = new.exercises.groupBy { it.displayName.lowercase() }.mapValues { it.value.toMutableList() }
        for (line in new.exercises) {
            val key = line.displayName.lowercase()
            val match = oldByName[key]?.removeFirstOrNull()
            if (match == null) { out += t.diffAdded(line.displayName); continue }
            val before = summary(match.sets, t, unit, language)
            val after = summary(line.sets, t, unit, language)
            if (before != after) out += t.diffChanged(line.displayName, before, after)
        }
        for (te in old.exercises) {
            val key = te.exercise.name.lowercase()
            val left = newByName[key]
            if (left == null || left.isEmpty()) out += t.diffRemoved(te.exercise.name) else left.removeFirst()
        }
        return out
    }

    /** "3× 60 kg 8×" for equal sets, otherwise the first set with the count. */
    fun summary(sets: List<PlannedSet>, t: ChatStrings, unit: WeightUnit, language: Language): String {
        val first = sets.firstOrNull() ?: return t.diffSets(0, "")
        val text = buildList {
            first.weightKg?.let { add("${Numbers.formatWeight(it, unit, language)} ${unit.symbol}") }
            first.reps?.let { add("$it×") }
            first.durationSec?.let { add(Numbers.formatDuration(it)) }
        }.joinToString(" ")
        val same = sets.all { it == first }
        return t.diffSets(sets.size, if (same) text else "$text …")
    }
}
