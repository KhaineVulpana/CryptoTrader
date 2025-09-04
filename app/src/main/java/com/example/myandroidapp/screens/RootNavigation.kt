package com.example.myandroidapp.screens

import androidx.compose.material.icons.Icons
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import com.example.myandroidapp.shared.SharedAppViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RootNavigation() {
    val navController = rememberNavController()
    val viewModel: SharedAppViewModel = viewModel()

    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = {},
                actions = {
                    IconButton(onClick = { /* TODO: profile */ }) {
                        Icon(Icons.Default.Person, contentDescription = "Profile")
                    }
                    IconButton(onClick = { /* TODO: settings */ }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        bottomBar = { RootBottomNavBar(navController) }
    ) { padding ->
        NavHost(navController = navController, startDestination = "market", modifier = Modifier.padding(padding)) {
            composable("market") { MarketSection(viewModel) }
            composable("tools") { ToolsSection(viewModel) }
            composable("profile") { Text("Profile Screen Placeholder") }
        }
    }
}

@Composable
fun RootBottomNavBar(navController: NavHostController) {
    val items = listOf("market" to Icons.Default.BarChart, "tools" to Icons.Default.Build, "profile" to Icons.Default.Person)
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    NavigationBar {
        items.forEach { (route, icon) ->
            NavigationBarItem(
                selected = currentRoute == route,
                onClick = { navController.navigate(route) },
                icon = { Icon(icon, contentDescription = route) },
                label = { Text(route.replaceFirstChar { it.uppercase() }) }
            )
        }
    }
}
