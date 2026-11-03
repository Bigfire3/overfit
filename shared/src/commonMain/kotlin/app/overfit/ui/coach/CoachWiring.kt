package app.overfit.ui.coach

import app.overfit.ai.ProviderId
import app.overfit.ai.Providers
import app.overfit.coach.AnthropicCoach
import app.overfit.coach.CoachAccess
import app.overfit.coach.CoachPhotoLoader
import app.overfit.coach.CoachProfile
import app.overfit.coach.CoachService
import app.overfit.coach.CoachTransport
import app.overfit.coach.FuelCoach
import app.overfit.coach.OpenAiCoach
import app.overfit.coach.OpenRouterCoach
import app.overfit.data.AppData
import app.overfit.data.AppStore
import app.overfit.domain.buildProfileBlock
import app.overfit.i18n.coachStringsFor
import app.overfit.platform.prepareJpeg
import kotlinx.datetime.LocalDate

/**
 * Connects the coach to profile, AI access and database. Access: Fuel when chosen and the backend is configured,
 * otherwise the chosen provider when a key is stored.
 */
internal fun createCoach(
    store: AppStore,
    providers: Providers,
    data: AppData,
    today: () -> LocalDate,
    clock: () -> Long,
    photos: CoachPhotoLoader?,
    transportOverride: ((ProviderId) -> CoachTransport)?,
): CoachService {
    val transports = mutableMapOf<ProviderId, CoachTransport>()
    fun transport(id: ProviderId): CoachTransport = transportOverride?.invoke(id) ?: transports.getOrPut(id) {
        when (id) {
            ProviderId.ANTHROPIC -> AnthropicCoach(providers.http, store::apiKey)
            ProviderId.OPENAI -> OpenAiCoach(providers.http, store::apiKey)
            ProviderId.OPENROUTER -> OpenRouterCoach(providers.http, store::apiKey)
            ProviderId.FUEL -> FuelCoach(providers.fuel)
        }
    }
    return CoachService(
        dao = data.database.coachDao(),
        templates = data.templates,
        exercises = data.exercises,
        settings = data.coachSettings,
        access = {
            val s = store.state.value
            when {
                s.providerId == ProviderId.FUEL -> if (providers.fuel.available || transportOverride != null) CoachAccess.Ready(ProviderId.FUEL) else CoachAccess.None
                s.hasApiKey -> CoachAccess.Ready(s.providerId)
                else -> CoachAccess.None
            }
        },
        currentProvider = { store.state.value.providerId },
        transport = ::transport,
        profile = {
            val s = store.state.value
            CoachProfile(buildProfileBlock(s.profile, s.weights, s.language, today()), s.language)
        },
        defaultInstructions = { coachStringsFor(it).defaultInstructions },
        scope = data.scope,
        photos = photos ?: CoachPhotoLoader { name -> data.workouts.photoFiles.read(name)?.let { prepareJpeg(it, maxEdge = 1500) } },
        clock = clock,
    )
}
