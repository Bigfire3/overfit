package app.overfit.coach

import app.overfit.domain.Language
import app.overfit.domain.SetValues
import app.overfit.domain.Template
import app.overfit.domain.TrackingType
import app.overfit.domain.WorkoutExercise
import app.overfit.domain.formatNumber
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.JsonObject
import kotlin.math.roundToInt
import kotlin.time.Instant

/** A JPEG for the coach (already downscaled) with a short label like "today" / "2026-09-30". */
class CoachImage(val label: String, val jpeg: ByteArray)

/** An earlier message of a multi-turn conversation (coach chat). */
data class PromptTurn(val fromUser: Boolean, val text: String)

/**
 * Provider-neutral request. [staticSystem] stays byte-identical between requests (rules, output format, exercise
 * list) so providers can cache it; [dynamicSystem] (profile block with today's date) comes after the cache break.
 */
data class CoachPrompt(
    val staticSystem: String,
    val dynamicSystem: String,
    val user: String,
    val images: List<CoachImage> = emptyList(),
    /**
     * Output schema for structured outputs; null = the coach schema. Other AI features (exercise autofill) send their
     * own. Every transport reads it through [outputSchema] / [outputSchemaName] / [outputMaxTokens].
     */
    val schema: JsonObject? = null,
    val schemaName: String? = null,
    val maxTokens: Int? = null,
    /** Earlier turns (coach chat), oldest first; they go before [user]. Empty for one-shot requests. */
    val history: List<PromptTurn> = emptyList(),
    /** True for a coach review (workout evaluation). */
    val review: Boolean = false,
    /** What the request is for (request log, credit backend). Null = derived: [review] → REVIEW, else CHAT. */
    val purpose: AiPurpose? = null,
) {
    val effectivePurpose: AiPurpose get() = purpose ?: if (review) AiPurpose.REVIEW else AiPurpose.CHAT

    val outputSchema: JsonObject get() = schema ?: CoachSchema.schema
    val outputSchemaName: String get() = schemaName ?: CoachSchema.NAME
    val outputMaxTokens: Int get() = maxTokens ?: CoachModels.MAX_TOKENS
}

/** Purpose of an AI request; [wire] goes to the credit backend (`overfit.purpose`) and into the request log. */
enum class AiPurpose(val wire: String) {
    REVIEW("review"), CHAT("chat"), AUTOFILL("autofill"), PLAN("plan"), OTHER("other");

    companion object {
        fun of(wire: String?): AiPurpose = entries.firstOrNull { it.wire == wire } ?: OTHER
    }
}

/** Everything the request is built from. Collected by [CoachService] from database, store and files. */
data class CoachContext(
    val kind: ReviewKind,
    val language: Language,
    /** Coach role and rules (editable in the app). */
    val instructions: String,
    val profileBlock: String,
    val memory: String?,
    /** id → name of the exercises the coach may use. */
    val exerciseList: List<Pair<String, String>>,
    /** The workout to review (SESSION). */
    val current: SessionRecord? = null,
    /** The template the next session will be based on (SESSION with template). */
    val template: Template? = null,
    /** Earlier sessions of the same template, newest first. */
    val sameTemplate: List<SessionRecord> = emptyList(),
    /** The latest other session (any template), if not already in [sameTemplate]. */
    val lastOverall: SessionRecord? = null,
    /** Sessions of the last weeks for the progress of the main exercises, newest first. */
    val recent: List<SessionRecord> = emptyList(),
    val photoToday: Boolean = false,
    /** Date of the comparison photo (≥ 4 weeks old), if one is attached. */
    val comparisonPhotoDate: String? = null,
    /** Today vs. last time per exercise, computed by the app (SESSION). */
    val comparisons: List<ExerciseComparison> = emptyList(),
    val timeZone: TimeZone = TimeZone.currentSystemDefault(),
)

object CoachPromptBuilder {

