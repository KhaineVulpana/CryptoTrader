package com.kevin.cryptotrader.live

import com.kevin.cryptotrader.contracts.BrokerEvent
import kotlinx.coroutines.flow.Flow

fun interface UserEventSource {
    fun stream(accounts: Set<String>): Flow<BrokerEvent>
}
