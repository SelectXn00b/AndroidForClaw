package com.xiaomo.androidforclaw.cron

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/cron/delivery.ts
 *   (resolveCronDeliveryPlan, resolveFailureDestination)
 *
 * AndroidForClaw adaptation: cron job output delivery resolution.
 * Aligned with OpenClaw delivery.ts.
 */

import com.xiaomo.androidforclaw.core.MyApplication
import com.xiaomo.androidforclaw.logging.Log

/**
 * Resolved delivery plan — where to send cron job output.
 * Aligned with OpenClaw CronDeliveryPlan.
 */
data class CronDeliveryPlan(
    val mode: DeliveryMode,
    val channel: String? = null,
    val to: String? = null,
    val accountId: String? = null,
    val source: String = "delivery",  // "delivery" | "payload"
    val requested: Boolean = false
)

/**
 * Failure delivery plan.
 * Aligned with OpenClaw CronFailureDeliveryPlan.
 */
data class CronFailureDeliveryPlan(
    val mode: String,  // "announce" | "webhook"
    val channel: String? = null,
    val to: String? = null,
    val accountId: String? = null
)

/** Failure notification timeout */
const val FAILURE_NOTIFICATION_TIMEOUT_MS = 30_000L

/**
 * CronDeliveryResolver — Cron job output delivery resolution.
 * Aligned with OpenClaw cron/delivery.ts.
 */
object CronDeliveryResolver {

    private const val TAG = "CronDeliveryResolver"

    // ── Normalization helpers (aligned with OpenClaw) ──

    private fun normalizeChannel(value: Any?): String? {
        if (value !is String) return null
        val trimmed = value.trim().lowercase()
        return trimmed.ifEmpty { null }
    }

    private fun normalizeTo(value: Any?): String? {
        if (value !is String) return null
        val trimmed = value.trim()
        return trimmed.ifEmpty { null }
    }

    private fun normalizeAccountId(value: Any?): String? = normalizeTo(value)

    private fun normalizeMode(value: Any?): String? {
        if (value !is String) return null
        return when (value.trim().lowercase()) {
            "announce" -> "announce"
            "webhook" -> "webhook"
            "none" -> "none"
            "deliver" -> "announce"  // legacy alias
            else -> null
        }
    }

    private fun normalizeFailureMode(value: Any?): String? {
        if (value !is String) return null
        return when (value.trim().lowercase()) {
            "announce" -> "announce"
            "webhook" -> "webhook"
            else -> null
        }
    }

    // ── DeliveryMode enum → string ──

    private fun deliveryModeToNormalized(mode: DeliveryMode): String = when (mode) {
        DeliveryMode.ANNOUNCE -> "announce"
        DeliveryMode.WEBHOOK -> "webhook"
        DeliveryMode.NONE -> "none"
    }

    // ── "last" channel resolution ──

    /**
     * Resolve "last" channel to the most recently active chat.
     * Returns Pair(channel, chatId) or null if no recent chat available.
     */
    private fun resolveLastChannel(): Pair<String, String>? {
        val (channel, chatId) = MyApplication.getLastActiveChat()
        if (channel != null && chatId != null) {
            return Pair(channel, chatId)
        }
        Log.w(TAG, "channel=last but no recent chat available")
        return null
    }

    // ── Core resolution (aligned with OpenClaw resolveCronDeliveryPlan) ──

    /**
     * Resolve delivery plan for a cron job.
     * Aligned with OpenClaw resolveCronDeliveryPlan.
     */
    fun resolveDeliveryPlan(job: CronJob): CronDeliveryPlan {
        val payload = job.payload as? CronPayload.AgentTurn
        val delivery = job.delivery

        val payloadChannel = if (payload != null) normalizeChannel(payload.channel) else null
        val payloadTo = if (payload != null) normalizeTo(payload.to) else null

        val deliveryChannel = normalizeChannel(delivery?.channel)
        val deliveryTo = normalizeTo(delivery?.to)

        // channel defaults to "last" when neither delivery nor payload specifies one
        val channel = deliveryChannel ?: payloadChannel ?: "last"
        val to = deliveryTo ?: payloadTo
        val deliveryAccountId = normalizeAccountId(delivery?.accountId)

        if (delivery != null) {
            // Direct enum → normalized string (fixes bug where .name gave uppercase)
            val rawMode = deliveryModeToNormalized(delivery.mode)
            val resolvedMode = normalizeMode(rawMode) ?: "announce"

            val mode = when (resolvedMode) {
                "announce" -> DeliveryMode.ANNOUNCE
                "webhook" -> DeliveryMode.WEBHOOK
                "none" -> DeliveryMode.NONE
                else -> DeliveryMode.ANNOUNCE
            }

            return CronDeliveryPlan(
                mode = mode,
                channel = if (mode == DeliveryMode.ANNOUNCE) channel else null,
                to = to,
                accountId = deliveryAccountId,
                source = "delivery",
                requested = mode == DeliveryMode.ANNOUNCE
            )
        }

        // Legacy: check payload for delivery hints
        if (payload != null) {
            val legacyMode = when (payload.deliver) {
                true -> "explicit"
                false -> "off"
                else -> "auto"
            }
            // Aligned with TS: requested = (legacyMode == "explicit") || (legacyMode == "auto" && hasExplicitTarget)
            // TS checks hasExplicitTarget = Boolean(to), not channel
            val hasExplicitTarget = to != null
            val requested = legacyMode == "explicit" || (legacyMode == "auto" && hasExplicitTarget)

            return CronDeliveryPlan(
                mode = if (requested) DeliveryMode.ANNOUNCE else DeliveryMode.NONE,
                channel = channel,
                to = to,
                source = "payload",
                requested = requested
            )
        }

        return CronDeliveryPlan(mode = DeliveryMode.NONE)
    }

