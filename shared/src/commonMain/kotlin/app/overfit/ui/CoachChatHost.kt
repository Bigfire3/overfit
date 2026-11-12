package app.overfit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import app.overfit.ui.components.absorbTaps
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.overfit.data.AppState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import app.overfit.ai.ProviderId
import app.overfit.i18n.LocalStrings
import app.overfit.ui.coach.chat.CoachChatTabScreen

/**
 * The coach chat with its model sheet and credit popup. [openedChatId] null = the Coach tab (story, earlier chats, new
 * chat, requests from elsewhere); otherwise exactly that earlier chat in its own screen with a back arrow.
 */
@Composable
internal fun CoachChatHost(
    deps: AppDependencies,
    state: AppState,
    nav: NavHostController,
    openedChatId: String?,
    overlay: app.overfit.ui.coach.chat.CoachOverlayRequest? = null,
    onClose: () -> Unit = {},
    proposals: app.overfit.ui.coach.chat.ProposalHandler? = null,
) {
    val openRequest by deps.coachChatRequests.pending.collectAsStateWithLifecycle()
    var modelSheet by rememberSaveable { mutableStateOf(false) }
    var creditPopup by rememberSaveable { mutableStateOf(false) }
    val fuelActive = state.providerId == ProviderId.FUEL
    Box(Modifier.fillMaxSize()) {
        CoachChatTabScreen(
            env = deps.coachChatEnv,
            // Requests from elsewhere (review card, prompts) go to the tab, never into an opened earlier chat or coach.
            openRequest = if (openedChatId == null && overlay == null) openRequest else null,
            onOpenRequestConsumed = deps.coachChatRequests::consumed,
            onHistory = { nav.navigate(CoachHistoryRoute) },
            onStory = { nav.navigate(CoachStoryRoute) },
            onModelChip = { modelSheet = true },
            // Plain proposal (no target): the import editor opens; an opened coach closes so it is not hidden underneath.
            onApplyWorkout = { json -> if (overlay != null) onClose(); deps.inbox.offer(json) },
            creditChip = { app.overfit.ui.coach.CreditChip(deps.fuelSession, fuelActive, onClick = { creditPopup = true }) },
            openedChatId = openedChatId,
            onBack = { nav.popBackStack() },
            overlay = overlay,
            onClose = onClose,
            proposals = proposals,
        )
        val close = LocalStrings.current.back
        app.overfit.ui.components.BottomSheet(modelSheet, onDismiss = { modelSheet = false }, closeLabel = close) {
            app.overfit.ui.coach.ModelPicker(state, deps.store, deps.providers, deps.coach, onApplied = { modelSheet = false }, onSurface = true)
        }
        app.overfit.ui.components.CenterPopup(creditPopup, onDismiss = { creditPopup = false }, closeLabel = close) {
            app.overfit.ui.coach.CreditPopupContent(
                session = deps.fuelSession,
                dao = deps.data.database.aiRequestDao(),
                onOpenFuel = { creditPopup = false; onClose(); nav.navigate(FuelRoute) },
                onAllRequests = { creditPopup = false; onClose(); nav.navigate(RequestLogRoute) },
                onClose = { creditPopup = false },
                distribution = deps.distribution,
            )
        }
    }
}

/**
 * The coach opened over the current screen (coach icon in its header): slides up over everything, closes with ✕ or
 * back. A new chat each time it opens, with the screen's context as chips; proposals apply to that screen's workout.
 */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
internal fun CoachOverlayHost(
    deps: AppDependencies,
    state: AppState,
    nav: NavHostController,
    editorSeeds: app.overfit.ui.templates.EditorSeedHolder,
) {
    val request by deps.coachOverlay.request.collectAsStateWithLifecycle()
    // Registered anew whenever it opens, so it wins over back handlers of the screen underneath (last one wins).
    androidx.compose.runtime.key(request) {
        androidx.compose.ui.backhandler.BackHandler(enabled = request != null) { deps.coachOverlay.close() }
    }
    // A text field underneath (e.g. the workout name) loses focus and keyboard.
    val focus = androidx.compose.ui.platform.LocalFocusManager.current
    LaunchedEffect(request) { if (request != null) focus.clearFocus(force = true) }
    // Keeps the last request while the layer slides out.
    var shown by androidx.compose.runtime.remember { mutableStateOf<app.overfit.ui.coach.chat.CoachOverlayRequest?>(null) }
    request?.let { shown = it }
    androidx.compose.animation.AnimatedVisibility(
        visible = request != null,
        enter = androidx.compose.animation.slideInVertically(androidx.compose.animation.core.tween(220)) { it / 4 } +
            androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(180)),
        exit = androidx.compose.animation.slideOutVertically(androidx.compose.animation.core.tween(150)) { it / 4 } +
            androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(150)),
    ) {
        val r = shown ?: return@AnimatedVisibility
        // A fresh chat screen per request (its state is keyed to the request).
        androidx.compose.runtime.key(r) {
            val handler = androidx.compose.runtime.remember(r) { r.target?.let { proposalHandler(deps, nav, editorSeeds, it) } }
            Box(Modifier.fillMaxSize().background(app.overfit.ui.theme.Theme.colors.background).absorbTaps()) {
                CoachChatHost(deps, state, nav, openedChatId = null, overlay = r, onClose = { deps.coachOverlay.close() }, proposals = handler)
            }
        }
    }
}


