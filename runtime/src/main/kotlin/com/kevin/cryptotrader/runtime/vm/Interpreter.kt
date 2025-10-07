package com.kevin.cryptotrader.runtime.vm

import com.kevin.cryptotrader.contracts.BacktestSample
import com.kevin.cryptotrader.contracts.Intent
import com.kevin.cryptotrader.contracts.Interval
import com.kevin.cryptotrader.contracts.Side
import com.kevin.cryptotrader.core.indicators.AtrIndicator
import com.kevin.cryptotrader.core.indicators.Bollinger
import com.kevin.cryptotrader.core.indicators.BollingerBands
import com.kevin.cryptotrader.core.indicators.DonchianChannel
import com.kevin.cryptotrader.core.indicators.EmaIndicator
import com.kevin.cryptotrader.core.indicators.Donchian
import com.kevin.cryptotrader.core.indicators.MacdIndicator
import com.kevin.cryptotrader.core.indicators.RocIndicator
import com.kevin.cryptotrader.core.indicators.RsiIndicator
import com.kevin.cryptotrader.core.indicators.SmaIndicator
import com.kevin.cryptotrader.core.indicators.WmaIndicator
import com.kevin.cryptotrader.core.indicators.ZScore
import com.kevin.cryptotrader.core.indicators.Macd
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.File
import java.util.TreeSet
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

data class InputBar(
  val ts: Long,
  val open: Double,
  val high: Double,
  val low: Double,
  val close: Double,
  val volume: Double,
)

data class FillEvent(val ts: Long, val symbol: String, val side: Side, val price: Double, val qty: Double)

data class PnLEvent(val ts: Long, val symbol: String, val realized: Double, val unrealized: Double)

/** Build and run an interpreted program defined by ProgramJson */
class Interpreter(private val program: ProgramJson) {
  private val intentCounter = AtomicLong(0L)

  fun run(input: List<InputBar>): Flow<Intent> {
    val defaultSymbol = program.defaultSymbol ?: "symbol"
    return run(mapOf(defaultSymbol to input))
  }

  fun run(inputs: Map<String, List<InputBar>>): Flow<Intent> = flow {
    val symbolSet = if (inputs.isEmpty()) {
      val sym = program.defaultSymbol ?: determineDefaultSymbol(emptySet())
      setOf(sym)
    } else {
      inputs.keys
    }
    val defaultSymbol = program.defaultSymbol ?: determineDefaultSymbol(symbolSet)

    val seriesEngines = SeriesEngines(program.series, defaultSymbol)
    val ctx = RuntimeCtx(program.interval, defaultSymbol)
    val state = HashMap<String, Double>()

    val barsBySymbolAndTs = inputs.mapValues { (_, bars) -> bars.associateBy { it.ts } }
    val allTimestamps = TreeSet<Long>()
    barsBySymbolAndTs.values.forEach { map -> allTimestamps.addAll(map.keys) }

    val fillByTs = program.fillEvents.groupBy { it.ts }.mapValues { entry ->
      entry.value.map { FillEvent(it.ts, it.symbol, it.side, it.price, it.qty) }
    }
    val pnlByTs = program.pnlEvents.groupBy { it.ts }.mapValues { entry ->
      entry.value.map { PnLEvent(it.ts, it.symbol, it.realized, it.unrealized) }
    }
    allTimestamps.addAll(fillByTs.keys)
    allTimestamps.addAll(pnlByTs.keys)

    for (ts in allTimestamps) {
      val barsAtTs = symbolSet.associateWith { symbol -> barsBySymbolAndTs[symbol]?.get(ts) }
      seriesEngines.update(ts, barsAtTs)

      val frame = EventFrame(
        ts = ts,
        bars = barsAtTs,
        fills = fillByTs[ts].orEmpty(),
        pnls = pnlByTs[ts].orEmpty(),
      )

      ctx.updateEventState(frame, state)

      for (rule in program.rules) {
        if (!ctx.shouldTrigger(rule, frame)) continue

        val guardPass = rule.guard?.let { evalGuard(it, seriesEngines, state, ctx) } ?: true
        val ready = ctx.updateTimerAndCheck(rule.id, frame.ts, rule.delayMs, guardPass)
        if (!guardPass || !ready) continue
        if (!ctx.quotaPrecheck(rule.id, frame.ts, rule.quota)) continue

        var aborted = false
        val actions = rule.actions
        if (actions.isEmpty()) continue
        actions.forEachIndexed { index, action ->
          if (aborted) return@forEachIndexed
          val results = executeAction(action, rule.id, index, frame, seriesEngines, state)
          results.forEach { emit(it) }
          if (action is ActionJson.Abort) {
            aborted = true
          }
        }
        if (!aborted) {
          ctx.recordEmit(rule.id, frame.ts, rule.quota)
        }
      }
    }
  }

