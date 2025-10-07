package com.kevin.cryptotrader.tools.backtest

import com.kevin.cryptotrader.contracts.AutomationDef
import com.kevin.cryptotrader.contracts.Intent
import com.kevin.cryptotrader.contracts.PolicyConfig
import com.kevin.cryptotrader.contracts.PolicyMode
import com.kevin.cryptotrader.contracts.RuntimeEnv
import com.kevin.cryptotrader.core.policy.PolicyEngineImpl
import com.kevin.cryptotrader.core.policy.RiskSizerImpl
import com.kevin.cryptotrader.paperbroker.PaperBroker
import com.kevin.cryptotrader.paperbroker.PaperBrokerConfig
import com.kevin.cryptotrader.paperbroker.PriceSource
import com.kevin.cryptotrader.runtime.AutomationRuntimeImpl
import com.kevin.cryptotrader.runtime.vm.InputLoader
import com.kevin.cryptotrader.runtime.vm.ProgramJson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json

data class BacktestConfig(
  val priceCsvPath: String? = null,
  val priceCsvBySymbol: Map<String, String> = emptyMap(),
  val programJson: String,
  val priority: List<String> = emptyList(),
  val policyConfig: PolicyConfig? = null,
  val equityUsd: Double = 100_000.0,
)

data class BacktestMetrics(
  var bars: Int = 0,
  var intents: Int = 0,
  var orders: Int = 0,
  var fills: Int = 0,
)

class Backtester(private val cfg: BacktestConfig) {
  fun run(): BacktestMetrics {
    val program = Json { ignoreUnknownKeys = true; classDiscriminator = "type" }
      .decodeFromString(ProgramJson.serializer(), cfg.programJson)

    val inputs = InputLoader.loadInputs(program).toMutableMap()

    if (cfg.priceCsvPath != null) {
      val symbol = program.defaultSymbol ?: inputs.keys.firstOrNull() ?: "BTCUSDT"
      inputs[symbol] = InputLoader.fromCsv(cfg.priceCsvPath)
    }
    cfg.priceCsvBySymbol.forEach { (symbol, path) ->
      inputs[symbol] = InputLoader.fromCsv(path)
    }

    val defaultSymbol = program.defaultSymbol ?: inputs.keys.firstOrNull() ?: "BTCUSDT"
    val primaryBars = inputs[defaultSymbol] ?: emptyList()
    val priceMaps = inputs.mapValues { (_, list) -> list.associate { it.ts to it.close } }
    val defaultPrices = priceMaps[defaultSymbol].orEmpty()
    var now = 0L
    val scope = CoroutineScope(Dispatchers.Default)
    val broker = PaperBroker(
      PaperBrokerConfig(clockMs = { now }, scope = scope),
      priceSource = PriceSource { symbol, ts ->
        val map = priceMaps[symbol]?.takeIf { it.isNotEmpty() } ?: defaultPrices
        map[ts] ?: map.entries.lastOrNull()?.value ?: defaultPrices.entries.lastOrNull()?.value ?: 0.0
      },
    )

    val metrics = BacktestMetrics()
    val rt = AutomationRuntimeImpl()
    rt.load(AutomationDef(id = "bt", version = 1, graphJson = cfg.programJson))

    val intentsFlow = rt.run(RuntimeEnv(clockMs = { now }))
    val pendingIntents = mutableListOf<Intent>()
    val job = scope.launch {
      intentsFlow.collect { i -> pendingIntents.add(i) }
    }

    val policy = PolicyEngineImpl(
      cfg.policyConfig ?: if (cfg.priority.isNotEmpty()) {
        PolicyConfig(mode = PolicyMode.PRIORITY, priority = cfg.priority)
      } else {
        PolicyConfig()
      },
    )
    val sizer = RiskSizerImpl()

    primaryBars.forEach { bar ->
      now = bar.ts
      metrics.bars += 1
      // drain intents emitted for this bar
      if (pendingIntents.isNotEmpty()) {
        metrics.intents += pendingIntents.size
        val plan = policy.net(pendingIntents.toList(), positions = emptyList())
        val riskResult = sizer.size(
          plan,
          account = com.kevin.cryptotrader.contracts.AccountSnapshot(cfg.equityUsd, emptyMap()),
        )
        metrics.orders += riskResult.orders.size
        for (o in riskResult.orders) {
          scope.launch { broker.place(o) }
        }
        // Stop orders are emitted for analytics but not placed in the paper broker during backtests.
        pendingIntents.clear()
      }
    }

    runBlocking { job.join() }
    if (pendingIntents.isNotEmpty()) {
      metrics.intents += pendingIntents.size
      val plan = policy.net(pendingIntents.toList(), positions = emptyList())
      val riskResult = sizer.size(
        plan,
        account = com.kevin.cryptotrader.contracts.AccountSnapshot(cfg.equityUsd, emptyMap()),
      )
      metrics.orders += riskResult.orders.size
      pendingIntents.clear()
    }
    return metrics
  }
}

