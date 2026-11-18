package app.overfit.ui.coach

import kotlinx.coroutines.flow.onEach
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.overfit.coach.CoachAccess
import app.overfit.coach.CoachDemo
import app.overfit.coach.CoachErrorKind
import app.overfit.coach.CoachReview
import app.overfit.coach.CoachService
import app.overfit.coach.ExerciseComparison
import app.overfit.coach.PlanChange
import app.overfit.coach.PlanDiff
import app.overfit.coach.PlanDiffs
import app.overfit.coach.PlanStatus
import app.overfit.coach.ReviewItem
import app.overfit.coach.ReviewKind
import app.overfit.coach.ReviewStatus
import app.overfit.domain.Language
import app.overfit.domain.PlannedSet
import app.overfit.domain.TrackingType
import app.overfit.domain.Template
import app.overfit.domain.WeightUnit
import app.overfit.fuel.formatEuro
import app.overfit.i18n.CoachStrings
import app.overfit.i18n.LocalCoachStrings
import app.overfit.i18n.LocalStrings
import app.overfit.ui.components.ConfirmButton
import app.overfit.ui.components.textInputArea
import app.overfit.ui.components.BackHeader
import app.overfit.ui.components.Hairline
import app.overfit.ui.components.Icon
import app.overfit.ui.components.LineIcon
import app.overfit.ui.components.ListRow
import app.overfit.ui.components.Hint
import app.overfit.ui.components.Panel
import app.overfit.ui.components.PanelItem
import app.overfit.ui.components.PanelSection
import app.overfit.ui.components.PrimaryButton
import app.overfit.ui.components.Screen
import app.overfit.ui.components.SectionTitle
import app.overfit.ui.components.Text
import app.overfit.ui.components.SmallButton
import app.overfit.ui.components.Title
import app.overfit.ui.components.Tone
import app.overfit.ui.components.pressableRow
import app.overfit.ui.components.Appear
import app.overfit.ui.components.Reveal
import app.overfit.ui.components.RevealInRow
import app.overfit.ui.components.RevealNotNull
import app.overfit.ui.components.Swap
import app.overfit.ui.components.motionColor
import app.overfit.ui.components.rememberCountUp
import app.overfit.ui.components.rememberHaptics
import app.overfit.ui.theme.IconSize
import app.overfit.ui.theme.Motion
import kotlin.math.roundToInt
import app.overfit.ui.theme.Space
import app.overfit.ui.theme.Theme
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/** Navigation callbacks the coach screens need from the app. */
class CoachNav(
    val back: () -> Unit,
    val connect: () -> Unit,
    val fuel: () -> Unit,
    val openReview: (String) -> Unit,
    val demo: (String) -> Unit,
    val memory: () -> Unit,
    val settings: () -> Unit,
    val openTemplate: (Long) -> Unit,
)

// ---------------------------------------------------------------------------------------------
// Coach home: review last workout, assess progress, list of reviews

@Composable
fun CoachHomeScreen(coach: CoachService, nav: CoachNav) {
    val c = LocalCoachStrings.current
    val t = LocalStrings.current
    // null until the database answered: no "no reviews yet" flash before the list arrives.
    val loaded by coach.reviews.collectAsState(initial = null)
    val reviews = loaded.orEmpty()
    Screen(scroll = true, header = { BackHeader(t.back, nav.back) }) {
        Title(c.coachTitle)
        Spacer(Modifier.height(Space.l))
        // Archive of the reviews before the coach chat (8.11.: no AI buttons here any more, new reviews go through the chat).
        // Loading → empty → list fades over in place instead of popping (the list arrives a moment after the screen).
        val listState = when {
            loaded == null -> 0
            reviews.isEmpty() -> 1
            else -> 2
        }
        Swap(listState, Modifier.fillMaxWidth()) { state ->
            Column(Modifier.fillMaxWidth()) {
                when (state) {
                    0 -> SectionTitle(c.reviews)
                    1 -> {
                        SectionTitle(c.reviews)
                        Text(c.reviewsEmpty, tone = Tone.Muted)
                    }
                    else -> PanelSection(c.reviews) {
                        reviews.forEach { item ->
                            val title = (if (item.kind == ReviewKind.PROGRESS) c.progressTitle else c.reviewTitle) + " · " + dateText(item.createdAt)
                            val value = when (item.status) {
                                ReviewStatus.DONE -> item.review?.rating?.let(c.rating) ?: "✓"
                                ReviewStatus.QUEUED -> c.statusQueued
                                ReviewStatus.SENDING -> c.statusSending
                                ReviewStatus.FAILED -> c.statusFailed
                            }
                            ListRow(title, value, onClick = { nav.openReview(item.id) })
                        }
                    }
                }
            }
        }

        PanelSection(t.coach) {
            ListRow(t.coachMemory, onClick = nav.memory)
            ListRow(c.settingsTitle, onClick = nav.settings)
        }
    }
}

