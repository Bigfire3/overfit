package app.overfit.ui.coach

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
    val openSession: (String) -> Unit,
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
    val reviews by coach.reviews.collectAsState(initial = emptyList())
    val latest by coach.latestFinished.collectAsState(initial = null)
    Screen(scroll = true, header = { BackHeader(t.back, nav.back) }) {
        Title(c.coachTitle)
        Spacer(Modifier.height(Space.l))
        Panel {
            latest?.let { s -> RowWithHint(c.reviewLast, c.reviewLastHint) { nav.openSession(s.id) } }
            RowWithHint(c.assessProgress, c.assessProgressHint) { nav.openReview(coach.startProgressReview()) }
        }

        if (reviews.isEmpty()) {
            SectionTitle(c.reviews)
            Text(c.reviewsEmpty, tone = Tone.Muted)
        } else PanelSection(c.reviews) {
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

/** Coach review of a finished workout; offers sending if there is none yet. */
@Composable
fun CoachSessionScreen(coach: CoachService, sessionId: String, unit: WeightUnit, language: Language, nav: CoachNav) {
    val item by remember(sessionId) { coach.reviewForSession(sessionId) }.collectAsState(initial = null)
    var requested by rememberSaveable(sessionId) { mutableStateOf(false) }
    val current = item
    if (current == null && !requested) {
        val c = LocalCoachStrings.current
        var comparisons by remember(sessionId) { mutableStateOf<List<ExerciseComparison>>(emptyList()) }
        LaunchedEffect(sessionId) { comparisons = coach.comparisons(sessionId) }
        val access = coach.access()
        Screen(
            header = { BackHeader(LocalStrings.current.back, nav.back) },
            footer = if (access is CoachAccess.Ready) {
                { PrimaryButton(c.sendToCoach, onClick = { requested = true; coach.startSessionReview(sessionId) }) }
            } else null,
        ) {
            Title(c.reviewTitle)
            Spacer(Modifier.height(Space.l))
            if (access !is CoachAccess.Ready) NoAccess(nav, onDemo = { nav.demo(sessionId) })
            LocalNumbers(comparisons, unit, language)
        }
        return
    }
    ReviewScreen(coach, current, unit, language, nav, onDemo = { nav.demo(sessionId) })
}

@Composable
fun CoachReviewScreen(coach: CoachService, reviewId: String, unit: WeightUnit, language: Language, nav: CoachNav) {
    val item by remember(reviewId) { coach.review(reviewId) }.collectAsState(initial = null)
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
            plan = coach.planDiff(item)?.let { PlanState(it.first, it.second) } ?: PlanState(null, null)
        }
    }
    var message by remember(item?.id) { mutableStateOf<String?>(null) }
    val sessionId = item?.sessionId?.takeIf { item.kind == ReviewKind.SESSION }
    var comparisons by remember(sessionId, item?.review) { mutableStateOf<List<ExerciseComparison>>(emptyList()) }
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
                coach.launch { coach.acceptPlan(i.id, keys) }
                message = if (plan?.diff?.changes?.size == keys.size) c.planAccepted else c.planPartial
            }
        },
        onReject = { item?.let { i -> coach.launch { coach.rejectPlan(i.id) }; message = c.planRejected } },
        onSaveAsTemplate = {
            item?.let { i ->
                coach.launch { coach.saveAsTemplate(i.id, c.planTemplateName) }
                message = c.planSavedAsTemplate
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
    val footer: (@Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit)? = when {
        item == null -> null
        item.status == ReviewStatus.FAILED || (item.status == ReviewStatus.QUEUED && item.errorKind != null) ->
            if (item.errorKind == CoachErrorKind.NO_ACCESS) null else { { PrimaryButton(c.retry, onClick = onRetry) } }
        review != null && planOpen && diff != null && !diff.isEmpty -> {
            {
                val all = selected.size == diff.changes.size
                PrimaryButton(
                    if (all) c.planAcceptAll else c.planAcceptSelected(selected.size),
                    enabled = selected.isNotEmpty(),
                    onClick = { onAccept(selected) },
                )
            }
        }
        review != null && planOpen && plan != null && plan.template == null -> {
            { PrimaryButton(c.planSaveAsTemplate, onClick = onSaveAsTemplate) }
        }
        else -> null
    }
    Screen(scroll = true, header = { BackHeader(t.back, nav.back) }, footer = footer) {
        if (item?.isDemo == true) {
            Text(c.demoBanner, Modifier.testTag("coach-demo-banner"), style = Theme.type.caption, tone = Tone.Muted)
            Spacer(Modifier.height(Space.s))
        }
        Title(if (item?.kind == ReviewKind.PROGRESS) c.progressTitle else c.reviewTitle)
        Spacer(Modifier.height(Space.l))
        when {
            item == null || item.status == ReviewStatus.SENDING ||
                (item.status == ReviewStatus.QUEUED && item.errorKind == null) -> {
                Text(c.thinking, tone = Tone.Muted)
                if (item != null) LocalNumbers(comparisons, unit, language)
            }
            item.status == ReviewStatus.QUEUED -> {
                Text(c.waitingForNetwork, style = Theme.type.label)
                Spacer(Modifier.height(Space.s))
                Text(c.queuedHint, tone = Tone.Muted)
                LocalNumbers(comparisons, unit, language)
            }
            item.status == ReviewStatus.FAILED -> {
                Failure(item, nav, onDemo)
                LocalNumbers(comparisons, unit, language)
            }
            review != null -> Result(item, review, plan, comparisons, unit, language, selected, { selected = it }, onReject, planOpen, message)
        }
    }
}

/** "Per exercise" with the local numbers only (no coach text): while waiting, on errors and without AI. */
@Composable
private fun LocalNumbers(list: List<ExerciseComparison>, unit: WeightUnit, language: Language) {
    if (list.isEmpty()) return
    SectionTitle(LocalCoachStrings.current.perExercise)
    ExerciseBreakdown(list, unit, language)
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
    SmallButton(if (show) c.hideRaw else c.showRaw, onClick = { show = !show })
    if (show) Text(raw.take(6_000), style = Theme.type.caption, tone = Tone.Muted)
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
    review.rating?.let { Text(c.rating(it), Modifier.testTag("coach-rating"), style = Theme.type.hero) }
    Spacer(Modifier.height(Space.m))
    if (review.summary.isNotBlank()) Text(review.summary)
    if (comparisons.isNotEmpty() && item.kind == ReviewKind.SESSION) {
        SectionTitle(c.perExercise)
        ExerciseBreakdown(comparisons, unit, language)
    }
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
        when {
            plan == null -> Text("…", tone = Tone.Muted)
            plan.template == null -> {
                Text(c.planNoTemplate, tone = Tone.Muted)
                review.nextSession.exercises.forEach { e ->
                    Text("${e.exerciseId} · ${e.sets.size}", style = Theme.type.caption, tone = Tone.Muted)
                }
            }
            diff == null || diff.isEmpty -> Text(c.planNoChanges, tone = Tone.Muted)
            else -> {
                Panel {
                    diff.changes.forEach { change ->
                        ChangeRow(change, unit, language, checked = change.key in selected, enabled = planOpen) {
                            onSelect(if (change.key in selected) selected - change.key else selected + change.key)
                        }
                    }
                }
                diff.unknownIds.forEach { Text(c.planUnknown(it), style = Theme.type.caption, tone = Tone.Muted) }
                if (planOpen) {
                    Spacer(Modifier.height(Space.s))
                    SmallButton(c.planReject, onClick = onReject)
                }
            }
        }
        val status = message ?: when (item.planStatus) {
            PlanStatus.ACCEPTED -> c.planAccepted
            PlanStatus.PARTIAL -> c.planPartial
            PlanStatus.REJECTED -> c.planRejected
            else -> null
        }
        status?.let { Spacer(Modifier.height(Space.m)); Text(it, tone = Tone.Muted) }
    }
    Spacer(Modifier.height(Space.l))
    if (review.memory != null && !item.isDemo) Text(c.memoryUpdated, style = Theme.type.caption, tone = Tone.Muted)
    val cost = item.costMicro
    val balance = item.balanceMicro
    if (cost != null && balance != null) {
        AnswerCostLine(c, language, cost, balance, item.servedModel, item.fallbackFrom)
    }
}

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
        Modifier.background(Theme.colors.surface).then(if (enabled) Modifier.pressableRow(onToggle) else Modifier)
            .semantics { selected = checked; role = Role.Checkbox },
    ) {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = Space.m, vertical = Space.s), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title)
                if (detail.isNotEmpty()) Text(detail, tone = Tone.Muted)
                change.reason?.let { Text(it, style = Theme.type.caption, tone = Tone.Muted) }
            }
            if (enabled) {
                Spacer(Modifier.width(Space.m))
                if (checked) LineIcon(Icon.Check)
                else Box(Modifier.size(20.dp).background(Theme.colors.surfacePressed, CircleShape))
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
    Screen(
        scroll = true,
        header = { BackHeader(t.back, onBack) },
        footer = {
            PrimaryButton(c.memorySave, enabled = text.trim() != current?.text.orEmpty(), onClick = {
                coach.launch { coach.saveMemory(text) }
                saved = true
            })
        },
    ) {
        Title(t.coachMemory)
        Spacer(Modifier.height(Space.m))
        Text(c.memoryHint, tone = Tone.Muted)
        Spacer(Modifier.height(Space.l))
        TextArea(text, { text = it; saved = false }, c.memoryPlaceholder, Modifier.testTag("coach-memory-input"))
        if (saved) { Spacer(Modifier.height(Space.s)); Text(c.memorySaved, style = Theme.type.caption, tone = Tone.Muted) }
        if (versions.size > 1) {
            PanelSection(c.memoryVersions) {
                versions.drop(1).take(20).forEach { v ->
                    val who = if (v.source == CoachService.SOURCE_COACH) c.memoryByCoach else c.memoryByYou
                    ListRow("${dateText(v.createdAt)} · $who", c.memoryRestore, onClick = { text = v.text; saved = false })
                }
            }
        }
        if (current != null) {
            Spacer(Modifier.height(Space.l))
            ConfirmButton(c.memoryDelete, onConfirm = { coach.launch { coach.clearMemory() }; text = "" })
        }
    }
}

