package com.xiaomo.hermes.agent.memory

import android.content.SharedPreferences
import com.xiaomo.hermes.logging.Log
import com.xiaomo.hermes.providers.UnifiedLLMProvider
import com.xiaomo.hermes.providers.llm.Message
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Memory Flush Manager — Pre-compaction memory persistence.
 *
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/prompt-composition-scenarios.ts (Pre-compaction memory flush turn)
 * - ../openclaw/src/agents/pi-tools.read.ts (memory flush tool restrictions)
 *
 * Hermes adaptation: stores in-memory ConcurrentHashMap (context-free)
 * - Daily format: memory/YYYY-MM-DD.md
 *
 * Threshold logic from OpenClaw:
 * - contextWindowTokens - reserveTokensFloor - softThresholdTokens = flush threshold
 * - If promptTokens + estimated newTokens >= threshold → trigger flush
 */
class MemoryFlushManager {
    companion object {
        private const val TAG = "MemoryFlushManager"

        // Threshold defaults (aligned with OpenClaw memoryFlush defaults)
        // reserveTokensFloor: reserve tokens for compaction summary + system prompt
        private const val DEFAULT_RESERVE_TOKENS_FLOOR = 8000
        // softThresholdTokens: buffer below hard limit
        private const val DEFAULT_SOFT_THRESHOLD_TOKENS = 12000

        // Default memory flush prompt (aligned with OpenClaw memoryFlush.prompt)
        private const val DEFAULT_MEMORY_FLUSH_PROMPT = """
You are a memory extraction assistant. Review the conversation history and extract durable memories — things worth remembering across sessions.

Focus on:
- User preferences, opinions, and habits
- Decisions made and their reasoning
- Lessons learned
- Important context about the user or their projects
- Things the user explicitly asked to remember

Do NOT extract:
- Routine tool outputs
- Technical details that will be re-read from files
- Temporary conversation state

Format each memory as a bullet point. Be concise.
"""

        // Default memory flush system prompt (aligned with OpenClaw memoryFlush systemPrompt)
        private const val DEFAULT_MEMORY_FLUSH_SYSTEM_PROMPT = """
Pre-compaction memory flush. Store durable memories only in memory/2026-04-05.md (create memory/ if needed). Treat workspace bootstrap/reference files such as MEMORY.md, SOUL.md, TOOLS.md, and AGENTS.md as read-only during this flush; never overwrite, replace, or edit them. If memory/2026-04-05.md already exists, APPEND new content only and do not overwrite existing entries. Do NOT create timestamped variant files (e.g., 2026-04-05-HHMM.md); always use the canonical 2026-04-05.md filename. If nothing to store, reply with NO_REPLY.
"""

        // In-memory daily memories store (thread-safe)
        private val dailyMemories = ConcurrentHashMap<String, String>()
    }

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    /**
     * Memory flush settings (aligned with OpenClaw memoryFlushSettings).
     */
    data class MemoryFlushSettings(
        val reserveTokensFloor: Int = DEFAULT_RESERVE_TOKENS_FLOOR,
        val softThresholdTokens: Int = DEFAULT_SOFT_THRESHOLD_TOKENS,
        val prompt: String = DEFAULT_MEMORY_FLUSH_PROMPT,
        val systemPrompt: String = DEFAULT_MEMORY_FLUSH_SYSTEM_PROMPT
    )

    /**
     * Resolve memory flush settings from config.
     * Aligned with OpenClaw resolveMemoryFlushSettings().
     */
    fun resolveSettings(): MemoryFlushSettings {
        return MemoryFlushSettings()
    }

    /**
     * Check if memory flush should run.
     * Aligned with OpenClaw shouldRunMemoryFlush().
     *
     * @param tokenCount Current token count in context
     * @param contextWindowTokens Max context window size
     * @return true if flush should be triggered
     */
    fun shouldRunFlush(
        tokenCount: Int,
        contextWindowTokens: Int
    ): Boolean {
        val settings = resolveSettings()
        val threshold = contextWindowTokens - settings.reserveTokensFloor - settings.softThresholdTokens
        val shouldFlush = tokenCount >= threshold
        Log.d(TAG, "Memory flush check: tokenCount=$tokenCount, threshold=$threshold, contextWindow=$contextWindowTokens, shouldFlush=$shouldFlush")
        return shouldFlush
    }

    /**
     * Run memory flush — execute a lightweight LLM call to extract memories.
     * Aligned with OpenClaw runMemoryFlushIfNeeded().
     *
     * @param llmProvider LLM provider for the flush call
     * @param modelRef Model reference to use
     * @param messages Current conversation messages
     * @return Flush result (memories extracted, or null if nothing)
     */
    suspend fun runFlush(
        llmProvider: UnifiedLLMProvider,
        modelRef: String,
        messages: List<Message>
    ): MemoryFlushResult {
        val settings = resolveSettings()
        val today = dateFormat.format(Date())
        val prefsKey = "memory_$today"

        // Build flush prompt with existing memories context
        val existingMemories = dailyMemories[prefsKey] ?: ""
        val existingContext = if (existingMemories.isNotBlank()) {
            "\n\nExisting memories for today ($today):\n$existingMemories"
        } else {
            ""
        }

        // Build the flush messages (include conversation history for context)
        val flushMessages = mutableListOf<Message>()
        flushMessages.add(Message(role = "system", content = settings.systemPrompt))

        // Include last N messages for context (avoid re-reading entire conversation)
        val recentMessages = messages.takeLast(20)
        flushMessages.addAll(recentMessages)

        // Add the flush prompt
        flushMessages.add(Message(role = "user", content = settings.prompt + existingContext))

        return try {
            Log.d(TAG, "Running memory flush for $today...")

            // Execute the flush LLM call (non-streaming, no tools)
            val response = llmProvider.chatWithTools(
                messages = flushMessages,
                modelRef = modelRef,
                tools = null // Memory flush doesn't use tools
            )

            val memories = response?.content?.trim()

            if (memories.isNullOrBlank() || memories.equals("NO_REPLY", ignoreCase = true)) {
                Log.d(TAG, "Memory flush: nothing to store")
                return MemoryFlushResult(
                    success = true,
                    memoriesExtracted = false,
                    storedDate = today
                )
            }

            // Store memories in dailyMemories (thread-safe in-memory store)
            val updated = if (existingMemories.isNotBlank()) {
                "$existingMemories\n\n---\n\n$memories"
            } else {
                memories
            }
            dailyMemories[prefsKey] = updated

            Log.d(TAG, "Memory flush: stored ${memories.length} chars for $today")
            MemoryFlushResult(
                success = true,
                memoriesExtracted = true,
                storedDate = today,
                memoriesContent = memories
            )
        } catch (e: Exception) {
            Log.e(TAG, "Memory flush failed: ${e.message}")
            MemoryFlushResult(
                success = false,
                error = e.message ?: "Unknown error"
            )
        }
    }

    /**
     * Get stored memories for a specific date.
     */
    fun getMemories(date: String): String {
        return dailyMemories["memory_$date"] ?: ""
    }

    /**
     * Get today's memories.
     */
    fun getTodayMemories(): String {
        return getMemories(dateFormat.format(Date()))
    }

    /**
     * Memory flush result.
     */
    data class MemoryFlushResult(
        val success: Boolean,
        val memoriesExtracted: Boolean = false,
        val storedDate: String? = null,
        val memoriesContent: String? = null,
        val error: String? = null
    )
}
