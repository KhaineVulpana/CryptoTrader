package com.kevin.cryptotrader.persistence.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kevin.cryptotrader.persistence.entity.BacktestRunEntity

@Dao
interface BacktestRunDao {
  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertAll(runs: List<BacktestRunEntity>)

  @Query("SELECT * FROM backtest_runs WHERE simulationId = :simulationId ORDER BY createdAt ASC")
  suspend fun runsForSimulation(simulationId: String): List<BacktestRunEntity>

  @Query("DELETE FROM backtest_runs WHERE simulationId = :simulationId")
  suspend fun deleteForSimulation(simulationId: String)
}
