package com.kevin.cryptotrader.runtime.execution

import com.kevin.cryptotrader.contracts.Intent
import com.kevin.cryptotrader.contracts.NetPlan
import com.kevin.cryptotrader.contracts.PolicyEngine
import com.kevin.cryptotrader.contracts.Position
import com.kevin.cryptotrader.contracts.Side
import java.util.UUID
import kotlin.math.absoluteValue

class DefaultPolicyEngine(
    private val config: ExecutionConfig
) : PolicyEngine {
    override fun net(intents: List<Intent>, positions: List<Position>): NetPlan {
        val prioritized = prioritize(intents)
        val voted = applyVoting(prioritized)
        val netted = netBySymbol(voted)
        val targeted = applyTargets(netted, positions)
        return NetPlan(targeted)
    }

    private fun prioritize(intents: List<Intent>): List<Intent> {
        if (config.priorityOrder.isEmpty()) return intents
        val priorityIndex = config.priorityOrder.withIndex().associate { it.value to it.index }
        return intents.sortedBy { priorityIndex[it.kind] ?: Int.MAX_VALUE }
    }

    private fun applyVoting(intents: List<Intent>): List<Intent> {
        val voteConfig = config.vote ?: return intents
        if (voteConfig.threshold <= 1) return intents
        val grouped = intents.groupBy { it.meta[voteConfig.groupKey] ?: it.symbol }
        return grouped.values.flatMap { group ->
            if (group.size >= voteConfig.threshold) group else emptyList()
        }
    }

    private fun netBySymbol(intents: List<Intent>): List<Intent> {
        if (intents.isEmpty()) return emptyList()
        val grouped = intents.groupBy { it.symbol to it.side }
        return grouped.mapNotNull { (key, bucket) ->
            val (symbol, side) = key
            val qty = bucket.sumOf { it.qty ?: 0.0 }
            val notional = bucket.sumOf { it.notionalUsd ?: 0.0 }
            if (qty <= 0.0 && notional <= 0.0) {
                null
            } else {
                val priceHints = bucket.mapNotNull { it.priceHint }
                Intent(
                    id = "net-${UUID.randomUUID()}",
                    sourceId = bucket.first().sourceId,
                    kind = "net:${bucket.first().kind}",
                    symbol = symbol,
                    side = side,
                    notionalUsd = notional.takeIf { it > 0.0 },
                    qty = qty.takeIf { it > 0.0 },
                    priceHint = priceHints.takeIf { it.isNotEmpty() }?.average(),
                    meta = mapOf("netted_from" to bucket.joinToString(",") { it.id })
                )
            }
        }
    }

    private fun applyTargets(intents: List<Intent>, positions: List<Position>): List<Intent> {
        if (config.portfolioTargets.isEmpty()) return intents
        val currentPositions = positions.groupBy { it.symbol }
            .mapValues { (_, pos) -> pos.sumOf { it.qty } }
        val bySymbol = intents.groupBy { it.symbol }
        val retained = intents.filter { it.symbol !in config.portfolioTargets.keys }
        val adjustments = mutableListOf<Intent>()
        for ((symbol, targetQty) in config.portfolioTargets) {
            val current = currentPositions[symbol] ?: 0.0
            val plannedDelta = bySymbol[symbol]?.sumOf { intent ->
                val qty = intent.qty ?: 0.0
                if (intent.side == Side.BUY) qty else -qty
            } ?: 0.0
            val delta = targetQty - current - plannedDelta
            if (delta.absoluteValue <= EPS) continue
            val side = if (delta > 0) Side.BUY else Side.SELL
            adjustments += Intent(
                id = "target-${UUID.randomUUID()}",
                sourceId = "portfolio.target",
                kind = "portfolio-target",
                symbol = symbol,
                side = side,
                notionalUsd = null,
                qty = delta.absoluteValue,
                priceHint = null,
                meta = mapOf("target" to targetQty.toString())
            )
        }
        return retained + adjustments
    }

    companion object {
        private const val EPS = 1e-9
    }
}