@Composable
private fun RowWithHint(label: String, hint: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(Theme.colors.surface).pressableRow(onClick).padding(Space.m)) {
        Text(label)
        Text(hint, style = Theme.type.caption, tone = Tone.Muted)
    }
}

@Composable
private fun dateText(ms: Long): String =
    LocalStrings.current.date(Instant.fromEpochMilliseconds(ms).toLocalDateTime(TimeZone.currentSystemDefault()).date)

// ---------------------------------------------------------------------------------------------
// Review of a workout: send, wait, result, plan


@Composable
fun CoachReviewScreen(coach: CoachService, reviewId: String, unit: WeightUnit, language: Language, nav: CoachNav) {
    // Tells "still loading" (null before the first emission) apart from "gone" (null emitted): a deleted review
    // must not show "the coach is looking at your training" forever.
    var answered by remember(reviewId) { mutableStateOf(false) }
    val item by remember(reviewId) { coach.review(reviewId).onEach { answered = true } }.collectAsState(initial = null)
    if (answered && item == null) {
        val c = LocalCoachStrings.current
        Screen(header = { BackHeader(LocalStrings.current.back, nav.back) }) {
            Title(c.reviewTitle)
            Spacer(Modifier.height(Space.l))
            Text(c.reviewMissing, tone = Tone.Muted)
        }
        return
    }
    ReviewScreen(coach, item, unit, language, nav, onDemo = item?.sessionId?.let { s -> { nav.demo(s) } })
}

/** State of the plan section: the template and the diff (null while loading or without plan). */
private data class PlanState(val template: Template?, val diff: PlanDiff?)

@Composable
private fun ReviewScreen(
    coach: CoachService,
    item: ReviewItem?,
    unit: WeightUnit,
    language: Language,
    nav: CoachNav,
    onDemo: (() -> Unit)?,
) {
    val c = LocalCoachStrings.current
    var plan by remember(item?.id) { mutableStateOf<PlanState?>(null) }
    LaunchedEffect(item?.id, item?.review != null) {
        if (item?.review?.nextSession != null) {
            // A failing diff ends the "…" state like a missing plan instead of leaving it forever.
            plan = runCatching { coach.planDiff(item) }.getOrNull()?.let { PlanState(it.first, it.second) } ?: PlanState(null, null)
        }
    }
    var message by remember(item?.id) { mutableStateOf<String?>(null) }
    val sessionId = item?.sessionId?.takeIf { item.kind == ReviewKind.SESSION }
    // Keyed on the session only: when the answer arrives the old numbers stay and the coach's words are filled in
    // (ExerciseBreakdown reveals them), instead of "Per exercise" vanishing for a moment and jumping back.
    var comparisons by remember(sessionId) { mutableStateOf<List<ExerciseComparison>>(emptyList()) }
    LaunchedEffect(sessionId, item?.review) {
        if (sessionId != null) comparisons = coach.comparisons(sessionId, item?.review?.exerciseReviews.orEmpty())
    }
    ReviewContent(
        item = item,
        plan = plan,
        comparisons = comparisons,
        unit = unit,
        language = language,
        nav = nav,
        onDemo = onDemo,
        onRetry = { item?.let { coach.retry(it.id) } },
        onAccept = { keys ->
            item?.let { i ->
                val done = if (plan?.diff?.changes?.size == keys.size) c.planAccepted else c.planPartial
                // The confirmation only after the write worked; otherwise say so (the buttons stay usable).
                coach.launch { message = if (runCatching { coach.acceptPlan(i.id, keys) }.isSuccess) done else c.planActionFailed }
            }
        },
        onReject = {
            item?.let { i -> coach.launch { message = if (runCatching { coach.rejectPlan(i.id) }.isSuccess) c.planRejected else c.planActionFailed } }
        },
        onSaveAsTemplate = {
            item?.let { i ->
                coach.launch {
                    message = if (runCatching { coach.saveAsTemplate(i.id, c.planTemplateName) }.isSuccess) c.planSavedAsTemplate else c.planActionFailed
                }
            }
        },
        message = message,
    )
}