  private fun determineDefaultSymbol(candidates: Set<String>): String {
    if (program.defaultSymbol != null) return program.defaultSymbol
    program.rules.firstOrNull { it.event is EventJson.Candle }?.let { rule ->
      val candle = rule.event as EventJson.Candle
      if (!candle.symbol.isNullOrBlank()) return candle.symbol
    }
    return candidates.firstOrNull() ?: "UNKNOWN"
  }

  private fun evalGuard(
    guard: GuardJson,
    engines: SeriesEngines,
    state: MutableMap<String, Double>,
    ctx: RuntimeCtx,
  ): Boolean {
    return when (guard) {
      is GuardJson.Threshold -> {
        val l = evalOperand(guard.left, engines, state)
        val r = evalOperand(guard.right, engines, state)
        if (l == null || r == null) return false
        when (guard.op) {
          Op.GT -> l > r
          Op.GTE -> l >= r
          Op.LT -> l < r
          Op.LTE -> l <= r
          Op.EQ -> l == r
        }
      }
      is GuardJson.Crosses -> {
        val l = evalOperand(guard.left, engines, state)
        val r = evalOperand(guard.right, engines, state)
        ctx.evalCrosses("${guard.left}-${guard.right}", guard.dir, l, r)
      }
      GuardJson.Always -> true
      is GuardJson.And -> guard.guards.all { evalGuard(it, engines, state, ctx) }
      is GuardJson.Or -> guard.guards.any { evalGuard(it, engines, state, ctx) }
    }
  }

  private fun evalOperand(op: Operand, engines: SeriesEngines, state: MutableMap<String, Double>): Double? {
    return when (op) {
      is Operand.Const -> op.value
      is Operand.Series -> engines.value(op.name)
      is Operand.State -> state[op.key] ?: op.default
      is Operand.Math -> {
        val l = evalOperand(op.left, engines, state) ?: return null
        val r = evalOperand(op.right, engines, state) ?: return null
        when (op.op) {
          MathOp.ADD -> l + r
          MathOp.SUB -> l - r
          MathOp.MUL -> l * r
          MathOp.DIV -> if (abs(r) <= 1e-12) null else l / r
          MathOp.MIN -> min(l, r)
          MathOp.MAX -> max(l, r)
        }
      }
      is Operand.Abs -> evalOperand(op.operand, engines, state)?.let { abs(it) }
    }
  }

