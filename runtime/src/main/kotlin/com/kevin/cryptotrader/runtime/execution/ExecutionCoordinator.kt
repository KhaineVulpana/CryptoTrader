package com.kevin.cryptotrader.runtime.execution

import com.kevin.cryptotrader.contracts.Broker
import com.kevin.cryptotrader.contracts.Intent
import com.kevin.cryptotrader.contracts.LivePipelineSample
import com.kevin.cryptotrader.contracts.PipelineObserver
import com.kevin.cryptotrader.contracts.PolicyEngine
import com.kevin.cryptotrader.contracts.Position
import com.kevin.cryptotrader.contracts.ResourceUsageSample
import com.kevin.cryptotrader.contracts.RiskSizer
import java.time.Clock
import java.time.Duration
import java.util.ArrayDeque
import kotlin.system.measureNanoTime
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ExecutionCoordinator(
    private val broker: Broker,
    private val policyEngine: PolicyEngine,
    private val riskSizer: RiskSizer,
    private val ledger: Ledger,
    private val clock: Clock = Clock.systemUTC(),
    private val config: ExecutionConfig = ExecutionConfig(),
    private val observer: PipelineObserver = PipelineObserver.NOOP,
) {
    private val mutex = Mutex()
    private val processedIds = ArrayDeque<String>()
    private val processedSet = linkedSetOf<String>()
    private val lastSourceExecution = mutableMapOf<String, Long>()

    suspend fun coordinate(intents: List<Intent>, positions: List<Position>) {
        val accepted = filterIntents(intents)
        if (accepted.isEmpty()) return
        val now = clock.millis()
        ledger.append(LedgerEvent.IntentsRecorded(now, accepted))

        lateinit var netPlan: com.kevin.cryptotrader.contracts.NetPlan
        val netNs = measureNanoTime { netPlan = policyEngine.net(accepted, positions) }
        ledger.append(LedgerEvent.NetPlanReady(now, netPlan))

        val account = broker.account()
        val riskResult: com.kevin.cryptotrader.contracts.RiskResult
        val riskNs = measureNanoTime { riskResult = riskSizer.size(netPlan, account) }
        if (riskResult.orders.isEmpty()) {
            recordResourceUsage(now)
            return
        }
        ledger.append(LedgerEvent.OrdersSized(now, riskResult.orders))

        val placementNs = measureNanoTime {
            for (order in riskResult.orders) {
                val brokerId = broker.place(order)
                ledger.append(LedgerEvent.OrderRouted(clock.millis(), order, brokerId))
            }
        }

        observer.onLivePipelineSample(
            LivePipelineSample(
                timestampMs = now,
                intents = accepted.size,
                netDurationMs = netNs.toDouble() / 1_000_000.0,
                riskDurationMs = riskNs.toDouble() / 1_000_000.0,
                placementDurationMs = placementNs.toDouble() / 1_000_000.0,
            ),
        )
        recordResourceUsage(now)
    }

    private suspend fun filterIntents(intents: List<Intent>): List<Intent> = mutex.withLock {
        val accepted = mutableListOf<Intent>()
        val cooldownMs = config.cooldown.toMillisSafe()
        val now = clock.millis()
        val acceptedSources = mutableSetOf<String>()
        for (intent in intents) {
            if (config.maxIntentCache > 0 && processedSet.contains(intent.id)) continue
            val last = lastSourceExecution[intent.sourceId]
            if (last != null && cooldownMs > 0 && now - last < cooldownMs && intent.sourceId !in acceptedSources) {
                continue
            }
            acceptedSources += intent.sourceId
            recordIntentId(intent.id)
            accepted += intent
        }
        for (source in acceptedSources) {
            lastSourceExecution[source] = now
        }
        accepted
    }

    private fun recordIntentId(id: String) {
        if (config.maxIntentCache <= 0) return
        processedSet.add(id)
        processedIds.addLast(id)
        while (processedIds.size > config.maxIntentCache) {
            val removed = processedIds.removeFirst()
            processedSet.remove(removed)
        }
    }

    private fun Duration.toMillisSafe(): Long = if (this.isZero) 0L else this.toMillis()

    private fun recordResourceUsage(ts: Long) {
        val runtime = Runtime.getRuntime()
        val used = runtime.totalMemory() - runtime.freeMemory()
        observer.onResourceSample(
            ResourceUsageSample(
                timestampMs = ts,
                heapUsedBytes = used,
                heapMaxBytes = runtime.maxMemory(),
            ),
        )
    }
}
