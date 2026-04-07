package com.xiaomo.androidforclaw.infra

import kotlin.math.floor
import kotlin.math.max

/**
 * OpenClaw module: infra
 * Source: OpenClaw/src/infra/fixed-window-rate-limit.ts
 *
 * Fixed-window rate limiter. 1:1 aligned with TS createFixedWindowRateLimiter().
 */

data class RateLimitResult(
    val allowed: Boolean,
    val retryAfterMs: Long,
    val remaining: Int
)

class FixedWindowRateLimiter(
    maxRequests: Int,
    windowMs: Long,
    private val clock: () -> Long = System::currentTimeMillis
) {
    private val maxRequests: Int = max(1, floor(maxRequests.toDouble()).toInt())
    private val windowMs: Long = max(1, windowMs)
    private var count: Int = 0
    private var windowStartMs: Long = 0

    @Synchronized
    fun consume(): RateLimitResult {
        val nowMs = clock()
        if (nowMs - windowStartMs >= this.windowMs) {
            windowStartMs = nowMs
            count = 0
        }
        if (count >= maxRequests) {
            return RateLimitResult(
                allowed = false,
                retryAfterMs = max(0, windowStartMs + this.windowMs - nowMs),
                remaining = 0
            )
        }
        count += 1
        return RateLimitResult(
            allowed = true,
            retryAfterMs = 0,
            remaining = max(0, maxRequests - count)
        )
    }

    @Synchronized
    fun reset() {
        count = 0
        windowStartMs = 0
    }
}
