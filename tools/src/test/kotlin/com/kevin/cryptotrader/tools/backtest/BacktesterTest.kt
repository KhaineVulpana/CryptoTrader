package com.kevin.cryptotrader.tools.backtest

import java.nio.file.Files
import java.nio.file.Paths
import com.kevin.cryptotrader.contracts.Interval
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BacktesterTest {
  @Test
  fun runs_ema_cross_strategy_over_fixture() {
    val program = """
      {
        "id":"p1",
        "version":1,
        "interval":"H1",
        "defaultSymbol":"BTCUSDT",
        "inputs":[{"symbol":"BTCUSDT","csvPath":"fixtures/ohlcv/BTCUSDT_1h_sample.csv"}],
        "series":[
          {"name":"ema12","type":"EMA","period":12,"source":"CLOSE","symbol":"BTCUSDT"},
          {"name":"ema26","type":"EMA","period":26,"source":"CLOSE","symbol":"BTCUSDT"}
        ],
        "rules":[
          {
            "id":"long",
            "event":{"type":"candle","symbol":"BTCUSDT"},
            "oncePerBar":true,
            "guard":{ "type":"crosses", "left":{"type":"series","name":"ema12"}, "dir":"ABOVE", "right":{"type":"series","name":"ema26"} },
            "actions":[
              { "type":"emitOrder", "symbol":"BTCUSDT", "side":"BUY", "kind":"signal", "metaStrings": {"risk.mode":"fixed_pct","risk.pct":"0.01"} }
            ],
            "quota":{ "max": 100, "windowMs": 86400000 }
          },
          {
            "id":"short",
            "event":{"type":"candle","symbol":"BTCUSDT"},
            "oncePerBar":true,
            "guard":{ "type":"crosses", "left":{"type":"series","name":"ema12"}, "dir":"BELOW", "right":{"type":"series","name":"ema26"} },
            "actions":[
              { "type":"emitOrder", "symbol":"BTCUSDT", "side":"SELL", "kind":"signal", "metaStrings": {"risk.mode":"fixed_pct","risk.pct":"0.01"} }
            ],
            "quota":{ "max": 100, "windowMs": 86400000 }
          }
        ]
      }
    """.trimIndent()

    // Resolve fixture path exists (for sanity)
    val exists = listOf(
      Paths.get("fixtures/ohlcv/BTCUSDT_1h_sample.csv"),
      Paths.get("../fixtures/ohlcv/BTCUSDT_1h_sample.csv"),
      Paths.get("../../fixtures/ohlcv/BTCUSDT_1h_sample.csv"),
      Paths.get("../../../fixtures/ohlcv/BTCUSDT_1h_sample.csv"),
    ).any { Files.exists(it) }
    assertTrue(exists)

    val bt = Backtester(
      BacktestConfig(
        runId = "wf_fixture",
        programJson = program,
        symbol = "BTCUSDT",
        priceCsvPath = "fixtures/ohlcv/BTCUSDT_1h_sample.csv",
        interval = Interval.H1,
        defaultIntentMeta = mapOf("risk.notional" to "1000"),
      ),
      resultService = null,
    )
    val result = bt.run()
    assertTrue(result.slices.isNotEmpty())
    val slice = result.slices.first()
    assertTrue(slice.equity.isNotEmpty())
    assertTrue(slice.metrics.averageExposure >= 0.0)
    assertEquals(result.aggregated, slice.metrics)
  }
}

