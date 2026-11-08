package app.overfit.ui.coach

import app.overfit.ai.ProviderId
import app.overfit.ai.Providers
import app.overfit.coach.asSink
import app.overfit.coach.AnthropicCoach
import app.overfit.coach.CoachAccess
import app.overfit.coach.CoachPhotoLoader
import app.overfit.coach.CoachProfile
import app.overfit.coach.CoachService
import app.overfit.coach.CoachTransport
import app.overfit.coach.FuelCoach
import app.overfit.coach.GeminiCoach
import app.overfit.coach.OpenAiCoach
import app.overfit.coach.OpenRouterCoach
import app.overfit.data.AppData
import app.overfit.data.AppStore
import app.overfit.domain.buildProfileBlock
import app.overfit.i18n.coachStringsFor
import app.overfit.platform.prepareJpeg
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toLocalDateTime

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
    val log = data.database.aiRequestDao().asSink()
    // Every request goes into the request log ("Abrechnung"), whatever the provider.
    fun transport(id: ProviderId): CoachTransport = transportOverride?.let { app.overfit.coach.LoggingTransport(it(id), log, clock) }
        ?: transports.getOrPut(id) {
        val inner = when (id) {
            ProviderId.ANTHROPIC -> AnthropicCoach(providers.http, store::apiKey)
            ProviderId.OPENAI -> OpenAiCoach(providers.http, store::apiKey)
            ProviderId.OPENROUTER -> OpenRouterCoach(providers.http, store::apiKey)
            ProviderId.FUEL -> FuelCoach(providers.fuel)
            ProviderId.GEMINI -> GeminiCoach(providers.http, store::apiKey)
        }
        app.overfit.coach.LoggingTransport(inner, log, clock)
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
        fuelTiers = providers.fuel::tiers,
        fuelConfig = providers.fuel::config,
        fuelBalance = providers.fuel::balance,
        instructionsAddition = { data.coachChatPrefs.state.value.addition },
    )
}

/**
 * The coach chat: same access, transports and model as [coach]; the system prompt gets the profile block and the
 * status of "Copy context" (without its answer instructions). Explicitly attached photos are downscaled like the
 * coach's.
 */
internal fun createCoachChat(
    store: AppStore,
    data: AppData,
    coach: CoachService,
    chat: app.overfit.chat.ChatFeature,
    today: () -> LocalDate,
    clock: () -> Long,
): app.overfit.coach.CoachChatService = app.overfit.coach.CoachChatService(
    dao = data.database.coachChatDao(),
    access = coach::access,
    transport = coach::transportFor,
    model = { coach.settings.state.value.model(it) },
    parts = { request ->
        val s = store.state.value
        val input = chat.contextInput(today())
        val chatStrings = app.overfit.i18n.chatStringsFor(s.language)
        app.overfit.coach.ChatPromptParts(
            instructions = coach.instructions(s.language),
            profileBlock = chatProfileBlock(
                include = request.includeProfile,
                facts = buildProfileBlock(s.profile, s.weights, s.language, today()),
                athlete = data.coachChatPrefs.state.value.profile,
                memory = input.memory,
                language = s.language,
                unit = s.profile.unit,
                today = today(),
            ),
            context = app.overfit.coach.ChatAttachmentText.build(
                request.attachments,
                app.overfit.coach.AttachmentSources(input.workouts, input.templates, s.profile.unit, s.language, clock()),
                chatStrings,
            ),
            exerciseList = coach.exerciseList(),
        )
    },
    photos = { ids ->
        val all = data.workouts.allPhotos().associateBy { it.id }
        ids.mapNotNull { id ->
            val photo = all[id] ?: return@mapNotNull null
            val jpeg = data.workouts.readPhoto(photo)?.let { prepareJpeg(it, maxEdge = 1500) } ?: return@mapNotNull null
            // Label = the photo's calendar day (v14), the same day the calendar shows it on.
            app.overfit.coach.CoachImage(photo.date.toString(), jpeg)
        }
    },
    demo = {
        val h = coachStringsFor(store.state.value.language).hub
        app.overfit.coach.ChatAnswer(h.demoReply, app.overfit.share.ShareFormat.toJson(demoWorkout(h.demoWorkoutName)))
    },
    scope = data.scope,
    clock = clock,
)

/**
 * Profile part of the chat's system prompt. With the profile: the coach profile, name/age/weight (calendar) and the
 * coach memory; without it (chip removed): only reply language, units and today.
 */
internal fun chatProfileBlock(
    include: Boolean,
    facts: String,
    athlete: app.overfit.coach.AthleteProfile,
    memory: String?,
    language: app.overfit.domain.Language,
    unit: app.overfit.domain.WeightUnit,
    today: LocalDate,
): String {
    if (!include) {
        val units = if (unit == app.overfit.domain.WeightUnit.KG) "metric" else "imperial"
        val lang = if (language == app.overfit.domain.Language.DE) "German" else "English"
        return "## Profile\nNot shared in this chat.\nApp language: $lang (answer in the language of the question) · Units: $units · Today: $today"
    }
    return buildString {
        appendLine("## Profile")
        // Chat: the answer follows the language of the question (base instruction); the app language is only a hint.
        appendLine(facts.trim().replace(Regex("Reply language: (\\w+)"), "App language: $1 (answer in the language of the question)"))
        athlete.promptLine()?.let { appendLine(it) }
        memory?.takeIf { it.isNotBlank() }?.let { appendLine().appendLine("## Coach memory").appendLine(it.trim()) }
    }.trimEnd()
}

/** Sample proposal of the chat demo (catalogue exercises, so the import preview finds them). */
internal fun demoWorkout(name: String) = app.overfit.share.SharedTraining(
    name = name,
    exercises = listOf(
        app.overfit.share.SharedExercise(id = "wg:bench-press", name = "Bench Press", restSec = 120, sets = List(3) { app.overfit.share.SharedSet(kg = 50.0, reps = 8) }),
        app.overfit.share.SharedExercise(id = "wg:barbell-row", name = "Barbell Row", restSec = 120, sets = List(3) { app.overfit.share.SharedSet(kg = 40.0, reps = 10) }),
        app.overfit.share.SharedExercise(id = "wg:bodyweight-squat", name = "Bodyweight Squat", restSec = 90, sets = List(3) { app.overfit.share.SharedSet(reps = 15) }),
    ),
)
