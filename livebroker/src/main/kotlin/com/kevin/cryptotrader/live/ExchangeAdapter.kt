package com.kevin.cryptotrader.live

import com.kevin.cryptotrader.contracts.AccountSnapshot
import com.kevin.cryptotrader.contracts.BrokerEvent
import com.kevin.cryptotrader.contracts.Order
import kotlinx.coroutines.flow.Flow

interface ExchangeAdapter {
    suspend fun place(order: Order): String
    suspend fun cancel(orderId: String): Boolean
    fun streamEvents(accounts: Set<String>): Flow<BrokerEvent>
    suspend fun account(): AccountSnapshot
}

class LiveBroker(private val adapter: ExchangeAdapter) : com.kevin.cryptotrader.contracts.Broker {
    override suspend fun place(order: Order): String = adapter.place(order)
    override suspend fun cancel(clientOrderId: String): Boolean = adapter.cancel(clientOrderId)
    override fun streamEvents(accounts: Set<String>): Flow<BrokerEvent> = adapter.streamEvents(accounts)
    override suspend fun account(): AccountSnapshot = adapter.account()
}
