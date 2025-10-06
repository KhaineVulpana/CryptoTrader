package com.kevin.cryptotrader.persistence.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.kevin.cryptotrader.contracts.Fill
import com.kevin.cryptotrader.contracts.Side

@Entity(
  tableName = "fills",
  indices = [Index(value = ["orderId", "accountId", "symbol"]), Index(value = ["ts"])],
)
data class FillEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val accountId: String,
  val orderId: String,
  val symbol: String,
  val side: String,
  val qty: Double,
  val price: Double,
  val ts: Long,
) {
  fun toContract(): Fill =
    Fill(
      orderId = orderId,
      qty = qty,
      price = price,
      ts = ts,
    )

  val tradeSide: Side
    get() = Side.valueOf(side)
}
