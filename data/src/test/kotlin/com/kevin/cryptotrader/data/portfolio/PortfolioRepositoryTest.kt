package com.kevin.cryptotrader.data.portfolio

import com.kevin.cryptotrader.contracts.Account
import com.kevin.cryptotrader.contracts.Holding
import com.kevin.cryptotrader.contracts.Kind
import com.kevin.cryptotrader.contracts.Position
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PortfolioRepositoryTest {
  private val accounts = listOf(
    Account(id = "acc_binance", kind = Kind.CEX, name = "Binance", venueId = "binance", networks = listOf("ETH", "TRX")),
    Account(id = "acc_coinbase", kind = Kind.CEX, name = "Coinbase", venueId = "coinbase", networks = listOf("ETH")),
    Account(id = "acc_wallet", kind = Kind.WALLET, name = "Wallet", venueId = "wallet:evm:0xabc", networks = listOf("ETH", "ARB")),
  )
  private val accountsRepo = AccountsRepository(accounts)
  private val balancesRepo = BalancesRepository()
  private val positionsRepo = PositionsRepository()
  private val portfolio = PortfolioRepository(accountsRepo, balancesRepo, positionsRepo)

  @Test
  fun aggregatesHoldingsAcrossAccounts() = runBlocking {
    balancesRepo.update(
      accountId = "acc_binance",
      newHoldings = listOf(
        Holding(accountId = "acc_binance", asset = "USDT", network = "ETH", free = 500.0, locked = 0.0, valuationUsd = 500.0),
        Holding(accountId = "acc_binance", asset = "BTC", network = null, free = 0.5, locked = 0.0, valuationUsd = 15000.0),
      ),
    )
    balancesRepo.update(
      accountId = "acc_coinbase",
      newHoldings = listOf(
        Holding(accountId = "acc_coinbase", asset = "USDT", network = "ETH", free = 200.0, locked = 0.0, valuationUsd = 200.0),
      ),
    )
    balancesRepo.update(
      accountId = "acc_wallet",
      newHoldings = listOf(
        Holding(accountId = "acc_wallet", asset = "USDT", network = "ETH", free = 100.0, locked = 0.0, valuationUsd = 100.0),
      ),
    )

    val holdings = portfolio.aggregatedHoldings.first()
    val usdtHolding = holdings.first { it.asset == "USDT" }
    assertEquals(800.0, usdtHolding.totalFree, 1e-6)
    assertEquals(800.0, usdtHolding.valuationUsd, 1e-6)
    assertEquals(3, usdtHolding.breakdown.size)
    assertEquals("ETH", usdtHolding.network)

    val portfolioValue = portfolio.portfolioValue()
    assertEquals(15800.0, portfolioValue, 1e-6)

    val netHoldings = portfolio.getNetHoldings("USDT")
    assertNotNull(netHoldings)
    assertEquals(800.0, netHoldings.totalFree, 1e-6)
  }

  @Test
  fun aggregatesPositions() = runBlocking {
    positionsRepo.update(
      accountId = "acc_binance",
      newPositions = listOf(
        Position(accountId = "acc_binance", symbol = "BTCUSDT", qty = 0.4, avgPrice = 28000.0, realizedPnl = 1000.0, unrealizedPnl = 500.0),
      ),
    )
    positionsRepo.update(
      accountId = "acc_coinbase",
      newPositions = listOf(
        Position(accountId = "acc_coinbase", symbol = "BTCUSDT", qty = 0.3, avgPrice = 29000.0, realizedPnl = 200.0, unrealizedPnl = 100.0),
      ),
    )

    val positions = portfolio.aggregatedPositions.first()
    assertEquals(1, positions.size)
    val btc = positions.first()
    assertEquals("BTCUSDT", btc.symbol)
    assertEquals(0.7, btc.netQty, 1e-6)
    assertTrue(btc.avgPrice in 28000.0..29000.0)

    val exposure = portfolio.getNetExposure("BTCUSDT")
    assertNotNull(exposure)
    assertEquals(0.7, exposure.netQty, 1e-6)
  }
}
