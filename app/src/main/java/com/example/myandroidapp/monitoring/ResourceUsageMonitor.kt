package com.example.myandroidapp.monitoring

import android.content.Context
import android.util.Log
import com.kevin.cryptotrader.contracts.BacktestSample
import com.kevin.cryptotrader.contracts.LivePipelineSample
import com.kevin.cryptotrader.contracts.ResourceUsageSample
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max

class ResourceUsageMonitor private constructor(private val appContext: Context) {
    private val heapThresholdBytes = AtomicLong(DEFAULT_HEAP_THRESHOLD_BYTES)
    private val workerRuntimeBudgetMs = AtomicLong(DEFAULT_WORKER_RUNTIME_MS)
    private val peakHeapBytes = AtomicLong(0L)

    fun recordBacktestSample(sample: BacktestSample) {
        if (sample.updateDurationMs > SERIES_UPDATE_BUDGET_MS) {
            Log.w(TAG, "Series update exceeded budget: ${sample.updateDurationMs} ms @${sample.barTs}")
        }
        if (sample.evaluationDurationMs > GUARD_EVAL_BUDGET_MS) {
            Log.w(TAG, "Guard evaluation exceeded budget: ${sample.evaluationDurationMs} ms @${sample.barTs}")
        }
    }

    fun recordLiveSample(sample: LivePipelineSample) {
        if (sample.netDurationMs > NETTING_BUDGET_MS) {
            Log.w(TAG, "Policy netting exceeded budget: ${sample.netDurationMs} ms")
        }
        if (sample.riskDurationMs > RISK_BUDGET_MS) {
            Log.w(TAG, "Risk sizing exceeded budget: ${sample.riskDurationMs} ms")
        }
        if (sample.placementDurationMs > PLACEMENT_BUDGET_MS) {
            Log.w(TAG, "Order routing exceeded budget: ${sample.placementDurationMs} ms")
        }
    }

    fun recordResourceSample(sample: ResourceUsageSample) {
        val peak = peakHeapBytes.updateAndGet { current -> max(current, sample.heapUsedBytes) }
        if (sample.heapUsedBytes > heapThresholdBytes.get()) {
            Log.e(
                TAG,
                "Heap usage ${sample.heapUsedBytes} bytes exceeded threshold ${heapThresholdBytes.get()} bytes (peak=$peak)"
            )
        }
    }

    fun trackWorkerRuntime(elapsedMs: Long): Boolean {
        val budget = workerRuntimeBudgetMs.get()
        val within = elapsedMs <= budget
        if (!within) {
            Log.w(TAG, "Worker runtime ${elapsedMs} ms exceeded budget ${budget} ms")
        }
        return within
    }

    fun updateHeapThreshold(bytes: Long) {
        heapThresholdBytes.set(bytes)
    }

    fun updateWorkerRuntimeBudget(ms: Long) {
        workerRuntimeBudgetMs.set(ms)
    }

    companion object {
        private const val TAG = "ResourceUsageMonitor"
        private const val DEFAULT_HEAP_THRESHOLD_BYTES = 512L * 1024L * 1024L
        private const val DEFAULT_WORKER_RUNTIME_MS = 5_000L
        private const val SERIES_UPDATE_BUDGET_MS = 2.5
        private const val GUARD_EVAL_BUDGET_MS = 4.5
        private const val NETTING_BUDGET_MS = 10.0
        private const val RISK_BUDGET_MS = 8.0
        private const val PLACEMENT_BUDGET_MS = 12.0

        @Volatile
        private var instance: ResourceUsageMonitor? = null

        fun get(context: Context): ResourceUsageMonitor {
            return instance ?: synchronized(this) {
                instance ?: ResourceUsageMonitor(context.applicationContext).also { instance = it }
            }
        }
    }
}
