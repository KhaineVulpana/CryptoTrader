package com.example.myandroidapp.monitoring

import android.content.Context
import android.util.Log
import com.kevin.cryptotrader.contracts.BacktestSample
import com.kevin.cryptotrader.contracts.LivePipelineSample
import com.kevin.cryptotrader.contracts.PipelineObserver
import com.kevin.cryptotrader.contracts.ResourceUsageSample

class AppPipelineObserver(context: Context) : PipelineObserver {
    private val monitor = ResourceUsageMonitor.get(context)

    override fun onBacktestSample(sample: BacktestSample) {
        monitor.recordBacktestSample(sample)
        Log.d(TAG, "backtest bar=${sample.barTs} update=${sample.updateDurationMs}ms eval=${sample.evaluationDurationMs}ms emitted=${sample.emittedIntents}")
    }

    override fun onLivePipelineSample(sample: LivePipelineSample) {
        monitor.recordLiveSample(sample)
        Log.d(
            TAG,
            "live ts=${sample.timestampMs} intents=${sample.intents} net=${sample.netDurationMs}ms risk=${sample.riskDurationMs}ms place=${sample.placementDurationMs}ms"
        )
    }

    override fun onResourceSample(sample: ResourceUsageSample) {
        monitor.recordResourceSample(sample)
    }

    companion object {
        private const val TAG = "AppPipelineObserver"
    }
}
