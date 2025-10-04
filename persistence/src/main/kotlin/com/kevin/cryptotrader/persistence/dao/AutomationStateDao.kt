package com.kevin.cryptotrader.persistence.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kevin.cryptotrader.persistence.entity.AutomationStateEntity

@Dao
interface AutomationStateDao {
  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsert(state: AutomationStateEntity)

  @Query("SELECT * FROM automation_state WHERE automationId = :id")
  suspend fun findById(id: String): AutomationStateEntity?
}
