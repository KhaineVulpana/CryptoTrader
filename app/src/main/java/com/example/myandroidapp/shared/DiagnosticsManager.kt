package com.example.myandroidapp.shared

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.kevin.cryptotrader.contracts.AnrReport
import com.kevin.cryptotrader.contracts.CrashReport
import com.kevin.cryptotrader.contracts.LogLevel
import com.kevin.cryptotrader.contracts.TelemetryModule
import com.kevin.cryptotrader.core.telemetry.TelemetryCenter

/**
 * Hooks crash handlers and schedules a lightweight ANR watchdog so telemetry can surface
 * stability signals in the dashboard.
 */
class DiagnosticsManager(
    private val telemetry: TelemetryCenter = TelemetryCenter,
    private val watchdogIntervalMs: Long = 2_000,
    private val anrThresholdMs: Long = 5_000
) : DefaultLifecycleObserver {

    private val handler = Handler(Looper.getMainLooper())
    private var installedHandler: Thread.UncaughtExceptionHandler? = null
    private var previousHandler: Thread.UncaughtExceptionHandler? = null

    private val watchdogRunnable = object : Runnable {
        override fun run() {
            val heartbeat = SystemClock.uptimeMillis()
            handler.post {
                val delay = SystemClock.uptimeMillis() - heartbeat
                if (delay > anrThresholdMs) {
                    telemetry.trackAnr(
                        AnrReport(
                            module = TelemetryModule.APP,
                            durationMs = delay,
                            message = "Main thread blocked for $delay ms"
                        )
                    )
                }
            }
            handler.postDelayed(this, watchdogIntervalMs)
        }
    }

    override fun onCreate(owner: LifecycleOwner) {
        installCrashHandler()
        telemetry.logEvent(
            module = TelemetryModule.APP,
            level = LogLevel.INFO,
            message = "Diagnostics manager started"
        )
        handler.postDelayed(watchdogRunnable, watchdogIntervalMs)
    }

    override fun onDestroy(owner: LifecycleOwner) {
        handler.removeCallbacksAndMessages(null)
        restoreCrashHandler()
        telemetry.logEvent(
            module = TelemetryModule.APP,
            level = LogLevel.INFO,
            message = "Diagnostics manager stopped"
        )
    }

    private fun installCrashHandler() {
        val existing = Thread.getDefaultUncaughtExceptionHandler()
        if (installedHandler === existing) return
        previousHandler = existing
        installedHandler = Thread.UncaughtExceptionHandler { thread, throwable ->
            telemetry.trackCrash(
                CrashReport(
                    module = TelemetryModule.APP,
                    threadName = thread.name,
                    message = throwable.message ?: throwable::class.java.simpleName,
                    stacktrace = throwable.stackTraceToString()
                )
            )
            previousHandler?.uncaughtException(thread, throwable)
        }
        Thread.setDefaultUncaughtExceptionHandler(installedHandler)
    }

    private fun restoreCrashHandler() {
        Thread.setDefaultUncaughtExceptionHandler(previousHandler)
        installedHandler = null
    }
}
