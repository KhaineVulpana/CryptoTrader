package com.example.myandroidapp.shared

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.kevin.cryptotrader.contracts.FeatureUsageEvent
import com.kevin.cryptotrader.contracts.LogLevel
import com.kevin.cryptotrader.contracts.TelemetryConsent
import com.kevin.cryptotrader.contracts.TelemetryModule
import com.kevin.cryptotrader.core.telemetry.TelemetryCenter

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
    var telemetryConsent by mutableStateOf(TelemetryCenter.consent.value)

    init {
        TelemetryCenter.logEvent(
            module = TelemetryModule.APP,
            level = LogLevel.INFO,
            message = "App view model initialized"
        )
    }

    fun setAnalyticsOptIn(enabled: Boolean) {
        updateConsent(telemetryConsent.copy(analyticsEnabled = enabled))
    }

    fun setCrashOptIn(enabled: Boolean) {
        updateConsent(telemetryConsent.copy(crashReportsEnabled = enabled))
    }

    private fun updateConsent(consent: TelemetryConsent) {
        telemetryConsent = consent
        TelemetryCenter.updateConsent(consent)
        TelemetryCenter.logEvent(
            module = TelemetryModule.APP,
            level = LogLevel.INFO,
            message = "Telemetry consent changed",
            fields = mapOf(
                "analytics" to consent.analyticsEnabled.toString(),
                "crash" to consent.crashReportsEnabled.toString()
            )
        )
    }

    fun recordFeature(name: String, metadata: Map<String, String> = emptyMap()) {
        TelemetryCenter.trackFeature(
            FeatureUsageEvent(
                module = TelemetryModule.APP,
                feature = name,
                properties = metadata
            )
        )
        TelemetryCenter.logEvent(
            module = TelemetryModule.APP,
            level = LogLevel.DEBUG,
            message = "Feature used",
            fields = metadata + mapOf("feature" to name)
        )
    }
}
