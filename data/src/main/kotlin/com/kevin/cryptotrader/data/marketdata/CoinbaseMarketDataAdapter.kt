package com.kevin.cryptotrader.data.marketdata

import com.kevin.cryptotrader.contracts.Interval

class CoinbaseMarketDataAdapter(
  cache: OhlcvCache,
  provider: FixtureOhlcvProvider = FixtureOhlcvProvider(),
) : BaseFixtureMarketDataAdapter(
  source = "coinbase",
  cache = cache,
  provider = provider,
) {
  override fun backfillDepth(): Int = 30

  override fun defaultStreamInterval(): Interval = Interval.H1
}
