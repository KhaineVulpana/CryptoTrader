package com.kevin.cryptotrader.contracts

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class ContractsSmokeTest {
  @Test
  fun `domain enums are stable`() {
    assertEquals(Side.BUY, Side.valueOf("BUY"))
    assertEquals(OrderType.LIMIT, OrderType.valueOf("LIMIT"))
  }
}

