package app.overfit.coach

import app.overfit.chat.ChatContext
import app.overfit.data.WorkoutRecord
import app.overfit.domain.Language
import app.overfit.domain.Numbers
import app.overfit.domain.Template
import app.overfit.domain.WeightUnit
import app.overfit.i18n.ChatStrings
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Instant

/**
 * Something the user attached to a chat message ("+" sheet, context chips). Stored with the question (JSON list in
 * `coach_chat_message.attachments`), so the chat shows it as a small card and the history keeps it. Photos are sent as
 * images, everything else as text in the system prompt. [label] is a snapshot for the card (the item may be renamed or
 * deleted later).
 */
@Serializable
sealed interface ChatAttachment {
    val label: String

    /**
     * A finished training from the calendar. [label] is its name; with [startedAt] the UI adds "· today" / the date at
     * display time, so an old chat never keeps saying "today".
     */
    @Serializable @SerialName("training")
    data class Training(val sessionId: String, override val label: String = "", val startedAt: Long? = null) : ChatAttachment

    /** A saved workout (plan). */
    @Serializable @SerialName("plan")
    data class Plan(val templateId: Long, override val label: String = "") : ChatAttachment

    /** Trainings of the last [weeks] weeks, summarised. */
    @Serializable @SerialName("history")
    data class History(val weeks: Int = 4, override val label: String = "") : ChatAttachment

    /** One exercise with its best values and its progress. */
    @Serializable @SerialName("exercise")
    data class Exercise(val exerciseId: String, override val label: String = "") : ChatAttachment

    /** A photo from the photo diary. */
    @Serializable @SerialName("photo")
    data class Photo(val photoId: String, override val label: String = "") : ChatAttachment

    companion object {
        private val json = Json { ignoreUnknownKeys = true; classDiscriminator = "type" }

        fun encode(list: List<ChatAttachment>): String? =
            list.takeIf { it.isNotEmpty() }?.let { json.encodeToString(kotlinx.serialization.builtins.ListSerializer(serializer()), it) }

        /** Unknown or broken entries are dropped (never crash the chat over an attachment). */
        fun decode(text: String?): List<ChatAttachment> {
            if (text.isNullOrBlank()) return emptyList()
            return runCatching { json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(serializer()), text) }.getOrDefault(emptyList())
        }
    }
}

/** Data the attachment texts are built from (loaded fresh for every question). */
data class AttachmentSources(
    val workouts: List<WorkoutRecord>,
    val templates: List<Template>,
    val unit: WeightUnit,
    val language: Language,
    val now: Long,
    val zone: TimeZone = TimeZone.currentSystemDefault(),
)

/**
 * Turns attachments into text for the system prompt. Only what is attached goes along (the base instruction says:
 * use only the data sent along). Photos are not text: they travel as images.
 */
object ChatAttachmentText {
    const val HISTORY_MAX_TRAININGS = 30
    const val EXERCISE_POINTS = 10
    private const val WEEK_MS = 7L * 24 * 60 * 60 * 1000

    fun build(attachments: List<ChatAttachment>, src: AttachmentSources, t: ChatStrings): String =
        attachments.mapNotNull { block(it, src, t) }.joinToString("\n\n")

    fun block(a: ChatAttachment, src: AttachmentSources, t: ChatStrings): String? = when (a) {
        is ChatAttachment.Training -> training(a, src, t)
        is ChatAttachment.Plan -> plan(a, src)
        is ChatAttachment.History -> history(a, src, t)
        is ChatAttachment.Exercise -> exercise(a, src)
        is ChatAttachment.Photo -> null
    }

    private fun finished(src: AttachmentSources) =
        src.workouts.filter { it.endedAt != null }.sortedByDescending { it.workout.startedAt }

    private fun training(a: ChatAttachment.Training, src: AttachmentSources, t: ChatStrings): String {
        val r = src.workouts.firstOrNull { it.workout.id == a.sessionId }
            ?: return "## Attached training\n(${a.label.ifBlank { a.sessionId }}: no longer in the calendar)"
        return "## Attached training\n" + ChatContext.workoutBlock(r, t, src.unit, src.language, src.zone).trimEnd()
    }

