package com.ryosoftware.bt_sound_manager.presentation.navigation

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ryosoftware.bt_sound_manager.R
import com.ryosoftware.bt_sound_manager.core.util.AppActions
import com.ryosoftware.bt_sound_manager.presentation.automation.AutomationScreen
import com.ryosoftware.bt_sound_manager.presentation.devices.DeviceSelectionScreen
import com.ryosoftware.bt_sound_manager.presentation.events.EventsScreen
import com.ryosoftware.bt_sound_manager.presentation.settings.SettingsScreen
import kotlinx.coroutines.flow.StateFlow

private data class BottomDestination(
    val route: String,
    val labelRes: Int,
    val icon: ImageVector,
)

@Composable
fun MonitorizeApp(
    pendingDestination: StateFlow<String?>,
    onDestinationConsumed: () -> Unit,
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val destination by pendingDestination.collectAsStateWithLifecycle()

    LaunchedEffect(destination) {
        if (destination != null) {
            if (destination == AppActions.DESTINATION_EVENTS) {
                navController.navigate(Routes.EVENTS) {
                    launchSingleTop = true
                }
            }
            onDestinationConsumed()
        }
    }

    val destinations = listOf(
        BottomDestination(Routes.EVENTS, R.string.nav_events, Icons.Outlined.History),
        BottomDestination(Routes.SETTINGS, R.string.nav_settings, Icons.Outlined.Settings),
    )
    val navigateTo: (String) -> Unit = { route ->
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val wide = maxWidth >= 600.dp
        if (wide) {
            Row(modifier = Modifier.fillMaxSize()) {
                if (currentRoute in Routes.topLevel) {
                    NavigationRail(containerColor = MaterialTheme.colorScheme.surface) {
                        destinations.forEach { item ->
                            NavigationRailItem(
                                selected = currentRoute == item.route,
                                onClick = { navigateTo(item.route) },
                                icon = {
                                    Icon(
                                        imageVector = item.icon,
                                        contentDescription = stringResource(item.labelRes),
                                    )
                                },
                                label = { Text(stringResource(item.labelRes)) },
                            )
                        }
                    }
                }
                AppNavHost(
                    navController = navController,
                    modifier = Modifier.weight(1f),
                )
            }
        } else {
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                containerColor = MaterialTheme.colorScheme.background,
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                bottomBar = {
                    if (currentRoute in Routes.topLevel) {
                        NavigationBar {
                            destinations.forEach { item ->
                                NavigationBarItem(
                                    selected = currentRoute == item.route,
                                    onClick = { navigateTo(item.route) },
                                    icon = {
                                        Icon(
                                            imageVector = item.icon,
                                            contentDescription = stringResource(item.labelRes),
                                        )
                                    },
                                    label = { Text(stringResource(item.labelRes)) },
                                )
                            }
                        }
                    }
                },
            ) { padding ->
                AppNavHost(
                    navController = navController,
                    modifier = Modifier.padding(padding),
                )
            }
        }
    }
}

@Composable
private fun AppNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = Routes.SETTINGS,
        modifier = modifier.fillMaxSize(),
    ) {
        composable(Routes.EVENTS) {
            EventsScreen()
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onConfigureAutomation = { navController.navigate(Routes.AUTOMATION) },
            )
        }
        composable(Routes.AUTOMATION) {
            AutomationScreen(
                onBack = { navController.popBackStack() },
                onSelectDevices = {
                    navController.navigate(Routes.DEVICES)
                },
            )
        }
        composable(Routes.DEVICES) {
            DeviceSelectionScreen(
                onBack = { navController.popBackStack() },
            )
        }
    }
}
