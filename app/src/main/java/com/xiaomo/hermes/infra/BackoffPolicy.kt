package com.xiaomo.hermes.infra

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToLong
import kotlin.random.Random

/**
 * OpenClaw module: infra
 * Source: OpenClaw/src/infra/backoff.ts
 *
 * Exponential backoff with jitter, and cancellable sleep.
 */

data class BackoffPolicy(
    val initialMs: Long = 1000,
    val maxMs: Long = 60_000,
    val factor: Double = 2.0,
    val jitter: Double = 0.1
)

/** Compute backoff delay for the given attempt (1-based). Aligned with TS computeBackoff(). */
fun computeBackoff(policy: BackoffPolicy, attempt: Int): Long {
    val base = policy.initialMs * policy.factor.pow(max(attempt - 1, 0).toDouble())
    val jitterAmount = base * policy.jitter * Random.nextDouble()
    return min(policy.maxMs, (base + jitterAmount).roundToLong())
}

/** Legacy alias. */
fun computeBackoffDelay(attempt: Int, policy: BackoffPolicy = BackoffPolicy()): Long =
    computeBackoff(policy, attempt)

/**
 * Coroutine-aware sleep that respects cancellation.
 * Aligned with TS sleepWithAbort().
 * If the coroutine's Job is cancelled during sleep, throws CancellationException.
 */
suspend fun sleepWithAbort(ms: Long, job: Job? = null) {
    if (ms <= 0) return
    try {
        delay(ms)
    } catch (e: CancellationException) {
        throw e
    }
}
