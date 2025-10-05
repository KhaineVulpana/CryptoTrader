package com.kevin.cryptotrader.data.funding

import com.kevin.cryptotrader.contracts.PlanPrefs
import com.kevin.cryptotrader.contracts.TransferStep
import com.kevin.cryptotrader.data.portfolio.AccountsRepository
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class FundingServiceImplTest {
  private val accountsRepository = AccountsRepository()
  private val funding = FundingServiceImpl(accountsRepository)

  @Test
  fun plansWithdrawFromCexToWalletWithSafetyChecks() = runBlocking {
    val plan = funding.planTransfer(
      from = "acc_binance",
      to = "acc_wallet_evm",
      asset = "USDT",
      amount = 100.0,
      preferences = PlanPrefs(),
    )

    assertEquals(1, plan.steps.size)
    val withdraw = plan.steps.first()
    assertIs<TransferStep.Withdraw>(withdraw)
    assertEquals("ETH", withdraw.network)
    assertTrue(plan.totalCostUsd > 0)
    assertEquals(plan.costBreakdown.totalCostUsd, plan.totalCostUsd, 1e-6)
    assertTrue(plan.safetyChecks.any { it.id == "two_tap_confirm" })
    assertTrue(plan.safetyChecks.any { it.id == "allowlist" })
  }

  @Test
  fun estimatesRoutesAndChoosesCheapest() {
    val quotes = funding.estimateRoutes(asset = "USDT", amount = 100.0, toAccountId = "acc_wallet_evm")
    assertTrue(quotes.size >= 1)
    val cheapest = quotes.first()
    assertTrue(quotes.zipWithNext().all { (a, b) -> a.plan.totalCostUsd <= b.plan.totalCostUsd })
    assertEquals("acc_binance", cheapest.sourceAccountId)
    assertEquals("binance", cheapest.venueId)
    assertTrue(cheapest.plan.steps.first() is TransferStep.BuyOnExchange)
  }

  @Test
  fun enforcesFeePreferences() {
    val quotes = funding.estimateRoutes(
      asset = "USDT",
      amount = 100.0,
      toAccountId = "acc_wallet_evm",
      preferences = PlanPrefs(maxFeesBps = 5),
    )
    // 5 bps is stricter than the default fees; expect no quotes
    assertTrue(quotes.isEmpty())
  }

  @Test
  fun plansWalletToExchangeTransfer() = runBlocking {
    val plan = funding.planTransfer(
      from = "acc_wallet_evm",
      to = "acc_coinbase",
      asset = "USDT",
      amount = 50.0,
      preferences = PlanPrefs(),
    )

    assertEquals(1, plan.steps.size)
    val step = plan.steps.first()
    assertIs<TransferStep.WalletTransfer>(step)
    assertEquals("ETH", step.network)
    assertEquals("walletconnect", step.connector)
  }

  @Test
  fun plansInternalTransferForSameVenue() = runBlocking {
    val internalAccount = com.kevin.cryptotrader.contracts.Account(
      id = "acc_binance_funding",
      kind = com.kevin.cryptotrader.contracts.Kind.CEX,
      name = "Binance Funding",
      venueId = "binance",
      networks = listOf("ETH", "TRX"),
    )
    accountsRepository.upsert(internalAccount)

    val plan = funding.planTransfer(
      from = "acc_binance",
      to = "acc_binance_funding",
      asset = "USDT",
      amount = 25.0,
      preferences = PlanPrefs(),
    )

    assertEquals(1, plan.steps.size)
    val internal = plan.steps.first()
    assertIs<TransferStep.TransferInternal>(internal)
    assertEquals("acc_binance", internal.accountFrom)
    assertEquals("acc_binance_funding", internal.accountTo)
    assertEquals(0.0, plan.totalCostUsd, 1e-6)
  }
}
