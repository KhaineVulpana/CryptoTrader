package com.kevin.cryptotrader.data.funding

import com.kevin.cryptotrader.contracts.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class FundingServiceMock : FundingService {
  private val updates = MutableSharedFlow<PlanStatus>(replay = 0, extraBufferCapacity = 16)

  override suspend fun planTransfer(from: AccountId?, to: AccountId, asset: String, amount: Double, preferences: PlanPrefs): TransferPlan {
    val steps = mutableListOf<TransferStep>()
    if (from == null) {
      steps += TransferStep.BuyOnExchange(exchangeId = preferences.preferExchange ?: "binance", symbol = "$asset/USDT", qty = amount)
    } else {
      steps += TransferStep.TransferInternal(accountFrom = from, accountTo = to, asset = asset, amount = amount)
    }
    val cost = CostBreakdown(
      notionalUsd = 0.0,
      tradingFeesUsd = 0.0,
      withdrawalFeesUsd = amount * 0.001,
      networkFeesUsd = 0.0,
    )
    val safety = listOf(
      PlanSafetyCheck(
        id = "mock_two_tap",
        description = "Confirm mock transfer",
        status = SafetyStatus.PENDING,
        blocking = false,
      )
    )
    return TransferPlan(
      id = "plan-$asset-$amount",
      steps = steps,
      totalCostUsd = cost.totalCostUsd,
      etaSeconds = 120,
      costBreakdown = cost,
      safetyChecks = safety,
    )
  }

  override suspend fun execute(plan: TransferPlan): String {
    val execId = "exec-${plan.id}"
    // Emit a couple of status updates deterministically
    updates.tryEmit(PlanStatus(planId = plan.id, stepIndex = 0, state = "STARTED"))
    updates.tryEmit(PlanStatus(planId = plan.id, stepIndex = plan.steps.size - 1, state = "COMPLETED"))
    return execId
  }

  override fun track(executionId: String): Flow<PlanStatus> = updates.asSharedFlow()
}

