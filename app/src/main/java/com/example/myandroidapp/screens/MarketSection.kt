package com.example.myandroidapp.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.*

import com.example.myandroidapp.components.PlaceholderChart
import com.example.myandroidapp.components.PlaceholderTable
import com.example.myandroidapp.components.ScrollAwareScaffold
import com.example.myandroidapp.shared.SharedAppViewModel

private val marketTabs = listOf("Overview", "Index", "Charts", "Trends")

@Composable
fun MarketSection(viewModel: SharedAppViewModel) {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = "Overview") {
        composable("Overview") { OverviewTab(nav) }
        composable("Index") { IndexTab(nav) }
        composable("Charts") { ChartsTab(nav) }
        composable("Trends") { TrendsTab(nav) }
    }
}

@Composable
fun TopTabs(nav: NavController, current: String) {
    TabRow(selectedTabIndex = marketTabs.indexOf(current)) {
        marketTabs.forEach { tab ->
            Tab(
                selected = current == tab,
                onClick = { nav.navigate(tab) },
                text = { Text(tab) }
            )
        }
    }
}

@Composable
fun OverviewTab(nav: NavController) {
    ScrollAwareScaffold(
        searchBarContent = {
            OutlinedTextField(
                value = "",
                onValueChange = {},
                label = { Text("Search Coins") },
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            )
        },
        topTabsContent = { TopTabs(nav, "Overview") }
    ) {
        item { Text("Charts & Tables (Placeholder)", modifier = Modifier.padding(16.dp)) }
        item { PlaceholderChart() }
        item { PlaceholderTable() }
    }
}

@Composable
fun IndexTab(nav: NavController) {
    ScrollAwareScaffold(
        searchBarContent = {
            OutlinedTextField(
                value = "",
                onValueChange = {},
                label = { Text("Search Coins") },
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            )
        },
        topTabsContent = { TopTabs(nav, "Index") }
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                OutlinedButton(onClick = { /* TODO filters */ }) { Text("Filters") }
                OutlinedButton(onClick = { /* TODO columns */ }) { Text("Columns") }
            }
        }
        item { PlaceholderTable(columns = 6, rowHeight = 20.dp) }
    }
}

@Composable
fun ChartsTab(nav: NavController) {
    ScrollAwareScaffold(
        searchBarContent = {
            OutlinedTextField(
                value = "",
                onValueChange = {},
                label = { Text("Search Coins") },
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            )
        },
        topTabsContent = { TopTabs(nav, "Charts") }
    ) {
        item { PlaceholderChart() }
    }
}

@Composable
fun TrendsTab(nav: NavController) {
    ScrollAwareScaffold(
        searchBarContent = {
            OutlinedTextField(
                value = "",
                onValueChange = {},
                label = { Text("Search Coins") },
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            )
        },
        topTabsContent = { TopTabs(nav, "Trends") }
    ) {
        item { Text("Trends Placeholder", modifier = Modifier.padding(16.dp)) }
    }
}
