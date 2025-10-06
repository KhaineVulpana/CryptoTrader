package com.kevin.cryptotrader.data.marketdata

import kotlinx.serialization.json.Json
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MetadataLoaderTest {
  private val fixturesRoot: Path = resolveFixturesRoot().resolve("metadata")

  @Test
  fun `coin gecko loader normalizes symbols and intervals`() {
    val loader = CoinGeckoMetadataLoader(fixturesRoot.resolve("coingecko_snapshot.json"), Json)
    val assets = loader.load()
    val bitcoin = assets.first { it.symbol == "BTC" }
    assertEquals(setOf("binance", "coinbase"), bitcoin.venues)
    assertTrue(bitcoin.timeframes.containsAll(listOf(com.kevin.cryptotrader.contracts.Interval.H1, com.kevin.cryptotrader.contracts.Interval.D1)))
  }

  @Test
  fun `dex aggregator loader is optional`() {
    val loader = DexAggregatorMetadataLoader(fixturesRoot.resolve("dex_aggregators.json"), Json)
    val aggregators = loader.load()
    assertEquals(2, aggregators.size)

    val missingLoader = DexAggregatorMetadataLoader(fixturesRoot.resolve("missing.json"), Json)
    assertTrue(missingLoader.load().isEmpty())
  }
}
