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
import app.overfit.fuel.isSpotLink
import app.overfit.i18n.LocalFuelStrings
import app.overfit.i18n.fuelStringsFor
import app.overfit.platform.Haptics
import app.overfit.platform.NoHaptics
import app.overfit.ui.fuel.FuelScreen
import app.overfit.ui.fuel.LedgerScreen
import app.overfit.ui.fuel.RecoveryScreen
import app.overfit.ui.fuel.SpotInviteScreen
import app.overfit.ui.fuel.SpotRedeemScreen
import app.overfit.ui.fuel.SpotRevealScreen
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
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.toRoute
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.crossfade
import coil3.svg.SvgDecoder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import app.overfit.i18n.LocalStrings
import app.overfit.i18n.Strings
import app.overfit.i18n.stringsFor
import app.overfit.ui.ai.ConnectAiScreen
import app.overfit.ui.components.Hairline
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
import app.overfit.ui.workout.StartWorkoutScreen
import app.overfit.ui.workout.WorkoutScreen
import app.overfit.ui.templates.TemplateEditorViewModel
import app.overfit.ui.history.FinishScreen
import app.overfit.ui.history.HistoryDetailScreen
import app.overfit.ui.history.HistoryScreen
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
import app.overfit.ui.share.DuoScreen
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
import app.overfit.ui.coach.CoachMemoryScreen
import app.overfit.ui.coach.CoachNav
import app.overfit.ui.coach.CoachReviewScreen
import app.overfit.ui.coach.CoachSessionScreen
import app.overfit.ui.coach.CoachSettingsScreen
import app.overfit.ui.coach.createCoach
import app.overfit.ui.coach.latestCoachTips
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
    /** Fuel/Spot backend (URL from the build, developer override); defaults to "not configured". */
    val fuel: FuelAccess = FuelAccess(InMemoryKeyValueStore(), "") { error("No backend") },
    /** Small key-value storage for the chat sync state (not secret). */
    kv: KeyValueStore = InMemoryKeyValueStore(),
    /** Body photos for the coach; null = the workout photos, downscaled (tests replace it). */
    coachPhotos: CoachPhotoLoader? = null,
    /** Replaces the real providers (tests). */
    coachTransport: ((ProviderId) -> CoachTransport)? = null,
) {
    val fuelSession = FuelSession(fuel)
    /** The AI coach: requests, queue, reviews, memory. Runs in the app scope. */
    val coach: CoachService = createCoach(store, providers, data, today, clock, coachPhotos, coachTransport)
    /** KI-Chat-Verbindung (Claude, ChatGPT, Gemini): sync, inbox, notes. The coach memory goes along as memory/coach. */
    val chat: ChatFeature = ChatFeature(fuel, data, store, kv, data.scope, clock, memory = { coach.memoryText })

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
    }
    val backup = BackupService(data, store, versionName, clock) { backupContributors.toList() }
    /** The running workout; created once per process, loads the stored workout right away. */
    val workout: WorkoutController = WorkoutController(
        store = data.workouts,
        alerts = restAlerts,
        haptics = haptics,
        prefs = {
            val s = store.state.value
            WorkoutPrefs(s.settings.defaultRestSec, s.settings.vibration, s.profile.unit, s.language)
        },
        scope = workoutScope,
        clock = clock,
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
@Serializable data object HistoryRoute
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
@Serializable data object CoachSettingsRoute
@Serializable data class CoachSessionRoute(val sessionId: String)
@Serializable data class CoachReviewRoute(val reviewId: String)
@Serializable data class CoachDemoRoute(val sessionId: String)
@Serializable data object AboutRoute
@Serializable data object FuelRoute
@Serializable data object LedgerRoute
@Serializable data object RecoveryRoute
@Serializable data object SpotInviteRoute
@Serializable data class SpotRedeemRoute(val code: String = "")
@Serializable data class SpotRevealRoute(val amountMicro: Long, val inviterName: String = "")
@Serializable data object ImportRoute
@Serializable data object DuoRoute
@Serializable data class ShareRoute(val templateId: Long)
@Serializable data class ExerciseDetailRoute(val id: String)
/** Finish flow right after "Finish": summary, feeling, note, photos, coach slot. */
@Serializable data class FinishRoute(val workoutId: String)
@Serializable data class HistoryDetailRoute(val workoutId: String)
/** Share a finished workout (`kind: "workout"`); [withActual] = logged values instead of targets. */
@Serializable data class WorkoutShareRoute(val workoutId: String, val withActual: Boolean = true)
@Serializable data class PhotoViewerRoute(val photoId: String)
@Serializable data class PhotoCompareRoute(val firstId: String, val secondId: String)
/** Preview and confirm a backup import; the file waits in [AppDependencies.pendingBackup]. */
@Serializable data object BackupImportRoute
/** Custom exercise; [id] empty = new one (prefilled with [name], e.g. the search text). */
@Serializable data class CustomExerciseRoute(val id: String = "", val name: String = "")
/** Template editor; [templateId] 0 = new template. */
@Serializable data class TemplateEditorRoute(val templateId: Long = 0)
/** Exercise picker; [single] = replace mode. The result goes through [PickedExercises]. */
@Serializable data class ExercisePickerRoute(val single: Boolean = false)

/** Hands the picker's result back to the editor below it on the back stack. */
class PickedExercises {
    val result = MutableStateFlow<List<String>?>(null)
}

enum class Tab(val route: Any, val icon: Icon, val label: (Strings) -> String) {
    START(StartRoute, Icon.Home, { it.navStart }),
    HISTORY(HistoryRoute, Icon.History, { it.navHistory }),
    EXERCISES(ExercisesRoute, Icon.Exercises, { it.navExercises }),
    PHOTOS(PhotosRoute, Icon.Photos, { it.navPhotos }),
    PROFILE(SettingsRoute, Icon.Settings, { it.navProfile });

    val routeClass: KClass<*> get() = route::class
}

@Composable
fun OverfitApp(deps: AppDependencies) {
    val state by deps.store.state.collectAsStateWithLifecycle()
    val templates by deps.data.templates.templates.collectAsStateWithLifecycle(initialValue = null)
    val picked = remember { PickedExercises() }
    val workoutState by deps.workout.state.collectAsStateWithLifecycle()
    val share = remember(deps) { ShareDependencies(deps.share, deps.data.importer, deps.data.templates, deps.inbox, deps.data.scope) }
    val incoming by deps.inbox.pending.collectAsStateWithLifecycle()
    setSingletonImageLoaderFactory { context ->
        ImageLoader.Builder(context)
            .components {
                add(SvgDecoder.Factory())
                add(KtorNetworkFetcherFactory())
            }
            .crossfade(true)
            .build()
    }
    LaunchedEffect(Unit) { deps.data.seedCatalog() }
    // KI-Chat: back in the foreground → send what is left and fetch the inbox; to the background → send a running workout now.
    // Queued coach requests go out on app start and whenever the app comes back to the foreground.
    LifecycleResumeEffect(deps) {
        deps.chat.onForeground()
        deps.coach.processQueue()
        onPauseOrDispose { deps.chat.onBackground() }
    }
    val chatInbox by deps.chat.inbox.state.collectAsStateWithLifecycle()
    OverfitTheme(state.settings.theme) {
        CompositionLocalProvider(
            LocalStrings provides stringsFor(state.language),
            LocalFuelStrings provides fuelStringsFor(state.language),
            LocalChatStrings provides chatStringsFor(state.language),
            LocalCoachStrings provides coachStringsFor(state.language),
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
                    openTemplate = { nav.navigate(TemplateEditorRoute(it)) },
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
            // Links, files and scans from outside open the import screen (not during onboarding).
            LaunchedEffect(incoming, state.onboardingDone) {
                // A Spot link (QR scanned with the camera app) goes to "I was spotted", not to the training import.
                val spot = incoming
                if (spot != null && state.onboardingDone && isSpotLink(spot)) {
                    deps.inbox.consume()
                    nav.navigate(SpotRedeemRoute(spot)) { launchSingleTop = true }
                    return@LaunchedEffect
                }
                if (incoming != null && state.onboardingDone &&
                    nav.currentDestination?.hasRoute(ImportRoute::class) != true
                ) {
                    nav.navigate(ImportRoute) { launchSingleTop = true }
                }
            }
            AppScaffold(nav) {
                NavHost(nav, startDestination = start, modifier = Modifier.fillMaxSize()) {
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
                        StartScreen(
                            state = state,
                            templates = templates,
                            activeWorkout = workoutState.workout != null,
                            onStartWorkout = {
                                nav.navigate(if (workoutState.workout != null) WorkoutRoute else StartWorkoutRoute)
                            },
                            onOpenTemplate = { nav.navigate(TemplateEditorRoute(it)) },
                            onNewTemplate = { nav.navigate(TemplateEditorRoute()) },
                            share = share,
                            onDuo = { nav.navigate(DuoRoute) },
                            coachTips = latestCoachTips(deps.coach),
                            onCoach = { nav.navigate(CoachRoute) },
                            chatInboxCount = chatInbox.items.size,
                            chatInboxFull = chatInbox.full,
                            onChatInbox = { nav.navigate(ChatInboxRoute) },
                        )
                    }
                    composable<StartWorkoutRoute> {
                        val scope = rememberCoroutineScope()
                        val openWorkout = { nav.navigate(WorkoutRoute) { popUpTo(StartRoute) } }
                        StartWorkoutScreen(
                            templates = templates,
                            onBack = nav::popBackStack,
                            onEmpty = { deps.workout.startEmpty(); openWorkout() },
                            onTemplate = { id ->
                                scope.launch {
                                    deps.data.templates.get(id)?.let { deps.workout.startFromTemplate(it); openWorkout() }
                                }
                            },
                        )
                    }
                    composable<WorkoutRoute> {
                        val scope = rememberCoroutineScope()
                        var pendingReplaceId by rememberSaveable { mutableStateOf<String?>(null) }
                        val result by picked.result.collectAsStateWithLifecycle()
                        LaunchedEffect(result) {
                            val ids = result ?: return@LaunchedEffect
                            picked.result.value = null
                            val replaceId = pendingReplaceId
                            pendingReplaceId = null
                            if (ids.isEmpty()) return@LaunchedEffect
                            scope.launch {
                                val exercises = deps.data.exercises.getAll(ids)
                                if (replaceId != null) exercises.firstOrNull()?.let { deps.workout.replaceExercise(replaceId, it) }
                                else deps.workout.addExercises(exercises)
                            }
                        }
                        WorkoutScreen(
                            controller = deps.workout,
                            unit = state.profile.unit,
                            language = state.language,
                            clock = deps.clock,
                            onAddExercises = { pendingReplaceId = null; nav.navigate(ExercisePickerRoute(single = false)) },
                            onReplaceExercise = { pendingReplaceId = it; nav.navigate(ExercisePickerRoute(single = true)) },
                            onClosed = { nav.navigate(StartRoute) { popUpTo(0) { inclusive = true } } },
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
                        )
                    }
                    composable<HistoryRoute> {
                        val rows by deps.data.workouts.history.collectAsStateWithLifecycle(initialValue = null)
                        HistoryScreen(
                            rows = rows,
                            unit = state.profile.unit,
                            language = state.language,
                            onOpen = { nav.navigate(HistoryDetailRoute(it)) },
                        )
                    }
                    composable<HistoryDetailRoute> { entry ->
                        val id = entry.toRoute<HistoryDetailRoute>().workoutId
                        val chatNotes by deps.chat.notes.notes.collectAsStateWithLifecycle()
                        HistoryDetailScreen(
                            workoutId = id,
                            chatNotes = chatNotes.filter { it.workoutId == id },
                            workouts = deps.data.workouts,
                            templates = deps.data.templates,
                            unit = state.profile.unit,
                            language = state.language,
                            appScope = deps.data.scope,
                            onBack = nav::popBackStack,
                            onOpenPhoto = { nav.navigate(PhotoViewerRoute(it)) },
                            onTemplateCreated = { nav.navigate(TemplateEditorRoute(it)) },
                            onShare = { nav.navigate(WorkoutShareRoute(id, withActual = true)) },
                            onCoach = { nav.navigate(CoachSessionRoute(id)) },
                        )
                    }
                    composable<WorkoutShareRoute> { entry ->
                        val route = entry.toRoute<WorkoutShareRoute>()
                        var withActual by rememberSaveable { mutableStateOf(route.withActual) }
                        ShareScreen(
                            viewModel = viewModel(key = "workout-share-${route.workoutId}-$withActual") {
                                ShareViewModel({ deps.data.workouts.getFinished(route.workoutId)?.workout?.let { ShareMapper.fromWorkout(it, withActual) } })
                            },
                            platform = deps.share,
                            onBack = nav::popBackStack,
                            description = LocalStrings.current.history.shareWorkoutHint,
                            extra = { Chip(LocalStrings.current.history.withActual, withActual, { withActual = !withActual }) },
                        )
                    }
                    composable<ExercisesRoute> {
                        val catalog = viewModel { CatalogViewModel(deps.data.exercises) }
                        ExercisesScreen(
                            catalog = catalog,
                            templates = templates,
                            language = state.language,
                            onOpenExercise = { nav.navigate(ExerciseDetailRoute(it.id)) },
                            onNewCustomExercise = { nav.navigate(CustomExerciseRoute(name = catalog.filter.value.query.trim())) },
                            onOpenTemplate = { nav.navigate(TemplateEditorRoute(it)) },
                            onNewTemplate = { nav.navigate(TemplateEditorRoute()) },
                            share = share,
                            onImport = { nav.navigate(ImportRoute) },
                        )
                    }
                    composable<ExerciseDetailRoute> { entry ->
                        val id = entry.toRoute<ExerciseDetailRoute>().id
                        ExerciseDetailScreen(
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
                            viewModel = viewModel { ExerciseViewModel(route.id.ifEmpty { null }, deps.data.exercises) },
                            initialName = route.name,
                            onBack = nav::popBackStack,
                            onSaved = { nav.popBackStack() },
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
                            onShare = { nav.navigate(ShareRoute(id)) },
                        )
                    }
                    composable<ExercisePickerRoute> { entry ->
                        val single = entry.toRoute<ExercisePickerRoute>().single
                        ExercisePickerScreen(
                            catalog = viewModel { CatalogViewModel(deps.data.exercises) },
                            language = state.language,
                            single = single,
                            onBack = nav::popBackStack,
                            onPicked = { ids -> picked.result.value = ids; nav.popBackStack() },
                        )
                    }
                    composable<PhotosRoute> {
                        val photos by deps.data.workouts.photos.collectAsStateWithLifecycle(initialValue = null)
                        val history by deps.data.workouts.history.collectAsStateWithLifecycle(initialValue = null)
                        PhotosScreen(
                            photos = photos,
                            history = history,
                            language = state.language,
                            today = deps.today(),
                            onOpen = { nav.navigate(PhotoViewerRoute(it)) },
                            onWorkout = { nav.navigate(HistoryDetailRoute(it)) },
                            onCompare = { a, b -> nav.navigate(PhotoCompareRoute(a, b)) },
                        )
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
                            onWorkout = { nav.navigate(HistoryDetailRoute(it)) },
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
                            onCoach = { nav.navigate(CoachRoute) },
                            onAbout = { nav.navigate(AboutRoute) },
                            fuelSession = deps.fuelSession,
                            onFuel = { nav.navigate(FuelRoute) },
                            onAiCoach = { nav.navigate(AiCoachRoute) },
                            onChatNotes = { nav.navigate(ChatNotesRoute) },
                            offlineImages = deps.data.offlineImages,
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
                                deps.data.scope.launch { deps.workout.discard(); deps.data.resetAll() }
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
                            onTemplate = { id -> nav.popBackStack(); nav.navigate(TemplateEditorRoute(id)) },
                        )
                    }
                    composable<ChatNotesRoute> { ChatNotesScreen(deps.chat, onBack = nav::popBackStack) }
                    composable<ImportRoute> {
                        ImportScreen(
                            viewModel = viewModel { ImportViewModel(deps.data.importer) },
                            share = share,
                            unit = state.profile.unit,
                            language = state.language,
                            onBack = nav::popBackStack,
                            onImported = { id ->
                                nav.popBackStack()
                                nav.navigate(TemplateEditorRoute(id))
                            },
                        )
                    }
                    composable<ShareRoute> { entry ->
                        val id = entry.toRoute<ShareRoute>().templateId
                        ShareScreen(
                            viewModel = viewModel { ShareViewModel(id, deps.data.templates) },
                            platform = deps.share,
                            onBack = nav::popBackStack,
                        )
                    }
                    composable<DuoRoute> { DuoScreen(onBack = nav::popBackStack) }
                    composable<CoachMemoryRoute> { CoachMemoryScreen(deps.coach, onBack = nav::popBackStack) }
                    composable<CoachRoute> { CoachHomeScreen(deps.coach, coachNav) }
                    composable<CoachSettingsRoute> { CoachSettingsScreen(deps.coach, onBack = nav::popBackStack) }
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
                            onSpotInvite = { nav.navigate(SpotInviteRoute) },
                            onSpotRedeem = { nav.navigate(SpotRedeemRoute()) },
                        )
                    }
                    composable<LedgerRoute> { LedgerScreen(deps.fuelSession, onBack = nav::popBackStack) }
                    composable<RecoveryRoute> { RecoveryScreen(deps.fuelSession, onBack = nav::popBackStack, onRestored = {}) }
                    composable<SpotInviteRoute> { SpotInviteScreen(deps.fuelSession, onBack = nav::popBackStack) }
                    composable<SpotRedeemRoute> { entry ->
                        SpotRedeemScreen(
                            canScan = deps.share.canScanInApp,
                            initialCode = entry.toRoute<SpotRedeemRoute>().code,
                            session = deps.fuelSession,
                            onBack = nav::popBackStack,
                            onRedeemed = { micro, name ->
                                nav.navigate(SpotRevealRoute(micro, name.orEmpty())) { popUpTo<SpotRedeemRoute> { inclusive = true } }
                            },
                        )
                    }
                    composable<SpotRevealRoute> { entry ->
                        val route = entry.toRoute<SpotRevealRoute>()
                        SpotRevealScreen(
                            amountMicro = route.amountMicro,
                            inviterName = route.inviterName.ifEmpty { null },
                            haptics = deps.haptics,
                            vibration = state.settings.vibration,
                            onClose = nav::popBackStack,
                        )
                    }
                }
            }
        }
    }
}

/** Shows the bottom tab bar only on the five top-level screens. */
@Composable
private fun AppScaffold(nav: NavHostController, content: @Composable () -> Unit) {
    val entry by nav.currentBackStackEntryAsState()
    val destination = entry?.destination
    val current = Tab.entries.firstOrNull { tab -> destination?.hierarchy?.any { it.hasRoute(tab.routeClass) } == true }

    Column(Modifier.fillMaxSize().background(Theme.colors.background)) {
        Box(Modifier.weight(1f)) { content() }
        if (current != null) {
            TabBar(current) { tab ->
                nav.navigate(tab.route) {
                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            }
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
