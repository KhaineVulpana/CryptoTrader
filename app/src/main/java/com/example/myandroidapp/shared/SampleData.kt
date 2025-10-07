package com.example.myandroidapp.shared

import com.kevin.cryptotrader.contracts.AccountId
import com.kevin.cryptotrader.contracts.Holding
import com.kevin.cryptotrader.contracts.Position
import com.kevin.cryptotrader.contracts.TransferPlan
import com.kevin.cryptotrader.contracts.TransferStep
import com.kevin.cryptotrader.contracts.SafetyStatus
import com.kevin.cryptotrader.contracts.PlanSafetyCheck
import com.kevin.cryptotrader.contracts.CostBreakdown
import com.kevin.cryptotrader.persistence.ledger.LedgerEvent

object SampleData {
    private const val baseTs = 1_710_000_000_000L

    val equityCurve: List<EquityPoint> = List(30) { index ->
        val drift = 50 * index
        val noise = (index % 4) * 25
        EquityPoint(
            timestamp = baseTs + index * 3_600_000L,
            equityUsd = 250_000 + drift + noise,
        )
    }

    val holdingsByAccount: Map<AccountId, List<Holding>> = mapOf(
        "acc_binance" to listOf(
            Holding("acc_binance", "BTC", "BTC", free = 0.8, locked = 0.1, valuationUsd = 48_000.0),
            Holding("acc_binance", "USDT", "TRX", free = 15_000.0, locked = 0.0, valuationUsd = 15_000.0),
        ),
        "acc_coinbase" to listOf(
            Holding("acc_coinbase", "ETH", "ETH", free = 12.0, locked = 0.0, valuationUsd = 36_000.0),
            Holding("acc_coinbase", "USDC", "ETH", free = 8_000.0, locked = 0.0, valuationUsd = 8_000.0),
        ),
        "acc_wallet_evm" to listOf(
            Holding("acc_wallet_evm", "ARB", "ARB", free = 2_500.0, locked = 0.0, valuationUsd = 5_000.0),
            Holding("acc_wallet_evm", "ETH", "ARB", free = 4.0, locked = 0.0, valuationUsd = 12_000.0),
        ),
    )

    val positionsByAccount: Map<AccountId, List<Position>> = mapOf(
        "acc_binance" to listOf(
            Position("acc_binance", "BTCUSDT", qty = 0.5, avgPrice = 32_500.0, realizedPnl = 1_250.0, unrealizedPnl = 2_850.0),
            Position("acc_binance", "ETHUSDT", qty = 4.0, avgPrice = 2_000.0, realizedPnl = 640.0, unrealizedPnl = 320.0),
        ),
        "acc_coinbase" to listOf(
            Position("acc_coinbase", "ARBUSD", qty = 1_200.0, avgPrice = 1.1, realizedPnl = -120.0, unrealizedPnl = 220.0),
        ),
    )

    val ledgerEvents: List<LedgerEvent> = listOf(
        LedgerEvent.CandleLogged(
            ts = baseTs - 18_000_000L,
            symbol = "BTCUSDT",
            interval = com.kevin.cryptotrader.contracts.Interval.H1,
            open = 31_900.0,
            high = 32_120.0,
            low = 31_800.0,
            close = 32_050.0,
            volume = 1_523.0,
            source = "binance",
        ),
        LedgerEvent.IntentLogged(
            ts = baseTs - 15_000_000L,
            intentId = "intent-1",
            sourceId = "strategy-alpha",
            accountId = "acc_binance",
            kind = "scalp",
            symbol = "BTCUSDT",
            side = "BUY",
            notionalUsd = 16_000.0,
            qty = 0.5,
            priceHint = 32_000.0,
        ),
        LedgerEvent.OrderPlaced(
            ts = baseTs - 14_400_000L,
            orderId = "order-1",
            accountId = "acc_binance",
            symbol = "BTCUSDT",
            side = "BUY",
            typeName = "LIMIT",
            qty = 0.5,
            price = 31_980.0,
            stopPrice = null,
            tif = "GTC",
            status = "NEW",
        ),
        LedgerEvent.FillRecorded(
            ts = baseTs - 13_800_000L,
            orderId = "order-1",
            accountId = "acc_binance",
            symbol = "BTCUSDT",
            side = "BUY",
            qty = 0.5,
            price = 31_990.0,
        ),
        LedgerEvent.PolicyApplied(
            ts = baseTs - 12_000_000L,
            policyId = "risk-default",
            accountId = "acc_binance",
            version = 4,
            config = mapOf("maxDrawdown" to "12", "allocation" to "0.6"),
        ),
        LedgerEvent.AutomationStateRecorded(
            ts = baseTs - 8_000_000L,
            automationId = "auto-ema",
            status = "running",
            state = mapOf("bars" to "480", "lastCross" to "bullish"),
        ),
        LedgerEvent.OrderPlaced(
            ts = baseTs - 6_000_000L,
            orderId = "order-2",
            accountId = "acc_coinbase",
            symbol = "ETHUSD",
            side = "SELL",
            typeName = "MARKET",
            qty = 2.0,
            price = null,
            stopPrice = null,
            tif = "IOC",
            status = "FILLED",
        ),
        LedgerEvent.FillRecorded(
            ts = baseTs - 5_900_000L,
            orderId = "order-2",
            accountId = "acc_coinbase",
            symbol = "ETHUSD",
            side = "SELL",
            qty = 2.0,
            price = 2_150.0,
        ),
    )

