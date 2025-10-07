package com.kevin.cryptotrader.persistence

import androidx.room.Database
import androidx.room.RoomDatabase
import com.kevin.cryptotrader.contracts.LogLevel
import com.kevin.cryptotrader.contracts.TelemetryModule
import com.kevin.cryptotrader.core.telemetry.TelemetryCenter
import com.kevin.cryptotrader.persistence.dao.AutomationStateDao
import com.kevin.cryptotrader.persistence.dao.CandleDao
import com.kevin.cryptotrader.persistence.dao.FillDao
import com.kevin.cryptotrader.persistence.dao.IntentDao
import com.kevin.cryptotrader.persistence.dao.LedgerEventDao
import com.kevin.cryptotrader.persistence.dao.OrderDao
import com.kevin.cryptotrader.persistence.dao.PolicyDao
import com.kevin.cryptotrader.persistence.dao.PositionDao
import com.kevin.cryptotrader.persistence.entity.AutomationStateEntity
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
  ],
  version = 2,
  exportSchema = false,
)
abstract class TraderDatabase : RoomDatabase() {
  init {
    TelemetryCenter.logEvent(
      module = TelemetryModule.PERSISTENCE,
      level = LogLevel.INFO,
      message = "TraderDatabase schema registered",
      fields = mapOf(
        "version" to "2",
        "entities" to "8"
      )
    )
    TelemetryCenter.recordDataGap(
      module = TelemetryModule.PERSISTENCE,
      streamId = "room",
      gapSeconds = 0.0,
      fields = mapOf("status" to "initialized")
    )
  }

  abstract fun candleDao(): CandleDao

  abstract fun intentDao(): IntentDao

  abstract fun orderDao(): OrderDao

  abstract fun fillDao(): FillDao

  abstract fun positionDao(): PositionDao

  abstract fun policyDao(): PolicyDao

  abstract fun automationStateDao(): AutomationStateDao

  abstract fun ledgerEventDao(): LedgerEventDao
}
