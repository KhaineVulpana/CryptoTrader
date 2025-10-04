package com.kevin.cryptotrader.paper

import com.kevin.cryptotrader.contracts.AccountSnapshot
import com.kevin.cryptotrader.contracts.Broker
import com.kevin.cryptotrader.contracts.BrokerEvent
import com.kevin.cryptotrader.contracts.Fill
import com.kevin.cryptotrader.contracts.Order
import com.kevin.cryptotrader.contracts.OrderBookDelta
import com.kevin.cryptotrader.contracts.OrderType
import com.kevin.cryptotrader.contracts.Side
import java.security.SecureRandom
import java.time.Clock
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.absoluteValue
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.round
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * A deterministic, depth-aware paper trading broker. The implementation simulates
 * fills against a level-2 order book, applies maker/taker fees, tracks balances
 * and emits broker events via a [Flow]. All state mutations are serialized via a
 * [Mutex] so the broker is safe to call from concurrent coroutines during tests.
 */
class PaperBroker(
    private val clock: Clock = Clock.systemUTC(),
    makerFeeBps: Double = 0.0,
    takerFeeBps: Double = 0.0,
    initialBalances: Map<String, Double> = emptyMap(),
    private val eventBuffer: Int = DEFAULT_EVENT_BUFFER
) : Broker {
    private val makerFeeRate = makerFeeBps / 10_000.0
    private val takerFeeRate = takerFeeBps / 10_000.0
    private val mutex = Mutex()
    private val balances = initialBalances.toMutableMap()
    private val books = mutableMapOf<String, MutableOrderBook>()
    private val openOrders = mutableMapOf<String, MutableList<OpenOrder>>()
    private val sharedFlow = MutableSharedFlow<BrokerEvent>(extraBufferCapacity = eventBuffer)
    private val scope = CoroutineScope(Dispatchers.Default)
    private val idSeed = AtomicLong(SecureRandom().nextLong().absoluteValue)

    override suspend fun place(order: Order): String = mutex.withLock {
        if (order.type !in SUPPORTED_ORDER_TYPES) {
            emitEvent(
                BrokerEvent.Rejected(
                    order.clientOrderId.ifEmpty { generateOrderId(order.symbol) },
                    "Unsupported order type ${order.type}"
                )
            )
            return order.clientOrderId
        }

        val normalized = normalizeOrder(order)
        val orderId = normalized.clientOrderId
        emitEvent(BrokerEvent.Accepted(orderId, normalized))

        val book = books[normalized.symbol]
        val takerResult = if (book != null) {
            book.match(normalized.side, normalized.qty, normalized.price)
        } else {
            MatchResult.empty(normalized.qty)
        }

        val takerFills = takerResult.fills
        val takerQty = takerFills.sumOf { it.qty }
        if (takerQty > 0.0) {
            applyFillEffects(
                normalized,
                takerFills,
                takerFeeRate
            )
        }

        if (takerFills.isEmpty() && normalized.type == OrderType.MARKET) {
            emitEvent(
                BrokerEvent.Rejected(
                    normalized.clientOrderId,
                    "No liquidity available for market order"
                )
            )
            return@withLock normalized.clientOrderId
        }

        val remaining = takerResult.remainingQty
        if (remaining > EPS) {
            if (normalized.type == OrderType.MARKET) {
                emitFillEvent(normalized, takerFills, isTerminal = false)
                return@withLock normalized.clientOrderId
            }
            if (!reserveForMaker(normalized, remaining)) {
                // Unable to reserve capital -> reject leftover and unwind taker fills
                rollbackFills(normalized, takerFills, takerFeeRate)
                emitEvent(
                    BrokerEvent.Rejected(
                        orderId,
                        "Insufficient balance to reserve maker order"
                    )
                )
                return@withLock orderId
            }
            registerOpenOrder(normalized, remaining)
        }

        emitFillEvent(normalized, takerFills, remaining <= EPS)

        orderId
    }

    override suspend fun cancel(clientOrderId: String): Boolean = mutex.withLock {
        val open = openOrders.values.firstNotNullOfOrNull { list ->
            list.firstOrNull { it.order.clientOrderId == clientOrderId }
        } ?: return@withLock false
        releaseReserve(open)
        removeOpenOrder(open.order.symbol, clientOrderId)
        emitEvent(BrokerEvent.Canceled(clientOrderId))
        true
    }

    override fun streamEvents(accounts: Set<String>): Flow<BrokerEvent> = sharedFlow.asSharedFlow()

    override suspend fun account(): AccountSnapshot = mutex.withLock {
        val equity = balances.filterKeys { it in QUOTE_ASSETS }
            .values
            .sum()
        AccountSnapshot(equity, balances.toMap())
    }

    /** Applies a fresh order book snapshot for [symbol]. */
    suspend fun updateOrderBook(delta: OrderBookDelta) {
        mutex.withLock {
            val book = books.getOrPut(delta.symbol) { MutableOrderBook() }
            book.applySnapshot(delta)
            processRestingOrders(delta.symbol, book)
        }
    }

    fun close() {
        scope.cancel()
    }

    private fun emitEvent(event: BrokerEvent) {
        if (!sharedFlow.tryEmit(event)) {
            scope.launch { sharedFlow.emit(event) }
        }
    }

    private fun normalizeOrder(order: Order): Order {
        val id = if (order.clientOrderId.isNullOrEmpty()) {
            generateOrderId(order.symbol)
        } else {
            order.clientOrderId
        }
        val ts = if (order.ts > 0) order.ts else clock.millis()
        return order.copy(clientOrderId = id, ts = ts)
    }

    private fun generateOrderId(symbol: String): String {
        val suffix = idSeed.incrementAndGet().toString(36)
        return "pb_${symbol.lowercase()}_$suffix"
    }

    private fun registerOpenOrder(order: Order, remaining: Double) {
        val list = openOrders.getOrPut(order.symbol) { mutableListOf() }
        list += OpenOrder(order, remaining, clock.millis())
    }

    private fun removeOpenOrder(symbol: String, id: String) {
        openOrders[symbol]?.removeAll { it.order.clientOrderId == id }
        if (openOrders[symbol].isNullOrEmpty()) {
            openOrders.remove(symbol)
        }
    }

    private fun releaseReserve(open: OpenOrder) {
        val (base, quote) = splitSymbol(open.order.symbol)
        when (open.order.side) {
            Side.BUY -> adjustBalance(quote, open.remainingQty * (open.order.price ?: 0.0))
            Side.SELL -> adjustBalance(base, open.remainingQty)
        }
    }

    private fun reserveForMaker(order: Order, remaining: Double): Boolean {
        val (base, quote) = splitSymbol(order.symbol)
        return when (order.side) {
            Side.BUY -> {
                val required = remaining * (order.price ?: return false)
                val current = balances[quote] ?: 0.0
                if (current + EPS < required) {
                    false
                } else {
                    balances[quote] = current - required
                    true
                }
            }
            Side.SELL -> {
                val available = balances[base] ?: 0.0
                if (available + EPS < remaining) {
                    false
                } else {
                    balances[base] = available - remaining
                    true
                }
            }
        }
    }

    private fun rollbackFills(order: Order, fills: List<FillDetail>, feeRate: Double) {
        if (fills.isEmpty()) return
        val (base, quote) = splitSymbol(order.symbol)
        val total = fills.sumOf { it.qty }
        val value = fills.sumOf { it.qty * it.price }
        when (order.side) {
            Side.BUY -> {
                adjustBalance(base, -total)
                adjustBalance(quote, value * (1 + feeRate))
            }
            Side.SELL -> {
                adjustBalance(base, total)
                adjustBalance(quote, -value * (1 - feeRate))
            }
        }
    }

    private fun applyFillEffects(
        order: Order,
        fills: List<FillDetail>,
        feeRate: Double
    ) {
        if (fills.isEmpty()) return
        val (base, quote) = splitSymbol(order.symbol)
        val fillQty = fills.sumOf { it.qty }
        val fillValue = fills.sumOf { it.qty * it.price }
        val fee = fillValue * feeRate
        when (order.side) {
            Side.BUY -> {
                adjustBalance(base, fillQty)
                adjustBalance(quote, -(fillValue + fee))
            }
            Side.SELL -> {
                adjustBalance(base, -fillQty)
                adjustBalance(quote, fillValue - fee)
            }
        }
    }

    private fun emitFillEvent(order: Order, fills: List<FillDetail>, isTerminal: Boolean) {
        if (fills.isEmpty()) return
        val total = fills.sumOf { it.qty }
        val price = fills.sumOf { it.qty * it.price } / total
        val fill = Fill(order.clientOrderId, total, price, clock.millis())
        val event = if (isTerminal) {
            BrokerEvent.Filled(order.clientOrderId, fill)
        } else {
            BrokerEvent.PartialFill(order.clientOrderId, fill)
        }
        emitEvent(event)
    }

    private fun processRestingOrders(symbol: String, book: MutableOrderBook) {
        val orders = openOrders[symbol] ?: return
        val iterator = orders.iterator()
        while (iterator.hasNext()) {
            val open = iterator.next()
            val price = open.order.price ?: continue
            val shouldFill = when (open.order.side) {
                Side.BUY -> book.bestAsk()?.let { it <= price + EPS } == true
                Side.SELL -> book.bestBid()?.let { it >= price - EPS } == true
            }
            if (!shouldFill) continue
            val makerFill = FillDetail(open.remainingQty, price)
            releaseReserve(open)
            applyFillEffects(open.order, listOf(makerFill), makerFeeRate)
            emitFillEvent(open.order, listOf(makerFill), isTerminal = true)
            iterator.remove()
        }
        if (orders.isEmpty()) {
            openOrders.remove(symbol)
        }
    }

    private fun adjustBalance(asset: String, delta: Double) {
        if (delta.absoluteValue <= EPS) return
        val updated = (balances[asset] ?: 0.0) + delta
        balances[asset] = round(updated * BALANCE_PRECISION).div(BALANCE_PRECISION)
    }

    private fun splitSymbol(symbol: String): Pair<String, String> {
        for (quote in QUOTE_ASSETS) {
            if (symbol.endsWith(quote)) {
                val base = symbol.removeSuffix(quote)
                if (base.isNotEmpty()) {
                    return base to quote
                }
            }
        }
        require(symbol.length > 3) { "Unable to split symbol $symbol" }
        val base = symbol.dropLast(3)
        val quote = symbol.takeLast(3)
        return base to quote
    }

    private data class OpenOrder(
        val order: Order,
        var remainingQty: Double,
        val createdAt: Long
    )

    private class MutableOrderBook {
        private val bids = mutableListOf<Level>()
        private val asks = mutableListOf<Level>()

        fun applySnapshot(delta: OrderBookDelta) {
            bids.clear()
            asks.clear()
            delta.bids.sortedByDescending { it.first }
                .mapTo(bids) { Level(it.first, it.second) }
            delta.asks.sortedBy { it.first }
                .mapTo(asks) { Level(it.first, it.second) }
        }

        fun match(side: Side, qty: Double, limitPrice: Double?): MatchResult {
            return when (side) {
                Side.BUY -> consume(asks, qty, limitPrice, isBuy = true)
                Side.SELL -> consume(bids, qty, limitPrice, isBuy = false)
            }
        }

        fun bestAsk(): Double? = asks.firstOrNull()?.price
        fun bestBid(): Double? = bids.firstOrNull()?.price

        private fun consume(
            levels: MutableList<Level>,
            qty: Double,
            limitPrice: Double?,
            isBuy: Boolean
        ): MatchResult {
            if (qty <= EPS) return MatchResult.empty(0.0)
            var remaining = qty
            val fills = mutableListOf<FillDetail>()
            var index = 0
            while (remaining > EPS && index < levels.size) {
                val level = levels[index]
                val price = level.price
                if (limitPrice != null) {
                    val violatesLimit = if (isBuy) {
                        price > limitPrice + EPS
                    } else {
                        price < limitPrice - EPS
                    }
                    if (violatesLimit) break
                }
                val fillQty = min(remaining, level.qty)
                if (fillQty <= EPS) {
                    index++
                    continue
                }
                fills += FillDetail(fillQty, price)
                remaining -= fillQty
                val leftover = level.qty - fillQty
                if (leftover <= EPS) {
                    levels.removeAt(index)
                } else {
                    levels[index] = level.copy(qty = leftover)
                    index++
                }
            }
            return MatchResult(fills, remaining)
        }
    }

    private data class Level(val price: Double, val qty: Double)

    private data class FillDetail(val qty: Double, val price: Double)

    private data class MatchResult(val fills: List<FillDetail>, val remainingQty: Double) {
        companion object {
            fun empty(remaining: Double) = MatchResult(emptyList(), remaining)
        }
    }

    companion object {
        private const val EPS = 1e-9
        private val BALANCE_PRECISION = 10.0.pow(8)
        private const val DEFAULT_EVENT_BUFFER = 128
        private val QUOTE_ASSETS = setOf("USD", "USDT", "USDC", "BUSD", "EUR")
        private val SUPPORTED_ORDER_TYPES = setOf(OrderType.MARKET, OrderType.LIMIT)
    }
}
