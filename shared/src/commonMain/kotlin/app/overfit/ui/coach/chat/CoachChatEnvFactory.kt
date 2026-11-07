package app.overfit.ui.coach.chat

import app.overfit.chat.ChatContext
import app.overfit.coach.ChatAttachment
import app.overfit.coach.CoachAccess
import app.overfit.data.db.HistoryRow
import app.overfit.domain.formatWeight
import app.overfit.domain.latestWeight
import app.overfit.i18n.CoachChatStrings
import app.overfit.i18n.coachChatStringsFor
import app.overfit.i18n.stringsFor
import app.overfit.ui.AppDependencies
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * Simple, correct values from [deps] for the Coach tab chat. Model chip and cost estimate are plain snapshots here
 * (the real wiring with the model sheet replaces them).
 */
fun createCoachChatEnv(deps: AppDependencies): CoachChatEnv {
    val store = deps.store
    fun strings(): CoachChatStrings = coachChatStringsFor(store.state.value.language)
    fun label(row: HistoryRow): String = row.name + " · " + dayLabel(row.startedAt, deps.today(), strings(), store.state.value.language)
    val access = deps.coach.access()
    val model = (access as? CoachAccess.Ready)?.let { app.overfit.fuel.modelLabel(deps.coach.settings.state.value.model(it.providerId)) }
    return CoachChatEnv(
        chat = deps.coachChat,
        prefs = deps.data.coachChatPrefs,
        lastTraining = deps.data.workouts.history.map { list -> list.firstOrNull()?.let { ChatAttachment.Training(it.id, label(it)) } },
        trainings = deps.data.workouts.history.map { list -> list.map { ChatAttachment.Training(it.id, label(it)) } },
        plans = deps.data.templates.templates.map { list -> list.map { ChatAttachment.Plan(it.id, it.name) } },
        exercises = {
            val s = store.state.value
            data class Best(val id: String, val name: String, val text: String, val score: Double, val last: Long)
            val bests = LinkedHashMap<String, Best>()
            deps.data.workouts.allRecords().filter { it.endedAt != null }.sortedByDescending { it.workout.startedAt }.forEach { r ->
                r.workout.exercises.forEach { e ->
                    val type = e.exercise.trackingType
                    val best = e.sets.filter { it.completed }.maxByOrNull { ChatContext.score(it, type) } ?: return@forEach
                    val score = ChatContext.score(best, type)
                    val old = bests[e.exercise.id]
                    if (old == null || score > old.score) {
                        bests[e.exercise.id] = Best(
                            e.exercise.id, e.exercise.name, ChatContext.setText(best, type, s.profile.unit, s.language), score,
                            old?.last ?: r.workout.startedAt,
                        )
                    }
                }
            }
            bests.values.map { ChatAttachment.Exercise(it.id, it.name) to strings().bestValue(it.text) }
        },
        photos = deps.data.workouts.photos,
        templateNames = deps.data.templates.templates.map { list -> list.map { it.name }.toSet() },
        latestWeight = {
            val s = store.state.value
            latestWeight(s.weights)?.let { w ->
                val date = runCatching { LocalDate.parse(w.date) }.getOrNull()
                val weight = formatWeight(w.kg, s.profile.unit, s.language)
                if (date == null) weight else strings().weightAsOf(weight, stringsFor(s.language).date(date))
            }
        },
        modelLabel = MutableStateFlow(model ?: strings().noAccess),
        costEstimate = MutableStateFlow(null),
    )
}

/** "heute", "gestern" or the date. */
internal fun dayLabel(ms: Long, today: LocalDate, s: CoachChatStrings, language: app.overfit.domain.Language): String {
    val day = Instant.fromEpochMilliseconds(ms).toLocalDateTime(TimeZone.currentSystemDefault()).date
    return when (day) {
        today -> s.today
        today.minus(DatePeriod(days = 1)) -> s.yesterday
        else -> stringsFor(language).date(day)
    }
}
