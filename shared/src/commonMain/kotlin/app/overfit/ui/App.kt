package app.overfit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.overfit.ai.Providers
import app.overfit.data.backup.BackupContributor
import app.overfit.data.backup.BackupService
import app.overfit.data.AppData
import app.overfit.data.AppStore
import app.overfit.data.KeyValueStore
import app.overfit.chat.ChatFeature
import app.overfit.data.InMemoryKeyValueStore
import app.overfit.fuel.FuelAccess
import app.overfit.fuel.FuelSession
import app.overfit.i18n.LocalFuelStrings
import app.overfit.i18n.fuelStringsFor
import app.overfit.platform.Haptics
import app.overfit.platform.NoHaptics
import app.overfit.ui.fuel.FuelScreen
import app.overfit.ui.fuel.RedeemCodeScreen
import app.overfit.ui.fuel.LedgerScreen
import app.overfit.ui.fuel.RecoveryScreen
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.overfit.ui.chat.AiCoachScreen
import app.overfit.ui.chat.ChatInboxScreen
import app.overfit.ui.chat.ChatNotesScreen
import app.overfit.ui.chat.ChatProvider
import app.overfit.ui.chat.ChatProviderScreen
import app.overfit.ui.chat.ChatViewModel
import app.overfit.i18n.LocalChatStrings
import app.overfit.i18n.chatStringsFor
import app.overfit.i18n.LocalExerciseFormStrings
import app.overfit.i18n.exerciseFormStringsFor
import app.overfit.ai.ExerciseAutofill
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.toRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import app.overfit.i18n.LocalStrings
import app.overfit.i18n.Strings
import app.overfit.i18n.stringsFor
import app.overfit.ui.ai.ConnectAiScreen
import app.overfit.ui.components.ConfirmArea
import app.overfit.ui.components.Hairline
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import app.overfit.ui.components.Icon
import app.overfit.ui.components.LineIcon
import app.overfit.ui.components.Text
import app.overfit.ui.components.Tone
import app.overfit.ui.exercises.CatalogViewModel
import app.overfit.ui.exercises.CustomExerciseScreen
import app.overfit.ui.exercises.ExerciseDetailScreen
import app.overfit.ui.exercises.ExercisePickerScreen
import app.overfit.ui.exercises.ExerciseViewModel
import app.overfit.ui.exercises.ExercisesScreen
import app.overfit.ui.templates.TemplateEditorScreen
import app.overfit.ui.workout.NewWorkoutScreen
import app.overfit.ui.templates.WorkoutPlanScreen
import app.overfit.ui.main.lastDoneText
import app.overfit.ui.workout.MiniWorkoutBar
import app.overfit.domain.Workout
import app.overfit.ui.workout.WorkoutScreen
import app.overfit.ui.templates.TemplateEditorViewModel
import app.overfit.ui.history.FinishScreen
import app.overfit.ui.history.WorkoutDetailScreen
import app.overfit.ui.history.WorkoutEditScreen
import app.overfit.ui.photos.DayScreen
import app.overfit.ui.photos.dayPhotoTarget
import app.overfit.platform.prepareJpeg
import app.overfit.share.ShareMapper
import app.overfit.ui.components.Chip
import app.overfit.ui.photos.PhotosScreen
import app.overfit.ui.photos.PhotoViewerScreen
import app.overfit.ui.photos.PhotoCompareScreen
import app.overfit.ui.settings.BackupImportScreen
import app.overfit.platform.rememberFilePicker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import app.overfit.ui.main.StartScreen
import app.overfit.ui.onboarding.AgeStep
import app.overfit.ui.onboarding.NameStep
import app.overfit.ui.onboarding.ONBOARDING_STEPS
import app.overfit.ui.onboarding.OnboardingHeader
import app.overfit.ui.onboarding.WeightStep
import app.overfit.ui.settings.AboutScreen
import app.overfit.share.ImportInbox
import app.overfit.share.NoopPlatformShare
import app.overfit.share.PlatformShare
import app.overfit.ui.share.ImportScreen
import app.overfit.ui.share.ImportViewModel
import app.overfit.ui.share.ShareDependencies
import app.overfit.ui.share.ShareScreen
import app.overfit.ui.share.ShareViewModel
import app.overfit.ai.ProviderId
import app.overfit.coach.CoachPhotoLoader
import app.overfit.coach.CoachService
import app.overfit.coach.CoachTransport
import app.overfit.i18n.LocalCoachStrings
import app.overfit.i18n.coachStringsFor
import app.overfit.ui.coach.CoachDemoScreen
import app.overfit.ui.coach.CoachBackup
import app.overfit.ui.coach.CoachFinishChoice
import app.overfit.ui.coach.CoachFinishSection
import app.overfit.ui.coach.CoachHomeScreen
import app.overfit.ui.coach.CoachChatScreen
import app.overfit.ui.coach.CoachStoryScreen
import app.overfit.ui.coach.StoryTarget
import app.overfit.ui.coach.CoachTabNav
import app.overfit.ui.coach.CoachTabScreen
import app.overfit.ui.coach.CoachMemoryScreen
import app.overfit.ui.coach.CoachNav
import app.overfit.ui.coach.CoachReviewScreen
import app.overfit.ui.coach.CoachSessionScreen
import app.overfit.ui.coach.CoachSettingsScreen
import app.overfit.ui.coach.createCoach
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.overfit.ui.settings.SettingsScreen
import app.overfit.ui.theme.OverfitTheme
import app.overfit.ui.theme.Space
import app.overfit.ui.theme.Theme
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlinx.serialization.Serializable
import app.overfit.platform.NoRestAlerts
import app.overfit.platform.RestAlerts
import app.overfit.workout.WorkoutController
import app.overfit.workout.WorkoutPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlin.reflect.KClass
import kotlin.time.Clock

