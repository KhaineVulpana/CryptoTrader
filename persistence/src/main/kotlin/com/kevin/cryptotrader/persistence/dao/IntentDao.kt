package com.kevin.cryptotrader.persistence.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kevin.cryptotrader.persistence.entity.IntentEntity

@Dao
interface IntentDao {
  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsert(intent: IntentEntity)

  @Query("SELECT * FROM intents WHERE id = :id")
  suspend fun findById(id: String): IntentEntity?

  @Query("SELECT * FROM intents ORDER BY ts DESC LIMIT :limit")
  suspend fun recent(limit: Int): List<IntentEntity>
}