    private fun plan(a: ChatAttachment.Plan, src: AttachmentSources): String {
        val tpl = src.templates.firstOrNull { it.id == a.templateId }
            ?: return "## Attached workout (plan)\n(${a.label.ifBlank { a.templateId.toString() }}: deleted)"
        val sb = StringBuilder("## Attached workout (plan): ${tpl.name}\n")
        tpl.note?.takeIf { it.isNotBlank() }?.let { sb.append(it.trim()).append('\n') }
        tpl.exercises.forEach { e ->
            val type = e.exercise.trackingType
            val sets = e.sets.joinToString(", ") { s ->
                val weight = s.weightKg?.takeIf { type.usesWeight }?.let {
                    if (it == 0.0) ChatContext.BODYWEIGHT else Numbers.formatWeight(it, src.unit, src.language)
                }
                when {
                    type.usesReps && weight != null -> "$weight×${s.reps ?: "?"}"
                    type.usesReps -> "${s.reps ?: "?"}×"
                    else -> Numbers.formatDuration(s.durationSec ?: 0)
                }
            }
            sb.append("- ${WeightRules.name(e.exercise.name, e.exercise.equipment)} [${e.exercise.id}]: ${e.sets.size} sets ($sets)")
            e.restSec?.let { sb.append(", rest $it s") }
            e.note?.takeIf { it.isNotBlank() }?.let { sb.append(" – ").append(it.trim()) }
            sb.append('\n')
        }
        return sb.toString().trimEnd()
    }

    private fun history(a: ChatAttachment.History, src: AttachmentSources, t: ChatStrings): String {
        val since = src.now - a.weeks * WEEK_MS
        val list = finished(src).filter { it.workout.startedAt >= since }
        val sb = StringBuilder("## Attached history: last ${a.weeks} weeks\n")
        if (list.isEmpty()) return sb.append("No finished trainings in this period.").toString()
        val minutes = list.sumOf { r -> ((r.endedAt!! - r.workout.startedAt) / 60_000).coerceAtLeast(0) }
        val sets = list.sumOf { r -> r.workout.exercises.sumOf { e -> e.sets.count { it.completed } } }
        sb.append("${list.size} trainings, ${"%.1f".fmt(list.size / a.weeks.toDouble())} per week, $sets sets, $minutes min in total\n")
        list.take(HISTORY_MAX_TRAININGS).forEach { sb.append(ChatContext.workoutBlock(it, t, src.unit, src.language, src.zone)) }
        val progress = ChatContext.progress(list, src.unit, src.language, src.zone)
        if (progress.isNotEmpty()) {
            sb.append("Progress of the main exercises:\n")
            progress.forEach { sb.append(it).append('\n') }
        }
        return sb.toString().trimEnd()
    }

    private fun exercise(a: ChatAttachment.Exercise, src: AttachmentSources): String {
        data class Point(val at: Long, val set: app.overfit.domain.WorkoutSet, val type: app.overfit.domain.TrackingType)
        val points = mutableListOf<Point>()
        var name = a.label
        var equipment: app.overfit.domain.Equipment? = null
        for (r in finished(src).sortedBy { it.workout.startedAt }) {
            for (e in r.workout.exercises) {
                if (e.exercise.id != a.exerciseId) continue
                name = e.exercise.name
                equipment = e.exercise.equipment
                val best = e.sets.filter { it.completed }.maxByOrNull { ChatContext.score(it, e.exercise.trackingType) } ?: continue
                points += Point(r.workout.startedAt, best, e.exercise.trackingType)
            }
        }
        val sb = StringBuilder("## Attached exercise: ${WeightRules.name(name, equipment)} [${a.exerciseId}]\n")
        if (points.isEmpty()) return sb.append("Not trained yet.").toString()
        val type = points.first().type
        val best = points.maxBy { ChatContext.score(it.set, it.type) }
        sb.append("Trained ${points.size} times. Best set: ${ChatContext.setText(best.set, type, src.unit, src.language)} (${date(best.at, src.zone)})")
        if (type.usesWeight && type.usesReps) {
            val e1rm = ChatContext.score(best.set, type)
            if (e1rm > 0) sb.append(", estimated 1RM ${Numbers.formatWeight(e1rm, src.unit, src.language)} ${src.unit.symbol}")
            points.mapNotNull { it.set.actual.weightKg }.maxOrNull()?.let { sb.append(", heaviest ${Numbers.formatWeight(it, src.unit, src.language)} ${src.unit.symbol}") }
        }
        sb.append('\n')
        sb.append("History (best set per training): ")
        sb.append(points.takeLast(EXERCISE_POINTS).joinToString(" → ") { "${date(it.at, src.zone)} ${ChatContext.setText(it.set, it.type, src.unit, src.language)}" })
        return sb.toString()
    }

    private fun date(ms: Long, zone: TimeZone): String = Instant.fromEpochMilliseconds(ms).toLocalDateTime(zone).date.toString()

    private fun String.fmt(value: Double): String {
        val r = kotlin.math.round(value * 10) / 10
        return if (r == r.toLong().toDouble()) r.toLong().toString() else r.toString()
    }
}
