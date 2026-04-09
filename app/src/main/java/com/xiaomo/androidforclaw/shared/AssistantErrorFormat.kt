package com.xiaomo.androidforclaw.shared

/**
 * OpenClaw module: shared
 * Source: OpenClaw/src/shared/assistant-error-format.ts
 *
 * Format error messages for display to the user or assistant.
 */

data class FormattedError(
    val summary: String,
    val detail: String? = null,
    val isRetryable: Boolean = false,
    val errorCode: String? = null
)

/** Classify and format an error for user display. */
fun formatAssistantError(error: Throwable): FormattedError {
    val message = error.message ?: error.toString()

    // Rate limit errors
    if (isRateLimitMessage(message)) {
        return FormattedError(
            summary = "Rate limited. Please wait a moment and try again.",
            detail = message,
            isRetryable = true,
            errorCode = "rate_limit"
        )
    }

    // Auth errors
    if (isAuthErrorMessage(message)) {
        return FormattedError(
            summary = "Authentication failed. Please check your API key.",
            detail = message,
            isRetryable = false,
            errorCode = "auth"
        )
    }

    // Overloaded
    if (isOverloadedMessage(message)) {
        return FormattedError(
            summary = "The service is currently overloaded. Please try again later.",
            detail = message,
            isRetryable = true,
            errorCode = "overloaded"
        )
    }

    // Context length
    if (isContextLengthMessage(message)) {
        return FormattedError(
            summary = "The conversation is too long. Try starting a new conversation.",
            detail = message,
            isRetryable = false,
            errorCode = "context_length"
        )
    }

    // Network errors
    if (isNetworkErrorMessage(message)) {
        return FormattedError(
            summary = "Network error. Please check your connection.",
            detail = message,
            isRetryable = true,
            errorCode = "network"
        )
    }

    // Generic error
    return FormattedError(
        summary = truncateErrorMessage(message, 200),
        detail = message,
        isRetryable = false
    )
}

/** Format an error into a single-line summary. */
fun formatErrorSummary(error: Throwable, maxLength: Int = 200): String {
    return truncateErrorMessage(error.message ?: error.toString(), maxLength)
}

// --- Error classification helpers ---

fun isRateLimitMessage(message: String): Boolean {
    val lower = message.lowercase()
    return "429" in lower || "rate limit" in lower || "rate_limit" in lower || "too many requests" in lower
}

fun isAuthErrorMessage(message: String): Boolean {
    val lower = message.lowercase()
    return "401" in lower || "403" in lower || "unauthorized" in lower ||
        "invalid.*api.?key" .toRegex().containsMatchIn(lower) ||
        "authentication" in lower
}

fun isOverloadedMessage(message: String): Boolean {
    val lower = message.lowercase()
    return "529" in lower || "overloaded" in lower || "503" in lower || "service unavailable" in lower
}

fun isContextLengthMessage(message: String): Boolean {
    val lower = message.lowercase()
    return "context length" in lower || "context_length" in lower ||
        "maximum.*tokens" .toRegex().containsMatchIn(lower) ||
        "too long" in lower
}

fun isNetworkErrorMessage(message: String): Boolean {
    val lower = message.lowercase()
    return "econnrefused" in lower || "econnreset" in lower || "etimedout" in lower ||
        "network" in lower || "dns" in lower || "socket" in lower ||
        "connection refused" in lower || "connection reset" in lower
}

private fun truncateErrorMessage(message: String, maxLength: Int): String {
    val singleLine = message.replace(Regex("\\s+"), " ").trim()
    if (singleLine.length <= maxLength) return singleLine
    return singleLine.take(maxLength - 3) + "..."
}
