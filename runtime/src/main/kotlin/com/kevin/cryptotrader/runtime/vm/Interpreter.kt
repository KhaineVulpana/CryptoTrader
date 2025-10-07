package com.kevin.cryptotrader.runtime.vm

import com.kevin.cryptotrader.contracts.Intent
import com.kevin.cryptotrader.contracts.Interval
import com.kevin.cryptotrader.contracts.Side
import com.kevin.cryptotrader.core.indicators.EmaIndicator
import com.kevin.cryptotrader.core.indicators.RsiIndicator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.File

data class InputBar(val ts: Long, val open: Double, val high: Double, val low: Double, val close: Double, val volume: Double)

/** Build and run an interpreted program defined by ProgramJson */
class Interpreter(private val program: ProgramJson) {

  fun run(input: List<InputBar>): Flow<Intent> = flow {
    val seriesEngines = SeriesEngines(program.series)
    val ctx = RuntimeCtx(program.interval)

    input.forEach { bar ->
      seriesEngines.update(bar)
      for (rule in program.rules) {
        if (rule.oncePerBar && !ctx.allowOncePerBar(rule.id, bar.ts)) continue

        val guardPass = when (val g = rule.guard) {
          is GuardJson.Threshold -> evalThreshold(g, seriesEngines)
          is GuardJson.Crosses -> ctx.evalCrosses(g, seriesEngines)
        }

        val ready = ctx.updateTimerAndCheck(rule.id, bar.ts, rule.delayMs, guardPass)
        val allowed = ready && ctx.quotaPrecheck(rule.id, bar.ts, rule.quota)
        if (allowed) {
          val out = toIntent(rule.action, rule.id)
          emit(out)
          ctx.recordEmit(rule.id, bar.ts, rule.quota)
        }
      }
    }
  }

  private fun toIntent(action: ActionJson, sourceId: String): Intent {
    val baseMeta = action.meta + mapOf("action.type" to action.type.name.lowercase())
    val symbol = if (action.symbol.isBlank()) "GLOBAL" else action.symbol
    return Intent(
      id = "i-" + sourceId + "-" + System.identityHashCode(this),
      sourceId = sourceId,
      kind = action.kind,
      symbol = symbol,
      side = action.side,
      notionalUsd = null,
      qty = null,
      priceHint = null,
      meta = baseMeta,
    )
  }

  private fun evalThreshold(t: GuardJson.Threshold, engines: SeriesEngines): Boolean {
    val l = engines.resolve(t.left) ?: return false
    val r = engines.resolve(t.right) ?: return false
    return when (t.op) {
      Op.GT -> l > r
      Op.GTE -> l >= r
      Op.LT -> l < r
      Op.LTE -> l <= r
      Op.EQ -> l == r
    }
  }
}

private class SeriesEngines(seriesDefs: List<SeriesDefJson>) {
  private data class Engine(
    val name: String,
    val type: SeriesType,
    val ema: EmaIndicator? = null,
    val rsi: RsiIndicator? = null,
    val source: SourceKey,
  )
  private val engines: List<Engine>
  private var last: InputBar? = null
  private val currentValues = HashMap<String, Double?>()

  init {
    engines = seriesDefs.map { def ->
      when (def.type) {
        SeriesType.EMA -> Engine(def.name, def.type, ema = EmaIndicator(def.period ?: error("EMA requires period")), source = def.source)
        SeriesType.RSI -> Engine(def.name, def.type, rsi = RsiIndicator(def.period ?: 14), source = def.source)
        SeriesType.CUSTOM -> Engine(def.name, def.type, source = def.source)
      }
    }
  }

  fun update(bar: InputBar) {
    last = bar
    engines.forEach { e ->
      val src = when (e.source) {
        SourceKey.OPEN -> bar.open
        SourceKey.HIGH -> bar.high
        SourceKey.LOW -> bar.low
        SourceKey.CLOSE -> bar.close
        SourceKey.VOLUME -> bar.volume
      }
      val v = when (e.type) {
        SeriesType.EMA -> e.ema!!.update(src)
        SeriesType.RSI -> e.rsi!!.update(src)
        SeriesType.CUSTOM -> null
      }
      currentValues[e.name] = v
    }
    // Expose raw sources as well
    currentValues["open"] = bar.open
    currentValues["high"] = bar.high
    currentValues["low"] = bar.low
    currentValues["close"] = bar.close
    currentValues["volume"] = bar.volume
  }

  fun value(name: String): Double? = currentValues[name]

  fun resolve(op: Operand): Double? = when (op) {
    is Operand.Const -> op.value
    is Operand.Series -> value(op.name)
  }
}

private class RuntimeCtx(private val interval: Interval) {
  private val lastBarByRule = HashMap<String, Long>()
  private val lastPairs = HashMap<String, Pair<Double?, Double?>>() // key -> (prevLeft, prevRight)
  private val pendingAt = HashMap<String, Long?>() // ruleId -> dueTs
  private val quota = HashMap<String, ArrayDeque<Long>>()

  fun allowOncePerBar(ruleId: String, ts: Long): Boolean {
    val bar = barBucket(ts)
    val last = lastBarByRule[ruleId]
    return if (last == bar) {
      false
    } else {
      lastBarByRule[ruleId] = bar
      true
    }
  }

  fun evalCrosses(c: GuardJson.Crosses, engines: SeriesEngines): Boolean {
    val l = engines.resolve(c.left) ?: return false
    val r = engines.resolve(c.right) ?: return false
    val key = "${c.left}-${c.right}-${c.dir}"
    val (prevL, prevR) = lastPairs.getOrPut(key) { Pair(null, null) }
    val crossed = when (c.dir) {
      CrossDir.ABOVE -> (prevL != null && prevR != null && prevL <= prevR && l > r)
      CrossDir.BELOW -> (prevL != null && prevR != null && prevL >= prevR && l < r)
    }
    lastPairs[key] = Pair(l, r)
    return crossed
  }

  fun updateTimerAndCheck(ruleId: String, ts: Long, delayMs: Long?, guardPass: Boolean): Boolean {
    val due = pendingAt[ruleId]
    if (guardPass) {
      if (delayMs == null || delayMs <= 0) return true
      val newDue = (due ?: (ts + delayMs))
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
      InputBar(p[0].toLong(), p[1].toDouble(), p[2].toDouble(), p[3].toDouble(), p[4].toDouble(), p[5].toDouble())
    }
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
