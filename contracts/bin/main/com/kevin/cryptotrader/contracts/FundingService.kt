package com.kevin.cryptotrader.contracts

import kotlinx.coroutines.flow.Flow

interface FundingService {
  suspend fun planTransfer(
    from: AccountId?,
    to: AccountId,
    asset: String,
    amount: Double,
    preferences: PlanPrefs = PlanPrefs(),
  ): TransferPlan

  suspend fun execute(plan: TransferPlan): String

  fun track(executionId: String): Flow<PlanStatus>
}
