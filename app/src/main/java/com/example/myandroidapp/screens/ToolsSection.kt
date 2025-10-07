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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.myandroidapp.blocks.AutomationSchema
import com.example.myandroidapp.blocks.rememberBlockEditorState
import com.example.myandroidapp.components.ChartTradeSplit
import com.example.myandroidapp.components.VisualBlockBuilder
import com.example.myandroidapp.shared.AutomationVisual
import com.example.myandroidapp.shared.SharedAppViewModel
import com.kevin.cryptotrader.core.telemetry.TelemetryCenter

private val toolTabs = listOf("Trade", "Automation", "Notifications", "Analysis", "Diagnostics")

@Composable
fun ToolsSection(viewModel: SharedAppViewModel) {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = "Trade") {
        composable("Trade") { TradeTab(nav, viewModel) }
        composable("Automation") { BlockTab(nav, viewModel, "Automation") }
        composable("Notifications") { BlockTab(nav, viewModel, "Notifications") }
        composable("Analysis") { BlockTab(nav, viewModel, "Analysis") }
        composable("Diagnostics") { DiagnosticsTab(nav, viewModel) }
    }
}

@Composable
fun ToolTopTabs(nav: NavController, current: String, viewModel: SharedAppViewModel) {
    TabRow(selectedTabIndex = toolTabs.indexOf(current)) {
        toolTabs.forEach { tab ->
            Tab(
                selected = current == tab,
                onClick = {
                    if (current != tab) {
                        viewModel.recordFeature("tools_tab_$tab", mapOf("from" to current))
                        nav.navigate(tab)
                    }
                },
                text = { Text(tab) }
            )
        }
    }
}

@Composable
fun TradeTab(nav: NavController, viewModel: SharedAppViewModel) {
    Column(Modifier.fillMaxSize()) {
        ToolTopTabs(nav, "Trade", viewModel)
        ChartTradeSplit(viewModel)
    }
}

@Composable
fun BlockTab(nav: NavController, viewModel: SharedAppViewModel, title: String) {
    val editorState = rememberBlockEditorState(initialId = title.lowercase())
    val doc = editorState.toAutomationDoc()
    val json = remember(doc) { AutomationSchema.encode(doc) }
    val errors = AutomationSchema.validate(doc, editorState)

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        ToolTopTabs(nav, title, viewModel)
        Spacer(Modifier.height(16.dp))
        VisualBlockBuilder(editorState, Modifier.weight(1f))
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = {
                viewModel.savedAutomations.add(
                    AutomationVisual(
                        id = doc.id,
                        version = doc.v,
                        json = json,
                        nodeCount = doc.graph.nodes.size,
                        description = "${title} flow with ${doc.graph.nodes.size} blocks"
                    )
                )
                viewModel.recordFeature(
                    "save_automation",
                    mapOf("tab" to title, "nodes" to doc.graph.nodes.size.toString())
                )
            },
            enabled = errors.isEmpty(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (errors.isEmpty()) "Save to My Stuff" else "Fix validation errors to save")
        }
    }
}

