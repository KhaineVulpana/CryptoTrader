package com.kevin.cryptotrader.data.portfolio

import com.kevin.cryptotrader.contracts.Account
import com.kevin.cryptotrader.contracts.AccountId
import com.kevin.cryptotrader.contracts.Holding
import com.kevin.cryptotrader.contracts.Kind
import com.kevin.cryptotrader.contracts.Position
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update

class AccountsRepository(
  initialAccounts: List<Account> = FixturesLoader.loadAccounts(),
) {
  private val accounts = MutableStateFlow(initialAccounts.sortedBy { it.name })

  fun stream(): StateFlow<List<Account>> = accounts.asStateFlow()

  fun getAccount(id: AccountId): Account? = accounts.value.firstOrNull { it.id == id }

  fun upsert(account: Account) {
    accounts.update { current ->
      val existing = current.indexOfFirst { it.id == account.id }
      if (existing >= 0) {
        current.toMutableList().also { list ->
          list[existing] = account
          list.sortBy { it.name }
          return@update list
        }
      }
      (current + account).sortedBy { it.name }
    }
  }

  fun remove(accountId: AccountId) {
    accounts.update { current -> current.filterNot { it.id == accountId } }
  }
}

class BalancesRepository {
  private val holdings = MutableStateFlow<Map<AccountId, List<Holding>>>(emptyMap())

  fun stream(): StateFlow<Map<AccountId, List<Holding>>> = holdings.asStateFlow()

  fun update(accountId: AccountId, newHoldings: List<Holding>) {
    holdings.update { current ->
      val sanitized = newHoldings.map { holding ->
        holding.copy(network = holding.network?.uppercase())
      }
      current + (accountId to sanitized)
    }
  }

  fun getHoldings(accountId: AccountId): List<Holding> = holdings.value[accountId] ?: emptyList()
}

class PositionsRepository {
  private val positions = MutableStateFlow<Map<AccountId, List<Position>>>(emptyMap())

  fun stream(): StateFlow<Map<AccountId, List<Position>>> = positions.asStateFlow()

  fun update(accountId: AccountId, newPositions: List<Position>) {
    positions.update { current -> current + (accountId to newPositions) }
  }

  fun getPositions(accountId: AccountId): List<Position> = positions.value[accountId] ?: emptyList()
}

data class AggregatedHolding(
  val asset: String,
  val network: String?,
  val totalFree: Double,
  val totalLocked: Double,
  val valuationUsd: Double,
  val breakdown: List<HoldingBreakdown>,
)

data class HoldingBreakdown(
  val accountId: AccountId,
  val accountKind: Kind,
  val accountName: String,
  val free: Double,
  val locked: Double,
  val valuationUsd: Double,
  val network: String?,
)

data class AggregatedPosition(
  val symbol: String,
  val netQty: Double,
  val avgPrice: Double,
  val realizedPnl: Double,
  val unrealizedPnl: Double,
  val breakdown: List<PositionBreakdown>,
)

data class PositionBreakdown(
  val accountId: AccountId,
  val accountKind: Kind,
  val accountName: String,
  val qty: Double,
  val avgPrice: Double,
  val realizedPnl: Double,
  val unrealizedPnl: Double,
)

class PortfolioRepository(
  private val accountsRepository: AccountsRepository,
  private val balancesRepository: BalancesRepository,
  private val positionsRepository: PositionsRepository,
) {

  val aggregatedHoldings: Flow<List<AggregatedHolding>> = combine(
    accountsRepository.stream(),
    balancesRepository.stream(),
  ) { accounts, holdings ->
    val byAsset = holdings.values.flatten().groupBy { it.asset.uppercase() to (it.network?.uppercase()) }
    byAsset.map { (key, items) ->
      val (asset, network) = key
      aggregateHolding(asset, network, items, accounts)
    }.sortedWith(compareBy<AggregatedHolding> { it.asset }.thenBy { it.network ?: "" })
  }

  val aggregatedPositions: Flow<List<AggregatedPosition>> = combine(
    accountsRepository.stream(),
    positionsRepository.stream(),
  ) { accounts, positions ->
    val bySymbol = positions.values.flatten().groupBy { it.symbol.uppercase() }
    bySymbol.map { (symbol, items) ->
      aggregatePosition(symbol, items, accounts)
    }.sortedBy { it.symbol }
  }

  fun portfolioValue(): Double {
    return balancesRepository.stream().value.values.flatten().sumOf { it.valuationUsd }
  }

  fun getNetHoldings(asset: String, network: String? = null): AggregatedHolding? {
    val accounts = accountsRepository.stream().value
    val items = balancesRepository.stream().value.values
      .flatten()
      .filter { it.asset.equals(asset, true) && networkMatches(it.network, network) }
    if (items.isEmpty()) return null
    return aggregateHolding(asset.uppercase(), network?.uppercase(), items, accounts)
  }

  fun getNetExposure(symbol: String): AggregatedPosition? {
    val accounts = accountsRepository.stream().value
    val positions = positionsRepository.stream().value.values.flatten().filter { it.symbol.equals(symbol, true) }
    if (positions.isEmpty()) return null
    return aggregatePosition(symbol.uppercase(), positions, accounts)
  }

  private fun aggregateHolding(
    asset: String,
    network: String?,
    holdings: List<Holding>,
    accounts: List<Account>,
  ): AggregatedHolding {
    val breakdown = holdings.map { holding ->
      val account = accounts.firstOrNull { it.id == holding.accountId }
      HoldingBreakdown(
        accountId = holding.accountId,
        accountKind = account?.kind ?: Kind.CEX,
        accountName = account?.name ?: holding.accountId,
        free = holding.free,
        locked = holding.locked,
        valuationUsd = holding.valuationUsd,
        network = holding.network,
      )
    }
    val totalFree = holdings.sumOf { it.free }
    val totalLocked = holdings.sumOf { it.locked }
    val valuationUsd = holdings.sumOf { it.valuationUsd }
    return AggregatedHolding(
      asset = asset,
      network = network,
      totalFree = totalFree,
      totalLocked = totalLocked,
      valuationUsd = valuationUsd,
      breakdown = breakdown,
    )
  }

  private fun aggregatePosition(
    symbol: String,
    positions: List<Position>,
    accounts: List<Account>,
  ): AggregatedPosition {
    val breakdown = positions.map { position ->
      val account = accounts.firstOrNull { it.id == position.accountId }
      PositionBreakdown(
        accountId = position.accountId,
        accountKind = account?.kind ?: Kind.CEX,
        accountName = account?.name ?: position.accountId,
        qty = position.qty,
        avgPrice = position.avgPrice,
        realizedPnl = position.realizedPnl,
        unrealizedPnl = position.unrealizedPnl,
      )
    }
    val netQty = positions.sumOf { it.qty }
    val weightedAvgPrice = if (netQty == 0.0) 0.0 else positions.sumOf { it.qty * it.avgPrice } / netQty
    val realized = positions.sumOf { it.realizedPnl }
    val unrealized = positions.sumOf { it.unrealizedPnl }
    return AggregatedPosition(
      symbol = symbol,
      netQty = netQty,
      avgPrice = weightedAvgPrice,
      realizedPnl = realized,
      unrealizedPnl = unrealized,
      breakdown = breakdown,
    )
  }

  private fun networkMatches(source: String?, target: String?): Boolean {
    if (target == null) return true
    return source?.equals(target, ignoreCase = true) == true
  }
}
