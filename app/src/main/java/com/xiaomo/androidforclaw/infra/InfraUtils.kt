package com.xiaomo.androidforclaw.infra

/**
 * OpenClaw module: infra
 * Source: OpenClaw/src/infra/
 *
 * Hub re-export for infra sub-modules:
 *  - RetryPolicy / withRetry
 *  - BackoffPolicy / computeBackoffDelay
 *  - JsonFile (typed JSON file I/O)
 *  - SecureRandom (generateSecureToken / generateSecureHex / generateNumericCode)
 */
object InfraUtils {

    fun nowEpochMs(): Long = System.currentTimeMillis()

    fun slugify(input: String): String =
        input.trim().lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')

    fun truncate(text: String, maxLength: Int, suffix: String = "…"): String {
        if (text.length <= maxLength) return text
        return text.take(maxLength - suffix.length) + suffix
    }
}
