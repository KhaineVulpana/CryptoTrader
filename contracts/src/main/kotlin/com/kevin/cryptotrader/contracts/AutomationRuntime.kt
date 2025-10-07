package com.kevin.cryptotrader.contracts

import kotlinx.coroutines.flow.Flow

interface AutomationRuntime {
  fun load(def: AutomationDef): LoadedProgram

  fun run(env: RuntimeEnv): Flow<Intent>
}

interface LoadedProgram

interface PipelineObserver {
  fun onBacktestSample(sample: BacktestSample) {}

  fun onLivePipelineSample(sample: LivePipelineSample) {}

  fun onResourceSample(sample: ResourceUsageSample) {}

  companion object {
    val NOOP: PipelineObserver = object : PipelineObserver {}
  }
}

data class BacktestSample(
  val barTs: Long,
  val updateDurationMs: Double,
  val evaluationDurationMs: Double,
  val emittedIntents: Int,
)

data class LivePipelineSample(
  val timestampMs: Long,
  val intents: Int,
  val netDurationMs: Double,
  val riskDurationMs: Double,
  val placementDurationMs: Double,
)

data class ResourceUsageSample(
  val timestampMs: Long,
  val heapUsedBytes: Long,
  val heapMaxBytes: Long,
)

data class RuntimeEnv(
  val clockMs: () -> Long,
  val observer: PipelineObserver = PipelineObserver.NOOP,
)
