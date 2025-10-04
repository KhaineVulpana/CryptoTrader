package com.kevin.cryptotrader.persistence.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kevin.cryptotrader.persistence.entity.FillEntity

@Dao
interface FillDao {
  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insert(fill: FillEntity)

  @Query("SELECT * FROM fills WHERE orderId = :orderId ORDER BY ts")
  suspend fun forOrder(orderId: String): List<FillEntity>
}
