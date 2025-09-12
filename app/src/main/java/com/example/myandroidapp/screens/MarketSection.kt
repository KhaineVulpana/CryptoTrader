package com.example.myandroidapp.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.*

import com.example.myandroidapp.components.ScrollAwareScaffold
import com.example.myandroidapp.components.ChartTradeSplit
import com.example.myandroidapp.shared.SharedAppViewModel
private val marketTabs = listOf("Index", "Charts", "Trends")

@Composable
fun MarketSection(viewModel: SharedAppViewModel) {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = "Index") {
        composable("Index") { IndexTab(nav, viewModel) }
        composable("Charts") { ChartsTab(nav, viewModel) }
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
fun IndexTab(nav: NavController, viewModel: SharedAppViewModel) {
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
        items(listOf("BTC", "ETH", "BNB", "SOL")) { sym ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        viewModel.selectedTicker.value = sym
                        nav.navigate("Charts")
                    }
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(sym)
                Text("$0.00")
            }
        }
    }
}

@Composable
fun ChartsTab(nav: NavController, viewModel: SharedAppViewModel) {
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
        item { ChartTradeSplit(viewModel) }
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
