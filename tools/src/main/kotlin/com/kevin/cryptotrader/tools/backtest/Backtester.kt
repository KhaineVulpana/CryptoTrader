package com.kevin.cryptotrader.tools.backtest

import com.kevin.cryptotrader.contracts.AutomationDef
import com.kevin.cryptotrader.contracts.Candle
import com.kevin.cryptotrader.contracts.Interval
import com.kevin.cryptotrader.contracts.PolicyConfig
import com.kevin.cryptotrader.contracts.SimulationConfig
import com.kevin.cryptotrader.contracts.SimulationCosts
import com.kevin.cryptotrader.contracts.SimulationLatencyConfig
import com.kevin.cryptotrader.contracts.SimulationResult
import com.kevin.cryptotrader.contracts.WalkForwardSplit
import com.kevin.cryptotrader.persistence.backtest.BacktestResultService
import com.kevin.cryptotrader.runtime.vm.InputLoader
import kotlinx.coroutines.runBlocking

/**
 * High-level facade wiring CSV market data, automation JSON, and simulator configuration.
 */
data class BacktestConfig(
   val runId: String,
   val programJson: String,
   val symbol: String,
   val priceCsvPath: String,
   val interval: Interval,
   val source: String = "backtest",
   val splits: List<WalkForwardSplit> = emptyList(),
   val policyConfig: PolicyConfig = PolicyConfig(),
  val latency: SimulationLatencyConfig = SimulationLatencyConfig(),
  val costs: SimulationCosts = SimulationCosts(),
  val initialEquityUsd: Double = 100_000.0,
  val defaultIntentMeta: Map<String, String> = emptyMap(),
)
 
class Backtester(
  private val cfg: BacktestConfig,
  private val simulator: EventDrivenSimulator = EventDrivenSimulator(),
  private val resultService: BacktestResultService? = null,
) {
  fun run(): SimulationResult {
    val automation = AutomationDef(id = cfg.runId, version = 1, graphJson = cfg.programJson)
    val bars = InputLoader.fromCsv(cfg.priceCsvPath)
    val candles = bars.map { bar ->
      Candle(
        ts = bar.ts,
        open = bar.open,
        high = bar.high,
        low = bar.low,
        close = bar.close,
        volume = bar.volume,
        interval = cfg.interval,
        symbol = cfg.symbol,
        source = cfg.source,
      )
    }
    val simConfig = SimulationConfig(
      runId = cfg.runId,
      automation = automation,
      symbol = cfg.symbol,
      candles = candles,
      splits = cfg.splits,
      initialEquityUsd = cfg.initialEquityUsd,
      latency = cfg.latency,
      costs = cfg.costs,
      policyConfig = cfg.policyConfig,
      defaultIntentMeta = cfg.defaultIntentMeta,
    )
    val result = simulator.runBlocking(simConfig)
    resultService?.let { service ->
      runBlocking { service.persist(result) }
    }
    return result
  }
}
