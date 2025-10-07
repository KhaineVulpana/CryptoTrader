package com.example.myandroidapp.screens

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AutoGraph
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.myandroidapp.shared.SharedAppViewModel

private enum class AppDestination(val route: String, val label: String) {
    Dashboard("dashboard", "Dashboard"),
    Strategies("strategies", "Strategies"),
    Automations("automations", "Automations"),
    Timeline("timeline", "Timeline"),
    Blotter("blotter", "Blotter"),
    Backtests("backtests", "Backtests"),
    Settings("settings", "Settings"),
    SimParams("sim_params", "Sim Params"),
    Alerts("alerts", "Alerts"),
    Portfolio("portfolio", "Portfolio"),
}

private val bottomDestinations = listOf(
    AppDestination.Dashboard,
    AppDestination.Strategies,
    AppDestination.Timeline,
    AppDestination.Portfolio,
    AppDestination.Settings,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RootNavigation() {
    val navController = rememberNavController()
    val stateHolder = rememberSaveableStateHolder()
    val viewModel: SharedAppViewModel = viewModel()
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route ?: AppDestination.Dashboard.route
    val currentDestination = remember(currentRoute) { AppDestination.values().firstOrNull { it.route == currentRoute } ?: AppDestination.Dashboard }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(currentDestination.label) },
                actions = {
                    IconButton(onClick = { navController.navigateSingleTop(AppDestination.Alerts.route) }) {
                        Icon(Icons.Default.Notifications, contentDescription = "Alerts")
                    }
                    IconButton(onClick = { navController.navigateSingleTop(AppDestination.Blotter.route) }) {
                        Icon(Icons.Default.ListAlt, contentDescription = "Blotter")
                    }
                }
            )
        },
        bottomBar = { RootBottomNavBar(navController, currentRoute) }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = AppDestination.Dashboard.route,
            modifier = Modifier.padding(padding)
        ) {
            composable(AppDestination.Dashboard.route) { entry ->
                stateHolder.SaveableStateProvider(entry.destination.route ?: AppDestination.Dashboard.route) {
                    DashboardScreen(viewModel) { navController.navigateSingleTop(it) }
                }
            }
            composable(AppDestination.Strategies.route) { entry ->
                stateHolder.SaveableStateProvider(entry.destination.route!!) {
                    StrategiesScreen(viewModel) { navController.navigateSingleTop(it) }
                }
            }
            composable(AppDestination.Automations.route) { entry ->
                stateHolder.SaveableStateProvider(entry.destination.route!!) {
                    AutomationsScreen(viewModel) { navController.navigateSingleTop(it) }
                }
            }
            composable(AppDestination.Timeline.route) { entry ->
                stateHolder.SaveableStateProvider(entry.destination.route!!) {
                    TimelineScreen(viewModel)
                }
            }
            composable(AppDestination.Blotter.route) { entry ->
                stateHolder.SaveableStateProvider(entry.destination.route!!) {
                    BlotterScreen(viewModel)
                }
            }
            composable(AppDestination.Backtests.route) { entry ->
                stateHolder.SaveableStateProvider(entry.destination.route!!) {
                    BacktestsScreen(viewModel) { navController.navigateSingleTop(it) }
                }
            }
            composable(AppDestination.Settings.route) { entry ->
                stateHolder.SaveableStateProvider(entry.destination.route!!) {
                    SettingsScreen(viewModel) { navController.navigateSingleTop(it) }
                }
            }
            composable(AppDestination.SimParams.route) { entry ->
                stateHolder.SaveableStateProvider(entry.destination.route!!) {
                    SimParamsScreen(viewModel) { navController.navigateSingleTop(AppDestination.Backtests.route) }
                }
            }
            composable(AppDestination.Alerts.route) { entry ->
                stateHolder.SaveableStateProvider(entry.destination.route!!) {
                    AlertsScreen(viewModel) { navController.navigateSingleTop(it) }
                }
            }
            composable(AppDestination.Portfolio.route) { entry ->
                stateHolder.SaveableStateProvider(entry.destination.route!!) {
                    PortfolioScreen(viewModel) { navController.navigateSingleTop(AppDestination.Automations.route) }
                }
            }
        }
    }
}

@Composable
private fun RootBottomNavBar(navController: NavHostController, currentRoute: String) {
    NavigationBar {
        bottomDestinations.forEach { destination ->
            val icon = when (destination) {
                AppDestination.Dashboard -> Icons.Default.Dashboard
                AppDestination.Strategies -> Icons.Default.AutoGraph
                AppDestination.Timeline -> Icons.Default.Timeline
                AppDestination.Portfolio -> Icons.Default.AccountBalance
                AppDestination.Settings -> Icons.Default.Settings
                else -> Icons.Default.Dashboard
            }
            NavigationBarItem(
                selected = currentRoute == destination.route,
                onClick = { navController.navigateSingleTop(destination.route) },
                icon = { Icon(icon, contentDescription = destination.label) },
                label = { Text(destination.label) }
            )
        }
    }
}

private fun NavHostController.navigateSingleTop(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}

