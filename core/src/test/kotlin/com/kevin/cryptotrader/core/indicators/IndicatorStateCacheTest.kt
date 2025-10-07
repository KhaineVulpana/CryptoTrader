package com.kevin.cryptotrader.core.indicators

import com.kevin.cryptotrader.contracts.Interval
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame

class IndicatorStateCacheTest {
  @org.junit.jupiter.api.Test
  fun memoizesBySymbolIntervalAndParams() {
    val cache = IndicatorStateCache()
    var created = 0
    val first = cache.getOrCreate("BTCUSDT", Interval.H1, "ema", mapOf("period" to 14)) {
      created += 1
      EmaIndicator(14)
    }
    val second = cache.getOrCreate("BTCUSDT", Interval.H1, "ema", mapOf("period" to 14)) {
      created += 1
      EmaIndicator(14)
    }
    assertSame(first, second)
    assertEquals(1, created)
  }

  @org.junit.jupiter.api.Test
  fun differentiatesKeys() {
    val cache = IndicatorStateCache()
    val base = cache.getOrCreate("BTCUSDT", Interval.H1, "ema", mapOf("period" to 14)) { EmaIndicator(14) }
    val diffSymbol = cache.getOrCreate("ETHUSDT", Interval.H1, "ema", mapOf("period" to 14)) { EmaIndicator(14) }
    val diffInterval = cache.getOrCreate("BTCUSDT", Interval.H4, "ema", mapOf("period" to 14)) { EmaIndicator(14) }
    val diffParams = cache.getOrCreate("BTCUSDT", Interval.H1, "ema", mapOf("period" to 20)) { EmaIndicator(20) }
    assertNotSame(base, diffSymbol)
    assertNotSame(base, diffInterval)
    assertNotSame(base, diffParams)
  }
}
