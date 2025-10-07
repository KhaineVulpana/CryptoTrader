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
import com.example.myandroidapp.components.EquityCurveChart
import com.example.myandroidapp.shared.SharedAppViewModel
import java.text.NumberFormat
import java.util.Locale

@Composable
fun BacktestsScreen(viewModel: SharedAppViewModel, onNavigate: (String) -> Unit) {
    val backtests by viewModel.backtests.collectAsStateWithLifecycle()
    val formatter = NumberFormat.getPercentInstance(Locale.US).apply { minimumFractionDigits = 1 }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (backtests.isEmpty()) {
            item { Text("No backtests run yet", style = MaterialTheme.typography.bodyMedium) }
        } else {
            items(backtests) { backtest ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(backtest.name, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                        Text("Trades ${backtest.trades} · Lookback ${backtest.lookbackDays}d", style = MaterialTheme.typography.bodySmall)
                        EquityCurveChart(points = backtest.equityCurve, modifier = Modifier.fillMaxWidth())
                        Text(
                            "CAGR ${formatter.format(backtest.cagr)} · Sharpe ${String.format(Locale.US, "%.2f", backtest.sharpe)} · Max DD ${formatter.format(backtest.maxDrawdown)}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Button(onClick = { onNavigate("sim_params") }) { Text("Adjust sim params") }
                    }
                }
            }
        }
    }
}

