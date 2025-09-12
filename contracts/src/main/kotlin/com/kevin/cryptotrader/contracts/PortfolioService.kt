package com.kevin.cryptotrader.contracts

import kotlinx.coroutines.flow.Flow

interface PortfolioService {
  fun accounts(): Flow<List<Account>>
  fun holdings(): Flow<List<Holding>>
  fun positions(): Flow<List<Position>>
  suspend fun acquire(asset: String, amount: Double, target: AccountId? = null): TransferPlan
}
