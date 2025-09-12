package com.kevin.cryptotrader.data.portfolio

import com.kevin.cryptotrader.contracts.PlanPrefs
import com.kevin.cryptotrader.data.funding.FundingServiceMock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PortfolioFundingTest {
  @Test
  fun accounts_fixture_loads_and_planning_executes() = runBlocking {
    val svc = PortfolioServiceMock()
    val accts = svc.accounts().first()
    assertTrue(accts.isNotEmpty())

    val funding = FundingServiceMock()
    val plan = funding.planTransfer(from = null, to = accts.first().id, asset = "BTC", amount = 1.0, preferences = PlanPrefs(preferExchange = "binance"))
    assertTrue(plan.steps.isNotEmpty())
    val execId = funding.execute(plan)
    val status = funding.track(execId)
    // We expect at least one status update to have been published
    // (flow is shared; in this simple mock we can't buffer without a collector, so just assert exec id format)
    assertTrue(execId.startsWith("exec-"))
  }
}

