package com.kevin.cryptotrader.core.indicators

data class Macd(val macd: Double, val signal: Double?, val hist: Double?)
data class Bollinger(val middle: Double, val upper: Double, val lower: Double, val stddev: Double)
data class Donchian(val upper: Double, val lower: Double, val middle: Double)

class EmaIndicator(period: Int) {
  private val ema = Ema(period)
  fun update(x: Double): Double? = ema.update(x)
}

class RsiIndicator(private val period: Int) {
  private var prev: Double? = null
  private val avgGain = WilderSMA(period)
  private val avgLoss = WilderSMA(period)

  fun update(close: Double): Double? {
    val p = prev
    prev = close
    if (p == null) return null
    val change = close - p
    val gain = if (change > 0) change else 0.0
    val loss = if (change < 0) -change else 0.0
    val g = avgGain.update(gain)
    val l = avgLoss.update(loss)
    if (g == null || l == null) return null
    if (l == 0.0) return 100.0
    val rs = g / l
    return 100.0 - (100.0 / (1.0 + rs))
  }
}

class MacdIndicator(
  fast: Int = 12,
  slow: Int = 26,
  private val signalPeriod: Int = 9,
) {
  private val emaFast = Ema(fast)
  private val emaSlow = Ema(slow)
  private val signalEma = Ema(signalPeriod)

  fun update(close: Double): Macd? {
    val fastV = emaFast.update(close) ?: return null
    val slowV = emaSlow.update(close) ?: return null
    val macdVal = fastV - slowV
    val signalVal = signalEma.update(macdVal)
    val histVal = signalVal?.let { macdVal - it }
    return Macd(macdVal, signalVal, histVal)
  }
}

class BollingerBands(private val period: Int, private val k: Double = 2.0) {
  private val stats = RollingStatsWindow(period)

  fun update(x: Double): Bollinger? {
    stats.add(x)
    val mean = stats.mean() ?: return null
    val sd = stats.stddev() ?: return null
    return Bollinger(
      middle = mean,
      upper = mean + k * sd,
      lower = mean - k * sd,
      stddev = sd,
    )
  }
}

class DonchianChannel(private val period: Int) {
  private val maxHigh = RollingMinMaxWindow(period)
  private val minLow = RollingMinMaxWindow(period)

  fun update(high: Double, low: Double): Donchian? {
    maxHigh.add(high)
    minLow.add(low)
    val up = maxHigh.currentMax()
    val lo = minLow.currentMin()
    if (up == null || lo == null) return null
    val mid = (up + lo) / 2.0
    return Donchian(up, lo, mid)
  }
}

class AtrIndicator(private val period: Int = 14) {
  private var prevClose: Double? = null
  private val wilder = WilderSMA(period)

  fun update(high: Double, low: Double, close: Double): Double? {
    val tr = TrueRange.tr(high, low, prevClose)
    prevClose = close
    return wilder.update(tr)
  }
}

class ZScore(private val period: Int) {
  private val stats = RollingStatsWindow(period)

  fun update(x: Double): Double? {
    stats.add(x)
    val mean = stats.mean() ?: return null
    val sd = stats.stddev() ?: return null
    if (sd == 0.0) return 0.0
    return (x - mean) / sd
  }
}
