package com.kevin.cryptotrader.persistence.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kevin.cryptotrader.persistence.entity.CandleEntity

@Dao
interface CandleDao {
  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsert(candle: CandleEntity)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertAll(candles: List<CandleEntity>)

  @Query(
    "SELECT * FROM candles WHERE symbol = :symbol AND interval = :interval ORDER BY ts DESC LIMIT :limit",
  )
  suspend fun recent(symbol: String, interval: String, limit: Int): List<CandleEntity>
}
