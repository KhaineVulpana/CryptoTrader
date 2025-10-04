package com.kevin.cryptotrader.runtime.execution

import java.time.Duration

data class ExecutionConfig(
    val cooldown: Duration = Duration.ZERO,
    val priorityOrder: List<String> = emptyList(),
    val vote: VoteConfig? = null,
    val portfolioTargets: Map<String, Double> = emptyMap(),
    val maxIntentCache: Int = 1024
)

data class VoteConfig(
    val threshold: Int,
    val groupKey: String = "voteGroup"
)
