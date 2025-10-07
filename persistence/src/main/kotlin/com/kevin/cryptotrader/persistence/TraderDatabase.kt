package com.kevin.cryptotrader.persistence

import androidx.room.Database
import androidx.room.RoomDatabase
import com.kevin.cryptotrader.persistence.dao.AutomationStateDao
import com.kevin.cryptotrader.persistence.dao.BacktestRunDao
import com.kevin.cryptotrader.persistence.dao.CandleDao
import com.kevin.cryptotrader.persistence.dao.FillDao
import com.kevin.cryptotrader.persistence.dao.IntentDao
import com.kevin.cryptotrader.persistence.dao.LedgerEventDao
import com.kevin.cryptotrader.persistence.dao.OrderDao
import com.kevin.cryptotrader.persistence.dao.PolicyDao
import com.kevin.cryptotrader.persistence.dao.PositionDao
import com.kevin.cryptotrader.persistence.entity.AutomationStateEntity
import com.kevin.cryptotrader.persistence.entity.BacktestRunEntity
import com.kevin.cryptotrader.persistence.entity.CandleEntity
import com.kevin.cryptotrader.persistence.entity.FillEntity
import com.kevin.cryptotrader.persistence.entity.IntentEntity
import com.kevin.cryptotrader.persistence.entity.LedgerEventEntity
import com.kevin.cryptotrader.persistence.entity.OrderEntity
import com.kevin.cryptotrader.persistence.entity.PolicyEntity
import com.kevin.cryptotrader.persistence.entity.PositionEntity

@Database(
  entities = [
    CandleEntity::class,
    IntentEntity::class,
    OrderEntity::class,
    FillEntity::class,
    PositionEntity::class,
    PolicyEntity::class,
    AutomationStateEntity::class,
    LedgerEventEntity::class,
    BacktestRunEntity::class,
  ],
  version = 3,
  exportSchema = false,
)
abstract class TraderDatabase : RoomDatabase() {
  abstract fun candleDao(): CandleDao

  abstract fun intentDao(): IntentDao

  abstract fun orderDao(): OrderDao

  abstract fun fillDao(): FillDao

  abstract fun positionDao(): PositionDao

  abstract fun policyDao(): PolicyDao

  abstract fun automationStateDao(): AutomationStateDao

  abstract fun ledgerEventDao(): LedgerEventDao

  abstract fun backtestRunDao(): BacktestRunDao
}