  private fun executeAction(
    action: ActionJson,
    ruleId: String,
    actionIndex: Int,
    frame: EventFrame,
    engines: SeriesEngines,
    state: MutableMap<String, Double>,
  ): List<Intent> {
    return when (action) {
      is ActionJson.EmitOrder -> {
        val qty = action.qty?.let { evalOperand(it, engines, state) }
        val notional = action.notionalUsd?.let { evalOperand(it, engines, state) }
        val price = action.price?.let { evalOperand(it, engines, state) }
        val meta = HashMap<String, String>()
        action.meta.forEach { (k, v) ->
          evalOperand(v, engines, state)?.let { meta[k] = it.toString() }
        }
        action.metaStrings.forEach { (k, v) -> meta[k] = v }

        listOf(
          Intent(
            id = buildIntentId(ruleId, actionIndex),
            sourceId = ruleId,
            kind = action.kind,
            symbol = action.symbol,
            side = action.side,
            notionalUsd = notional,
            qty = qty,
            priceHint = price,
            meta = meta,
          ),
        )
      }
      is ActionJson.EmitSpread -> {
        val qty = action.qty?.let { evalOperand(it, engines, state) }
        val notional = action.notionalUsd?.let { evalOperand(it, engines, state) }
        val meta = HashMap<String, String>()
        meta["spread.offset_pct"] = action.offsetPct.toString()
        meta["spread.width_pct"] = action.widthPct.toString()
        action.metaStrings.forEach { (k, v) -> meta[k] = v }

        listOf(
          Intent(
            id = buildIntentId(ruleId, actionIndex),
            sourceId = ruleId,
            kind = action.kind,
            symbol = action.symbol,
            side = action.side,
            notionalUsd = notional,
            qty = qty,
            priceHint = null,
            meta = meta,
          ),
        )
      }
      is ActionJson.SetState -> {
        evalOperand(action.value, engines, state)?.let { state[action.key] = it }
        emptyList()
      }
      is ActionJson.IncState -> {
        val delta = evalOperand(action.delta, engines, state)
        if (delta != null) {
          val next = state.getOrDefault(action.key, 0.0) + delta
          state[action.key] = next
        }
        emptyList()
      }
      is ActionJson.ClearState -> {
        state.remove(action.key)
        emptyList()
      }
      is ActionJson.SetStopAtr -> {
        val atr = engines.value(action.atrSeries)
        val meta = mapOf(
          "stop.kind" to "ATR",
          "stop.atr_mult" to action.multiplier.toString(),
          "risk.atr" to (atr ?: 0.0).toString(),
        )
        listOf(
          Intent(
            id = buildIntentId(ruleId, actionIndex),
            sourceId = ruleId,
            kind = action.kind,
            symbol = action.symbol,
            side = action.side,
            meta = meta,
          ),
        )
      }
      is ActionJson.TrailStop -> {
        val meta = mapOf(
          "stop.kind" to "TRAILING",
          "stop.trailing_pct" to action.trailPct.toString(),
        )
        listOf(
          Intent(
            id = buildIntentId(ruleId, actionIndex),
            sourceId = ruleId,
            kind = action.kind,
            symbol = action.symbol,
            side = action.side,
            meta = meta,
          ),
        )
      }
      is ActionJson.Log -> {
        val meta = HashMap<String, String>()
        meta["log.message"] = action.message
        meta["log.level"] = action.level.name
        action.fields.forEach { (k, v) ->
          evalOperand(v, engines, state)?.let { meta["log.$k"] = it.toString() }
        }
        listOf(
          Intent(
            id = buildIntentId(ruleId, actionIndex),
            sourceId = ruleId,
            kind = "log",
            symbol = engines.defaultSymbol,
            side = Side.BUY,
            meta = meta,
          ),
        )
      }
      is ActionJson.Notify -> {
        val meta = HashMap<String, String>()
        meta["notify.channel"] = action.channel
        meta["notify.message"] = action.message
        meta["notify.severity"] = action.severity
        action.fields.forEach { (k, v) ->
          evalOperand(v, engines, state)?.let { meta["notify.$k"] = it.toString() }
        }
        listOf(
          Intent(
            id = buildIntentId(ruleId, actionIndex),
            sourceId = ruleId,
            kind = "notify",
            symbol = engines.defaultSymbol,
            side = Side.BUY,
            meta = meta,
          ),
        )
      }
      is ActionJson.Abort -> {
        listOf(
          Intent(
            id = buildIntentId(ruleId, actionIndex),
            sourceId = ruleId,
            kind = "abort",
            symbol = engines.defaultSymbol,
            side = Side.BUY,
            meta = mapOf("abort.reason" to action.reason),
          ),
        )
      }
    }
  }