/**
 * "Apply" for an opened coach:
 * - saved workout: the proposal opens in the normal workout editor (seed path of the import, #88) with the diff as
 *   hints; "Save" overwrites exactly that workout, back discards it,
 * - running workout: merged right away ([app.overfit.coach.RunningMerge], done sets stay), "Undo" restores it as long
 *   as nothing else changed since.
 */
internal fun proposalHandler(
    deps: AppDependencies,
    nav: NavHostController,
    editorSeeds: app.overfit.ui.templates.EditorSeedHolder,
    target: app.overfit.ui.coach.chat.ApplyTarget,
): app.overfit.ui.coach.chat.ProposalHandler {
    val language = deps.store.state.value.language
    val unit = deps.store.state.value.profile.unit
    val s = app.overfit.i18n.coachChatStringsFor(language)
    val c = app.overfit.i18n.chatStringsFor(language)
    suspend fun preview(json: String): app.overfit.share.ImportPreview? {
        val training = app.overfit.coach.CoachChatParser.workout(json) ?: return null
        return deps.data.importer.preview(training)
    }
    return when (target) {
        is app.overfit.ui.coach.chat.ApplyTarget.Template -> app.overfit.ui.coach.chat.ProposalHandler(
            title = s.changeFor(target.name),
            diff = { json ->
                val old = deps.data.templates.get(target.templateId)
                val p = preview(json)
                if (old == null || p == null) emptyList() else app.overfit.chat.TemplateDiff.lines(old, p, c, unit, language)
            },
            apply = { json ->
                val p = preview(json)
                val old = deps.data.templates.get(target.templateId)
                if (p != null) {
                    val lines = if (old == null) emptyList() else app.overfit.chat.TemplateDiff.lines(old, p, c, unit, language)
                    editorSeeds.put(
                        app.overfit.ui.templates.EditorSeed(
                            preview = p,
                            hints = listOf(c.diffTitle) + lines.ifEmpty { listOf(c.diffNone) },
                            replaceId = old?.id,
                        ),
                    )
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        deps.coachOverlay.close()
                        // The workout's own screen (if it is open) goes: after "Save" the editor opens a fresh one, a
                        // stale one underneath would show (and autosave) the old version.
                        nav.popBackStack(WorkoutPlanRoute(target.templateId), inclusive = true)
                        nav.navigate(TemplateEditorRoute(seeded = true))
                    }
                    null
                } else {
                    app.overfit.ui.coach.chat.AppliedChange(undo = null, note = s.applyFailed)
                }
            },
            applied = deps.coachOverlay.applied,
        )
        is app.overfit.ui.coach.chat.ApplyTarget.Running -> app.overfit.ui.coach.chat.ProposalHandler(
            title = s.changeRunning,
            diff = { json ->
                val w = deps.workout.state.value.workout?.takeIf { it.id == target.sessionId }
                val p = preview(json)
                if (w == null || p == null) emptyList()
                else app.overfit.chat.TemplateDiff.lines(app.overfit.coach.RunningMerge.asTemplate(w), p.copy(name = w.name), c, unit, language)
            },
            apply = apply@{ json ->
                val failed = app.overfit.ui.coach.chat.AppliedChange(undo = null, note = s.applyFailed)
                val proposal = preview(json)?.let(app.overfit.share.ShareMapper::toDraftTemplate) ?: return@apply failed
                // Merged from the workout as it is in this moment (a set ticked meanwhile is not lost).
                var before: app.overfit.domain.Workout? = null
                var result: app.overfit.coach.RunningMerge.Result? = null
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    deps.workout.edit { w ->
                        if (w.id != target.sessionId) w
                        else { before = w; app.overfit.coach.RunningMerge.apply(w, proposal).also { result = it }.workout }
                    }
                }
                val old = before ?: return@apply failed
                val merged = result ?: return@apply failed
                app.overfit.ui.coach.chat.AppliedChange(
                    undo = {
                        var undone = false
                        deps.workout.edit { w -> if (w == merged.workout) { undone = true; old } else w }
                        undone
                    },
                    note = merged.unknown.takeIf { it.isNotEmpty() }?.let { s.leftOut(it.joinToString(", ")) },
                )
            },
            applied = deps.coachOverlay.applied,
        )
    }
}
