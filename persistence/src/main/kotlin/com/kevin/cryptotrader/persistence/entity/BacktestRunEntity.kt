package com.kevin.cryptotrader.persistence.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "backtest_runs")
data class BacktestRunEntity(
  @PrimaryKey val id: String,
  val simulationId: String,
  val splitId: String,
  val label: String,
  val inSampleStart: Long,
  val inSampleEnd: Long,
  val outSampleStart: Long,
  val outSampleEnd: Long,
  val createdAt: Long,
  val metricsJson: String,
  val equityJson: String,
  val tradesJson: String,
) {
  companion object {
    const val AGGREGATED_ID = "__aggregate__"
  }
}
