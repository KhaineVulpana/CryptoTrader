package com.kevin.cryptotrader.tools.compiler

import com.kevin.cryptotrader.contracts.Interval
import com.kevin.cryptotrader.contracts.Side
import com.kevin.cryptotrader.runtime.vm.ActionJson
import com.kevin.cryptotrader.runtime.vm.CrossDir
import com.kevin.cryptotrader.runtime.vm.EventJson
import com.kevin.cryptotrader.runtime.vm.GuardJson
import com.kevin.cryptotrader.runtime.vm.InputSourceJson
import com.kevin.cryptotrader.runtime.vm.Operand
import com.kevin.cryptotrader.runtime.vm.Op
import com.kevin.cryptotrader.runtime.vm.ProgramJson
import com.kevin.cryptotrader.runtime.vm.QuotaJson
import com.kevin.cryptotrader.runtime.vm.RuleJson
import com.kevin.cryptotrader.runtime.vm.SeriesDefJson
import com.kevin.cryptotrader.runtime.vm.SeriesType
import com.kevin.cryptotrader.runtime.vm.SourceKey
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

@Serializable
data class BlockDoc(val v: Int, val id: String, val graph: Graph)

@Serializable
data class Graph(val nodes: List<Node>, val edges: List<List<String>>)

@Serializable
data class Node(
    val id: String,
    val type: String,
    val props: Map<String, JsonElement> = emptyMap(),
)

data class CompilerOutput(
    val doc: BlockDoc,
    val ir: AutomationIr,
    val program: ProgramJson,
) {
    fun programJson(pretty: Boolean = false): String {
        val json = if (pretty) Json { prettyPrint = true } else Json
        return json.encodeToString(program)
    }
}

object BlockCompiler {
    private const val SUPPORTED_VERSION = 1
    private val json = Json { ignoreUnknownKeys = true }

    fun compile(jsonStr: String): CompilerOutput {
        val doc = json.decodeFromString(BlockDoc.serializer(), jsonStr)
        require(doc.v == SUPPORTED_VERSION) { "Unsupported version: ${doc.v}" }
        val context = IrBuilder(doc).build()
        val program = ProgramAssembler(doc, context).assemble()
        return CompilerOutput(doc, context.automation, program)
    }
}

private data class IrContext(
    val automation: AutomationIr,
    val nodeMap: Map<String, Node>,
    val eventsById: Map<String, EventIr>,
    val dataById: Map<String, DataIr>,
    val logicById: Map<String, LogicIr>,
    val riskById: Map<String, RiskIr>,
    val actionsById: Map<String, ActionIr>,
    val universesById: Map<String, UniverseIr>,
    val upstream: Map<String, List<String>>,
)

