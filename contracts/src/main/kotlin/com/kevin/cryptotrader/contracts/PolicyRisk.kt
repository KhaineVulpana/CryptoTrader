package com.kevin.cryptotrader.contracts

data class NetPlan(val intents: List<Intent>)

interface PolicyEngine {
  fun net(intents: List<Intent>, positions: List<Position>): NetPlan
}

interface RiskSizer {
  fun size(plan: NetPlan, account: AccountSnapshot): List<Order>
}
