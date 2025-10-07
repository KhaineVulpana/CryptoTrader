package com.kevin.cryptotrader.core.indicators

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.math.abs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.double

class IndicatorParityTest {
  private data class Sample(val close: Double, val high: Double, val low: Double)

  private val samples: List<Sample> = loadSamples()
  private val fixture: JsonObject = loadFixture()

  private fun loadSamples(): List<Sample> {
    val path = resolveFixture("fixtures", "ohlcv", "BTCUSDT_1h_sample.csv")
    return Files.newBufferedReader(path).useLines { lines ->
      lines.drop(1).map { line ->
        val cols = line.split(",")
        Sample(
          close = cols[4].toDouble(),
          high = cols[2].toDouble(),
          low = cols[3].toDouble(),
        )
      }.toList()
    }
  }

  private fun loadFixture(): JsonObject {
    val path = resolveFixture("fixtures", "indicators", "BTCUSDT_1h_ta_fixture.json")
    return Json.parseToJsonElement(Files.readString(path)).jsonObject
  }

  private fun resolveFixture(vararg segments: String): Path {
    val relative = Paths.get("", *segments)
    var current = Paths.get(System.getProperty("user.dir"))
    repeat(6) {
      val candidate = current.resolve(relative)
      if (Files.exists(candidate)) return candidate
      current = current.parent ?: return@repeat
    }
    throw IllegalStateException("Fixture ${relative} not found from ${System.getProperty("user.dir")}")
  }

  private fun expected(name: String): List<Double?> {
    val node: JsonElement = fixture[name] ?: error("Missing fixture $name")
    val arr: JsonArray = node.jsonArray
    return arr.map { element ->
      when (element) {
        JsonNull -> null
        else -> element.jsonPrimitive.double
      }
    }
  }

  private fun assertClose(expected: Double?, actual: Double?, label: String, index: Int, tolerance: Double = 1e-6) {
    if (expected == null) {
      assertNull(actual, "$label[$index] expected null but was $actual")
    } else {
      assertNotNull(actual, "$label[$index] expected $expected but was null")
      val delta = abs(expected - actual)
      assertTrue(delta <= tolerance, "$label[$index] expected $expected but was $actual (Δ=$delta)")
    }
  }

  @org.junit.jupiter.api.Test
  fun rollingStatsMatchFixture() {
    val meanExpected = expected("rolling_mean_20")
    val stdExpected = expected("rolling_std_20")
    val window = RollingStatsWindow(20)
    samples.forEachIndexed { index, sample ->
      window.add(sample.close)
      assertClose(meanExpected[index], window.mean(), "rolling_mean", index)
      assertClose(stdExpected[index], window.stddev(), "rolling_std", index)
    }
  }

  @org.junit.jupiter.api.Test
  fun rollingSumMatchesFixture() {
    val expected = expected("rolling_sum_14")
    val window = RollingSumWindow(14)
    samples.forEachIndexed { index, sample ->
      window.add(sample.close)
      assertClose(expected[index], window.currentSum(), "rolling_sum", index)
    }
  }

  @org.junit.jupiter.api.Test
  fun rollingMinMaxMatchesFixture() {
    val expectedMax = expected("rolling_max_20")
    val expectedMin = expected("rolling_min_20")
    val highWindow = RollingMinMaxWindow(20)
    val lowWindow = RollingMinMaxWindow(20)
    samples.forEachIndexed { index, sample ->
      highWindow.add(sample.high)
      lowWindow.add(sample.low)
      assertClose(expectedMax[index], highWindow.currentMax(), "rolling_max", index)
      assertClose(expectedMin[index], lowWindow.currentMin(), "rolling_min", index)
    }
  }

  @org.junit.jupiter.api.Test
  fun smaMatchesFixture() {
    val expected = expected("sma_14")
    val indicator = SmaIndicator(14)
    samples.forEachIndexed { index, sample ->
      val actual = indicator.update(sample.close)
      assertClose(expected[index], actual, "sma", index)
    }
  }

  @org.junit.jupiter.api.Test
  fun emaMatchesFixture() {
    val expected = expected("ema_14")
    val indicator = EmaIndicator(14)
    samples.forEachIndexed { index, sample ->
      val actual = indicator.update(sample.close)
      assertClose(expected[index], actual, "ema", index)
    }
  }

  @org.junit.jupiter.api.Test
  fun wmaMatchesFixture() {
    val expected = expected("wma_14")
    val indicator = WmaIndicator(14)
    samples.forEachIndexed { index, sample ->
      val actual = indicator.update(sample.close)
      assertClose(expected[index], actual, "wma", index)
    }
  }

  @org.junit.jupiter.api.Test
  fun stddevMatchesFixture() {
    val expected = expected("rolling_std_20")
    val indicator = StdDevIndicator(20)
    samples.forEachIndexed { index, sample ->
      val actual = indicator.update(sample.close)
      assertClose(expected[index], actual, "stddev", index)
    }
  }

