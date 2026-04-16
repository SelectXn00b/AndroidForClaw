/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/subagent-registry.store.ts (disk persistence)
 * - ../openclaw/src/agents/subagent-registry-state.ts (save/load/snapshot)
 *
 * Hermes adaptation: JSON-based disk persistence for SubagentRunRecord registry.
 * Uses StoragePaths.agents/subagents/runs.json, version 2 format.
 */
package com.xiaomo.hermes.agent.subagent

import com.xiaomo.hermes.logging.Log
import com.xiaomo.hermes.workspace.StoragePaths
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Disk persistence for SubagentRegistry.
 * Saves/loads run records to/from {stateDir}/subagents/runs.json.
 * Aligned with OpenClaw subagent-registry.store.ts (version 2 format).
 */
class SubagentRegistryStore {

    companion object {
        private const val TAG = "SubagentRegistryStore"
        private const val VERSION = 2

        private val stateDir: File get() = File(StoragePaths.agents, "subagents")
        private val registryFile: File get() = File(stateDir, "runs.json")
    }

    /**
     * Save all runs to disk (best-effort).
     * Format: { version: 2, runs: { runId: { ...fields } } }
     */
    fun save(runs: Map<String, SubagentRunRecord>) {
        try {
            stateDir.mkdirs()
            val runsObj = JSONObject()
            for ((runId, record) in runs) {
                runsObj.put(runId, recordToJson(record))
            }
            val root = JSONObject().apply {
                put("version", VERSION)
                put("runs", runsObj)
            }
            registryFile.writeText(root.toString(2))
        } catch (e: Exception) {
            Log.w(TAG, "Failed to persist subagent runs to disk: ${e.message}")
        }
    }

    /**
     * Load runs from disk.
     * Returns empty map on failure or if file doesn't exist.
     */
    fun load(): Map<String, SubagentRunRecord> {
        if (!registryFile.exists()) return emptyMap()
        return try {
            val text = registryFile.readText()
            val root = JSONObject(text)
            val version = root.optInt("version", 1)
            val runsObj = root.optJSONObject("runs") ?: return emptyMap()
            val result = mutableMapOf<String, SubagentRunRecord>()
            for (key in runsObj.keys()) {
                val json = runsObj.getJSONObject(key)
                val record = jsonToRecord(json, version)
                if (record != null) {
                    result[key] = record
                }
            }
            Log.i(TAG, "Loaded ${result.size} subagent runs from disk (version $version)")
            result
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load subagent runs from disk: ${e.message}")
            emptyMap()
        }
    }

    private fun recordToJson(r: SubagentRunRecord): JSONObject {
        return JSONObject().apply {
            put("runId", r.runId)
            put("childSessionKey", r.childSessionKey)
            putOpt("controllerSessionKey", r.controllerSessionKey)
            put("requesterSessionKey", r.requesterSessionKey)
            put("requesterDisplayKey", r.requesterDisplayKey)
            put("task", r.task)
            put("label", r.label)
            putOpt("model", r.model)
            put("cleanup", r.cleanup)
            put("spawnMode", r.spawnMode.wireValue)
            putOpt("workspaceDir", r.workspaceDir)
            putOpt("runTimeoutSeconds", r.runTimeoutSeconds)
            put("createdAt", r.createdAt)
            putOpt("startedAt", r.startedAt)
            putOpt("sessionStartedAt", r.sessionStartedAt)
            put("accumulatedRuntimeMs", r.accumulatedRuntimeMs)
            putOpt("endedAt", r.endedAt)
            r.outcome?.let {
                put("outcome", JSONObject().apply {
                    put("status", it.status.wireValue)
                    putOpt("error", it.error)
                })
            }
            putOpt("archiveAtMs", r.archiveAtMs)
            putOpt("cleanupCompletedAt", r.cleanupCompletedAt)
            put("cleanupHandled", r.cleanupHandled)
            putOpt("suppressAnnounceReason", r.suppressAnnounceReason)
            put("expectsCompletionMessage", r.expectsCompletionMessage)
            put("announceRetryCount", r.announceRetryCount)
            putOpt("lastAnnounceRetryAt", r.lastAnnounceRetryAt)
            r.endedReason?.let { put("endedReason", it.wireValue) }
            put("wakeOnDescendantSettle", r.wakeOnDescendantSettle)
            putOpt("frozenResultText", r.frozenResultText)
            putOpt("frozenResultCapturedAt", r.frozenResultCapturedAt)
            putOpt("fallbackFrozenResultText", r.fallbackFrozenResultText)
            putOpt("fallbackFrozenResultCapturedAt", r.fallbackFrozenResultCapturedAt)
            putOpt("endedHookEmittedAt", r.endedHookEmittedAt)
            putOpt("attachmentsDir", r.attachmentsDir)
            putOpt("attachmentsRootDir", r.attachmentsRootDir)
            put("retainAttachmentsOnKeep", r.retainAttachmentsOnKeep)
            put("depth", r.depth)
        }
    }

