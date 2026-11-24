package app.floodcallblocker.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.floodcallblocker.R
import app.floodcallblocker.data.Settings
import app.floodcallblocker.ui.screens.EditScreen
import app.floodcallblocker.ui.screens.LogScreen
import app.floodcallblocker.ui.screens.OnboardingScreen
import app.floodcallblocker.ui.screens.OverviewScreen
import app.floodcallblocker.ui.screens.SettingsScreen
import kotlinx.coroutines.launch

object Routes {
    const val ONBOARDING = "onboarding"
    const val OVERVIEW = "overview"
    const val LOG = "log"
    const val SETTINGS = "settings"
    const val EDIT = "edit/{id}"

    fun edit(id: Long?) = "edit/${id ?: -1}"
}

private data class Tab(
    val route: String,
    val label: Int,
    val icon: ImageVector,
)

private val tabs =
    listOf(
        Tab(Routes.OVERVIEW, R.string.nav_overview, Icons.Filled.Call),
        Tab(Routes.LOG, R.string.nav_log, Icons.AutoMirrored.Filled.List),
        Tab(Routes.SETTINGS, R.string.nav_settings, Icons.Filled.Settings),
    )

@Composable
fun AppNavigation(
    settings: Settings,
    requestedEntry: Long,
    onRequestedEntryHandled: () -> Unit,
) {
    val nav = rememberNavController()
    val container = rememberContainer()
    val scope = rememberCoroutineScope()
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    LaunchedEffect(requestedEntry, settings.onboardingDone) {
        if (requestedEntry >= 0 && settings.onboardingDone) {
            nav.navigate(Routes.edit(requestedEntry)) { launchSingleTop = true }
            onRequestedEntryHandled()
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (tabs.any { it.route == currentRoute }) {
                NavigationBar {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = backStack?.destination?.hierarchy?.any { it.route == tab.route } == true,
                            onClick = {
                                nav.navigate(tab.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(stringResource(tab.label)) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = if (settings.onboardingDone) Routes.OVERVIEW else Routes.ONBOARDING,
            modifier = Modifier.padding(padding),
        ) {
            composable(Routes.ONBOARDING) {
                OnboardingScreen(
                    onFinished = {
                        scope.launch {
                            container.settings.setOnboardingDone(true)
                            nav.navigate(Routes.OVERVIEW) { popUpTo(Routes.ONBOARDING) { inclusive = true } }
                        }
                    },
                )
            }
            composable(Routes.OVERVIEW) { OverviewScreen(onOpenEntry = { nav.navigate(Routes.edit(it)) }) }
            composable(Routes.LOG) { LogScreen() }
            composable(Routes.SETTINGS) { SettingsScreen(settings) }
            composable(Routes.EDIT, arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                val id = entry.arguments?.getLong("id")?.takeIf { it >= 0 }
                EditScreen(entryId = id, onClose = { nav.popBackStack() })
            }
        }
    }
}
