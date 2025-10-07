package com.example.myandroidapp.shared

import com.kevin.cryptotrader.contracts.TransferPlan
import com.kevin.cryptotrader.persistence.ledger.LedgerEvent

data class EquityPoint(val timestamp: Long, val equityUsd: Double)

data class StrategySummary(
    val id: String,
    val name: String,
    val description: String,
    val tags: List<String>,
    val capitalAllocatedUsd: Double,
    val cagr: Double,
    val maxDrawdown: Double,
    val conflicts: List<AlertBannerState> = emptyList(),
)

data class AutomationSummary(
    val id: String,
    val name: String,
    val schedule: String,
    val status: String,
    val lastRunTs: Long?,
    val nextRunTs: Long?,
    val linkedStrategyId: String?,
    val conflicts: List<AlertBannerState> = emptyList(),
)

data class BacktestSummary(
    val id: String,
    val name: String,
    val lookbackDays: Int,
    val cagr: Double,
    val sharpe: Double,
    val maxDrawdown: Double,
    val trades: Int,
    val equityCurve: List<EquityPoint>,
    val startedAt: Long,
    val completedAt: Long,
)

data class SimParamsState(
    val startingBalanceUsd: Double,
    val slippageBps: Int,
    val includeFees: Boolean,
    val leverage: Double,
    val warmupBars: Int,
    val venueId: String,
    val error: String? = null,
)

data class PlannerState(
    val asset: String,
    val amount: Double,
    val plan: TransferPlan? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
)

data class TimelineEntry(
    val ts: Long,
    val headline: String,
    val detail: String,
    val type: LedgerEvent.Type,
)

data class BlotterRow(
    val ts: Long,
    val accountId: String,
    val symbol: String,
    val side: String,
    val qty: Double,
    val price: Double?,
    val status: String,
    val type: LedgerEvent.Type,
)

enum class AlertSeverity { INFO, WARNING, CRITICAL }

data class AlertBannerState(
    val id: String,
    val title: String,
    val message: String,
    val severity: AlertSeverity,
    val relatedRoute: String? = null,
)