/** Everything the UI needs from the platform shell (Android activity / iOS view controller). */
class AppDependencies(
    val store: AppStore,
    val providers: Providers,
    val data: AppData,
    val versionName: String,
    val share: PlatformShare = NoopPlatformShare(),
    val inbox: ImportInbox = ImportInbox(),
    val today: () -> LocalDate = { Clock.System.todayIn(TimeZone.currentSystemDefault()) },
    val haptics: Haptics = NoHaptics,
    val restAlerts: RestAlerts = NoRestAlerts,
    val clock: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    /** Main-thread scope that lives as long as the app (workout state, rest timer). */
    workoutScope: CoroutineScope = MainScope(),
    /** credit backend (URL from the build, developer override); defaults to "not configured". */
    val fuel: FuelAccess = FuelAccess(InMemoryKeyValueStore(), "") { error("No backend") },
    /** Small key-value storage for the chat sync state (not secret). */
    val kv: KeyValueStore = InMemoryKeyValueStore(),
    /** Body photos for the coach; null = the workout photos, downscaled (tests replace it). */
    coachPhotos: CoachPhotoLoader? = null,
    /** Replaces the real providers (tests). */
    coachTransport: ((ProviderId) -> CoachTransport)? = null,
    /** How this build was installed; decides with `/v1/config` whether "Top up on the web" is shown. */
    val distribution: app.overfit.fuel.Distribution = app.overfit.fuel.Distribution.Sideload,
    /** The Coach story moves on by itself after a few seconds (tests switch it off). */
    val coachStoryAutoAdvance: Boolean = true,
) {
    val fuelSession = FuelSession(fuel)
    /** The AI coach: requests, queue, reviews, memory. Runs in the app scope. */
    val coach: CoachService = createCoach(store, providers, data, today, clock, coachPhotos, coachTransport)
    /** "Fill in with AI" for custom exercises: same access, provider and model as the coach. */
    val exerciseAutofill = ExerciseAutofill(coach.ai)
    /** KI-Chat-Verbindung (Claude, ChatGPT, Gemini): sync, inbox, notes. The coach memory goes along as memory/coach. */
    val chat: ChatFeature = ChatFeature(fuel, data, store, kv, data.scope, clock, memory = { coach.memoryText })
    /** Coach chat in the Coach tab (own key or Fuel, demo without access). */
    val coachChat: app.overfit.coach.CoachChatService = app.overfit.ui.coach.createCoachChat(store, data, coach, chat, today, clock)

    init {
        // Every change of the coach memory (coach review, edit, delete) is synced to the connected chats.
        data.scope.launch {
            runCatching { coach.memory.collect { coach.memoryText = it?.text; chat.sync.markMemory() } }
                .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
        }
    }

    /**
     * Other features add their data to the backup here (coach memory, AI chat settings), see
     * [BackupContributor]. Register before the UI starts.
     */
    val backupContributors = mutableListOf<BackupContributor>()

    /**
     * Counter raised when a notification (rest timer, running workout) is tapped: the UI then always shows
     * the maximized workout, also from the minimized view. After a process restart the workout comes back maximized anyway.
     */
    val openWorkoutRequests = MutableStateFlow(0)
    fun requestOpenWorkout() { openWorkoutRequests.value += 1 }

    /** Backup file picked in the settings, waiting for the import preview ([BackupImportRoute]). */
    val pendingBackup = MutableStateFlow<ByteArray?>(null)

    /**
     * Phase 4 (coach): content at the end of the finish flow, below note/feeling/photos and
     * above "Save" – e.g. "Send to coach" / "Later". Empty until the coach is wired in.
     */
    var finishCoachSection: @Composable (workoutId: String) -> Unit = {}

    /**
     * Called (on the main thread) when the finish flow is saved: note, feeling and photos are
     * stored. Phase 4 queues the coach review here, the AI chat sync can push the workout.
     */
    val workoutSavedListeners = mutableListOf<(workoutId: String) -> Unit>()

    init {
        // Coach (phase 4): choice at the end of the finish flow, review queued on "Save", memory/reviews in the backup.
        val coachChoice = CoachFinishChoice()
        finishCoachSection = { id -> CoachFinishSection(coach, coachChoice, id) }
        workoutSavedListeners += { id -> if (coachChoice.send && coach.access() is app.overfit.coach.CoachAccess.Ready) coach.startSessionReview(id) }
        backupContributors += CoachBackup(coach)
        backupContributors += app.overfit.ui.coach.CoachChatBackup(coachChat)
        coachChat.recover()
    }
    val backup = BackupService(data, store, versionName, clock) { backupContributors.toList() }
    /** The running workout; created once per process, loads the stored workout right away. */
    val workout: WorkoutController = WorkoutController(
        store = data.workouts,
        alerts = restAlerts,
        haptics = haptics,
        prefs = {
            val s = store.state.value
            WorkoutPrefs(s.settings.defaultRestSec, s.settings.vibration, s.profile.unit, s.language, s.settings.restTicks)
        },
        scope = workoutScope,
        clock = clock,
        // A workout that ends passes its structure on to its saved workout (or becomes one).
        plans = { data.templates.adopt(it) },
    )
}

// Type-safe routes.
@Serializable data object NameRoute
@Serializable data object AgeRoute
@Serializable data object WeightRoute
@Serializable data object AiRoute
@Serializable data object StartRoute
@Serializable data object StartWorkoutRoute
@Serializable data object WorkoutRoute
@Serializable data object ExercisesRoute
@Serializable data object PhotosRoute
@Serializable data object SettingsRoute
@Serializable data object ConnectRoute
@Serializable data object AiCoachRoute
@Serializable data class ChatProviderRoute(val provider: String)
@Serializable data object ChatInboxRoute
@Serializable data object ChatNotesRoute
@Serializable data object CoachMemoryRoute
@Serializable data object CoachRoute
/** Full-screen story from the Coach title: what the AI can evaluate and the ways to use it. */
@Serializable data object CoachStoryRoute
/** Coach chat; [chatId] empty = new chat, [draft] prefills the input ("Use in chat"). */
@Serializable data class CoachChatRoute(val chatId: String = "", val draft: String = "")
@Serializable data object CoachReviewsRoute
@Serializable data object CoachSettingsRoute
@Serializable data class CoachSessionRoute(val sessionId: String)
@Serializable data class CoachReviewRoute(val reviewId: String)
@Serializable data class CoachDemoRoute(val sessionId: String)
@Serializable data object AboutRoute
@Serializable data object FuelRoute
@Serializable data object LedgerRoute
/** Every AI request ("Abrechnung"): time, purpose, model, tokens, cost, status. */
@Serializable data object RequestLogRoute
@Serializable data object RedeemCodeRoute
@Serializable data object RecoveryRoute
@Serializable data object ImportRoute
@Serializable data class ShareRoute(val templateId: Long)
@Serializable data class ExerciseDetailRoute(val id: String, val readOnly: Boolean = false)
/** Finish flow right after "Finish": summary, feeling, note, photos, coach slot. */
@Serializable data class FinishRoute(val workoutId: String)
/** A calendar day (ISO date, e.g. 2026-11-03): its photos and trainings. */
@Serializable data class DayRoute(val date: String, val focusWeight: Boolean = false)
/** One finished training (from its calendar day or a photo). */
@Serializable data class WorkoutDetailRoute(val workoutId: String)
/** Edit a finished training afterwards. */
@Serializable data class WorkoutEditRoute(val workoutId: String)
/** Share a finished workout (`kind: "workout"`); [withActual] = logged values instead of targets. */
@Serializable data class WorkoutShareRoute(val workoutId: String, val withActual: Boolean = true)
@Serializable data class PhotoViewerRoute(val photoId: String)
@Serializable data class PhotoCompareRoute(val firstId: String, val secondId: String)
/** Preview and confirm a backup import; the file waits in [AppDependencies.pendingBackup]. */
@Serializable data object BackupImportRoute
/**
 * Custom exercise; [id] empty = new one (prefilled with [name], e.g. the search text). [pick]: opened from the
 * exercise picker, the saved exercise goes straight back into the picker's caller (template or running workout).
 */
