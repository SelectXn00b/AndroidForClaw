package com.xiaomo.androidforclaw.contextengine

/**
 * OpenClaw module: context-engine
 * Source: OpenClaw/src/context-engine/types.ts
 */

data class AssembleResult(
    val messages: List<Map<String, Any?>>,
    val estimatedTokens: Int,
    val systemPromptAddition: String? = null
)

data class CompactResultDetail(
    val summary: String? = null,
    val firstKeptEntryId: String? = null,
    val tokensBefore: Int,
    val tokensAfter: Int? = null
)

data class CompactResult(
    val ok: Boolean,
    val compacted: Boolean,
    val reason: String? = null,
    val result: CompactResultDetail? = null
)

data class IngestResult(val ingested: Boolean)
data class IngestBatchResult(val ingestedCount: Int)
data class BootstrapResult(val bootstrapped: Boolean, val importedMessages: Int? = null, val reason: String? = null)

data class ContextEngineInfo(
    val id: String,
    val name: String,
    val version: String? = null,
    val ownsCompaction: Boolean? = null
)

enum class SubagentEndReason { DELETED, COMPLETED, SWEPT, RELEASED }

interface ContextEngine {
    val info: ContextEngineInfo
    suspend fun bootstrap(): BootstrapResult
    suspend fun maintain(): Unit
    suspend fun ingest(message: Map<String, Any?>): IngestResult
    suspend fun ingestBatch(messages: List<Map<String, Any?>>): IngestBatchResult
    suspend fun afterTurn()
    suspend fun assemble(budget: Int): AssembleResult
    suspend fun compact(): CompactResult
    suspend fun dispose()
}
