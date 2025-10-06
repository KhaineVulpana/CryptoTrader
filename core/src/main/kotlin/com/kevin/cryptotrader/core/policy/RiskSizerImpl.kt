package com.kevin.cryptotrader.core.policy

import com.kevin.cryptotrader.contracts.AccountSnapshot
import com.kevin.cryptotrader.contracts.NetPlan
import com.kevin.cryptotrader.contracts.Order
import com.kevin.cryptotrader.contracts.OrderType
import com.kevin.cryptotrader.contracts.RiskEvent
import com.kevin.cryptotrader.contracts.RiskResult
import com.kevin.cryptotrader.contracts.RiskSizer
import com.kevin.cryptotrader.contracts.Side
import com.kevin.cryptotrader.contracts.StopKind
import com.kevin.cryptotrader.contracts.TIF
import java.util.concurrent.TimeUnit
import kotlin.math.max

/**
 * Risk sizing with deterministic outcomes across:
 * - Fixed % of equity, ATR-scaled sizing, volatility targeting, and fixed-notional fallbacks.
 * - ATR, trailing, and time-based stops emitting both orders and ledger-friendly events.
 * - Global portfolio caps and symbol-level caps.
 * - Correlation guard to prevent overlapping exposures in the same beta group.
 */
data class CorrelationGuardConfig(
  val groups: List<Set<String>> = emptyList(),
  val maxActivePerGroup: Int = Int.MAX_VALUE,
)

data class RiskSizerConfig(
  val defaultRiskPct: Double = 0.01,
  val maxPortfolioRiskPct: Double = 1.0,
  val perSymbolCaps: Map<String, Double> = emptyMap(),
  val correlationGuard: CorrelationGuardConfig = CorrelationGuardConfig(),
  val clockMs: () -> Long = System::currentTimeMillis,
)

