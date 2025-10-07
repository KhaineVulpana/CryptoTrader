package com.kevin.cryptotrader.core.indicators

import java.util.ArrayDeque
import kotlin.math.max
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

/** Generic fixed-size double window retaining insertion order. */
class RollingWindow(private val period: Int) {
  private val buffer = ArrayDeque<Double>(period)

  val size: Int get() = buffer.size

  fun add(value: Double): Double? {
    buffer.addLast(value)
    return if (buffer.size > period) buffer.removeFirst() else null
  }

  fun isFull(): Boolean = buffer.size == period

  fun values(): List<Double> = buffer.toList()

  fun peekOldest(): Double? = buffer.firstOrNull()

  fun peekNewest(): Double? = buffer.lastOrNull()
}

/** Rolling sum window with O(1) updates. */
class RollingSumWindow(private val period: Int) {
  private val buffer = ArrayDeque<Double>(period)
  private var sum = 0.0

  val size: Int get() = buffer.size

  fun add(value: Double): Double? {
    buffer.addLast(value)
    sum += value
    return if (buffer.size > period) {
      val removed = buffer.removeFirst()
      sum -= removed
      removed
    } else {
      null
    }
  }

  fun isFull(): Boolean = buffer.size == period

  fun currentSum(): Double? = if (isFull()) sum else null

  fun peekOldest(): Double? = buffer.firstOrNull()
}

/**
 * Linear weighted moving average where newest sample has weight [period].
 * Maintains sum and weighted sum for O(1) updates.
 */
class RollingLinearWeightedWindow(private val period: Int) {
  private val buffer = ArrayDeque<Double>(period)
  private var sum = 0.0
  private var weightedSum = 0.0
  private val divisor = period * (period + 1) / 2.0

  val size: Int get() = buffer.size

  fun add(value: Double) {
    val previousSum = sum
    val previousWeighted = weightedSum
    buffer.addLast(value)
    sum += value
    if (buffer.size <= period) {
      weightedSum = previousWeighted + value * buffer.size
      return
    }
    val removed = buffer.removeFirst()
    sum -= removed
    weightedSum = previousWeighted - previousSum + value * period
  }

  fun isFull(): Boolean = buffer.size == period

  fun current(): Double? = if (isFull()) weightedSum / divisor else null
}

/**
 * Monotonic queues to track rolling min and max in O(1) per update.
 */
class RollingMinMaxWindow(private val period: Int) {
  private data class Entry(val value: Double, val index: Int)

  private val minQ = ArrayDeque<Entry>()
  private val maxQ = ArrayDeque<Entry>()
  private var indexCounter = -1

  fun add(value: Double) {
    indexCounter += 1
    val expiry = indexCounter - period + 1

    // Max queue (decreasing)
    while (maxQ.isNotEmpty() && maxQ.last().value < value) maxQ.removeLast()
    maxQ.addLast(Entry(value, indexCounter))
    while (maxQ.isNotEmpty() && maxQ.first().index < expiry) maxQ.removeFirst()

    // Min queue (increasing)
    while (minQ.isNotEmpty() && minQ.last().value > value) minQ.removeLast()
    minQ.addLast(Entry(value, indexCounter))
    while (minQ.isNotEmpty() && minQ.first().index < expiry) minQ.removeFirst()
  }

  fun isFull(): Boolean = indexCounter + 1 >= period

  fun currentMin(): Double? = if (isFull()) minQ.first().value else null

  fun currentMax(): Double? = if (isFull()) maxQ.first().value else null
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

class EwmAverage(private val alpha: Double, private val warmup: Int) {
  private var count = 0
  private var value: Double? = null

  fun update(next: Double): Double? {
    val prev = value
    value = if (prev == null) {
      next
    } else {
      (next - prev) * alpha + prev
    }
    count += 1
    return if (count >= warmup) value else null
  }

  fun current(): Double? = value
}

/**
 * Standard EMA with initialization from SMA(period) once enough samples seen.
 */
class Ema(private val period: Int) {
  private val alpha = 2.0 / (period + 1.0)
  private var count = 0
  private var ema: Double? = null

  fun update(price: Double): Double? {
    val prev = ema
    ema = if (prev == null) {
      price
    } else {
      (price - prev) * alpha + prev
    }
    count += 1
    return if (count >= period) ema else null
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

