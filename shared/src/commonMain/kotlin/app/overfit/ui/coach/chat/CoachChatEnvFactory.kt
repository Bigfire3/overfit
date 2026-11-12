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
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
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
    fun training(row: HistoryRow) = ChatAttachment.Training(row.id, row.name, row.startedAt)
    // Model chip and pricing follow the access and the chosen model live. The price table comes from the backend: it is
    // only asked while credit is the chosen access (own key: only the config already on the device, OwnKeyPrivacyTest).
    val access = kotlinx.coroutines.flow.combine(store.state, deps.coach.settings.state) { s, settings -> s to settings }
    // Credit: the model name comes from the backend's /v1/config (tiers can point to any vendor); the built-in name of
    // the tier is only the fallback while the config is unknown.
    // mapLatest: a slow config request never holds back a later change (e.g. switching to the own key).
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val chip = access
        .mapLatest { (s, settings) ->
            val fuel = s.providerId == app.overfit.ai.ProviderId.FUEL
            val config = if (fuel) deps.providers.fuel.config() else null
            val fallback = app.overfit.ui.coach.modelChipLabel(s, settings, deps.providers.fuel.available, app.overfit.i18n.coachSettingsStringsFor(s.language))
            val tier = config?.tiers?.get(settings.model(app.overfit.ai.ProviderId.FUEL))
            val name = tier?.let { it.displayName.ifEmpty { app.overfit.fuel.modelShortName(it.model) } }?.takeIf { it.isNotBlank() }
            val pricing = when {
                fuel && deps.providers.fuel.available -> app.overfit.coach.ChatPricing.credit(config, settings.model(app.overfit.ai.ProviderId.FUEL))
                !fuel && s.hasApiKey -> app.overfit.coach.ChatPricing.ownKey(deps.providers.fuel.storedConfig(), settings.model(s.providerId))
                else -> app.overfit.coach.ChatPricing.Demo
            }
            (name ?: fallback) to pricing
        }
        .stateIn(deps.data.scope, kotlinx.coroutines.flow.SharingStarted.Eagerly, "" to app.overfit.coach.ChatPricing.Unknown)
    val modelLabel = chip.map { it.first }.stateIn(deps.data.scope, kotlinx.coroutines.flow.SharingStarted.Eagerly, "")
    val pricing = chip.map { it.second }.stateIn(deps.data.scope, kotlinx.coroutines.flow.SharingStarted.Eagerly, app.overfit.coach.ChatPricing.Unknown)
    return CoachChatEnv(
        chat = deps.coachChat,
        prefs = deps.data.coachChatPrefs,
        lastTraining = deps.data.workouts.history.map { list -> list.firstOrNull()?.let(::training) },
        trainings = deps.data.workouts.history.map { list -> list.map(::training) },
        plans = deps.data.templates.templates.map { list -> list.map { ChatAttachment.Plan(it.id, it.name) } },
        exercises = {
            val s = store.state.value
            data class Best(val id: String, val name: String, val text: String, val score: Double)
            val bests = LinkedHashMap<String, Best>()
            deps.data.workouts.allRecords().filter { it.endedAt != null }.sortedByDescending { it.workout.startedAt }.forEach { r ->
                r.workout.exercises.forEach { e ->
                    val type = e.exercise.trackingType
                    val best = e.sets.filter { it.completed }.maxByOrNull { ChatContext.score(it, type) } ?: return@forEach
                    val score = ChatContext.score(best, type)
                    val old = bests[e.exercise.id]
                    if (old == null || score > old.score) {
                        bests[e.exercise.id] = Best(
                            e.exercise.id, e.exercise.name, ChatContext.setText(best, type, s.profile.unit, s.language, bodyweightMark = false), score)
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
        modelLabel = modelLabel,
        pricing = pricing,
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

/** Card/chip text of an attachment: a training gets "· heute" / "· gestern" / the date, computed when shown. */
@androidx.compose.runtime.Composable
internal fun attachmentText(a: ChatAttachment): String {
    if (a !is ChatAttachment.Training || a.startedAt == null) return a.label
    val language = app.overfit.i18n.LocalFuelStrings.current.language
    val today = kotlin.time.Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    return a.label + " · " + dayLabel(a.startedAt, today, coachChatStringsFor(language), language)
}
