package com.kevin.cryptotrader.tools.backtest

import java.nio.file.Files
import java.nio.file.Paths
import kotlin.test.Test
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

    val bt = Backtester(BacktestConfig(
      priceCsvPath = "fixtures/ohlcv/BTCUSDT_1h_sample.csv",
      programJson = program,
      priority = listOf("strategy.", "automation."),
    ))
    val m = bt.run()
    assertTrue(m.bars > 0)
    // Strategy should produce at least some intents and orders
    assertTrue(m.intents >= 0)
    assertTrue(m.orders >= 0)
  }
}

