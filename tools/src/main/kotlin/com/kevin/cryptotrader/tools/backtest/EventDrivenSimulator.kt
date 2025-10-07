package com.kevin.cryptotrader.tools.backtest

import com.kevin.cryptotrader.contracts.AccountSnapshot
import com.kevin.cryptotrader.contracts.Broker
import com.kevin.cryptotrader.contracts.BrokerEvent
import com.kevin.cryptotrader.contracts.Candle
import com.kevin.cryptotrader.contracts.EquityPoint
import com.kevin.cryptotrader.runtime.execution.ExecutionConfig
import com.kevin.cryptotrader.runtime.execution.Ledger
import com.kevin.cryptotrader.runtime.execution.LedgerEvent
import com.kevin.cryptotrader.contracts.Order
import com.kevin.cryptotrader.contracts.PolicyConfig
import com.kevin.cryptotrader.contracts.PolicyEngine
import com.kevin.cryptotrader.contracts.Position
import com.kevin.cryptotrader.contracts.RiskSizer
import com.kevin.cryptotrader.contracts.Side
import com.kevin.cryptotrader.contracts.SimulationConfig
import com.kevin.cryptotrader.contracts.SimulationLatencyConfig
import com.kevin.cryptotrader.contracts.SimulationMetrics
import com.kevin.cryptotrader.contracts.SimulationResult
import com.kevin.cryptotrader.contracts.SimulationSliceResult
import com.kevin.cryptotrader.contracts.SimulationCosts
import com.kevin.cryptotrader.contracts.TradeRecord
import com.kevin.cryptotrader.contracts.WalkForwardSplit
import com.kevin.cryptotrader.core.policy.PolicyEngineImpl
import com.kevin.cryptotrader.core.policy.RiskSizerImpl
import com.kevin.cryptotrader.runtime.execution.DefaultPolicyEngine
import com.kevin.cryptotrader.runtime.execution.ExecutionCoordinator
import com.kevin.cryptotrader.runtime.vm.InputBar
import com.kevin.cryptotrader.runtime.vm.Interpreter
import com.kevin.cryptotrader.runtime.vm.ProgramJson
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.PriorityQueue
import java.util.TreeMap
import kotlin.collections.ArrayDeque
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json

