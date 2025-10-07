package com.kevin.cryptotrader.contracts

import kotlinx.serialization.Serializable

@Serializable
data class WalkForwardSplit(
  val id: String,
  val label: String,
  val inSampleStart: Long,
  val inSampleEnd: Long,
  val outSampleStart: Long,
  val outSampleEnd: Long,
)

@Serializable
data class EquityPoint(
  val ts: Long,
  val equity: Double,
  val cash: Double,
  val grossExposureUsd: Double,
) {
  val exposure: Double
    get() = if (equity <= 0.0) 0.0 else (grossExposureUsd / equity)
}

@Serializable
data class TradeRecord(
  val symbol: String,
  val side: Side,
  val entryTs: Long,
  val exitTs: Long,
  val qty: Double,
  val entryPrice: Double,
  val exitPrice: Double,
  val feesUsd: Double,
  val pnlUsd: Double,
  val returnPct: Double,
)

@Serializable
data class SimulationMetrics(
  val cagr: Double,
  val sharpe: Double,
  val sortino: Double,
  val maxDrawdown: Double,
  val mar: Double,
  val winRate: Double,
  val averageExposure: Double,
)

@Serializable
data class SimulationSliceResult(
  val split: WalkForwardSplit,
  val metrics: SimulationMetrics,
  val equity: List<EquityPoint>,
  val trades: List<TradeRecord>,
)

@Serializable
data class SimulationResult(
  val runId: String,
  val slices: List<SimulationSliceResult>,
  val aggregated: SimulationMetrics,
)

data class SimulationLatencyConfig(
  val ackLatencyMs: Long = 0,
  val firstFillLatencyMs: Long = 0,
  val perFillIntervalMs: Long = 0,
  val partialPieces: Int = 1,
)

data class SimulationCosts(
  val slippageBps: Int = 0,
  val feeBps: Int = 0,
)

data class SimulationConfig(
  val runId: String,
  val automation: AutomationDef,
  val symbol: String,
  val candles: List<Candle>,
  val splits: List<WalkForwardSplit>,
  val initialEquityUsd: Double = 100_000.0,
  val latency: SimulationLatencyConfig = SimulationLatencyConfig(),
  val costs: SimulationCosts = SimulationCosts(),
  val policyConfig: PolicyConfig = PolicyConfig(),
  val defaultIntentMeta: Map<String, String> = emptyMap(),
)
