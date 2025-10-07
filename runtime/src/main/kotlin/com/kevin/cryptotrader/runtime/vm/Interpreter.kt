package com.kevin.cryptotrader.runtime.vm

import com.kevin.cryptotrader.contracts.BacktestSample
import com.kevin.cryptotrader.contracts.Intent
import com.kevin.cryptotrader.contracts.Interval
import com.kevin.cryptotrader.contracts.PipelineObserver
import com.kevin.cryptotrader.contracts.ResourceUsageSample
import com.kevin.cryptotrader.contracts.RuntimeEnv
import com.kevin.cryptotrader.contracts.Side
import com.kevin.cryptotrader.core.indicators.EmaIndicator
import java.io.File
import kotlin.math.max
import kotlin.system.measureNanoTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

data class InputBar(val ts: Long, val open: Double, val high: Double, val low: Double, val close: Double, val volume: Double)

/** Build and run an interpreted program defined by ProgramJson */
class Interpreter(
  private val program: ProgramJson,
  private val observer: PipelineObserver = PipelineObserver.NOOP,
) {

  private val compiledRules = program.rules.map { CompiledRule(it) }

  fun run(input: List<InputBar>, env: RuntimeEnv): Flow<Intent> = flow {
    val seriesEngines = SeriesEngines(program.series)
    val ctx = RuntimeCtx(program.interval)

    var barCounter = 0
    input.forEach { bar ->
      lateinit var snapshot: SeriesSnapshot
      val updateNs = measureNanoTime { snapshot = seriesEngines.update(bar) }

      var emittedForBar = 0
      val evalNs = measureNanoTime {
        for (rule in compiledRules) {
          if (rule.oncePerBar && !ctx.allowOncePerBar(rule.id, bar.ts)) continue

          val guardPass = rule.guard.evaluate(snapshot, ctx)
          val ready = ctx.updateTimerAndCheck(rule.id, bar.ts, rule.delayMs, guardPass)
          val allowed = ready && ctx.quotaPrecheck(rule.id, bar.ts, rule.quota)
          if (allowed) {
            val out = toIntent(rule.action, rule.id)
            emit(out)
            ctx.recordEmit(rule.id, bar.ts, rule.quota)
            emittedForBar += 1
          }
        }
      }

      observer.onBacktestSample(
        BacktestSample(
          barTs = bar.ts,
          updateDurationMs = updateNs.toDouble() / 1_000_000.0,
          evaluationDurationMs = evalNs.toDouble() / 1_000_000.0,
          emittedIntents = emittedForBar,
        ),
      )

      if (barCounter % RESOURCE_SAMPLE_EVERY == 0) {
        val runtime = Runtime.getRuntime()
        val used = runtime.totalMemory() - runtime.freeMemory()
        observer.onResourceSample(
          ResourceUsageSample(
            timestampMs = max(bar.ts, env.clockMs()),
            heapUsedBytes = used,
            heapMaxBytes = runtime.maxMemory(),
          ),
        )
      }

      barCounter += 1
    }
  }

  private fun toIntent(action: ActionJson, sourceId: String): Intent {
    return Intent(
      id = "i-" + sourceId + "-" + System.identityHashCode(this),
      sourceId = sourceId,
      kind = action.kind,
      symbol = action.symbol,
      side = action.side,
      notionalUsd = null,
      qty = null,
      priceHint = null,
      meta = emptyMap(),
    )
  }

  private inner class CompiledRule(rule: RuleJson) {
    val id: String = rule.id
    val oncePerBar: Boolean = rule.oncePerBar
    val action: ActionJson = rule.action
    val quota: QuotaJson? = rule.quota
    val delayMs: Long? = rule.delayMs
    val guard: GuardEvaluator = when (val g = rule.guard) {
      is GuardJson.Threshold -> GuardEvaluator.ThresholdEvaluator(
        resolveOperand(g.left),
        g.op,
        resolveOperand(g.right),
      )
      is GuardJson.Crosses -> GuardEvaluator.CrossEvaluator(
        resolveOperand(g.left),
        resolveOperand(g.right),
        CrossCacheKey(rule.id, g),
      )
    }
  }

  private fun resolveOperand(op: Operand): OperandResolver =
    when (op) {
      is Operand.Const -> OperandResolver.ConstOperand(op.value)
      is Operand.Series -> OperandResolver.SeriesOperand(op.name)
    }

  private sealed interface GuardEvaluator {
    fun evaluate(snapshot: SeriesSnapshot, ctx: RuntimeCtx): Boolean

    class ThresholdEvaluator(
      private val left: OperandResolver,
      private val op: Op,
      private val right: OperandResolver,
    ) : GuardEvaluator {
      override fun evaluate(snapshot: SeriesSnapshot, ctx: RuntimeCtx): Boolean {
        val l = left.resolve(snapshot) ?: return false
        val r = right.resolve(snapshot) ?: return false
        return when (op) {
          Op.GT -> l > r
          Op.GTE -> l >= r
          Op.LT -> l < r
          Op.LTE -> l <= r
          Op.EQ -> l == r
        }
      }
    }

    class CrossEvaluator(
      private val left: OperandResolver,
      private val right: OperandResolver,
      private val cacheKey: CrossCacheKey,
    ) : GuardEvaluator {
      override fun evaluate(snapshot: SeriesSnapshot, ctx: RuntimeCtx): Boolean {
        val l = left.resolve(snapshot) ?: return false
        val r = right.resolve(snapshot) ?: return false
        return ctx.evalCrosses(cacheKey, l, r)
      }
    }
  }

  private sealed interface OperandResolver {
    fun resolve(snapshot: SeriesSnapshot): Double?

    class ConstOperand(private val value: Double) : OperandResolver {
      override fun resolve(snapshot: SeriesSnapshot): Double? = value
    }

    class SeriesOperand(private val name: String) : OperandResolver {
      private var cachedVersion: Long = Long.MIN_VALUE
      private var cachedValue: Double? = null

      override fun resolve(snapshot: SeriesSnapshot): Double? {
        if (cachedVersion != snapshot.version) {
          cachedValue = snapshot.values[name]
          cachedVersion = snapshot.version
        }
        return cachedValue
      }
    }
  }

  private companion object {
    private const val RESOURCE_SAMPLE_EVERY = 128
  }
}

