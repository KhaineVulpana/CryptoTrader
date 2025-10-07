package com.kevin.cryptotrader.core.telemetry

import com.kevin.cryptotrader.contracts.AnrReport
import com.kevin.cryptotrader.contracts.CrashReport
import com.kevin.cryptotrader.contracts.DiagnosticsSnapshot
import com.kevin.cryptotrader.contracts.FeatureUsageEvent
import com.kevin.cryptotrader.contracts.HealthProbe
import com.kevin.cryptotrader.contracts.HealthProbeKind
import com.kevin.cryptotrader.contracts.LogLevel
import com.kevin.cryptotrader.contracts.StructuredLog
import com.kevin.cryptotrader.contracts.TelemetryConsent
import com.kevin.cryptotrader.contracts.TelemetryModule
import com.kevin.cryptotrader.contracts.TelemetrySink
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Central hub that receives telemetry from every module and exposes observable streams to
 * dashboards and the UI layer.
 */
object TelemetryCenter : TelemetrySink {
    private const val MAX_LOGS = 200
    private const val MAX_EVENTS = 200
    private val lock = Any()
    private val logBuffer = ArrayDeque<StructuredLog>()
    private val featureBuffer = ArrayDeque<FeatureUsageEvent>()
    private val crashBuffer = ArrayDeque<CrashReport>()
    private val anrBuffer = ArrayDeque<AnrReport>()
    private val healthMap = LinkedHashMap<String, HealthProbe>()

    private val _logs = MutableSharedFlow<StructuredLog>(extraBufferCapacity = 256)
    private val _logHistory = MutableStateFlow<List<StructuredLog>>(emptyList())
    private val _health = MutableStateFlow<List<HealthProbe>>(emptyList())
    private val _crashes = MutableStateFlow<List<CrashReport>>(emptyList())
    private val _anrs = MutableStateFlow<List<AnrReport>>(emptyList())
    private val _features = MutableStateFlow<List<FeatureUsageEvent>>(emptyList())
    private val _consent = MutableStateFlow(TelemetryConsent())
    private val json = Json { prettyPrint = true }

    val logStream: SharedFlow<StructuredLog> = _logs.asSharedFlow()
    val logHistory: StateFlow<List<StructuredLog>> = _logHistory.asStateFlow()
    val health: StateFlow<List<HealthProbe>> = _health.asStateFlow()
    val crashes: StateFlow<List<CrashReport>> = _crashes.asStateFlow()
    val anrs: StateFlow<List<AnrReport>> = _anrs.asStateFlow()
    val features: StateFlow<List<FeatureUsageEvent>> = _features.asStateFlow()
    val consent: StateFlow<TelemetryConsent> = _consent.asStateFlow()

    override fun log(event: StructuredLog) {
        synchronized(lock) {
            logBuffer.addLast(event)
            if (logBuffer.size > MAX_LOGS) {
                logBuffer.removeFirst()
            }
            _logHistory.value = logBuffer.toList()
        }
        _logs.tryEmit(event)
    }

    override fun updateProbe(probe: HealthProbe) {
        val key = buildKey(probe)
        synchronized(lock) {
            healthMap[key] = probe
            _health.value = healthMap.values.sortedByDescending { it.timestamp }
        }
    }

    override fun trackCrash(report: CrashReport) {
        if (!_consent.value.crashReportsEnabled) return
        synchronized(lock) {
            crashBuffer.addLast(report)
            if (crashBuffer.size > MAX_EVENTS) {
                crashBuffer.removeFirst()
            }
            _crashes.value = crashBuffer.toList()
        }
        logEvent(
            module = report.module,
            level = LogLevel.ERROR,
            message = "Crash captured",
            fields = mapOf(
                "thread" to report.threadName,
                "message" to report.message
            )
        )
    }

    override fun trackAnr(report: AnrReport) {
        if (!_consent.value.crashReportsEnabled) return
        synchronized(lock) {
            anrBuffer.addLast(report)
            if (anrBuffer.size > MAX_EVENTS) {
                anrBuffer.removeFirst()
            }
            _anrs.value = anrBuffer.toList()
        }
        logEvent(
            module = report.module,
            level = LogLevel.WARN,
            message = "ANR detected",
            fields = mapOf("durationMs" to report.durationMs.toString())
        )
    }

    override fun trackFeature(event: FeatureUsageEvent) {
        if (!_consent.value.analyticsEnabled) return
        synchronized(lock) {
            featureBuffer.addLast(event)
            if (featureBuffer.size > MAX_EVENTS) {
                featureBuffer.removeFirst()
            }
            _features.value = featureBuffer.toList()
        }
    }

    override fun updateConsent(consent: TelemetryConsent) {
        _consent.value = consent
        logEvent(
            module = TelemetryModule.CORE,
            level = LogLevel.INFO,
            message = "Telemetry consent updated",
            fields = mapOf(
                "analytics" to consent.analyticsEnabled.toString(),
                "crashReports" to consent.crashReportsEnabled.toString()
            )
        )
    }

    fun logEvent(
        module: TelemetryModule,
        level: LogLevel,
        message: String,
        fields: Map<String, String> = emptyMap()
    ) {
        log(
            StructuredLog(
                module = module,
                level = level,
                message = message,
                fields = fields
            )
        )
    }

    fun recordWsLatency(
        module: TelemetryModule,
        streamId: String,
        latencyMs: Double,
        fields: Map<String, String> = emptyMap()
    ) {
        updateProbe(
            HealthProbe(
                module = module,
                kind = HealthProbeKind.WS_LATENCY_MS,
                value = latencyMs,
                dimension = streamId,
                fields = fields
            )
        )
    }

    fun recordReconnect(
        module: TelemetryModule,
        streamId: String,
        reconnectCount: Double,
        fields: Map<String, String> = emptyMap()
    ) {
        updateProbe(
            HealthProbe(
                module = module,
                kind = HealthProbeKind.WS_RECONNECT_COUNT,
                value = reconnectCount,
                dimension = streamId,
                fields = fields
            )
        )
    }

    fun recordDataGap(
        module: TelemetryModule,
        streamId: String,
        gapSeconds: Double,
        fields: Map<String, String> = emptyMap()
    ) {
        updateProbe(
            HealthProbe(
                module = module,
                kind = HealthProbeKind.DATA_GAP_SECONDS,
                value = gapSeconds,
                dimension = streamId,
                fields = fields
            )
        )
    }

    fun exportDiagnostics(): DiagnosticsSnapshot = synchronized(lock) {
        DiagnosticsSnapshot(
            generatedAt = System.currentTimeMillis(),
            consent = _consent.value,
            logs = logBuffer.toList(),
            health = _health.value,
            crashes = _crashes.value,
            anrs = _anrs.value,
            features = _features.value
        )
    }

    fun exportDiagnosticsJson(): String = json.encodeToString(exportDiagnostics())

    private fun buildKey(probe: HealthProbe): String =
        listOfNotNull(probe.module.name, probe.kind.name, probe.dimension).joinToString(":")
}