  @org.junit.jupiter.api.Test
  fun rsiMatchesFixture() {
    val expected = expected("rsi_14")
    val indicator = RsiIndicator(14)
    samples.forEachIndexed { index, sample ->
      val actual = indicator.update(sample.close)
      assertClose(expected[index], actual, "rsi", index, tolerance = 1e-5)
    }
  }

  @org.junit.jupiter.api.Test
  fun rocMatchesFixture() {
    val expected = expected("roc_14")
    val indicator = RocIndicator(14)
    samples.forEachIndexed { index, sample ->
      val actual = indicator.update(sample.close)
      assertClose(expected[index], actual, "roc", index, tolerance = 1e-5)
    }
  }

  @org.junit.jupiter.api.Test
  fun macdMatchesFixture() {
    val macdExpected = expected("macd_line")
    val signalExpected = expected("macd_signal")
    val histExpected = expected("macd_hist")
    val indicator = MacdIndicator()
    samples.forEachIndexed { index, sample ->
      val actual = indicator.update(sample.close)
      if (macdExpected[index] == null) {
        kotlin.test.assertNull(actual, "macd[$index] expected null")
      } else {
        assertNotNull(actual, "macd[$index] expected value")
        assertClose(macdExpected[index], actual.macd, "macd_line", index, tolerance = 1e-6)
        assertClose(signalExpected[index], actual.signal, "macd_signal", index, tolerance = 1e-6)
        assertClose(histExpected[index], actual.hist, "macd_hist", index, tolerance = 1e-6)
      }
    }
  }

  @org.junit.jupiter.api.Test
  fun bollingerMatchesFixture() {
    val middleExpected = expected("bb_middle_20")
    val upperExpected = expected("bb_upper_20")
    val lowerExpected = expected("bb_lower_20")
    val stdExpected = expected("bb_std_20")
    val indicator = BollingerBands(20, 2.0)
    samples.forEachIndexed { index, sample ->
      val actual = indicator.update(sample.close)
      if (middleExpected[index] == null) {
        kotlin.test.assertNull(actual, "bb[$index] expected null")
      } else {
        assertNotNull(actual, "bb[$index] expected value")
        assertClose(middleExpected[index], actual.middle, "bb_middle", index)
        assertClose(upperExpected[index], actual.upper, "bb_upper", index)
        assertClose(lowerExpected[index], actual.lower, "bb_lower", index)
        assertClose(stdExpected[index], actual.stddev, "bb_std", index)
      }
    }
  }

  @org.junit.jupiter.api.Test
  fun donchianMatchesFixture() {
    val upperExpected = expected("donchian_upper_20")
    val lowerExpected = expected("donchian_lower_20")
    val middleExpected = expected("donchian_middle_20")
    val indicator = DonchianChannel(20)
    samples.forEachIndexed { index, sample ->
      val actual = indicator.update(sample.high, sample.low)
      if (upperExpected[index] == null) {
        kotlin.test.assertNull(actual, "donchian[$index] expected null")
      } else {
        assertNotNull(actual, "donchian[$index] expected value")
        assertClose(upperExpected[index], actual.upper, "donchian_upper", index)
        assertClose(lowerExpected[index], actual.lower, "donchian_lower", index)
        assertClose(middleExpected[index], actual.middle, "donchian_middle", index)
      }
    }
  }

  @org.junit.jupiter.api.Test
  fun atrMatchesFixture() {
    val expected = expected("atr_14")
    val indicator = AtrIndicator(14)
    samples.forEachIndexed { index, sample ->
      val actual = indicator.update(sample.high, sample.low, sample.close)
      assertClose(expected[index], actual, "atr", index)
    }
  }

  @org.junit.jupiter.api.Test
  fun zscoreMatchesFixture() {
    val expected = expected("zscore_20")
    val indicator = ZScore(20)
    samples.forEachIndexed { index, sample ->
      val actual = indicator.update(sample.close)
      val tol = if (expected[index] != null) 1e-5 else 1e-6
      assertClose(expected[index], actual, "zscore", index, tolerance = tol)
    }
  }

  @org.junit.jupiter.api.Test
  fun chandelierExitMatchesFixture() {
    val longExpected = expected("chandelier_long_22")
    val shortExpected = expected("chandelier_short_22")
    val indicator = ChandelierExitIndicator()
    samples.forEachIndexed { index, sample ->
      val actual = indicator.update(sample.high, sample.low, sample.close)
      if (longExpected[index] == null) {
        kotlin.test.assertNull(actual, "chandelier[$index] expected null")
      } else {
        assertNotNull(actual, "chandelier[$index] expected value")
        assertClose(longExpected[index], actual.long, "chandelier_long", index)
        assertClose(shortExpected[index], actual.short, "chandelier_short", index)
      }
    }
  }
}
