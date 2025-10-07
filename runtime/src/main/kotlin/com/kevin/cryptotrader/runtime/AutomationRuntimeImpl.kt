package com.kevin.cryptotrader.runtime

import com.kevin.cryptotrader.contracts.AutomationDef
import com.kevin.cryptotrader.contracts.AutomationRuntime
import com.kevin.cryptotrader.contracts.Intent
import com.kevin.cryptotrader.contracts.LoadedProgram
import com.kevin.cryptotrader.contracts.RuntimeEnv
import com.kevin.cryptotrader.contracts.LogLevel
import com.kevin.cryptotrader.contracts.TelemetryModule
import com.kevin.cryptotrader.core.telemetry.TelemetryCenter
import com.kevin.cryptotrader.runtime.vm.InputLoader
import com.kevin.cryptotrader.runtime.vm.InputBar
import com.kevin.cryptotrader.runtime.vm.Interpreter
import com.kevin.cryptotrader.runtime.vm.ProgramJson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.serialization.json.Json

class AutomationRuntimeImpl : AutomationRuntime {
  private val json = Json { ignoreUnknownKeys = true }
  @Volatile private var loaded: LoadedProgramImpl? = null

  override fun load(def: AutomationDef): LoadedProgram {
    val program = json.decodeFromString(ProgramJson.serializer(), def.graphJson)
    val lp = LoadedProgramImpl(program)
    loaded = lp
    TelemetryCenter.logEvent(
      module = TelemetryModule.RUNTIME,
      level = LogLevel.INFO,
      message = "Loaded automation definition",
      fields = mapOf("automationId" to def.id, "version" to def.version.toString())
    )
    return lp
  }

  override fun run(env: com.kevin.cryptotrader.contracts.RuntimeEnv): Flow<Intent> {
    val lp = loaded ?: return flow { }
    TelemetryCenter.logEvent(
      module = TelemetryModule.RUNTIME,
      level = LogLevel.DEBUG,
      message = "Starting automation runtime",
      fields = mapOf("automationId" to lp.program.id)
    )
    return lp.run(env)
  }

  private class LoadedProgramImpl(val program: ProgramJson) : LoadedProgram {
    fun run(env: RuntimeEnv): Flow<Intent> {
      val inputs: List<InputBar> = when {
        program.inputsInline != null -> program.inputsInline.map { InputBar(it.ts, it.open, it.high, it.low, it.close, it.volume) }
        program.inputsCsvPath != null -> InputLoader.fromCsv(program.inputsCsvPath)
        else -> emptyList()
      }
      val interp = Interpreter(program)
      TelemetryCenter.logEvent(
        module = TelemetryModule.RUNTIME,
        level = LogLevel.INFO,
        message = "Prepared runtime inputs",
        fields = mapOf(
          "automationId" to program.id,
          "bars" to inputs.size.toString(),
          "interval" to program.interval.name
        )
      )
      recordDataGaps(inputs)
      var emitted = 0
      return interp.run(inputs)
        .onStart {
          TelemetryCenter.logEvent(
            module = TelemetryModule.RUNTIME,
            level = LogLevel.DEBUG,
            message = "Interpreter flow started",
            fields = mapOf("automationId" to program.id)
          )
        }
        .onEach { intent ->
          emitted += 1
          TelemetryCenter.logEvent(
            module = TelemetryModule.RUNTIME,
            level = LogLevel.DEBUG,
            message = "Intent emitted",
            fields = mapOf("automationId" to program.id, "intentId" to intent.id, "symbol" to intent.symbol)
          )
        }
        .onCompletion { cause ->
          val level = if (cause == null) LogLevel.INFO else LogLevel.ERROR
          val msg = if (cause == null) "Interpreter flow completed" else "Interpreter flow failed"
          val fields = mutableMapOf(
            "automationId" to program.id,
            "emitted" to emitted.toString()
          )
          cause?.let { fields["error"] = it.message ?: it::class.java.name }
          TelemetryCenter.logEvent(
            module = TelemetryModule.RUNTIME,
            level = level,
            message = msg,
            fields = fields
          )
        }
        .catch { throwable ->
          TelemetryCenter.logEvent(
            module = TelemetryModule.RUNTIME,
            level = LogLevel.ERROR,
            message = "Interpreter exception",
            fields = mapOf(
              "automationId" to program.id,
              "error" to (throwable.message ?: throwable::class.java.name)
            )
          )
          throw throwable
        }
    }

    private fun recordDataGaps(inputs: List<InputBar>) {
      if (inputs.size <= 1) {
        TelemetryCenter.recordDataGap(
          module = TelemetryModule.RUNTIME,
          streamId = program.id,
          gapSeconds = 0.0,
          fields = mapOf("bars" to inputs.size.toString())
        )
        return
      }
      val expectedGapMs = intervalToMillis(program.interval)
      var maxGapMs = 0L
      var gaps = 0
      inputs.sortedBy { it.ts }.zipWithNext { a, b ->
        val delta = b.ts - a.ts
        val gap = delta - expectedGapMs
        if (gap > 0) {
          gaps += 1
          if (gap > maxGapMs) {
            maxGapMs = gap
          }
        }
      }
      val fields = mutableMapOf(
        "bars" to inputs.size.toString(),
        "gaps" to gaps.toString()
      )
      TelemetryCenter.recordDataGap(
        module = TelemetryModule.RUNTIME,
        streamId = program.id,
        gapSeconds = maxGapMs / 1000.0,
        fields = fields
      )
    }

    private fun intervalToMillis(interval: com.kevin.cryptotrader.contracts.Interval): Long = when (interval) {
      com.kevin.cryptotrader.contracts.Interval.M1 -> 60_000L
      com.kevin.cryptotrader.contracts.Interval.M5 -> 5 * 60_000L
      com.kevin.cryptotrader.contracts.Interval.M15 -> 15 * 60_000L
      com.kevin.cryptotrader.contracts.Interval.M30 -> 30 * 60_000L
      com.kevin.cryptotrader.contracts.Interval.H1 -> 60 * 60_000L
      com.kevin.cryptotrader.contracts.Interval.H4 -> 4 * 60 * 60_000L
      com.kevin.cryptotrader.contracts.Interval.D1 -> 24 * 60 * 60_000L
    }
  }
}
