package com.example.myandroidapp.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.myandroidapp.components.PolicyConflictBanner
import com.example.myandroidapp.shared.SharedAppViewModel
import java.util.Locale

@Composable
fun StrategiesScreen(viewModel: SharedAppViewModel, onNavigate: (String) -> Unit) {
    val strategies by viewModel.strategies.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (strategies.isEmpty()) {
            item { Text("No strategies deployed yet", style = MaterialTheme.typography.bodyMedium) }
        } else {
            items(strategies) { strategy ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(strategy.name, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                        Text(strategy.description, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Capital ${strategy.capitalAllocatedUsd.formatUsd()} · CAGR ${strategy.cagr.formatPercent()} · Max DD ${strategy.maxDrawdown.formatPercent()}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        if (strategy.conflicts.isNotEmpty()) {
                            strategy.conflicts.forEach { banner ->
                                PolicyConflictBanner(banner = banner, onNavigate = onNavigate)
                            }
                        }
                        Button(onClick = { onNavigate("backtests") }) { Text("View backtests") }
                    }
                }
            }
        }
    }
}

private fun Double.formatUsd(): String = "$" + String.format(Locale.US, "%,.0f", this)
private fun Double.formatPercent(): String = String.format(Locale.US, "%.1f%%", this * 100)

