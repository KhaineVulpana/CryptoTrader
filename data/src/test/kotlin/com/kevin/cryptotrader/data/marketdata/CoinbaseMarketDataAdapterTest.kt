package com.kevin.cryptotrader.data.marketdata

import com.kevin.cryptotrader.contracts.Interval
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CoinbaseMarketDataAdapterTest {
  private val cache = OhlcvCache()
  private val provider = FixtureOhlcvProvider()
  private val adapter = CoinbaseMarketDataAdapter(cache, provider)

  @Test
  fun `fetch backfills and caches`() = runBlocking {
    val candles = adapter.fetchOhlcv("ETH-USDT", Interval.H1, start = 1672531200000, limit = 2)
    assertEquals(2, candles.size)
    val cached = adapter.fetchOhlcv("ETHUSDT", Interval.H1, limit = 2)
    assertEquals(candles, cached)
  }

  @Test
  fun `trade stream contains deterministic backfill`() = runBlocking {
    val trades = adapter.streamTrades(setOf("ETHUSDT"))
      .take(4)
      .toList()
    assertEquals(4, trades.size)
    assertTrue(trades.all { it.symbol == "ETHUSDT" })
  }
}
