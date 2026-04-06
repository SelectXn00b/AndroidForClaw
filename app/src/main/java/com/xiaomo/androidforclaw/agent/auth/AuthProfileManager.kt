package com.xiaomo.androidforclaw.agent.auth

import com.xiaomo.androidforclaw.logging.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

/**
 * Auth Profile Manager — Multi-API-key rotation with cooldown.
 *
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/auth-profiles.ts (re-exports from auth-profiles/ subdir)
 * - ../openclaw/src/agents/auth-profiles/order.ts (resolveAuthProfileOrder, resolveAuthProfileEligibility)
 * - ../openclaw/src/agents/auth-profiles/credential-state.ts (cooldown tracking)
 * - ../openclaw/src/agents/auth-profiles.runtime.ts (runtime profile management)
 *
 * Key behaviors from OpenClaw:
 * - resolveAuthProfileOrder(): resolve priority list of profiles for a provider
 * - markAuthProfileFailure(): mark profile failed with exponential backoff
 * - markAuthProfileUsed(): mark profile as successfully used (reset errors)
 * - isProfileInCooldown(): check if profile is in cooldown
 * - clearExpiredCooldowns(): reset expired cooldown windows
 * - cooldown: 1min → 5min → 25min → 1hr (exponential, capped)
 * - billing/auth_permanent: 5hr → 10hr → 20hr → 24hr (exponential, capped)
 */
