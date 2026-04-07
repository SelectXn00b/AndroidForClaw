package com.xiaomo.androidforclaw.shared

data class UsageAggregates(
    val inputTokens: Long = 0,
    val outputTokens: Long = 0,
    val cacheReadTokens: Long = 0,
    val cacheWriteTokens: Long = 0
)