  private fun buildIntentId(ruleId: String, actionIndex: Int): String {
    val counter = intentCounter.incrementAndGet()
    return "i-$ruleId-$actionIndex-$counter"
  }
}

private data class EventFrame(
  val ts: Long,
  val bars: Map<String, InputBar?>,
  val fills: List<FillEvent>,
  val pnls: List<PnLEvent>,
)

private class SeriesEngines(
  seriesDefs: List<SeriesDefJson>,
  val defaultSymbol: String,
) {
  private data class SingleEngine(
    val name: String,
    val symbol: String,
    val source: SourceKey,
    val type: SeriesType,
    val ema: EmaIndicator? = null,
    val sma: SmaIndicator? = null,
    val wma: WmaIndicator? = null,
    val rsi: RsiIndicator? = null,
    val zscore: ZScore? = null,
    val roc: RocIndicator? = null,
  )

  private data class MacdBundle(
    val symbol: String,
    val source: SourceKey,
    val indicator: MacdIndicator,
    var lastTs: Long = Long.MIN_VALUE,
    var last: Macd? = null,
  )

  private data class BollBundle(
    val symbol: String,
    val source: SourceKey,
    val indicator: BollingerBands,
    var lastTs: Long = Long.MIN_VALUE,
    var last: Bollinger? = null,
  )

  private data class DonchianBundle(
    val symbol: String,
    val indicator: DonchianChannel,
    var lastTs: Long = Long.MIN_VALUE,
    var last: Donchian? = null,
  )

  private data class AtrBundle(
    val symbol: String,
    val indicator: AtrIndicator,
    var lastTs: Long = Long.MIN_VALUE,
    var last: Double? = null,
  )

  private data class MacdOutput(val name: String, val type: SeriesType, val key: String)
  private data class BollOutput(val name: String, val type: SeriesType, val key: String)
  private data class DonchianOutput(val name: String, val type: SeriesType, val key: String)
  private data class AtrOutput(val name: String, val key: String)

  private val singleEngines = mutableListOf<SingleEngine>()
  private val macdOutputs = mutableMapOf<String, MutableList<MacdOutput>>()
  private val macdBundles = mutableMapOf<String, MacdBundle>()
  private val bollOutputs = mutableMapOf<String, MutableList<BollOutput>>()
  private val bollBundles = mutableMapOf<String, BollBundle>()
  private val donchianOutputs = mutableMapOf<String, MutableList<DonchianOutput>>()
  private val donchianBundles = mutableMapOf<String, DonchianBundle>()
  private val atrOutputs = mutableMapOf<String, MutableList<AtrOutput>>()
  private val atrBundles = mutableMapOf<String, AtrBundle>()

  private val currentValues = HashMap<String, Double?>()
  private val lastBars = HashMap<String, InputBar>()

  init {
    seriesDefs.forEach { def ->
      val symbol = def.symbol ?: defaultSymbol
      when (def.type) {
        SeriesType.EMA -> singleEngines.add(
          SingleEngine(
            name = def.name,
            symbol = symbol,
            source = def.source,
            type = def.type,
            ema = EmaIndicator(def.period ?: error("EMA requires period")),
          ),
        )
        SeriesType.SMA -> singleEngines.add(
          SingleEngine(
            name = def.name,
            symbol = symbol,
            source = def.source,
            type = def.type,
            sma = SmaIndicator(def.period ?: error("SMA requires period")),
          ),
        )
        SeriesType.WMA -> singleEngines.add(
          SingleEngine(
            name = def.name,
            symbol = symbol,
            source = def.source,
            type = def.type,
            wma = WmaIndicator(def.period ?: error("WMA requires period")),
          ),
        )
        SeriesType.RSI -> singleEngines.add(
          SingleEngine(
            name = def.name,
            symbol = symbol,
            source = def.source,
            type = def.type,
            rsi = RsiIndicator(def.period ?: error("RSI requires period")),
          ),
        )
        SeriesType.ZSCORE -> singleEngines.add(
          SingleEngine(
            name = def.name,
            symbol = symbol,
            source = def.source,
            type = def.type,
            zscore = ZScore(def.period ?: error("ZScore requires period")),
          ),
        )
        SeriesType.ROC -> singleEngines.add(
          SingleEngine(
            name = def.name,
            symbol = symbol,
            source = def.source,
            type = def.type,
            roc = RocIndicator(def.period ?: error("ROC requires period")),
          ),
        )
        SeriesType.MACD, SeriesType.MACD_SIGNAL, SeriesType.MACD_HIST -> {
          val fast = def.params["fast"]?.toInt() ?: 12
          val slow = def.params["slow"]?.toInt() ?: 26
          val signalPeriod = def.params["signal"]?.toInt() ?: 9
          val key = "${symbol}:${def.source}:$fast:$slow:$signalPeriod"
          macdBundles.getOrPut(key) {
            MacdBundle(symbol, def.source, MacdIndicator(fast, slow, signalPeriod))
          }
          macdOutputs.getOrPut(key) { mutableListOf() }
            .add(MacdOutput(def.name, def.type, key))
        }
        SeriesType.BOLLINGER_MIDDLE,
        SeriesType.BOLLINGER_UPPER,
        SeriesType.BOLLINGER_LOWER,
        SeriesType.BOLLINGER_STDDEV -> {
          val k = def.params["k"] ?: 2.0
          val period = def.period ?: error("Bollinger requires period")
          val key = "${symbol}:${def.source}:$period:$k"
          bollBundles.getOrPut(key) { BollBundle(symbol, def.source, BollingerBands(period, k)) }
          bollOutputs.getOrPut(key) { mutableListOf() }
            .add(BollOutput(def.name, def.type, key))
        }
        SeriesType.DONCHIAN_UPPER,
        SeriesType.DONCHIAN_LOWER,
        SeriesType.DONCHIAN_MIDDLE -> {
          val period = def.period ?: error("Donchian requires period")
          val key = "${symbol}:$period"
          donchianBundles.getOrPut(key) { DonchianBundle(symbol, DonchianChannel(period)) }
          donchianOutputs.getOrPut(key) { mutableListOf() }
            .add(DonchianOutput(def.name, def.type, key))
        }
        SeriesType.ATR -> {
          val period = def.period ?: 14
          val key = "${symbol}:$period"
          atrBundles.getOrPut(key) { AtrBundle(symbol, AtrIndicator(period)) }
          atrOutputs.getOrPut(key) { mutableListOf() }
            .add(AtrOutput(def.name, key))
        }
      }
    }
  }

  fun update(ts: Long, bars: Map<String, InputBar?>) {
    bars.forEach { (symbol, bar) ->
      if (bar != null) {
        lastBars[symbol] = bar
      }
    }

    singleEngines.forEach { engine ->
      val bar = lastBars[engine.symbol] ?: return@forEach
      val src = when (engine.source) {
        SourceKey.OPEN -> bar.open
        SourceKey.HIGH -> bar.high
        SourceKey.LOW -> bar.low
        SourceKey.CLOSE -> bar.close
        SourceKey.VOLUME -> bar.volume
      }
      val value = when (engine.type) {
        SeriesType.EMA -> engine.ema!!.update(src)
        SeriesType.SMA -> engine.sma!!.update(src)
        SeriesType.WMA -> engine.wma!!.update(src)
        SeriesType.RSI -> engine.rsi!!.update(src)
        SeriesType.ZSCORE -> engine.zscore!!.update(src)
        SeriesType.ROC -> engine.roc!!.update(src)
        else -> null
      }
      currentValues[engine.name] = value
    }

    macdBundles.forEach { (key, bundle) ->
      val bar = lastBars[bundle.symbol]
      if (bar != null) {
        val src = when (bundle.source) {
          SourceKey.OPEN -> bar.open
          SourceKey.HIGH -> bar.high
          SourceKey.LOW -> bar.low
          SourceKey.CLOSE -> bar.close
          SourceKey.VOLUME -> bar.volume
        }
        if (bundle.lastTs != ts) {
          bundle.last = bundle.indicator.update(src)
          bundle.lastTs = ts
        }
        macdOutputs[key]?.forEach { out ->
          val macd = bundle.last
          val value = when (out.type) {
            SeriesType.MACD -> macd?.macd
            SeriesType.MACD_SIGNAL -> macd?.signal
            SeriesType.MACD_HIST -> macd?.hist
            else -> null
          }
          currentValues[out.name] = value
        }
      }
    }

    bollBundles.forEach { (key, bundle) ->
      val bar = lastBars[bundle.symbol]
      if (bar != null) {
        val src = when (bundle.source) {
          SourceKey.OPEN -> bar.open
          SourceKey.HIGH -> bar.high
          SourceKey.LOW -> bar.low
          SourceKey.CLOSE -> bar.close
          SourceKey.VOLUME -> bar.volume
        }
        if (bundle.lastTs != ts) {
          bundle.last = bundle.indicator.update(src)
          bundle.lastTs = ts
        }
        bollOutputs[key]?.forEach { out ->
          val bb = bundle.last
          val value = when (out.type) {
            SeriesType.BOLLINGER_MIDDLE -> bb?.middle
            SeriesType.BOLLINGER_UPPER -> bb?.upper
            SeriesType.BOLLINGER_LOWER -> bb?.lower
            SeriesType.BOLLINGER_STDDEV -> bb?.stddev
            else -> null
          }
          currentValues[out.name] = value
        }
      }
    }

    donchianBundles.forEach { (key, bundle) ->
      val bar = lastBars[bundle.symbol]
      if (bar != null && bundle.lastTs != ts) {
        bundle.last = bundle.indicator.update(bar.high, bar.low)
        bundle.lastTs = ts
      }
      donchianOutputs[key]?.forEach { out ->
        val dc = bundle.last
        val value = when (out.type) {
          SeriesType.DONCHIAN_UPPER -> dc?.upper
          SeriesType.DONCHIAN_LOWER -> dc?.lower
          SeriesType.DONCHIAN_MIDDLE -> dc?.middle
          else -> null
        }
        currentValues[out.name] = value
      }
    }

    atrBundles.forEach { (key, bundle) ->
      val bar = lastBars[bundle.symbol]
      if (bar != null && bundle.lastTs != ts) {
        bundle.last = bundle.indicator.update(bar.high, bar.low, bar.close)
        bundle.lastTs = ts
      }
      atrOutputs[key]?.forEach { out ->
        currentValues[out.name] = bundle.last
      }
    }

    lastBars.forEach { (symbol, bar) ->
      currentValues["open:$symbol"] = bar.open
      currentValues["high:$symbol"] = bar.high
      currentValues["low:$symbol"] = bar.low
      currentValues["close:$symbol"] = bar.close
      currentValues["volume:$symbol"] = bar.volume
      if (symbol == defaultSymbol) {
        currentValues["open"] = bar.open
        currentValues["high"] = bar.high
        currentValues["low"] = bar.low
        currentValues["close"] = bar.close
        currentValues["volume"] = bar.volume
      }
    }
  }

  fun value(name: String): Double? = currentValues[name]
}

