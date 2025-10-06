package com.kevin.cryptotrader.data.marketdata

import com.kevin.cryptotrader.contracts.Candle
import com.kevin.cryptotrader.contracts.Interval
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile

internal fun normalizeSymbol(symbol: String): String = symbol
  .replace("-", "")
  .replace("/", "")
  .uppercase()

internal fun normalizeIntervalLabel(interval: Interval): String = when (interval) {
  Interval.M1 -> "1m"
  Interval.M5 -> "5m"
  Interval.M15 -> "15m"
  Interval.M30 -> "30m"
  Interval.H1 -> "1h"
  Interval.H4 -> "4h"
  Interval.D1 -> "1d"
}

fun resolveFixturesRoot(): Path {
  var current: Path? = Paths.get("").toAbsolutePath()
  repeat(6) {
    val candidate = current?.resolve("fixtures")
    if (candidate != null && candidate.exists()) return candidate
    current = current?.parent
  }
  error("Unable to locate fixtures directory")
}

class FixtureOhlcvProvider(private val fixtureRoot: Path = resolveFixturesRoot().resolve("ohlcv")) {
  fun load(symbol: String, interval: Interval, source: String): List<Candle> {
    val normalized = normalizeSymbol(symbol)
    val filename = "${normalized}_${normalizeIntervalLabel(interval)}_sample.csv"
    val path = fixtureRoot.resolve(filename)
    require(path.isRegularFile()) { "Missing OHLCV fixture $filename" }
    return Files.readAllLines(path)
      .drop(1)
      .filter { it.isNotBlank() }
      .map { line ->
        val parts = line.split(',')
        require(parts.size >= 6) { "Invalid candle line: $line" }
        Candle(
          ts = parts[0].trim().toLong(),
          open = parts[1].trim().toDouble(),
          high = parts[2].trim().toDouble(),
          low = parts[3].trim().toDouble(),
          close = parts[4].trim().toDouble(),
          volume = parts[5].trim().toDouble(),
          interval = interval,
          symbol = normalized,
          source = source,
        )
      }
  }
}
