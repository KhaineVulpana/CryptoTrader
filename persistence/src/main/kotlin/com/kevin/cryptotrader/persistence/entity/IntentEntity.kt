package com.kevin.cryptotrader.persistence.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.kevin.cryptotrader.contracts.Intent
import com.kevin.cryptotrader.contracts.Side
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

@Entity(
  tableName = "intents",
  indices = [
    Index(value = ["sourceId", "ts"]),
    Index(value = ["accountId", "ts"]),
  ],
)
data class IntentEntity(
  @PrimaryKey val id: String,
  val sourceId: String,
  val accountId: String,
  val kind: String,
  val symbol: String,
  val side: String,
  val notionalUsd: Double?,
  val qty: Double?,
  val priceHint: Double?,
  val metaJson: String,
  val ts: Long,
) {
  companion object {
    fun from(
      id: String,
      sourceId: String,
      accountId: String,
      kind: String,
      symbol: String,
      side: String,
      notionalUsd: Double?,
      qty: Double?,
      priceHint: Double?,
      meta: Map<String, String>,
      ts: Long,
      json: Json,
    ): IntentEntity {
      val serializer = MapSerializer(String.serializer(), String.serializer())
      return IntentEntity(
        id = id,
        sourceId = sourceId,
        accountId = accountId,
        kind = kind,
        symbol = symbol,
        side = side,
        notionalUsd = notionalUsd,
        qty = qty,
        priceHint = priceHint,
        metaJson = json.encodeToString(serializer, meta),
        ts = ts,
      )
    }
  }

  fun meta(json: Json): Map<String, String> {
    val serializer = MapSerializer(String.serializer(), String.serializer())
    return json.decodeFromString(serializer, metaJson)
  }

  fun toContract(json: Json): Intent =
    Intent(
      id = id,
      sourceId = sourceId,
      kind = kind,
      symbol = symbol,
      side = Side.valueOf(side),
      notionalUsd = notionalUsd,
      qty = qty,
      priceHint = priceHint,
      meta = meta(json),
    )
}
