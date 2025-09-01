package com.example.myandroidapp.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.*

import com.example.myandroidapp.components.PlaceholderChart
import com.example.myandroidapp.components.PlaceholderTable
import com.example.myandroidapp.components.ScrollAwareScaffold
import com.example.myandroidapp.shared.SharedAppViewModel

@Composable
fun MarketSection(viewModel: SharedAppViewModel) {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = "Overview") {
        composable("Overview") { OverviewTab() }
        composable("Index") { PlaceholderTable() }
        composable("Charts") { PlaceholderChart() }
        composable("Trends") { Text("Trends Placeholder") }
    }
}

@Composable
fun OverviewTab() {
    ScrollAwareScaffold(
        searchBarContent = {
            OutlinedTextField(
                value = "",
                onValueChange = {},
                label = { Text("Search Coins") },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            )
        },
        topTabsContent = {
            TabRow(selectedTabIndex = 0) {
                listOf("Overview", "Index", "Charts", "Trends").forEachIndexed { index, tab ->
                    Tab(selected = index == 0, onClick = {}, text = { Text(tab) })
                }
            }
        }
    ) { scrollState ->
        LazyColumn(state = scrollState, modifier = Modifier.padding(16.dp)) {
            item { Text("Charts & Tables (Placeholder)") }
            item { Spacer(modifier = Modifier.height(12.dp)) }
            item { PlaceholderChart() }
            item { Spacer(modifier = Modifier.height(12.dp)) }
            item { PlaceholderTable() }
        }
    }
}
