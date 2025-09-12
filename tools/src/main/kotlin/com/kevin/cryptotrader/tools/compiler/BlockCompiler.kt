package com.kevin.cryptotrader.tools.compiler

import com.kevin.cryptotrader.contracts.Interval
import com.kevin.cryptotrader.contracts.Side
import com.kevin.cryptotrader.runtime.vm.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class BlockDoc(val v: Int, val id: String, val graph: Graph)

@Serializable
data class Graph(val nodes: List<Node>, val edges: List<List<String>>)

@Serializable
data class Node(val id: String, val type: String, val props: Map<String, @kotlinx.serialization.json.JsonElement> = emptyMap())

object BlockCompiler {
  private val json = Json { ignoreUnknownKeys = true }

  fun compile(jsonStr: String): String {
    val doc = json.decodeFromString(BlockDoc.serializer(), jsonStr)
    require(doc.v == 1) { "Unsupported version: ${doc.v}" }

    // Default interval H1 from onCandle
    val interval = doc.graph.nodes.firstOrNull { it.type == "onCandle" }?.props?.get("tf")?.let {
      json.decodeFromJsonElement(Interval.serializer(), it)
    } ?: Interval.H1

    // Indicators -> series
    val series = mutableListOf<SeriesDefJson>()
    val idToSeriesName = HashMap<String, String>()
    doc.graph.nodes.filter { it.type == "indicator" }.forEachIndexed { idx, n ->
      val nameProp = n.props["name"]?.toString()?.trim('"') ?: ""
      if (nameProp.equals("ema", ignoreCase = true)) {
        val len = n.props["params"]?.let { params ->
          val obj = params
          val lenEl = obj.jsonObject["len"]
          lenEl?.toString()?.trim('"')?.toInt()
        } ?: 14
        val seriesName = "ema_${len}_${idx}"
        series.add(SeriesDefJson(name = seriesName, type = SeriesType.EMA, period = len, source = SourceKey.CLOSE))
        idToSeriesName[n.id] = seriesName
      }
    }

    // Edges determine flow; for now support oncePerBar + crossesAbove -> emitOrder BUY
    val rules = mutableListOf<RuleJson>()
    val crossesNodes = doc.graph.nodes.filter { it.type.startsWith("crosses") }
    crossesNodes.forEachIndexed { idx, node ->
      // Find two upstream indicators connected to this node via edges
      val upstream = doc.graph.edges.filter { it[1] == node.id }.map { it[0] }
      val seriesNames = upstream.mapNotNull { idToSeriesName[it] }
      if (seriesNames.size >= 2) {
        val left = Operand.Series(seriesNames[0])
        val right = Operand.Series(seriesNames[1])
        val dir = if (node.type.equals("crossesAbove", true)) CrossDir.ABOVE else CrossDir.BELOW
        val guard = GuardJson.Crosses(left = left, dir = dir, right = right)
        val action = ActionJson(type = ActionType.EMIT, symbol = "BTCUSDT", side = if (dir == CrossDir.ABOVE) Side.BUY else Side.SELL)
        val rule = RuleJson(id = "rule_$idx", oncePerBar = true, guard = guard, action = action, quota = QuotaJson(100, 86_400_000))
        rules.add(rule)
      }
    }

    val program = ProgramJson(
      id = doc.id,
      version = 1,
      interval = interval,
      inputsCsvPath = "fixtures/ohlcv/BTCUSDT_1h_sample.csv",
      series = series,
      rules = rules,
    )
    return Json.encodeToString(ProgramJson.serializer(), program)
  }
}

