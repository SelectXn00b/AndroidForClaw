package com.xiaomo.androidforclaw.pairing

/**
 * OpenClaw module: pairing
 * Source: OpenClaw/src/pairing/setup-code.ts
 *
 * Formats pairing codes for display and QR generation.
 */
object SetupCode {

    fun formatForDisplay(code: String, groupSize: Int = 3): String {
        return code.chunked(groupSize).joinToString("-")
    }

    fun stripFormatting(displayCode: String): String {
        return displayCode.replace(Regex("[^0-9]"), "")
    }

    fun generateSetupUri(code: String, host: String = "openclaw"): String {
        return "$host://pair?code=$code"
    }

    fun parseSetupUri(uri: String): String? {
        val match = Regex("://pair\\?code=([0-9]+)").find(uri)
        return match?.groupValues?.get(1)
    }
}
