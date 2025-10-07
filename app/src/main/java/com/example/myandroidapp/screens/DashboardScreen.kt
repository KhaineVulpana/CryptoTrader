package com.example.myandroidapp.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.myandroidapp.components.EquityCurveChart
import com.example.myandroidapp.components.PolicyConflictBanner
import com.example.myandroidapp.shared.SharedAppViewModel
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun DashboardScreen(viewModel: SharedAppViewModel, onNavigate: (String) -> Unit) {
    val equityCurve by viewModel.equityCurve.collectAsStateWithLifecycle()
    val portfolioValue by viewModel.portfolioValue.collectAsStateWithLifecycle()
    val strategies by viewModel.strategies.collectAsStateWithLifecycle()
    val alerts by viewModel.alerts.collectAsStateWithLifecycle()
    val timeline by viewModel.timeline.collectAsStateWithLifecycle()

    val currencyFormatter = NumberFormat.getCurrencyInstance(Locale.US)
    val dateFormatter = SimpleDateFormat("MMM d, HH:mm", Locale.US)

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Portfolio equity", style = MaterialTheme.typography.titleSmall)
                    Text(
                        currencyFormatter.format(portfolioValue),
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    )
                    Text("Updated ${dateFormatter.format(System.currentTimeMillis())}", style = MaterialTheme.typography.bodySmall)
                    Button(onClick = { onNavigate("portfolio") }) { Text("View portfolio") }
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Equity curve", style = MaterialTheme.typography.titleSmall)
                    EquityCurveChart(points = equityCurve, modifier = Modifier.fillMaxWidth())
                }
            }
        }

        if (alerts.isNotEmpty()) {
            item { Text("Policy conflicts", style = MaterialTheme.typography.titleMedium) }
            items(alerts.take(2)) { banner ->
                PolicyConflictBanner(banner = banner, onNavigate = onNavigate)
            }
            item { Divider() }
        }

        item { Text("Active strategies", style = MaterialTheme.typography.titleMedium) }
        items(strategies) { strategy ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(strategy.name, style = MaterialTheme.typography.titleMedium)
                    Text(strategy.description, style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("CAGR ${strategy.cagr.formatPercent()}", style = MaterialTheme.typography.bodySmall)
                        Text("DD ${strategy.maxDrawdown.formatPercent()}", style = MaterialTheme.typography.bodySmall)
                    }
                    Button(onClick = { onNavigate("strategies") }) { Text("Strategy details") }
                }
            }
        }

        if (timeline.isNotEmpty()) {
            item { Text("Latest activity", style = MaterialTheme.typography.titleMedium) }
            items(timeline.take(4)) { entry ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(entry.headline, style = MaterialTheme.typography.titleSmall)
                        Text(entry.detail, style = MaterialTheme.typography.bodySmall)
                        Text(dateFormatter.format(entry.ts), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            item {
                Button(onClick = { onNavigate("timeline") }) { Text("Open timeline") }
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

private fun Double.formatPercent(): String = String.format(Locale.US, "%.1f%%", this * 100)

