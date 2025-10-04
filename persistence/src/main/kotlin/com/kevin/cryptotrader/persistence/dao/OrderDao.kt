package com.kevin.cryptotrader.persistence.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kevin.cryptotrader.persistence.entity.OrderEntity

@Dao
interface OrderDao {
  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsert(order: OrderEntity)

  @Query("SELECT * FROM orders WHERE clientOrderId = :id")
  suspend fun findById(id: String): OrderEntity?

  @Query("UPDATE orders SET status = :status WHERE clientOrderId = :id")
  suspend fun updateStatus(id: String, status: String)
}
