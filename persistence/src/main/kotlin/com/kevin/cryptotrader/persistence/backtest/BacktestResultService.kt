package com.kevin.cryptotrader.persistence.backtest

import com.kevin.cryptotrader.contracts.EquityPoint
import com.kevin.cryptotrader.contracts.SimulationMetrics
import com.kevin.cryptotrader.contracts.SimulationResult
import com.kevin.cryptotrader.contracts.SimulationSliceResult
import com.kevin.cryptotrader.contracts.TradeRecord
import com.kevin.cryptotrader.contracts.WalkForwardSplit
import com.kevin.cryptotrader.persistence.dao.BacktestRunDao
import com.kevin.cryptotrader.persistence.entity.BacktestRunEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

class BacktestResultService(
  private val dao: BacktestRunDao?,
  private val json: Json = Json { encodeDefaults = true },
) {
  suspend fun persist(result: SimulationResult) {
    dao ?: return
    withContext(Dispatchers.IO) {
      val now = System.currentTimeMillis()
      val entries = mutableListOf<BacktestRunEntity>()
      result.slices.forEach { slice ->
        entries += slice.toEntity(result.runId, now)
      }
      entries += BacktestRunEntity(
        id = "${result.runId}-${BacktestRunEntity.AGGREGATED_ID}",
        simulationId = result.runId,
        splitId = BacktestRunEntity.AGGREGATED_ID,
        label = "Aggregate",
        inSampleStart = 0L,
        inSampleEnd = 0L,
        outSampleStart = 0L,
        outSampleEnd = 0L,
        createdAt = now,
        metricsJson = json.encodeToString(SimulationMetrics.serializer(), result.aggregated),
        equityJson = json.encodeToString(ListSerializer(EquityPoint.serializer()), emptyList()),
        tradesJson = json.encodeToString(ListSerializer(TradeRecord.serializer()), emptyList()),
      )
      dao.deleteForSimulation(result.runId)
      dao.upsertAll(entries)
    }
  }

  suspend fun load(simulationId: String): SimulationResult? {
    dao ?: return null
    return withContext(Dispatchers.IO) {
      val rows = dao.runsForSimulation(simulationId)
      if (rows.isEmpty()) return@withContext null
      val aggregatedRow = rows.firstOrNull { it.splitId == BacktestRunEntity.AGGREGATED_ID }
      val sliceRows = rows.filterNot { it.splitId == BacktestRunEntity.AGGREGATED_ID }
      val slices = sliceRows.map { it.toSlice(json) }
      val aggregated = aggregatedRow?.let {
        json.decodeFromString(SimulationMetrics.serializer(), it.metricsJson)
      } ?: SimulationMetrics(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0)
      SimulationResult(runId = simulationId, slices = slices, aggregated = aggregated)
    }
  }

  private fun SimulationSliceResult.toEntity(simId: String, createdAt: Long): BacktestRunEntity {
    return BacktestRunEntity(
      id = "$simId-${split.id}",
      simulationId = simId,
      splitId = split.id,
      label = split.label,
      inSampleStart = split.inSampleStart,
      inSampleEnd = split.inSampleEnd,
      outSampleStart = split.outSampleStart,
      outSampleEnd = split.outSampleEnd,
      createdAt = createdAt,
      metricsJson = json.encodeToString(SimulationMetrics.serializer(), metrics),
      equityJson = json.encodeToString(ListSerializer(EquityPoint.serializer()), equity),
      tradesJson = json.encodeToString(ListSerializer(TradeRecord.serializer()), trades),
    )
  }

  private fun BacktestRunEntity.toSlice(json: Json): SimulationSliceResult {
    val split = WalkForwardSplit(
      id = splitId,
      label = label,
      inSampleStart = inSampleStart,
      inSampleEnd = inSampleEnd,
      outSampleStart = outSampleStart,
      outSampleEnd = outSampleEnd,
    )
    val metrics = json.decodeFromString(SimulationMetrics.serializer(), metricsJson)
    val equity = json.decodeFromString(ListSerializer(EquityPoint.serializer()), equityJson)
    val trades = json.decodeFromString(ListSerializer(TradeRecord.serializer()), tradesJson)
    return SimulationSliceResult(split, metrics, equity, trades)
  }
}
