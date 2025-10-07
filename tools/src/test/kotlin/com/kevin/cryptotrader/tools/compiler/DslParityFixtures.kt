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

data class Expected(val ir: AutomationIr, val program: ProgramJson)

object DslParityFixtures {
  fun emaCross(): Expected {
    val ir = AutomationIr(
      id = "emaCross",
      version = 1,
      events = listOf(CandleEventIr(id = "e1", interval = Interval.H1)),
      data = listOf(
        IndicatorIr(id = "i1", indicator = "ema", params = mapOf("len" to 50.0)),
        IndicatorIr(id = "i2", indicator = "ema", params = mapOf("len" to 200.0)),
      ),
      logic = listOf(
        OncePerBarLogicIr("g1"),
        CrossLogicIr("c1", CrossDirection.ABOVE),
      ),
      risk = listOf(RiskSizeIr("r1", riskPct = 1.0, notionalUsd = null)),
      actions = listOf(EmitOrderIr("a1", symbol = "BTCUSDT", side = "BUY", orderType = "MARKET")),
      universes = emptyList(),
      edges = listOf(
        EdgeIr("e1", "i1"),
        EdgeIr("e1", "i2"),
        EdgeIr("i1", "g1"),
        EdgeIr("i2", "g1"),
        EdgeIr("g1", "c1"),
        EdgeIr("c1", "r1"),
        EdgeIr("r1", "a1"),
      ),
    )
    val ruleMeta = mapOf("event.type" to "onCandle", "event.interval" to "H1")
    val emitMeta = ruleMeta + mapOf("risk.pct" to "1.0")
    val program = ProgramJson(
      id = "emaCross",
      version = 1,
      interval = Interval.H1,
      defaultSymbol = "BTCUSDT",
      inputs = listOf(InputSourceJson(symbol = "BTCUSDT", csvPath = "fixtures/ohlcv/BTCUSDT_1h_sample.csv")),
      inputsCsvPath = "fixtures/ohlcv/BTCUSDT_1h_sample.csv",
      series = listOf(
        SeriesDefJson(name = "i1", type = SeriesType.EMA, period = 50, source = SourceKey.CLOSE),
        SeriesDefJson(name = "i2", type = SeriesType.EMA, period = 200, source = SourceKey.CLOSE),
      ),
      rules = listOf(
        RuleJson(
          id = "a1",
          event = EventJson.Candle(),
          oncePerBar = true,
          guard = GuardJson.Crosses(Operand.Series("i1"), CrossDir.ABOVE, Operand.Series("i2")),
          actions = listOf(
            ActionJson.EmitOrder(
              symbol = "BTCUSDT",
              side = Side.BUY,
              kind = "signal",
              metaStrings = emitMeta,
            )
          ),
          quota = QuotaJson(1, 3_600_000),
          delayMs = null,
          meta = ruleMeta,
        )
      ),
    )
    return Expected(ir, program)
  }

