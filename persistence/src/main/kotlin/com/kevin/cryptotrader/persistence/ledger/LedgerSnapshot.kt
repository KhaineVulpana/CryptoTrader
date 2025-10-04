package com.kevin.cryptotrader.persistence.ledger

import com.kevin.cryptotrader.contracts.Position

data class LedgerSnapshot(
  val positions: List<Position>,
  val totalRealizedPnl: Double,
  val totalUnrealizedPnl: Double,
)
