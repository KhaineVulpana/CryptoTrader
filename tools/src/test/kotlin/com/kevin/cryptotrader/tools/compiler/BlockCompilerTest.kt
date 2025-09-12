package com.kevin.cryptotrader.tools.compiler

import com.kevin.cryptotrader.runtime.vm.ProgramJson
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BlockCompilerTest {
  @Test
  fun compiles_ema_cross_fixture_to_program() {
    val path = resolvePath("fixtures/automations/ema_cross.json")
    val src = Files.readString(path)
    val out = BlockCompiler.compile(src)
    val program = Json { ignoreUnknownKeys = true }.decodeFromString(ProgramJson.serializer(), out)
    assertEquals(2, program.series.size)
    assertTrue(program.rules.isNotEmpty())
  }

  private fun resolvePath(path: String): java.nio.file.Path {
    val candidates = listOf(
      Paths.get(path),
      Paths.get("../$path"),
      Paths.get("../../$path"),
      Paths.get("../../../$path"),
    )
    return candidates.firstOrNull { Files.exists(it) } ?: error("Fixture not found: $path")
  }
}

