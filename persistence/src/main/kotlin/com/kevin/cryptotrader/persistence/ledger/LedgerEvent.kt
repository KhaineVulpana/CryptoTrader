package com.kevin.cryptotrader.persistence.ledger

import com.kevin.cryptotrader.contracts.Interval
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class LedgerEvent {
  abstract val ts: Long
  abstract val type: Type

  @Serializable
  enum class Type {
    CANDLE,
    INTENT,
    ORDER,
    FILL,
    POLICY,
    AUTOMATION,
  }

  @Serializable
  @SerialName("candle")
  data class CandleLogged(
    override val ts: Long,
    val symbol: String,
    val interval: Interval,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Double,
    val source: String,
  ) : LedgerEvent() {
    override val type: Type = Type.CANDLE
  }

  @Serializable
  @SerialName("intent")
  data class IntentLogged(
    override val ts: Long,
    val intentId: String,
    val sourceId: String,
    val accountId: String,
    val kind: String,
    val symbol: String,
    val side: String,
    val notionalUsd: Double? = null,
    val qty: Double? = null,
    val priceHint: Double? = null,
    val meta: Map<String, String> = emptyMap(),
  ) : LedgerEvent() {
    override val type: Type = Type.INTENT
  }

  @Serializable
  @SerialName("order")
  data class OrderPlaced(
    override val ts: Long,
    val orderId: String,
    val accountId: String,
    val symbol: String,
    val side: String,
    val typeName: String,
    val qty: Double,
    val price: Double? = null,
    val stopPrice: Double? = null,
    val tif: String,
    val status: String,
  ) : LedgerEvent() {
    override val type: Type = Type.ORDER
  }

  @Serializable
  @SerialName("fill")
  data class FillRecorded(
    override val ts: Long,
    val orderId: String,
    val accountId: String,
    val symbol: String,
    val side: String,
    val qty: Double,
    val price: Double,
  ) : LedgerEvent() {
    override val type: Type = Type.FILL
  }

  @Serializable
  @SerialName("policy")
  data class PolicyApplied(
    override val ts: Long,
    val policyId: String,
    val accountId: String,
    val version: Int,
    val config: Map<String, String>,
  ) : LedgerEvent() {
    override val type: Type = Type.POLICY
  }

  @Serializable
  @SerialName("automation")
  data class AutomationStateRecorded(
    override val ts: Long,
    val automationId: String,
    val status: String,
    val state: Map<String, String>,
  ) : LedgerEvent() {
    override val type: Type = Type.AUTOMATION
  }
}
