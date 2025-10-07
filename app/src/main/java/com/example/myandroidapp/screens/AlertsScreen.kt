package com.example.myandroidapp.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.myandroidapp.components.PolicyConflictBanner
import com.example.myandroidapp.shared.SharedAppViewModel

@Composable
fun AlertsScreen(viewModel: SharedAppViewModel, onNavigate: (String) -> Unit) {
    val alerts by viewModel.alerts.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (alerts.isEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("All clear", style = MaterialTheme.typography.titleMedium)
                    Text("No active policy conflicts or funding alerts", style = MaterialTheme.typography.bodyMedium)
                }
            }
        } else {
            items(alerts) { banner ->
                PolicyConflictBanner(banner = banner, onNavigate = onNavigate)
                Button(onClick = { viewModel.dismissAlert(banner.id) }) { Text("Dismiss") }
            }
        }
    }
}

