package com.kevin.cryptotrader.runtime.vm

import com.kevin.cryptotrader.contracts.Interval
import com.kevin.cryptotrader.contracts.Side
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ProgramJson(
  val id: String,
  val version: Int,
  val interval: Interval = Interval.H1,
  val defaultSymbol: String? = null,
  val inputs: List<InputSourceJson> = emptyList(),
  val inputsCsvPath: String? = null,
  val inputsInline: List<InputBarJson>? = null,
  val series: List<SeriesDefJson> = emptyList(),
  val rules: List<RuleJson> = emptyList(),
  val fillEvents: List<FillEventJson> = emptyList(),
  val pnlEvents: List<PnLEventJson> = emptyList(),
)

@Serializable
data class InputSourceJson(
  val symbol: String,
  val csvPath: String? = null,
  val inline: List<InputBarJson>? = null,
)

@Serializable
data class InputBarJson(val ts: Long, val open: Double, val high: Double, val low: Double, val close: Double, val volume: Double)

@Serializable
data class SeriesDefJson(
  val name: String,
  val type: SeriesType,
  val period: Int? = null,
  val source: SourceKey = SourceKey.CLOSE,
  val symbol: String? = null,
  val params: Map<String, Double> = emptyMap(),
)

@Serializable
enum class SeriesType {
  EMA,
  SMA,
  WMA,
  RSI,
  MACD,
  MACD_SIGNAL,
  MACD_HIST,
  BOLLINGER_MIDDLE,
  BOLLINGER_UPPER,
  BOLLINGER_LOWER,
  BOLLINGER_STDDEV,
  DONCHIAN_UPPER,
  DONCHIAN_LOWER,
  DONCHIAN_MIDDLE,
  ATR,
  ZSCORE,
  ROC,
}

@Serializable
enum class SourceKey { OPEN, HIGH, LOW, CLOSE, VOLUME }

@Serializable
data class RuleJson(
  val id: String,
  val event: EventJson = EventJson.Candle(),
  val oncePerBar: Boolean = true,
  val guard: GuardJson? = null,
  val actions: List<ActionJson> = emptyList(),
  val quota: QuotaJson? = null,
  val delayMs: Long? = null,
)

@Serializable
sealed class EventJson {
  @Serializable
  @SerialName("candle")
  data class Candle(val symbol: String? = null) : EventJson()

  @Serializable
  @SerialName("schedule")
  data class Schedule(
    val everyMs: Long? = null,
    val at: List<Long> = emptyList(),
    val offsetMs: Long = 0L,
  ) : EventJson()

  @Serializable
  @SerialName("fill")
  data class Fill(val symbol: String? = null, val side: Side? = null) : EventJson()

  @Serializable
  @SerialName("pnl")
  data class PnL(
    val symbol: String? = null,
    val realizedThreshold: Double? = null,
    val unrealizedThreshold: Double? = null,
  ) : EventJson()
}

@Serializable
sealed class GuardJson {
  @Serializable
  @SerialName("threshold")
  data class Threshold(val left: Operand, val op: Op, val right: Operand) : GuardJson()

  @Serializable
  @SerialName("crosses")
  data class Crosses(val left: Operand, val dir: CrossDir, val right: Operand) : GuardJson()

  @Serializable
  @SerialName("always")
  object Always : GuardJson()

  @Serializable
  @SerialName("and")
  data class And(val guards: List<GuardJson>) : GuardJson()

  @Serializable
  @SerialName("or")
  data class Or(val guards: List<GuardJson>) : GuardJson()
}

@Serializable
enum class Op { GT, GTE, LT, LTE, EQ }

@Serializable
enum class CrossDir { ABOVE, BELOW }

@Serializable
sealed class Operand {
  @Serializable
  @SerialName("series")
  data class Series(val name: String) : Operand()

  @Serializable
  @SerialName("const")
  data class Const(val value: Double) : Operand()

  @Serializable
  @SerialName("state")
  data class State(val key: String, val default: Double? = null) : Operand()

  @Serializable
  @SerialName("math")
  data class Math(val op: MathOp, val left: Operand, val right: Operand) : Operand()

  @Serializable
  @SerialName("abs")
  data class Abs(val operand: Operand) : Operand()
}

@Serializable
sealed class ActionJson {
  @Serializable
  @SerialName("emitOrder")
  data class EmitOrder(
    val symbol: String,
    val side: Side,
    val kind: String = "signal",
    val qty: Operand? = null,
    val notionalUsd: Operand? = null,
    val price: Operand? = null,
    val meta: Map<String, Operand> = emptyMap(),
    val metaStrings: Map<String, String> = emptyMap(),
  ) : ActionJson()

  @Serializable
  @SerialName("emitSpread")
  data class EmitSpread(
    val symbol: String,
    val side: Side,
    val offsetPct: Double,
    val widthPct: Double,
    val qty: Operand? = null,
    val notionalUsd: Operand? = null,
    val kind: String = "spread",
    val metaStrings: Map<String, String> = emptyMap(),
  ) : ActionJson()

  @Serializable
  @SerialName("setState")
  data class SetState(val key: String, val value: Operand) : ActionJson()

  @Serializable
  @SerialName("incState")
  data class IncState(val key: String, val delta: Operand) : ActionJson()

  @Serializable
  @SerialName("clearState")
  data class ClearState(val key: String) : ActionJson()

  @Serializable
  @SerialName("setStopAtr")
  data class SetStopAtr(
    val symbol: String,
    val side: Side,
    val atrSeries: String,
    val multiplier: Double,
    val kind: String = "stop",
  ) : ActionJson()

  @Serializable
  @SerialName("trailStop")
  data class TrailStop(
    val symbol: String,
    val side: Side,
    val trailPct: Double,
    val kind: String = "stop",
  ) : ActionJson()

  @Serializable
  @SerialName("log")
  data class Log(
    val message: String,
    val level: LogLevel = LogLevel.INFO,
    val fields: Map<String, Operand> = emptyMap(),
  ) : ActionJson()

  @Serializable
  @SerialName("notify")
  data class Notify(
    val channel: String,
    val message: String,
    val severity: String = "info",
    val fields: Map<String, Operand> = emptyMap(),
  ) : ActionJson()

  @Serializable
  @SerialName("abort")
  data class Abort(val reason: String = "") : ActionJson()
}

@Serializable
enum class LogLevel { DEBUG, INFO, WARN, ERROR }

@Serializable
enum class MathOp { ADD, SUB, MUL, DIV, MIN, MAX }

@Serializable
data class QuotaJson(val max: Int, val windowMs: Long)

@Serializable
data class FillEventJson(
  val ts: Long,
  val symbol: String,
  val side: Side,
  val price: Double,
  val qty: Double,
)

@Serializable
data class PnLEventJson(
  val ts: Long,
  val symbol: String,
  val realized: Double = 0.0,
  val unrealized: Double = 0.0,
)

