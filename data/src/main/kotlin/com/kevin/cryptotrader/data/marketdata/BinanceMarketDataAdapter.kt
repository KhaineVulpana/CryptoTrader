package com.kevin.cryptotrader.data.marketdata

import com.kevin.cryptotrader.contracts.Interval

class BinanceMarketDataAdapter(
  cache: OhlcvCache,
  provider: FixtureOhlcvProvider = FixtureOhlcvProvider(),
) : BaseFixtureMarketDataAdapter(
  source = "binance",
  cache = cache,
  provider = provider,
) {
  override fun backfillDepth(): Int = 50

  override fun defaultStreamInterval(): Interval = Interval.H1
}
