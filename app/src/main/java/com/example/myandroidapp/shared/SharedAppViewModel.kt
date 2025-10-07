package com.example.myandroidapp.shared

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myandroidapp.shared.SampleData.buildBlotter
import com.example.myandroidapp.shared.SampleData.buildTimeline
import com.kevin.cryptotrader.contracts.AccountId
import com.kevin.cryptotrader.contracts.PortfolioService
import com.kevin.cryptotrader.data.portfolio.AccountsRepository
import com.kevin.cryptotrader.data.portfolio.BalancesRepository
import com.kevin.cryptotrader.data.portfolio.PortfolioRepository
import com.kevin.cryptotrader.data.portfolio.PortfolioServiceMock
import com.kevin.cryptotrader.data.portfolio.PositionsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PlannerUpdate(val asset: String? = null, val amount: Double? = null, val target: AccountId? = null)

class SharedAppViewModel(
    private val portfolioService: PortfolioService = PortfolioServiceMock(),
) : ViewModel() {
    private val accountsRepository = AccountsRepository()
    private val balancesRepository = BalancesRepository()
    private val positionsRepository = PositionsRepository()
    private val portfolioRepository = PortfolioRepository(accountsRepository, balancesRepository, positionsRepository)

    private val _equityCurve = MutableStateFlow(SampleData.equityCurve)
    val equityCurve: StateFlow<List<EquityPoint>> = _equityCurve.asStateFlow()

    private val _strategies = MutableStateFlow(SampleData.strategies)
    val strategies: StateFlow<List<StrategySummary>> = _strategies.asStateFlow()

    private val _automations = MutableStateFlow(SampleData.automations)
    val automations: StateFlow<List<AutomationSummary>> = _automations.asStateFlow()

    private val _backtests = MutableStateFlow(SampleData.backtests)
    val backtests: StateFlow<List<BacktestSummary>> = _backtests.asStateFlow()

    private val _alerts = MutableStateFlow(SampleData.alerts)
    val alerts: StateFlow<List<AlertBannerState>> = _alerts.asStateFlow()

    private val _simParams = MutableStateFlow(SampleData.simParams)
    val simParams: StateFlow<SimParamsState> = _simParams.asStateFlow()

    private val _planner = MutableStateFlow(SampleData.plannerSeed)
    val planner: StateFlow<PlannerState> = _planner.asStateFlow()

    private val _timeline = MutableStateFlow(buildTimeline())
    val timeline: StateFlow<List<TimelineEntry>> = _timeline.asStateFlow()

    private val _blotter = MutableStateFlow(buildBlotter())
    val blotter: StateFlow<List<BlotterRow>> = _blotter.asStateFlow()

    private val _darkTheme = MutableStateFlow(true)
    val darkTheme: StateFlow<Boolean> = _darkTheme.asStateFlow()

    val aggregatedHoldings = portfolioRepository.aggregatedHoldings
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val aggregatedPositions = portfolioRepository.aggregatedPositions
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val portfolioValue: StateFlow<Double> = balancesRepository.stream()
        .map { map -> map.values.flatten().sumOf { it.valuationUsd } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = SampleData.holdingsByAccount.values.flatten().sumOf { it.valuationUsd },
        )

    init {
        SampleData.holdingsByAccount.forEach { (accountId, holdings) ->
            balancesRepository.update(accountId, holdings)
        }
        SampleData.positionsByAccount.forEach { (accountId, positions) ->
            positionsRepository.update(accountId, positions)
        }

        viewModelScope.launch { refreshPlannerPlan() }
    }

    fun updateSimParams(transform: (SimParamsState) -> SimParamsState) {
        _simParams.update { current ->
            transform(current).copy(error = null)
        }
    }

    fun setSimParamsError(message: String) {
        _simParams.update { it.copy(error = message) }
    }

    fun updatePlanner(update: PlannerUpdate) {
        _planner.update { current ->
            current.copy(
                asset = update.asset ?: current.asset,
                amount = update.amount ?: current.amount,
            )
        }
    }

    fun refreshPlannerPlan(target: AccountId? = null) {
        viewModelScope.launch {
            val current = _planner.value
            _planner.value = current.copy(isLoading = true, error = null)
            try {
                val plan = portfolioService.acquire(current.asset, current.amount, target)
                _planner.value = current.copy(plan = plan, isLoading = false)
            } catch (t: Throwable) {
                _planner.value = current.copy(isLoading = false, error = t.message ?: "Unable to generate plan")
            }
        }
    }

    fun addAlert(alert: AlertBannerState) {
        _alerts.update { current -> (current + alert).distinctBy { it.id } }
    }

    fun dismissAlert(alertId: String) {
        _alerts.update { current -> current.filterNot { it.id == alertId } }
    }

    fun setStrategies(strategies: List<StrategySummary>) {
        _strategies.value = strategies
    }

    fun setDarkTheme(enabled: Boolean) {
        _darkTheme.value = enabled
    }
}

