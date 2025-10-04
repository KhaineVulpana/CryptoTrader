package com.kevin.cryptotrader.data.funding

import com.kevin.cryptotrader.contracts.AccountId

data class WithdrawalNetworkFee(
  val network: String,
  val feeUsd: Double,
  val minAmount: Double,
  val etaSeconds: Long,
  val allowlistRequired: Boolean = true,
)

data class TradingProfile(
  val exchangeId: String,
  val tradingFeeBps: Double,
  val withdrawalFees: Map<String, List<WithdrawalNetworkFee>>,
  val internalTransferFeeBps: Double = 0.0,
)

data class DepositAddress(
  val address: String,
  val memo: String? = null,
  val whitelisted: Boolean = true,
)

data class WalletProfile(
  val accountId: AccountId,
  val connectorId: String,
  val addresses: Map<String, String>,
  val gasFeeUsd: Map<String, Double>,
)

data class FundingConfig(
  val tradingProfiles: Map<String, TradingProfile>,
  val depositAddresses: Map<AccountId, Map<String, Map<String, DepositAddress>>>,
  val walletProfiles: Map<AccountId, WalletProfile>,
  val priceQuotes: Map<String, Map<String, Double>>,
) {
  fun tradingProfileFor(venueId: String): TradingProfile? = tradingProfiles[venueId.lowercase()]

  fun walletProfile(accountId: AccountId): WalletProfile? = walletProfiles[accountId]

  fun depositAddress(accountId: AccountId, asset: String, network: String): DepositAddress? {
    return depositAddresses[accountId]
      ?.get(asset.uppercase())
      ?.get(network.uppercase())
  }

  fun quotePrice(venueId: String, asset: String): Double? =
    priceQuotes[venueId.lowercase()]?.get(asset.uppercase())
}

object FundingFixtures {
  fun default(): FundingConfig {
    val tradingProfiles = mapOf(
      "binance" to TradingProfile(
        exchangeId = "binance",
        tradingFeeBps = 8.0,
        withdrawalFees = mapOf(
          "USDT" to listOf(
            WithdrawalNetworkFee(network = "ETH", feeUsd = 5.0, minAmount = 10.0, etaSeconds = 600),
            WithdrawalNetworkFee(network = "TRX", feeUsd = 1.0, minAmount = 10.0, etaSeconds = 300),
          ),
          "BTC" to listOf(
            WithdrawalNetworkFee(network = "BTC", feeUsd = 8.0, minAmount = 0.001, etaSeconds = 1800),
          ),
        ),
        internalTransferFeeBps = 0.0,
      ),
      "coinbase" to TradingProfile(
        exchangeId = "coinbase",
        tradingFeeBps = 35.0,
        withdrawalFees = mapOf(
          "USDT" to listOf(
            WithdrawalNetworkFee(network = "ETH", feeUsd = 8.0, minAmount = 10.0, etaSeconds = 900),
          ),
          "BTC" to listOf(
            WithdrawalNetworkFee(network = "BTC", feeUsd = 10.0, minAmount = 0.001, etaSeconds = 2400),
          ),
        ),
        internalTransferFeeBps = 0.0,
      ),
    )

    val depositAddresses = mapOf(
      "acc_coinbase" to mapOf(
        "USDT" to mapOf(
          "ETH" to DepositAddress(address = "0xCOINBASEDEPOSIT", memo = null, whitelisted = true),
        ),
        "BTC" to mapOf(
          "BTC" to DepositAddress(address = "bc1-coinbase", memo = null, whitelisted = true),
        ),
      ),
      "acc_binance" to mapOf(
        "USDT" to mapOf(
          "ETH" to DepositAddress(address = "0xBINANCEUSDT", memo = null, whitelisted = true),
          "TRX" to DepositAddress(address = "TBinance", memo = null, whitelisted = true),
        ),
        "BTC" to mapOf(
          "BTC" to DepositAddress(address = "bc1-binance", memo = null, whitelisted = true),
        ),
      ),
    )

    val walletProfiles = mapOf(
      "acc_wallet_evm" to WalletProfile(
        accountId = "acc_wallet_evm",
        connectorId = "walletconnect",
        addresses = mapOf(
          "ETH" to "0xWALLETETH",
          "ARB" to "0xWALLETARB",
        ),
        gasFeeUsd = mapOf(
          "ETH" to 2.5,
          "ARB" to 0.5,
        ),
      ),
    )

    val priceQuotes = mapOf(
      "binance" to mapOf(
        "USDT" to 1.0,
        "BTC" to 30000.0,
      ),
      "coinbase" to mapOf(
        "USDT" to 1.01,
        "BTC" to 30100.0,
      ),
    )

    return FundingConfig(
      tradingProfiles = tradingProfiles,
      depositAddresses = depositAddresses,
      walletProfiles = walletProfiles,
      priceQuotes = priceQuotes,
    )
  }
}
