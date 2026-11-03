package app.overfit.workout

import app.overfit.domain.Language
import app.overfit.domain.Numbers
import app.overfit.domain.SetValues
import app.overfit.domain.TrackingType
import app.overfit.domain.Workout
import app.overfit.domain.WeightUnit
import app.overfit.i18n.Strings

/** Texts around sets, shared by the workout screen, the rest timer and the notifications. */
object WorkoutFormat {
    /**
     * Short form of set values: "30 kg × 6", "12 Wdh.", "0:45", "20 kg · 0:45". Missing values
     * are left out; empty values give "".
     */
    fun values(v: SetValues, type: TrackingType, unit: WeightUnit, language: Language, t: Strings): String {
        val weight = v.weightKg?.takeIf { type.usesWeight }?.let { "${Numbers.formatWeight(it, unit, language)} ${unit.symbol}" }
        val reps = v.reps?.takeIf { type.usesReps }
        val time = v.durationSec?.takeIf { type.usesTime }?.let(Numbers::formatDuration)
        return when {
            weight != null && reps != null -> "$weight × $reps"
            weight != null && time != null -> "$weight · $time"
            reps != null -> "$reps ${t.repsUnit}"
            else -> listOfNotNull(weight, time).joinToString(" · ")
        }
    }

    /** "90 kg × 4, 90 × 3": like [values], the unit only on the first weight. */
    fun lastTime(sets: List<SetValues>, type: TrackingType, unit: WeightUnit, language: Language, t: Strings): String =
        sets.mapIndexed { i, v ->
            val text = values(v, type, unit, language, t)
            if (i > 0 && type.usesWeight) text.replace(" ${unit.symbol}", "") else text
        }.filter { it.isNotEmpty() }.joinToString(", ")

    /** "Lateral raise · Set 3 · 30 kg × 6" */
    fun next(ref: Workout.SetRef, unit: WeightUnit, language: Language, t: Strings): String {
        val values = values(ref.set.actual.takeUnless { it == SetValues() } ?: ref.set.target, ref.exercise.exercise.trackingType, unit, language, t)
        return listOf(ref.exercise.exercise.name, t.setNumber(ref.number), values).filter { it.isNotEmpty() }.joinToString(" · ")
    }

    /** Elapsed time "12:05" or "1:02:05". */
    fun elapsed(ms: Long): String {
        val total = (ms / 1000).coerceAtLeast(0)
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        val ss = s.toString().padStart(2, '0')
        return if (h > 0) "$h:${m.toString().padStart(2, '0')}:$ss" else "$m:$ss"
    }

    /** Countdown "1:30"; rounds up so the display reaches 0:00 exactly when the rest ends. */
    fun countdown(remainingMs: Long): String = Numbers.formatDuration(((remainingMs + 999) / 1000).toInt())
}