    val strategies: List<StrategySummary> = listOf(
        StrategySummary(
            id = "strategy-alpha",
            name = "Adaptive Trend",
            description = "Blends momentum and mean reversion with adaptive risk sizing.",
            tags = listOf("Trend", "Futures"),
            capitalAllocatedUsd = 85_000.0,
            cagr = 0.26,
            maxDrawdown = 0.11,
            conflicts = listOf(
                AlertBannerState(
                    id = "conflict-1",
                    title = "Allocation cap reached",
                    message = "Binance futures margin is at 95% of the configured ceiling.",
                    severity = AlertSeverity.WARNING,
                    relatedRoute = "settings",
                )
            ),
        ),
        StrategySummary(
            id = "strategy-beta",
            name = "Mean Reversion",
            description = "L1 order-book mean reversion with hedged legs.",
            tags = listOf("Spot", "Market Making"),
            capitalAllocatedUsd = 45_000.0,
            cagr = 0.18,
            maxDrawdown = 0.07,
        ),
    )

    val automations: List<AutomationSummary> = listOf(
        AutomationSummary(
            id = "auto-ema",
            name = "EMA Cross",
            schedule = "Every 15m",
            status = "Healthy",
            lastRunTs = baseTs - 3_600_000L,
            nextRunTs = baseTs + 900_000L,
            linkedStrategyId = "strategy-alpha",
            conflicts = emptyList(),
        ),
        AutomationSummary(
            id = "auto-rotation",
            name = "Sector Rotation",
            schedule = "Daily at 12:00 UTC",
            status = "Degraded",
            lastRunTs = baseTs - 26_000_000L,
            nextRunTs = baseTs + 40_000_000L,
            linkedStrategyId = "strategy-beta",
            conflicts = listOf(
                AlertBannerState(
                    id = "conflict-rotation",
                    title = "Policy override",
                    message = "Voting policy prevented reallocation due to drawdown.",
                    severity = AlertSeverity.CRITICAL,
                    relatedRoute = "alerts",
                )
            ),
        ),
    )

    val backtests: List<BacktestSummary> = listOf(
        BacktestSummary(
            id = "bt-alpha",
            name = "Adaptive Trend - 2y",
            lookbackDays = 730,
            cagr = 0.32,
            sharpe = 1.6,
            maxDrawdown = 0.18,
            trades = 420,
            equityCurve = equityCurve.takeLast(20),
            startedAt = baseTs - 50_000L,
            completedAt = baseTs - 45_000L,
        ),
        BacktestSummary(
            id = "bt-beta",
            name = "Mean Reversion - 1y",
            lookbackDays = 365,
            cagr = 0.22,
            sharpe = 1.2,
            maxDrawdown = 0.12,
            trades = 520,
            equityCurve = equityCurve.takeLast(15),
            startedAt = baseTs - 120_000L,
            completedAt = baseTs - 110_000L,
        ),
    )

