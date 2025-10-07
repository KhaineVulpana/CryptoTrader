package com.kevin.cryptotrader.contracts

import kotlinx.serialization.Serializable

/** Identifies the high level module emitting telemetry. */
@Serializable
enum class TelemetryModule {
    APP,
    CORE,
    RUNTIME,
    LIVE_BROKER,
    PAPER_BROKER,
    PERSISTENCE,
    TOOLS,
    DATA
}

/** Log severity for structured events. */
@Serializable
enum class LogLevel { DEBUG, INFO, WARN, ERROR }

/** Structured log payload sent to observers. */
@Serializable
data class StructuredLog(
    val module: TelemetryModule,
    val level: LogLevel,
    val message: String,
    val timestamp: Long = System.currentTimeMillis(),
    val fields: Map<String, String> = emptyMap()
)

/** Supported health probes for dashboard level monitoring. */
@Serializable
enum class HealthProbeKind {
    WS_LATENCY_MS,
    WS_RECONNECT_COUNT,
    DATA_GAP_SECONDS
}

/** Represents the latest health data point for a particular stream or resource. */
@Serializable
data class HealthProbe(
    val module: TelemetryModule,
    val kind: HealthProbeKind,
    val value: Double,
    val dimension: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val fields: Map<String, String> = emptyMap()
)

/** Crash level reporting emitted from crash handlers. */
@Serializable
data class CrashReport(
    val module: TelemetryModule,
    val threadName: String,
    val message: String,
    val stacktrace: String,
    val timestamp: Long = System.currentTimeMillis()
)

/** ANR style report generated when the UI thread appears frozen. */
@Serializable
data class AnrReport(
    val module: TelemetryModule,
    val durationMs: Long,
    val message: String,
    val timestamp: Long = System.currentTimeMillis()
)

/** Anonymous usage metrics produced when a feature is invoked. */
@Serializable
data class FeatureUsageEvent(
    val module: TelemetryModule,
    val feature: String,
    val properties: Map<String, String> = emptyMap(),
    val timestamp: Long = System.currentTimeMillis()
)

/** Privacy guard describing which signals may be collected. */
@Serializable
data class TelemetryConsent(
    val analyticsEnabled: Boolean = false,
    val crashReportsEnabled: Boolean = false
)

/** Snapshot used when exporting diagnostics. */
@Serializable
data class DiagnosticsSnapshot(
    val generatedAt: Long,
    val consent: TelemetryConsent,
    val logs: List<StructuredLog>,
    val health: List<HealthProbe>,
    val crashes: List<CrashReport>,
    val anrs: List<AnrReport>,
    val features: List<FeatureUsageEvent>
)

/** Contract implemented by sinks that receive telemetry signals. */
interface TelemetrySink {
    fun log(event: StructuredLog)
    fun updateProbe(probe: HealthProbe)
    fun trackCrash(report: CrashReport)
    fun trackAnr(report: AnrReport)
    fun trackFeature(event: FeatureUsageEvent)
    fun updateConsent(consent: TelemetryConsent)
}
