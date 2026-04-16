package com.xiaomo.hermes.agent

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/identity-file.ts
 *
 * Load and parse the agent identity file (IDENTITY.md).
 */

import com.xiaomo.hermes.logging.Log
import java.io.File

object IdentityFile {
    private const val TAG = "IdentityFile"
    private const val IDENTITY_FILENAME = "IDENTITY.md"

    /**
     * Load identity from workspace IDENTITY.md.
     * Returns the raw content, or null if not found.
     */
    fun load(workspaceDir: File): String? {
        val file = File(workspaceDir, IDENTITY_FILENAME)
        return try {
            if (file.exists()) {
                val content = file.readText().trim()
                if (content.isNotEmpty()) {
                    Log.d(TAG, "Loaded identity file: ${file.absolutePath} (${content.length} chars)")
                    content
                } else null
            } else null
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load identity file: ${e.message}")
            null
        }
    }

    /**
     * Extract agent name from identity content.
     * Looks for "# <name>" or "name: <name>" patterns.
     */
    fun extractName(content: String): String? {
        // Try heading: "# AgentName"
        val headingMatch = Regex("^#\\s+(.+)$", RegexOption.MULTILINE).find(content)
        if (headingMatch != null) return headingMatch.groupValues[1].trim()

        // Try YAML-like: "name: AgentName"
        val yamlMatch = Regex("^name:\\s*(.+)$", RegexOption.MULTILINE).find(content)
        if (yamlMatch != null) return yamlMatch.groupValues[1].trim()

        return null
    }
}
