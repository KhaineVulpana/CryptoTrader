package com.example.myandroidapp.screens

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.myandroidapp.shared.SharedAppViewModel

@Composable
fun ToolsSection(viewModel: SharedAppViewModel) {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = "Automation") {
        composable("Automation") {
            AutomationScreen(viewModel)
        }
        composable("Trading") {
            Text("Trading Screen Placeholder")
        }
        composable("Notifications") {
            Text("Notifications Screen Placeholder")
        }
    }
}
