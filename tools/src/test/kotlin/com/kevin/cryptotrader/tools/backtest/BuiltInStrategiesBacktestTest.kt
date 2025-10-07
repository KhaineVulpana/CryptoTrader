package com.kevin.cryptotrader.tools.backtest

import java.nio.file.Files
import java.nio.file.Paths
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class BuiltInStrategiesBacktestTest {
  private fun loadStrategy(name: String): String {
    val path = resolvePath("fixtures/strategies/$name.json")
    return Files.readString(path)
  }

  private fun resolvePath(path: String) = listOf(
    Paths.get(path),
    Paths.get("../$path"),
    Paths.get("../../$path"),
    Paths.get("../../../$path"),
  ).firstOrNull { Files.exists(it) } ?: error("Fixture not found: $path")

  private fun runStrategy(name: String): BacktestMetrics {
    val programJson = loadStrategy(name)
    val bt = Backtester(
      BacktestConfig(
        programJson = programJson,
        priority = listOf("strategy.", "automation."),
      ),
    )
    return bt.run()
  }

  @Test
  fun built_in_strategies_have_deterministic_metrics() {
    val expected = mapOf(
      "ema" to BacktestMetrics(bars = 200, intents = 4, orders = 0, fills = 0),
      "rsi" to BacktestMetrics(bars = 200, intents = 10, orders = 0, fills = 0),
      "bollinger" to BacktestMetrics(bars = 200, intents = 22, orders = 0, fills = 0),
      "macd" to BacktestMetrics(bars = 200, intents = 8, orders = 0, fills = 0),
      "donchian" to BacktestMetrics(bars = 200, intents = 0, orders = 0, fills = 0),
      "rotation" to BacktestMetrics(bars = 200, intents = 24, orders = 0, fills = 0),
      "zscore" to BacktestMetrics(bars = 200, intents = 11, orders = 0, fills = 0),
      "grid" to BacktestMetrics(bars = 200, intents = 99, orders = 0, fills = 0),
      "dca" to BacktestMetrics(bars = 200, intents = 4, orders = 1, fills = 0),
      "pair_ratio" to BacktestMetrics(bars = 200, intents = 0, orders = 0, fills = 0),
    )

    val actual = expected.keys.associateWith { name -> runStrategy(name) }
    actual.forEach { (name, metrics) ->
      println("strategy=$name metrics=$metrics")
    }
    expected.forEach { (name, expectedMetrics) ->
      val metrics = actual.getValue(name)
      assertEquals(expectedMetrics.bars, metrics.bars, "$name bars")
      assertEquals(expectedMetrics.intents, metrics.intents, "$name intents")
      assertEquals(expectedMetrics.orders, metrics.orders, "$name orders")
    }
  }
}