private class IrBuilder(private val doc: BlockDoc) {
    fun build(): IrContext {
        val nodeMap = doc.graph.nodes.associateBy { it.id }
        val edges = doc.graph.edges.mapNotNull { if (it.size >= 2) EdgeIr(it[0], it[1]) else null }

        val events = mutableListOf<EventIr>()
        val data = mutableListOf<DataIr>()
        val logic = mutableListOf<LogicIr>()
        val risk = mutableListOf<RiskIr>()
        val actions = mutableListOf<ActionIr>()
        val universes = mutableListOf<UniverseIr>()

        nodeMap.values.forEach { node ->
            val props = NodeProps(node)
            when (node.type) {
                "onCandle" -> events += CandleEventIr(node.id, props.interval("tf") ?: Interval.H1)
                "onTick" -> events += TickEventIr(node.id)
                "onSchedule" -> {
                    val cron = props.string("cron") ?: "0 * * * *"
                    val tz = props.string("timezone") ?: "UTC"
                    events += ScheduleEventIr(node.id, cron, tz)
                }
                "onFill" -> events += FillEventIr(node.id, props.string("symbol"))
                "onPnL" -> events += PnLEventIr(node.id, props.double("threshold") ?: 0.0)
                "indicator" -> {
                    val params = props.params().toMutableMap()
                    props.double("len")?.let { params.putIfAbsent("len", it) }
                    props.double("fast")?.let { params.putIfAbsent("fast", it) }
                    props.double("slow")?.let { params.putIfAbsent("slow", it) }
                    props.double("signal")?.let { params.putIfAbsent("signal", it) }
                    props.double("mult")?.let { params.putIfAbsent("mult", it) }
                    data += IndicatorIr(node.id, props.string("name")?.lowercase() ?: "ema", params)
                }
                "window" -> props.int("len")?.let { data += WindowIr(node.id, it) }
                "stateGet" -> props.string("key")?.let { data += StateGetIr(node.id, it) }
                "stateSet" -> {
                    val key = props.string("key") ?: ""
                    val value = props.string("value") ?: ""
                    data += StateSetIr(node.id, key, value)
                }
                "pairFeed" -> {
                    val symbol = props.string("symbol") ?: "BTCUSDT"
                    val interval = props.interval("interval") ?: Interval.H1
                    data += PairFeedIr(node.id, symbol, interval)
                }
                "universe" -> {
                    val symbols = props.list("symbols").takeIf { it.isNotEmpty() } ?: listOf("BTCUSDT", "ETHUSDT")
                    universes += StaticUniverseIr(node.id, symbols)
                }
                "if" -> logic += IfLogicIr(node.id, props.string("condition"))
                "else" -> logic += ElseLogicIr(node.id)
                "math" -> props.string("expr")?.let { logic += MathLogicIr(node.id, it) }
                "compare" -> {
                    val left = props.string("left") ?: ""
                    val right = props.string("right") ?: ""
                    val op = props.string("op")?.let { runCatching { CompareOp.valueOf(it.uppercase()) }.getOrNull() }
                        ?: CompareOp.GT
                    logic += CompareLogicIr(node.id, left, op, right)
                }
                "crossesAbove" -> logic += CrossLogicIr(node.id, CrossDirection.ABOVE)
                "crossesBelow" -> logic += CrossLogicIr(node.id, CrossDirection.BELOW)
                "cooldown" -> logic += CooldownLogicIr(node.id, props.long("ms") ?: 0L)
                "oncePerBar" -> logic += OncePerBarLogicIr(node.id)
                "forEach" -> logic += ForEachLogicIr(node.id, props.string("source") ?: "")
                "rank" -> logic += RankLogicIr(node.id, props.string("metric") ?: "", props.int("limit") ?: 0)
                "riskSize" -> risk += RiskSizeIr(node.id, props.double("riskPct"), props.double("notional"))
                "atrStop" -> props.double("atrMult")?.let { risk += AtrStopIr(node.id, it) }
                "trailStop" -> props.double("trailingPct")?.let { risk += TrailStopIr(node.id, it) }
                "emitOrder" -> actions += EmitOrderIr(
                    id = node.id,
                    symbol = props.string("symbol") ?: "BTCUSDT",
                    side = props.string("side") ?: "BUY",
                    orderType = props.string("type") ?: "MARKET",
                )
                "emitSpread" -> actions += EmitSpreadIr(
                    node.id,
                    longSymbol = props.string("long") ?: "BTCUSDT",
                    shortSymbol = props.string("short") ?: "ETHUSDT",
                )
                "log" -> actions += LogActionIr(node.id, props.string("message") ?: "")
                "notify" -> actions += NotifyActionIr(
                    node.id,
                    channel = props.string("channel") ?: "push",
                    message = props.string("message") ?: "",
                )
                "abort" -> actions += AbortActionIr(node.id, props.string("reason") ?: "")
            }
        }

        val automation = AutomationIr(
            id = doc.id,
            version = doc.v,
            events = events,
            data = data,
            logic = logic,
            risk = risk,
            actions = actions,
            universes = universes,
            edges = edges,
        )
        val upstream = edges.groupBy({ it.to }, { it.from })
        return IrContext(
            automation = automation,
            nodeMap = nodeMap,
            eventsById = events.associateBy { it.id },
            dataById = data.associateBy { it.id },
            logicById = logic.associateBy { it.id },
            riskById = risk.associateBy { it.id },
            actionsById = actions.associateBy { it.id },
            universesById = universes.associateBy { it.id },
            upstream = upstream,
        )
    }
}

