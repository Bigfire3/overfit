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
import androidx.compose.runtime.remember
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
import app.overfit.data.AppData
import app.overfit.data.AppStore
import androidx.compose.runtime.LaunchedEffect
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
import app.overfit.ui.templates.TemplateEditorViewModel
import app.overfit.ui.main.HistoryScreen
import app.overfit.ui.main.PhotosScreen
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
import app.overfit.ui.settings.CoachMemoryScreen
import app.overfit.ui.settings.SettingsScreen
import app.overfit.ui.theme.OverfitTheme
import app.overfit.ui.theme.Space
import app.overfit.ui.theme.Theme
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlinx.serialization.Serializable
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
)

// Type-safe routes.
@Serializable data object NameRoute
@Serializable data object AgeRoute
@Serializable data object WeightRoute
@Serializable data object AiRoute
@Serializable data object StartRoute
@Serializable data object HistoryRoute
@Serializable data object ExercisesRoute
@Serializable data object PhotosRoute
@Serializable data object SettingsRoute
@Serializable data object ConnectRoute
@Serializable data object CoachMemoryRoute
@Serializable data object AboutRoute
@Serializable data object ImportRoute
@Serializable data object DuoRoute
@Serializable data class ShareRoute(val templateId: Long)
@Serializable data class ExerciseDetailRoute(val id: String)
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
    val share = remember(deps) { ShareDependencies(deps.share, deps.data.importer, deps.data.templates, deps.inbox) }
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
    OverfitTheme(state.settings.theme) {
        CompositionLocalProvider(LocalStrings provides stringsFor(state.language)) {
            val nav = rememberNavController()
            val start = remember { if (state.onboardingDone) StartRoute else NameRoute }
            // Links, files and scans from outside open the import screen (not during onboarding).
            LaunchedEffect(incoming, state.onboardingDone) {
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
                        )
                    }

                    composable<StartRoute> {
                        StartScreen(
                            state = state,
                            templates = templates,
                            onOpenTemplate = { nav.navigate(TemplateEditorRoute(it)) },
                            onNewTemplate = { nav.navigate(TemplateEditorRoute()) },
                            share = share,
                            onDuo = { nav.navigate(DuoRoute) },
                        )
                    }
                    composable<HistoryRoute> { HistoryScreen() }
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
                    composable<PhotosRoute> { PhotosScreen() }
                    composable<SettingsRoute> {
                        SettingsScreen(
                            state = state,
                            store = deps.store,
                            providers = deps.providers,
                            today = deps.today(),
                            onConnect = { nav.navigate(ConnectRoute) },
                            onCoachMemory = { nav.navigate(CoachMemoryRoute) },
                            onAbout = { nav.navigate(AboutRoute) },
                            offlineImages = deps.data.offlineImages,
                            share = share,
                            onReset = {
                                deps.store.resetAll()
                                deps.data.scope.launch { deps.data.resetAll() }
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
                        )
                    }
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
                    composable<CoachMemoryRoute> { CoachMemoryScreen(onBack = nav::popBackStack) }
                    composable<AboutRoute> { AboutScreen(deps.versionName, onBack = nav::popBackStack) }
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
