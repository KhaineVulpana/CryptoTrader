package com.kevin.cryptotrader.data.marketdata

import com.kevin.cryptotrader.contracts.Candle
import com.kevin.cryptotrader.contracts.Interval
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Lightweight in-memory cache that emulates the behaviour of the Room-backed cache that
 * the production app will use. The Room schema is intentionally mirrored so the cache can
 * be swapped out with the generated DAO without touching the adapters.
 */
class OhlcvCache {
  private data class CacheKey(val symbol: String, val interval: Interval)

  private val mutex = Mutex()
  private val storage = mutableMapOf<CacheKey, MutableList<Candle>>()

  suspend fun upsert(candles: Collection<Candle>) {
    if (candles.isEmpty()) return
    mutex.withLock {
      candles.groupBy { CacheKey(it.symbol, it.interval) }.forEach { (key, grouped) ->
        val existing = storage.getOrPut(key) { mutableListOf() }
        val deduped = (existing + grouped)
          .distinctBy { it.ts }
          .sortedBy { it.ts }
        storage[key] = deduped.toMutableList()
      }
    }
  }

  suspend fun query(
    symbol: String,
    interval: Interval,
    start: Long? = null,
    end: Long? = null,
    limit: Int? = null,
  ): List<Candle> {
    return mutex.withLock {
      val key = CacheKey(symbol, interval)
      val data = storage[key]?.asSequence() ?: emptySequence()
      val filtered = data
        .filter { start == null || it.ts >= start }
        .filter { end == null || it.ts <= end }
        .toList()
      if (limit != null && filtered.size > limit) {
        filtered.takeLast(limit)
      } else {
        filtered
      }
    }
  }

  suspend fun last(symbol: String, interval: Interval): Candle? = mutex.withLock {
    storage[CacheKey(symbol, interval)]?.lastOrNull()
  }
}