    val alerts: List<AlertBannerState> = listOf(
        AlertBannerState(
            id = "alert-risk",
            title = "Risk policy conflict",
            message = "Priority policy blocked orders from Strategy Alpha due to leverage over 5x.",
            severity = AlertSeverity.CRITICAL,
            relatedRoute = "blotter",
        ),
        AlertBannerState(
            id = "alert-funding",
            title = "Funding buffer low",
            message = "Wallet buffer for stables is below 10% threshold. Top up recommended.",
            severity = AlertSeverity.WARNING,
            relatedRoute = "automations",
        ),
    )

    val simParams = SimParamsState(
        startingBalanceUsd = 100_000.0,
        slippageBps = 5,
        includeFees = true,
        leverage = 2.0,
        warmupBars = 240,
        venueId = "binance",
    )

    val plannerSeed = PlannerState(
        asset = "BTC",
        amount = 0.5,
        plan = TransferPlan(
            id = "plan-seed",
            steps = listOf(
                TransferStep.BuyOnExchange("binance", "BTC/USDT", 0.3),
                TransferStep.BuyOnExchange("coinbase", "BTC/USD", 0.2),
                TransferStep.Withdraw("binance", "BTC", "BTC", "bc1-plan", 0.3),
            ),
            totalCostUsd = 16_050.0,
            etaSeconds = 3_600,
            costBreakdown = CostBreakdown(
                notionalUsd = 16_000.0,
                tradingFeesUsd = 25.0,
                withdrawalFeesUsd = 20.0,
                networkFeesUsd = 5.0,
            ),
            safetyChecks = listOf(
                PlanSafetyCheck(
                    id = "buffer_check",
                    description = "Maintains $5k wallet buffer",
                    status = SafetyStatus.WARNING,
                    blocking = false,
                )
            ),
        ),
    )

    fun buildTimeline(): List<TimelineEntry> = ledgerEvents.sortedBy { it.ts }.map { event ->
        when (event) {
            is LedgerEvent.CandleLogged -> TimelineEntry(
                ts = event.ts,
                headline = "${event.symbol} ${event.interval}",
                detail = "Close ${event.close} on ${event.source.uppercase()} volume ${event.volume}",
                type = event.type,
            )
            is LedgerEvent.IntentLogged -> TimelineEntry(
                ts = event.ts,
                headline = "Intent ${event.intentId} ${event.side}",
                detail = "${event.symbol} ${event.qty ?: event.notionalUsd}".trim(),
                type = event.type,
            )
            is LedgerEvent.OrderPlaced -> TimelineEntry(
                ts = event.ts,
                headline = "Order ${event.side} ${event.symbol}",
                detail = "${event.qty} @ ${event.price ?: "MKT"} (${event.status})",
                type = event.type,
            )
            is LedgerEvent.FillRecorded -> TimelineEntry(
                ts = event.ts,
                headline = "Fill ${event.side} ${event.symbol}",
                detail = "${event.qty} @ ${event.price}",
                type = event.type,
            )
            is LedgerEvent.PolicyApplied -> TimelineEntry(
                ts = event.ts,
                headline = "Policy ${event.policyId}",
                detail = "v${event.version} ${event.config.entries.joinToString { "${it.key}:${it.value}" }}",
                type = event.type,
            )
            is LedgerEvent.AutomationStateRecorded -> TimelineEntry(
                ts = event.ts,
                headline = "Automation ${event.automationId}",
                detail = event.state.entries.joinToString { "${it.key}=${it.value}" },
                type = event.type,
            )
        }
    }

    fun buildBlotter(): List<BlotterRow> = ledgerEvents.filter {
        it is LedgerEvent.OrderPlaced || it is LedgerEvent.FillRecorded
    }.sortedBy { it.ts }.map { event ->
        when (event) {
            is LedgerEvent.OrderPlaced -> BlotterRow(
                ts = event.ts,
                accountId = event.accountId,
                symbol = event.symbol,
                side = event.side,
                qty = event.qty,
                price = event.price,
                status = event.status,
                type = event.type,
            )
            is LedgerEvent.FillRecorded -> BlotterRow(
                ts = event.ts,
                accountId = event.accountId,
                symbol = event.symbol,
                side = event.side,
                qty = event.qty,
                price = event.price,
                status = "Filled",
                type = event.type,
            )
            else -> error("Unexpected event type")
        }
    }
}

