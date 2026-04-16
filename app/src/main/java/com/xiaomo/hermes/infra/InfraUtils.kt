package com.xiaomo.hermes.infra

/**
 * OpenClaw module: infra
 * Source: OpenClaw/src/infra/
 *
 * Hub re-export for infra sub-modules:
 *  - SecureRandom: generateSecureUuid, generateSecureToken, generateSecureHex,
 *    generateSecureFraction, generateSecureInt, generateNumericCode,
 *    sha256Hex, sha256, sha256Base64, hmacSha256, hmacSha256Hex,
 *    safeEqual, safeEqualSecret, base64UrlEncode, base64UrlDecode
 *  - BackoffPolicy: computeBackoff, sleepWithAbort
 *  - RetryPolicy: withRetry
 *  - JsonFile: loadJsonFile, saveJsonFile, writeJsonAtomic, writeTextAtomic, AsyncLock
 *  - RateLimiter: FixedWindowRateLimiter, RateLimitResult
 *  - DeviceIdentity: DeviceIdentityManager
 *  - EventBus: EventBus, GlobalEventBus, Unsubscribe
 *  - Archive: Archive, ArchiveExtractOptions, ArchiveSecurityException
 *  - NetUtils: NetUtils, normalizeFingerprint
 *  - MapUtils: pruneMapToMaxSize, jsonUtf8Bytes, isPathInside,
 *    generatePairingToken, verifyPairingToken
 */
object InfraUtils {

    fun nowEpochMs(): Long = System.currentTimeMillis()

    fun slugify(input: String): String =
        input.trim().lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')

    fun truncate(text: String, maxLength: Int, suffix: String = "\u2026"): String {
        if (text.length <= maxLength) return text
        val cutoff = maxLength - suffix.length
        if (cutoff <= 0) return suffix.take(maxLength)
        return text.take(cutoff) + suffix
    }
}
