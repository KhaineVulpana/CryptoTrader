package com.example.myandroidapp.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.myandroidapp.components.PlaceholderChart
import com.example.myandroidapp.components.PlaceholderTable
import com.example.myandroidapp.shared.SharedAppViewModel

private val homeTabs = listOf("Overview", "Portfolio", "My Stuff")

@Composable
fun HomeSection(viewModel: SharedAppViewModel) {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = "Portfolio") {
        composable("Overview") { HomeOverview(nav, viewModel) }
        composable("Portfolio") { HomePortfolio(nav, viewModel) }
        composable("My Stuff") { HomeMyStuff(nav, viewModel) }
    }
}

@Composable
fun HomeTopTabs(nav: NavController, current: String, viewModel: SharedAppViewModel) {
    TabRow(selectedTabIndex = homeTabs.indexOf(current)) {
        homeTabs.forEach { tab ->
            Tab(
                selected = current == tab,
                onClick = {
                    if (current != tab) {
                        viewModel.recordFeature("home_tab_$tab", mapOf("from" to current))
                        nav.navigate(tab)
                    }
                },
                text = { Text(tab) }
            )
        }
    }
}

@Composable
fun HomeOverview(nav: NavController, viewModel: SharedAppViewModel) {
    Column(Modifier.fillMaxSize()) {
        HomeTopTabs(nav, "Overview", viewModel)
        LazyColumn(Modifier.fillMaxSize()) {
            item { Text("Overview content (market stats, charts, trending)", modifier = Modifier.padding(16.dp)) }
            item { PlaceholderChart() }
            item { PlaceholderTable() }
        }
    }
}

@Composable
fun HomePortfolio(nav: NavController, viewModel: SharedAppViewModel) {
    Column(Modifier.fillMaxSize()) {
        HomeTopTabs(nav, "Portfolio", viewModel)
        LazyColumn(Modifier.fillMaxSize().padding(16.dp)) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("$47,832.42", style = MaterialTheme.typography.headlineSmall)
                        Text("+6.32%", color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
            items(listOf("BTC" to "Bitcoin", "ETH" to "Ethereum", "USDC" to "USD Coin")) { (sym, name) ->
                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column { Text(name); Text(sym, style = MaterialTheme.typography.bodySmall) }
                        Column { Text("0.0", style = MaterialTheme.typography.bodyMedium); Text("$0.00", style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
        }
    }
}

@Composable
fun HomeMyStuff(nav: NavController, viewModel: SharedAppViewModel) {
    Column(Modifier.fillMaxSize()) {
        HomeTopTabs(nav, "My Stuff", viewModel)
        LazyColumn(Modifier.fillMaxSize().padding(16.dp)) {
            if (viewModel.savedAutomations.isEmpty()) {
                item { Text("No saved items") }
            } else {
                items(viewModel.savedAutomations) { rule ->
                    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Saved Rule", style = MaterialTheme.typography.titleSmall)
                            Text(rule.filters.joinToString { it.metric })
                        }
                    }
                }
            }
        }
    }
}
