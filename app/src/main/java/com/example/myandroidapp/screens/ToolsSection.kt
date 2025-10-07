package com.example.myandroidapp.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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

private val toolTabs = listOf("Trade", "Automation", "Notifications", "Analysis")

@Composable
fun ToolsSection(viewModel: SharedAppViewModel) {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = "Trade") {
        composable("Trade") { TradeTab(nav, viewModel) }
        composable("Automation") { BlockTab(nav, viewModel, "Automation") }
        composable("Notifications") { BlockTab(nav, viewModel, "Notifications") }
        composable("Analysis") { BlockTab(nav, viewModel, "Analysis") }
    }
}

@Composable
fun ToolTopTabs(nav: NavController, current: String) {
    androidx.compose.material3.TabRow(selectedTabIndex = toolTabs.indexOf(current)) {
        toolTabs.forEach { tab ->
            androidx.compose.material3.Tab(selected = current == tab, onClick = { nav.navigate(tab) }, text = { Text(tab) })
        }
    }
}

@Composable
fun TradeTab(nav: NavController, viewModel: SharedAppViewModel) {
    Column(Modifier.fillMaxSize()) {
        ToolTopTabs(nav, "Trade")
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
        ToolTopTabs(nav, title)
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
            },
            enabled = errors.isEmpty(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (errors.isEmpty()) "Save to My Stuff" else "Fix validation errors to save")
        }
    }
}
