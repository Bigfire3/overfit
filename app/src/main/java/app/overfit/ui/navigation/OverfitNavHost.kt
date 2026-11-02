package app.overfit.ui.navigation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.toRoute
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.overfit.ui.screens.AboutScreen
import app.overfit.ui.screens.CoachMemoryScreen
import app.overfit.ui.exercises.ExercisePickerScreen
import app.overfit.ui.exercises.ExercisesScreen
import app.overfit.ui.templates.TemplateEditorScreen
import app.overfit.ui.templates.TemplateEditorViewModel
import app.overfit.ui.screens.HistoryScreen
import app.overfit.ui.screens.PhotosScreen
import app.overfit.ui.screens.SettingsScreen
import app.overfit.ui.screens.StartScreen

@Composable
fun OverfitNavHost(navController: NavHostController = rememberNavController()) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val showBottomBar = TopLevelDestination.entries.any { currentDestination.isOn(it) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val showMessage: (String) -> Unit = { msg -> scope.launch { snackbar.showSnackbar(msg) } }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        // Insets übernehmen die Screens selbst (TopAppBar) bzw. die NavigationBar.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    TopLevelDestination.entries.forEach { dest ->
                        NavigationBarItem(
                            selected = currentDestination.isOn(dest),
                            onClick = { navController.navigateTopLevel(dest) },
                            icon = { Icon(dest.icon, contentDescription = null) },
                            label = { Text(stringResource(dest.label)) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = StartRoute,
            modifier = Modifier
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding),
        ) {
            composable<StartRoute> {
                StartScreen(
                    onOpenTemplate = { navController.navigate(TemplateEditorRoute(it)) },
                    onNewTemplate = { navController.navigate(TemplateEditorRoute()) },
                )
            }
            composable<HistoryRoute> { HistoryScreen() }
            composable<ExercisesRoute> {
                ExercisesScreen(
                    onOpenTemplate = { navController.navigate(TemplateEditorRoute(it)) },
                    onNewTemplate = { navController.navigate(TemplateEditorRoute()) },
                )
            }
            composable<TemplateEditorRoute> { entry ->
                val route = entry.toRoute<TemplateEditorRoute>()
                val vm: TemplateEditorViewModel = viewModel(factory = TemplateEditorViewModel.factory(route.templateId))
                // Ergebnis der Übungsauswahl abholen.
                val picked by entry.savedStateHandle.getStateFlow<List<String>?>(PICKED_EXERCISES_KEY, null)
                    .collectAsStateWithLifecycle()
                LaunchedEffect(picked) {
                    picked?.let {
                        vm.onExercisesPicked(it)
                        entry.savedStateHandle[PICKED_EXERCISES_KEY] = null
                    }
                }
                TemplateEditorScreen(
                    viewModel = vm,
                    onClose = { navController.popBackStack() },
                    onAddExercises = { navController.navigate(ExercisePickerRoute(single = false)) },
                    onReplaceExercise = { navController.navigate(ExercisePickerRoute(single = true)) },
                    onMessage = showMessage,
                )
            }
            composable<ExercisePickerRoute> { entry ->
                val route = entry.toRoute<ExercisePickerRoute>()
                ExercisePickerScreen(
                    single = route.single,
                    onBack = { navController.popBackStack() },
                    onPicked = { ids ->
                        navController.previousBackStackEntry?.savedStateHandle?.set(PICKED_EXERCISES_KEY, ArrayList(ids))
                        navController.popBackStack()
                    },
                )
            }
            composable<PhotosRoute> { PhotosScreen() }
            composable<SettingsRoute> {
                SettingsScreen(
                    onOpenCoachMemory = { navController.navigate(CoachMemoryRoute) },
                    onOpenAbout = { navController.navigate(AboutRoute) },
                )
            }
            composable<CoachMemoryRoute> { CoachMemoryScreen(onBack = { navController.popBackStack() }) }
            composable<AboutRoute> { AboutScreen(onBack = { navController.popBackStack() }) }
        }
    }
}

private fun NavDestination?.isOn(dest: TopLevelDestination): Boolean =
    this?.hierarchy?.any { it.hasRoute(dest.routeClass) } == true

private fun NavHostController.navigateTopLevel(dest: TopLevelDestination) {
    navigate(dest.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