@Composable
fun DiagnosticsTab(nav: NavController, viewModel: SharedAppViewModel) {
    val consent = viewModel.telemetryConsent
    val logs by TelemetryCenter.logHistory.collectAsState()
    val health by TelemetryCenter.health.collectAsState()
    val crashes by TelemetryCenter.crashes.collectAsState()
    val anrs by TelemetryCenter.anrs.collectAsState()
    val features by TelemetryCenter.features.collectAsState()
    var exported by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        ToolTopTabs(nav, "Diagnostics", viewModel)
        Spacer(Modifier.height(16.dp))
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Usage analytics", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Collect anonymous feature usage when enabled.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Switch(checked = consent.analyticsEnabled, onCheckedChange = { enabled ->
                        viewModel.setAnalyticsOptIn(enabled)
                    })
                }
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Crash & ANR reports", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Send crash diagnostics when opted in.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Switch(checked = consent.crashReportsEnabled, onCheckedChange = { enabled ->
                        viewModel.setCrashOptIn(enabled)
                    })
                }
            }
            item {
                Button(onClick = {
                    viewModel.recordFeature("export_diagnostics")
                    exported = TelemetryCenter.exportDiagnosticsJson()
                }, modifier = Modifier.fillMaxWidth()) {
                    Text("Export diagnostics JSON")
                }
            }
            if (exported != null) {
                item {
                    Spacer(Modifier.height(12.dp))
                    Text("Export preview", style = MaterialTheme.typography.titleSmall)
                }
                item {
                    Card(Modifier.fillMaxWidth()) {
                        SelectionContainer {
                            Text(
                                text = exported ?: "",
                                modifier = Modifier.padding(12.dp),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
            item {
                Spacer(Modifier.height(16.dp))
                Text("Health probes", style = MaterialTheme.typography.titleMedium)
            }
            items(health.take(10)) { probe ->
                Text(
                    "${probe.module.name}/${probe.kind.name} (${probe.dimension ?: "global"}) → ${"%.2f".format(probe.value)}",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }
            item {
                Spacer(Modifier.height(16.dp))
                Text("Recent logs", style = MaterialTheme.typography.titleMedium)
            }
            items(logs.takeLast(10).asReversed()) { log ->
                Text(
                    "[${log.level}] ${log.message}",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }
            if (crashes.isNotEmpty() || anrs.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(16.dp))
                    Text("Stability", style = MaterialTheme.typography.titleMedium)
                }
                items(crashes.takeLast(5)) { crash ->
                    Text(
                        "Crash on ${crash.threadName}: ${crash.message}",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
                items(anrs.takeLast(5)) { anr ->
                    Text(
                        "ANR ${anr.durationMs} ms — ${anr.message}",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }
            if (features.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(16.dp))
                    Text("Feature usage", style = MaterialTheme.typography.titleMedium)
                }
                items(features.takeLast(5).asReversed()) { feature ->
                    Text(
                        "${feature.feature} ${feature.properties}",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun DiagnosticsTab(nav: NavController, viewModel: SharedAppViewModel) {
    val consent = viewModel.telemetryConsent
    val logs by TelemetryCenter.logHistory.collectAsState()
    val health by TelemetryCenter.health.collectAsState()
    val crashes by TelemetryCenter.crashes.collectAsState()
    val anrs by TelemetryCenter.anrs.collectAsState()
    val features by TelemetryCenter.features.collectAsState()
    var exported by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        ToolTopTabs(nav, "Diagnostics", viewModel)
        Spacer(Modifier.height(16.dp))
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Usage analytics", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Collect anonymous feature usage when enabled.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Switch(checked = consent.analyticsEnabled, onCheckedChange = { enabled ->
                        viewModel.setAnalyticsOptIn(enabled)
                    })
                }
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Crash & ANR reports", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Send crash diagnostics when opted in.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Switch(checked = consent.crashReportsEnabled, onCheckedChange = { enabled ->
                        viewModel.setCrashOptIn(enabled)
                    })
                }
            }
            item {
                Button(onClick = {
                    viewModel.recordFeature("export_diagnostics")
                    exported = TelemetryCenter.exportDiagnosticsJson()
                }, modifier = Modifier.fillMaxWidth()) {
                    Text("Export diagnostics JSON")
                }
            }
            if (exported != null) {
                item {
                    Spacer(Modifier.height(12.dp))
                    Text("Export preview", style = MaterialTheme.typography.titleSmall)
                }
                item {
                    Card(Modifier.fillMaxWidth()) {
                        SelectionContainer {
                            Text(
                                text = exported ?: "",
                                modifier = Modifier.padding(12.dp),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
            item {
                Spacer(Modifier.height(16.dp))
                Text("Health probes", style = MaterialTheme.typography.titleMedium)
            }
            items(health.take(10)) { probe ->
                Text(
                    "${probe.module.name}/${probe.kind.name} (${probe.dimension ?: "global"}) → ${"%.2f".format(probe.value)}",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }
            item {
                Spacer(Modifier.height(16.dp))
                Text("Recent logs", style = MaterialTheme.typography.titleMedium)
            }
            items(logs.takeLast(10).asReversed()) { log ->
                Text(
                    "[${log.level}] ${log.message}",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }
            if (crashes.isNotEmpty() || anrs.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(16.dp))
                    Text("Stability", style = MaterialTheme.typography.titleMedium)
                }
                items(crashes.takeLast(5)) { crash ->
                    Text(
                        "Crash on ${crash.threadName}: ${crash.message}",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
                items(anrs.takeLast(5)) { anr ->
                    Text(
                        "ANR ${anr.durationMs} ms — ${anr.message}",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }
            if (features.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(16.dp))
                    Text("Feature usage", style = MaterialTheme.typography.titleMedium)
                }
                items(features.takeLast(5).asReversed()) { feature ->
                    Text(
                        "${feature.feature} ${feature.properties}",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }
        }
    }
}
