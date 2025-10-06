package com.kevin.cryptotrader.core.policy

import com.kevin.cryptotrader.contracts.AccountSnapshot
import com.kevin.cryptotrader.contracts.Intent
import com.kevin.cryptotrader.contracts.NetPlan
import com.kevin.cryptotrader.contracts.OrderType
import com.kevin.cryptotrader.contracts.PolicyConfig
import com.kevin.cryptotrader.contracts.PolicyMode
import com.kevin.cryptotrader.contracts.Position
import com.kevin.cryptotrader.contracts.RiskEvent
import com.kevin.cryptotrader.contracts.Side
import com.kevin.cryptotrader.contracts.StopKind
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class PolicyRiskTest {
  private val json = Json { ignoreUnknownKeys = true }

  @Test
  fun priorityFixtureNetsConflicts() {
    val fixture = loadPolicyFixture("priority.json")
    val policy = PolicyEngineImpl(fixture.toConfig())
    val net = policy.net(fixture.intentObjects(), fixture.positionObjects())
    assertMatches(fixture.expected, net.intents)
  }

  @Test
  fun portfolioTargetFixtureRespectsTargets() {
    val fixture = loadPolicyFixture("portfolio_target.json")
    val policy = PolicyEngineImpl(fixture.toConfig())
    val net = policy.net(fixture.intentObjects(), fixture.positionObjects())
    assertMatches(fixture.expected, net.intents)
  }

  @Test
  fun voteFixtureRequiresMajorityThreshold() {
    val fixture = loadPolicyFixture("vote.json")
    val policy = PolicyEngineImpl(fixture.toConfig())
    val net = policy.net(fixture.intentObjects(), fixture.positionObjects())
    assertMatches(fixture.expected, net.intents)
  }

  @Test
  fun riskSizerFixedPercentAtrStop() {
    val plan = NetPlan(
      listOf(
        Intent(
          id = "risk-1",
          sourceId = "strategy.momentum#main",
          kind = "sig",
          symbol = "BTCUSDT",
          side = Side.BUY,
          qty = null,
          notionalUsd = null,
          priceHint = 2000.0,
          meta = mapOf(
            "risk.mode" to "fixed_pct",
            "risk.pct" to "0.02",
            "risk.atr" to "100",
            "stop.atr_mult" to "2",
          ),
        ),
      ),
    )
    val sizer = RiskSizerImpl()
    val result = sizer.size(plan, AccountSnapshot(equityUsd = 10_000.0, balances = emptyMap()))

    assertEquals(1, result.orders.size)
    val order = result.orders.first()
    assertEquals(Side.BUY, order.side)
    assertEquals(OrderType.MARKET, order.type)
    assertApprox(1.0, order.qty)

    assertEquals(1, result.stopOrders.size)
    val stop = result.stopOrders.first()
    assertEquals(Side.SELL, stop.side)
    assertEquals(OrderType.STOP, stop.type)
    assertApprox(1.0, stop.qty)
    assertApprox(1800.0, stop.stopPrice!!)

    assertTrue(result.events.any { ev ->
      ev is RiskEvent.StopSet && ev.kind == StopKind.ATR &&
        abs((ev.stopPrice ?: 0.0) - 1800.0) < 1e-6
    })
  }

  @Test
  fun riskSizerVolTargetCapsAndCorrelationGuard() {
    val config = RiskSizerConfig(
      defaultRiskPct = 0.02,
      maxPortfolioRiskPct = 0.03,
      perSymbolCaps = mapOf("BTCUSDT" to 0.015),
      correlationGuard = CorrelationGuardConfig(
        groups = listOf(setOf("BTCUSDT", "ETHUSDT")),
        maxActivePerGroup = 1,
      ),
      clockMs = { 1_000L },
    )
    val sizer = RiskSizerImpl(config)
    val plan = NetPlan(
      listOf(
        Intent(
          id = "a",
          sourceId = "strategy.vol#1",
          kind = "sig",
          symbol = "BTCUSDT",
          side = Side.BUY,
          priceHint = 100.0,
          meta = mapOf(
            "risk.mode" to "vol_target",
            "risk.vol" to "0.1",
            "risk.target_vol" to "0.2",
            "risk.pct" to "0.02",
            "stop.trailing_pct" to "0.05",
            "stop.time_sec" to "3600",
          ),
        ),
        Intent(
          id = "b",
          sourceId = "strategy.other#1",
          kind = "sig",
          symbol = "ETHUSDT",
          side = Side.BUY,
          priceHint = 200.0,
          meta = mapOf(
            "risk.mode" to "vol_target",
            "risk.vol" to "0.12",
            "risk.target_vol" to "0.24",
          ),
        ),
      ),
    )

    val result = sizer.size(plan, AccountSnapshot(equityUsd = 50_000.0, balances = emptyMap()))

    assertEquals(1, result.orders.size, "Correlation guard should skip the second intent")
    val order = result.orders.first()
    assertApprox(7.5, order.qty)
    assertEquals("BTCUSDT", order.symbol)

    assertTrue(result.stopOrders.isEmpty())
    val trailing = result.events.firstOrNull { it is RiskEvent.StopSet && it.kind == StopKind.TRAILING }
    assertTrue(trailing is RiskEvent.StopSet && abs((trailing.trailingPct ?: 0.0) - 0.05) < 1e-6)
    val timeStop = result.events.firstOrNull { it is RiskEvent.StopSet && it.kind == StopKind.TIME }
    assertTrue(timeStop is RiskEvent.StopSet && timeStop.expiresAt == 3_601_000L)
  }

  private fun assertMatches(expected: List<ExpectedIntent>, actual: List<Intent>) {
    assertEquals(expected.size, actual.size, "Unexpected number of intents: $actual")
    expected.forEach { exp ->
      val match = actual.firstOrNull { it.symbol == exp.symbol && it.side == exp.sideEnum }
        ?: fail("No intent for ${exp.symbol} ${exp.side}")
      exp.qty?.let { assertApprox(it, match.qty ?: 0.0) }
      exp.priceHint?.let { assertApprox(it, match.priceHint ?: 0.0) }
    }
  }

  private fun assertApprox(expected: Double, actual: Double, tolerance: Double = 1e-6) {
    assertTrue(abs(expected - actual) <= tolerance, "Expected $expected but was $actual")
  }

  private fun loadPolicyFixture(name: String): PolicyFixture {
    val path = resolvePath("fixtures/policies/$name")
    val root = Json.parseToJsonElement(Files.readString(path)).jsonObject
    return PolicyFixture(
      mode = root.string("mode") ?: error("mode required"),
      priority = root.stringList("priority"),
      strategyWeights = root.obj("strategyWeights")?.entries?.associate { entry ->
        entry.key to (entry.value.jsonPrimitive.doubleOrNull ?: 0.0)
      } ?: emptyMap(),
      voteThreshold = root.double("voteThreshold"),
      positions = root.array("positions").map { it.toFixturePosition() },
      intents = root.array("intents").map { it.toFixtureIntent() },
      expected = root.array("expected").map { it.toExpectedIntent() },
    )
  }

  private fun PolicyFixture.intentObjects(): List<Intent> = intents.map { it.toIntent() }

  private fun PolicyFixture.positionObjects(): List<Position> = positions.map { it.toPosition() }

  private fun PolicyFixture.toConfig(): PolicyConfig = PolicyConfig(
    mode = PolicyMode.valueOf(mode.uppercase().replace('-', '_')),
    priority = priority,
    voteThreshold = voteThreshold ?: 0.5,
    strategyWeights = strategyWeights,
  )

  private data class PolicyFixture(
    val mode: String,
    val priority: List<String> = emptyList(),
    val strategyWeights: Map<String, Double> = emptyMap(),
    val voteThreshold: Double? = null,
    val positions: List<FixturePosition> = emptyList(),
    val intents: List<FixtureIntent>,
    val expected: List<ExpectedIntent> = emptyList(),
  )

  private data class FixtureIntent(
    val id: String,
    val sourceId: String,
    val kind: String,
    val symbol: String,
    val side: String,
    val qty: Double? = null,
    val notionalUsd: Double? = null,
    val priceHint: Double? = null,
    val meta: Map<String, String> = emptyMap(),
  ) {
    fun toIntent(): Intent = Intent(
      id = id,
      sourceId = sourceId,
      kind = kind,
      symbol = symbol,
      side = Side.valueOf(side.uppercase()),
      qty = qty,
      notionalUsd = notionalUsd,
      priceHint = priceHint,
      meta = meta,
    )
  }

  private data class FixturePosition(
    val accountId: String = "acc",
    val symbol: String,
    val qty: Double,
    val avgPrice: Double,
  ) {
    fun toPosition(): Position = Position(
      accountId = accountId,
      symbol = symbol,
      qty = qty,
      avgPrice = avgPrice,
      realizedPnl = 0.0,
      unrealizedPnl = 0.0,
    )
  }

  private data class ExpectedIntent(
    val symbol: String,
    val side: String,
    val qty: Double? = null,
    val priceHint: Double? = null,
  ) {
    val sideEnum: Side get() = Side.valueOf(side.uppercase())
  }

  private fun resolvePath(path: String): Path {
    val candidates = listOf(
      Paths.get(path),
      Paths.get("../$path"),
      Paths.get("../../$path"),
      Paths.get("../../../$path"),
    )
    return candidates.firstOrNull { Files.exists(it) } ?: error("Fixture not found: $path")
  }

  private fun JsonObject.string(key: String): String? = this[key]?.jsonPrimitive?.content

  private fun JsonObject.double(key: String): Double? = this[key]?.jsonPrimitive?.doubleOrNull

  private fun JsonObject.stringList(key: String): List<String> =
    this[key]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()

  private fun JsonObject.array(key: String): List<JsonObject> =
    this[key]?.jsonArray?.map { it.jsonObject } ?: emptyList()

  private fun JsonObject.obj(key: String): JsonObject? = this[key]?.jsonObject

  private fun JsonObject.toFixtureIntent(): FixtureIntent = FixtureIntent(
    id = string("id") ?: error("intent id missing"),
    sourceId = string("sourceId") ?: error("sourceId missing"),
    kind = string("kind") ?: "sig",
    symbol = string("symbol") ?: error("symbol missing"),
    side = string("side") ?: error("side missing"),
    qty = double("qty"),
    notionalUsd = double("notionalUsd"),
    priceHint = double("priceHint"),
    meta = obj("meta")?.entries?.associate { it.key to it.value.jsonPrimitive.content } ?: emptyMap(),
  )

  private fun JsonObject.toFixturePosition(): FixturePosition = FixturePosition(
    accountId = string("accountId") ?: "acc",
    symbol = string("symbol") ?: error("position symbol missing"),
    qty = double("qty") ?: 0.0,
    avgPrice = double("avgPrice") ?: 0.0,
  )

  private fun JsonObject.toExpectedIntent(): ExpectedIntent = ExpectedIntent(
    symbol = string("symbol") ?: error("expected symbol"),
    side = string("side") ?: error("expected side"),
    qty = double("qty"),
    priceHint = double("priceHint"),
  )
}