@Serializable data class CustomExerciseRoute(val id: String = "", val name: String = "", val pick: Boolean = false)
/** A saved workout with "Start", edit and share. */
@Serializable data class WorkoutPlanRoute(val templateId: Long)
/** Template editor; [templateId] 0 = new template. */
@Serializable data class TemplateEditorRoute(val templateId: Long = 0)
/** Exercise picker; [single] = replace mode. The result goes through [PickedExercises]. */
@Serializable data class ExercisePickerRoute(val single: Boolean = false)

/** Hands the picker's result back to the editor below it on the back stack. */
class PickedExercises {
    val result = MutableStateFlow<List<String>?>(null)
    /** Exercise created (or chosen under "Did you mean …?") in the form opened from the picker. */
    val created = MutableStateFlow<String?>(null)
}

enum class Tab(val route: Any, val icon: Icon, val label: (Strings) -> String) {
    START(StartRoute, Icon.Workouts, { it.navStart }),
    COACH(CoachRoute, Icon.Coach, { it.navCoach }),
    PHOTOS(PhotosRoute, Icon.Calendar, { it.navPhotos }),
    PROFILE(SettingsRoute, Icon.Profile, { it.navProfile });

    val routeClass: KClass<*> get() = route::class
}

@Composable
fun OverfitApp(deps: AppDependencies) {
    val state by deps.store.state.collectAsStateWithLifecycle()
    val templates by deps.data.templates.templates.collectAsStateWithLifecycle(initialValue = null)
    val picked = remember { PickedExercises() }
    val workoutState by deps.workout.state.collectAsStateWithLifecycle()
    val share = remember(deps) { ShareDependencies(deps.share, deps.data.importer, deps.data.templates, deps.inbox, deps.data.scope) {
        val s = deps.store.state.value
        app.overfit.domain.aiProfileHeader(s.profile, s.weights, deps.today(), s.language)
    } }
    val incoming by deps.inbox.pending.collectAsStateWithLifecycle()
    // The image loader is not set here: a process-wide singleton set during composition breaks as soon as
    // anything drew an image before (tests, previews). Android provides it via the Application, iOS sets it
    // before the first screen (see [overfitImageLoader]).
    // App scope: the first saved workout changes the screen, a composition scope would cancel the seeding half-way.
    LaunchedEffect(Unit) { deps.data.seedCatalog(); deps.data.scope.launch { deps.data.seedSamplesOnce() } }
    // Body weight: take over the profile list once, then mirror the table into the profile (AI context reads it there).
    LaunchedEffect(deps) { app.overfit.data.syncProfileWeights(deps.data, deps.store, deps.kv) }
    // KI-Chat: back in the foreground → send what is left and fetch the inbox; to the background → send a running workout now.
    // Queued coach requests go out on app start and whenever the app comes back to the foreground.
    LifecycleResumeEffect(deps) {
        deps.chat.onForeground()
        deps.coach.processQueue()
        onPauseOrDispose { deps.chat.onBackground() }
    }
    val chatInbox by deps.chat.inbox.state.collectAsStateWithLifecycle()
    OverfitTheme {
        CompositionLocalProvider(
            LocalStrings provides stringsFor(state.language),
            LocalFuelStrings provides fuelStringsFor(state.language),
            LocalChatStrings provides chatStringsFor(state.language),
            LocalCoachStrings provides coachStringsFor(state.language),
            LocalExerciseFormStrings provides exerciseFormStringsFor(state.language),
        ) {
            val nav = rememberNavController()
            val coachNav = remember(nav) {
                CoachNav(
                    back = { nav.popBackStack() },
                    connect = { nav.navigate(ConnectRoute) },
                    fuel = { nav.navigate(FuelRoute) },
                    openReview = { nav.navigate(CoachReviewRoute(it)) },
                    openSession = { nav.navigate(CoachSessionRoute(it)) },
                    demo = { nav.navigate(CoachDemoRoute(it)) },
                    memory = { nav.navigate(CoachMemoryRoute) },
                    settings = { nav.navigate(CoachSettingsRoute) },
                    openTemplate = { nav.navigate(WorkoutPlanRoute(it)) },
                )
            }
            val start = remember { if (state.onboardingDone) StartRoute else NameRoute }
            // Pick up a running workout after the app was killed: once, as soon as it is loaded.
            var resumeChecked by rememberSaveable { mutableStateOf(false) }
            LaunchedEffect(workoutState.loaded) {
                if (resumeChecked || !workoutState.loaded) return@LaunchedEffect
                resumeChecked = true
                if (workoutState.workout != null && state.onboardingDone) nav.navigate(WorkoutRoute)
            }
            // Tap on the rest/workout notification → maximized workout (from anywhere, also when minimized).
            val openRequests by deps.openWorkoutRequests.collectAsStateWithLifecycle()
            var handledOpenRequests by remember { mutableStateOf(openRequests) }
            LaunchedEffect(openRequests) {
                if (openRequests == handledOpenRequests) return@LaunchedEffect
                handledOpenRequests = openRequests
                if (workoutState.workout != null && state.onboardingDone) nav.openWorkout()
            }
            // Links, files and scans from outside open the import screen (not during onboarding).
            LaunchedEffect(incoming, state.onboardingDone) {
                val link = incoming
                // Back from the web purchase page (overfit://guthaben): credit screen, which reloads the balance.
                if (link != null && state.onboardingDone && app.overfit.fuel.isCreditReturnLink(link)) {
                    deps.inbox.consume()
                    nav.navigate(FuelRoute) { launchSingleTop = true }
                    return@LaunchedEffect
                }
                if (incoming != null && state.onboardingDone &&
                    nav.currentDestination?.hasRoute(ImportRoute::class) != true
                ) {
                    nav.navigate(ImportRoute) { launchSingleTop = true }
                }
            }
            ConfirmArea(Modifier.fillMaxSize()) {
            AppScaffold(nav, workoutState.workout, deps.clock, onOpenWorkout = { nav.openWorkout() }) {
                NavHost(
                    nav,
                    startDestination = start,
                    modifier = Modifier.fillMaxSize(),
                    // One calm transition for the whole app (see DECISIONS "Übergänge"): tab switches are instant,
                    // everything else a short fade-through. The default (700 ms crossfade) looked buggy.
                    enterTransition = { ScreenMotion.enter(initialState.destination, targetState.destination) },
                    exitTransition = { ScreenMotion.exit(initialState.destination, targetState.destination) },
                    popEnterTransition = { ScreenMotion.enter(initialState.destination, targetState.destination) },
                    popExitTransition = { ScreenMotion.exit(initialState.destination, targetState.destination) },
                    sizeTransform = null,
                ) {
                    composable<NameRoute> {
                        NameStep(state.profile.name, onNext = { deps.store.setName(it); nav.navigate(AgeRoute) })
                    }
                    composable<AgeRoute> {
                        AgeStep(state.profile.birthYear, deps.today(), onBack = nav::popBackStack) {
                            deps.store.setBirthYear(it)
                            nav.navigate(WeightRoute)
                        }
                    }
                    composable<WeightRoute> {
                        WeightStep(
                            weights = state.weights,
                            unit = state.profile.unit,
                            language = state.language,
                            onUnitChange = deps.store::setUnit,
                            onBack = nav::popBackStack,
                        ) {
                            deps.store.logWeight(it, deps.today())
                            val kg = it
                            deps.data.scope.launch { deps.data.bodyWeights.set(deps.today(), kg) }
                            nav.navigate(AiRoute)
                        }
                    }
                    composable<AiRoute> {
                        val finish = {
                            deps.store.completeOnboarding()
                            nav.navigate(StartRoute) { popUpTo(0) { inclusive = true } }
                        }
                        ConnectAiScreen(
                            providers = deps.providers,
                            initialProvider = state.providerId,
                            header = { OnboardingHeader(ONBOARDING_STEPS, onBack = nav::popBackStack) },
                            onConnected = { id, key -> deps.store.connectAi(id, key); finish() },
                            onSkip = finish,
                            onUseFuel = { deps.store.useFuel(); finish(); nav.navigate(FuelRoute) },
                        )
                    }

                    composable<StartRoute> {
                        val lastDone by deps.data.templates.lastDone.collectAsStateWithLifecycle(initialValue = emptyMap())
                        StartScreen(
                            templates = templates,
                            lastDone = lastDone,
                            today = deps.today(),
                            running = workoutState.workout,
                            onOpenRunning = { nav.openWorkout() },
                            onEndRunning = {
                                // Ends the running workout (saved, finish flow): the workout screen takes it from there. Runs in the app scope.
                                deps.data.scope.launch { deps.workout.finish() }
                                nav.openWorkout()
                            },
                            onNew = { nav.navigate(StartWorkoutRoute) },
                            onExercises = { nav.navigate(ExercisesRoute) },
                            onOpenWorkout = { nav.navigate(WorkoutPlanRoute(it)) },
                            share = share,
                            chatInboxCount = chatInbox.items.size,
                            chatInboxFull = chatInbox.full,
                            onChatInbox = { nav.navigate(ChatInboxRoute) },
                        )
                    }
                    composable<StartWorkoutRoute> {
                        NewWorkoutScreen(
                            workoutRunning = workoutState.workout != null,
                            share = share,
                            onBack = nav::popBackStack,
                            onCreate = { nav.navigate(TemplateEditorRoute()) { popUpTo(StartRoute) } },
                            onStartEmpty = {
                                deps.workout.startEmpty()
                                nav.navigate(WorkoutRoute) { popUpTo(StartRoute) }
                            },
                            onImport = { nav.navigate(ImportRoute) { popUpTo(StartRoute) } },
                            onOpenRunning = { nav.openWorkout() },
                            onEndRunning = { deps.data.scope.launch { deps.workout.finish() }; nav.openWorkout() },
                        )
                    }
                    composable<WorkoutPlanRoute> { entry ->
                        val id = entry.toRoute<WorkoutPlanRoute>().templateId
                        val scope = rememberCoroutineScope()
                        val lastDone by deps.data.templates.lastDone.collectAsStateWithLifecycle(initialValue = emptyMap())
                        WorkoutPlanScreen(
                            templateId = id,
                            templates = deps.data.templates,
                            lastDone = lastDoneText(LocalStrings.current, lastDone[id], deps.today()),
                            unit = state.profile.unit,
                            language = state.language,
                            workoutRunning = workoutState.workout != null,
                            onBack = nav::popBackStack,
                            onStart = { template ->
                                scope.launch {
                                    if (deps.workout.startFromTemplate(template)) nav.navigate(WorkoutRoute) { popUpTo(StartRoute) }
                                }
                            },
                            onToRunning = { nav.openWorkout() },
                            onEndRunning = { deps.data.scope.launch { deps.workout.finish() }; nav.openWorkout() },
                            onEdit = { nav.navigate(TemplateEditorRoute(id)) },
                            onShare = { nav.navigate(ShareRoute(id)) },
                            // App scope: the screen closes itself as soon as the workout is gone.
                            onDelete = { deps.data.scope.launch { deps.data.templates.delete(id) } },
                            onExerciseDetails = { nav.navigate(ExerciseDetailRoute(it, readOnly = true)) },
                            loadLast = { deps.data.workouts.lastPerformance(it, id) },
                        )
                    }
                    composable<WorkoutRoute> {
                        var pendingReplaceId by rememberSaveable { mutableStateOf<String?>(null) }
                        val result by picked.result.collectAsStateWithLifecycle()
                        LaunchedEffect(result) {
                            val ids = result ?: return@LaunchedEffect
                            picked.result.value = null
                            val replaceId = pendingReplaceId
                            pendingReplaceId = null
                            if (ids.isEmpty()) return@LaunchedEffect
                            // Taken from the relay and handed to the controller without a suspension point in between,
                            // so the pick is neither lost nor applied twice if this screen leaves the composition.
                            deps.workout.addPicked(replaceId) { deps.data.exercises.getAll(ids) }
                        }
                        WorkoutScreen(
                            controller = deps.workout,
                            unit = state.profile.unit,
                            language = state.language,
                            clock = deps.clock,
                            onAddExercises = { pendingReplaceId = null; nav.navigate(ExercisePickerRoute(single = false)) },
                            onReplaceExercise = { pendingReplaceId = it; nav.navigate(ExercisePickerRoute(single = true)) },
                            onExerciseDetails = { nav.navigate(ExerciseDetailRoute(it, readOnly = true)) },
                            onClosed = { nav.navigate(StartRoute) { popUpTo(0) { inclusive = true } } },
                            onMinimize = { if (!nav.popBackStack()) nav.navigate(StartRoute) { popUpTo(0) { inclusive = true } } },
                            onFinished = { id -> nav.navigate(FinishRoute(id)) { popUpTo(StartRoute) } },
                        )
                    }
                    composable<FinishRoute> { entry ->
                        FinishScreen(
                            workoutId = entry.toRoute<FinishRoute>().workoutId,
                            workouts = deps.data.workouts,
                            awaitFinished = deps.workout::awaitFinished,
                            unit = state.profile.unit,
                            language = state.language,
                            appScope = deps.data.scope,
                            coachSection = deps.finishCoachSection,
                            onSaved = { id -> deps.workoutSavedListeners.toList().forEach { it(id) } },
                            onClose = { nav.navigate(StartRoute) { popUpTo(0) { inclusive = true } } },
                            rename = { w, name ->
                                deps.data.workouts.rename(w.id, name)
                                w.templateId?.let { deps.data.templates.rename(it, name) }
                            },
                        )
                    }
                    composable<DayRoute> { entry ->
                        val route = entry.toRoute<DayRoute>()
                        val date = LocalDate.parse(route.date)
                        val photos by deps.data.workouts.photos.collectAsStateWithLifecycle(initialValue = null)
                        val history by deps.data.workouts.history.collectAsStateWithLifecycle(initialValue = null)
                        val weights by deps.data.bodyWeights.entries.collectAsStateWithLifecycle(initialValue = null)
                        DayScreen(
                            weights = weights,
                            focusWeight = route.focusWeight,
                            // App scope: leaving the day right after typing must not drop the value.
                            onSetWeight = { kg -> deps.data.scope.launch { deps.data.bodyWeights.set(date, kg) } },
                            onDeleteWeight = { deps.data.scope.launch { deps.data.bodyWeights.delete(date) } },
                            date = date,
                            photos = photos,
                            history = history,
                            unit = state.profile.unit,
                            language = state.language,
                            onBack = nav::popBackStack,
                            onOpenPhoto = { nav.navigate(PhotoViewerRoute(it)) },
                            onOpenWorkout = { nav.navigate(WorkoutDetailRoute(it)) },
                            onAddPhoto = { bytes ->
                                // App scope: leaving the day must not drop a photo that is still being prepared.
                                deps.data.scope.launch {
                                    val jpeg = withContext(Dispatchers.Default) { prepareJpeg(bytes) } ?: return@launch
                                    // Read the trainings now (not the screen's state, which may still be loading).
                                    val workoutsNow = deps.data.workouts.history.first()
                                    val target = dayPhotoTarget(date, deps.today(), workoutsNow, deps.clock())
                                    deps.data.workouts.addPhoto(target.sessionId, jpeg, target.takenAt)
                                }
                            },
                            onRemovePhoto = { id -> deps.data.scope.launch { deps.data.workouts.deletePhoto(id) } },
                        )
                    }
                    composable<WorkoutDetailRoute> { entry ->
                        val id = entry.toRoute<WorkoutDetailRoute>().workoutId
                        val chatNotes by deps.chat.notes.notes.collectAsStateWithLifecycle()
                        WorkoutDetailScreen(
                            workoutId = id,
                            chatNotes = chatNotes.filter { it.workoutId == id },
                            workouts = deps.data.workouts,
                            templates = deps.data.templates,
                            unit = state.profile.unit,
                            language = state.language,
                            appScope = deps.data.scope,
                            onBack = nav::popBackStack,
                            onOpenPhoto = { nav.navigate(PhotoViewerRoute(it)) },
                            onTemplateCreated = { nav.navigate(WorkoutPlanRoute(it)) },
                            onShare = { nav.navigate(WorkoutShareRoute(id, withActual = true)) },
                            onCoach = { nav.navigate(CoachSessionRoute(id)) },
                            onEdit = { nav.navigate(WorkoutEditRoute(id)) },
                            onExerciseDetails = { nav.navigate(ExerciseDetailRoute(it, readOnly = true)) },
                        )
                    }
                    composable<WorkoutEditRoute> { entry ->
                        WorkoutEditScreen(
                            workoutId = entry.toRoute<WorkoutEditRoute>().workoutId,
                            workouts = deps.data.workouts,
                            unit = state.profile.unit,
                            language = state.language,
                            appScope = deps.data.scope,
                            onClose = nav::popBackStack,
                            onExerciseDetails = { nav.navigate(ExerciseDetailRoute(it, readOnly = true)) },
                        )
                    }
                    composable<WorkoutShareRoute> { entry ->
                        val route = entry.toRoute<WorkoutShareRoute>()
                        var withActual by rememberSaveable { mutableStateOf(route.withActual) }
                        ShareScreen(
                            viewModel = viewModel(key = "workout-share-${route.workoutId}-$withActual") {
                                ShareViewModel({ deps.data.workouts.getFinished(route.workoutId)?.workout?.let { ShareMapper.fromWorkout(it, withActual) } }, share.shortCodes)
                            },
                            platform = deps.share,
                            onBack = nav::popBackStack,
                            description = LocalStrings.current.history.shareWorkoutHint,
                            extra = {
                                val h = LocalStrings.current.history
                                Chip(h.withActual, withActual, { withActual = !withActual })
                                Spacer(Modifier.height(Space.s))
                                Text(h.withActualHint(withActual), style = Theme.type.caption, tone = Tone.Muted)
                            },
                            unit = state.profile.unit,
                            language = state.language,
                        )
                    }
                    composable<ExercisesRoute> {
                        val catalog = viewModel { CatalogViewModel(deps.data.exercises) }
                        ExercisesScreen(
                            catalog = catalog,
                            language = state.language,
                            onOpenExercise = { nav.navigate(ExerciseDetailRoute(it.id)) },
                            onNewCustomExercise = { nav.navigate(CustomExerciseRoute(name = catalog.filter.value.query.trim())) },
                            onBack = nav::popBackStack,
                        )
                    }
                    composable<ExerciseDetailRoute> { entry ->
                        val route = entry.toRoute<ExerciseDetailRoute>()
                        val id = route.id
                        ExerciseDetailScreen(
                            readOnly = route.readOnly,
                            viewModel = viewModel { ExerciseViewModel(id, deps.data.exercises) },
                            workouts = deps.data.workouts,
                            unit = state.profile.unit,
                            language = state.language,
                            onBack = nav::popBackStack,
                            onEditCustom = { nav.navigate(CustomExerciseRoute(id = it)) },
                        )
                    }
                    composable<CustomExerciseRoute> { entry ->
                        val route = entry.toRoute<CustomExerciseRoute>()
                        CustomExerciseScreen(
                            viewModel = viewModel { ExerciseViewModel(route.id.ifEmpty { null }, deps.data.exercises, deps.exerciseAutofill) },
                            initialName = route.name,
                            language = state.language,
                            onBack = nav::popBackStack,
                            onSaved = { id ->
                                if (route.pick) picked.created.value = id
                                nav.popBackStack()
                            },
                            onPickExisting = { existing ->
                                if (route.pick) {
                                    picked.created.value = existing.id
                                    nav.popBackStack()
                                } else {
                                    nav.navigate(ExerciseDetailRoute(existing.id)) { popUpTo<CustomExerciseRoute> { inclusive = true } }
                                }
                            },
                        )
                    }
                    composable<TemplateEditorRoute> { entry ->
                        val id = entry.toRoute<TemplateEditorRoute>().templateId
                        val editor = viewModel {
                            TemplateEditorViewModel(id, deps.data.templates, deps.data.exercises, state.profile.unit, state.language)
                        }
                        val result by picked.result.collectAsStateWithLifecycle()
                        LaunchedEffect(result) {
                            result?.let { editor.onExercisesPicked(it); picked.result.value = null }
                        }
                        TemplateEditorScreen(
                            viewModel = editor,
                            unit = state.profile.unit,
                            onClose = nav::popBackStack,
                            onAddExercises = { nav.navigate(ExercisePickerRoute(single = false)) },
                            onReplaceExercise = { nav.navigate(ExercisePickerRoute(single = true)) },
                            onExerciseDetails = { nav.navigate(ExerciseDetailRoute(it)) },
                        )
                    }
                    composable<ExercisePickerRoute> { entry ->
                        val single = entry.toRoute<ExercisePickerRoute>().single
                        val pickedCreated by picked.created.collectAsStateWithLifecycle()
                        ExercisePickerScreen(
                            catalog = viewModel { CatalogViewModel(deps.data.exercises) },
                            language = state.language,
                            single = single,
                            onBack = nav::popBackStack,
                            onPicked = { ids -> picked.result.value = ids; nav.popBackStack() },
                            onCreate = { query -> nav.navigate(CustomExerciseRoute(name = query, pick = true)) },
                            onDetails = { nav.navigate(ExerciseDetailRoute(it)) },
                            created = pickedCreated,
                            onCreatedConsumed = { picked.created.value = null },
                        )
                    }
                    composable<PhotosRoute> {
                        val photos by deps.data.workouts.photos.collectAsStateWithLifecycle(initialValue = null)
                        val history by deps.data.workouts.history.collectAsStateWithLifecycle(initialValue = null)
                        val viewPref = remember { app.overfit.ui.photos.DiaryViewPref(deps.kv) }
                        val weights by deps.data.bodyWeights.entries.collectAsStateWithLifecycle(initialValue = null)
                        var showMuscles by rememberSaveable { mutableStateOf(false) }
                        PhotosScreen(
                            weights = weights,
                            onWeightToday = { nav.navigate(DayRoute(deps.today().toString(), focusWeight = true)) },
                            photos = photos,
                            history = history,
                            language = state.language,
                            today = deps.today(),
                            onDay = { nav.navigate(DayRoute(it.toString())) },
                            onCompare = { a, b -> nav.navigate(PhotoCompareRoute(a, b)) },
                            unit = state.profile.unit,
                            listView = viewPref.list,
                            onListViewChange = { viewPref.list = it },
                            onMuscles = { showMuscles = true },
                        )
                        if (showMuscles) {
                            val muscleSets by deps.data.workouts.muscleSets.collectAsStateWithLifecycle(initialValue = null)
                            val periodPref = remember { app.overfit.ui.muscles.MusclePeriodPref(deps.kv) }
                            var period by remember { mutableStateOf(periodPref.period) }
                            app.overfit.ui.muscles.MuscleMapSheet(
                                rows = muscleSets,
                                period = period,
                                today = deps.today(),
                                weekStart = app.overfit.domain.DiaryCalendar.weekStart(state.language),
                                onPeriod = { period = it; periodPref.period = it },
                                onDismiss = { showMuscles = false },
                            )
                        }
                    }
                    composable<PhotoViewerRoute> { entry ->
                        val id = entry.toRoute<PhotoViewerRoute>().photoId
                        val photos by deps.data.workouts.photos.collectAsStateWithLifecycle(initialValue = null)
                        val history by deps.data.workouts.history.collectAsStateWithLifecycle(initialValue = null)
                        PhotoViewerScreen(
                            photos = photos,
                            history = history,
                            photoId = id,
                            language = state.language,
                            unit = state.profile.unit,
                            platform = deps.share,
                            loadSummary = { deps.data.workouts.summary(it) },
                            loadJpeg = { deps.data.workouts.photoJpeg(it) },
                            onBack = nav::popBackStack,
                            onWorkout = { nav.navigate(WorkoutDetailRoute(it)) },
                            onDelete = { photoId ->
                                deps.data.scope.launch {
                                    deps.data.workouts.deletePhoto(photoId)
                                    withContext(Dispatchers.Main) { nav.popBackStack() }
                                }
                            },
                        )
                    }
                    composable<PhotoCompareRoute> { entry ->
                        val route = entry.toRoute<PhotoCompareRoute>()
                        val photos by deps.data.workouts.photos.collectAsStateWithLifecycle(initialValue = null)
                        PhotoCompareScreen(photos, route.firstId, route.secondId, onBack = nav::popBackStack)
                    }
                    composable<BackupImportRoute> {
                        BackupImportScreen(
                            backup = deps.backup,
                            pending = deps.pendingBackup,
                            workoutRunning = workoutState.workout != null,
                            appScope = deps.data.scope,
                            onBack = nav::popBackStack,
                            onRestored = { nav.navigate(StartRoute) { popUpTo(0) { inclusive = true } } },
                        )
                    }
                    composable<SettingsRoute> {
                        var exportingBackup by remember { mutableStateOf(false) }
                        val pickBackup = rememberFilePicker { bytes ->
                            deps.pendingBackup.value = bytes
                            nav.navigate(BackupImportRoute)
                        }
                        SettingsScreen(
                            state = state,
                            store = deps.store,
                            providers = deps.providers,
                            today = deps.today(),
                            onConnect = { nav.navigate(ConnectRoute) },
                            onCoachMemory = { nav.navigate(CoachMemoryRoute) },
                            onCoach = { nav.selectTab(Tab.COACH) },
                            onAbout = { nav.navigate(AboutRoute) },
                            fuelSession = deps.fuelSession,
                            onFuel = { nav.navigate(FuelRoute) },
                            onAiCoach = { nav.navigate(AiCoachRoute) },
                            onChatNotes = { nav.navigate(ChatNotesRoute) },
                            restAlerts = deps.restAlerts,
                            share = share,
                            exportingBackup = exportingBackup,
                            onExportBackup = {
                                exportingBackup = true
                                deps.data.scope.launch {
                                    val bytes = try { deps.backup.export() } catch (e: Exception) { null }
                                    withContext(Dispatchers.Main) {
                                        exportingBackup = false
                                        if (bytes != null) deps.share.shareBytes(BackupService.fileName(deps.today().toString()), bytes, BackupService.MIME_TYPE)
                                    }
                                }
                            },
                            onImportBackup = pickBackup,
                            onReset = {
                                deps.store.resetAll()
                                deps.data.scope.launch { deps.workout.discard(keepStructure = false); deps.data.resetAll() }
                                nav.navigate(NameRoute) { popUpTo(0) { inclusive = true } }
                            },
                        )
                    }
                    composable<ConnectRoute> {
                        ConnectAiScreen(
                            providers = deps.providers,
                            initialProvider = state.providerId,
                            header = { app.overfit.ui.components.BackHeader(LocalStrings.current.back, nav::popBackStack) },
                            onConnected = { id, key -> deps.store.connectAi(id, key); nav.popBackStack() },
                            onUseFuel = { deps.store.useFuel(); nav.popBackStack(); nav.navigate(FuelRoute) },
                        )
                    }
                    composable<AiCoachRoute> {
                        AiCoachScreen(
                            state = state,
                            providers = deps.providers,
                            chat = deps.chat,
                            onBack = nav::popBackStack,
                            onOwnKey = { nav.navigate(ConnectRoute) },
                            onFuel = { nav.navigate(FuelRoute) },
                            onProvider = { nav.navigate(ChatProviderRoute(it.name)) },
                            onCoachSettings = { nav.navigate(CoachSettingsRoute) },
                            onDisconnect = deps.store::disconnectAi,
                        )
                    }
                    composable<ChatProviderRoute> { entry ->
                        ChatProviderScreen(
                            provider = ChatProvider.of(entry.toRoute<ChatProviderRoute>().provider),
                            chat = deps.chat,
                            viewModel = viewModel { ChatViewModel(deps.chat, deps.data.scope, deps.clock) },
                            platform = deps.share,
                            today = deps.today(),
                            clock = deps.clock,
                            onBack = nav::popBackStack,
                        )
                    }
                    composable<ChatInboxRoute> {
                        ChatInboxScreen(
                            chat = deps.chat,
                            unit = state.profile.unit,
                            language = state.language,
                            onBack = nav::popBackStack,
                            onTemplate = { id -> nav.popBackStack(); nav.navigate(WorkoutPlanRoute(id)) },
                        )
                    }
                    composable<ChatNotesRoute> { ChatNotesScreen(deps.chat, onBack = nav::popBackStack) }
                    composable<ImportRoute> {
                        ImportScreen(
                            viewModel = viewModel { ImportViewModel(deps.data.importer, share.shortCodes) },
                            share = share,
                            unit = state.profile.unit,
                            language = state.language,
                            onBack = nav::popBackStack,
                            onImported = { id ->
                                nav.popBackStack()
                                nav.navigate(WorkoutPlanRoute(id))
                            },
                        )
                    }
                    composable<ShareRoute> { entry ->
                        val id = entry.toRoute<ShareRoute>().templateId
                        ShareScreen(
                            viewModel = viewModel { ShareViewModel(id, deps.data.templates, share.shortCodes) },
                            platform = deps.share,
                            onBack = nav::popBackStack,
                            unit = state.profile.unit,
                            language = state.language,
                        )
                    }
                    composable<CoachMemoryRoute> { CoachMemoryScreen(deps.coach, onBack = nav::popBackStack) }
                    composable<CoachStoryRoute> {
                        CoachStoryScreen(
                            onClose = nav::popBackStack,
                            autoAdvance = deps.coachStoryAutoAdvance,
                            onTarget = { target ->
                                nav.popBackStack()
                                nav.navigate(
                                    when (target) {
                                        StoryTarget.OwnKey -> ConnectRoute
                                        StoryTarget.Credit -> FuelRoute
                                        StoryTarget.ChatConnections -> AiCoachRoute
                                    },
                                )
                            },
                        )
                    }
                    composable<CoachRoute> {
                        // First visit: the story opens once by itself; afterwards only via the title.
                        LaunchedEffect(Unit) {
                            if (!deps.store.coachStorySeen()) {
                                deps.store.markCoachStorySeen()
                                nav.navigate(CoachStoryRoute)
                            }
                        }
                        CoachTabScreen(
                            coach = deps.coach,
                            chat = deps.coachChat,
                            chatFeature = deps.chat,
                            state = state,
                            providers = deps.providers,
                            fuelSession = deps.fuelSession,
                            prompts = { coachPrompts(deps, share) },
                            copy = deps.share::copyText,
                            nav = CoachTabNav(
                                coach = coachNav,
                                openChat = { id, draft -> nav.navigate(CoachChatRoute(id, draft)) },
                                allReviews = { nav.navigate(CoachReviewsRoute) },
                                access = { nav.navigate(AiCoachRoute) },
                                chatProvider = { nav.navigate(ChatProviderRoute(it.name)) },
                                chatNotes = { nav.navigate(ChatNotesRoute) },
                                story = { nav.navigate(CoachStoryRoute) },
                            ),
                        )
                    }
                    composable<CoachReviewsRoute> { CoachHomeScreen(deps.coach, coachNav) }
                    composable<CoachChatRoute> { entry ->
                        val route = entry.toRoute<CoachChatRoute>()
                        CoachChatScreen(
                            chat = deps.coachChat,
                            chatId = route.chatId,
                            draft = route.draft,
                            photos = deps.data.workouts.photos,
                            // Same import preview as a link: the import screen picks it up from the inbox.
                            onImportWorkout = { deps.inbox.offer(it) },
                            onAccess = { nav.navigate(AiCoachRoute) },
                            onBack = nav::popBackStack,
                        )
                    }
                    composable<CoachSettingsRoute> { CoachSettingsScreen(deps.coach, onBack = nav::popBackStack, onRequestLog = { nav.navigate(RequestLogRoute) }) }
                    composable<CoachSessionRoute> { entry ->
                        CoachSessionScreen(deps.coach, entry.toRoute<CoachSessionRoute>().sessionId, state.profile.unit, state.language, coachNav)
                    }
                    composable<CoachReviewRoute> { entry ->
                        CoachReviewScreen(deps.coach, entry.toRoute<CoachReviewRoute>().reviewId, state.profile.unit, state.language, coachNav)
                    }
                    composable<CoachDemoRoute> { entry ->
                        CoachDemoScreen(deps.coach, entry.toRoute<CoachDemoRoute>().sessionId, state.profile.unit, state.language, coachNav)
                    }
                    composable<AboutRoute> { AboutScreen(deps.versionName, onBack = nav::popBackStack, fuel = deps.fuel) }
                    composable<FuelRoute> {
                        FuelScreen(
                            session = deps.fuelSession,
                            onBack = nav::popBackStack,
                            onLedger = { nav.navigate(LedgerRoute) },
                            onRecovery = { nav.navigate(RecoveryRoute) },
                            fuelActive = state.providerId == ProviderId.FUEL,
                            onRedeemCode = { nav.navigate(RedeemCodeRoute) },
                            distribution = deps.distribution,
                            onRequestLog = { nav.navigate(RequestLogRoute) },
                        )
                    }
                    composable<RequestLogRoute> {
                        app.overfit.ui.fuel.RequestLogScreen(
                            dao = deps.data.database.aiRequestDao(),
                            session = deps.fuelSession,
                            fuelActive = state.providerId == ProviderId.FUEL,
                            clock = deps.clock,
                            onBack = nav::popBackStack,
                        )
                    }
                    composable<RedeemCodeRoute> { RedeemCodeScreen(deps.fuelSession, onBack = nav::popBackStack) }
                    composable<LedgerRoute> { LedgerScreen(deps.fuelSession, onBack = nav::popBackStack) }
                    composable<RecoveryRoute> { RecoveryScreen(deps.fuelSession, onBack = nav::popBackStack, onRestored = {}) }
                }
            }
            }
        }
    }
}

