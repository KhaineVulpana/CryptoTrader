package com.kevin.cryptotrader.core.indicators

import java.util.ArrayDeque
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * O(1) rolling statistics over a fixed-size window for Double values.
 * Maintains sum and sum of squares to compute mean and population standard deviation.
 */
class RollingStatsWindow(private val period: Int) {
  private val buffer = ArrayDeque<Double>(period)
  private var sum = 0.0
  private var sumSq = 0.0

  val size: Int get() = buffer.size

  fun add(value: Double) {
    buffer.addLast(value)
    sum += value
    sumSq += value * value
    if (buffer.size > period) {
      val old = buffer.removeFirst()
      sum -= old
      sumSq -= old * old
    }
  }

  fun isFull(): Boolean = buffer.size == period

  fun mean(): Double? = if (isFull()) sum / period else null

  fun variance(): Double? = if (isFull()) {
    val mu = sum / period
    // population variance
    (sumSq / period) - mu * mu
  } else null

  fun stddev(): Double? = variance()?.let { v -> if (v <= 0.0) 0.0 else sqrt(v) }
}

/**
 * Monotonic queues to track rolling min and max in O(1) per update.
 */
private data class Entry(val value: Double, val index: Int)

class RollingMaxWindow(private val period: Int) {
  private val q = ArrayDeque<Entry>()
  private var indexCounter = -1

  fun add(value: Double) {
    indexCounter += 1
    val expiry = indexCounter - period + 1
    while (q.isNotEmpty() && q.last().value < value) q.removeLast()
    q.addLast(Entry(value, indexCounter))
    while (q.isNotEmpty() && q.first().index < expiry) q.removeFirst()
  }

  fun isFull(): Boolean = indexCounter + 1 >= period
  fun currentMax(): Double? = if (isFull()) q.first().value else null
}

class RollingMinWindow(private val period: Int) {
  private val q = ArrayDeque<Entry>()
  private var indexCounter = -1

  fun add(value: Double) {
    indexCounter += 1
    val expiry = indexCounter - period + 1
    while (q.isNotEmpty() && q.last().value > value) q.removeLast()
    q.addLast(Entry(value, indexCounter))
    while (q.isNotEmpty() && q.first().index < expiry) q.removeFirst()
  }

  fun isFull(): Boolean = indexCounter + 1 >= period
  fun currentMin(): Double? = if (isFull()) q.first().value else null
}

/**
 * Wilder-style smoothed moving average used for RSI and ATR.
 * Initializes with simple average of the first [period] values.
 */
class WilderSMA(private val period: Int) {
  private var count = 0
  private var value: Double? = null
  private var sum = 0.0

  fun update(next: Double): Double? {
    if (count < period) {
      sum += next
      count += 1
      if (count == period) {
        value = sum / period
      }
      return value
    }
    val prev = value ?: return null
    val newVal = (prev * (period - 1) + next) / period
    value = newVal
    return newVal
  }

  fun current(): Double? = value
}

/**
 * Standard EMA with initialization from SMA(period) once enough samples seen.
 */
class Ema(private val period: Int) {
  private val alpha = 2.0 / (period + 1.0)
  private var count = 0
  private var seedSum = 0.0
  private var ema: Double? = null

  fun update(price: Double): Double? {
    if (count < period) {
      seedSum += price
      count += 1
      if (count == period) {
        ema = seedSum / period
      }
      return ema
    }
    val prev = ema ?: return null
    val next = (price - prev) * alpha + prev
    ema = next
    return next
  }

  fun current(): Double? = ema
}

/** Utility functions to compute true range and helpers. */
object TrueRange {
  fun tr(high: Double, low: Double, prevClose: Double?): Double {
    return if (prevClose == null) {
      high - low
    } else {
      max(high - low, max(kotlin.math.abs(high - prevClose), kotlin.math.abs(low - prevClose)))
    }
  }
}
