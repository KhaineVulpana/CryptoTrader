package com.kevin.cryptotrader.core.policy

import com.kevin.cryptotrader.contracts.AccountSnapshot
import com.kevin.cryptotrader.contracts.NetPlan
import com.kevin.cryptotrader.contracts.Order
import com.kevin.cryptotrader.contracts.OrderType
import com.kevin.cryptotrader.contracts.RiskSizer

/**
 * Simple, deterministic risk sizer:
 * - If qty provided, emit a MARKET order with that qty.
 * - Else if notionalUsd provided and priceHint present, qty = notionalUsd / priceHint.
 * - Else skip (un-sizable) — higher-level should surface an error.
 * No leverage or balance checks to keep it pure and testable.
 */
class RiskSizerImpl : RiskSizer {
  override fun size(plan: NetPlan, account: AccountSnapshot): List<Order> {
    val out = mutableListOf<Order>()
    plan.intents.forEach { intent ->
      val qty = intent.qty ?: intent.notionalUsd?.let { n -> intent.priceHint?.let { p -> if (p > 0) n / p else null } }
      if (qty != null && qty > 0.0) {
        out.add(
          Order(
            clientOrderId = "ord-${intent.id}",
            symbol = intent.symbol,
            side = intent.side,
            type = OrderType.MARKET,
            qty = qty,
            price = null,
          ),
        )
      }
    }
    return out
  }
}

