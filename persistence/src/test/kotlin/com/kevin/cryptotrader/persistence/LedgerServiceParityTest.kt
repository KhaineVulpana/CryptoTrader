package com.kevin.cryptotrader.persistence

import com.kevin.cryptotrader.contracts.Interval
import com.kevin.cryptotrader.persistence.dao.LedgerEventDao
import com.kevin.cryptotrader.persistence.dao.PositionDao
import com.kevin.cryptotrader.persistence.entity.LedgerEventEntity
import com.kevin.cryptotrader.persistence.entity.PositionEntity
import com.kevin.cryptotrader.persistence.ledger.LedgerEvent
import com.kevin.cryptotrader.persistence.ledger.LedgerService
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class LedgerServiceParityTest {
  private val json = Json {
    encodeDefaults = true
    ignoreUnknownKeys = true
    classDiscriminator = "event"
  }

  @Test
  fun replayingLedgerBackfillsDerivedState() = runTest {
    val ledgerDao = InMemoryLedgerEventDao()
    val positionDao = InMemoryPositionDao()
    val service = LedgerService(
      ledgerEventDao = ledgerDao,
      positionDao = positionDao,
      json = json,
    )
    service.initialize()

    val baseTs = 1_000L
    service.append(
      LedgerEvent.CandleLogged(
        ts = baseTs,
        symbol = "BTCUSDT",
        interval = Interval.H1,
        open = 30000.0,
        high = 30500.0,
        low = 29500.0,
        close = 30000.0,
        volume = 100.0,
        source = "binance",
      ),
    )
    service.append(
      LedgerEvent.OrderPlaced(
        ts = baseTs + 1,
        orderId = "order-1",
        accountId = "acct-1",
        symbol = "BTCUSDT",
        side = "BUY",
        typeName = "MARKET",
        qty = 2.0,
        price = null,
        stopPrice = null,
        tif = "GTC",
        status = "FILLED",
      ),
    )
    service.append(
      LedgerEvent.FillRecorded(
        ts = baseTs + 2,
        orderId = "order-1",
        accountId = "acct-1",
        symbol = "BTCUSDT",
        side = "BUY",
        qty = 2.0,
        price = 30000.0,
      ),
    )
    service.append(
      LedgerEvent.CandleLogged(
        ts = baseTs + 3,
        symbol = "BTCUSDT",
        interval = Interval.H1,
        open = 30000.0,
        high = 32500.0,
        low = 29900.0,
        close = 32000.0,
        volume = 85.0,
        source = "binance",
      ),
    )
    service.append(
      LedgerEvent.OrderPlaced(
        ts = baseTs + 4,
        orderId = "order-2",
        accountId = "acct-1",
        symbol = "BTCUSDT",
        side = "SELL",
        typeName = "LIMIT",
        qty = 0.5,
        price = 35000.0,
        stopPrice = null,
        tif = "GTC",
        status = "FILLED",
      ),
    )
    service.append(
      LedgerEvent.FillRecorded(
        ts = baseTs + 5,
        orderId = "order-2",
        accountId = "acct-1",
        symbol = "BTCUSDT",
        side = "SELL",
        qty = 0.5,
        price = 35000.0,
      ),
    )
    service.append(
      LedgerEvent.CandleLogged(
        ts = baseTs + 6,
        symbol = "BTCUSDT",
        interval = Interval.H1,
        open = 32000.0,
        high = 34000.0,
        low = 31500.0,
        close = 33000.0,
        volume = 70.0,
        source = "binance",
      ),
    )
    service.append(
      LedgerEvent.OrderPlaced(
        ts = baseTs + 7,
        orderId = "order-3",
        accountId = "acct-1",
        symbol = "BTCUSDT",
        side = "SELL",
        typeName = "LIMIT",
        qty = 1.5,
        price = 29000.0,
        stopPrice = null,
        tif = "GTC",
        status = "FILLED",
      ),
    )
    service.append(
      LedgerEvent.FillRecorded(
        ts = baseTs + 8,
        orderId = "order-3",
        accountId = "acct-1",
        symbol = "BTCUSDT",
        side = "SELL",
        qty = 1.5,
        price = 29000.0,
      ),
    )
    service.append(
      LedgerEvent.CandleLogged(
        ts = baseTs + 9,
        symbol = "BTCUSDT",
        interval = Interval.H1,
        open = 30000.0,
        high = 30200.0,
        low = 28000.0,
        close = 28000.0,
        volume = 60.0,
        source = "binance",
      ),
    )
    service.append(
      LedgerEvent.OrderPlaced(
        ts = baseTs + 10,
        orderId = "order-4",
        accountId = "acct-1",
        symbol = "BTCUSDT",
        side = "SELL",
        typeName = "MARKET",
        qty = 1.0,
        price = 27000.0,
        stopPrice = null,
        tif = "GTC",
        status = "FILLED",
      ),
    )
    service.append(
      LedgerEvent.FillRecorded(
        ts = baseTs + 11,
        orderId = "order-4",
        accountId = "acct-1",
        symbol = "BTCUSDT",
        side = "SELL",
        qty = 1.0,
        price = 27000.0,
      ),
    )
    service.append(
      LedgerEvent.CandleLogged(
        ts = baseTs + 12,
        symbol = "BTCUSDT",
        interval = Interval.H1,
        open = 27500.0,
        high = 28000.0,
        low = 24000.0,
        close = 25000.0,
        volume = 55.0,
        source = "binance",
      ),
    )
    service.append(
      LedgerEvent.OrderPlaced(
        ts = baseTs + 13,
        orderId = "order-5",
        accountId = "acct-1",
        symbol = "BTCUSDT",
        side = "BUY",
        typeName = "LIMIT",
        qty = 0.4,
        price = 20000.0,
        stopPrice = null,
        tif = "GTC",
        status = "FILLED",
      ),
    )
    service.append(
      LedgerEvent.FillRecorded(
        ts = baseTs + 14,
        orderId = "order-5",
        accountId = "acct-1",
        symbol = "BTCUSDT",
        side = "BUY",
        qty = 0.4,
        price = 20000.0,
      ),
    )
    service.append(
      LedgerEvent.CandleLogged(
        ts = baseTs + 15,
        symbol = "BTCUSDT",
        interval = Interval.H1,
        open = 24000.0,
        high = 27000.0,
        low = 24000.0,
        close = 26000.0,
        volume = 48.0,
        source = "binance",
      ),
    )

    val incrementalSnapshot = service.materialize()

    val replayPositionDao = InMemoryPositionDao()
    val replayService = LedgerService(
      ledgerEventDao = ledgerDao,
      positionDao = replayPositionDao,
      json = json,
    )
    replayService.initialize()
    val replaySnapshot = replayService.materialize()

    assertEquals(incrementalSnapshot.positions, replaySnapshot.positions)
    assertEquals(incrementalSnapshot.totalRealizedPnl, replaySnapshot.totalRealizedPnl, 1e-6)
    assertEquals(incrementalSnapshot.totalUnrealizedPnl, replaySnapshot.totalUnrealizedPnl, 1e-6)

    val position = replaySnapshot.positions.single()
    assertEquals("acct-1", position.accountId)
    assertEquals("BTCUSDT", position.symbol)
    assertEquals(-0.6, position.qty, 1e-6)
    assertEquals(27000.0, position.avgPrice, 1e-6)
    assertEquals(3800.0, position.realizedPnl, 1e-6)
    assertEquals(600.0, position.unrealizedPnl, 1e-6)
  }
}

