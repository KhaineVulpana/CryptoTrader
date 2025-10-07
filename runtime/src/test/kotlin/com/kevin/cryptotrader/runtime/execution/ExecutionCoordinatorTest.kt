package com.kevin.cryptotrader.runtime.execution

import com.kevin.cryptotrader.contracts.AccountSnapshot
import com.kevin.cryptotrader.contracts.Broker
import com.kevin.cryptotrader.contracts.BrokerEvent
import com.kevin.cryptotrader.contracts.Intent
import com.kevin.cryptotrader.contracts.NetPlan
import com.kevin.cryptotrader.contracts.Order
import com.kevin.cryptotrader.contracts.OrderType
import com.kevin.cryptotrader.contracts.Position
import com.kevin.cryptotrader.contracts.RiskSizer
import com.kevin.cryptotrader.contracts.Side
import com.kevin.cryptotrader.contracts.TIF
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class ExecutionCoordinatorTest {
    private val clock = object : Clock() {
        private val instant = Instant.parse("2024-06-01T00:00:00Z")
        override fun getZone(): ZoneId = ZoneId.of("UTC")
        override fun withZone(zone: ZoneId): Clock = this
        override fun instant(): Instant = instant
    }

    @Test
    fun `coordinator enforces idempotency cooldown and policies`() = runTest {
        val config = ExecutionConfig(
            cooldown = Duration.ofMinutes(5),
            priorityOrder = listOf("high", "low"),
            vote = VoteConfig(threshold = 2, groupKey = "vote"),
            portfolioTargets = mapOf("SOLUSDT" to 5.0)
        )
        val ledger = RecordingLedger()
        val broker = RecordingBroker()
        val riskSizer = PassthroughRiskSizer()
        val policy = DefaultPolicyEngine(config)
        val coordinator = ExecutionCoordinator(broker, policy, riskSizer, ledger, clock, config)

        val intents = listOf(
            intent(
                id = "h1",
                source = "alpha",
                kind = "high",
                symbol = "ETHUSDT",
                side = Side.BUY,
                qty = 1.0,
                vote = "group-eth"
            ),
            intent(
                id = "h2",
                source = "alpha",
                kind = "high",
                symbol = "ETHUSDT",
                side = Side.BUY,
                qty = 0.5,
                vote = "group-eth"
            ),
            intent(
                id = "l1",
                source = "beta",
                kind = "low",
                symbol = "BTCUSDT",
                side = Side.BUY,
                qty = 0.3,
                vote = "group-btc"
            ),
            intent(
                id = "l1-dup",
                source = "beta",
                kind = "low",
                symbol = "BTCUSDT",
                side = Side.BUY,
                qty = 0.3,
                vote = "group-btc"
            ),
            intent(
                id = "dup",
                source = "beta",
                kind = "low",
                symbol = "BTCUSDT",
                side = Side.BUY,
                qty = 0.1,
                vote = "group-btc"
            ),
            intent(
                id = "dup",
                source = "beta",
                kind = "low",
                symbol = "BTCUSDT",
                side = Side.BUY,
                qty = 0.1,
                vote = "group-btc"
            )
        )
        val positions = listOf(
            Position("acct", "BTCUSDT", 0.2, 0.0, 0.0, 0.0),
            Position("acct", "SOLUSDT", 1.0, 0.0, 0.0, 0.0)
        )

        coordinator.coordinate(intents, positions)

        assertEquals(3, broker.placed.size)
        val sizedNetPlan = requireNotNull(riskSizer.lastNetPlan)
        assertEquals(3, sizedNetPlan.intents.size)
        val symbols = sizedNetPlan.intents.map { it.symbol }
        assertTrue(symbols.containsAll(listOf("ETHUSDT", "BTCUSDT", "SOLUSDT")))
        assertEquals("ETHUSDT", sizedNetPlan.intents.first().symbol)

        val events = ledger.events
        assertEquals(5, (events[0] as LedgerEvent.IntentsRecorded).intents.size)
        assertIs<LedgerEvent.NetPlanReady>(events[1])
        assertIs<LedgerEvent.OrdersSized>(events[2])
        assertEquals(3, events.count { it is LedgerEvent.OrderRouted })

        // Cooldown prevents immediate re-processing for the same source.
        coordinator.coordinate(
            listOf(
                intent(
                    id = "new",
                    source = "beta",
                    kind = "low",
                    symbol = "BTCUSDT",
                    side = Side.SELL,
                    qty = 0.5,
                    vote = "group-btc"
                )
            ),
            positions
        )
        assertEquals(3, broker.placed.size)
    }

    private fun intent(
        id: String,
        source: String,
        kind: String,
        symbol: String,
        side: Side,
        qty: Double,
        vote: String
    ): Intent = Intent(
        id = id,
        sourceId = source,
        kind = kind,
        symbol = symbol,
        side = side,
        notionalUsd = null,
        qty = qty,
        priceHint = null,
        meta = mapOf("vote" to vote)
    )

    private class RecordingLedger : Ledger {
        val events = mutableListOf<LedgerEvent>()
        override suspend fun append(event: LedgerEvent) {
            events += event
        }
    }

    private class RecordingBroker : Broker {
        val placed = mutableListOf<Order>()
        private val snapshot = AccountSnapshot(10_000.0, mapOf("USDT" to 10_000.0))
        override suspend fun place(order: Order): String {
            placed += order
            return "broker-${placed.size}"
        }
        override suspend fun cancel(clientOrderId: String): Boolean = false
        override fun streamEvents(accounts: Set<String>): Flow<BrokerEvent> = emptyFlow()
        override suspend fun account(): AccountSnapshot = snapshot
    }

    private class PassthroughRiskSizer : RiskSizer {
        var lastNetPlan: NetPlan? = null
        override fun size(netPlan: NetPlan, account: AccountSnapshot): com.kevin.cryptotrader.contracts.RiskResult {
            lastNetPlan = netPlan
            val orders = netPlan.intents.mapIndexed { index, intent ->
                Order(
                    clientOrderId = "sized-$index",
                    symbol = intent.symbol,
                    side = intent.side,
                    type = OrderType.MARKET,
                    qty = intent.qty ?: 0.0,
                    price = intent.priceHint,
                    stopPrice = null,
                    tif = TIF.GTC,
                    ts = 0L
                )
            }
            return com.kevin.cryptotrader.contracts.RiskResult(orders)
        }
    }
}
