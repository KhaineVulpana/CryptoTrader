package com.kevin.cryptotrader.persistence.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.kevin.cryptotrader.persistence.ledger.LedgerEvent
import kotlinx.serialization.json.Json

@Entity(
  tableName = "ledger_events",
  indices = [Index(value = ["ts"]), Index(value = ["type"])],
)
data class LedgerEventEntity(
  @PrimaryKey(autoGenerate = true) val sequence: Long = 0,
  val ts: Long,
  val type: String,
  val payload: String,
) {
  companion object {
    fun from(event: LedgerEvent, json: Json): LedgerEventEntity =
      LedgerEventEntity(
        ts = event.ts,
        type = event.type.name,
        payload = json.encodeToString(LedgerEvent.serializer(), event),
      )
  }

  fun toLedgerEvent(json: Json): LedgerEvent = json.decodeFromString(LedgerEvent.serializer(), payload)
}
