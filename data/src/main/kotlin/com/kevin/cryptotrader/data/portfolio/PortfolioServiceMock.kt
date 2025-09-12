package com.kevin.cryptotrader.data.portfolio

import com.kevin.cryptotrader.contracts.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class PortfolioServiceMock : PortfolioService {
  private val accountsFlow = MutableStateFlow(FixturesLoader.loadAccounts())
  private val holdingsFlow = MutableStateFlow<List<Holding>>(emptyList())
  private val positionsFlow = MutableStateFlow<List<Position>>(emptyList())

  override fun accounts(): Flow<List<Account>> = accountsFlow.asStateFlow()

  override fun holdings(): Flow<List<Holding>> = holdingsFlow.asStateFlow()

  override fun positions(): Flow<List<Position>> = positionsFlow.asStateFlow()

  override suspend fun acquire(asset: String, amount: Double, target: AccountId?): TransferPlan {
    val to = target ?: accountsFlow.value.firstOrNull()?.id ?: "acc_unknown"
    return TransferPlan(
      id = "tp-$asset-$amount",
      steps = listOf(
        TransferStep.BuyOnExchange(exchangeId = "binance", symbol = "$asset/USDT", qty = amount),
      ),
      totalCostUsd = amount * 1.0,
      etaSeconds = 60,
    )
  }
}

