package com.kevin.cryptotrader.data.funding

import com.kevin.cryptotrader.contracts.Account
import com.kevin.cryptotrader.contracts.AccountId
import com.kevin.cryptotrader.contracts.CostBreakdown
import com.kevin.cryptotrader.contracts.FundingService
import com.kevin.cryptotrader.contracts.Kind
import com.kevin.cryptotrader.contracts.PlanPrefs
import com.kevin.cryptotrader.contracts.PlanSafetyCheck
import com.kevin.cryptotrader.contracts.PlanStatus
import com.kevin.cryptotrader.contracts.SafetyStatus
import com.kevin.cryptotrader.contracts.TransferPlan
import com.kevin.cryptotrader.contracts.TransferStep
import com.kevin.cryptotrader.data.portfolio.AccountsRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.UUID

class FundingServiceImpl(
  private val accountsRepository: AccountsRepository,
  private val config: FundingConfig = FundingFixtures.default(),
) : FundingService {
  private val updates = MutableSharedFlow<PlanStatus>(extraBufferCapacity = 32)

  override suspend fun planTransfer(
    from: AccountId?,
    to: AccountId,
    asset: String,
    amount: Double,
    preferences: PlanPrefs,
  ): TransferPlan {
    require(amount > 0) { "Amount must be positive" }
    val targetAccount = accountsRepository.getAccount(to)
      ?: error("Target account $to not found")
    val sourceAccount = from?.let { accountsRepository.getAccount(it) }

    return if (sourceAccount == null) {
      val quotes = estimateRoutes(asset, amount, to, preferences)
      quotes.firstOrNull()?.plan ?: error("No viable funding route found for $asset → ${targetAccount.name}")
    } else {
      planMovement(sourceAccount, targetAccount, asset.uppercase(), amount)
    }
  }

  override suspend fun execute(plan: TransferPlan): String {
    val execId = "exec-${plan.id}-${System.currentTimeMillis()}"
    plan.steps.forEachIndexed { idx, _ ->
      updates.tryEmit(PlanStatus(planId = plan.id, stepIndex = idx, state = "STARTED"))
      delay(25)
      updates.tryEmit(PlanStatus(planId = plan.id, stepIndex = idx, state = "COMPLETED"))
    }
    updates.tryEmit(PlanStatus(planId = plan.id, stepIndex = plan.steps.lastIndex, state = "COMPLETED_PLAN"))
    return execId
  }

  override fun track(executionId: String): Flow<PlanStatus> = updates.asSharedFlow()

  data class PlanQuote(
    val plan: TransferPlan,
    val sourceAccountId: AccountId,
    val venueId: String,
  )

  fun estimateRoutes(
    asset: String,
    amount: Double,
    toAccountId: AccountId,
    preferences: PlanPrefs = PlanPrefs(),
  ): List<PlanQuote> {
    val targetAccount = accountsRepository.getAccount(toAccountId)
      ?: error("Target account $toAccountId not found")
    val cexAccounts = accountsRepository.stream().value.filter { it.kind == Kind.CEX }
    val preferred = preferences.preferExchange?.lowercase()
    val candidates = if (preferred != null) {
      cexAccounts.filter { it.venueId.equals(preferred, true) }
    } else {
      cexAccounts
    }

    val plans = candidates.mapNotNull { source ->
      val profile = config.tradingProfileFor(source.venueId) ?: return@mapNotNull null
      val price = config.quotePrice(source.venueId, asset) ?: return@mapNotNull null
      val networkSelection = selectWithdrawalNetwork(source, targetAccount, asset, profile) ?: return@mapNotNull null
      val address = resolveDestinationAddress(targetAccount, asset, networkSelection.network) ?: return@mapNotNull null
      val notional = price * amount
      val tradingFee = notional * profile.tradingFeeBps / 10_000.0
      val withdrawalFee = networkSelection.fee.feeUsd
      val networkFee = if (targetAccount.kind == Kind.WALLET) {
        config.walletProfile(targetAccount.id)?.gasFeeUsd?.get(networkSelection.network.uppercase()) ?: 0.0
      } else {
        0.0
      }
      val cost = CostBreakdown(
        notionalUsd = notional,
        tradingFeesUsd = tradingFee,
        withdrawalFeesUsd = withdrawalFee,
        networkFeesUsd = networkFee,
      )
      val totalFeesBps = if (cost.notionalUsd > 0) (cost.totalFeesUsd / cost.notionalUsd) * 10_000 else 0.0
      val maxFeesBps = preferences.maxFeesBps
      if (maxFeesBps != null && totalFeesBps > maxFeesBps) return@mapNotNull null
      val safety = buildSafetyChecks(
        allowlisted = address.whitelisted,
        twoTap = true,
      )
      val plan = TransferPlan(
        id = UUID.randomUUID().toString(),
        steps = listOf(
          TransferStep.BuyOnExchange(exchangeId = source.venueId, symbol = "$asset/USDT", qty = amount),
          TransferStep.Withdraw(
            exchangeId = source.venueId,
            asset = asset.uppercase(),
            network = networkSelection.network,
            address = address.address,
            amount = amount,
            memo = address.memo,
          ),
        ),
        totalCostUsd = cost.totalCostUsd,
        etaSeconds = networkSelection.fee.etaSeconds + 60,
        costBreakdown = cost,
        safetyChecks = safety,
      )
      PlanQuote(plan = plan, sourceAccountId = source.id, venueId = source.venueId)
    }

    return plans.sortedBy { it.plan.totalCostUsd }
  }

  private fun planMovement(
    from: Account,
    to: Account,
    asset: String,
    amount: Double,
  ): TransferPlan {
    val profile = config.tradingProfileFor(from.venueId)
    return when {
      from.kind == Kind.CEX && to.kind == Kind.CEX && from.venueId.equals(to.venueId, true) -> {
        val cost = CostBreakdown(notionalUsd = 0.0)
        TransferPlan(
          id = UUID.randomUUID().toString(),
          steps = listOf(
            TransferStep.TransferInternal(
              accountFrom = from.id,
              accountTo = to.id,
              asset = asset,
              amount = amount,
            ),
          ),
          totalCostUsd = cost.totalCostUsd,
          etaSeconds = 120,
          costBreakdown = cost,
          safetyChecks = listOf(
            PlanSafetyCheck(
              id = "internal_transfer_review",
              description = "Review internal transfer between ${from.name} accounts",
              status = SafetyStatus.PASSED,
              blocking = false,
            ),
          ),
        )
      }

      from.kind == Kind.CEX -> {
        requireNotNull(profile) { "No trading profile for ${from.venueId}" }
        val networkSelection = selectWithdrawalNetwork(from, to, asset, profile)
          ?: error("No shared network between ${from.name} and ${to.name} for $asset")
        ensureMinAmount(amount, networkSelection.fee)
        val destination = resolveDestinationAddress(to, asset, networkSelection.network)
          ?: error("No destination address for ${to.name} on ${networkSelection.network}")
        val networkFee = if (to.kind == Kind.WALLET) {
          config.walletProfile(to.id)?.gasFeeUsd?.get(networkSelection.network.uppercase()) ?: 0.0
        } else {
          0.0
        }
        val cost = CostBreakdown(
          notionalUsd = 0.0,
          tradingFeesUsd = 0.0,
          withdrawalFeesUsd = networkSelection.fee.feeUsd,
          networkFeesUsd = networkFee,
        )
        TransferPlan(
          id = UUID.randomUUID().toString(),
          steps = listOf(
            TransferStep.Withdraw(
              exchangeId = from.venueId,
              asset = asset,
              network = networkSelection.network,
              address = destination.address,
              amount = amount,
              memo = destination.memo,
            ),
          ),
          totalCostUsd = cost.totalCostUsd,
          etaSeconds = networkSelection.fee.etaSeconds,
          costBreakdown = cost,
          safetyChecks = buildSafetyChecks(destination.whitelisted, twoTap = true),
        )
      }

      from.kind == Kind.WALLET -> {
        val walletProfile = config.walletProfile(from.id)
          ?: error("Wallet profile missing for ${from.name}")
        val network = pickWalletNetwork(from, to)
          ?: error("Wallet ${from.name} does not support network to reach ${to.name}")
        val destination = resolveDestinationAddress(to, asset, network)
          ?: error("Destination address unavailable for ${to.name} on $network")
        val gasFee = walletProfile.gasFeeUsd[network.uppercase()] ?: 0.0
        val cost = CostBreakdown(
          notionalUsd = 0.0,
          tradingFeesUsd = 0.0,
          withdrawalFeesUsd = 0.0,
          networkFeesUsd = gasFee,
        )
        TransferPlan(
          id = UUID.randomUUID().toString(),
          steps = listOf(
            TransferStep.WalletTransfer(
              walletId = from.id,
              toAddress = destination.address,
              asset = asset,
              network = network,
              amount = amount,
              connector = walletProfile.connectorId,
              memo = destination.memo,
            ),
          ),
          totalCostUsd = cost.totalCostUsd,
          etaSeconds = 180,
          costBreakdown = cost,
          safetyChecks = listOf(
            PlanSafetyCheck(
              id = "wallet_confirm",
              description = "Approve transaction in connected wallet",
              status = SafetyStatus.PENDING,
              blocking = true,
            ),
          ),
        )
      }

      else -> error("Unsupported funding route from ${from.kind} to ${to.kind}")
    }
  }

  private fun pickWalletNetwork(from: Account, to: Account): String? {
    val fromNetworks = from.networks.map { it.uppercase() }.toSet()
    val toNetworks = to.networks.map { it.uppercase() }.toSet()
    val shared = fromNetworks.intersect(toNetworks)
    if (shared.isEmpty()) return null
    return shared.first()
  }

  private fun selectWithdrawalNetwork(
    from: Account,
    to: Account,
    asset: String,
    profile: TradingProfile,
  ): NetworkSelection? {
    val fromNetworks = from.networks.map { it.uppercase() }.toSet()
    val toNetworks = to.networks.map { it.uppercase() }.toSet()
    val shared = fromNetworks.intersect(toNetworks)
    val candidates = profile.withdrawalFees[asset.uppercase()] ?: return null
    return candidates
      .filter { fee -> shared.contains(fee.network.uppercase()) }
      .minByOrNull { it.feeUsd }
      ?.let { fee -> NetworkSelection(network = fee.network.uppercase(), fee = fee) }
  }

  private fun resolveDestinationAddress(account: Account, asset: String, network: String): DepositAddress? {
    return when (account.kind) {
      Kind.CEX -> config.depositAddress(account.id, asset, network)
      Kind.WALLET -> {
        val wallet = config.walletProfile(account.id) ?: return null
        val address = wallet.addresses[network.uppercase()] ?: return null
        DepositAddress(address = address, memo = null, whitelisted = true)
      }
    }
  }

  private fun buildSafetyChecks(allowlisted: Boolean, twoTap: Boolean): List<PlanSafetyCheck> {
    val checks = mutableListOf<PlanSafetyCheck>()
    checks += PlanSafetyCheck(
      id = "allowlist",
      description = if (allowlisted) "Destination address is allow-listed" else "Destination address requires review",
      status = if (allowlisted) SafetyStatus.PASSED else SafetyStatus.WARNING,
      blocking = !allowlisted,
    )
    if (twoTap) {
      checks += PlanSafetyCheck(
        id = "two_tap_confirm",
        description = "Two-step confirmation required",
        status = SafetyStatus.PENDING,
        blocking = false,
      )
    }
    return checks
  }

  private fun ensureMinAmount(amount: Double, fee: WithdrawalNetworkFee) {
    require(amount >= fee.minAmount) {
      "Amount $amount is below minimum withdrawal ${fee.minAmount} for network ${fee.network}"
    }
  }

  private data class NetworkSelection(
    val network: String,
    val fee: WithdrawalNetworkFee,
  )
}
