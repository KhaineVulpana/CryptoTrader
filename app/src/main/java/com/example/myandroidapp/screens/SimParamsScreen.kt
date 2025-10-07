package com.example.myandroidapp.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.myandroidapp.shared.SharedAppViewModel
import java.util.Locale

@Composable
fun SimParamsScreen(viewModel: SharedAppViewModel, onNavigateBacktests: () -> Unit) {
    val state by viewModel.simParams.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Simulation params", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                Text("Starting balance ${state.startingBalanceUsd.formatUsd()}")
                Slider(
                    value = state.startingBalanceUsd.toFloat(),
                    onValueChange = { value -> viewModel.updateSimParams { it.copy(startingBalanceUsd = value.toDouble()) } },
                    valueRange = 50_000f..500_000f,
                    steps = 8,
                )

                Text("Slippage ${state.slippageBps} bps")
                Slider(
                    value = state.slippageBps.toFloat(),
                    onValueChange = { value -> viewModel.updateSimParams { it.copy(slippageBps = value.toInt()) } },
                    valueRange = 0f..30f,
                )

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Include fees", modifier = Modifier.weight(1f))
                    Switch(
                        checked = state.includeFees,
                        onCheckedChange = { checked -> viewModel.updateSimParams { params -> params.copy(includeFees = checked) } }
                    )
                }

                Text("Leverage ${String.format(Locale.US, "%.1fx", state.leverage)}")
                Slider(
                    value = state.leverage.toFloat(),
                    onValueChange = { value -> viewModel.updateSimParams { it.copy(leverage = value.toDouble()) } },
                    valueRange = 1f..5f,
                )

                Text("Warmup bars ${state.warmupBars}")
                Slider(
                    value = state.warmupBars.toFloat(),
                    onValueChange = { value -> viewModel.updateSimParams { it.copy(warmupBars = value.toInt()) } },
                    valueRange = 0f..720f,
                )

                OutlinedTextField(
                    value = state.venueId,
                    onValueChange = { value -> viewModel.updateSimParams { params -> params.copy(venueId = value) } },
                    label = { Text("Venue id") },
                    modifier = Modifier.fillMaxWidth(),
                )

                if (state.error != null) {
                    Text(state.error!!, color = MaterialTheme.colorScheme.error)
                }

                Button(onClick = onNavigateBacktests) { Text("Run backtests") }
            }
        }
    }
}

private fun Double.formatUsd(): String = "$" + String.format(Locale.US, "%,.0f", this)

