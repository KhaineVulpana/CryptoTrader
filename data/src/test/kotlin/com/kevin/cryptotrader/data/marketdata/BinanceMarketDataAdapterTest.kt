package com.kevin.cryptotrader.data.marketdata

import com.kevin.cryptotrader.contracts.Interval
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BinanceMarketDataAdapterTest {
  private val cache = OhlcvCache()
  private val provider = FixtureOhlcvProvider()
  private val adapter = BinanceMarketDataAdapter(cache, provider)

  @Test
  fun `fetchOhlcv seeds cache and respects limits`() = runBlocking {
    val candles = adapter.fetchOhlcv("btc/usdt", Interval.H1, limit = 3)
    assertEquals(3, candles.size)
    assertTrue(candles.zipWithNext().all { (a, b) -> a.ts < b.ts })

    val cached = adapter.fetchOhlcv("BTCUSDT", Interval.H1, limit = 3)
    assertEquals(candles, cached)
  }

  @Test
  fun `streaming tickers emit backfill followed by live updates`() = runBlocking {
    val events = adapter.streamTicker(setOf("BTCUSDT"))
      .take(5)
      .toList()
    assertEquals(5, events.size)
    assertTrue(events.zipWithNext().all { (a, b) -> a.ts <= b.ts })
  }

  @Test
  fun `user stream emits balance and fills`() = runBlocking {
    val events = adapter.streamUserEvents("acct-1", setOf("BTCUSDT"))
      .take(3)
      .toList()
    assertTrue(events.first() is BaseFixtureMarketDataAdapter.UserStreamEvent.BalanceUpdate)
    assertTrue(events.drop(1).all { it is BaseFixtureMarketDataAdapter.UserStreamEvent.OrderFill })
  }
}
