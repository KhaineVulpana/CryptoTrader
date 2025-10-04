package com.kevin.cryptotrader.persistence.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.kevin.cryptotrader.contracts.Order
import com.kevin.cryptotrader.contracts.OrderType
import com.kevin.cryptotrader.contracts.Side
import com.kevin.cryptotrader.contracts.TIF

@Entity(
  tableName = "orders",
  indices = [Index(value = ["symbol", "accountId"])],
)
data class OrderEntity(
  @PrimaryKey val clientOrderId: String,
  val accountId: String,
  val symbol: String,
  val side: String,
  val type: String,
  val qty: Double,
  val price: Double?,
  val stopPrice: Double?,
  val tif: String,
  val status: String,
  val ts: Long,
) {
  companion object {
    const val STATUS_OPEN = "OPEN"
    const val STATUS_FILLED = "FILLED"
    const val STATUS_CANCELED = "CANCELED"

    fun from(order: Order, accountId: String, status: String = STATUS_OPEN): OrderEntity =
      OrderEntity(
        clientOrderId = order.clientOrderId,
        accountId = accountId,
        symbol = order.symbol,
        side = order.side.name,
        type = order.type.name,
        qty = order.qty,
        price = order.price,
        stopPrice = order.stopPrice,
        tif = order.tif.name,
        status = status,
        ts = order.ts,
      )
  }

  fun toContract(): Order =
    Order(
      clientOrderId = clientOrderId,
      symbol = symbol,
      side = Side.valueOf(side),
      type = OrderType.valueOf(type),
      qty = qty,
      price = price,
      stopPrice = stopPrice,
      tif = TIF.valueOf(tif),
      ts = ts,
    )
}
