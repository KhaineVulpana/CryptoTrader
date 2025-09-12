package com.kevin.cryptotrader.contracts

import kotlinx.coroutines.flow.Flow

interface MarketDataFeed {
  suspend fun fetchOhlcv(symbol: String, tf: Interval, start: Long? = null, end: Long? = null, limit: Int? = null): List<Candle>
  fun streamTicker(symbols: Set<String>): Flow<Ticker>
  fun streamTrades(symbols: Set<String>): Flow<Trade>
  fun streamBook(symbols: Set<String>): Flow<OrderBookDelta>
}
