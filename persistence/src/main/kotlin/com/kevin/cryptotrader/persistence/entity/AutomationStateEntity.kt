package com.kevin.cryptotrader.persistence.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "automation_state")
data class AutomationStateEntity(
  @PrimaryKey val automationId: String,
  val status: String,
  val stateJson: String,
  val updatedAt: Long,
)
