package com.example.myandroidapp.shared

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel

data class AutomationFilter(val metric: String, val operator: String, val value: String)
data class AutomationAction(val type: String, val target: String, val orderType: String, val amount: String, val currency: String)
data class AutomationRule(
    val filters: List<AutomationFilter>,
    val action: AutomationAction,
    val runMode: String,
    val scheduleInfo: String = ""
)

class SharedAppViewModel : ViewModel() {
    var selectedTicker = mutableStateOf<String?>(null)
    var searchQuery = mutableStateOf("")
    var savedAutomations = mutableStateListOf<AutomationRule>()
}
