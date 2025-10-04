package com.kevin.cryptotrader.persistence.ledger

import com.kevin.cryptotrader.persistence.dao.LedgerEventDao
import com.kevin.cryptotrader.persistence.dao.PositionDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

class LedgerMigrations(
  private val ledgerEventDao: LedgerEventDao,
  private val positionDao: PositionDao,
  private val json: Json,
) {
  suspend fun backfillDerivedState() {
    withContext(Dispatchers.Default) {
      val events = ledgerEventDao.listAll()
      if (events.isEmpty()) {
        positionDao.clear()
        return@withContext
      }
      val projector = LedgerProjector()
      events
        .map { it.toLedgerEvent(json) }
        .forEach { projector.apply(it) }

      positionDao.clear()
      positionDao.upsertAll(projector.snapshot())
    }
  }
}