private class RuntimeCtx(
  private val interval: Interval,
  private val defaultSymbol: String,
) {
  private val lastBarByRule = HashMap<String, Long>()
  private val lastPairs = HashMap<String, Pair<Double?, Double?>>()
  private val pendingAt = HashMap<String, Long?>()
  private val quota = HashMap<String, ArrayDeque<Long>>()
  private val scheduleBase = HashMap<String, Long>()
  private val scheduleDue = HashMap<String, Long>()
  private val scheduleOnce = HashMap<String, MutableSet<Long>>()

  fun shouldTrigger(rule: RuleJson, frame: EventFrame): Boolean {
    return when (val event = rule.event) {
      is EventJson.Candle -> {
        val symbol = event.symbol ?: defaultSymbol
        val bar = frame.bars[symbol] ?: return false
        if (rule.oncePerBar && !allowOncePerBar(rule.id, bar.ts)) return false
        true
      }
      is EventJson.Schedule -> allowSchedule(rule.id, frame.ts, event)
      is EventJson.Fill -> frame.fills.any { fill ->
        (event.symbol == null || event.symbol == fill.symbol) &&
          (event.side == null || event.side == fill.side)
      }
      is EventJson.PnL -> frame.pnls.any { pnl ->
        if (event.symbol != null && pnl.symbol != event.symbol) return@any false
        val realizedOk = event.realizedThreshold?.let { threshold ->
          if (threshold >= 0) pnl.realized >= threshold else pnl.realized <= threshold
        } ?: true
        val unrealizedOk = event.unrealizedThreshold?.let { threshold ->
          if (threshold >= 0) pnl.unrealized >= threshold else pnl.unrealized <= threshold
        } ?: true
        realizedOk && unrealizedOk
      }
    }
  }

  private fun allowOncePerBar(ruleId: String, ts: Long): Boolean {
    val bar = barBucket(ts)
    val last = lastBarByRule[ruleId]
    return if (last == bar) {
      false
    } else {
      lastBarByRule[ruleId] = bar
      true
    }
  }

  private fun allowSchedule(ruleId: String, ts: Long, schedule: EventJson.Schedule): Boolean {
    val atSet = if (schedule.at.isNotEmpty()) {
      scheduleOnce.getOrPut(ruleId) { schedule.at.toMutableSet() }
    } else null
    if (atSet != null && atSet.remove(ts)) return true

    val every = schedule.everyMs
    if (every == null || every <= 0) {
      return false
    }

    val base = scheduleBase.getOrPut(ruleId) { ts + schedule.offsetMs }
    val due = scheduleDue.getOrPut(ruleId) { base }
    if (ts >= due) {
      var next = due + every
      if (next <= ts) {
        val laps = max(1L, (ts - due) / every + 1)
        next = due + laps * every
      }
      scheduleDue[ruleId] = next
      return true
    }
    return false
  }

  fun evalCrosses(key: String, dir: CrossDir, left: Double?, right: Double?): Boolean {
    if (left == null || right == null) return false
    val pair = lastPairs.getOrPut(key) { Pair(null, null) }
    val prevLeft = pair.first
    val prevRight = pair.second
    val crossed = when (dir) {
      CrossDir.ABOVE -> prevLeft != null && prevRight != null && prevLeft <= prevRight && left > right
      CrossDir.BELOW -> prevLeft != null && prevRight != null && prevLeft >= prevRight && left < right
    }
    lastPairs[key] = Pair(left, right)
    return crossed
  }

  fun updateTimerAndCheck(ruleId: String, ts: Long, delayMs: Long?, guardPass: Boolean): Boolean {
    val due = pendingAt[ruleId]
    if (guardPass) {
      if (delayMs == null || delayMs <= 0) return true
      val newDue = due ?: (ts + delayMs)
      pendingAt[ruleId] = newDue
      if (ts >= newDue) {
        pendingAt.remove(ruleId)
        return true
      }
      return false
    } else {
      if (due != null && ts >= due) {
        pendingAt.remove(ruleId)
        return true
      }
      return false
    }
  }

  fun quotaPrecheck(ruleId: String, ts: Long, q: QuotaJson?): Boolean {
    q ?: return true
    val dq = quota.getOrPut(ruleId) { ArrayDeque() }
    val windowStart = ts - q.windowMs
    while (dq.isNotEmpty() && dq.first() < windowStart) dq.removeFirst()
    return dq.size < q.max
  }

  fun recordEmit(ruleId: String, ts: Long, q: QuotaJson?) {
    q ?: return
    val dq = quota.getOrPut(ruleId) { ArrayDeque() }
    dq.addLast(ts)
  }

  fun updateEventState(frame: EventFrame, state: MutableMap<String, Double>) {
    frame.bars.forEach { (symbol, bar) ->
      if (bar != null) {
        state["bar.open.$symbol"] = bar.open
        state["bar.high.$symbol"] = bar.high
        state["bar.low.$symbol"] = bar.low
        state["bar.close.$symbol"] = bar.close
        state["bar.volume.$symbol"] = bar.volume
        if (symbol == defaultSymbol) {
          state["bar.open"] = bar.open
          state["bar.high"] = bar.high
          state["bar.low"] = bar.low
          state["bar.close"] = bar.close
          state["bar.volume"] = bar.volume
        }
      }
    }

    frame.fills.forEach { fill ->
      state["event.fill.price.${fill.symbol}.${fill.side}"] = fill.price
      state["event.fill.qty.${fill.symbol}.${fill.side}"] = fill.qty
      state["event.fill.ts"] = fill.ts.toDouble()
    }

    frame.pnls.forEach { pnl ->
      state["event.pnl.realized.${pnl.symbol}"] = pnl.realized
      state["event.pnl.unrealized.${pnl.symbol}"] = pnl.unrealized
      if (pnl.symbol == defaultSymbol) {
        state["event.pnl.realized"] = pnl.realized
        state["event.pnl.unrealized"] = pnl.unrealized
      }
    }
  }

  private fun barBucket(ts: Long): Long {
    val ms = when (interval) {
      Interval.M1 -> 60_000L
      Interval.M5 -> 5 * 60_000L
      Interval.M15 -> 15 * 60_000L
      Interval.M30 -> 30 * 60_000L
      Interval.H1 -> 60 * 60_000L
      Interval.H4 -> 4 * 60 * 60_000L
      Interval.D1 -> 24 * 60 * 60_000L
    }
    return (ts / ms) * ms
  }
}