/** Demo review for users without AI access, built from their own workout. */
@Composable
fun CoachDemoScreen(coach: CoachService, sessionId: String, unit: WeightUnit, language: Language, nav: CoachNav) {
    val c = LocalCoachStrings.current
    var state by remember(sessionId) { mutableStateOf<Pair<CoachReview, PlanState>?>(null) }
    var comparisons by remember(sessionId) { mutableStateOf<List<ExerciseComparison>>(emptyList()) }
    LaunchedEffect(sessionId, c) {
        val (name, template) = coach.sessionAndTemplate(sessionId)
        val list = coach.comparisons(sessionId)
        val review = CoachDemo.review(name, template, c, list)
        comparisons = app.overfit.coach.ExerciseReviews.withFeedback(list, review.exerciseReviews)
        val diff = if (template != null && review.nextSession != null) {
            val byId = template.exercises.associate { it.exercise.id to it.exercise }
            PlanDiffs.diff(template, review.nextSession) { byId[it] }
        } else null
        state = review to PlanState(template, diff)
    }
    var message by remember { mutableStateOf<String?>(null) }
    val s = state
    ReviewContent(
        item = s?.let {
            ReviewItem(
                id = "demo", sessionId = sessionId, kind = ReviewKind.SESSION, status = ReviewStatus.DONE, createdAt = 0,
                review = it.first, errorKind = null, errorMessage = null, planStatus = PlanStatus.OPEN,
                acceptedChanges = emptySet(), costMicro = null, balanceMicro = null, providerId = null, model = null,
                rawResponse = null, isDemo = true,
            )
        },
        plan = s?.second,
        comparisons = comparisons,
        unit = unit,
        language = language,
        nav = nav,
        onDemo = null,
        onRetry = {},
        onAccept = { message = c.demoPlanNote },
        onReject = { message = c.demoPlanNote },
        onSaveAsTemplate = { message = c.demoPlanNote },
        message = message,
    )
}

