package com.kevin.cryptotrader.tools.compiler

import com.kevin.cryptotrader.contracts.Interval

data class AutomationIr(
    val id: String,
    val version: Int,
    val events: List<EventIr>,
    val data: List<DataIr>,
    val logic: List<LogicIr>,
    val risk: List<RiskIr>,
    val actions: List<ActionIr>,
    val universes: List<UniverseIr>,
    val edges: List<EdgeIr>,
)

data class EdgeIr(val from: String, val to: String)

sealed interface EventIr { val id: String }

data class CandleEventIr(override val id: String, val interval: Interval) : EventIr

data class TickEventIr(override val id: String) : EventIr

data class ScheduleEventIr(override val id: String, val cron: String, val timezone: String) : EventIr

data class FillEventIr(override val id: String, val symbol: String?) : EventIr

data class PnLEventIr(override val id: String, val thresholdPct: Double) : EventIr

sealed interface DataIr { val id: String }

data class IndicatorIr(
    override val id: String,
    val indicator: String,
    val params: Map<String, Double>,
) : DataIr

data class WindowIr(override val id: String, val length: Int) : DataIr

data class StateGetIr(override val id: String, val key: String) : DataIr

data class StateSetIr(override val id: String, val key: String, val value: String) : DataIr

data class PairFeedIr(override val id: String, val symbol: String, val interval: Interval) : DataIr

sealed interface LogicIr { val id: String }

data class IfLogicIr(override val id: String, val label: String?) : LogicIr

data class ElseLogicIr(override val id: String) : LogicIr

data class MathLogicIr(override val id: String, val expression: String) : LogicIr

data class CompareLogicIr(override val id: String, val left: String, val op: CompareOp, val right: String) : LogicIr

data class CrossLogicIr(override val id: String, val direction: CrossDirection) : LogicIr

data class CooldownLogicIr(override val id: String, val cooldownMs: Long) : LogicIr

data class OncePerBarLogicIr(override val id: String) : LogicIr

data class ForEachLogicIr(override val id: String, val source: String) : LogicIr

data class RankLogicIr(override val id: String, val metric: String, val limit: Int) : LogicIr

enum class CompareOp { GT, GTE, LT, LTE, EQ }

enum class CrossDirection { ABOVE, BELOW }

sealed interface RiskIr { val id: String }

data class RiskSizeIr(override val id: String, val riskPct: Double?, val notionalUsd: Double?) : RiskIr

data class AtrStopIr(override val id: String, val multiplier: Double) : RiskIr

data class TrailStopIr(override val id: String, val trailingPct: Double) : RiskIr

sealed interface ActionIr { val id: String }

data class EmitOrderIr(
    override val id: String,
    val symbol: String,
    val side: String,
    val orderType: String,
) : ActionIr

data class EmitSpreadIr(override val id: String, val longSymbol: String, val shortSymbol: String) : ActionIr

data class LogActionIr(override val id: String, val message: String) : ActionIr

data class NotifyActionIr(override val id: String, val channel: String, val message: String) : ActionIr

data class AbortActionIr(override val id: String, val reason: String) : ActionIr

sealed interface UniverseIr { val id: String }

data class StaticUniverseIr(override val id: String, val symbols: List<String>) : UniverseIr
