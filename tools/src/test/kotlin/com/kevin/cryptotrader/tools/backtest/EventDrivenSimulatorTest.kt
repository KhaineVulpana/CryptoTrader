package com.kevin.cryptotrader.tools.backtest

import com.kevin.cryptotrader.contracts.AutomationDef
import com.kevin.cryptotrader.contracts.Candle
import com.kevin.cryptotrader.contracts.Interval
import com.kevin.cryptotrader.contracts.SimulationConfig
import com.kevin.cryptotrader.contracts.SimulationCosts
import com.kevin.cryptotrader.contracts.SimulationLatencyConfig
import com.kevin.cryptotrader.contracts.WalkForwardSplit
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EventDrivenSimulatorTest {
  @Test
  fun single_split_generates_trade_and_metrics() = runBlocking {
    val programJson = """
      {
        "id":"wf1",
        "version":1,
        "interval":"M1",
        "inputsInline":[
          {"ts":0,"open":100.0,"high":101.0,"low":99.5,"close":100.0,"volume":10.0},
          {"ts":60000,"open":101.0,"high":105.0,"low":100.5,"close":104.0,"volume":12.0},
          {"ts":120000,"open":104.0,"high":104.5,"low":95.0,"close":96.0,"volume":11.0}
        ],
        "series":[],
        "rules":[
          {
            "id":"enter",
            "oncePerBar":true,
            "guard":{ "type":"threshold", "left":{"type":"series","name":"close"}, "op":"GT", "right":{"type":"const","value":101.0} },
            "action":{ "type":"EMIT", "symbol":"BTCUSD", "side":"BUY", "kind":"long" }
          },
          {
            "id":"exit",
            "oncePerBar":true,
            "guard":{ "type":"threshold", "left":{"type":"series","name":"close"}, "op":"LT", "right":{"type":"const","value":99.0} },
            "action":{ "type":"EMIT", "symbol":"BTCUSD", "side":"SELL", "kind":"exit" }
          }
        ]
      }
    """.trimIndent()

    val automation = AutomationDef(id = "wf1", version = 1, graphJson = programJson)
    val candles = listOf(
      Candle(0, 100.0, 101.0, 99.5, 100.0, 10.0, Interval.M1, "BTCUSD", "test"),
      Candle(60_000, 101.0, 105.0, 100.5, 104.0, 12.0, Interval.M1, "BTCUSD", "test"),
      Candle(120_000, 104.0, 104.5, 95.0, 96.0, 11.0, Interval.M1, "BTCUSD", "test"),
    )
    val split = WalkForwardSplit(
      id = "split-1",
      label = "OOS",
      inSampleStart = 0,
      inSampleEnd = 0,
      outSampleStart = 60_000,
      outSampleEnd = 120_000,
    )
    val simulator = EventDrivenSimulator()
    val config = SimulationConfig(
      runId = "wf1",
      automation = automation,
      symbol = "BTCUSD",
      candles = candles,
      splits = listOf(split),
      initialEquityUsd = 100_000.0,
      latency = SimulationLatencyConfig(),
      costs = SimulationCosts(feeBps = 0, slippageBps = 0),
      defaultIntentMeta = mapOf("risk.notional" to "1000"),
    )

    val result = simulator.run(config)
    assertEquals(1, result.slices.size)
    val slice = result.slices.first()
    assertEquals(1, slice.trades.size)
    val trade = slice.trades.first()
    assertTrue(trade.qty > 0.0)
    val expectedQty = 1000.0 / 104.0
    assertEquals(expectedQty, trade.qty, 1e-6)
    assertEquals(96.0, trade.exitPrice)
    assertEquals(104.0, trade.entryPrice)
    assertTrue(slice.equity.size >= candles.size)
    assertTrue(slice.metrics.averageExposure > 0.0)
    assertEquals(result.aggregated, slice.metrics)
  }
}
