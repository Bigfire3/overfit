package app.overfit.ui.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import app.overfit.ai.ProviderId
import app.overfit.ai.Providers
import app.overfit.chat.ChatFeature
import app.overfit.coach.CoachChatPrefsStore
import app.overfit.coach.CoachService
import app.overfit.data.AppState
import app.overfit.fuel.FuelSession
import app.overfit.i18n.LocalChatStrings
import app.overfit.i18n.LocalCoachStrings
import app.overfit.i18n.LocalFuelStrings
import app.overfit.i18n.LocalStrings
import app.overfit.i18n.coachSettingsStringsFor
import app.overfit.i18n.requestLogStringsFor
import app.overfit.ui.chat.ChatProvider
import app.overfit.ui.coach.modelChipLabel
import app.overfit.ui.coach.ownKeyProviderLabel
import app.overfit.ui.components.Hint
import app.overfit.ui.components.ListRow
import app.overfit.ui.components.Panel
import app.overfit.ui.components.PanelSection
import app.overfit.ui.components.SwitchRow
import app.overfit.ui.theme.Space

/** Where the rows of settings → Coach lead. */
class CoachSettingsNav(
    /** AI access and default model ([app.overfit.ui.coach.CoachAccessScreen]). */
    val access: () -> Unit,
    val fuel: () -> Unit,
    val profile: () -> Unit,
    val instructions: () -> Unit,
    val memory: () -> Unit,
    val reviews: () -> Unit,
    val requestLog: () -> Unit,
    val prompts: () -> Unit,
    val chatProvider: (ChatProvider) -> Unit,
    val chatNotes: () -> Unit,
    val story: () -> Unit,
)

/**
 * Settings → Coach: everything that used to sit on the Coach page (AI access, credit, model, automatic review,
 * profile, instructions, memory, reviews, billing, photos) plus "Prompts & connections" and the story. With an own key
 * nothing here talks to the Overfit backend (the credit row only loads with credit, OwnKeyPrivacyTest).
 */
@Composable
internal fun CoachSettingsSection(
    state: AppState,
    providers: Providers,
    coach: CoachService,
    prefs: CoachChatPrefsStore,
    chatFeature: ChatFeature,
    fuelSession: FuelSession,
    nav: CoachSettingsNav,
) {
    val language = LocalFuelStrings.current.language
    val s = coachSettingsStringsFor(language)
    val c = LocalCoachStrings.current
    val t = LocalStrings.current
    val settings by coach.settings.state.collectAsState()
    val reviews by coach.reviews.collectAsState(initial = emptyList())
    val fuel = state.providerId == ProviderId.FUEL
    val ownKey = !fuel && state.hasApiKey
    val accessValue = when {
        fuel && providers.fuel.available -> s.accessCredit
        ownKey -> s.accessOwnKey(ownKeyProviderLabel(state.providerId))
        else -> s.accessNone
    }

    PanelSection(s.sectionCoach) {
        ListRow(s.access, accessValue, onClick = nav.access)
        FuelRow(fuelSession, fuelActive = fuel, onClick = nav.fuel)
        ListRow(s.defaultModel, modelChipLabel(state, settings, providers.fuel.available, s), onClick = nav.access)
    }
    if (ownKey) {
        Hint(c.hub.accessOwnKeyPrivacy(providers.get(state.providerId).label), Modifier.testTag("coach:access-privacy"))
    }


    Spacer(Modifier.height(Space.m))
    Panel {
        ListRow(s.profile, onClick = nav.profile)
        ListRow(s.instructions, onClick = nav.instructions)
        ListRow(t.coachMemory, onClick = nav.memory)
        if (reviews.isNotEmpty()) ListRow(s.reviews, reviews.size.toString(), onClick = nav.reviews)
        else ListRow(s.reviews, onClick = nav.reviews)
        ListRow(requestLogStringsFor(language).title, onClick = nav.requestLog)
    }

    Spacer(Modifier.height(Space.m))
    Panel {
        SwitchRow(s.sendPhotos, settings.sendPhotos, Modifier.testTag("coach:send-photos"), onCheckedChange = coach.settings::setSendPhotos)
    }
    Hint(c.sendPhotosHint)

    val cs = LocalChatStrings.current
    val sync by chatFeature.sync.state.collectAsState()
    val connection = when {
        !chatFeature.configured -> cs.waySoon
        sync.enabled -> cs.wayConnected
        else -> cs.wayNotConnected
    }
    PanelSection(s.sectionPrompts) {
        ListRow(s.copyPrompts, onClick = nav.prompts)
        ChatProvider.entries.forEach { p -> ListRow(p.displayName, connection, onClick = { nav.chatProvider(p) }) }
        ListRow(cs.notesTitle, onClick = nav.chatNotes)
    }
    Hint(s.promptsHint)

    Spacer(Modifier.height(Space.m))
    Panel {
        ListRow(s.story, onClick = nav.story)
    }
}
