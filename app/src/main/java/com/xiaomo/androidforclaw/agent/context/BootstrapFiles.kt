package com.xiaomo.androidforclaw.agent.context

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/bootstrap-files.ts
 * - ../openclaw/src/agents/bootstrap-budget.ts
 *
 * Load and budget-trim workspace bootstrap files.
 */

import android.content.Context
import com.xiaomo.androidforclaw.logging.Log
import com.xiaomo.androidforclaw.config.ConfigLoader
import com.xiaomo.androidforclaw.shared.ReasoningTagMode
import com.xiaomo.androidforclaw.shared.stripReasoningTagsFromText
import java.io.File

// Bootstrap file list (complete OpenClaw 9 files)
// Bootstrap file load order -- aligned with OpenClaw loadWorkspaceBootstrapFiles()
// OpenClaw order: AGENTS -> SOUL -> TOOLS -> IDENTITY -> USER -> HEARTBEAT -> BOOTSTRAP -> memory/*
internal val BOOTSTRAP_FILES = listOf(
    "AGENTS.md",
    "SOUL.md",
    "TOOLS.md",
    "IDENTITY.md",
    "USER.md",
    "HEARTBEAT.md",
    "BOOTSTRAP.md",
    "MEMORY.md"
)

// Bootstrap file budget (aligned with OpenClaw bootstrap-budget.ts)
internal const val DEFAULT_BOOTSTRAP_MAX_CHARS = 20_000
internal const val DEFAULT_BOOTSTRAP_TOTAL_MAX_CHARS = 150_000
internal const val MIN_BOOTSTRAP_FILE_BUDGET_CHARS = 64
internal const val BOOTSTRAP_TAIL_RATIO = 0.2

private const val TAG = "BootstrapFiles"

/**
 * Load Bootstrap files with budget control
 * Aligned with OpenClaw's buildBootstrapContextFiles (bootstrap-budget.ts)
 *
 * Priority: workspace > assets (bundled)
 * Budget: per-file max + total max (prevents MEMORY.md from blowing context)
 */
internal fun loadBootstrapFiles(
    context: Context,
    workspaceDir: File,
    configLoader: ConfigLoader?,
    channelContext: ChannelContext? = null
): String {
    val config = try { configLoader?.loadOpenClawConfig() } catch (_: Exception) { null }
    val perFileMaxChars = config?.agents?.defaults?.bootstrapMaxChars ?: DEFAULT_BOOTSTRAP_MAX_CHARS
    val totalMaxChars = maxOf(perFileMaxChars, config?.agents?.defaults?.bootstrapTotalMaxChars ?: DEFAULT_BOOTSTRAP_TOTAL_MAX_CHARS)

    var remainingTotalChars = totalMaxChars
    val loadedFiles = mutableListOf<Triple<String, String, Boolean>>()
    var hasSoulFile = false

    for (filename in BOOTSTRAP_FILES) {
        // Code-level MEMORY.md guard: skip in shared contexts (group chats)
        // Supplements the prompt-level instruction in SOUL.md
        if (filename == "MEMORY.md" && !ContextSecurityGuard.shouldLoadMemory(channelContext)) {
            continue
        }

        if (remainingTotalChars <= 0) {
            Log.w(TAG, "Bootstrap total budget exhausted, skipping: $filename")
            break
        }
        if (remainingTotalChars < MIN_BOOTSTRAP_FILE_BUDGET_CHARS) {
            Log.w(TAG, "Remaining bootstrap budget ($remainingTotalChars chars) < minimum ($MIN_BOOTSTRAP_FILE_BUDGET_CHARS), skipping: $filename")
            break
        }

        try {
            // 1. First try loading from workspace (user-defined)
            val workspaceFile = File(workspaceDir, filename)
            val rawContent = if (workspaceFile.exists()) {
                Log.d(TAG, "Loaded bootstrap from workspace: $filename")
                workspaceFile.readText()
            } else {
                // 2. Load from assets (bundled)
                try {
                    val inputStream = context.assets.open("bootstrap/$filename")
                    val content = inputStream.bufferedReader().use { it.readText() }
                    Log.d(TAG, "Loaded bootstrap from assets: $filename (${content.length} chars)")
                    content
                } catch (e: Exception) {
                    Log.w(TAG, "Bootstrap file not found: $filename")
                    null
                }
            }

            if (rawContent != null && rawContent.isNotEmpty()) {
                // Sanitize reasoning tags from workspace-editable files
                val sanitizedContent = stripReasoningTagsFromText(rawContent, ReasoningTagMode.STRICT)

                // Apply per-file budget (aligned with OpenClaw trimBootstrapContent)
                val fileMaxChars = maxOf(1, minOf(perFileMaxChars, remainingTotalChars))
                val (content, truncated) = trimBootstrapContent(sanitizedContent, fileMaxChars)

                if (truncated) {
                    Log.w(TAG, "Bootstrap file truncated: $filename (${sanitizedContent.length} -> ${content.length} chars, max=$fileMaxChars)")
                }

                loadedFiles.add(Triple(filename, content, truncated))
                remainingTotalChars = maxOf(0, remainingTotalChars - content.length)

                if (filename.equals("SOUL.md", ignoreCase = true)) {
                    hasSoulFile = true
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load $filename", e)
        }
    }

    if (loadedFiles.isEmpty()) {
        return ""
    }

    // Build Project Context section (aligned with OpenClaw)
    val parts = mutableListOf<String>()
    parts.add("# Project Context")
    parts.add("")
    parts.add("The following project context files have been loaded:")

    if (hasSoulFile) {
        parts.add("If SOUL.md is present, embody its persona and tone. Avoid stiff, generic replies; follow its guidance unless higher-priority instructions override it.")
    }
    parts.add("")

    // Each file starts with "## full/path" (aligned with OpenClaw: uses full workspace path)
    for ((filename, content, truncated) in loadedFiles) {
        val fullPath = "${workspaceDir.absolutePath}/$filename"
        parts.add("## $fullPath")
        if (truncated) {
            parts.add("_This file was truncated to fit the context budget._")
        }
        parts.add("")
        parts.add(content)
        parts.add("")
    }

    return parts.joinToString("\n")
}

/**
 * Trim bootstrap content to fit budget
 * Aligned with OpenClaw's trimBootstrapContent:
 * - Keep head (80%) + tail (20%) when truncating
 * - Insert truncation marker in the middle
 *
 * @return Pair(content, wasTruncated)
 */
internal fun trimBootstrapContent(content: String, maxChars: Int): Pair<String, Boolean> {
    if (content.length <= maxChars) {
        return content to false
    }

    val tailChars = (maxChars * BOOTSTRAP_TAIL_RATIO).toInt()
    val headChars = maxChars - tailChars - 50  // Reserve space for truncation marker

    if (headChars <= 0 || tailChars <= 0) {
        return content.take(maxChars) to true
    }

    val head = content.take(headChars)
    val tail = content.takeLast(tailChars)
    val omitted = content.length - headChars - tailChars
    val marker = "\n\n... ($omitted chars omitted) ...\n\n"

    return (head + marker + tail) to true
}
