package app.overfit.ui.navigation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import app.overfit.ui.screens.AboutScreen
import app.overfit.ui.screens.CoachMemoryScreen
import app.overfit.ui.screens.ExercisesScreen
import app.overfit.ui.screens.HistoryScreen
import app.overfit.ui.screens.PhotosScreen
import app.overfit.ui.screens.SettingsScreen
import app.overfit.ui.screens.StartScreen

@Composable
fun OverfitNavHost(navController: NavHostController = rememberNavController()) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val showBottomBar = TopLevelDestination.entries.any { currentDestination.isOn(it) }

    Scaffold(
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
            composable<StartRoute> { StartScreen() }
            composable<HistoryRoute> { HistoryScreen() }
            composable<ExercisesRoute> { ExercisesScreen() }
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
