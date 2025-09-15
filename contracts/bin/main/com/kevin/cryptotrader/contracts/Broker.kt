package com.kevin.cryptotrader.contracts

import kotlinx.coroutines.flow.Flow

interface Broker {
  suspend fun place(order: Order): String

  suspend fun cancel(orderId: String): Boolean

  fun streamEvents(symbols: Set<String>): Flow<BrokerEvent>

  suspend fun account(): AccountSnapshot
}