class RiskSizerImpl(
  private val config: RiskSizerConfig = RiskSizerConfig(),
) : RiskSizer {

  override fun size(plan: NetPlan, account: AccountSnapshot): RiskResult {
    val equity = account.equityUsd
    if (plan.intents.isEmpty() || equity <= 0.0) return RiskResult(emptyList())

    val entries = mutableListOf<Order>()
    val stops = mutableListOf<Order>()
    val events = mutableListOf<RiskEvent>()

    val sortedIntents = plan.intents.sortedByDescending { resolveNotional(it) ?: 0.0 }
    val groupUsage = mutableMapOf<Int, Int>()
    val maxPortfolioUsd = max(0.0, equity * config.maxPortfolioRiskPct)
    var usedUsd = 0.0

    sortedIntents.forEach { intent ->
      val price = resolvePrice(intent) ?: return@forEach
      val meta = parseRiskMeta(intent)
      val baseQty = computeQty(intent, price, equity, meta) ?: return@forEach
      var qty = baseQty
      if (qty <= EPS) return@forEach

      var notional = qty * price

      config.perSymbolCaps[intent.symbol]?.takeIf { it > 0 }?.let { capPct ->
        val capUsd = equity * capPct
        if (capUsd <= EPS) return@forEach
        if (notional > capUsd) {
          qty = capUsd / price
          notional = qty * price
        }
      }

      if (maxPortfolioUsd > EPS) {
        val remaining = maxPortfolioUsd - usedUsd
        if (remaining <= EPS) return@forEach
        if (notional > remaining) {
          qty = remaining / price
          notional = qty * price
        }
      }

      if (qty <= EPS || notional <= EPS) return@forEach

      if (!passesCorrelationGuard(intent.symbol, groupUsage)) return@forEach

      entries.add(
        Order(
          clientOrderId = "ord-${intent.id}",
          symbol = intent.symbol,
          side = intent.side,
          type = OrderType.MARKET,
          qty = qty,
          price = null,
        ),
      )
      usedUsd += notional

      val stopArtifacts = buildStops(intent, qty, price, meta)
      stops.addAll(stopArtifacts.orders)
      events.addAll(stopArtifacts.events)
    }

    return RiskResult(entries, stops, events)
  }

  private fun resolvePrice(intent: com.kevin.cryptotrader.contracts.Intent): Double? {
    val price = intent.priceHint
    if (price != null && price > 0) return price
    val qty = intent.qty
    val notional = intent.notionalUsd
    if (qty != null && qty > 0 && notional != null && notional > 0) return notional / qty
    return null
  }

  private fun resolveNotional(intent: com.kevin.cryptotrader.contracts.Intent): Double? {
    intent.notionalUsd?.let { return it }
    val price = resolvePrice(intent)
    val qty = intent.qty
    if (price != null && qty != null) return qty * price
    return null
  }

  private fun computeQty(
    intent: com.kevin.cryptotrader.contracts.Intent,
    price: Double,
    equity: Double,
    meta: RiskMeta,
  ): Double? {
    val qty = when (meta.mode) {
      RiskMode.FIXED_NOTIONAL -> intent.qty ?: meta.notionalUsd?.let { it / price }
        ?: resolveNotional(intent)?.let { it / price }
      RiskMode.FIXED_PCT -> {
        val pct = (meta.riskPct ?: config.defaultRiskPct).coerceAtLeast(0.0)
        if (pct <= 0.0) return null
        val capital = equity * pct
        val stopDistance = stopDistance(meta)
        val denom = if (stopDistance != null && stopDistance > EPS) stopDistance else price
        if (denom <= EPS) return null
        capital / denom
      }
      RiskMode.ATR -> {
        val atr = meta.atr ?: return null
        val mult = meta.atrMultiplier ?: 1.0
        val distance = atr * mult
        if (distance <= EPS) return null
        val pct = (meta.riskPct ?: config.defaultRiskPct).coerceAtLeast(0.0)
        if (pct <= 0.0) return null
        val capital = equity * pct
        capital / distance
      }
      RiskMode.VOL_TARGET -> {
        val vol = meta.volatility ?: return null
        val target = meta.targetVolatility ?: return null
        if (vol <= EPS || target <= 0.0) return null
        val pct = (meta.riskPct ?: config.defaultRiskPct).coerceAtLeast(0.0)
        if (pct <= 0.0) return null
        val exposureUsd = equity * pct * (target / vol)
        if (price <= EPS) return null
        exposureUsd / price
      }
    }
    return qty?.coerceAtLeast(0.0)
  }

  private fun stopDistance(meta: RiskMeta): Double? {
    val atr = meta.atr
    val mult = meta.stopAtrMultiplier ?: meta.atrMultiplier
    return if (atr != null && mult != null) atr * mult else null
  }

  private fun passesCorrelationGuard(symbol: String, usage: MutableMap<Int, Int>): Boolean {
    if (config.correlationGuard.groups.isEmpty()) return true
    var allowed = true
    config.correlationGuard.groups.forEachIndexed { index, group ->
      if (!group.contains(symbol)) return@forEachIndexed
      val count = usage.getOrDefault(index, 0)
      if (count >= config.correlationGuard.maxActivePerGroup) {
        allowed = false
        return@forEachIndexed
      }
    }
    if (!allowed) return false
    config.correlationGuard.groups.forEachIndexed { index, group ->
      if (group.contains(symbol)) {
        usage[index] = usage.getOrDefault(index, 0) + 1
      }
    }
    return true
  }

  private fun buildStops(
    intent: com.kevin.cryptotrader.contracts.Intent,
    qty: Double,
    price: Double,
    meta: RiskMeta,
  ): StopArtifacts {
    val orders = mutableListOf<Order>()
    val events = mutableListOf<RiskEvent>()
    val stopDistance = stopDistance(meta)
    val opposite = if (intent.side == Side.BUY) Side.SELL else Side.BUY

    if (stopDistance != null && stopDistance > EPS) {
      val stopPrice = if (intent.side == Side.BUY) price - stopDistance else price + stopDistance
      if (stopPrice > EPS) {
        orders.add(
          Order(
            clientOrderId = "stop-${intent.id}",
            symbol = intent.symbol,
            side = opposite,
            type = OrderType.STOP,
            qty = qty,
            price = null,
            stopPrice = stopPrice,
            tif = TIF.GTC,
          ),
        )
        events.add(
          RiskEvent.StopSet(
            intentId = intent.id,
            symbol = intent.symbol,
            side = opposite,
            kind = StopKind.ATR,
            stopPrice = stopPrice,
          ),
        )
      }
    }

    meta.trailingPct?.takeIf { it > 0 }?.let { pct ->
      events.add(
        RiskEvent.StopSet(
          intentId = intent.id,
          symbol = intent.symbol,
          side = opposite,
          kind = StopKind.TRAILING,
          trailingPct = pct,
        ),
      )
    }

    meta.timeStopSec?.takeIf { it > 0 }?.let { seconds ->
      val expiresAt = config.clockMs().plus(TimeUnit.SECONDS.toMillis(seconds))
      events.add(
        RiskEvent.StopSet(
          intentId = intent.id,
          symbol = intent.symbol,
          side = opposite,
          kind = StopKind.TIME,
          expiresAt = expiresAt,
        ),
      )
    }

    return StopArtifacts(orders, events)
  }

  private fun parseRiskMeta(intent: com.kevin.cryptotrader.contracts.Intent): RiskMeta {
    val meta = intent.meta
    val modeKey = meta["risk.mode"]?.lowercase()
    val mode = when (modeKey) {
      "fixed_pct", "fixed%", "fixed-percent" -> RiskMode.FIXED_PCT
      "atr" -> RiskMode.ATR
      "vol_target", "volatility", "vol" -> RiskMode.VOL_TARGET
      else -> when {
        meta.containsKey("risk.pct") -> RiskMode.FIXED_PCT
        meta.containsKey("risk.atr") || meta.containsKey("atr") -> RiskMode.ATR
        meta.containsKey("risk.target_vol") || meta.containsKey("target_volatility") -> RiskMode.VOL_TARGET
        else -> RiskMode.FIXED_NOTIONAL
      }
    }

    val riskPct = meta["risk.pct"]?.toDoubleOrNull()
    val notional = meta["risk.notional"]?.toDoubleOrNull()
    val atr = meta["risk.atr"]?.toDoubleOrNull() ?: meta["atr"]?.toDoubleOrNull()
    val atrMult = meta["risk.atr_mult"]?.toDoubleOrNull() ?: meta["atr_multiplier"]?.toDoubleOrNull()
    val stopAtrMult = meta["stop.atr_mult"]?.toDoubleOrNull() ?: meta["stop.atr_multiplier"]?.toDoubleOrNull()
    val trailingPct = meta["stop.trailing_pct"]?.toDoubleOrNull() ?: meta["trailing_pct"]?.toDoubleOrNull()
    val timeStopSec = meta["stop.time_sec"]?.toLongOrNull()
      ?: meta["stop.time_minutes"]?.toLongOrNull()?.let { it * 60 }
      ?: meta["time_stop_sec"]?.toLongOrNull()
    val volatility = meta["risk.vol"]?.toDoubleOrNull() ?: meta["volatility"]?.toDoubleOrNull()
    val targetVolatility = meta["risk.target_vol"]?.toDoubleOrNull()
      ?: meta["target_volatility"]?.toDoubleOrNull()

    return RiskMeta(
      mode = mode,
      riskPct = riskPct,
      notionalUsd = notional,
      atr = atr,
      atrMultiplier = atrMult,
      stopAtrMultiplier = stopAtrMult,
      trailingPct = trailingPct,
      timeStopSec = timeStopSec,
      volatility = volatility,
      targetVolatility = targetVolatility,
    )
  }

  private data class StopArtifacts(
    val orders: List<Order>,
    val events: List<RiskEvent>,
  )

  private data class RiskMeta(
    val mode: RiskMode,
    val riskPct: Double?,
    val notionalUsd: Double?,
    val atr: Double?,
    val atrMultiplier: Double?,
    val stopAtrMultiplier: Double?,
    val trailingPct: Double?,
    val timeStopSec: Long?,
    val volatility: Double?,
    val targetVolatility: Double?,
  )

  private enum class RiskMode { FIXED_NOTIONAL, FIXED_PCT, ATR, VOL_TARGET }

  companion object {
    private const val EPS = 1e-9
  }
}