    // ── Failure destination (aligned with OpenClaw resolveFailureDestination) ──

    /**
     * Resolve failure destination for a cron job.
     * Layers global config as base, then overlays job-level failureDestination.
     * Returns null if no failure destination or if it's the same as the primary delivery.
     */
    fun resolveFailureDestination(
        job: CronJob,
        globalFailureMode: String? = null,
        globalFailureChannel: String? = null,
        globalFailureTo: String? = null,
        globalFailureAccountId: String? = null
    ): CronFailureDeliveryPlan? {
        // Start with global defaults
        var mode = normalizeFailureMode(globalFailureMode)
        var channel = normalizeChannel(globalFailureChannel)
        var to = normalizeTo(globalFailureTo)
        var accountId = normalizeAccountId(globalFailureAccountId)

        // Overlay job-level failure destination
        val jobFailure = job.delivery?.failureDestination
        if (jobFailure != null) {
            val jobMode = normalizeFailureMode(jobFailure.mode)
            val prevMode = mode

            val hasJobToField = jobFailure.to != null
            val jobToExplicitValue = hasJobToField && normalizeTo(jobFailure.to) != null

            if (jobFailure.channel != null) channel = normalizeChannel(jobFailure.channel)
            if (hasJobToField) to = normalizeTo(jobFailure.to)
            if (jobFailure.accountId != null) accountId = normalizeAccountId(jobFailure.accountId)

            if (jobMode != null) {
                // When mode changes between global and job level, clear inherited `to`
                // (URL semantics differ between announce and webhook)
                val globalMode = globalFailureMode ?: "announce"
                if (!jobToExplicitValue && globalMode != jobMode) {
                    to = null
                }
                mode = jobMode
            }
        }

        // No fields set at all
        if (mode == null && channel == null && to == null && accountId == null) return null

        // Default mode
        val resolvedMode = mode ?: "announce"

        // Webhook mode requires a URL
        if (resolvedMode == "webhook" && to == null) return null

        val result = CronFailureDeliveryPlan(
            mode = resolvedMode,
            channel = if (resolvedMode == "announce") (channel ?: "last") else null,
            to = to,
            accountId = accountId
        )

        // Check if failure destination is same as primary delivery
        val delivery = job.delivery
        if (delivery != null && isSameDeliveryTarget(delivery, result)) {
            return null
        }

        return result
    }

    /**
     * Compare failure plan against primary delivery target.
     * Aligned with OpenClaw isSameDeliveryTarget.
     */
    private fun isSameDeliveryTarget(
        delivery: CronDelivery,
        failurePlan: CronFailureDeliveryPlan
    ): Boolean {
        val primaryMode = deliveryModeToNormalized(delivery.mode)
        if (primaryMode == "none") return false

        if (failurePlan.mode == "webhook") {
            return primaryMode == "webhook" && normalizeTo(delivery.to) == failurePlan.to
        }

        val primaryChannel = normalizeChannel(delivery.channel) ?: "last"
        val failureChannel = failurePlan.channel ?: "last"

        return (
            failureChannel == primaryChannel &&
            normalizeTo(delivery.to) == failurePlan.to &&
            normalizeAccountId(delivery.accountId) == failurePlan.accountId
        )
    }

    // ── Best effort (aligned with OpenClaw) ──

    /**
     * Resolve whether to use best-effort delivery.
     * Checks delivery.bestEffort first, falls back to payload.bestEffortDeliver.
     */
    fun resolveCronDeliveryBestEffort(job: CronJob): Boolean {
        val delivery = job.delivery
        if (delivery?.bestEffort != null) return delivery.bestEffort

        val payload = job.payload as? CronPayload.AgentTurn
        return payload?.bestEffortDeliver ?: false
    }

    // ── Helpers ──

    fun formatResultMessage(jobId: String, jobDescription: String?, result: String): String {
        val desc = jobDescription ?: jobId
        return "[Cron: $desc]\n$result"
    }

    fun formatFailureMessage(
        jobId: String,
        jobDescription: String?,
        error: String,
        consecutiveErrors: Int
    ): String {
        val desc = jobDescription ?: jobId
        return "[Cron Failure: $desc]\nError: $error\nConsecutive failures: $consecutiveErrors"
    }
}
