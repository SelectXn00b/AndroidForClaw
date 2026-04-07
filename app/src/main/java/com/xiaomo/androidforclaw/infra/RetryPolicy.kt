package com.xiaomo.androidforclaw.infra

/**
 * OpenClaw module: infra
 * Source: OpenClaw/src/infra/retry.ts
 */

data class RetryPolicy(
    val maxAttempts: Int = 3,
    val initialDelayMs: Long = 1000,
    val maxDelayMs: Long = 30_000,
    val backoffMultiplier: Double = 2.0,
    val retryableExceptions: Set<Class<out Throwable>> = emptySet()
)

suspend fun <T> withRetry(
    policy: RetryPolicy = RetryPolicy(),
    block: suspend (attempt: Int) -> T
): T {
    var lastException: Throwable? = null
    var delay = policy.initialDelayMs
    for (attempt in 1..policy.maxAttempts) {
        try {
            return block(attempt)
        } catch (e: Throwable) {
            lastException = e
            if (attempt == policy.maxAttempts) break
            if (policy.retryableExceptions.isNotEmpty() &&
                policy.retryableExceptions.none { it.isInstance(e) }
            ) break
            kotlinx.coroutines.delay(delay)
            delay = (delay * policy.backoffMultiplier).toLong().coerceAtMost(policy.maxDelayMs)
        }
    }
    throw lastException!!
}
