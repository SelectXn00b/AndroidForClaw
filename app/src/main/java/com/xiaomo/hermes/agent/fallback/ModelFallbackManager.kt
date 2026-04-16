package com.xiaomo.hermes.agent.fallback

import com.xiaomo.hermes.agent.auth.AuthProfileManager
import com.xiaomo.hermes.logging.Log

/**
 * Model Fallback Manager — Multi-model fallback with provider rotation.
 *
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/model-fallback.ts (runWithModelFallback, runWithImageModelFallback)
 *
 * Key behaviors from OpenClaw:
 * - resolveFallbackCandidates(): build candidate list (primary + fallbacks)
 * - Iterate candidates until one succeeds
 * - Integration with auth profile cooldown (skip candidates with all profiles in cooldown)
 * - Cooldown probe: try one "cold" provider per run to recover from cooldown
 * - Log each fallback decision
 */
class ModelFallbackManager(
    private val authProfileManager: AuthProfileManager? = null
) {
    companion object {
        private const val TAG = "ModelFallbackManager"
    }

    /**
     * Fallback candidate (aligned with OpenClaw candidate structure).
     */
    data class FallbackCandidate(
        val provider: String,
        val model: String,
        val priority: Int = 0 // Lower = higher priority
    )

    /**
     * Fallback attempt result.
     */
    data class FallbackAttempt(
        val provider: String,
        val model: String,
        val error: String? = null,
        val reason: String? = null,
        val result: Any? = null
    )

    /**
     * Result of the full fallback sequence.
     */
    data class FallbackResult(
        val success: Boolean,
        val provider: String,
        val model: String,
        val response: Any? = null,
        val attempts: List<FallbackAttempt> = emptyList(),
        val error: String? = null
    )

    /**
     * Resolve fallback candidates for a provider/model pair.
     * Aligned with OpenClaw resolveFallbackCandidates().
     *
     * @param provider Primary provider
     * @param model Primary model
     * @param fallbackModels Configured fallback models (format: "provider/model")
     */
    fun resolveCandidates(
        provider: String,
        model: String,
        fallbackModels: List<String> = emptyList()
    ): List<FallbackCandidate> {
        val candidates = mutableListOf<FallbackCandidate>()

        // Primary candidate (always first)
        candidates.add(FallbackCandidate(provider, model, priority = 0))

        // Add fallback candidates from config
        for ((index, fallback) in fallbackModels.withIndex()) {
            val parts = fallback.split("/", limit = 2)
            if (parts.size == 2) {
                candidates.add(FallbackCandidate(
                    provider = parts[0],
                    model = parts[1],
                    priority = index + 1
                ))
            }
        }

        return candidates
    }

    /**
     * Run with model fallback — iterate candidates until one succeeds.
     * Aligned with OpenClaw runWithModelFallback().
     *
     * @param provider Primary provider
     * @param model Primary model
     * @param fallbackModels Configured fallback models
     * @param run Function that executes a call with a specific provider/model
     */
    suspend fun <T> runWithFallback(
        provider: String,
        model: String,
        fallbackModels: List<String> = emptyList(),
        run: suspend (provider: String, model: String) -> T
    ): FallbackResult where T : Any {
        val candidates = resolveCandidates(provider, model, fallbackModels)
        val attempts = mutableListOf<FallbackAttempt>()
        var lastError: String? = null

        for ((index, candidate) in candidates.withIndex()) {
            val isPrimary = index == 0

            // Check auth profile cooldown
            if (authProfileManager != null) {
                val profileIds = authProfileManager.resolveProfileOrder(candidate.provider)
                val anyAvailable = profileIds.any { !authProfileManager.isProfileInCooldown(it) }

                if (profileIds.isNotEmpty() && !anyAvailable) {
                    // All profiles in cooldown — skip unless this is primary or we should probe
                    if (!isPrimary) {
                        val error = "Provider ${candidate.provider} is in cooldown"
                        attempts.add(FallbackAttempt(candidate.provider, candidate.model, error, "cooldown"))
                        Log.d(TAG, "Skipping ${candidate.provider}/${candidate.model}: all profiles in cooldown")
                        continue
                    }
                    // For primary, still try (cooldown probe)
                    Log.d(TAG, "Probing primary ${candidate.provider}/${candidate.model} despite cooldown")
                }
            }

            // Attempt the call
            try {
                Log.d(TAG, "Attempting ${candidate.provider}/${candidate.model}" +
                    if (!isPrimary) " (fallback #${index})" else " (primary)")

                val result = run(candidate.provider, candidate.model)

                if (result != null) {
                    // Mark profile as used on success
                    authProfileManager?.let { apm ->
                        val profiles = apm.resolveProfileOrder(candidate.provider)
                        profiles.firstOrNull()?.let { apm.markUsed(it) }
                    }

                    attempts.add(FallbackAttempt(
                        candidate.provider, candidate.model, result = result
                    ))

                    return FallbackResult(
                        success = true,
                        provider = candidate.provider,
                        model = candidate.model,
                        response = result,
                        attempts = attempts
                    )
                }
            } catch (e: Exception) {
                lastError = e.message ?: "Unknown error"
                val reason = classifyError(lastError)

                // Mark profile as failed
                authProfileManager?.let { apm ->
                    val profiles = apm.resolveProfileOrder(candidate.provider)
                    profiles.firstOrNull()?.let {
                        val failureReason = when (reason) {
                            "rate_limit" -> AuthProfileManager.FailureReason.RATE_LIMIT
                            "overloaded" -> AuthProfileManager.FailureReason.OVERLOADED
                            "billing" -> AuthProfileManager.FailureReason.BILLING
                            "auth" -> AuthProfileManager.FailureReason.AUTH
                            "timeout" -> AuthProfileManager.FailureReason.TIMEOUT
                            else -> AuthProfileManager.FailureReason.UNKNOWN
                        }
                        apm.markFailure(it, failureReason)
                    }
                }

                attempts.add(FallbackAttempt(candidate.provider, candidate.model, lastError, reason))
                Log.w(TAG, "${candidate.provider}/${candidate.model} failed: $lastError")
            }
        }

        return FallbackResult(
            success = false,
            provider = provider,
            model = model,
            attempts = attempts,
            error = lastError ?: "All fallback candidates failed"
        )
    }

    /**
     * Classify error into a reason string.
     * Aligned with OpenClaw error classification.
     */
    private fun classifyError(error: String): String {
        return when {
            error.contains("rate limit", ignoreCase = true) -> "rate_limit"
            error.contains("overload", ignoreCase = true) ||
                error.contains("529") -> "overloaded"
            error.contains("billing", ignoreCase = true) ||
                error.contains("credit", ignoreCase = true) -> "billing"
            error.contains("auth", ignoreCase = true) ||
                error.contains("401") ||
                error.contains("403") -> "auth"
            error.contains("timeout", ignoreCase = true) -> "timeout"
            error.contains("model", ignoreCase = true) &&
                error.contains("not found", ignoreCase = true) -> "model_not_found"
            else -> "unknown"
        }
    }
}
