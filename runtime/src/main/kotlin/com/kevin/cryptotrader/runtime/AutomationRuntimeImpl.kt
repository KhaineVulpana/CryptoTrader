package com.kevin.cryptotrader.runtime

import com.kevin.cryptotrader.contracts.AutomationDef
import com.kevin.cryptotrader.contracts.AutomationRuntime
import com.kevin.cryptotrader.contracts.Intent
import com.kevin.cryptotrader.contracts.LoadedProgram
import com.kevin.cryptotrader.contracts.RuntimeEnv
import com.kevin.cryptotrader.runtime.vm.InputLoader
import com.kevin.cryptotrader.runtime.vm.Interpreter
import com.kevin.cryptotrader.runtime.vm.ProgramJson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json

class AutomationRuntimeImpl : AutomationRuntime {
  private val json = Json { ignoreUnknownKeys = true; classDiscriminator = "type" }
  @Volatile private var loaded: LoadedProgramImpl? = null

  override fun load(def: AutomationDef): LoadedProgram {
    val program = json.decodeFromString(ProgramJson.serializer(), def.graphJson)
    val lp = LoadedProgramImpl(program)
    loaded = lp
    return lp
  }

  override fun run(env: com.kevin.cryptotrader.contracts.RuntimeEnv): Flow<Intent> {
    val lp = loaded ?: return flow { }
    return lp.run(env)
  }

  private class LoadedProgramImpl(private val program: ProgramJson) : LoadedProgram {
    fun run(env: RuntimeEnv): Flow<Intent> {
      val inputs: List<InputBar> = when {
        program.inputsInline != null -> program.inputsInline.map { InputBar(it.ts, it.open, it.high, it.low, it.close, it.volume) }
        program.inputsCsvPath != null -> InputLoader.fromCsv(program.inputsCsvPath)
        else -> emptyList()
      }
      val interp = Interpreter(program, env.observer)
      return interp.run(inputs, env)
    }
  }
}
