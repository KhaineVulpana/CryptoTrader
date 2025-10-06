package com.kevin.cryptotrader.persistence.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "policies")
data class PolicyEntity(
  @PrimaryKey val policyId: String,
  val version: Int,
  val accountId: String,
  val configJson: String,
  val appliedAt: Long,
)
