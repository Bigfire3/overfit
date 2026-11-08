package app.overfit.chat

import app.overfit.data.WorkoutRecord
import app.overfit.domain.Language
import app.overfit.domain.Numbers
import app.overfit.domain.Profile
import app.overfit.domain.TrackingType
import app.overfit.domain.WeightEntry
import app.overfit.domain.WeightUnit
import app.overfit.domain.WorkoutSet
import app.overfit.domain.aiProfileFacts
import app.overfit.domain.latestWeight
import app.overfit.i18n.ChatStrings
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

data class ContextInput(
    val profile: Profile,
    val weights: List<WeightEntry>,
    val memory: String?,
    val workouts: List<WorkoutRecord>,
    val language: Language,
    val today: LocalDate,
    val timeZone: TimeZone = TimeZone.currentSystemDefault(),
    /** The saved workouts (name and exercises), so the AI can refer to them. */
    val templates: List<app.overfit.domain.Template> = emptyList(),
)

/**
 * The "copy context" text: a compact status for any chat (profile, coach memory, last workouts, progress of the main
 * exercises) plus how to answer with new templates. Works without a server. Texts come from [ChatStrings].
 */
object ChatContext {
    const val RECENT_WORKOUTS = 10
    const val MAIN_EXERCISES = 5
    const val PROGRESS_POINTS = 8
    /** The text stays small enough to paste into any chat. */
    const val MAX_CHARS = 12_000
    const val MAX_TEMPLATES = 15
    /** Marks 0 kg in the AI data (valid: bodyweight, assisted or without added weight). */
    const val BODYWEIGHT = "BW"

    /** [task] null = only the status, without the answer instructions (coach chat, prompts with their own ask). */
    fun build(input: ContextInput, t: ChatStrings, example: String): String = status(input, t, t.ctxTask(example))

    fun status(input: ContextInput, t: ChatStrings, task: String?): String {
        val unit = input.profile.unit
        val finished = input.workouts.filter { it.endedAt != null }.sortedByDescending { it.workout.startedAt }
        val out = StringBuilder()
        out.appendLine(t.ctxIntro).appendLine()

        out.appendLine("## ${t.ctxProfile}")
        aiProfileFacts(input.profile, input.weights, input.today, input.language)?.let { out.appendLine(it) }
        out.appendLine("${t.ctxUnits}: ${if (unit == WeightUnit.KG) "kg" else "lb"} · ${t.ctxToday(input.today.toString())}")
        out.appendLine(t.ctxWeights)
        out.appendLine()

        input.memory?.takeIf { it.isNotBlank() }?.let {
            out.appendLine("## ${t.ctxMemory}").appendLine(it.trim()).appendLine()
        }

        if (input.templates.isNotEmpty()) {
            out.appendLine("## ${t.ctxWorkouts}")
            input.templates.take(MAX_TEMPLATES).forEach { tpl ->
                out.appendLine("- ${tpl.name}: " + tpl.exercises.joinToString(", ") { "${it.exercise.name} ${it.sets.size}×" })
            }
            out.appendLine()
        }

        out.appendLine("## ${t.ctxRecent}")
        if (finished.isEmpty()) out.appendLine(t.ctxNoWorkouts)
        finished.take(RECENT_WORKOUTS).forEach { r -> out.append(workoutBlock(r, t, input)) }
        out.appendLine()

        val progress = progress(finished, unit, input.language, input.timeZone)
        if (progress.isNotEmpty()) {
            out.appendLine("## ${t.ctxProgress}")
            progress.forEach { out.appendLine(it) }
            out.appendLine()
        }
        if (task != null) out.append(task)
        val text = if (task != null) out.toString() else out.toString().trimEnd() + "\n"
        return if (text.length <= MAX_CHARS) text else trim(text, task.orEmpty())
    }

    /** Over the limit: the oldest of the listed workouts are cut first, the task at the end always stays. */
    private fun trim(text: String, task: String): String {
        val keep = task.length + 200
        val head = text.dropLast(task.length)
        val cut = head.take((MAX_CHARS - keep).coerceAtLeast(0))
        return cut.substringBeforeLast('\n') + "\n…\n\n" + task
    }

    private fun workoutBlock(r: WorkoutRecord, t: ChatStrings, input: ContextInput): String =
        workoutBlock(r, t, input.profile.unit, input.language, input.timeZone)