class AuthProfileManager(
    private val storeFile: File
) {
    companion object {
        private const val TAG = "AuthProfileManager"

        // Cooldown: 1min → 5min → 25min → 1hr (capped)
        // Aligned with OpenClaw calculateAuthProfileCooldownMs
        private const val COOLDOWN_BASE_MS = 60_000L // 1 minute
        private const val COOLDOWN_MAX_MS = 3_600_000L // 1 hour
        private const val COOLDOWN_MULTIPLIER = 5

        // Billing/auth_permanent backoff: 5hr → 10hr → 20hr → 24hr (capped)
        // Aligned with OpenClaw calculateAuthProfileBillingDisableMsWithConfig
        private const val BILLING_BACKOFF_MS = 5 * 3_600_000L // 5 hours
        private const val BILLING_MAX_MS = 24 * 3_600_000L // 24 hours

        // Failure window: reset counters if last failure was >24h ago
        // Aligned with OpenClaw resolveAuthCooldownConfig.failureWindowHours
        private const val FAILURE_WINDOW_MS = 24 * 3_600_000L // 24 hours
    }

    /**
     * Profile credential types (aligned with OpenClaw cred.type).
     */
    enum class ProfileType { API_KEY, TOKEN, OAUTH }

    /**
     * Failure reasons (aligned with OpenClaw FAILURE_REASON_PRIORITY).
     */
    enum class FailureReason {
        AUTH_PERMANENT, AUTH, BILLING, FORMAT, MODEL_NOT_FOUND,
        OVERLOADED, TIMEOUT, RATE_LIMIT, UNKNOWN
    }

    /**
     * Auth profile entry (aligned with OpenClaw profile store entry).
     */
    data class AuthProfile(
        val profileId: String,
        val provider: String,
        val type: ProfileType = ProfileType.API_KEY,
        val key: String? = null,
        val email: String? = null,
        val displayName: String? = null
    )

    /**
     * Usage stats for a profile (aligned with OpenClaw usageStats[profileId]).
     */
    data class ProfileUsageStats(
        val errorCount: Int = 0,
        val cooldownUntil: Long? = null,
        val disabledUntil: Long? = null,
        val disabledReason: String? = null,
        val failureCounts: Map<String, Int>? = null,
        val lastFailureAt: Long? = null,
        val lastUsed: Long? = null
    )

    /**
     * Auth profile store (aligned with OpenClaw AuthProfileStore).
     */
    data class AuthProfileStore(
        val profiles: Map<String, AuthProfile> = emptyMap(),
        val usageStats: Map<String, ProfileUsageStats> = emptyMap(),
        val order: Map<String, List<String>> = emptyMap(),
        val lastGood: Map<String, String> = emptyMap()
    )

    private val gson = Gson()
    private var store: AuthProfileStore = loadStore()

    // ── Store persistence ──

    private fun loadStore(): AuthProfileStore {
        if (!storeFile.exists()) return AuthProfileStore()
        return try {
            val json = storeFile.readText()
            val type = object : TypeToken<AuthProfileStore>() {}.type
            gson.fromJson(json, type) ?: AuthProfileStore()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load auth profile store: ${e.message}")
            AuthProfileStore()
        }
    }

    private fun saveStore() {
        try {
            storeFile.parentFile?.mkdirs()
            storeFile.writeText(gson.toJson(store))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save auth profile store: ${e.message}")
        }
    }

    // ── Profile registration ──

    /**
     * Register a profile from config (API key).
     * Called during config loading to populate the store.
     */
    fun registerProfile(profileId: String, provider: String, apiKey: String) {
        val profile = AuthProfile(
            profileId = profileId,
            provider = provider,
            type = ProfileType.API_KEY,
            key = apiKey
        )
        store = store.copy(
            profiles = store.profiles + (profileId to profile)
        )
        saveStore()
    }

    /**
     * Resolve available profile IDs for a provider.
     * Aligned with OpenClaw resolveAuthProfileOrder().
     */
    fun resolveProfileOrder(provider: String, preferredProfile: String? = null): List<String> {
        val now = System.currentTimeMillis()
        clearExpiredCooldowns(now)

        // Get profiles matching provider
        val providerProfiles = store.profiles.filter { it.value.provider == provider }.keys.toList()

        if (providerProfiles.isEmpty()) return emptyList()

        // Use explicit order if configured
        val configuredOrder = store.order[provider]
        val baseOrder = configuredOrder ?: providerProfiles

        // Filter eligible profiles (have a key, matching provider)
        val eligible = baseOrder.filter { profileId ->
            val profile = store.profiles[profileId]
            profile != null && profile.provider == provider && !profile.key.isNullOrBlank()
        }

        // Separate available vs cooldown
        val available = mutableListOf<String>()
        val inCooldown = mutableListOf<Pair<String, Long>>()

        for (profileId in eligible) {
            if (isProfileInCooldown(profileId, now)) {
                val until = store.usageStats[profileId]?.let { resolveUnusableUntil(it) } ?: now
                inCooldown.add(profileId to until)
            } else {
                available.add(profileId)
            }
        }

        // Sort cooldown by soonest expiry
        val cooldownSorted = inCooldown.sortedBy { it.second }.map { it.first }

        // Build final order: available first, cooldown last
        val ordered = available + cooldownSorted

        // If preferred profile is specified, move it to front
        return if (preferredProfile != null && preferredProfile in ordered) {
            listOf(preferredProfile) + ordered.filter { it != preferredProfile }
        } else {
            ordered
        }
    }

    /**
     * Get the API key for a profile.
     */
    fun getApiKey(profileId: String): String? {
        return store.profiles[profileId]?.key
    }

    // ── Cooldown logic ──

    /**
     * Check if a profile is currently in cooldown.
     * Aligned with OpenClaw isProfileInCooldown().
     */
    fun isProfileInCooldown(profileId: String, now: Long = System.currentTimeMillis()): Boolean {
        val stats = store.usageStats[profileId] ?: return false
        val unusableUntil = resolveUnusableUntil(stats) ?: return false
        return now < unusableUntil
    }

    /**
     * Resolve when a profile becomes usable again.
     * Aligned with OpenClaw resolveProfileUnusableUntil().
     */
    private fun resolveUnusableUntil(stats: ProfileUsageStats): Long? {
        val values = listOfNotNull(stats.cooldownUntil, stats.disabledUntil)
            .filter { it > 0 }
        return if (values.isEmpty()) null else values.max()
    }

    /**
     * Mark a profile as failed for a specific reason.
     * Aligned with OpenClaw markAuthProfileFailure().
     */
    fun markFailure(
        profileId: String,
        reason: FailureReason,
        runId: String? = null
    ) {
        val profile = store.profiles[profileId] ?: return
        val now = System.currentTimeMillis()
        val existing = store.usageStats[profileId] ?: ProfileUsageStats()

        // Check if failure window expired → reset counters
        val windowExpired = existing.lastFailureAt != null &&
            (now - existing.lastFailureAt) > FAILURE_WINDOW_MS
        val unusableUntil = existing.let { resolveUnusableUntil(it) }
        val cooldownExpired = unusableUntil != null && now >= unusableUntil
        val shouldReset = windowExpired || cooldownExpired

        val nextErrorCount = (if (shouldReset) 0 else existing.errorCount) + 1
        val failureCounts = (if (shouldReset) emptyMap() else existing.failureCounts ?: emptyMap())
            .toMutableMap()
        failureCounts[reason.name] = (failureCounts[reason.name] ?: 0) + 1

        val updatedStats: ProfileUsageStats

        if (reason == FailureReason.BILLING || reason == FailureReason.AUTH_PERMANENT) {
            // Long disable: 5hr → 10hr → 20hr → 24hr
            // Aligned with OpenClaw calculateAuthProfileBillingDisableMsWithConfig
            val backoffMs = calculateBillingBackoffMs(failureCounts[reason.name] ?: 1)
            updatedStats = existing.copy(
                errorCount = nextErrorCount,
                failureCounts = failureCounts,
                lastFailureAt = now,
                disabledUntil = keepActiveWindow(existing.disabledUntil, now, now + backoffMs),
                disabledReason = reason.name.lowercase()
            )
        } else {
            // Short cooldown: 1min → 5min → 25min → 1hr
            // Aligned with OpenClaw calculateAuthProfileCooldownMs
            val backoffMs = calculateCooldownMs(nextErrorCount)
            updatedStats = existing.copy(
                errorCount = nextErrorCount,
                failureCounts = failureCounts,
                lastFailureAt = now,
                cooldownUntil = keepActiveWindow(existing.cooldownUntil, now, now + backoffMs)
            )
        }

        store = store.copy(
            usageStats = store.usageStats + (profileId to updatedStats)
        )
        saveStore()

        Log.d(TAG, "Profile $profileId failed (${reason.name}): errorCount=$nextErrorCount, " +
            "cooldownUntil=${updatedStats.cooldownUntil}, disabledUntil=${updatedStats.disabledUntil}")
    }

    /**
     * Mark a profile as successfully used (reset errors).
     * Aligned with OpenClaw markAuthProfileUsed().
     */
    fun markUsed(profileId: String) {
        val existing = store.usageStats[profileId] ?: ProfileUsageStats()
        val updated = existing.copy(
            errorCount = 0,
            cooldownUntil = null,
            disabledUntil = null,
            disabledReason = null,
            failureCounts = null,
            lastUsed = System.currentTimeMillis()
        )
        val provider = store.profiles[profileId]?.provider
        val updatedLastGood = if (provider != null) {
            store.lastGood + (provider to profileId)
        } else {
            store.lastGood
        }
        store = store.copy(
            usageStats = store.usageStats + (profileId to updated),
            lastGood = updatedLastGood
        )
        saveStore()
    }

    /**
     * Clear expired cooldowns from all profiles.
     * Aligned with OpenClaw clearExpiredCooldowns().
     */
    fun clearExpiredCooldowns(now: Long = System.currentTimeMillis()) {
        val updatedStats = store.usageStats.toMutableMap()
        var mutated = false

        for ((profileId, stats) in updatedStats) {
            val cooldownExpired = stats.cooldownUntil != null && now >= stats.cooldownUntil
            val disabledExpired = stats.disabledUntil != null && now >= stats.disabledUntil

            if (cooldownExpired || disabledExpired) {
                val newStats = stats.copy(
                    cooldownUntil = if (cooldownExpired) null else stats.cooldownUntil,
                    disabledUntil = if (disabledExpired) null else stats.disabledUntil,
                    disabledReason = if (disabledExpired) null else stats.disabledReason,
                    // Reset counters if profile is fully usable again
                    errorCount = if (!cooldownExpired && !disabledExpired) stats.errorCount else 0,
                    failureCounts = if (!cooldownExpired && !disabledExpired) stats.failureCounts else null
                )
                if (cooldownExpired) {
                    newStats.copy(
                        errorCount = 0,
                        failureCounts = null
                    ).let { updatedStats[profileId] = it }
                } else {
                    updatedStats[profileId] = newStats
                }
                mutated = true
            }
        }

        if (mutated) {
            store = store.copy(usageStats = updatedStats)
            saveStore()
        }
    }

    // ── Cooldown calculation (aligned with OpenClaw) ──

    /**
     * Calculate cooldown backoff: 1min → 5min → 25min → 1hr (capped).
     * Formula: min(1hr, 5min * 5^(errorCount-1))
     * Aligned with OpenClaw calculateAuthProfileCooldownMs.
     */
    private fun calculateCooldownMs(errorCount: Int): Long {
        val n = errorCount.coerceAtLeast(1)
        return (COOLDOWN_BASE_MS * Math.pow(COOLDOWN_MULTIPLIER.toDouble(), (n - 1).toDouble()))
            .toLong()
            .coerceAtMost(COOLDOWN_MAX_MS)
    }

    /**
     * Calculate billing backoff: 5hr → 10hr → 20hr → 24hr (capped).
     * Formula: min(24hr, 5hr * 2^(errorCount-1))
     * Aligned with OpenClaw calculateAuthProfileBillingDisableMsWithConfig.
     */
    private fun calculateBillingBackoffMs(errorCount: Int): Long {
        val n = errorCount.coerceAtLeast(1)
        return (BILLING_BACKOFF_MS * Math.pow(2.0, (n - 1).toDouble()))
            .toLong()
            .coerceAtMost(BILLING_MAX_MS)
    }

    /**
     * Keep existing window if still active, otherwise use recomputed value.
     * Aligned with OpenClaw keepActiveWindowOrRecompute().
     */
    private fun keepActiveWindow(existingUntil: Long?, now: Long, recomputedUntil: Long): Long {
        return if (existingUntil != null && existingUntil > now) existingUntil else recomputedUntil
    }
}