/** Switches to [tab] like a tap on the tab bar. */
internal fun NavHostController.selectTab(tab: Tab) = navigate(tab.route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

/** The prompts of the Coach tab in the app language, with the profile line where it belongs. */
internal suspend fun coachPrompts(deps: AppDependencies, share: ShareDependencies): List<app.overfit.coach.PromptEntry> {
    val language = deps.store.state.value.language
    val c = app.overfit.i18n.chatStringsFor(language)
    val today = deps.today()
    val sources = app.overfit.coach.PromptSources(
        contextWithTask = deps.chat.contextText(c, today),
        contextOnly = deps.chat.contextOnly(c, today),
        gem = deps.chat.gemText(c, today),
        createWorkout = app.overfit.ui.share.aiTemplateText(stringsFor(language).share, share.aiProfile()),
    )
    return app.overfit.coach.CoachPromptLibrary.all(coachStringsFor(language).hub, sources)
}

/** Brings the (maximized) workout to the front: back to it if it is on the stack, otherwise open it on top. */
internal fun NavHostController.openWorkout() {
    if (currentDestination?.hasRoute(WorkoutRoute::class) == true) return
    if (!popBackStack(WorkoutRoute, inclusive = false)) navigate(WorkoutRoute) { launchSingleTop = true }
}

/** Shows the bottom tab bar only on the four top-level screens. */
@Composable
private fun AppScaffold(
    nav: NavHostController,
    runningWorkout: Workout?,
    clock: () -> Long,
    onOpenWorkout: () -> Unit,
    content: @Composable () -> Unit,
) {
    val entry by nav.currentBackStackEntryAsState()
    val destination = entry?.destination
    val current = Tab.entries.firstOrNull { tab -> destination?.hierarchy?.any { it.hasRoute(tab.routeClass) } == true }

    // While a screen change runs, taps are swallowed: a double tap no longer opens a screen twice or pops two screens.
    val visible by nav.visibleEntries.collectAsStateWithLifecycle()
    // Tab switches have no transition (ScreenMotion), so they never block: only a fade between other screens does.
    val screens = visible.filter { it.destination !is androidx.navigation.NavGraph }
    val transitioning = screens.size > 1 && !screens.all { e -> Tab.entries.any { e.destination.hasRoute(it.routeClass) } }
    Column(Modifier.fillMaxSize().background(Theme.colors.background)) {
        Box(Modifier.weight(1f)) {
            content()
            if (transitioning) Box(Modifier.matchParentSize().testTag("nav-transition").pointerInput(Unit) {
                awaitPointerEventScope { while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() } }
            })
        }
        // A running workout that is not on screen (minimized) sits above the tab bar on every main screen.
        // While the story is still on screen (last frames of closing), the bars stay hidden: they appear in the same
        // frame the story disappears, never underneath it first.
        val storyOnScreen = screens.any { it.destination.hasRoute(CoachStoryRoute::class) }
        if (current != null && !storyOnScreen && runningWorkout != null) MiniWorkoutBar(runningWorkout, clock, onOpenWorkout)
        if (current != null && !storyOnScreen) {
            TabBar(current) { tab -> nav.selectTab(tab) }
        }
    }
}

@Composable
private fun TabBar(current: Tab, onSelect: (Tab) -> Unit) {
    val strings = LocalStrings.current
    Column(Modifier.fillMaxWidth().background(Theme.colors.background).windowInsetsPadding(WindowInsets.navigationBars)) {
        Hairline()
        Row(Modifier.fillMaxWidth().padding(vertical = Space.s), horizontalArrangement = Arrangement.SpaceEvenly) {
            Tab.entries.forEach { tab ->
                val active = tab == current
                val color = if (active) Theme.colors.foreground else Theme.colors.muted
                Column(
                    Modifier
                        .weight(1f)
                        .semantics { selected = active }
                        .testTag("tab:${tab.name}")
                        .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Tab) { onSelect(tab) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    LineIcon(tab.icon, color = color)
                    Spacer(Modifier.height(2.dp))
                    Text(tab.label(strings), style = Theme.type.caption, tone = if (active) Tone.Default else Tone.Muted)
                }
            }
        }
    }
}
