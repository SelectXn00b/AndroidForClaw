package com.xiaomo.hermes.agent.context

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/pi-embedded-helpers.ts
 *
 * Shared helper utilities for the embedded agent context.
 */

/**
 * Escape special characters for XML content.
 */
internal fun escapeXml(str: String): String {
    return str
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
}