@Composable
fun CoachSettingsScreen(coach: CoachService, onBack: () -> Unit) {
    val c = LocalCoachStrings.current
    val t = LocalStrings.current
    val settings by coach.settings.state.collectAsState()
    val provider = coach.providerId()
    val defaultText = c.defaultInstructions
    var model by remember(provider, settings.models) { mutableStateOf(settings.model(provider)) }
    var instructions by remember(settings.instructions, defaultText) { mutableStateOf(settings.instructions ?: defaultText) }
    var saved by remember { mutableStateOf(false) }
    Screen(
        scroll = true,
        header = { BackHeader(t.back, onBack) },
        footer = {
            PrimaryButton(c.instructionsSave, enabled = instructions != (settings.instructions ?: defaultText), onClick = {
                coach.settings.setInstructions(instructions.takeIf { it.trim() != defaultText.trim() })
                saved = true
            })
        },
    ) {
        Title(c.settingsTitle)
        if (provider == app.overfit.ai.ProviderId.FUEL) {
            SectionTitle(c.model)
            FuelQuality(coach, settings.model(provider))
        } else {
            PanelSection(c.model) {
                ListRow(providerLabel(provider)) {
                    app.overfit.ui.components.RowInput(model, { model = it.trim().take(100) }, onCommit = { coach.settings.setModel(provider, model) })
                }
            }
            Hint(c.modelHint(app.overfit.coach.CoachModels.default(provider)))
        }
        PanelSection(c.sendPhotos) {
            app.overfit.ui.components.SwitchRow(c.sendPhotos, settings.sendPhotos, onCheckedChange = coach.settings::setSendPhotos)
        }
        Hint(c.sendPhotosHint)
        SectionTitle(c.instructions)
        Text(c.instructionsHint, style = Theme.type.caption, tone = Tone.Muted)
        Spacer(Modifier.height(Space.s))
        TextArea(instructions, { instructions = it; saved = false }, "", Modifier.testTag("coach-instructions"))
        Row(Modifier.fillMaxWidth().padding(top = Space.s), horizontalArrangement = Arrangement.SpaceBetween) {
            SmallButton(c.instructionsReset, onClick = { instructions = defaultText; coach.settings.setInstructions(null); saved = true })
            if (saved) Text(c.saved, style = Theme.type.caption, tone = Tone.Muted)
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
    // Cost is credit used (never a price); 0 = paid with a gifted review (invite bonus).
    val money = if (cost == 0L) f.answerGifted else f.creditAmount(app.overfit.fuel.formatCost(cost, language))
    val line = c.answerInfo(model, money, balance?.let { formatEuro(it, language) })
    Text(line, style = Theme.type.caption, tone = Tone.Muted)
    if (served != null && fallbackFrom != null) {
        Text(
            c.answerFallback(app.overfit.fuel.modelShortName(fallbackFrom), app.overfit.fuel.modelShortName(served)),
            Modifier.padding(top = Space.xs), style = Theme.type.caption, tone = Tone.Muted,
        )
    }
}

/**
 * Credit: choice of the coach model by its real name. Every model shows what it costs (estimated per review and chat
 * message, from the backend's price table, markup and typical token amounts) and how many reviews the credit pays for.
 * A model is only offered when the backend has it (`/v1/config` → tiers). The tier IDs stay internal.
 */
@Composable
private fun FuelQuality(coach: CoachService, tier: String) {
    val c = LocalCoachStrings.current
    val language = app.overfit.i18n.LocalFuelStrings.current.language
    val config by androidx.compose.runtime.produceState<app.overfit.fuel.FuelConfig?>(null, coach) { value = coach.fuelConfig() }
    val balance by androidx.compose.runtime.produceState<Long?>(null, coach) { value = coach.fuelBalance() }
    val cfg = config
    val tiers = cfg?.tiers
    if (cfg == null) { Text(app.overfit.i18n.LocalFuelStrings.current.loading, tone = Tone.Muted); return }
    val offered = listOf(app.overfit.ai.TIER_STANDARD, app.overfit.ai.TIER_PRO).mapNotNull { id -> tiers?.get(id)?.takeIf { it.available }?.let { id to it } }
    if (offered.isEmpty()) return
    if (offered.size == 1) {
        Text(c.modelFixed(offered[0].second.label), tone = Tone.Muted)
        // The one model answers whatever is stored: keep the stored choice in sync.
    }
    val selected = offered.firstOrNull { it.first == tier } ?: offered.first()
    fun cost(info: app.overfit.fuel.FuelTierInfo, task: app.overfit.fuel.FuelTask) =
        app.overfit.fuel.CostEstimate.micro(info, cfg.markupBp, task, cfg.usageProfiles)
    if (offered.size > 1) Panel {
        offered.forEach { (id, info) ->
            val on = id == selected.first
            ListRow(info.label, tone = if (on) Tone.Default else Tone.Muted, onClick = { coach.settings.setModel(app.overfit.ai.ProviderId.FUEL, id) }, trailing = {
                if (on) LineIcon(Icon.Check, size = 20.dp)
            })
            PanelItem {
                Text(
                    c.modelCosts(
                        app.overfit.fuel.formatCents(cost(info, app.overfit.fuel.FuelTask.COACH), language),
                        app.overfit.fuel.formatCents(cost(info, app.overfit.fuel.FuelTask.CHAT), language),
                    ),
                    style = Theme.type.caption, tone = Tone.Muted,
                )
            }
        }
    } else Hint(
        c.modelCosts(
            app.overfit.fuel.formatCents(cost(selected.second, app.overfit.fuel.FuelTask.COACH), language),
            app.overfit.fuel.formatCents(cost(selected.second, app.overfit.fuel.FuelTask.CHAT), language),
        ),
    )
    val review = cost(selected.second, app.overfit.fuel.FuelTask.COACH)
    balance?.let {
        Hint(c.modelAffordable(formatEuro(it, language), app.overfit.fuel.CostEstimate.affordable(it, review)))
    }
    tiers?.get(app.overfit.ai.TIER_LITE)?.takeIf { it.available }?.let { lite ->
        Hint(c.modelAutofill(lite.label, app.overfit.fuel.formatCents(cost(lite, app.overfit.fuel.FuelTask.AUTOFILL), language)))
    }
    Text(c.modelEstimateNote((cfg.markupBp / 100).toString()), Modifier.padding(top = Space.s), style = Theme.type.caption, tone = Tone.Faint)
}

private fun providerLabel(id: app.overfit.ai.ProviderId) = when (id) {
    app.overfit.ai.ProviderId.ANTHROPIC -> "Anthropic"
    app.overfit.ai.ProviderId.OPENAI -> "OpenAI"
    app.overfit.ai.ProviderId.OPENROUTER -> "OpenRouter"
    app.overfit.ai.ProviderId.GEMINI -> "Gemini"
    app.overfit.ai.ProviderId.FUEL -> "Guthaben"
}
