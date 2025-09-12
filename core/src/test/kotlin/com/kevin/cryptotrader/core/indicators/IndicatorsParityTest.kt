package com.kevin.cryptotrader.core.indicators

import java.nio.file.Files
import java.nio.file.Paths
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

private data class Row(val ts: Long, val open: Double, val high: Double, val low: Double, val close: Double, val volume: Double)

class IndicatorsParityTest {
  private fun loadCsv(path: String): List<Row> {
    val p = resolvePath(path)
    val lines = Files.readAllLines(p).drop(1)
    return lines.map { line ->
      val parts = line.split(',')
      Row(
        ts = parts[0].toLong(),
        open = parts[1].toDouble(),
        high = parts[2].toDouble(),
        low = parts[3].toDouble(),
        close = parts[4].toDouble(),
        volume = parts[5].toDouble(),
      )
    }
  }

  private fun resolvePath(path: String): java.nio.file.Path {
    val candidates = listOf(
      Paths.get(path),
      Paths.get("../$path"),
      Paths.get("../../$path"),
      Paths.get("../../../$path"),
    )
    return candidates.firstOrNull { Files.exists(it) }
      ?: error("Fixture not found: $path")
  }

  private fun nearly(a: Double?, b: Double?, eps: Double = 1e-6): Boolean {
    if (a == null && b == null) return true
    if (a == null || b == null) return false
    return abs(a - b) <= eps
  }

  @Test
  fun ema_rsi_macd_bb_donchian_atr_zscore_parity() {
    val rows = loadCsv("fixtures/ohlcv/BTCUSDT_1h_sample.csv")
    // EMA
    val emaEngine = EmaIndicator(12)
    val emaNaive = Naive.ema(12)

    // RSI
    val rsiEngine = RsiIndicator(14)
    val rsiNaive = Naive.rsi(14)

    // MACD
    val macdEngine = MacdIndicator(12, 26, 9)
    val macdNaive = Naive.macd(12, 26, 9)

    // BB
    val bbEngine = BollingerBands(20, 2.0)
    val bbNaive = Naive.bb(20, 2.0)

    // Donchian
    val donEngine = DonchianChannel(20)
    val donNaive = Naive.donchian(20)

    // ATR
    val atrEngine = AtrIndicator(14)
    val atrNaive = Naive.atr(14)

    // Z-score
    val zEngine = ZScore(20)
    val zNaive = Naive.zscore(20)

    var agreements = 0
    var totalComparisons = 0

    rows.forEach { r ->
      val emaA = emaEngine.update(r.close)
      val emaB = emaNaive.update(r.close)
      if (emaA != null || emaB != null) {
        totalComparisons++
        assertTrue(nearly(emaA, emaB, 1e-6))
        agreements++
      }

      val rsiA = rsiEngine.update(r.close)
      val rsiB = rsiNaive.update(r.close)
      if (rsiA != null || rsiB != null) {
        totalComparisons++
        assertTrue(nearly(rsiA, rsiB, 1e-6))
        agreements++
      }

      val macdA = macdEngine.update(r.close)
      val macdB = macdNaive.update(r.close)
      if (macdA != null || macdB != null) {
        totalComparisons++
        assertTrue(nearly(macdA?.macd, macdB?.macd, 1e-6))
        assertTrue(nearly(macdA?.signal, macdB?.signal, 1e-6))
        assertTrue(nearly(macdA?.hist, macdB?.hist, 1e-6))
        agreements++
      }

      val bbA = bbEngine.update(r.close)
      val bbB = bbNaive.update(r.close)
      if (bbA != null || bbB != null) {
        totalComparisons++
        assertTrue(nearly(bbA?.middle, bbB?.middle, 1e-6))
        assertTrue(nearly(bbA?.upper, bbB?.upper, 1e-6))
        assertTrue(nearly(bbA?.lower, bbB?.lower, 1e-6))
        agreements++
      }

      val donA = donEngine.update(r.high, r.low)
      val donB = donNaive.update(r.high, r.low)
      if (donA != null || donB != null) {
        totalComparisons++
        assertTrue(nearly(donA?.upper, donB?.upper, 1e-6))
        assertTrue(nearly(donA?.lower, donB?.lower, 1e-6))
        assertTrue(nearly(donA?.middle, donB?.middle, 1e-6))
        agreements++
      }

      val atrA = atrEngine.update(r.high, r.low, r.close)
      val atrB = atrNaive.update(r.high, r.low, r.close)
      if (atrA != null || atrB != null) {
        totalComparisons++
        assertTrue(nearly(atrA, atrB, 1e-6))
        agreements++
      }

      val zA = zEngine.update(r.close)
      val zB = zNaive.update(r.close)
      if (zA != null || zB != null) {
        totalComparisons++
        assertTrue(nearly(zA, zB, 1e-6))
        agreements++
      }
    }

    // sanity: we performed a decent number of comparisons
    assertTrue(totalComparisons > 0 && agreements == totalComparisons)
  }
}

/**
 * Naive implementations for parity testing — straightforward loops and lists,
 * not sharing logic with O(1) engines above.
 */