@Composable
private fun ReviewContent(
    item: ReviewItem?,
    plan: PlanState?,
    comparisons: List<ExerciseComparison>,
    unit: WeightUnit,
    language: Language,
    nav: CoachNav,
    onDemo: (() -> Unit)?,
    onRetry: () -> Unit,
    onAccept: (Set<String>) -> Unit,
    onReject: () -> Unit,
    onSaveAsTemplate: () -> Unit,
    message: String?,
) {
    val c = LocalCoachStrings.current
    val t = LocalStrings.current
    val review = item?.review
    val diff = plan?.diff
    val planOpen = item?.planStatus == PlanStatus.OPEN && message == null
    var selected by remember(item?.id, diff) { mutableStateOf(diff?.changes?.map { it.key }?.toSet().orEmpty()) }
    val haptics = rememberHaptics()
    // Live arrival (waiting → answer while the screen is open): felt, and the rating counts up. Opening a finished
    // review from the archive (null → DONE) is not an arrival.
    val track = remember(item?.id) { StatusTrack(item?.status) }
    track.update(item?.status)
    LaunchedEffect(item?.status) {
        when (track.event) {
            ReviewStatus.DONE -> haptics.confirm()
            ReviewStatus.FAILED -> haptics.reject()
            else -> Unit
        }
        track.event = null
    }
    LaunchedEffect(message) {
        if (message == c.planAccepted || message == c.planPartial || message == c.planSavedAsTemplate) haptics.confirm()
    }
    val footerKind = when {
        item == null -> FooterKind.None
        item.status == ReviewStatus.FAILED || (item.status == ReviewStatus.QUEUED && item.errorKind != null) ->
            if (item.errorKind == CoachErrorKind.NO_ACCESS) FooterKind.None else FooterKind.Retry
        review != null && planOpen && diff != null && !diff.isEmpty -> FooterKind.Accept
        review != null && planOpen && plan != null && plan.template == null -> FooterKind.SaveTemplate
        else -> FooterKind.None
    }
    // The footer stays set; its button fades over or away (an empty footer takes no room).
    val footer: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit = {
        Swap(footerKind, Modifier.fillMaxWidth()) { kind ->
            when (kind) {
                FooterKind.Retry -> PrimaryButton(c.retry, onClick = onRetry)
                FooterKind.Accept -> diff?.let { d ->
                    val all = selected.size == d.changes.size
                    PrimaryButton(
                        if (all) c.planAcceptAll else c.planAcceptSelected(selected.size),
                        enabled = selected.isNotEmpty(),
                        onClick = { onAccept(selected) },
                    )
                }
                FooterKind.SaveTemplate -> PrimaryButton(c.planSaveAsTemplate, onClick = onSaveAsTemplate)
                FooterKind.None -> Unit
            }
        }
    }
    val phase = when {
        item == null -> ReviewPhase.Loading
        item.status == ReviewStatus.SENDING || (item.status == ReviewStatus.QUEUED && item.errorKind == null) -> ReviewPhase.Thinking
        item.status == ReviewStatus.QUEUED -> ReviewPhase.Queued
        item.status == ReviewStatus.FAILED -> ReviewPhase.Failed
        else -> ReviewPhase.Result
    }
    Screen(scroll = true, header = { BackHeader(t.back, nav.back) }, footer = footer) {
        if (item?.isDemo == true) {
            Text(c.demoBanner, Modifier.testTag("coach-demo-banner"), style = Theme.type.caption, tone = Tone.Muted)
            Spacer(Modifier.height(Space.s))
        }
        Title(if (item?.kind == ReviewKind.PROGRESS) c.progressTitle else c.reviewTitle)
        Spacer(Modifier.height(Space.l))
        // Only the head of the page is replaced when the status changes (keyed on the phase, not on every database
        // emission); "Per exercise" below stays in place between "thinking" and the result.
        Swap(ReviewHead(phase, item), Modifier.fillMaxWidth(), contentKey = { it.phase }) { head ->
            Column(Modifier.fillMaxWidth()) {
                val i = head.item
                when (head.phase) {
                    ReviewPhase.Loading -> Unit
                    ReviewPhase.Thinking -> Text(c.thinking, tone = Tone.Muted)
                    ReviewPhase.Queued -> {
                        Text(c.waitingForNetwork, style = Theme.type.label)
                        Spacer(Modifier.height(Space.s))
                        Text(c.queuedHint, tone = Tone.Muted)
                    }
                    ReviewPhase.Failed -> if (i != null) Failure(i, nav, onDemo)
                    ReviewPhase.Result -> i?.review?.let { r ->
                        r.rating?.let { Rating(it, countUp = track.live) }
                        Spacer(Modifier.height(Space.m))
                        if (r.summary.isNotBlank()) Text(r.summary)
                    }
                }
            }
        }
        val showNumbers = item != null && phase != ReviewPhase.Loading && comparisons.isNotEmpty() &&
            (phase != ReviewPhase.Result || item.kind == ReviewKind.SESSION)
        Reveal(showNumbers) {
            Column(Modifier.fillMaxWidth()) {
                SectionTitle(c.perExercise)
                ExerciseBreakdown(comparisons, unit, language)
            }
        }
        RevealNotNull(item?.takeIf { phase == ReviewPhase.Result && it.review != null }) { i ->
            Column(Modifier.fillMaxWidth()) {
                Result(i, i.review!!, plan, comparisons, unit, language, selected, { selected = it }, onReject, planOpen, message)
            }
        }
    }
}

private enum class ReviewPhase { Loading, Thinking, Queued, Failed, Result }

private enum class FooterKind { None, Retry, Accept, SaveTemplate }

private data class ReviewHead(val phase: ReviewPhase, val item: ReviewItem?)

/** Remembers the last status of a review so a live change (waiting → answer/error) can be told apart from opening. */
private class StatusTrack(private var last: ReviewStatus?) {
    /** True once the answer arrived while the screen was open. */
    var live = false
    /** The status a live change ended in, until the haptic for it ran. */
    var event: ReviewStatus? = null

    fun update(now: ReviewStatus?) {
        val prev = last
        if (prev == now) return
        if (prev == ReviewStatus.SENDING || prev == ReviewStatus.QUEUED) {
            if (now == ReviewStatus.DONE) live = true
            event = now
        }
        last = now
    }
}