    fun build(ctx: CoachContext, images: List<CoachImage> = emptyList()): CoachPrompt {
        val static = buildString {
            appendLine(ctx.instructions.trim())
            appendLine()
            appendLine(OUTPUT_RULES)
            appendLine(WeightRules.TEXT)
            appendLine(SAUNA_NOTE)
            appendLine("Example of the shape: ${CoachSchema.example}")
            appendLine()
            appendLine("Exercise IDs you may use in next_session (id | name):")
            ctx.exerciseList.forEach { (id, name) -> appendLine("$id | $name") }
        }.trimEnd()
        val dynamic = buildString {
            appendLine("Profile:")
            appendLine(ctx.profileBlock)
            append("Write every text field in ${if (ctx.language == Language.DE) "German" else "English"}.")
        }
        return CoachPrompt(static, dynamic, userMessage(ctx), images, review = true)
    }

    fun userMessage(ctx: CoachContext): String = buildString {
        appendLine("## Coach memory")
        appendLine(ctx.memory?.takeIf { it.isNotBlank() } ?: "(empty – nothing known yet)")
        appendLine()
        when (ctx.kind) {
            ReviewKind.SESSION -> {
                val current = ctx.current
                appendLine("## Task")
                appendLine(
                    "Review today's workout. Then suggest the next session" +
                        if (ctx.template != null) " of the template \"${ctx.template.name}\" as next_session (complete list of exercises in order)."
                        else " (this was a free workout without template; next_session may suggest a sensible next workout or be null).",
                )
                if (ctx.photoToday) appendLine("A body photo of today is attached; give photo_feedback.")
                else appendLine("No photo: photo_feedback must be null.")
                ctx.comparisonPhotoDate?.let { appendLine("The second photo is the comparison photo from $it.") }
                appendLine()
                if (current != null) {
                    appendLine("## Today's workout")
                    appendLine(formatSession(current, ctx.timeZone, detailed = true))
                }
                if (ctx.comparisons.isNotEmpty()) {
                    appendLine("## Per exercise: today vs. last time (computed by the app, use these numbers)")
                    ExerciseReviews.promptLines(ctx.comparisons).forEach { appendLine(it) }
                    appendLine()
                }
                ctx.template?.let {
                    appendLine("## Current template \"${it.name}\" (plan for the next session)")
                    appendLine(formatTemplate(it))
                }
                if (ctx.sameTemplate.isNotEmpty()) {
                    appendLine("## Earlier sessions of this template (newest first)")
                    ctx.sameTemplate.forEach { appendLine(formatSession(it, ctx.timeZone, detailed = false)) }
                }
                ctx.lastOverall?.let {
                    appendLine("## Latest other workout")
                    appendLine(formatSession(it, ctx.timeZone, detailed = false))
                }
            }
            ReviewKind.PROGRESS -> {
                appendLine("## Task")
                appendLine(
                    "Assess the progress of the last weeks: what improves, what stagnates, consistency, and what to " +
                        "change. rating = overall progress. next_session must be null. photo_feedback only if photos are attached.",
                )
                if (ctx.photoToday) appendLine("The first photo is the latest progress photo.")
                ctx.comparisonPhotoDate?.let { appendLine("The second photo is the comparison photo from $it.") }
                appendLine()
                appendLine("## Workouts of the last weeks (newest first)")
                if (ctx.recent.isEmpty()) appendLine("(none)")
                ctx.recent.forEach { appendLine(formatSession(it, ctx.timeZone, detailed = false)) }
            }
        }
        val progress = mainExerciseProgress(ctx.recent, ctx.timeZone)
        if (progress.isNotEmpty()) {
            appendLine("## Progress of the main exercises (best set per session, oldest → newest)")
            progress.forEach { appendLine(it) }
        }
    }.trimEnd()