private object Naive {
  fun ema(period: Int): ObjectDoubleUpdate {
    val alpha = 2.0 / (period + 1.0)
    val buf = ArrayList<Double>()
    var ema: Double? = null
    return ObjectDoubleUpdate { x ->
      if (buf.size < period) {
        buf.add(x)
        if (buf.size == period) {
          ema = buf.sum() / period
        }
        ema
      } else {
        val prev = ema!!
        val next = (x - prev) * alpha + prev
        ema = next
        next
      }
    }
  }

  fun rsi(period: Int): ObjectDoubleUpdate {
    var prev: Double? = null
    val gains = ArrayList<Double>()
    val losses = ArrayList<Double>()
    var avgGain: Double? = null
    var avgLoss: Double? = null
    return ObjectDoubleUpdate { close ->
      val p = prev
      prev = close
      if (p == null) return@ObjectDoubleUpdate null
      val change = close - p
      val gain = if (change > 0) change else 0.0
      val loss = if (change < 0) -change else 0.0
      if (avgGain == null) {
        gains.add(gain)
        losses.add(loss)
        if (gains.size == period) {
          avgGain = gains.sum() / period
          avgLoss = losses.sum() / period
        }
      } else {
        avgGain = ((avgGain!! * (period - 1)) + gain) / period
        avgLoss = ((avgLoss!! * (period - 1)) + loss) / period
      }

      val g = avgGain
      val l = avgLoss
      if (g == null || l == null) return@ObjectDoubleUpdate null
      if (l == 0.0) return@ObjectDoubleUpdate 100.0
      val rs = g / l
      100.0 - (100.0 / (1.0 + rs))
    }
  }

  fun macd(fast: Int, slow: Int, signal: Int): ObjectDoubleUpdateMacd {
    val emaFast = ema(fast)
    val emaSlow = ema(slow)
    val sig = ema(signal)
    return ObjectDoubleUpdateMacd { x ->
      val f = emaFast.update(x) ?: return@ObjectDoubleUpdateMacd null
      val s = emaSlow.update(x) ?: return@ObjectDoubleUpdateMacd null
      val macd = f - s
      val signalVal = sig.update(macd)
      val hist = signalVal?.let { macd - it }
      Macd(macd, signalVal, hist)
    }
  }

  fun bb(period: Int, k: Double): ObjectDoubleUpdateBoll {
    val buf = ArrayDeque<Double>()
    var sum = 0.0
    var sumSq = 0.0
    return ObjectDoubleUpdateBoll { x ->
      buf.addLast(x)
      sum += x
      sumSq += x * x
      if (buf.size > period) {
        val old = buf.removeFirst()
        sum -= old
        sumSq -= old * old
      }
      if (buf.size == period) {
        val mean = sum / period
        val varp = (sumSq / period) - mean * mean
        val sd = if (varp <= 0.0) 0.0 else kotlin.math.sqrt(varp)
        Bollinger(mean, mean + k * sd, mean - k * sd, sd)
      } else null
    }
  }

  fun donchian(period: Int): ObjectHLUpdateDonchian {
    val highs = ArrayDeque<Double>()
    val lows = ArrayDeque<Double>()
    return ObjectHLUpdateDonchian { high, low ->
      highs.addLast(high)
      lows.addLast(low)
      if (highs.size > period) highs.removeFirst()
      if (lows.size > period) lows.removeFirst()
      if (highs.size == period && lows.size == period) {
        val up = highs.maxOrNull()!!
        val lo = lows.minOrNull()!!
        val mid = (up + lo) / 2.0
        Donchian(up, lo, mid)
      } else null
    }
  }

  fun atr(period: Int): ObjectHLCUpdateDouble {
    var prevClose: Double? = null
    val trs = ArrayList<Double>()
    var atr: Double? = null
    return ObjectHLCUpdateDouble { h, l, c ->
      val tr = TrueRange.tr(h, l, prevClose)
      prevClose = c
      if (atr == null) {
        trs.add(tr)
        if (trs.size == period) {
          atr = trs.sum() / period
        }
      } else {
        atr = ((atr!! * (period - 1)) + tr) / period
      }
      atr
    }
  }

  fun zscore(period: Int): ObjectDoubleUpdate {
    val buf = ArrayDeque<Double>()
    var sum = 0.0
    var sumSq = 0.0
    return ObjectDoubleUpdate { x ->
      buf.addLast(x)
      sum += x
      sumSq += x * x
      if (buf.size > period) {
        val old = buf.removeFirst()
        sum -= old
        sumSq -= old * old
      }
      if (buf.size == period) {
        val mean = sum / period
        val varp = (sumSq / period) - mean * mean
        val sd = if (varp <= 0.0) 0.0 else kotlin.math.sqrt(varp)
        if (sd == 0.0) 0.0 else (x - mean) / sd
      } else null
    }
  }
}

private fun interface ObjectDoubleUpdate { fun update(x: Double): Double? }
private fun interface ObjectDoubleUpdateMacd { fun update(x: Double): Macd? }
private fun interface ObjectDoubleUpdateBoll { fun update(x: Double): Bollinger? }
private fun interface ObjectHLUpdateDonchian { fun update(high: Double, low: Double): Donchian? }
private fun interface ObjectHLCUpdateDouble { fun update(high: Double, low: Double, close: Double): Double? }
