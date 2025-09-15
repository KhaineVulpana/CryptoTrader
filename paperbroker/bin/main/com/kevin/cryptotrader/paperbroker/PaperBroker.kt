package com.kevin.cryptotrader.paperbroker

import com.kevin.cryptotrader.contracts.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

data class PaperBrokerConfig(
  val ackLatencyMs: Long = 0,
  val firstFillLatencyMs: Long = 0,
  val perFillIntervalMs: Long = 0,
  val slippageBps: Int = 0,
  val feeBps: Int = 0,
  val partialPieces: Int = 1,
  val clockMs: () -> Long = { System.currentTimeMillis() },
  val scope: CoroutineScope = CoroutineScope(Dispatchers.Default),
)

fun interface PriceSource { fun price(symbol: String, ts: Long): Double }

class PaperBroker(
  private val cfg: PaperBrokerConfig,
  private val priceSource: PriceSource,
) : Broker {
  private val events = MutableSharedFlow<BrokerEvent>(replay = 64, extraBufferCapacity = 64)
  private val openOrders = ConcurrentHashMap<String, Order>()
  private val jobs = ConcurrentHashMap<String, Job>()

  override suspend fun place(order: Order): String {
    val id = if (order.clientOrderId.isNotBlank()) order.clientOrderId else "ord-${cfg.clockMs()}-${openOrders.size}"
    openOrders[id] = order
    // Emit acceptance immediately to simplify testing/consumers
    events.emit(BrokerEvent.Accepted(id, order))

    jobs[id] = cfg.scope.launch {
      if (cfg.firstFillLatencyMs > 0) delay(cfg.firstFillLatencyMs)
      val totalQty = order.qty
      val pieces = cfg.partialPieces.coerceAtLeast(1)
      val basePiece = totalQty / pieces
      var remaining = totalQty
      repeat(pieces) { idx ->
        if (!openOrders.containsKey(id)) return@launch
        val ts = cfg.clockMs()
        val ref = priceSource.price(order.symbol, ts)
        val slip = cfg.slippageBps / 10_000.0
        val fillPrice = when (order.side) {
          Side.BUY -> ref * (1.0 + slip)
          Side.SELL -> ref * (1.0 - slip)
        }
        val qty = if (idx == pieces - 1) remaining else basePiece
        remaining -= qty
        val fill = Fill(orderId = id, qty = qty, price = fillPrice, ts = ts)
        val evt = if (idx == pieces - 1) BrokerEvent.Filled(id, fill) else BrokerEvent.PartialFill(id, fill)
        events.emit(evt)
        if (idx != pieces - 1 && cfg.perFillIntervalMs > 0) delay(cfg.perFillIntervalMs)
      }
      openOrders.remove(id)
    }
    return id
  }

  override suspend fun cancel(orderId: String): Boolean {
    val o = openOrders.remove(orderId) ?: return false
    jobs.remove(orderId)?.cancel()
    events.emit(BrokerEvent.Canceled(orderId))
    return true
  }

  override fun streamEvents(symbols: Set<String>): Flow<BrokerEvent> = events.asSharedFlow()

  override suspend fun account(): AccountSnapshot {
    return AccountSnapshot(equityUsd = 0.0, balances = emptyMap())
  }
}

