package com.kevin.cryptotrader.persistence.entity

import androidx.room.Entity
import androidx.room.Index
import com.kevin.cryptotrader.contracts.Position

@Entity(
  tableName = "positions",
  primaryKeys = ["accountId", "symbol"],
  indices = [Index(value = ["symbol"])],
)
data class PositionEntity(
  val accountId: String,
  val symbol: String,
  val qty: Double,
  val avgPrice: Double,
  val realizedPnl: Double,
  val unrealizedPnl: Double,
  val lastPrice: Double?,
) {
  fun toContract(): Position =
    Position(
      accountId = accountId,
      symbol = symbol,
      qty = qty,
      avgPrice = avgPrice,
      realizedPnl = realizedPnl,
      unrealizedPnl = unrealizedPnl,
    )
}
