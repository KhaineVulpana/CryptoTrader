package com.kevin.cryptotrader.persistence.ledger

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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

class LedgerService(
  private val ledgerEventDao: LedgerEventDao,
  private val positionDao: PositionDao,
  private val candleDao: CandleDao? = null,
  private val intentDao: IntentDao? = null,
  private val orderDao: OrderDao? = null,
  private val fillDao: FillDao? = null,
  private val policyDao: PolicyDao? = null,
  private val automationStateDao: AutomationStateDao? = null,
  private val json: Json = Json {
    encodeDefaults = true
    ignoreUnknownKeys = true
    classDiscriminator = "event"
  },
) {
  private val migrations = LedgerMigrations(ledgerEventDao, positionDao, json)
  private val projector = LedgerProjector()
  private val events = MutableSharedFlow<LedgerEvent>(extraBufferCapacity = 64)
  private val initMutex = Mutex()
  private var initialized = false

  suspend fun initialize() {
    ensureInitialized()
  }

  suspend fun append(event: LedgerEvent) {
    ensureInitialized()
    ledgerEventDao.insert(LedgerEventEntity.from(event, json))
    persistEvent(event)
    val updatedPositions = projector.apply(event)
    if (updatedPositions.isNotEmpty()) {
      positionDao.upsertAll(updatedPositions)
    }
    events.emit(event)
  }

  fun stream(fromTs: Long = 0L): Flow<LedgerEvent> =
    channelFlow {
      ensureInitialized()
      ledgerEventDao.listFrom(fromTs).forEach { send(it.toLedgerEvent(json)) }
      val job = launch(Dispatchers.Default) {
        events.collect { event ->
          if (event.ts >= fromTs) {
            send(event)
          }
        }
      }
      awaitClose { job.cancel() }
    }

  suspend fun materialize(): LedgerSnapshot {
    ensureInitialized()
    val positions = positionDao.listAll().map(PositionEntity::toContract).sortedBy { it.symbol }
    val realized = positions.sumOf { it.realizedPnl }
    val unrealized = positions.sumOf { it.unrealizedPnl }
    return LedgerSnapshot(positions, realized, unrealized)
  }

  private suspend fun ensureInitialized() {
    if (initialized) return
    initMutex.withLock {
      if (initialized) return
      migrations.backfillDerivedState()
      projector.seed(positionDao.listAll())
      initialized = true
    }
  }

  private suspend fun persistEvent(event: LedgerEvent) {
    when (event) {
      is LedgerEvent.CandleLogged -> candleDao?.upsert(event.toEntity())
      is LedgerEvent.IntentLogged -> intentDao?.upsert(event.toEntity(json))
      is LedgerEvent.OrderPlaced -> orderDao?.upsert(event.toEntity())
      is LedgerEvent.FillRecorded -> {
        fillDao?.insert(event.toEntity())
        orderDao?.updateStatus(event.orderId, OrderEntity.STATUS_FILLED)
      }
      is LedgerEvent.PolicyApplied -> policyDao?.upsert(event.toEntity(json))
      is LedgerEvent.AutomationStateRecorded -> automationStateDao?.upsert(event.toEntity(json))
    }
  }
}

private fun LedgerEvent.CandleLogged.toEntity(): CandleEntity =
  CandleEntity.from(
    symbol = symbol,
    interval = interval,
    ts = ts,
    open = open,
    high = high,
    low = low,
    close = close,
    volume = volume,
    source = source,
  )

private fun LedgerEvent.IntentLogged.toEntity(json: Json): IntentEntity =
  IntentEntity.from(
    id = intentId,
    sourceId = sourceId,
    accountId = accountId,
    kind = kind,
    symbol = symbol,
    side = side,
    notionalUsd = notionalUsd,
    qty = qty,
    priceHint = priceHint,
    meta = meta,
    ts = ts,
    json = json,
  )

private fun LedgerEvent.OrderPlaced.toEntity(): OrderEntity =
  OrderEntity(
    clientOrderId = orderId,
    accountId = accountId,
    symbol = symbol,
    side = side,
    type = typeName,
    qty = qty,
    price = price,
    stopPrice = stopPrice,
    tif = tif,
    status = status,
    ts = ts,
  )

private fun LedgerEvent.FillRecorded.toEntity(): FillEntity =
  FillEntity(
    accountId = accountId,
    orderId = orderId,
    symbol = symbol,
    side = side,
    qty = qty,
    price = price,
    ts = ts,
  )

private fun LedgerEvent.PolicyApplied.toEntity(json: Json): PolicyEntity {
  val serializer = MapSerializer(String.serializer(), String.serializer())
  return PolicyEntity(
    policyId = policyId,
    version = version,
    accountId = accountId,
    configJson = json.encodeToString(serializer, config),
    appliedAt = ts,
  )
}

private fun LedgerEvent.AutomationStateRecorded.toEntity(json: Json): AutomationStateEntity {
  val serializer = MapSerializer(String.serializer(), String.serializer())
  return AutomationStateEntity(
    automationId = automationId,
    status = status,
    stateJson = json.encodeToString(serializer, state),
    updatedAt = ts,
  )
}