    private fun jsonToRecord(j: JSONObject, version: Int): SubagentRunRecord? {
        return try {
            val outcomeJson = j.optJSONObject("outcome")
            val outcome = outcomeJson?.let {
                val statusStr = it.optString("status", "unknown")
                val status = SubagentRunStatus.entries.find { s -> s.wireValue == statusStr }
                    ?: SubagentRunStatus.UNKNOWN
                SubagentRunOutcome(status, it.optString("error", null))
            }

            val endedReasonStr = j.optString("endedReason", "")
            val endedReason = if (endedReasonStr.isNotBlank()) {
                SubagentLifecycleEndedReason.entries.find { it.wireValue == endedReasonStr }
            } else null

            val spawnModeStr = j.optString("spawnMode", "run")
            val spawnMode = if (spawnModeStr == "session") SpawnMode.SESSION else SpawnMode.RUN

            // V1→V2 migration: cleanup was boolean in V1 (true="delete", false="keep")
            // V2 uses string "delete"/"keep". Default to "keep" for V2+.
            val cleanupRaw = j.opt("cleanup")
            val cleanup = when {
                cleanupRaw is Boolean -> if (cleanupRaw) "delete" else "keep"
                cleanupRaw is String -> cleanupRaw
                version < 2 -> "delete" // V1 default was delete
                else -> "keep" // V2+ default is keep
            }

            SubagentRunRecord(
                runId = j.getString("runId"),
                childSessionKey = j.getString("childSessionKey"),
                controllerSessionKey = j.optString("controllerSessionKey", null),
                requesterSessionKey = j.getString("requesterSessionKey"),
                requesterDisplayKey = j.optString("requesterDisplayKey", ""),
                task = j.optString("task", ""),
                label = j.optString("label", ""),
                model = j.optString("model", null),
                cleanup = cleanup,
                spawnMode = spawnMode,
                workspaceDir = j.optString("workspaceDir", null),
                runTimeoutSeconds = if (j.has("runTimeoutSeconds")) j.optInt("runTimeoutSeconds") else null,
                createdAt = j.optLong("createdAt", 0L),
                depth = j.optInt("depth", 0),
            ).apply {
                startedAt = if (j.has("startedAt") && !j.isNull("startedAt")) j.optLong("startedAt") else null
                sessionStartedAt = if (j.has("sessionStartedAt") && !j.isNull("sessionStartedAt")) j.optLong("sessionStartedAt") else null
                accumulatedRuntimeMs = j.optLong("accumulatedRuntimeMs", 0L)
                endedAt = if (j.has("endedAt") && !j.isNull("endedAt")) j.optLong("endedAt") else null
                this.outcome = outcome
                archiveAtMs = if (j.has("archiveAtMs") && !j.isNull("archiveAtMs")) j.optLong("archiveAtMs") else null
                cleanupCompletedAt = if (j.has("cleanupCompletedAt") && !j.isNull("cleanupCompletedAt")) j.optLong("cleanupCompletedAt") else null
                cleanupHandled = j.optBoolean("cleanupHandled", false)
                suppressAnnounceReason = j.optString("suppressAnnounceReason", null)
                expectsCompletionMessage = j.optBoolean("expectsCompletionMessage", true)
                announceRetryCount = j.optInt("announceRetryCount", 0)
                lastAnnounceRetryAt = if (j.has("lastAnnounceRetryAt") && !j.isNull("lastAnnounceRetryAt")) j.optLong("lastAnnounceRetryAt") else null
                this.endedReason = endedReason
                wakeOnDescendantSettle = j.optBoolean("wakeOnDescendantSettle", false)
                frozenResultText = j.optString("frozenResultText", null)
                frozenResultCapturedAt = if (j.has("frozenResultCapturedAt") && !j.isNull("frozenResultCapturedAt")) j.optLong("frozenResultCapturedAt") else null
                fallbackFrozenResultText = j.optString("fallbackFrozenResultText", null)
                fallbackFrozenResultCapturedAt = if (j.has("fallbackFrozenResultCapturedAt") && !j.isNull("fallbackFrozenResultCapturedAt")) j.optLong("fallbackFrozenResultCapturedAt") else null
                endedHookEmittedAt = if (j.has("endedHookEmittedAt") && !j.isNull("endedHookEmittedAt")) j.optLong("endedHookEmittedAt") else null
                attachmentsDir = j.optString("attachmentsDir", null)
                attachmentsRootDir = j.optString("attachmentsRootDir", null)
                retainAttachmentsOnKeep = j.optBoolean("retainAttachmentsOnKeep", false)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to deserialize subagent run record: ${e.message}")
            null
        }
    }
}
