package com.example.myandroidapp.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.myandroidapp.components.BuyAnywherePlanner
import com.example.myandroidapp.components.PolicyConflictBanner
import com.example.myandroidapp.shared.PlannerUpdate
import com.example.myandroidapp.shared.SharedAppViewModel
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun AutomationsScreen(viewModel: SharedAppViewModel, onNavigate: (String) -> Unit) {
    val automations by viewModel.automations.collectAsStateWithLifecycle()
    val planner by viewModel.planner.collectAsStateWithLifecycle()

    val dateFormatter = SimpleDateFormat("MMM d, HH:mm", Locale.US)

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(automations) { automation ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(automation.name, style = MaterialTheme.typography.titleMedium)
                    Text("Schedule ${automation.schedule}", style = MaterialTheme.typography.bodySmall)
                    Text("Status ${automation.status}", style = MaterialTheme.typography.bodySmall)
                    automation.lastRunTs?.let { Text("Last run ${dateFormatter.format(it)}", style = MaterialTheme.typography.bodySmall) }
                    automation.nextRunTs?.let { Text("Next run ${dateFormatter.format(it)}", style = MaterialTheme.typography.bodySmall) }
                    if (automation.conflicts.isNotEmpty()) {
                        automation.conflicts.forEach { banner ->
                            PolicyConflictBanner(banner = banner, onNavigate = onNavigate)
                        }
                    }
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                BuyAnywherePlanner(
                    state = planner,
                    onUpdateInputs = { asset, amount -> viewModel.updatePlanner(PlannerUpdate(asset = asset, amount = amount)) },
                    onGeneratePlan = { viewModel.refreshPlannerPlan() },
                    onOpenFunding = { onNavigate("portfolio") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

