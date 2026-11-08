package app.overfit.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
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
internal fun CoachChatHost(deps: AppDependencies, state: AppState, nav: NavHostController, openedChatId: String?) {
    val openRequest by deps.coachChatRequests.pending.collectAsStateWithLifecycle()
    var modelSheet by rememberSaveable { mutableStateOf(false) }
    var creditPopup by rememberSaveable { mutableStateOf(false) }
    val fuelActive = state.providerId == ProviderId.FUEL
    Box(Modifier.fillMaxSize()) {
        CoachChatTabScreen(
            env = deps.coachChatEnv,
            // Requests from elsewhere (review card, prompts) go to the tab, never into an opened earlier chat.
            openRequest = if (openedChatId == null) openRequest else null,
            onOpenRequestConsumed = deps.coachChatRequests::consumed,
            onHistory = { nav.navigate(CoachHistoryRoute) },
            onStory = { nav.navigate(CoachStoryRoute) },
            onModelChip = { modelSheet = true },
            onApplyWorkout = { json -> deps.inbox.offer(json) },
            creditChip = { app.overfit.ui.coach.CreditChip(deps.fuelSession, fuelActive, onClick = { creditPopup = true }) },
            openedChatId = openedChatId,
            onBack = { nav.popBackStack() },
        )
        val close = LocalStrings.current.back
        app.overfit.ui.components.BottomSheet(modelSheet, onDismiss = { modelSheet = false }, closeLabel = close) {
            app.overfit.ui.coach.ModelPicker(state, deps.store, deps.providers, deps.coach, onApplied = { modelSheet = false }, onSurface = true)
        }
        app.overfit.ui.components.CenterPopup(creditPopup, onDismiss = { creditPopup = false }, closeLabel = close) {
            app.overfit.ui.coach.CreditPopupContent(
                session = deps.fuelSession,
                dao = deps.data.database.aiRequestDao(),
                onOpenFuel = { creditPopup = false; nav.navigate(FuelRoute) },
                onAllRequests = { creditPopup = false; nav.navigate(RequestLogRoute) },
                onClose = { creditPopup = false },
                distribution = deps.distribution,
            )
        }
    }
}
