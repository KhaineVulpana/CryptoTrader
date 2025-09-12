package com.kevin.cryptotrader.tools.backtest

import com.kevin.cryptotrader.contracts.AutomationDef
import com.kevin.cryptotrader.contracts.Intent
import com.kevin.cryptotrader.contracts.RuntimeEnv
import com.kevin.cryptotrader.core.policy.PolicyEngineImpl
import com.kevin.cryptotrader.core.policy.RiskSizerImpl
import com.kevin.cryptotrader.paperbroker.PaperBroker
import com.kevin.cryptotrader.paperbroker.PaperBrokerConfig
import com.kevin.cryptotrader.paperbroker.PriceSource
import com.kevin.cryptotrader.runtime.AutomationRuntimeImpl
import com.kevin.cryptotrader.runtime.vm.InputLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

data class BacktestConfig(
  val priceCsvPath: String,
  val programJson: String,
  val priority: List<String> = emptyList(),
)

data class BacktestMetrics(
  var bars: Int = 0,
  var intents: Int = 0,
  var orders: Int = 0,
  var fills: Int = 0,
)

class Backtester(private val cfg: BacktestConfig) {
  fun run(): BacktestMetrics {
    val bars = InputLoader.fromCsv(cfg.priceCsvPath)
    val prices = bars.associate { it.ts to it.close }
    var now = 0L
    val scope = CoroutineScope(Dispatchers.Default)
    val broker = PaperBroker(
      PaperBrokerConfig(clockMs = { now }, scope = scope),
      priceSource = PriceSource { _, ts -> prices[ts] ?: prices.values.last() },
    )

    val metrics = BacktestMetrics()
    val rt = AutomationRuntimeImpl()
    rt.load(AutomationDef(id = "bt", version = 1, graphJson = cfg.programJson))

    val intentsFlow = rt.run(RuntimeEnv(clockMs = { now }))
    val pendingIntents = mutableListOf<Intent>()
    val job = scope.launch {
      intentsFlow.collect { i -> pendingIntents.add(i) }
    }

    val policy = PolicyEngineImpl(priority = cfg.priority)
    val sizer = RiskSizerImpl()

    bars.forEach { bar ->
      now = bar.ts
      metrics.bars += 1
      // drain intents emitted for this bar
      if (pendingIntents.isNotEmpty()) {
        metrics.intents += pendingIntents.size
        val plan = policy.net(pendingIntents.toList(), positions = emptyList())
        val orders = sizer.size(plan, account = com.kevin.cryptotrader.contracts.AccountSnapshot(0.0, emptyMap()))
        metrics.orders += orders.size
        for (o in orders) {
          scope.launch { broker.place(o) }
        }
        pendingIntents.clear()
      }
    }

    job.cancel()
    return metrics
  }
}

