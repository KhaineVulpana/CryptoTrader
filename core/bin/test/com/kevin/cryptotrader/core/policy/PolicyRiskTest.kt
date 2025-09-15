package com.kevin.cryptotrader.core.policy

import com.kevin.cryptotrader.contracts.*
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PolicyRiskTest {
  @Test
  fun priority_netting_and_sizing() {
    val priority = loadPriority()
    val policy = PolicyEngineImpl(priority)

    val intents = listOf(
      Intent(id = "i1", sourceId = "automation.dca#1", kind = "sig", symbol = "BTCUSDT", side = Side.BUY, qty = 0.1, priceHint = 20000.0),
      Intent(id = "i2", sourceId = "strategy.momentum#main", kind = "sig", symbol = "BTCUSDT", side = Side.SELL, qty = 0.05, priceHint = 20000.0),
      Intent(id = "i3", sourceId = "strategy.momentum#main", kind = "sig", symbol = "BTCUSDT", side = Side.BUY, qty = 0.12, priceHint = 20000.0),
    )

    val net = policy.net(intents, positions = emptyList())
    // Sell and buy from same high-priority source should net; buy wins with 0.07
    assertEquals(1, net.intents.size)
    val only = net.intents.first()
    assertEquals(Side.BUY, only.side)
    assertEquals("BTCUSDT", only.symbol)
    assertTrue((only.qty ?: 0.0) > 0.069 && (only.qty ?: 0.0) < 0.071)

    val sizer = RiskSizerImpl()
    val orders = sizer.size(net, AccountSnapshot(0.0, emptyMap()))
    assertEquals(1, orders.size)
    assertEquals(OrderType.MARKET, orders.first().type)
  }

  private fun loadPriority(): List<String> {
    val path = resolvePath("fixtures/policies/priority.json")
    val json = Files.readString(path)
    val lines = json.lines().map { it.trim() }
    val start = lines.indexOfFirst { it.startsWith("\"priority\"") }
    if (start < 0) return emptyList()
    val arr = mutableListOf<String>()
    var i = start
    while (i < lines.size) {
      val ln = lines[i]
      if (ln.contains("[")) { i++; continue }
      if (ln.contains("]")) break
      val v = ln.trim().trim(',').trim().trim('"')
      if (v.isNotEmpty()) arr.add(v)
      i++
    }
    return arr
  }

  private fun resolvePath(path: String): java.nio.file.Path {
    val candidates = listOf(
      Paths.get(path),
      Paths.get("../$path"),
      Paths.get("../../$path"),
      Paths.get("../../../$path"),
    )
    return candidates.firstOrNull { Files.exists(it) } ?: error("Fixture not found: $path")
  }
}

