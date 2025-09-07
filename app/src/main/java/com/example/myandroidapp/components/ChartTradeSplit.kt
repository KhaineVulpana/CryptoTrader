package com.example.myandroidapp.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.myandroidapp.shared.SharedAppViewModel

@Composable
fun ChartTradeSplit(viewModel: SharedAppViewModel) {
    var split by remember { mutableStateOf(false) }
    var ratio by remember { mutableStateOf(0.5f) }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(viewModel.selectedTicker.value ?: "Select a Coin")
            TextButton(onClick = { split = !split }) {
                Text(if (split) "Single View" else "Split View")
            }
        }
        if (split) {
            Slider(value = ratio, onValueChange = { ratio = it })
            Row(Modifier.fillMaxSize()) {
                PlaceholderChart(Modifier.weight(ratio))
                TradeForm(viewModel, Modifier.weight(1 - ratio))
            }
        } else {
            PlaceholderChart(Modifier.fillMaxSize())
        }
    }
}

@Composable
fun TradeForm(viewModel: SharedAppViewModel, modifier: Modifier = Modifier) {
    var mode by remember { mutableStateOf(0) }
    Column(modifier.fillMaxSize().padding(16.dp)) {
        TabRow(selectedTabIndex = mode) {
            Tab(selected = mode == 0, onClick = { mode = 0 }, text = { Text("Buy") })
            Tab(selected = mode == 1, onClick = { mode = 1 }, text = { Text("Sell") })
        }
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(value = "", onValueChange = {}, label = { Text("Amount") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(16.dp))
        Button(onClick = { }, modifier = Modifier.fillMaxWidth()) {
            val action = if (mode == 0) "Buy" else "Sell"
            Text("$action ${viewModel.selectedTicker.value ?: ""}")
        }
    }
}
