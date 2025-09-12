package com.kevin.cryptotrader.contracts

import kotlinx.coroutines.flow.Flow

interface AutomationRuntime {
  fun load(def: AutomationDef): LoadedProgram
  fun run(env: RuntimeEnv): Flow<Intent>
}

interface LoadedProgram
data class RuntimeEnv(val clockMs: () -> Long)
