package com.kevin.cryptotrader.persistence.entity

import androidx.room.Entity
import androidx.room.Index
import com.kevin.cryptotrader.contracts.Candle
import com.kevin.cryptotrader.contracts.Interval

@Entity(
  tableName = "candles",
  primaryKeys = ["symbol", "interval", "ts", "source"],
  indices = [Index(value = ["symbol", "interval", "ts"])],
)
data class CandleEntity(
  val symbol: String,
  val interval: String,
  val ts: Long,
  val open: Double,
  val high: Double,
  val low: Double,
  val close: Double,
  val volume: Double,
  val source: String,
) {
  companion object {
    fun from(
      symbol: String,
      interval: Interval,
      ts: Long,
      open: Double,
      high: Double,
      low: Double,
      close: Double,
      volume: Double,
      source: String,
    ): CandleEntity =
      CandleEntity(
        symbol = symbol,
        interval = interval.name,
        ts = ts,
        open = open,
        high = high,
        low = low,
        close = close,
        volume = volume,
        source = source,
      )
  }

  fun toContract(): Candle =
    Candle(
      ts = ts,
      open = open,
      high = high,
      low = low,
      close = close,
      volume = volume,
      interval = Interval.valueOf(interval),
      symbol = symbol,
      source = source,
    )
}
