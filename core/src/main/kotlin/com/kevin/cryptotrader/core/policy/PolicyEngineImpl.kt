package com.kevin.cryptotrader.core.policy

import com.kevin.cryptotrader.contracts.Intent
import com.kevin.cryptotrader.contracts.NetPlan
import com.kevin.cryptotrader.contracts.PolicyEngine
import com.kevin.cryptotrader.contracts.Position
import com.kevin.cryptotrader.contracts.Side

/**
 * Deterministic netting based on source priority and side cancellation.
 * - Keep intents from higher-priority sources when conflicts exist for the same symbol.
 * - Net opposing sides by qty if provided, else by notionalUsd when qty is null.
 * - Aggregate by (symbol, side) after netting.
 */
class PolicyEngineImpl(
  private val priority: List<String> = emptyList(),
) : PolicyEngine {

  override fun net(intents: List<Intent>, positions: List<Position>): NetPlan {
    if (intents.isEmpty()) return NetPlan(emptyList())

    // Partition intents by symbol
    val bySymbol = intents.groupBy { it.symbol }
    val result = mutableListOf<Intent>()

    for ((symbol, group) in bySymbol) {
      // When multiple sources conflict, keep highest-priority ones per side
      val bestBySide = Side.values().associateWith { side ->
        pickBest(group.filter { it.side == side })
      }

      val buy = bestBySide[Side.BUY]
      val sell = bestBySide[Side.SELL]

      val net = netOpposing(symbol, buy, sell)
      result.addAll(net)
    }

    // Aggregate by (symbol, side) to a single intent per pair
    val aggregated = result.groupBy { it.symbol to it.side }.map { (k, list) ->
      val first = list.first()
      val totalQty = list.mapNotNull { it.qty }.takeIf { it.isNotEmpty() }?.sum()
      val totalNotional = list.mapNotNull { it.notionalUsd }.takeIf { it.isNotEmpty() }?.sum()
      first.copy(id = first.id, qty = totalQty, notionalUsd = totalNotional)
    }

    return NetPlan(aggregated)
  }

  private fun pickBest(candidates: List<Intent>): Intent? {
    if (candidates.isEmpty()) return null
    if (priority.isEmpty()) return candidates.first() // stable
    return candidates.minBy { sourceRank(it.sourceId) }
  }

  private fun sourceRank(sourceId: String): Int {
    val idx = priority.indexOfFirst { prefix -> sourceId.startsWith(prefix) }
    return if (idx >= 0) idx else Int.MAX_VALUE
  }

  private fun netOpposing(symbol: String, buy: Intent?, sell: Intent?): List<Intent> {
    if (buy == null && sell == null) return emptyList()
    if (buy == null) return listOf(sell!!)
    if (sell == null) return listOf(buy)

    // Prefer qty-based netting when both have qty; else notionalUsd.
    val bq = buy.qty
    val sq = sell.qty
    if (bq != null && sq != null) {
      val diff = bq - sq
      return when {
        diff > 0 -> listOf(buy.copy(qty = diff))
        diff < 0 -> listOf(sell.copy(qty = -diff))
        else -> emptyList()
      }
    }

    val bn = buy.notionalUsd
    val sn = sell.notionalUsd
    if (bn != null && sn != null) {
      val diff = bn - sn
      return when {
        diff > 0 -> listOf(buy.copy(notionalUsd = diff))
        diff < 0 -> listOf(sell.copy(notionalUsd = -diff))
        else -> emptyList()
      }
    }

    // If mixed (one qty, one notional) keep higher-priority one verbatim
    val keep = listOfNotNull(buy, sell).minBy { sourceRank(it.sourceId) }
    return listOf(keep)
  }
}

