package com.kevin.cryptotrader.persistence.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kevin.cryptotrader.persistence.entity.PolicyEntity

@Dao
interface PolicyDao {
  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsert(policy: PolicyEntity)

  @Query("SELECT * FROM policies WHERE policyId = :id")
  suspend fun findById(id: String): PolicyEntity?
}
