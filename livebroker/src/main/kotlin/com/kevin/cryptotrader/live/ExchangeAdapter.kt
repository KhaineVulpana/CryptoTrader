package com.kevin.cryptotrader.live

import com.kevin.cryptotrader.contracts.AccountSnapshot
import com.kevin.cryptotrader.contracts.BrokerEvent
import com.kevin.cryptotrader.contracts.Order
import com.kevin.cryptotrader.contracts.LogLevel
import com.kevin.cryptotrader.contracts.TelemetryModule
import com.kevin.cryptotrader.core.telemetry.TelemetryCenter
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart

interface ExchangeAdapter {
    suspend fun place(order: Order): String
    suspend fun cancel(orderId: String): Boolean
    fun streamEvents(accounts: Set<String>): Flow<BrokerEvent>
    suspend fun account(): AccountSnapshot
}

class LiveBroker(private val adapter: ExchangeAdapter) : com.kevin.cryptotrader.contracts.Broker {
    override suspend fun place(order: Order): String {
        val start = System.nanoTime()
        return runCatching { adapter.place(order) }
            .onSuccess { orderId ->
                val latencyMs = (System.nanoTime() - start) / 1_000_000.0
                TelemetryCenter.logEvent(
                    module = TelemetryModule.LIVE_BROKER,
                    level = LogLevel.INFO,
                    message = "Order placed",
                    fields = mapOf(
                        "symbol" to order.symbol,
                        "orderId" to orderId,
                        "latencyMs" to "%.2f".format(latencyMs)
                    )
                )
            }
            .onFailure { throwable ->
                TelemetryCenter.logEvent(
                    module = TelemetryModule.LIVE_BROKER,
                    level = LogLevel.ERROR,
                    message = "Order placement failed",
                    fields = mapOf(
                        "symbol" to order.symbol,
                        "error" to (throwable.message ?: throwable::class.java.simpleName)
                    )
                )
            }
            .getOrThrow()
    }

    override suspend fun cancel(clientOrderId: String): Boolean {
        val start = System.nanoTime()
        return runCatching { adapter.cancel(clientOrderId) }
            .onSuccess { canceled ->
                val latencyMs = (System.nanoTime() - start) / 1_000_000.0
                TelemetryCenter.logEvent(
                    module = TelemetryModule.LIVE_BROKER,
                    level = LogLevel.INFO,
                    message = "Cancel request",
                    fields = mapOf(
                        "orderId" to clientOrderId,
                        "latencyMs" to "%.2f".format(latencyMs),
                        "canceled" to canceled.toString()
                    )
                )
            }
            .onFailure { throwable ->
                TelemetryCenter.logEvent(
                    module = TelemetryModule.LIVE_BROKER,
                    level = LogLevel.ERROR,
                    message = "Cancel failed",
                    fields = mapOf(
                        "orderId" to clientOrderId,
                        "error" to (throwable.message ?: throwable::class.java.simpleName)
                    )
                )
            }
            .getOrThrow()
    }

    override fun streamEvents(accounts: Set<String>): Flow<BrokerEvent> {
        val streamId = accounts.sorted().joinToString(",").ifEmpty { "default" }
        val reconnectCounter = AtomicInteger(0)
        TelemetryCenter.logEvent(
            module = TelemetryModule.LIVE_BROKER,
            level = LogLevel.INFO,
            message = "Subscribing to broker events",
            fields = mapOf("accounts" to streamId)
        )
        var last = System.nanoTime()
        return adapter.streamEvents(accounts)
            .onStart {
                last = System.nanoTime()
                TelemetryCenter.recordReconnect(
                    module = TelemetryModule.LIVE_BROKER,
                    streamId = streamId,
                    reconnectCount = reconnectCounter.get().toDouble(),
                    fields = mapOf("reason" to "initial")
                )
            }
            .onEach { event ->
                val now = System.nanoTime()
                val latencyMs = (now - last) / 1_000_000.0
                last = now
                TelemetryCenter.recordWsLatency(
                    module = TelemetryModule.LIVE_BROKER,
                    streamId = streamId,
                    latencyMs = latencyMs,
                    fields = mapOf("event" to event::class.simpleName.orEmpty())
                )
            }
            .catch { throwable ->
                val reconnects = reconnectCounter.incrementAndGet().toDouble()
                TelemetryCenter.recordReconnect(
                    module = TelemetryModule.LIVE_BROKER,
                    streamId = streamId,
                    reconnectCount = reconnects,
                    fields = mapOf("error" to (throwable.message ?: throwable::class.java.simpleName))
                )
                TelemetryCenter.logEvent(
                    module = TelemetryModule.LIVE_BROKER,
                    level = LogLevel.ERROR,
                    message = "Event stream error",
                    fields = mapOf(
                        "accounts" to streamId,
                        "error" to (throwable.message ?: throwable::class.java.simpleName)
                    )
                )
                throw throwable
            }
            .onCompletion { cause ->
                val level = if (cause == null) LogLevel.INFO else LogLevel.WARN
                TelemetryCenter.logEvent(
                    module = TelemetryModule.LIVE_BROKER,
                    level = level,
                    message = "Event stream completed",
                    fields = mapOf(
                        "accounts" to streamId,
                        "reconnects" to reconnectCounter.get().toString(),
                        "status" to (cause?.message ?: "ok")
                    )
                )
            }
    }

    override suspend fun account(): AccountSnapshot {
        val start = System.nanoTime()
        return runCatching { adapter.account() }
            .onSuccess { snapshot ->
                val latencyMs = (System.nanoTime() - start) / 1_000_000.0
                TelemetryCenter.logEvent(
                    module = TelemetryModule.LIVE_BROKER,
                    level = LogLevel.INFO,
                    message = "Fetched account snapshot",
                    fields = mapOf(
                        "latencyMs" to "%.2f".format(latencyMs),
                        "equityUsd" to snapshot.equityUsd.toString()
                    )
                )
            }
            .onFailure { throwable ->
                TelemetryCenter.logEvent(
                    module = TelemetryModule.LIVE_BROKER,
                    level = LogLevel.ERROR,
                    message = "Account snapshot failed",
                    fields = mapOf("error" to (throwable.message ?: throwable::class.java.simpleName))
                )
            }
            .getOrThrow()
    }
}