/** The rating; counts up once when the answer arrived live, otherwise simply there. */
@Composable
private fun Rating(value: Int, countUp: Boolean) {
    val c = LocalCoachStrings.current
    val shown = if (countUp) {
        val v by rememberCountUp(value.toFloat())
        v.roundToInt()
    } else value
    Text(c.rating(shown), Modifier.testTag("coach-rating"), style = Theme.type.hero)
}

@Composable
private fun Failure(item: ReviewItem, nav: CoachNav, onDemo: (() -> Unit)?) {
    val c = LocalCoachStrings.current
    if (item.errorKind == CoachErrorKind.NO_ACCESS) {
        NoAccess(nav, onDemo)
        return
    }
    Text(errorText(item.errorKind, c))
    Spacer(Modifier.height(Space.m))
    when (item.errorKind) {
        CoachErrorKind.AUTH, CoachErrorKind.BAD_REQUEST -> Panel { ListRow(c.changeAccess, onClick = nav.connect) }
        CoachErrorKind.NO_FUEL -> Panel { ListRow(c.openFuel, onClick = nav.fuel) }
        else -> Unit
    }
    RawAnswer(item.rawResponse ?: item.errorMessage)
}

@Composable
private fun RawAnswer(raw: String?) {
    if (raw.isNullOrBlank()) return
    val c = LocalCoachStrings.current
    var show by remember { mutableStateOf(false) }
    Spacer(Modifier.height(Space.m))
    Swap(show) { s -> SmallButton(if (s) c.hideRaw else c.showRaw, onClick = { show = !show }) }
    Reveal(show) { Text(raw.take(6_000), style = Theme.type.caption, tone = Tone.Muted) }
}

private fun errorText(kind: CoachErrorKind?, c: CoachStrings) = when (kind) {
    CoachErrorKind.NO_ACCESS -> c.errNoAccess
    CoachErrorKind.AUTH -> c.errAuth
    CoachErrorKind.NO_FUEL -> c.errNoFuel
    CoachErrorKind.BAD_REQUEST -> c.errBadRequest
    CoachErrorKind.INVALID_ANSWER -> c.errInvalidAnswer
    CoachErrorKind.TIMEOUT -> c.errTimeout
    CoachErrorKind.RATE_LIMIT -> c.errRateLimit
    CoachErrorKind.NETWORK, CoachErrorKind.SERVER, null -> c.errServer
}

@Composable
private fun NoAccess(nav: CoachNav, onDemo: (() -> Unit)?) {
    val c = LocalCoachStrings.current
    Text(c.noAccessTitle, style = Theme.type.label)
    Spacer(Modifier.height(Space.s))
    Text(c.noAccessText, tone = Tone.Muted)
    Spacer(Modifier.height(Space.l))
    Panel {
        ListRow(c.connectAi, onClick = nav.connect)
        ListRow(c.useFuel, onClick = nav.fuel)
        if (onDemo != null) ListRow(c.demoReview, onClick = onDemo)
    }
}

