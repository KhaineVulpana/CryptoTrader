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
  val inputsCsvPath: String? = null,
  val inputsInline: List<InputBarJson>? = null,
  val series: List<SeriesDefJson> = emptyList(),
  val rules: List<RuleJson> = emptyList(),
)

@Serializable
data class InputBarJson(val ts: Long, val open: Double, val high: Double, val low: Double, val close: Double, val volume: Double)

@Serializable
data class SeriesDefJson(
  val name: String,
  val type: SeriesType,
  val period: Int? = null,
  val source: SourceKey = SourceKey.CLOSE,
)

@Serializable
enum class SeriesType { EMA }

@Serializable
enum class SourceKey { OPEN, HIGH, LOW, CLOSE, VOLUME }

@Serializable
data class RuleJson(
  val id: String,
  val oncePerBar: Boolean = true,
  val guard: GuardJson,
  val action: ActionJson,
  val quota: QuotaJson? = null,
  val delayMs: Long? = null,
)

@Serializable
sealed class GuardJson {
  @Serializable
  @SerialName("threshold")
  data class Threshold(val left: Operand, val op: Op, val right: Operand) : GuardJson()

  @Serializable
  @SerialName("crosses")
  data class Crosses(val left: Operand, val dir: CrossDir, val right: Operand) : GuardJson()
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
}

@Serializable
data class ActionJson(
  val type: ActionType,
  val symbol: String,
  val side: Side,
  val kind: String = "signal",
)

@Serializable
enum class ActionType { EMIT }

@Serializable
data class QuotaJson(val max: Int, val windowMs: Long)

