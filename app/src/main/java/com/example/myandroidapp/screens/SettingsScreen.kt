package com.example.myandroidapp.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Divider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.myandroidapp.shared.SharedAppViewModel
import java.util.Locale

@Composable
fun SettingsScreen(viewModel: SharedAppViewModel, onNavigate: (String) -> Unit) {
    val darkTheme by viewModel.darkTheme.collectAsStateWithLifecycle()
    val simParams by viewModel.simParams.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ListItem(
            headlineContent = { Text("Dark theme") },
            supportingContent = { Text("Applies across dashboard and planner") },
            trailingContent = { Switch(checked = darkTheme, onCheckedChange = { viewModel.setDarkTheme(it) }) },
        )
        Divider()

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Simulation parameters", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Start ${simParams.startingBalanceUsd.formatUsd()} · Slippage ${simParams.slippageBps} bps · Leverage ${simParams.leverage}x",
                    style = MaterialTheme.typography.bodySmall,
                )
                if (simParams.error != null) {
                    Text(simParams.error!!, color = MaterialTheme.colorScheme.error)
                }
                Button(onClick = { onNavigate("sim_params") }) { Text("Configure sim") }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Alerts", style = MaterialTheme.typography.titleMedium)
                Text("View policy conflicts and risk warnings", style = MaterialTheme.typography.bodySmall)
                Button(onClick = { onNavigate("alerts") }) { Text("Open alerts") }
            }
        }

        Button(onClick = { onNavigate("automations") }) { Text("Open Buy Anywhere planner") }
    }
}

private fun Double.formatUsd(): String = "$" + String.format(Locale.US, "%,.0f", this)

