package com.kevin.cryptotrader.core.policy

import com.kevin.cryptotrader.contracts.Intent
import com.kevin.cryptotrader.contracts.NetPlan
import com.kevin.cryptotrader.contracts.PolicyConfig
import com.kevin.cryptotrader.contracts.PolicyEngine
import com.kevin.cryptotrader.contracts.PolicyMode
import com.kevin.cryptotrader.contracts.Position
import com.kevin.cryptotrader.contracts.Side
import kotlin.math.abs

/**
 * Policy engine supporting multiple conflict modes:
 * - Priority: deterministic selection of intents from higher-ranked sources.
 * - Netting: sums same-side intents before offsetting opposing directions.
 * - Portfolio target: blends strategy targets with weights and current positions.
 * - Vote: applies weighted voting with configurable thresholds.
 */
class PolicyEngineImpl(
  private val config: PolicyConfig = PolicyConfig(),
) : PolicyEngine {

  override fun net(intents: List<Intent>, positions: List<Position>): NetPlan {
    if (intents.isEmpty()) return NetPlan(emptyList())

    val weightedIntents = intents.filter { config.weightFor(it.sourceId) > 0.0 }
    if (weightedIntents.isEmpty()) return NetPlan(emptyList())

    val planIntents = when (config.mode) {
      PolicyMode.PRIORITY -> priorityNet(weightedIntents)
      PolicyMode.NETTING -> netting(weightedIntents)
      PolicyMode.PORTFOLIO_TARGET -> portfolioTarget(weightedIntents, positions)
      PolicyMode.VOTE -> vote(weightedIntents, positions)
    }

    return normalize(planIntents)
  }

  private fun pickBest(candidates: List<Intent>): Intent? {
    if (candidates.isEmpty()) return null
    if (config.priority.isEmpty()) return candidates.first()
    return candidates.minBy { sourceRank(it.sourceId) }
  }

  private fun sourceRank(sourceId: String): Int {
    val idx = config.priority.indexOfFirst { prefix -> sourceId.startsWith(prefix) }
    return if (idx >= 0) idx else Int.MAX_VALUE
  }

  private fun priorityNet(intents: List<Intent>): List<Intent> {
    val result = mutableListOf<Intent>()
    intents.groupBy { it.symbol }.forEach { (symbol, group) ->
      val buys = pickBest(group.filter { it.side == Side.BUY })
      val sells = pickBest(group.filter { it.side == Side.SELL })
      result.addAll(netOpposing(symbol, buys, sells))
    }
    return result
  }

  private fun netting(intents: List<Intent>): List<Intent> {
    val result = mutableListOf<Intent>()
    intents.groupBy { it.symbol }.forEach { (symbol, group) ->
      val buys = aggregateSide(symbol, Side.BUY, group.filter { it.side == Side.BUY })
      val sells = aggregateSide(symbol, Side.SELL, group.filter { it.side == Side.SELL })
      result.addAll(netOpposing(symbol, buys, sells))
    }
    return result
  }

  private fun portfolioTarget(intents: List<Intent>, positions: List<Position>): List<Intent> {
    if (intents.isEmpty()) return emptyList()
    val currentBySymbol = positions.groupBy { it.symbol }.mapValues { entry ->
      entry.value.sumOf { it.qty }
    }
    val results = mutableListOf<Intent>()

    intents.groupBy { it.symbol }.forEach { (symbol, group) ->
      val contributions = group.mapNotNull { intent ->
        val target = extractTargetQty(intent)
        val weight = config.weightFor(intent.sourceId)
        if (target != null && weight > 0.0) WeightedTarget(target, weight, intent) else null
      }
      if (contributions.isEmpty()) return@forEach
      val weightSum = contributions.sumOf { it.weight }
      if (weightSum <= 0.0) return@forEach
      val weightedTarget = contributions.sumOf { it.target * it.weight } / weightSum
      val currentQty = currentBySymbol[symbol] ?: 0.0
      val delta = weightedTarget - currentQty
      if (abs(delta) <= EPS) return@forEach
      val side = if (delta > 0) Side.BUY else Side.SELL
      val qty = abs(delta)
      val priceHint = contributions.mapNotNull { it.intent.priceHint }.firstOrNull()
      results.add(
        Intent(
          id = "portfolio-${symbol.lowercase()}",
          sourceId = "policy.portfolio",
          kind = "portfolio_target",
          symbol = symbol,
          side = side,
          qty = qty,
          notionalUsd = priceHint?.let { it * qty },
          priceHint = priceHint,
          meta = mergeMeta(contributions.map { it.intent }),
        ),
      )
    }

    return results
  }

  private fun vote(intents: List<Intent>, positions: List<Position>): List<Intent> {
    if (intents.isEmpty()) return emptyList()
    val currentBySymbol = positions.groupBy { it.symbol }.mapValues { entry ->
      entry.value.sumOf { it.qty }
    }
    val results = mutableListOf<Intent>()

    intents.groupBy { it.symbol }.forEach { (symbol, group) ->
      val contributions = group.map { WeightedIntent(config.weightFor(it.sourceId), it) }
        .filter { it.weight > 0.0 }
      if (contributions.isEmpty()) return@forEach
      val totalWeight = contributions.sumOf { it.weight }
      if (totalWeight <= 0.0) return@forEach

      val buyWeight = contributions.filter { it.intent.side == Side.BUY }.sumOf { it.weight }
      val sellWeight = contributions.filter { it.intent.side == Side.SELL }.sumOf { it.weight }
      val thresholdWeight = totalWeight * config.voteThreshold.coerceIn(0.0, 1.0)

      val winner = when {
        buyWeight >= thresholdWeight && buyWeight > sellWeight -> Side.BUY
        sellWeight >= thresholdWeight && sellWeight > buyWeight -> Side.SELL
        else -> null
      }
      if (winner == null) return@forEach

      val currentQty = currentBySymbol[symbol] ?: 0.0
      val delta = computeVoteDelta(contributions, currentQty) ?: return@forEach
      if (winner == Side.BUY && delta <= EPS) return@forEach
      if (winner == Side.SELL && delta >= -EPS) return@forEach
      val qty = abs(delta)
      if (qty <= EPS) return@forEach

      val priceHint = contributions.mapNotNull { it.intent.priceHint }.firstOrNull()
      results.add(
        Intent(
          id = "vote-${symbol.lowercase()}",
          sourceId = "policy.vote",
          kind = "vote",
          symbol = symbol,
          side = winner,
          qty = qty,
          notionalUsd = priceHint?.let { it * qty },
          priceHint = priceHint,
          meta = mergeMeta(contributions.map { it.intent }),
        ),
      )
    }

    return results
  }

  private fun computeVoteDelta(contributions: List<WeightedIntent>, currentQty: Double): Double? {
    val targets = contributions.mapNotNull { wi ->
      extractTargetQty(wi.intent)?.let { wi.weight to it }
    }
    return if (targets.isNotEmpty()) {
      val weightSum = targets.sumOf { it.first }
      if (weightSum <= 0.0) {
        null
      } else {
        (targets.sumOf { it.first * it.second } / weightSum) - currentQty
      }
    } else {
      val weightSum = contributions.sumOf { it.weight }
      if (weightSum <= 0.0) return null
      contributions.sumOf { wi ->
        val qty = resolveQty(wi.intent) ?: 0.0
        val signed = if (wi.intent.side == Side.BUY) qty else -qty
        signed * wi.weight
      } / weightSum
    }
  }

  private fun aggregateSide(symbol: String, side: Side, intents: List<Intent>): Intent? {
    if (intents.isEmpty()) return null
    val template = intents.first()
    val totalQty = sumQty(intents)
    val totalNotional = sumNotional(intents)
    val priceHint = template.priceHint ?: intents.mapNotNull { it.priceHint }.firstOrNull()
    return Intent(
      id = "agg-${symbol.lowercase()}-${side.name.lowercase()}",
      sourceId = template.sourceId,
      kind = template.kind,
      symbol = symbol,
      side = side,
      qty = totalQty,
      notionalUsd = totalNotional,
      priceHint = priceHint,
      meta = template.meta,
    )
  }

  private fun sumQty(intents: List<Intent>): Double? {
    val values = intents.mapNotNull { it.qty }
    return if (values.isEmpty()) null else values.sum()
  }

  private fun sumNotional(intents: List<Intent>): Double? {
    val values = intents.mapNotNull { it.notionalUsd }
    return if (values.isEmpty()) null else values.sum()
  }

  private fun normalize(intents: List<Intent>): NetPlan {
    if (intents.isEmpty()) return NetPlan(emptyList())
    val aggregated = intents.groupBy { it.symbol to it.side }.mapNotNull { (_, group) ->
      val first = group.first()
      val qty = sumQty(group)
      val notional = sumNotional(group)
      val effectiveQty = qty ?: notional?.let { n ->
        val price = first.priceHint
        if (price != null && price > 0) n / price else null
      }
      if ((effectiveQty ?: 0.0) <= EPS && (notional ?: 0.0) <= EPS) {
        null
      } else {
        first.copy(qty = qty ?: effectiveQty, notionalUsd = notional)
      }
    }
    return NetPlan(aggregated)
  }

  private fun netOpposing(symbol: String, buy: Intent?, sell: Intent?): List<Intent> {
    if (buy == null && sell == null) return emptyList()
    if (buy == null) return listOf(sell!!)
    if (sell == null) return listOf(buy)

    val bq = resolveQty(buy)
    val sq = resolveQty(sell)
    if (bq != null && sq != null) {
      val diff = bq - sq
      return when {
        diff > EPS -> listOf(adjustQty(buy, diff))
        diff < -EPS -> listOf(adjustQty(sell, -diff))
        else -> emptyList()
      }
    }

    val bn = resolveNotional(buy)
    val sn = resolveNotional(sell)
    if (bn != null && sn != null) {
      val diff = bn - sn
      return when {
        diff > EPS -> listOf(adjustNotional(buy, diff))
        diff < -EPS -> listOf(adjustNotional(sell, -diff))
        else -> emptyList()
      }
    }

    // If mixed (one qty, one notional) keep higher-priority one verbatim
    val keep = listOfNotNull(buy, sell).minBy { sourceRank(it.sourceId) }
    return listOf(keep)
  }

  private fun resolveQty(intent: Intent): Double? {
    intent.qty?.let { return it }
    val notional = intent.notionalUsd
    val price = intent.priceHint
    if (notional != null && price != null && price > 0) return notional / price
    return null
  }

  private fun resolveNotional(intent: Intent): Double? {
    intent.notionalUsd?.let { return it }
    val qty = intent.qty
    val price = intent.priceHint
    if (qty != null && price != null) return qty * price
    return null
  }

  private fun adjustQty(intent: Intent, qty: Double): Intent {
    val price = intent.priceHint
    val notional = if (price != null) qty * price else intent.notionalUsd
    return intent.copy(qty = qty, notionalUsd = notional)
  }

  private fun adjustNotional(intent: Intent, notional: Double): Intent {
    val price = intent.priceHint
    val qty = if (price != null && price > 0) notional / price else intent.qty
    return intent.copy(notionalUsd = notional, qty = qty)
  }

  private fun extractTargetQty(intent: Intent): Double? {
    val meta = intent.meta
    val keys = listOf(
      "target_qty",
      "targetQty",
      "target_position",
      "portfolio_target_qty",
    )
    val direct = keys.asSequence().mapNotNull { meta[it]?.toDoubleOrNull() }.firstOrNull()
    if (direct != null) return direct
    val targetNotional = meta["target_notional"]?.toDoubleOrNull()
      ?: meta["target_notional_usd"]?.toDoubleOrNull()
    val price = intent.priceHint
    if (targetNotional != null && price != null && price > 0) return targetNotional / price
    return null
  }

  private fun mergeMeta(intents: List<Intent>): Map<String, String> {
    if (intents.isEmpty()) return emptyMap()
    val merged = linkedMapOf<String, String>()
    intents.forEach { merged.putAll(it.meta) }
    return merged
  }

  private data class WeightedTarget(val target: Double, val weight: Double, val intent: Intent)

  private data class WeightedIntent(val weight: Double, val intent: Intent)

  companion object {
    private const val EPS = 1e-9
  }
}

