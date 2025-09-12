package com.kevin.cryptotrader.contracts

enum class Side { BUY, SELL }
enum class OrderType { MARKET, LIMIT, STOP, STOP_LIMIT }
enum class TIF { GTC, IOC, FOK, DAY }
enum class Interval { M1, M5, M15, M30, H1, H4, D1 }

typealias AccountId = String

data class Candle(val ts: Long, val open: Double, val high: Double, val low: Double, val close: Double, val volume: Double, val interval: Interval, val symbol: String, val source: String)
data class Ticker(val ts: Long, val symbol: String, val price: Double)
data class Trade(val ts: Long, val symbol: String, val price: Double, val qty: Double, val isBuy: Boolean)
data class OrderBookDelta(val ts: Long, val symbol: String, val bids: List<Pair<Double, Double>>, val asks: List<Pair<Double, Double>>)

data class Intent(val id: String, val sourceId: String, val kind: String, val symbol: String, val side: Side, val notionalUsd: Double? = null, val qty: Double? = null, val priceHint: Double? = null, val meta: Map<String,String> = emptyMap())
data class Order(val clientOrderId: String, val symbol: String, val side: Side, val type: OrderType, val qty: Double, val price: Double? = null, val stopPrice: Double? = null, val tif: TIF = TIF.GTC, val ts: Long = System.currentTimeMillis())
data class Fill(val orderId: String, val qty: Double, val price: Double, val ts: Long)

sealed class BrokerEvent {
  data class Accepted(val orderId: String, val order: Order): BrokerEvent()
  data class PartialFill(val orderId: String, val fill: Fill): BrokerEvent()
  data class Filled(val orderId: String, val fill: Fill): BrokerEvent()
  data class Canceled(val orderId: String): BrokerEvent()
  data class Rejected(val orderId: String, val reason: String): BrokerEvent()
}

data class AccountSnapshot(val equityUsd: Double, val balances: Map<String, Double>)

data class Account(val id: AccountId, val kind: Kind, val name: String, val venueId: String, val networks: List<String>)
enum class Kind { CEX, WALLET }
data class Holding(val accountId: AccountId, val asset: String, val network: String?, val free: Double, val locked: Double, val valuationUsd: Double)
data class Position(val accountId: AccountId, val symbol: String, val qty: Double, val avgPrice: Double, val realizedPnl: Double, val unrealizedPnl: Double)

data class TransferPlan(val id: String, val steps: List<TransferStep>, val totalCostUsd: Double, val etaSeconds: Long)
sealed class TransferStep {
  data class BuyOnExchange(val exchangeId: String, val symbol: String, val qty: Double): TransferStep()
  data class Withdraw(val exchangeId: String, val asset: String, val network: String, val address: String, val amount: Double, val memo: String? = null): TransferStep()
  data class OnChainSwap(val aggregator: String, val fromAsset: String, val toAsset: String, val amount: Double, val network: String): TransferStep()
  data class TransferInternal(val accountFrom: AccountId, val accountTo: AccountId, val asset: String, val amount: Double): TransferStep()
}

data class PlanPrefs(val preferExchange: String? = null, val maxFeesBps: Int? = null)
data class PlanStatus(val planId: String, val stepIndex: Int, val state: String, val error: String? = null)

data class AutomationDef(val id: String, val version: Int, val graphJson: String)