private data class CrossCacheKey(val ruleId: String, val guard: GuardJson.Crosses)

private class SeriesEngines(seriesDefs: List<SeriesDefJson>) {
  private data class Engine(
    val name: String,
    val type: SeriesType,
    val ema: EmaIndicator?,
    val extractor: (InputBar) -> Double,
  )

  private val engines: List<Engine>
  private val currentValues = HashMap<String, Double?>()
  private var version: Long = 0

  init {
    engines = seriesDefs.map { def ->
      when (def.type) {
        SeriesType.EMA ->
          Engine(
            name = def.name,
            type = def.type,
            ema = EmaIndicator(def.period ?: error("EMA requires period")),
            extractor = sourceExtractor(def.source),
          )
      }
    }
  }

  fun update(bar: InputBar): SeriesSnapshot {
    version += 1
    engines.forEach { e ->
      val src = e.extractor(bar)
      val v = when (e.type) {
        SeriesType.EMA -> e.ema!!.update(src)
      }
      currentValues[e.name] = v
    }
    // Expose raw sources as well
    currentValues["open"] = bar.open
    currentValues["high"] = bar.high
    currentValues["low"] = bar.low
    currentValues["close"] = bar.close
    currentValues["volume"] = bar.volume
    return SeriesSnapshot(version = version, values = currentValues)
  }

  private fun sourceExtractor(source: SourceKey): (InputBar) -> Double =
    when (source) {
      SourceKey.OPEN -> { bar -> bar.open }
      SourceKey.HIGH -> { bar -> bar.high }
      SourceKey.LOW -> { bar -> bar.low }
      SourceKey.CLOSE -> { bar -> bar.close }
      SourceKey.VOLUME -> { bar -> bar.volume }
    }
}

private data class SeriesSnapshot(val version: Long, val values: Map<String, Double?>)

private class RuntimeCtx(private val interval: Interval) {
  private val lastBarByRule = HashMap<String, Long>()
  private val lastPairs = HashMap<CrossCacheKey, Pair<Double, Double>>()
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

  fun evalCrosses(key: CrossCacheKey, left: Double, right: Double): Boolean {
    val (prevL, prevR) = lastPairs[key] ?: Pair(Double.NaN, Double.NaN)
    val crossed = when (key.guard.dir) {
      CrossDir.ABOVE -> !prevL.isNaN() && !prevR.isNaN() && prevL <= prevR && left > right
      CrossDir.BELOW -> !prevL.isNaN() && !prevR.isNaN() && prevL >= prevR && left < right
    }
    lastPairs[key] = left to right
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
