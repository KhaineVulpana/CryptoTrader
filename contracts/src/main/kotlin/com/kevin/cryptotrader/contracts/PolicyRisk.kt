package com.kevin.cryptotrader.contracts

data class NetPlan(
  val intents: List<Intent>,
)

enum class PolicyMode { PRIORITY, NETTING, PORTFOLIO_TARGET, VOTE }

data class PolicyConfig(
  val mode: PolicyMode = PolicyMode.NETTING,
  val priority: List<String> = emptyList(),
  val voteThreshold: Double = 0.5,
  val strategyWeights: Map<String, Double> = emptyMap(),
) {
  fun weightFor(sourceId: String): Double {
    if (strategyWeights.isEmpty()) return 1.0
    val match = strategyWeights
      .filterKeys { sourceId.startsWith(it) }
      .maxByOrNull { it.key.length }
    return match?.value ?: 0.0
  }
}

interface PolicyEngine {
  fun net(
    intents: List<Intent>,
    positions: List<Position>,
  ): NetPlan
}

@kotlinx.serialization.Serializable
enum class StopKind { ATR, TRAILING, TIME }

@kotlinx.serialization.Serializable
sealed class RiskEvent {
  abstract val intentId: String
  abstract val symbol: String
  abstract val side: Side

  @kotlinx.serialization.Serializable
  data class StopSet(
    override val intentId: String,
    override val symbol: String,
    override val side: Side,
    val kind: StopKind,
    val stopPrice: Double? = null,
    val trailingPct: Double? = null,
    val expiresAt: Long? = null,
    val meta: Map<String, String> = emptyMap(),
  ) : RiskEvent()
}

data class RiskResult(
  val orders: List<Order>,
  val stopOrders: List<Order> = emptyList(),
  val events: List<RiskEvent> = emptyList(),
)

interface RiskSizer {
  fun size(
    plan: NetPlan,
    account: AccountSnapshot,
  ): RiskResult
}
