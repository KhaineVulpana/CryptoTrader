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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.myandroidapp.shared.SharedAppViewModel
import com.kevin.cryptotrader.data.portfolio.AggregatedHolding
import com.kevin.cryptotrader.data.portfolio.AggregatedPosition
import java.util.Locale

@Composable
fun PortfolioScreen(viewModel: SharedAppViewModel, onOpenPlanner: () -> Unit) {
    val holdings by viewModel.aggregatedHoldings.collectAsStateWithLifecycle()
    val positions by viewModel.aggregatedPositions.collectAsStateWithLifecycle()
    val equity by viewModel.portfolioValue.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Total equity", style = MaterialTheme.typography.titleMedium)
                    Text(equity.formatUsd(), style = MaterialTheme.typography.headlineMedium)
                    Button(onClick = onOpenPlanner) { Text("Plan funding") }
                }
            }
        }

        item { Text("Holdings", style = MaterialTheme.typography.titleMedium) }
        if (holdings.isEmpty()) {
            item { Text("No balances loaded", style = MaterialTheme.typography.bodyMedium) }
        } else {
            items(holdings) { holding -> HoldingCard(holding) }
        }

        item { Text("Positions", style = MaterialTheme.typography.titleMedium) }
        if (positions.isEmpty()) {
            item { Text("No open positions", style = MaterialTheme.typography.bodyMedium) }
        } else {
            items(positions) { position -> PositionCard(position) }
        }
    }
}

@Composable
private fun HoldingCard(holding: AggregatedHolding) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("${holding.asset}${holding.network?.let { " (${it})" } ?: ""}", style = MaterialTheme.typography.titleSmall)
            Text("Free ${holding.totalFree} · Locked ${holding.totalLocked} · Value ${holding.valuationUsd.formatUsd()}", style = MaterialTheme.typography.bodySmall)
            holding.breakdown.forEach { breakdown ->
                Text(
                    "${breakdown.accountName}: ${breakdown.free + breakdown.locked} (${breakdown.valuationUsd.formatUsd()})",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun PositionCard(position: AggregatedPosition) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(position.symbol, style = MaterialTheme.typography.titleSmall)
            Text(
                "Net ${position.netQty} @ ${position.avgPrice.formatUsd()} · Realized ${position.realizedPnl.formatUsd()} · Unrealized ${position.unrealizedPnl.formatUsd()}",
                style = MaterialTheme.typography.bodySmall,
            )
            position.breakdown.forEach { breakdown ->
                Text(
                    "${breakdown.accountName}: ${breakdown.qty} @ ${breakdown.avgPrice.formatUsd()} (${breakdown.unrealizedPnl.formatUsd()})",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

private fun Double.formatUsd(): String = "$" + String.format(Locale.US, "%,.2f", this)

