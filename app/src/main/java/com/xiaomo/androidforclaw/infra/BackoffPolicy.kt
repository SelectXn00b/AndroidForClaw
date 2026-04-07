package com.xiaomo.androidforclaw.infra

import kotlin.math.min
import kotlin.random.Random

/**
 * OpenClaw module: infra
 * Source: OpenClaw/src/infra/backoff.ts
 */

data class BackoffPolicy(
    val baseMs: Long = 1000,
    val maxMs: Long = 60_000,
    val factor: Double = 2.0,
    val jitterFraction: Double = 0.1
)

fun computeBackoffDelay(attempt: Int, policy: BackoffPolicy = BackoffPolicy()): Long {
    val exponential = (policy.baseMs * Math.pow(policy.factor, (attempt - 1).toDouble())).toLong()
    val capped = min(exponential, policy.maxMs)
    val jitter = (capped * policy.jitterFraction * Random.nextDouble()).toLong()
    return capped + jitter
}
