package com.kevin.cryptotrader.data.marketdata

import com.kevin.cryptotrader.contracts.Candle
import com.kevin.cryptotrader.contracts.Interval
import com.kevin.cryptotrader.contracts.MarketDataFeed
import com.kevin.cryptotrader.contracts.OrderBookDelta
import com.kevin.cryptotrader.contracts.Ticker
import com.kevin.cryptotrader.contracts.Trade
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.merge

abstract class BaseFixtureMarketDataAdapter(
  private val source: String,
  private val cache: OhlcvCache,
  private val provider: FixtureOhlcvProvider,
) : MarketDataFeed {

  override suspend fun fetchOhlcv(
    symbol: String,
    tf: Interval,
    start: Long?,
    end: Long?,
    limit: Int?,
  ): List<Candle> {
    val normalizedSymbol = normalizeSymbol(symbol)
    val cached = cache.query(normalizedSymbol, tf, start, end, limit)
    val needsBackfill = cached.isEmpty() ||
      (start != null && (cached.firstOrNull()?.ts ?: Long.MAX_VALUE) > start)
    if (!needsBackfill) {
      return cached
    }

    val candles = provider.load(normalizedSymbol, tf, source)
    cache.upsert(candles)
    return cache.query(normalizedSymbol, tf, start, end, limit)
  }

  override fun streamTicker(symbols: Set<String>): Flow<Ticker> {
    val flows = symbols.map { symbol ->
      val normalized = normalizeSymbol(symbol)
      channelFlow {
        val backfill = cache.query(normalized, defaultStreamInterval(), limit = backfillDepth())
        for (candle in backfill) {
          send(Ticker(ts = candle.ts, symbol = normalized, price = candle.close))
        }
        for (candle in provider.load(normalized, defaultStreamInterval(), source)) {
          cache.upsert(listOf(candle))
          send(Ticker(ts = candle.ts, symbol = normalized, price = candle.close))
        }
      }
    }
    if (flows.isEmpty()) return emptyFlow()
    return merge(*flows.toTypedArray())
  }

  override fun streamTrades(symbols: Set<String>): Flow<Trade> {
    val flows = symbols.map { symbol ->
      val normalized = normalizeSymbol(symbol)
      channelFlow {
        val backfill = cache.query(normalized, defaultStreamInterval(), limit = backfillDepth())
        for (candle in backfill) {
          send(
            Trade(
              ts = candle.ts,
              symbol = normalized,
              price = candle.close,
              qty = candle.volume,
              isBuy = true,
            ),
          )
        }
        for (candle in provider.load(normalized, defaultStreamInterval(), source)) {
          cache.upsert(listOf(candle))
          send(
            Trade(
              ts = candle.ts,
              symbol = normalized,
              price = candle.close,
              qty = candle.volume,
              isBuy = candle.close >= candle.open,
            ),
          )
        }
      }
    }
    if (flows.isEmpty()) return emptyFlow()
    return merge(*flows.toTypedArray())
  }

  override fun streamBook(symbols: Set<String>): Flow<OrderBookDelta> {
    val flows = symbols.map { symbol ->
      val normalized = normalizeSymbol(symbol)
      channelFlow {
        val backfill = cache.query(normalized, defaultStreamInterval(), limit = backfillDepth())
        for (candle in backfill) {
          send(candle.toBookDelta())
        }
        for (candle in provider.load(normalized, defaultStreamInterval(), source)) {
          cache.upsert(listOf(candle))
          send(candle.toBookDelta())
        }
      }
    }
    if (flows.isEmpty()) return emptyFlow()
    return merge(*flows.toTypedArray())
  }

  private fun Candle.toBookDelta(): OrderBookDelta {
    val mid = close
    val bid = (mid * 0.999).coerceAtLeast(low)
    val ask = (mid * 1.001).coerceAtMost(high)
    return OrderBookDelta(
      ts = ts,
      symbol = symbol,
      bids = listOf(bid to volume / 2),
      asks = listOf(ask to volume / 2),
    )
  }

  protected open fun defaultStreamInterval(): Interval = Interval.H1

  protected open fun backfillDepth(): Int = 20

  fun streamUserEvents(accountId: String, symbols: Set<String>): Flow<UserStreamEvent> = channelFlow {
    for (symbol in symbols) {
      val normalized = normalizeSymbol(symbol)
      val candles = provider.load(normalized, defaultStreamInterval(), source)
      if (candles.isEmpty()) continue
      val first = candles.first()
      send(
        UserStreamEvent.BalanceUpdate(
          venue = source,
          accountId = accountId,
          asset = normalized.takeLast(3),
          free = first.volume,
        ),
      )
      for (candle in candles) {
        send(
          UserStreamEvent.OrderFill(
            venue = source,
            accountId = accountId,
            symbol = normalized,
            price = candle.close,
            qty = candle.volume / 10,
          ),
        )
      }
    }
  }

  sealed interface UserStreamEvent {
    val venue: String
    val accountId: String

    data class BalanceUpdate(
      override val venue: String,
      override val accountId: String,
      val asset: String,
      val free: Double,
    ) : UserStreamEvent

    data class OrderFill(
      override val venue: String,
      override val accountId: String,
      val symbol: String,
      val price: Double,
      val qty: Double,
    ) : UserStreamEvent
  }
}