@Composable
private fun Result(
    item: ReviewItem,
    review: CoachReview,
    plan: PlanState?,
    comparisons: List<ExerciseComparison>,
    unit: WeightUnit,
    language: Language,
    selected: Set<String>,
    onSelect: (Set<String>) -> Unit,
    onReject: () -> Unit,
    planOpen: Boolean,
    message: String?,
) {
    val c = LocalCoachStrings.current
    // Rating, summary and "Per exercise" stand above (ReviewContent), so they stay in place when the answer arrives.
    Bullets(c.positives, review.positives)
    Bullets(c.improvements, review.improvements)
    Bullets(c.tips, review.tips)
    review.photoFeedback?.let {
        SectionTitle(c.photoFeedback)
        Panel { PanelItem { Text(it) } }
    }
    if (review.nextSession != null && item.kind == ReviewKind.SESSION) {
        SectionTitle(c.nextSession)
        review.nextSession.note?.let { Text(it, tone = Tone.Muted); Spacer(Modifier.height(Space.s)) }
        val diff = plan?.diff
        val planKind = when {
            plan == null -> PlanKind.Loading
            plan.template == null -> PlanKind.NoTemplate
            diff == null || diff.isEmpty -> PlanKind.NoChanges
            else -> PlanKind.Changes
        }
        Swap(planKind, Modifier.fillMaxWidth()) { kind ->
            Column(Modifier.fillMaxWidth()) {
                when (kind) {
                    PlanKind.Loading -> Text("…", tone = Tone.Muted)
                    PlanKind.NoTemplate -> {
                        Text(c.planNoTemplate, tone = Tone.Muted)
                        // Names and sets like the change rows; the names come from this workout's exercises.
                        val known = comparisons.associateBy { it.exerciseId }
                        review.nextSession.exercises.forEach { e ->
                            val ex = known[e.exerciseId]
                            val sets = e.sets.map { PlannedSet(it.weightKg, it.reps, it.durationSec) }
                            val summary = PlanDiffs.summary(sets, ex?.type ?: TrackingType.WEIGHT_REPS, unit, language)
                            Text("${ex?.name ?: e.exerciseId} · $summary", style = Theme.type.caption, tone = Tone.Muted)
                        }
                    }
                    PlanKind.NoChanges -> Text(c.planNoChanges, tone = Tone.Muted)
                    PlanKind.Changes -> diff?.let { d ->
                        Panel {
                            d.changes.forEach { change ->
                                ChangeRow(change, unit, language, checked = change.key in selected, enabled = planOpen) {
                                    onSelect(if (change.key in selected) selected - change.key else selected + change.key)
                                }
                            }
                        }
                        d.unknownIds.forEach { Text(c.planUnknown(it), style = Theme.type.caption, tone = Tone.Muted) }
                        Reveal(planOpen) {
                            Column {
                                Spacer(Modifier.height(Space.s))
                                SmallButton(c.planReject, onClick = onReject)
                            }
                        }
                    }
                }
            }
        }
        val status = message ?: when (item.planStatus) {
            PlanStatus.ACCEPTED -> c.planAccepted
            PlanStatus.PARTIAL -> c.planPartial
            PlanStatus.REJECTED -> c.planRejected
            else -> null
        }
        RevealNotNull(status) {
            Column {
                Spacer(Modifier.height(Space.m))
                Swap(it) { text -> Text(text, tone = Tone.Muted) }
            }
        }
    }
    Spacer(Modifier.height(Space.l))
    if (review.memory != null && !item.isDemo) Text(c.memoryUpdated, style = Theme.type.caption, tone = Tone.Muted)
    val cost = item.costMicro
    val balance = item.balanceMicro
    if (cost != null && balance != null) {
        AnswerCostLine(c, language, cost, balance, item.servedModel, item.fallbackFrom)
    }
}

private enum class PlanKind { Loading, NoTemplate, NoChanges, Changes }

@Composable
private fun Bullets(title: String, items: List<String>) {
    if (items.isEmpty()) return
    PanelSection(title) {
        PanelItem {
            items.forEach { line ->
                Row(Modifier.padding(vertical = Space.xs)) {
                    Text("–", tone = Tone.Muted)
                    Spacer(Modifier.width(Space.s))
                    Text(line)
                }
            }
        }
    }
}

@Composable
private fun ChangeRow(change: PlanChange, unit: WeightUnit, language: Language, checked: Boolean, enabled: Boolean, onToggle: () -> Unit) {
    val c = LocalCoachStrings.current
    val haptics = rememberHaptics()
    val type = change.exercise.trackingType
    val (title, detail) = when (change) {
        is PlanChange.Modified -> {
            val parts = buildList {
                if (change.setsChanged) {
                    add("${PlanDiffs.summary(change.before, type, unit, language)} → ${PlanDiffs.summary(change.after, type, unit, language)}")
                }
                if (change.restChanged) add(c.changeRest(change.restBefore?.let { "$it s" } ?: "–", change.restAfter?.let { "$it s" } ?: "–"))
            }
            change.exercise.name to parts.joinToString("\n")
        }
        is PlanChange.Added -> "${if (change.markedNew) c.changeNew else c.changeSuggested}: ${change.exercise.name}" to
            PlanDiffs.summary(change.sets, type, unit, language)
        is PlanChange.Removed -> "${c.changeRemoved}: ${change.exercise.name}" to ""
    }
    Column(
        Modifier.background(Theme.colors.surface).then(if (enabled) Modifier.pressableRow { haptics.tick(); onToggle() } else Modifier)
            .semantics { selected = checked; role = Role.Checkbox },
    ) {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = Space.m, vertical = Space.s), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title)
                if (detail.isNotEmpty()) Text(detail, tone = Tone.Muted)
                change.reason?.let { Text(it, style = Theme.type.caption, tone = Tone.Muted) }
            }
            // Fixed slot: the empty circle fades out under the tick instead of the row jumping by 4 dp; after taking
            // over the plan the slot folds away sideways.
            RevealInRow(enabled) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.width(Space.m))
                    Box(Modifier.size(IconSize.action), contentAlignment = Alignment.Center) {
                        val fill = motionColor(if (checked) Color.Transparent else Theme.colors.surfacePressed, "changeCircle")
                        Box(Modifier.size(20.dp).drawBehind { drawCircle(fill.value) })
                        Appear(checked) { LineIcon(Icon.Check) }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Memory and settings

@Composable
internal fun TextArea(value: String, onValueChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    Panel(modifier) {
        PanelItem(Modifier.heightIn(min = 160.dp)) {
            Box(Modifier.fillMaxWidth()) {
                if (value.isEmpty()) Text(placeholder, tone = Tone.Faint)
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    textStyle = Theme.type.body.copy(color = Theme.colors.foreground),
                    cursorBrush = SolidColor(Theme.colors.foreground),
                    modifier = Modifier.fillMaxWidth().textInputArea(),
                )
            }
        }
    }
}