    /** Compact text of a session. [detailed] adds planned values, set times and notes. */
    fun formatSession(s: SessionRecord, tz: TimeZone, detailed: Boolean): String = buildString {
        val start = Instant.fromEpochMilliseconds(s.startedAt).toLocalDateTime(tz)
        val head = listOfNotNull(
            "\"${s.name}\" ${start.date} ${pad(start.hour)}:${pad(start.minute)}",
            s.durationMin?.let { "$it min" },
            s.feeling?.let { "feeling $it/5" },
            "sauna: yes".takeIf { s.sauna },
        )
        appendLine(head.joinToString(", "))
        s.note?.takeIf { it.isNotBlank() }?.let { appendLine("Note: ${it.trim()}") }
        val completedTimes = s.exercises.flatMap { e -> e.sets.mapNotNull { it.completedAt } }.sorted()
        s.exercises.forEachIndexed { i, e ->
            val origin = when {
                e.replacedExerciseId != null -> " (replaced ${e.replacedExerciseId})"
                e.origin == app.overfit.domain.ExerciseOrigin.ADDED -> " (added)"
                else -> ""
            } + if (e.skipped) " (skipped today by the user)" else ""
            // A skipped exercise: only what was done before skipping counts; its open sets are not "not done".
            if (e.skipped) {
                val done = e.sets.filter { it.completed }.joinToString(", ") { formatSet(it.actual, e.exercise.trackingType) }
                appendLine("${i + 1}. ${e.exercise.name} [${e.exercise.id}]$origin${if (done.isEmpty()) "" else ": $done"}")
                return@forEachIndexed
            }
            val type = e.exercise.trackingType
            if (!detailed) {
                val done = e.sets.filter { it.completed }.joinToString(", ") { formatSet(it.actual, type) }
                appendLine("${i + 1}. ${WeightRules.name(e.exercise.name, e.exercise.equipment)} [${e.exercise.id}]$origin: ${done.ifEmpty { "no sets done" }}")
                return@forEachIndexed
            }
            appendLine("${i + 1}. ${WeightRules.name(e.exercise.name, e.exercise.equipment)} [${e.exercise.id}]$origin${e.restSec?.let { ", planned rest ${it}s" } ?: ""}")
            e.sets.forEachIndexed { si, set ->
                val planned = formatSet(set.target, type)
                val line = if (set.completed) {
                    val gap = set.completedAt?.let { at ->
                        val prev = completedTimes.lastOrNull { it < at }
                        prev?.let { " (+${formatGap(at - it)})" }
                    }.orEmpty()
                    "done ${formatSet(set.actual, type)}$gap"
                } else "not done"
                appendLine("   S${si + 1}: plan $planned → $line")
            }
            if (e.note.isNotBlank()) appendLine("   note: ${e.note.trim()}")
        }
    }.trimEnd()

    fun formatTemplate(t: Template): String = t.exercises.mapIndexed { i, e ->
        val type = e.exercise.trackingType
        val sets = e.sets.joinToString(", ") { formatSet(it, type) }
        "${i + 1}. ${WeightRules.name(e.exercise.name, e.exercise.equipment)} [${e.exercise.id}]${e.restSec?.let { " rest ${it}s" } ?: ""}: $sets"
    }.joinToString("\n")

    /** "80kg×8", "12 reps", "45s", "-20kg×8" (assisted), "20kg×45s". */
    fun formatSet(v: SetValues, type: TrackingType): String {
        // 0 kg = bodyweight / no added weight (a valid entry, see WeightRules).
        val w = v.weightKg?.takeIf { it != 0.0 }?.let { "${formatNumber(it, Language.EN)}kg" }
        return when (type) {
            TrackingType.WEIGHT_REPS -> "${w ?: "BW"}×${v.reps ?: "?"}"
            TrackingType.REPS -> "${v.reps ?: "?"} reps"
            TrackingType.TIME -> "${v.durationSec ?: "?"}s"
            TrackingType.WEIGHT_TIME -> "${w ?: "BW"}×${v.durationSec ?: "?"}s"
        }
    }