class EventDrivenSimulator(
  private val json: Json = Json { ignoreUnknownKeys = true },
) {
  companion object {
    private const val ACCOUNT_ID = "SIM"
    private const val EPS = 1e-9
    private const val MS_PER_YEAR = 365.25 * 24 * 60 * 60 * 1000.0

    private val ZERO_METRICS = SimulationMetrics(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0)
  }

  suspend fun run(config: SimulationConfig): SimulationResult {
    require(config.candles.isNotEmpty()) { "Simulation requires candle data" }
    val splits = config.splits.ifEmpty { defaultSplit(config.candles) }
    val program = json.decodeFromString(ProgramJson.serializer(), config.automation.graphJson)

    val slices = splits.map { split -> runSplit(config, split, program) }
    val aggregated = aggregateSlices(slices, config.initialEquityUsd)
    return SimulationResult(config.runId, slices, aggregated)
  }

  fun runBlocking(config: SimulationConfig): SimulationResult = runBlocking { run(config) }

  private fun defaultSplit(candles: List<Candle>): List<WalkForwardSplit> {
    val first = candles.first()
    val last = candles.last()
    return listOf(
      WalkForwardSplit(
        id = "split-all",
        label = "Full Sample",
        inSampleStart = first.ts,
        inSampleEnd = first.ts,
        outSampleStart = first.ts,
        outSampleEnd = last.ts,
      ),
    )
  }

  private suspend fun runSplit(
    config: SimulationConfig,
    split: WalkForwardSplit,
    program: ProgramJson,
  ): SimulationSliceResult {
    val bars = config.candles.filter { it.ts in split.inSampleStart..split.outSampleEnd }
    if (bars.isEmpty()) {
      return SimulationSliceResult(split, ZERO_METRICS, emptyList(), emptyList())
    }

    val interpreter = Interpreter(program)
    val inputs = bars.map { it.toInputBar() }
    val intents = interpreter.run(inputs).toList()
    val intentsByTs = intents.filter { it.ts != null }.groupBy { it.ts!! }

    val clock = MutableClock(bars.first().ts)
    val priceHistory = TreeMap<Long, Double>()
    val simulationState = SimulationState(config.initialEquityUsd)
    val broker = SimulatedBroker(
      latency = config.latency,
      costs = config.costs,
      clock = clock::millis,
      priceLookup = { _, ts ->
        priceHistory.floorEntry(ts)?.value ?: priceHistory.ceilingEntry(ts)?.value
      },
      accountProvider = simulationState::accountSnapshot,
    )
    val ledger = InMemoryLedger()
    val policy = policyEngine(config.policyConfig)
    val riskSizer = riskSizer()
    val coordinator = ExecutionCoordinator(
      broker = broker,
      policyEngine = policy,
      riskSizer = riskSizer,
      ledger = ledger,
      clock = clock,
      config = ExecutionConfig(),
    )

    val activeOrders = mutableMapOf<String, Order>()
    val testRange = split.outSampleStart..split.outSampleEnd

    priceHistory[bars.first().ts] = bars.first().close
    simulationState.latestPrices[config.symbol] = bars.first().close
    simulationState.snapshotEquity(bars.first().ts)

    for (bar in bars) {
      priceHistory[bar.ts] = bar.close
      simulationState.latestPrices[config.symbol] = bar.close

      broker.advanceTo(bar.ts) { event ->
        handleBrokerEvent(event, activeOrders, simulationState, config.costs)
      }

      val intentsForBar = intentsByTs[bar.ts].orEmpty()
      val enrichedIntents = intentsForBar.map { intent ->
        val mergedMeta = if (config.defaultIntentMeta.isEmpty()) intent.meta else config.defaultIntentMeta + intent.meta
        val priceHint = intent.priceHint ?: bar.close
        val notionalHint = intent.notionalUsd ?: mergedMeta.firstDouble(
          "risk.notional",
          "risk.notional_usd",
          "notional",
          "notional_usd",
        )
        val qtyHint = intent.qty ?: mergedMeta.firstDouble(
          "risk.qty",
          "qty",
        )
        val resolvedQty = qtyHint ?: notionalHint?.takeIf { priceHint > EPS }?.let { it / priceHint }
        val resolvedNotional = notionalHint ?: qtyHint?.let { priceHint * it }
        intent.copy(
          meta = mergedMeta,
          priceHint = priceHint,
          notionalUsd = resolvedNotional,
          qty = resolvedQty,
        )
      }
      if (bar.ts in testRange && enrichedIntents.isNotEmpty()) {
        clock.set(bar.ts)
        val positions = simulationState.toPositions()
        coordinator.coordinate(enrichedIntents, positions)
      }

      broker.advanceTo(bar.ts) { event ->
        handleBrokerEvent(event, activeOrders, simulationState, config.costs)
      }

      simulationState.snapshotEquity(bar.ts)
    }

    val settleHorizon = split.outSampleEnd + config.latency.firstFillLatencyMs +
      (config.latency.partialPieces.toLong().coerceAtLeast(1L) * config.latency.perFillIntervalMs)
    clock.set(settleHorizon)
    broker.advanceTo(settleHorizon) { event ->
      handleBrokerEvent(event, activeOrders, simulationState, config.costs)
    }

    if (simulationState.equitySeries.lastOrNull()?.ts != settleHorizon) {
      simulationState.snapshotEquity(settleHorizon)
    }

    val metrics = metricsFromSeries(simulationState.equitySeries, simulationState.trades)
    return SimulationSliceResult(split, metrics, simulationState.equitySeries.toList(), simulationState.trades.toList())
  }

  private fun metricsFromSeries(equity: List<EquityPoint>, trades: List<TradeRecord>): SimulationMetrics {
    if (equity.size < 2) {
      val winRate = if (trades.isEmpty()) 0.0 else trades.count { it.pnlUsd > 0 }.toDouble() / trades.size
      val exposure = equity.firstOrNull()?.exposure ?: 0.0
      return ZERO_METRICS.copy(winRate = winRate, averageExposure = exposure)
    }
    val samples = samplesFromEquity(equity)
    return metricsFromSamples(samples, trades)
  }

  private fun aggregateSlices(slices: List<SimulationSliceResult>, initialEquity: Double): SimulationMetrics {
    if (slices.isEmpty()) return ZERO_METRICS
    val samples = slices.flatMap { samplesFromEquity(it.equity) }
    val trades = slices.flatMap { it.trades }
    return metricsFromSamples(samples, trades)
  }

  private fun metricsFromSamples(samples: List<ReturnSample>, trades: List<TradeRecord>): SimulationMetrics {
    if (samples.isEmpty()) {
      val winRate = if (trades.isEmpty()) 0.0 else trades.count { it.pnlUsd > 0 }.toDouble() / trades.size
      return ZERO_METRICS.copy(winRate = winRate)
    }
    val totalDuration = samples.sumOf { it.durationMs }
    val years = if (totalDuration > 0) totalDuration / MS_PER_YEAR else 0.0
    val totalReturnMultiplier = samples.fold(1.0) { acc, sample -> acc * (1.0 + sample.ret) }
    val cagr = if (years > 0 && totalReturnMultiplier > 0.0) totalReturnMultiplier.pow(1.0 / years) - 1.0 else 0.0

    val returns = samples.map { it.ret }
    val meanReturn = returns.average()
    val variance = if (returns.size > 1) {
      returns.sumOf { (it - meanReturn).pow(2) } / (returns.size - 1)
    } else {
      0.0
    }
    val stdDev = sqrt(max(variance, 0.0))
    val periodsPerYear = if (years > 0) returns.size / years else 0.0
    val sharpe = if (stdDev > 0.0 && periodsPerYear > 0.0) meanReturn / stdDev * sqrt(periodsPerYear) else 0.0

    val downside = returns.filter { it < 0.0 }
    val downsideDeviation = if (downside.isNotEmpty()) {
      sqrt(downside.sumOf { it.pow(2) } / downside.size)
    } else {
      0.0
    }
    val sortino = if (downsideDeviation > 0.0 && periodsPerYear > 0.0) meanReturn / downsideDeviation * sqrt(periodsPerYear) else 0.0

    var peak = 1.0
    var equity = 1.0
    var maxDrawdown = 0.0
    for (sample in samples) {
      equity *= (1.0 + sample.ret)
      if (equity > peak) peak = equity
      if (peak > 0) {
        val dd = (equity / peak) - 1.0
        if (dd < maxDrawdown) maxDrawdown = dd
      }
    }
    val mar = if (maxDrawdown < 0.0) cagr / abs(maxDrawdown) else 0.0

    val winRate = if (trades.isNotEmpty()) trades.count { it.pnlUsd > 0 }.toDouble() / trades.size else 0.0
    val avgExposure = if (totalDuration > 0) {
      samples.sumOf { it.exposure * it.durationMs } / totalDuration
    } else {
      0.0
    }

    return SimulationMetrics(
      cagr = cagr,
      sharpe = sharpe,
      sortino = sortino,
      maxDrawdown = maxDrawdown,
      mar = mar,
      winRate = winRate,
      averageExposure = avgExposure,
    )
  }

  private fun samplesFromEquity(series: List<EquityPoint>): List<ReturnSample> {
    if (series.size < 2) return emptyList()
    return series.zipWithNext().map { (a, b) ->
      val ret = if (a.equity > 0.0) (b.equity / a.equity) - 1.0 else 0.0
      val duration = (b.ts - a.ts).coerceAtLeast(0)
      val exposure = (a.exposure + b.exposure) / 2.0
      ReturnSample(ret, duration, exposure)
    }
  }

  private fun policyEngine(policyConfig: PolicyConfig): PolicyEngine =
    if (policyConfig == PolicyConfig()) {
      DefaultPolicyEngine(ExecutionConfig())
    } else {
      PolicyEngineImpl(policyConfig)
    }

  private fun riskSizer(): RiskSizer = RiskSizerImpl()

  private fun handleBrokerEvent(
    event: BrokerEvent,
    activeOrders: MutableMap<String, Order>,
    state: SimulationState,
    costs: SimulationCosts,
  ) {
    when (event) {
      is BrokerEvent.Accepted -> activeOrders[event.orderId] = event.order
      is BrokerEvent.PartialFill -> applyFill(event.orderId, event.fill, activeOrders, state, costs)
      is BrokerEvent.Filled -> {
        applyFill(event.orderId, event.fill, activeOrders, state, costs)
        activeOrders.remove(event.orderId)
      }
      is BrokerEvent.Canceled -> activeOrders.remove(event.orderId)
      is BrokerEvent.Rejected -> activeOrders.remove(event.orderId)
    }
  }

  private fun applyFill(
    orderId: String,
    fill: com.kevin.cryptotrader.contracts.Fill,
    active: MutableMap<String, Order>,
    state: SimulationState,
    costs: SimulationCosts,
  ) {
    val order = active[orderId] ?: return
    val feeUsd = fill.qty * fill.price * costs.feeBps / 10_000.0
    if (order.side == Side.BUY) {
      state.cash -= fill.price * fill.qty + feeUsd
    } else {
      state.cash += fill.price * fill.qty - feeUsd
    }
    state.applyFill(order.symbol, order.side, fill.qty, fill.price, fill.ts, feeUsd)
  }

  private data class ReturnSample(val ret: Double, val durationMs: Long, val exposure: Double)

  private class SimulationState(initialEquity: Double) {
    var cash: Double = initialEquity
    val positions: MutableMap<String, PositionState> = linkedMapOf()
    val trades: MutableList<TradeRecord> = mutableListOf()
    val equitySeries: MutableList<EquityPoint> = mutableListOf()
    val latestPrices: MutableMap<String, Double> = mutableMapOf()

    private var currentEquity: Double = initialEquity

    fun snapshotEquity(ts: Long) {
      val (equity, gross) = computeEquity()
      currentEquity = equity
      equitySeries += EquityPoint(ts = ts, equity = equity, cash = cash, grossExposureUsd = gross)
    }

    fun accountSnapshot(): AccountSnapshot {
      val (equity, _) = computeEquity()
      currentEquity = equity
      return AccountSnapshot(equityUsd = equity, balances = mapOf("USD" to cash))
    }

    fun toPositions(): List<Position> {
      return positions.map { (symbol, pos) ->
        val signedQty = if (pos.side == Side.SELL) -pos.qty else pos.qty
        val price = latestPrices[symbol] ?: pos.avgPrice
        val entryValue = signedQty * pos.avgPrice
        val currentValue = signedQty * price
        val unrealized = currentValue - entryValue
        Position(
          accountId = ACCOUNT_ID,
          symbol = symbol,
          qty = signedQty,
          avgPrice = pos.avgPrice,
          realizedPnl = pos.realizedPnl,
          unrealizedPnl = unrealized,
        )
      }
    }

    fun applyFill(
      symbol: String,
      side: Side,
      qty: Double,
      price: Double,
      ts: Long,
      feeUsd: Double,
    ) {
      if (qty <= EPS) return
      val state = positions.getOrPut(symbol) { PositionState() }
      processFill(state, symbol, side, qty, price, ts, feeUsd)
    }

    private fun processFill(
      state: PositionState,
      symbol: String,
      side: Side,
      qty: Double,
      price: Double,
      ts: Long,
      feeUsd: Double,
    ) {
      if (state.qty == 0.0 || state.side == side) {
        openOrIncrease(state, symbol, side, qty, price, ts, feeUsd)
        return
      }

      val closingQty = min(state.qty, qty)
      val feeForClose = feeUsd * (closingQty / qty)
      closePosition(state, symbol, closingQty, price, ts, feeForClose)
      val remainingQty = qty - closingQty
      val remainingFee = feeUsd - feeForClose
      if (remainingQty > EPS) {
        processFill(state, symbol, side, remainingQty, price, ts, remainingFee)
      }
    }

    private fun openOrIncrease(
      state: PositionState,
      symbol: String,
      side: Side,
      qty: Double,
      price: Double,
      ts: Long,
      feeUsd: Double,
    ) {
      if (state.qty == 0.0) {
        state.side = side
        state.qty = qty
        state.avgPrice = price
        state.feesUsd = feeUsd
        state.trade = TradeBuilder(symbol, side, ts, price, qty, feeUsd)
        state.realizedPnl = 0.0
      } else {
        val totalQty = state.qty + qty
        state.avgPrice = ((state.avgPrice * state.qty) + (price * qty)) / totalQty
        state.qty = totalQty
        state.feesUsd += feeUsd
        state.trade?.apply {
          avgEntryPrice = state.avgPrice
          openQty = state.qty
          feesUsd += feeUsd
        }
      }
    }

    private fun closePosition(
      state: PositionState,
      symbol: String,
      qty: Double,
      price: Double,
      ts: Long,
      feeUsd: Double,
    ) {
      val direction = state.side ?: return
      if (qty <= EPS) return
      val proportion = qty / state.qty
      val entryFeesPortion = state.feesUsd * proportion
      state.feesUsd -= entryFeesPortion
      val grossPnl = when (direction) {
        Side.BUY -> (price - state.avgPrice) * qty
        Side.SELL -> (state.avgPrice - price) * qty
      }
      val netPnl = grossPnl - entryFeesPortion - feeUsd
      state.realizedPnl += netPnl
      state.qty -= qty

      state.trade?.apply {
        realizedPnl += netPnl
        totalClosedQty += qty
        feesUsd += entryFeesPortion + feeUsd
        openQty = state.qty
        lastExitPrice = price
        exitTs = ts
      }

      if (state.qty <= EPS) {
        val builder = state.trade
        if (builder != null) {
          val totalQty = builder.totalClosedQty
          if (totalQty > EPS) {
            val entryPrice = builder.avgEntryPrice
            val pnl = builder.realizedPnl
            val fees = builder.feesUsd
            val retPct = if (entryPrice * totalQty != 0.0) pnl / (entryPrice * totalQty) else 0.0
            trades += TradeRecord(
              symbol = symbol,
              side = builder.side,
              entryTs = builder.entryTs,
              exitTs = builder.exitTs ?: ts,
              qty = totalQty,
              entryPrice = entryPrice,
              exitPrice = builder.lastExitPrice ?: price,
              feesUsd = fees,
              pnlUsd = pnl,
              returnPct = retPct,
            )
          }
        }
        state.reset()
      }
    }

    private fun computeEquity(): Pair<Double, Double> {
      var equity = cash
      var gross = 0.0
      positions.forEach { (symbol, pos) ->
        val price = latestPrices[symbol] ?: pos.avgPrice
        val signedQty = if (pos.side == Side.SELL) -pos.qty else pos.qty
        val value = signedQty * price
        equity += value
        gross += abs(value)
      }
      return equity to gross
    }
  }

  private data class PositionState(
    var side: Side? = null,
    var qty: Double = 0.0,
    var avgPrice: Double = 0.0,
    var feesUsd: Double = 0.0,
    var realizedPnl: Double = 0.0,
    var trade: TradeBuilder? = null,
  ) {
    fun reset() {
      side = null
      qty = 0.0
      avgPrice = 0.0
      feesUsd = 0.0
      realizedPnl = 0.0
      trade = null
    }
  }

  private data class TradeBuilder(
    val symbol: String,
    val side: Side,
    var entryTs: Long,
    var avgEntryPrice: Double,
    var openQty: Double,
    var feesUsd: Double,
    var realizedPnl: Double = 0.0,
    var totalClosedQty: Double = 0.0,
    var lastExitPrice: Double? = null,
    var exitTs: Long? = null,
  )

  private class MutableClock(initial: Long) : Clock() {
    private var current = initial
    override fun withZone(zone: ZoneId?): Clock = this
    override fun getZone(): ZoneId = ZoneId.of("UTC")
    override fun instant(): Instant = Instant.ofEpochMilli(current)
    fun set(ts: Long) { current = ts }
    override fun millis(): Long = current
  }

  private class InMemoryLedger : Ledger {
    val events = mutableListOf<LedgerEvent>()
    override suspend fun append(event: LedgerEvent) {
      events += event
    }
  }

  private class SimulatedBroker(
    private val latency: SimulationLatencyConfig,
    private val costs: SimulationCosts,
    private val clock: () -> Long,
    private val priceLookup: (String, Long) -> Double?,
    private val accountProvider: () -> AccountSnapshot,
  ) : Broker {
    private data class ScheduledEvent(val ts: Long, val sequence: Long, val action: () -> Unit) : Comparable<ScheduledEvent> {
      override fun compareTo(other: ScheduledEvent): Int {
        val cmp = ts.compareTo(other.ts)
        return if (cmp != 0) cmp else sequence.compareTo(other.sequence)
      }
    }

    private val flow = MutableSharedFlow<BrokerEvent>(extraBufferCapacity = 128, replay = 0)
    private val queue = PriorityQueue<ScheduledEvent>()
    private val openOrders = mutableMapOf<String, Order>()
    private val pendingEvents = ArrayDeque<BrokerEvent>()
    private var sequence = 0L

    suspend fun advanceTo(targetTs: Long, consumer: suspend (BrokerEvent) -> Unit) {
      while (queue.isNotEmpty() && queue.peek().ts <= targetTs) {
        val ev = queue.poll()
        ev.action()
      }
      while (pendingEvents.isNotEmpty()) {
        consumer(pendingEvents.removeFirst())
      }
    }

    override suspend fun place(order: Order): String {
      val id = if (order.clientOrderId.isNotBlank()) order.clientOrderId else "sim-${clock()}-${openOrders.size}"
      openOrders[id] = order
      schedule(clock() + latency.ackLatencyMs) {
        emitEvent(BrokerEvent.Accepted(id, order))
      }
      val pieces = max(latency.partialPieces, 1)
      val baseQty = order.qty / pieces
      var remaining = order.qty
      val firstFillAt = clock() + latency.firstFillLatencyMs
      repeat(pieces) { idx ->
        val fillTs = firstFillAt + idx * latency.perFillIntervalMs
        val qty = if (idx == pieces - 1) remaining else baseQty
        remaining -= qty
        schedule(fillTs) {
          val price = priceLookup(order.symbol, fillTs) ?: order.price ?: 0.0
          val slip = costs.slippageBps / 10_000.0
          val fillPrice = when (order.side) {
            Side.BUY -> price * (1.0 + slip)
            Side.SELL -> price * (1.0 - slip)
          }
          val fill = com.kevin.cryptotrader.contracts.Fill(orderId = id, qty = qty, price = fillPrice, ts = fillTs)
          val event = if (idx == pieces - 1) BrokerEvent.Filled(id, fill) else BrokerEvent.PartialFill(id, fill)
          emitEvent(event)
          if (idx == pieces - 1) {
            openOrders.remove(id)
          }
        }
      }
      return id
    }

    override suspend fun cancel(orderId: String): Boolean {
      val removed = openOrders.remove(orderId) != null
      if (removed) {
        emitEvent(BrokerEvent.Canceled(orderId))
      }
      return removed
    }

    override fun streamEvents(symbols: Set<String>): Flow<BrokerEvent> = flow.asSharedFlow()

    override suspend fun account(): AccountSnapshot = accountProvider()

    private fun schedule(ts: Long, action: () -> Unit) {
      queue += ScheduledEvent(ts, sequence++, action)
    }

    private fun emitEvent(event: BrokerEvent) {
      pendingEvents.addLast(event)
      flow.tryEmit(event)
    }
  }
}

private fun Map<String, String>.firstDouble(vararg keys: String): Double? {
  for (key in keys) {
    val value = this[key] ?: continue
    val parsed = value.toDoubleOrNull()
    if (parsed != null) return parsed
  }
  return null
}

private fun Candle.toInputBar(): InputBar = InputBar(ts, open, high, low, close, volume)
