package com.example.myandroidapp.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.myandroidapp.components.ChartTradeSplit
import com.example.myandroidapp.components.VisualBlockBuilder
import com.example.myandroidapp.shared.AutomationAction
import com.example.myandroidapp.shared.AutomationFilter
import com.example.myandroidapp.shared.AutomationRule
import com.example.myandroidapp.shared.SharedAppViewModel

private val toolTabs = listOf("Trade", "Automation", "Notifications", "Analysis")

@Composable
fun ToolsSection(viewModel: SharedAppViewModel) {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = "Trade") {
        composable("Trade") { TradeTab(nav, viewModel) }
        composable("Automation") { BlockTab(nav, viewModel, "Automation") }
        composable("Notifications") { BlockTab(nav, viewModel, "Notifications") }
        composable("Analysis") { BlockTab(nav, viewModel, "Analysis") }
    }
}

@Composable
fun ToolTopTabs(nav: NavController, current: String) {
    TabRow(selectedTabIndex = toolTabs.indexOf(current)) {
        toolTabs.forEach { tab ->
            Tab(selected = current == tab, onClick = { nav.navigate(tab) }, text = { Text(tab) })
        }
    }
}

@Composable
fun TradeTab(nav: NavController, viewModel: SharedAppViewModel) {
    Column(Modifier.fillMaxSize()) {
        ToolTopTabs(nav, "Trade")
        ChartTradeSplit(viewModel)
    }
}

@Composable
fun BlockTab(nav: NavController, viewModel: SharedAppViewModel, title: String) {
    val filters = remember { mutableStateListOf<AutomationFilter>() }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        ToolTopTabs(nav, title)
        Spacer(Modifier.height(16.dp))
        VisualBlockBuilder(filters) { filters.add(AutomationFilter("", "", "")) }
        Spacer(Modifier.height(16.dp))
        Button(onClick = {
            viewModel.savedAutomations.add(
                AutomationRule(filters.toList(), AutomationAction(title, "", "", "", ""), runMode = "once")
            )
        }, modifier = Modifier.fillMaxWidth()) {
            Text("Save to My Stuff")
        }
    }
}