    /** One finished workout: "- date · name · 52 min · feeling" and one line per exercise with its done sets. */
    fun workoutBlock(r: WorkoutRecord, t: ChatStrings, unit: WeightUnit, language: Language, zone: TimeZone): String {
        val w = r.workout
        val title = buildList {
            add(dateOf(w.startedAt, zone))
            add(w.name)
            r.endedAt?.let { add("${((it - w.startedAt) / 60_000).coerceAtLeast(0)} min") }
            r.feeling?.let { add(t.ctxFeeling(it)) }
            if (r.sauna) add(t.ctxSauna)
        }.joinToString(" · ")
        val sb = StringBuilder("- $title\n")
        for (e in w.exercises) {
            val done = e.sets.filter { it.completed }
            // A skipped exercise stays visible with its marker (and whatever was done before skipping).
            if (e.skipped) {
                sb.append("  ").append(app.overfit.coach.WeightRules.name(e.exercise.name, e.exercise.equipment)).append(" (").append(app.overfit.i18n.stringsFor(language).training.skipped).append(')')
                if (done.isNotEmpty()) sb.append(": ").append(done.joinToString(", ") { setText(it, e.exercise.trackingType, unit, language) })
                sb.append('\n')
                continue
            }
            if (done.isEmpty()) continue
            sb.append("  ").append(app.overfit.coach.WeightRules.name(e.exercise.name, e.exercise.equipment)).append(": ")
                .append(done.joinToString(", ") { setText(it, e.exercise.trackingType, unit, language) }).append('\n')
        }
        w.note?.takeIf { it.isNotBlank() }?.let { sb.append("  ").append(it.trim()).append('\n') }
        return sb.toString()
    }

    /**
     * One set for the AI: "70×10", "BW×12" (0 kg = bodyweight/no added weight, a valid entry, see [app.overfit.coach.WeightRules]).
     * [bodyweightMark] false shows "0×12" (UI sub lines).
     */
    fun setText(s: WorkoutSet, type: TrackingType, unit: WeightUnit, language: Language, bodyweightMark: Boolean = true): String {
        val a = s.actual
        val weight = a.weightKg?.takeIf { type.usesWeight }?.let {
            if (bodyweightMark && it == 0.0) BODYWEIGHT else Numbers.formatWeight(it, unit, language)
        }
        return when {
            type.usesReps && weight != null -> "$weight×${a.reps ?: "?"}"
            type.usesReps -> "${a.reps ?: "?"}×"
            type.usesTime && weight == BODYWEIGHT -> "$weight, ${Numbers.formatDuration(a.durationSec ?: 0)}"
            type.usesTime && weight != null -> "$weight ${unit.symbol}, ${Numbers.formatDuration(a.durationSec ?: 0)}"
            else -> Numbers.formatDuration(a.durationSec ?: 0)
        }
    }

    /** Best set per workout for the most frequently trained exercises: "Bench Press: 02.10. 75×8 → 09.10. 77,5×8". */
    fun progress(finished: List<WorkoutRecord>, unit: WeightUnit, language: Language, zone: TimeZone): List<String> {
        data class Point(val at: Long, val text: String, val score: Double)
        val byExercise = LinkedHashMap<String, MutableList<Point>>()
        for (r in finished.sortedBy { it.workout.startedAt }) {
            for (e in r.workout.exercises) {
                val done = e.sets.filter { it.completed }
                if (done.isEmpty()) continue
                val best = done.maxByOrNull { score(it, e.exercise.trackingType) } ?: continue
                byExercise.getOrPut(app.overfit.coach.WeightRules.name(e.exercise.name, e.exercise.equipment)) { mutableListOf() } +=
                    Point(r.workout.startedAt, setText(best, e.exercise.trackingType, unit, language), score(best, e.exercise.trackingType))
            }
        }
        return byExercise.entries
            .sortedByDescending { it.value.size }
            .take(MAIN_EXERCISES)
            .map { (name, points) ->
                val shown = points.takeLast(PROGRESS_POINTS)
                "$name: " + shown.joinToString(" → ") { "${shortDate(it.at, zone, language)} ${it.text}" }
            }
    }

    /** Epley estimated one-rep max for weighted sets; reps or seconds otherwise. */
    fun score(s: WorkoutSet, type: TrackingType): Double {
        val a = s.actual
        val w = a.weightKg ?: 0.0
        return when {
            type.usesWeight && type.usesReps -> w * (1 + (a.reps ?: 0) / 30.0)
            type.usesReps -> (a.reps ?: 0).toDouble()
            else -> (a.durationSec ?: 0).toDouble() + w / 1000
        }
    }

    private fun dateOf(ms: Long, zone: TimeZone): String = Instant.fromEpochMilliseconds(ms).toLocalDateTime(zone).date.toString()

    private fun shortDate(ms: Long, zone: TimeZone, language: Language): String {
        val d = Instant.fromEpochMilliseconds(ms).toLocalDateTime(zone).date
        val day = d.day.toString().padStart(2, '0')
        val month = (d.month.ordinal + 1).toString().padStart(2, '0')
        return if (language == Language.DE) "$day.$month." else "$month/$day"
    }
}