object InputLoader {
  fun fromCsv(path: String): List<InputBar> {
    val file = resolvePath(path)
    val lines = file.readLines().drop(1)
    return lines.map { ln ->
      val p = ln.split(',')
      InputBar(
        ts = p[0].toLong(),
        open = p[1].toDouble(),
        high = p[2].toDouble(),
        low = p[3].toDouble(),
        close = p[4].toDouble(),
        volume = p[5].toDouble(),
      )
    }
  }

  fun fromInline(bars: List<InputBarJson>): List<InputBar> {
    return bars.map { InputBar(it.ts, it.open, it.high, it.low, it.close, it.volume) }
  }

  fun loadInputs(program: ProgramJson): Map<String, List<InputBar>> {
    if (program.inputs.isNotEmpty()) {
      return program.inputs.associate { src ->
        val bars = when {
          src.inline != null -> fromInline(src.inline)
          src.csvPath != null -> fromCsv(src.csvPath)
          else -> emptyList()
        }
        src.symbol to bars
      }
    }

    val symbol = program.defaultSymbol ?: "symbol"
    program.inputsInline?.let { inline -> return mapOf(symbol to fromInline(inline)) }
    program.inputsCsvPath?.let { path -> return mapOf(symbol to fromCsv(path)) }
    return emptyMap()
  }

  private fun resolvePath(path: String): File {
    val candidates = listOf(
      File(path),
      File("../$path"),
      File("../../$path"),
      File("../../../$path"),
    )
    return candidates.firstOrNull { it.exists() }
      ?: error("Input CSV not found at $path or parent dirs")
  }
}
