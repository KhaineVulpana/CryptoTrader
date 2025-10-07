package com.kevin.cryptotrader.tools.compiler

import java.nio.file.Files
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals

class BlockCompilerParityTest {
  @Test
  fun block_json_matches_dsl_for_ema_cross() {
    val src = Files.readString(resolvePath("fixtures/automations/ema_cross.json"))
    val compiled = BlockCompiler.compile(src)
    val expected = DslParityFixtures.emaCross()
    assertEquals(expected.ir, compiled.ir)
    assertEquals(expected.program, compiled.program)
  }

  @Test
  fun block_json_matches_dsl_for_alert_workflow() {
    val src = Files.readString(resolvePath("fixtures/automations/alert_workflow.json"))
    val compiled = BlockCompiler.compile(src)
    val expected = DslParityFixtures.alertWorkflow()
    assertEquals(expected.ir, compiled.ir)
    assertEquals(expected.program, compiled.program)
  }

  private fun resolvePath(path: String) = listOf(
    Paths.get(path),
    Paths.get("../$path"),
    Paths.get("../../$path"),
    Paths.get("../../../$path"),
  ).firstOrNull { Files.exists(it) } ?: error("Fixture not found: $path")
}
