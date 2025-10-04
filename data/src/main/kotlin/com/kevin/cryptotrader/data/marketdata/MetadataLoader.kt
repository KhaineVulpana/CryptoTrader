package com.kevin.cryptotrader.data.marketdata

import com.kevin.cryptotrader.contracts.Interval
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.file.Path

@Serializable
private data class CoinGeckoSnapshot(
  val assets: List<CoinGeckoAsset>,
)

@Serializable
private data class CoinGeckoAsset(
  val id: String,
  val symbol: String,
  val name: String,
  @SerialName("tick_size") val tickSize: Double,
  @SerialName("lot_size") val lotSize: Double,
  val venues: List<String>,
  val timeframes: List<String>,
)

@Serializable
private data class DexAggregatorConfig(
  val aggregators: List<DexAggregator>,
)

@Serializable
data class DexAggregator(
  val id: String,
  val name: String,
  val supportsLimitOrders: Boolean = false,
)

data class AssetMetadata(
  val id: String,
  val symbol: String,
  val name: String,
  val tickSize: Double,
  val lotSize: Double,
  val venues: Set<String>,
  val timeframes: Set<Interval>,
)

class CoinGeckoMetadataLoader(
  private val snapshotPath: Path,
  private val json: Json = Json { ignoreUnknownKeys = true },
) {
  fun load(): List<AssetMetadata> {
    val snapshot = json.decodeFromString<CoinGeckoSnapshot>(snapshotPath.toFile().readText())
    return snapshot.assets.map { asset ->
      AssetMetadata(
        id = asset.id,
        symbol = normalizeSymbol(asset.symbol),
        name = asset.name,
        tickSize = asset.tickSize,
        lotSize = asset.lotSize,
        venues = asset.venues.map { it.lowercase() }.toSet(),
        timeframes = asset.timeframes.mapNotNull { it.toInterval() }.toSet(),
      )
    }
  }
}

class DexAggregatorMetadataLoader(
  private val configPath: Path?,
  private val json: Json = Json { ignoreUnknownKeys = true },
) {
  fun load(): List<DexAggregator> {
    val path = configPath ?: return emptyList()
    if (!path.toFile().exists()) return emptyList()
    val config = json.decodeFromString<DexAggregatorConfig>(path.toFile().readText())
    return config.aggregators
  }
}

private fun String.toInterval(): Interval? = when (lowercase()) {
  "1m" -> Interval.M1
  "5m" -> Interval.M5
  "15m" -> Interval.M15
  "30m" -> Interval.M30
  "1h" -> Interval.H1
  "4h" -> Interval.H4
  "1d" -> Interval.D1
  else -> null
}