  fun alertWorkflow(): Expected {
    val ir = AutomationIr(
      id = "alertWorkflow",
      version = 1,
      events = listOf(
        ScheduleEventIr("e1", cron = "0 * * * *", timezone = "UTC"),
        PnLEventIr("onPnL1", thresholdPct = 10.0),
        TickEventIr("onTick1"),
        FillEventIr("onFill1", symbol = "BTCUSDT"),
      ),
      data = listOf(
        IndicatorIr("i1", indicator = "rsi", params = mapOf("len" to 14.0)),
        StateGetIr("stateGet1", key = "lastRun"),
        StateSetIr("stateSet1", key = "lastRun", value = "now"),
        WindowIr("window1", length = 20),
        PairFeedIr("pair1", symbol = "ETHBTC", interval = Interval.H1),
      ),
      logic = listOf(
        ForEachLogicIr("f1", source = "u1"),
        RankLogicIr("r1", metric = "momentum", limit = 2),
        CompareLogicIr("c1", left = "i1", op = CompareOp.LT, right = "30"),
        CooldownLogicIr("cd1", cooldownMs = 300000),
        MathLogicIr("math1", expression = "(a-b)/b"),
        IfLogicIr("if1", label = "oversold"),
        ElseLogicIr("else1"),
      ),
      risk = listOf(
        RiskSizeIr("risk1", riskPct = 2.5, notionalUsd = null),
        AtrStopIr("atr1", multiplier = 2.0),
        TrailStopIr("trail1", trailingPct = 4.0),
      ),
      actions = listOf(
        EmitSpreadIr("emit1", longSymbol = "BTCUSDT", shortSymbol = "ETHUSDT"),
        LogActionIr("log1", message = "Entering spread"),
        NotifyActionIr("notify1", channel = "push", message = "Spread entered"),
        AbortActionIr("abort1", reason = "Risk breach"),
      ),
      universes = listOf(StaticUniverseIr("u1", symbols = listOf("BTCUSDT", "ETHUSDT", "SOLUSDT"))),
      edges = listOf(
        EdgeIr("e1", "u1"),
        EdgeIr("u1", "f1"),
        EdgeIr("f1", "r1"),
        EdgeIr("r1", "i1"),
        EdgeIr("i1", "c1"),
        EdgeIr("c1", "cd1"),
        EdgeIr("cd1", "risk1"),
        EdgeIr("risk1", "atr1"),
        EdgeIr("atr1", "trail1"),
        EdgeIr("trail1", "emit1"),
        EdgeIr("emit1", "log1"),
        EdgeIr("log1", "notify1"),
        EdgeIr("notify1", "abort1"),
        EdgeIr("e1", "stateGet1"),
        EdgeIr("stateGet1", "math1"),
        EdgeIr("math1", "stateSet1"),
        EdgeIr("stateSet1", "log1"),
        EdgeIr("onTick1", "window1"),
        EdgeIr("window1", "pair1"),
        EdgeIr("pair1", "if1"),
        EdgeIr("if1", "else1"),
        EdgeIr("else1", "abort1"),
        EdgeIr("onPnL1", "trail1"),
        EdgeIr("onFill1", "notify1"),
      ),
    )

    val baseMeta = mapOf(
      "logic.forEach" to "u1",
      "logic.rank.metric" to "momentum",
      "logic.rank.limit" to "2",
      "universe.symbols" to "BTCUSDT,ETHUSDT,SOLUSDT",
      "event.type" to "onSchedule",
      "event.cron" to "0 * * * *",
      "event.timezone" to "UTC",
    )

    val emitMeta = baseMeta + mapOf(
      "risk.pct" to "2.5",
      "risk.atr.mult" to "2.0",
      "risk.trailingPct" to "4.0",
      "spread.long" to "BTCUSDT",
      "spread.short" to "ETHUSDT",
    )

    val logMeta = baseMeta + mapOf("logic.math" to "(a-b)/b")
    val notifyMeta = logMeta
    val abortMeta = logMeta + mapOf("logic.if" to "oversold", "logic.branch" to "else")

    val program = ProgramJson(
      id = "alertWorkflow",
      version = 1,
      interval = Interval.H1,
      defaultSymbol = "BTCUSDT",
      inputs = listOf(InputSourceJson(symbol = "BTCUSDT", csvPath = "fixtures/ohlcv/BTCUSDT_1h_sample.csv")),
      inputsCsvPath = "fixtures/ohlcv/BTCUSDT_1h_sample.csv",
      series = listOf(
        SeriesDefJson(name = "i1", type = SeriesType.RSI, period = 14, source = SourceKey.CLOSE),
      ),
      rules = listOf(
        RuleJson(
          id = "emit1",
          event = EventJson.Schedule(),
          oncePerBar = false,
          guard = GuardJson.Threshold(Operand.Series("i1"), Op.LT, Operand.Const(30.0)),
          actions = listOf(
            ActionJson.EmitSpread(
              symbol = "BTCUSDT",
              side = Side.BUY,
              offsetPct = 0.0,
              widthPct = 0.0,
              metaStrings = emitMeta,
            )
          ),
          quota = null,
          delayMs = 300000,
          meta = baseMeta,
        ),
        RuleJson(
          id = "log1",
          event = EventJson.Schedule(),
          oncePerBar = false,
          guard = GuardJson.Threshold(Operand.Series("i1"), Op.LT, Operand.Const(30.0)),
          actions = listOf(
            ActionJson.Log(message = "Entering spread"),
          ),
          quota = null,
          delayMs = 300000,
          meta = logMeta,
        ),
        RuleJson(
          id = "notify1",
          event = EventJson.Schedule(),
          oncePerBar = false,
          guard = GuardJson.Threshold(Operand.Series("i1"), Op.LT, Operand.Const(30.0)),
          actions = listOf(
            ActionJson.Notify(channel = "push", message = "Spread entered"),
          ),
          quota = null,
          delayMs = 300000,
          meta = notifyMeta,
        ),
        RuleJson(
          id = "abort1",
          event = EventJson.Schedule(),
          oncePerBar = false,
          guard = GuardJson.Threshold(Operand.Series("i1"), Op.LT, Operand.Const(30.0)),
          actions = listOf(ActionJson.Abort(reason = "Risk breach")),
          quota = null,
          delayMs = 300000,
          meta = abortMeta,
        ),
      ),
    )
    return Expected(ir, program)
  }
}
