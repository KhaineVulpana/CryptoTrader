package com.example.myandroidapp.screens

import androidx.compose.material.icons.Icons
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.myandroidapp.shared.SharedAppViewModel
import com.example.myandroidapp.security.SecurePreferencesManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RootNavigation(securePreferencesManager: SecurePreferencesManager) {
    val navController = rememberNavController()
    val viewModel: SharedAppViewModel = viewModel()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val title = when (currentRoute) {
        "home" -> "Home"
        "markets" -> "Markets"
        "tools" -> "Tools"
        "settings" -> "Settings"
        else -> "CryptoTrader"
    }

    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = { Text(title) },
                navigationIcon = if (currentRoute == "settings") {
                    {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    }
                } else null,
                actions = {
                    if (currentRoute != "settings") {
                        IconButton(onClick = { /* TODO: profile */ }) {
                            Icon(Icons.Default.Person, contentDescription = "Profile")
                        }
                        IconButton(onClick = {
                            navController.navigate("settings") {
                                launchSingleTop = true
                            }
                        }) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
                        }
                    }
                }
            )
        },
        bottomBar = {
            if (currentRoute != "settings") {
                RootBottomNavBar(navController)
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.padding(padding)
        ) {
            composable("home") { HomeSection(viewModel) }
            composable("markets") { MarketSection(viewModel) }
            composable("tools") { ToolsSection(viewModel) }
            composable("settings") {
                SettingsScreen(onNavigateBack = { navController.popBackStack() })
            }
        }
    }
}

@Composable
fun RootBottomNavBar(navController: NavHostController, viewModel: SharedAppViewModel) {
    val items = listOf(
        "home" to Icons.Default.Home,
        "markets" to Icons.Default.BarChart,
        "tools" to Icons.Default.Build
    )
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    NavigationBar {
        items.forEach { (route, icon) ->
            NavigationBarItem(
                selected = currentRoute == route,
                onClick = {
                    if (currentRoute != route) {
                        viewModel.recordFeature(
                            name = "navigate_$route",
                            metadata = mapOf(
                                "from" to (currentRoute ?: "none")
                            )
                        )
                        navController.navigate(route)
                    }
                },
                icon = { Icon(icon, contentDescription = route) },
                label = { Text(route.replaceFirstChar { it.uppercase() }) }
            )
        }
    }
}
