package com.kevin.cryptotrader.paper

import com.kevin.cryptotrader.contracts.Order
import com.kevin.cryptotrader.contracts.OrderBookDelta
import com.kevin.cryptotrader.contracts.OrderType
import com.kevin.cryptotrader.contracts.Side
import com.kevin.cryptotrader.contracts.TIF
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

class PaperBrokerTest {
    private val clock = MutableTestClock()
    private val broker = PaperBroker(
        clock = clock,
        makerFeeBps = 5.0,
        takerFeeBps = 10.0,
        initialBalances = mapOf("USDT" to 1_000.0)
    )

    @AfterEach
    fun tearDown() {
        broker.close()
    }

    @Test
    fun `market buy consumes top of book with taker fees`() = runTest {
        broker.updateOrderBook(
            OrderBookDelta(
                ts = 0,
                symbol = "BTCUSDT",
                bids = listOf(99.0 to 1.0),
                asks = listOf(100.0 to 0.5, 101.0 to 0.5)
            )
        )

        val orderId = broker.place(
            Order(
                clientOrderId = "",
                symbol = "BTCUSDT",
                side = Side.BUY,
                type = OrderType.MARKET,
                qty = 0.4,
                price = null,
                stopPrice = null,
                tif = TIF.GTC,
                ts = 0L
            )
        )

        val snapshot = broker.account()
        assertEquals(0.4, snapshot.balances["BTC"] ?: 0.0, 1e-9)
        val expectedQuote = 1_000.0 - (0.4 * 100.0) - (0.4 * 100.0 * 0.001)
        assertEquals(expectedQuote, snapshot.balances["USDT"] ?: 0.0, 1e-6)
        assertTrue(orderId.startsWith("pb_btcusdt_"))
    }

    @Test
    fun `limit order rests then fills on cross with maker fees`() = runTest {
        val broker = PaperBroker(
            clock = clock,
            makerFeeBps = 2.0,
            takerFeeBps = 10.0,
            initialBalances = mapOf("USDT" to 2_000.0)
        )

        try {

        broker.updateOrderBook(
            OrderBookDelta(0, "ETHUSDT", bids = listOf(1800.0 to 1.0), asks = listOf(1810.0 to 1.0))
        )

        val orderId = broker.place(
            Order(
                clientOrderId = "manual-id",
                symbol = "ETHUSDT",
                side = Side.BUY,
                type = OrderType.LIMIT,
                qty = 0.5,
                price = 1800.0,
                stopPrice = null,
                tif = TIF.GTC,
                ts = 0L
            )
        )
        assertEquals("manual-id", orderId)
        val reserved = broker.account().balances["USDT"] ?: error("missing reserve")
        assertEquals(2_000.0 - 0.5 * 1800.0, reserved, 1e-6)

        broker.updateOrderBook(
            OrderBookDelta(1, "ETHUSDT", bids = listOf(1799.0 to 1.0), asks = listOf(1795.0 to 2.0))
        )

        val snapshot = broker.account()
        assertEquals(0.5, snapshot.balances["ETH"] ?: 0.0, 1e-9)
        val expectedQuote = 2_000.0 - (0.5 * 1800.0) - (0.5 * 1800.0 * 0.0002)
        assertEquals(expectedQuote, snapshot.balances["USDT"] ?: 0.0, 1e-6)
        } finally {
            broker.close()
        }
    }

    @Test
    fun `canceling open order releases reserved balances`() = runTest {
        val broker = PaperBroker(
            clock = clock,
            makerFeeBps = 0.0,
            takerFeeBps = 0.0,
            initialBalances = mapOf("USDT" to 1_000.0)
        )
        try {
            broker.updateOrderBook(OrderBookDelta(0, "BTCUSDT", bids = emptyList(), asks = listOf(15000.0 to 1.0)))

            val id = broker.place(
                Order(
                    clientOrderId = "",
                    symbol = "BTCUSDT",
                    side = Side.BUY,
                    type = OrderType.LIMIT,
                    qty = 0.1,
                    price = 9000.0,
                    stopPrice = null,
                    tif = TIF.GTC,
                    ts = 0L
                )
            )
            val afterPlace = broker.account().balances["USDT"] ?: 0.0
            assertEquals(1_000.0 - 900.0, afterPlace, 1e-6)

            assertTrue(broker.cancel(id))
            val afterCancel = broker.account().balances["USDT"] ?: 0.0
            assertEquals(1_000.0, afterCancel, 1e-6)
        } finally {
            broker.close()
        }
    }

    private class MutableTestClock : Clock() {
        private var instant: Instant = Instant.parse("2024-01-01T00:00:00Z")
        override fun getZone(): ZoneId = ZoneId.of("UTC")
        override fun withZone(zone: ZoneId?): Clock = this
        override fun instant(): Instant = instant
        fun advanceMillis(delta: Long) {
            instant = instant.plusMillis(delta)
        }
    }
}
