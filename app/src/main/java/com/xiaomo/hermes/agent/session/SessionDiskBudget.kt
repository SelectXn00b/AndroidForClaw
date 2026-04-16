package com.xiaomo.hermes.agent.session

import com.xiaomo.hermes.logging.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/config/sessions/disk-budget.ts
 *
 * Session disk budget enforcement — measures total disk usage of the sessions
 * directory and sweeps oldest entries when the budget is exceeded.
 */
object SessionDiskBudget {

    private const val TAG = "SessionDiskBudget"

    data class DiskBudgetResult(
        val totalBytesBefore: Long,
        val totalBytesAfter: Long,
        val deletedCount: Int,
        val freedBytes: Long
    )

    /**
     * Enforce the disk budget for the sessions directory.
     *
     * Measures total bytes across all files. If total exceeds [maxDiskBytes],
     * sweeps to bring usage down to [highWaterBytes]:
     * 1. Remove orphaned files (no matching index entry)
     * 2. Remove oldest session entries by updatedAt
     *
     * @param sessionsDir The sessions directory
     * @param sessionIndex The in-memory session index (will be mutated)
     * @param activeKey The active session key to preserve
     * @param maxDiskBytes Maximum allowed disk usage
     * @param highWaterBytes Target usage after sweep (default: 80% of max)
     * @return Sweep result, or null if no sweep was needed
     */
    suspend fun enforceSessionDiskBudget(
        sessionsDir: File,
        sessionIndex: MutableMap<String, SessionMetadata>,
        activeKey: String?,
        maxDiskBytes: Long,
        highWaterBytes: Long = (maxDiskBytes * 0.8).toLong()
    ): DiskBudgetResult? = withContext(Dispatchers.IO) {
        val totalBefore = measureDirSize(sessionsDir)
        if (totalBefore <= maxDiskBytes) return@withContext null

        Log.w(TAG, "Session disk usage ${totalBefore / 1024}KB exceeds budget ${maxDiskBytes / 1024}KB, sweeping...")

        var freedBytes = 0L
        var deletedCount = 0

        // Phase 1: Remove orphaned JSONL files (no matching index entry)
        val knownIds = sessionIndex.values.map { it.sessionId }.toSet()
        val allFiles = sessionsDir.listFiles() ?: emptyArray()
        for (file in allFiles) {
            if (!file.name.endsWith(".jsonl")) continue
            val fileId = file.nameWithoutExtension
            if (fileId !in knownIds) {
                val size = file.length()
                file.delete()
                freedBytes += size
                deletedCount++
                Log.d(TAG, "Deleted orphaned file: ${file.name} (${size / 1024}KB)")
            }
        }

        // Check if we're now under the high water mark
        val afterOrphanCleanup = totalBefore - freedBytes
        if (afterOrphanCleanup <= highWaterBytes) {
            Log.i(TAG, "Disk budget satisfied after orphan cleanup: freed ${freedBytes / 1024}KB")
            return@withContext DiskBudgetResult(totalBefore, afterOrphanCleanup, deletedCount, freedBytes)
        }

        // Phase 2: Remove oldest session entries
        val sorted = sessionIndex.entries
            .filter { it.key != activeKey }
            .sortedBy { it.value.updatedAt }

        var currentSize = afterOrphanCleanup
        for (entry in sorted) {
            if (currentSize <= highWaterBytes) break

            val sessionFile = File(sessionsDir, "${entry.value.sessionId}.jsonl")
            val fileSize = if (sessionFile.exists()) sessionFile.length() else 0L

            sessionIndex.remove(entry.key)
            if (sessionFile.exists()) sessionFile.delete()

            currentSize -= fileSize
            freedBytes += fileSize
            deletedCount++
            Log.d(TAG, "Removed session ${entry.key} (${fileSize / 1024}KB)")
        }

        val totalAfter = measureDirSize(sessionsDir)
        Log.i(TAG, "Disk budget sweep complete: freed ${freedBytes / 1024}KB, " +
            "deleted $deletedCount entries, ${totalAfter / 1024}KB remaining")

        DiskBudgetResult(totalBefore, totalAfter, deletedCount, freedBytes)
    }

    /**
     * Measure total size of all files in a directory (non-recursive).
     */
    private fun measureDirSize(dir: File): Long {
        if (!dir.exists()) return 0L
        return dir.listFiles()?.sumOf { it.length() } ?: 0L
    }
}
