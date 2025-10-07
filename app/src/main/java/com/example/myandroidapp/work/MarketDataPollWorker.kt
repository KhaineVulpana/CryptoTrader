package com.example.myandroidapp.work

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.myandroidapp.monitoring.ResourceUsageMonitor
import com.kevin.cryptotrader.contracts.ResourceUsageSample
import java.util.concurrent.TimeUnit
import kotlin.system.measureTimeMillis
import kotlinx.coroutines.delay

class MarketDataPollWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    private val monitor = ResourceUsageMonitor.get(appContext)

    override suspend fun doWork(): Result {
        val elapsed = measureTimeMillis {
            // Simulate lightweight polling; real implementation would hit network/cache layers.
            delay(POLL_SIMULATION_DELAY_MS)
        }
        val withinBudget = monitor.trackWorkerRuntime(elapsed)
        monitor.recordResourceSample(currentHeapSample())
        if (!withinBudget) {
            Log.w(TAG, "Polling runtime ${elapsed}ms exceeded budget; scheduling retry")
            return Result.retry()
        }
        return Result.success()
    }

    private fun currentHeapSample(): ResourceUsageSample {
        val runtime = Runtime.getRuntime()
        val used = runtime.totalMemory() - runtime.freeMemory()
        return ResourceUsageSample(
            timestampMs = System.currentTimeMillis(),
            heapUsedBytes = used,
            heapMaxBytes = runtime.maxMemory(),
        )
    }

    companion object {
        private const val TAG = "MarketDataPollWorker"
        private val POLL_SIMULATION_DELAY_MS = TimeUnit.SECONDS.toMillis(1)
    }
}