@Composable
fun CoachMemoryScreen(coach: CoachService, onBack: () -> Unit) {
    val c = LocalCoachStrings.current
    val t = LocalStrings.current
    val current by coach.memory.collectAsState(initial = null)
    val versions by coach.memoryVersions.collectAsState(initial = emptyList())
    var text by remember(current?.id) { mutableStateOf(current?.text.orEmpty()) }
    var saved by remember { mutableStateOf(false) }
    val haptics = rememberHaptics()
    val changed = text.trim() != current?.text.orEmpty()
    Screen(
        scroll = true,
        header = { BackHeader(t.back, onBack) },
        footer = {
            // The confirmation sits in the button itself ("Saved", like "Copied"), no extra line that pushes the page.
            PrimaryButton(
                if (saved && !changed) c.memorySaved.trimEnd('.') else c.memorySave,
                enabled = changed || saved,
                onClick = {
                    if (changed) {
                        coach.launch { coach.saveMemory(text) }
                        saved = true
                        haptics.confirm()
                    }
                },
            )
        },
    ) {
        Title(t.coachMemory)
        Spacer(Modifier.height(Space.m))
        Text(c.memoryHint, tone = Tone.Muted)
        Spacer(Modifier.height(Space.l))
        TextArea(text, { text = it; saved = false }, c.memoryPlaceholder, Modifier.testTag("coach-memory-input"))
        Reveal(versions.size > 1) {
            Column(Modifier.fillMaxWidth().animateContentSize(Motion.smooth())) {
                PanelSection(c.memoryVersions) {
                    versions.drop(1).take(20).forEach { v ->
                        val who = if (v.source == CoachService.SOURCE_COACH) c.memoryByCoach else c.memoryByYou
                        ListRow("${dateText(v.createdAt)} · $who", c.memoryRestore, onClick = { text = v.text; saved = false; haptics.tick() })
                    }
                }
            }
        }
        Reveal(current != null) {
            Column {
                Spacer(Modifier.height(Space.l))
                ConfirmButton(c.memoryDelete, onConfirm = { coach.launch { coach.clearMemory() }; text = "" })
            }
        }
    }
}

/**
 * Credit: the real model name and cost of an answer, plus a transparent note when a replacement model answered
 * (the price of the chosen model was charged). [served] is the model ID; null with an old backend (no model line).
 */
@Composable
internal fun AnswerCostLine(c: CoachStrings, language: app.overfit.domain.Language, cost: Long, balance: Long?, served: String?, fallbackFrom: String?) {
    val model = served?.let { app.overfit.fuel.modelLabel(it) }
    val f = app.overfit.i18n.LocalFuelStrings.current
    // Cost is credit used (never a price).
    val money = f.creditAmount(app.overfit.fuel.formatCost(cost, language))
    val line = c.answerInfo(model, money, balance?.let { formatEuro(it, language) })
    Text(line, style = Theme.type.caption, tone = Tone.Muted)
    if (served != null && fallbackFrom != null) {
        Text(
            c.answerFallback(app.overfit.fuel.modelShortName(fallbackFrom), app.overfit.fuel.modelShortName(served)),
            Modifier.padding(top = Space.xs), style = Theme.type.caption, tone = Tone.Muted,
        )
    }
}
