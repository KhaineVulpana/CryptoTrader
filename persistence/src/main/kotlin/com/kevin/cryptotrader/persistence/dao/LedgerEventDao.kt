package com.kevin.cryptotrader.persistence.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kevin.cryptotrader.persistence.entity.LedgerEventEntity

@Dao
interface LedgerEventDao {
  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insert(event: LedgerEventEntity): Long

  @Query("SELECT * FROM ledger_events WHERE ts >= :fromTs ORDER BY sequence ASC")
  suspend fun listFrom(fromTs: Long): List<LedgerEventEntity>

  @Query("SELECT * FROM ledger_events ORDER BY sequence ASC")
  suspend fun listAll(): List<LedgerEventEntity>

  @Query("DELETE FROM ledger_events")
  suspend fun clear()
}
