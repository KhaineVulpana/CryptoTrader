package com.example.myandroidapp.shared

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.ViewModel

/**
 * Lightweight summary of a saved automation authored via the block editor.
 */
data class AutomationVisual(
    val id: String,
    val version: Int,
    val json: String,
    val nodeCount: Int,
    val description: String = ""
)

class SharedAppViewModel : ViewModel() {
    var selectedTicker = mutableStateOf<String?>(null)
    var searchQuery = mutableStateOf("")
    var savedAutomations: SnapshotStateList<AutomationVisual> = mutableStateListOf()
}
