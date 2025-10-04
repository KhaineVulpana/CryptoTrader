package com.kevin.cryptotrader.persistence.ledger

import com.kevin.cryptotrader.persistence.entity.PositionEntity
import kotlin.math.abs
import kotlin.math.min

internal class LedgerProjector {
  private val positions = mutableMapOf<PositionKey, PositionAccumulator>()

  fun seed(existing: List<PositionEntity>) {
    existing.forEach { entity ->
      val key = PositionKey(entity.accountId, entity.symbol)
      positions[key] =
        PositionAccumulator(
          accountId = entity.accountId,
          symbol = entity.symbol,
          qty = entity.qty,
          avgPrice = entity.avgPrice,
          realizedPnl = entity.realizedPnl,
          lastPrice = entity.lastPrice,
        )
    }
  }

  fun apply(event: LedgerEvent): List<PositionEntity> =
    when (event) {
      is LedgerEvent.FillRecorded -> listOfNotNull(applyFill(event))
      is LedgerEvent.CandleLogged -> applyCandle(event)
      else -> emptyList()
    }

  fun snapshot(): List<PositionEntity> = positions.values.map { it.toEntity() }

  private fun applyFill(event: LedgerEvent.FillRecorded): PositionEntity? {
    val key = PositionKey(event.accountId, event.symbol)
    val accumulator = positions.getOrPut(key) {
      PositionAccumulator(accountId = event.accountId, symbol = event.symbol)
    }
    accumulator.applyFill(event)
    return accumulator.toEntity()
  }

  private fun applyCandle(event: LedgerEvent.CandleLogged): List<PositionEntity> {
    val updated = mutableListOf<PositionEntity>()
    positions.values
      .filter { it.symbol == event.symbol }
      .forEach { accumulator ->
        accumulator.lastPrice = event.close
        updated += accumulator.toEntity()
      }
    return updated
  }

  private data class PositionKey(
    val accountId: String,
    val symbol: String,
  )

  private class PositionAccumulator(
    val accountId: String,
    val symbol: String,
    var qty: Double = 0.0,
    var avgPrice: Double = 0.0,
    var realizedPnl: Double = 0.0,
    var lastPrice: Double? = null,
  ) {
    fun applyFill(event: LedgerEvent.FillRecorded) {
      val signedQty = if (event.side.uppercase() == "BUY") event.qty else -event.qty
      val previousQty = qty
      val previousAvg = avgPrice
      val newQty = previousQty + signedQty

      if (previousQty == 0.0 || previousQty.sign() == signedQty.sign()) {
        // Opening or adding to existing position
        qty = newQty
        avgPrice =
          if (qty == 0.0) {
            0.0
          } else {
            val totalCost = previousAvg * previousQty + event.price * signedQty
            totalCost / qty
          }
      } else {
        val closingQty = min(abs(signedQty), abs(previousQty))
        val pnlPerUnit =
          if (previousQty > 0) {
            event.price - previousAvg
          } else {
            previousAvg - event.price
          }
        realizedPnl += closingQty * pnlPerUnit

        qty = newQty
        avgPrice =
          when {
            qty == 0.0 -> 0.0
            previousQty.sign() != qty.sign() -> event.price
            else -> previousAvg
          }
      }

      lastPrice = event.price
    }

    fun toEntity(): PositionEntity {
      val unrealized =
        when {
          qty > 0 && lastPrice != null -> (lastPrice!! - avgPrice) * qty
          qty < 0 && lastPrice != null -> (avgPrice - lastPrice!!) * abs(qty)
          else -> 0.0
        }
      return PositionEntity(
        accountId = accountId,
        symbol = symbol,
        qty = qty,
        avgPrice = avgPrice,
        realizedPnl = realizedPnl,
        unrealizedPnl = unrealized,
        lastPrice = lastPrice,
      )
    }
  }
}

private fun Double.sign(): Int = when {
  this > 0 -> 1
  this < 0 -> -1
  else -> 0
}