private class NodeProps(private val node: Node) {
    fun string(key: String): String? = node.props[key]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
    fun double(key: String): Double? = node.props[key]?.jsonPrimitive?.doubleOrNull
    fun int(key: String): Int? = node.props[key]?.jsonPrimitive?.intOrNull
    fun long(key: String): Long? = node.props[key]?.jsonPrimitive?.longOrNull
    fun list(key: String): List<String> {
        val raw = node.props[key] ?: return emptyList()
        return when (raw) {
            is JsonArray -> raw.mapNotNull { it.jsonPrimitive.contentOrNull?.takeIf(String::isNotEmpty) }
            else -> raw.jsonPrimitive.contentOrNull?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
        }
    }
    fun params(key: String = "params"): Map<String, Double> {
        val raw = node.props[key] ?: return emptyMap()
        val obj = raw.jsonObject
        return obj.mapNotNull { (k, v) -> v.jsonPrimitive.doubleOrNull?.let { k to it } }.toMap()
    }
    fun interval(key: String): Interval? = string(key)?.let { runCatching { Interval.valueOf(it.uppercase()) }.getOrNull() }
}

private class ProgramAssembler(
    private val doc: BlockDoc,
    private val context: IrContext,
) {
    private val automation get() = context.automation

    fun assemble(): ProgramJson {
        val interval = resolveInterval()
        val series = buildSeries()
        val rules = buildRules(interval)
        val defaultSymbol = series.firstOrNull()?.symbol ?: "BTCUSDT"
        return ProgramJson(
            id = doc.id,
            version = 1,
            interval = interval,
            defaultSymbol = defaultSymbol,
            inputs = listOf(InputSourceJson(symbol = defaultSymbol, csvPath = "fixtures/ohlcv/BTCUSDT_1h_sample.csv")),
            inputsCsvPath = "fixtures/ohlcv/BTCUSDT_1h_sample.csv",
            series = series,
            rules = rules,
        )
    }

    private fun resolveInterval(): Interval {
        val candle = automation.events.filterIsInstance<CandleEventIr>().firstOrNull()
        return candle?.interval ?: Interval.H1
    }

    private fun buildSeries(): List<SeriesDefJson> {
        val defs = mutableListOf<SeriesDefJson>()
        automation.data.filterIsInstance<IndicatorIr>().forEach { indicator ->
            when (indicator.indicator.lowercase()) {
                "ema" -> {
                    val len = indicator.params["len"]?.toInt() ?: 14
                    defs += SeriesDefJson(
                        name = indicator.id,
                        type = SeriesType.EMA,
                        period = len,
                        source = SourceKey.CLOSE,
                    )
                }
                "rsi" -> {
                    val len = indicator.params["len"]?.toInt() ?: 14
                    defs += SeriesDefJson(
                        name = indicator.id,
                        type = SeriesType.RSI,
                        period = len,
                        source = SourceKey.CLOSE,
                    )
                }
                "macd" -> {
                    val fast = indicator.params["fast"]?.toInt() ?: 12
                    val slow = indicator.params["slow"]?.toInt() ?: 26
                    val signal = indicator.params["signal"]?.toInt() ?: 9
                    val params = mapOf("fast" to fast.toDouble(), "slow" to slow.toDouble(), "signal" to signal.toDouble())
                    defs += SeriesDefJson(indicator.id + "_macd", SeriesType.MACD, params = params)
                    defs += SeriesDefJson(indicator.id + "_signal", SeriesType.MACD_SIGNAL, params = params)
                    defs += SeriesDefJson(indicator.id + "_hist", SeriesType.MACD_HIST, params = params)
                }
                "bollinger" -> {
                    val period = indicator.params["len"]?.toInt() ?: 20
                    val mult = indicator.params["mult"] ?: 2.0
                    val meta = mapOf("mult" to mult.toString())
                    defs += SeriesDefJson(indicator.id + "_mid", SeriesType.BOLLINGER_MIDDLE, period = period, meta = meta)
                    defs += SeriesDefJson(indicator.id + "_upper", SeriesType.BOLLINGER_UPPER, period = period, meta = meta)
                    defs += SeriesDefJson(indicator.id + "_lower", SeriesType.BOLLINGER_LOWER, period = period, meta = meta)
                    defs += SeriesDefJson(indicator.id + "_std", SeriesType.BOLLINGER_STDDEV, period = period, meta = meta)
                }
                "atr" -> {
                    val period = indicator.params["len"]?.toInt() ?: 14
                    defs += SeriesDefJson(
                        name = indicator.id,
                        type = SeriesType.ATR,
                        period = period,
                        source = SourceKey.CLOSE,
                    )
                }
                "zscore" -> {
                    val period = indicator.params["len"]?.toInt() ?: 20
                    defs += SeriesDefJson(
                        name = indicator.id,
                        type = SeriesType.ZSCORE,
                        period = period,
                        source = SourceKey.CLOSE,
                    )
                }
                else -> {
                    defs += SeriesDefJson(
                        name = indicator.id,
                        type = SeriesType.ROC,
                        period = indicator.params["len"]?.toInt(),
                        source = SourceKey.CLOSE,
                        meta = mapOf("indicator" to indicator.indicator),
                    )
                }
            }
        }
        return defs
    }

    private fun buildRules(interval: Interval): List<RuleJson> {
        val rules = mutableListOf<RuleJson>()
        automation.actions.forEach { action ->
            val ancestors = ancestorsOf(action.id)
            val oncePerBar = ancestors.mapNotNull { context.logicById[it] }.any { it is OncePerBarLogicIr }
            val cooldown = ancestors.mapNotNull { context.logicById[it] }
                .filterIsInstance<CooldownLogicIr>()
                .maxOfOrNull { it.cooldownMs }
            val guard = buildGuard(ancestors)
            val ruleMeta = buildRuleMeta(ancestors)
            val actions = buildActions(action, ancestors, ruleMeta)
            val event = resolveEvent(ancestors)
            val quota = if (oncePerBar) QuotaJson(1, intervalToMillis(interval)) else null
            rules += RuleJson(
                id = action.id,
                event = event,
                oncePerBar = oncePerBar,
                guard = guard,
                actions = actions,
                quota = quota,
                delayMs = cooldown,
                meta = ruleMeta,
            )
        }
        return rules
    }

    private fun resolveEvent(ancestors: Set<String>): EventJson {
        val eventId = ancestors.firstOrNull { context.eventsById.containsKey(it) }
        val event = eventId?.let { context.eventsById[it] }
        return when (event) {
            is CandleEventIr -> EventJson.Candle()
            is TickEventIr -> EventJson.Candle()
            is ScheduleEventIr -> EventJson.Schedule()
            is FillEventIr -> EventJson.Fill(symbol = event.symbol, side = null)
            is PnLEventIr -> EventJson.PnL(symbol = null, realizedThreshold = event.thresholdPct, unrealizedThreshold = null)
            else -> EventJson.Candle()
        }
    }

    private fun buildGuard(ancestors: Set<String>): GuardJson? {
        val cross = ancestors.mapNotNull { context.logicById[it] }.filterIsInstance<CrossLogicIr>().firstOrNull()
        if (cross != null) return buildCrossGuard(cross)
        val compare = ancestors.mapNotNull { context.logicById[it] }.filterIsInstance<CompareLogicIr>().firstOrNull()
        if (compare != null) return buildCompareGuard(compare)
        return GuardJson.Always
    }

    private fun buildActions(action: ActionIr, ancestors: Set<String>, ruleMeta: Map<String, String>): List<ActionJson> {
        val metaStrings = ruleMeta.toMutableMap()
        ancestors.mapNotNull { context.riskById[it] }.forEach { risk ->
            when (risk) {
                is RiskSizeIr -> {
                    risk.riskPct?.let { metaStrings["risk.pct"] = it.toString() }
                    risk.notionalUsd?.let { metaStrings["risk.notional"] = it.toString() }
                }
                is AtrStopIr -> metaStrings["risk.atr.mult"] = risk.multiplier.toString()
                is TrailStopIr -> metaStrings["risk.trailingPct"] = risk.trailingPct.toString()
            }
        }
        return when (action) {
            is EmitOrderIr -> {
                listOf(
                    ActionJson.EmitOrder(
                        symbol = action.symbol,
                        side = Side.valueOf(action.side.uppercase()),
                        kind = "signal",
                        metaStrings = metaStrings,
                    )
                )
            }
            is EmitSpreadIr -> {
                metaStrings["spread.long"] = action.longSymbol
                metaStrings["spread.short"] = action.shortSymbol
                listOf(
                    ActionJson.EmitSpread(
                        symbol = action.longSymbol,
                        side = Side.BUY,
                        offsetPct = 0.0,
                        widthPct = 0.0,
                        metaStrings = metaStrings,
                    )
                )
            }
            is LogActionIr -> {
                listOf(
                    ActionJson.Log(
                        message = action.message,
                    )
                )
            }
            is NotifyActionIr -> {
                listOf(
                    ActionJson.Notify(
                        channel = action.channel,
                        message = action.message,
                    )
                )
            }
            is AbortActionIr -> {
                listOf(ActionJson.Abort(action.reason))
            }
            else -> emptyList()
        }
    }

    private fun buildRuleMeta(ancestors: Set<String>): Map<String, String> {
        val meta = mutableMapOf<String, String>()
        val eventId = ancestors.firstOrNull { context.eventsById.containsKey(it) }
        when (val event = eventId?.let { context.eventsById[it] }) {
            is CandleEventIr -> {
                meta["event.type"] = "onCandle"
                meta["event.interval"] = event.interval.name
            }
            is TickEventIr -> meta["event.type"] = "onTick"
            is ScheduleEventIr -> {
                meta["event.type"] = "onSchedule"
                meta["event.cron"] = event.cron
                meta["event.timezone"] = event.timezone
            }
            is FillEventIr -> {
                meta["event.type"] = "onFill"
                event.symbol?.let { meta["event.symbol"] = it }
            }
            is PnLEventIr -> {
                meta["event.type"] = "onPnL"
                meta["event.thresholdPct"] = event.thresholdPct.toString()
            }
            null -> Unit
        }
        ancestors.mapNotNull { context.logicById[it] }.forEach { logic ->
            when (logic) {
                is IfLogicIr -> logic.label?.let { meta["logic.if"] = it }
                is ElseLogicIr -> meta["logic.branch"] = "else"
                is MathLogicIr -> meta["logic.math"] = logic.expression
                is ForEachLogicIr -> meta["logic.forEach"] = logic.source
                is RankLogicIr -> {
                    meta["logic.rank.metric"] = logic.metric
                    meta["logic.rank.limit"] = logic.limit.toString()
                }
                else -> Unit
            }
        }
        ancestors.mapNotNull { context.universesById[it] }.forEach { universe ->
            if (universe is StaticUniverseIr) {
                meta["universe.symbols"] = universe.symbols.joinToString(",")
            }
        }
        return meta
    }

    private fun buildCrossGuard(cross: CrossLogicIr): GuardJson {
        val upstreamIndicators = ancestorsOf(cross.id)
            .mapNotNull { context.dataById[it] as? IndicatorIr }
        val leftSeries = upstreamIndicators.getOrNull(0)?.let { Operand.Series(it.id) } ?: Operand.Const(0.0)
        val rightSeries = upstreamIndicators.getOrNull(1)?.let { Operand.Series(it.id) } ?: Operand.Const(0.0)
        val dir = if (cross.direction == CrossDirection.ABOVE) CrossDir.ABOVE else CrossDir.BELOW
        return GuardJson.Crosses(left = leftSeries, dir = dir, right = rightSeries)
    }

    private fun buildCompareGuard(compare: CompareLogicIr): GuardJson {
        val left = operandFrom(compare.left)
        val right = operandFrom(compare.right)
        val op = when (compare.op) {
            CompareOp.GT -> Op.GT
            CompareOp.GTE -> Op.GTE
            CompareOp.LT -> Op.LT
            CompareOp.LTE -> Op.LTE
            CompareOp.EQ -> Op.EQ
        }
        return GuardJson.Threshold(left, op, right)
    }

    private fun operandFrom(ref: String): Operand {
        val trimmed = ref.trim()
        trimmed.toDoubleOrNull()?.let { return Operand.Const(it) }
        if (trimmed.isNotEmpty()) {
            if (context.dataById.containsKey(trimmed) || trimmed.lowercase() in setOf("open", "high", "low", "close", "volume")) {
                return Operand.Series(trimmed)
            }
        }
        return Operand.Const(0.0)
    }

    private fun ancestorsOf(nodeId: String): Set<String> {
        val visited = mutableSetOf<String>()
        fun dfs(id: String) {
            context.upstream[id]?.forEach { upstreamId ->
                if (visited.add(upstreamId)) dfs(upstreamId)
            }
        }
        dfs(nodeId)
        return visited
    }

    private fun intervalToMillis(interval: Interval): Long = when (interval) {
        Interval.M1 -> 60_000L
        Interval.M5 -> 5 * 60_000L
        Interval.M15 -> 15 * 60_000L
        Interval.M30 -> 30 * 60_000L
        Interval.H1 -> 60 * 60_000L
        Interval.H4 -> 4 * 60 * 60_000L
        Interval.D1 -> 24 * 60 * 60_000L
    }
}
