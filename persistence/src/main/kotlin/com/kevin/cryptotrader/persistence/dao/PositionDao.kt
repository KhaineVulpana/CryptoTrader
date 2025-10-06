package com.kevin.cryptotrader.persistence.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kevin.cryptotrader.persistence.entity.PositionEntity

@Dao
interface PositionDao {
  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsert(position: PositionEntity)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertAll(positions: List<PositionEntity>)

  @Query("SELECT * FROM positions")
  suspend fun listAll(): List<PositionEntity>

  @Query("DELETE FROM positions")
  suspend fun clear()
}
