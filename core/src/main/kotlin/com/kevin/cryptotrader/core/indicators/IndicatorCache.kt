package com.kevin.cryptotrader.core.indicators

import com.kevin.cryptotrader.contracts.Interval
import java.util.concurrent.ConcurrentHashMap

/**
 * Generic memoization for indicators/windows keyed by symbol, interval and params.
 */
class IndicatorStateCache {
  private data class Key(
    val symbol: String,
    val interval: Interval,
    val name: String,
    val params: List<Pair<String, Any?>>,
  )

  private val states = ConcurrentHashMap<Key, Any>()

  fun <T : Any> getOrCreate(
    symbol: String,
    interval: Interval,
    name: String,
    params: Map<String, Any?> = emptyMap(),
    factory: () -> T,
  ): T {
    val normalized = params.entries
      .sortedBy { it.key }
      .map { it.key to it.value }
    val key = Key(symbol, interval, name, normalized)
    @Suppress("UNCHECKED_CAST")
    return states.computeIfAbsent(key) { factory() } as T
  }

  fun clear() {
    states.clear()
  }
}