private class InMemoryLedgerEventDao : LedgerEventDao {
  private val mutex = Mutex()
  private val events = mutableListOf<LedgerEventEntity>()
  private var nextId = 1L

  override suspend fun insert(event: LedgerEventEntity): Long =
    mutex.withLock {
      val stored = event.copy(sequence = nextId++)
      events += stored
      stored.sequence
    }

  override suspend fun listFrom(fromTs: Long): List<LedgerEventEntity> =
    mutex.withLock { events.filter { it.ts >= fromTs }.sortedBy { it.sequence } }

  override suspend fun listAll(): List<LedgerEventEntity> = mutex.withLock { events.sortedBy { it.sequence } }

  override suspend fun clear() {
    mutex.withLock {
      events.clear()
      nextId = 1L
    }
  }
}

private class InMemoryPositionDao : PositionDao {
  private val mutex = Mutex()
  private val positions = linkedMapOf<Pair<String, String>, PositionEntity>()

  override suspend fun upsert(position: PositionEntity) {
    mutex.withLock { positions[position.accountId to position.symbol] = position }
  }

  override suspend fun upsertAll(positions: List<PositionEntity>) {
    mutex.withLock {
      positions.forEach { entity ->
        this.positions[entity.accountId to entity.symbol] = entity
      }
    }
  }

  override suspend fun listAll(): List<PositionEntity> = mutex.withLock { positions.values.map { it.copy() } }

  override suspend fun clear() {
    mutex.withLock { positions.clear() }
  }
}