    /**
     * Best set per session for the most frequent exercises (max. [MAX_EXERCISES] exercises, last [MAX_POINTS]
     * sessions each). Weight exercises are compared by estimated 1RM (Epley), others by reps or time.
     */
    fun mainExerciseProgress(sessions: List<SessionRecord>, tz: TimeZone): List<String> {
        data class Point(val date: String, val set: SetValues, val score: Double)
        val byExercise = linkedMapOf<String, Pair<WorkoutExercise, MutableList<Point>>>()
        sessions.sortedBy { it.startedAt }.forEach { s ->
            val date = Instant.fromEpochMilliseconds(s.startedAt).toLocalDateTime(tz).date.toString()
            s.exercises.forEach { e ->
                val best = e.sets.filter { it.completed }.maxByOrNull { score(it.actual, e.exercise.trackingType) } ?: return@forEach
                val entry = byExercise.getOrPut(e.exercise.id) { e to mutableListOf() }
                entry.second += Point(date, best.actual, score(best.actual, e.exercise.trackingType))
            }
        }
        return byExercise.values
            .filter { it.second.size >= 2 }
            .sortedByDescending { it.second.size }
            .take(MAX_EXERCISES)
            .map { (e, points) ->
                val type = e.exercise.trackingType
                val list = points.takeLast(MAX_POINTS).joinToString(" · ") { p ->
                    val e1rm = if (type == TrackingType.WEIGHT_REPS && (p.set.weightKg ?: 0.0) > 0) " (e1RM ${p.score.roundToInt()})" else ""
                    "${p.date} ${formatSet(p.set, type)}$e1rm"
                }
                "${WeightRules.name(e.exercise.name, e.exercise.equipment)} [${e.exercise.id}]: $list"
            }
    }

    private fun score(v: SetValues, type: TrackingType): Double = when (type) {
        TrackingType.WEIGHT_REPS -> {
            val w = v.weightKg ?: 0.0
            val r = v.reps ?: 0
            if (w > 0) w * (1 + r / 30.0) else r.toDouble() + w / 100.0
        }
        TrackingType.REPS -> (v.reps ?: 0).toDouble()
        TrackingType.TIME -> (v.durationSec ?: 0).toDouble()
        TrackingType.WEIGHT_TIME -> (v.weightKg ?: 0.0) * 1000 + (v.durationSec ?: 0)
    }

    private fun pad(n: Int) = n.toString().padStart(2, '0')
    private fun formatGap(ms: Long): String {
        val sec = (ms / 1000).toInt()
        return "${sec / 60}:${pad(sec % 60)}"
    }

    private const val MAX_EXERCISES = 8
    private const val MAX_POINTS = 8

    /** Explains the "sauna: yes" marker in workout headers (static, so the cache prefix stays stable). */
    const val SAUNA_NOTE = "A workout line ending in \"sauna: yes\" means the user went to the sauna right after that workout. " +
        "Take it into account for recovery and hydration; it is not training volume."

    const val OUTPUT_RULES = """Answer with exactly one JSON object and nothing else (no Markdown, no text around it). Fields:
- rating: integer 1–10 for the workout (for a progress review: for the progress).
- summary: workout review: 1–2 sentences overall verdict (details go into exercise_reviews); progress review: 2–4 sentences.
- exercise_reviews: workout review: one entry per exercise of today's workout (same exercise IDs, same order). verdict = better, same or worse compared to last time (start from "by the numbers", you may correct it with a reason, e.g. more reps at the same weight); first time = same. comment: one short sentence why. next: one concrete working set for next time (weight_kg in kg and reps, or duration_sec for timed exercises; unused values null), or null if it should stay unchanged. Progress review: empty list.
- positives, improvements, tips: short bullet sentences (0–4 each). In a workout review at most 2 each and only points that are not about a single exercise.
- photo_feedback: only if a photo is attached, otherwise null. Factual and constructive, focus on visible changes compared to the comparison photo. Never judge the person, only the training progress.
- next_session: the complete next session in order. Use only exercise IDs from the list below. An exercise that is not in the current plan gets is_new = true and a reason. Sets with target values (weight_kg in kg, never negative (0 or more); reps; duration_sec for timed exercises; unused values null). rest_sec in seconds or null. reason: one short sentence per changed exercise, otherwise null. Keep unchanged exercises unchanged.
- memory: the complete revised coach memory (max. about 1500 words): goals, progress per main exercise, plateaus, preferences, complaints, what works. Keep everything still relevant, add what you learned today."""
}
