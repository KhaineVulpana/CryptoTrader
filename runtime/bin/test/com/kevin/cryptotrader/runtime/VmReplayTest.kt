package com.kevin.cryptotrader.runtime

import com.kevin.cryptotrader.contracts.AutomationDef
import com.kevin.cryptotrader.contracts.RuntimeEnv
import com.kevin.cryptotrader.contracts.Side
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class VmReplayTest {
  private fun runtime(): AutomationRuntimeImpl = AutomationRuntimeImpl()

  @Test
  fun ema_cross_deterministic_replay() = runBlocking {
    val programJson = """
      {
        "id":"p1",
        "version":1,
        "interval":"H1",
        "inputsCsvPath":"fixtures/ohlcv/BTCUSDT_1h_sample.csv",
        "series":[
          {"name":"ema12","type":"EMA","period":12,"source":"CLOSE"},
          {"name":"ema26","type":"EMA","period":26,"source":"CLOSE"}
        ],
        "rules":[
          {
            "id":"long",
            "oncePerBar":true,
            "guard":{ "type":"crosses", "left":{"type":"series","name":"ema12"}, "dir":"ABOVE", "right":{"type":"series","name":"ema26"} },
            "action":{ "type":"EMIT", "symbol":"BTCUSDT", "side":"BUY", "kind":"signal"},
            "quota":{ "max": 10, "windowMs": 86400000 }
          },
          {
            "id":"short",
            "oncePerBar":true,
            "guard":{ "type":"crosses", "left":{"type":"series","name":"ema12"}, "dir":"BELOW", "right":{"type":"series","name":"ema26"} },
            "action":{ "type":"EMIT", "symbol":"BTCUSDT", "side":"SELL", "kind":"signal"},
            "quota":{ "max": 10, "windowMs": 86400000 }
          }
        ]
      }
    """.trimIndent()

    val rt = runtime()
    rt.load(AutomationDef(id = "p1", version = 1, graphJson = programJson))
    val env = RuntimeEnv(clockMs = { 0L })
    val first = rt.run(env).toList().map { it.side to it.symbol }
    val second = rt.run(env).toList().map { it.side to it.symbol }
    assertEquals(first, second)
  }

  @Test
  fun threshold_with_delay_and_quota() = runBlocking {
    // inline inputs with timestamps 0, 1000, 2000
    val programJson = """
      {
        "id":"p2",
        "version":1,
        "interval":"M1",
        "inputsInline":[
          {"ts":0,"open":0,"high":0,"low":0,"close":5,"volume":0},
          {"ts":1000,"open":0,"high":0,"low":0,"close":15,"volume":0},
          {"ts":2000,"open":0,"high":0,"low":0,"close":20,"volume":0}
        ],
        "series":[],
        "rules":[
          {
            "id":"r1",
            "oncePerBar":false,
            "guard":{ "type":"threshold", "left":{"type":"series","name":"close"}, "op":"GT", "right":{"type":"const","value":10.0} },
            "action":{ "type":"EMIT", "symbol":"X", "side":"BUY", "kind":"thresh"},
            "quota":{ "max": 1, "windowMs": 3600000 },
            "delayMs": 1000
          }
        ]
      }
    """.trimIndent()

    val rt = runtime()
    rt.load(AutomationDef(id = "p2", version = 1, graphJson = programJson))
    val env = RuntimeEnv(clockMs = { 0L })
    val out = rt.run(env).toList()
    // Should emit exactly one intent due at ts >= 2000 due to delay and quota
    assertEquals(1, out.size)
    assertEquals(Side.BUY, out.first().side)
    assertEquals("X", out.first().symbol)
  }
}

