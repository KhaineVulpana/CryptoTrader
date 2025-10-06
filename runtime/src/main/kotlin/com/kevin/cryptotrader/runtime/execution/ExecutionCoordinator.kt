package com.kevin.cryptotrader.runtime.execution

import com.kevin.cryptotrader.contracts.Broker
import com.kevin.cryptotrader.contracts.Intent
import com.kevin.cryptotrader.contracts.PolicyEngine
import com.kevin.cryptotrader.contracts.Position
import com.kevin.cryptotrader.contracts.RiskSizer
import java.time.Clock
import java.time.Duration
import java.util.ArrayDeque
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ExecutionCoordinator(
    private val broker: Broker,
    private val policyEngine: PolicyEngine,
    private val riskSizer: RiskSizer,
    private val ledger: Ledger,
    private val clock: Clock = Clock.systemUTC(),
    private val config: ExecutionConfig = ExecutionConfig()
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

        val netPlan = policyEngine.net(accepted, positions)
        ledger.append(LedgerEvent.NetPlanReady(now, netPlan))

        val account = broker.account()
        val orders = riskSizer.size(netPlan, account)
        if (orders.isEmpty()) return
        ledger.append(LedgerEvent.OrdersSized(now, orders))

        for (order in orders) {
            val brokerId = broker.place(order)
            ledger.append(LedgerEvent.OrderRouted(clock.millis(), order, brokerId))
        }
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
}
