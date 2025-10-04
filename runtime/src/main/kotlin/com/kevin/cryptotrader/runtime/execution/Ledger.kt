package com.kevin.cryptotrader.runtime.execution

import com.kevin.cryptotrader.contracts.Intent
import com.kevin.cryptotrader.contracts.NetPlan
import com.kevin.cryptotrader.contracts.Order

interface Ledger {
    suspend fun append(event: LedgerEvent)
}

sealed interface LedgerEvent {
    val timestamp: Long

    data class IntentsRecorded(override val timestamp: Long, val intents: List<Intent>) : LedgerEvent
    data class NetPlanReady(override val timestamp: Long, val netPlan: NetPlan) : LedgerEvent
    data class OrdersSized(override val timestamp: Long, val orders: List<Order>) : LedgerEvent
    data class OrderRouted(override val timestamp: Long, val order: Order, val brokerOrderId: String) : LedgerEvent
}
