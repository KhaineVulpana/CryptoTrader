package com.kevin.cryptotrader.paperbroker

import com.kevin.cryptotrader.contracts.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.launch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PaperBrokerTest {
  @Test
  fun deterministic_single_fill_with_slippage_and_latency() = runTest {
    val dispatcher = StandardTestDispatcher(testScheduler)
    val scope = TestScope(dispatcher)
    var now = 0L
    val cfg = PaperBrokerConfig(
      ackLatencyMs = 100,
      firstFillLatencyMs = 200,
      perFillIntervalMs = 0,
      slippageBps = 10,
      partialPieces = 1,
      clockMs = { now },
      scope = scope,
    )
    val price = PriceSource { _, _ -> 100.0 }
    val broker = PaperBroker(cfg, price)
    val events = mutableListOf<BrokerEvent>()
    val job = scope.launch { broker.streamEvents(emptySet()).toList(events) }
    runCurrent()

    val id = broker.place(Order(clientOrderId = "", symbol = "BTCUSDT", side = Side.BUY, type = OrderType.MARKET, qty = 1.0))

    // Advance to ack
    advanceTimeBy(100); now = 100; runCurrent()
    // Advance to fill
    advanceTimeBy(100); now = 200; runCurrent()

    job.cancel()

    // Expect Accepted then Filled with slippage applied
    assertTrue(events.first() is BrokerEvent.Accepted)
    val filled = events.filterIsInstance<BrokerEvent.Filled>().first()
    assertEquals(id, filled.orderId)
    assertEquals(1.0, filled.fill.qty)
    assertEquals(100.0 * 1.001, filled.fill.price, 1e-9)
  }

  @Test
  fun partial_fills_and_cancel() = runTest {
    val dispatcher = StandardTestDispatcher(testScheduler)
    val scope = TestScope(dispatcher)
    var now = 0L
    val cfg = PaperBrokerConfig(
      ackLatencyMs = 0,
      firstFillLatencyMs = 50,
      perFillIntervalMs = 50,
      slippageBps = 0,
      partialPieces = 3,
      clockMs = { now },
      scope = scope,
    )
    val price = PriceSource { _, _ -> 10.0 }
    val broker = PaperBroker(cfg, price)
    val collected = mutableListOf<BrokerEvent>()
    val job = scope.launch { broker.streamEvents(emptySet()).toList(collected) }
    runCurrent()

    val id = broker.place(Order(clientOrderId = "", symbol = "ETHUSDT", side = Side.SELL, type = OrderType.MARKET, qty = 3.0))
    advanceTimeBy(50); now = 50; runCurrent()
    // Cancel before second piece
    broker.cancel(id)
    advanceTimeBy(200); now = 250; runCurrent()
    job.cancel()

    // We should have at least one PartialFill and one Canceled, and no final Filled
    assertTrue(collected.any { it is BrokerEvent.PartialFill })
    assertTrue(collected.any { it is BrokerEvent.Canceled })
    assertTrue(collected.none { it is BrokerEvent.Filled })
  }
}

