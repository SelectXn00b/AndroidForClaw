package com.xiaomo.androidforclaw.hermes.tools

import android.util.Log
import java.io.File
import java.security.MessageDigest
import java.util.UUID

/**
 * Checkpoint manager — save and restore file state for rollback.
 * Ported from checkpoint_manager.py
 */
object CheckpointManager {

    private const val TAG = "CheckpointManager"

    data class Checkpoint(
        val id: String,
        val name: String,
        val files: Map<String, String>,  // path -> content snapshot
        val timestamp: Long = System.currentTimeMillis(),
    )

    private val _checkpoints = mutableListOf<Checkpoint>()

    /**
     * Create a checkpoint by saving the current state of files.
     */
    fun createCheckpoint(
        name: String,
        filePaths: List<String>,
        workingDir: String = ".",
    ): Checkpoint {
        val files = mutableMapOf<String, String>()
        for (path in filePaths) {
            val file = if (File(path).isAbsolute) File(path) else File(workingDir, path)
            if (file.exists() && file.isFile) {
                try {
                    files[file.absolutePath] = file.readText(Charsets.UTF_8)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to snapshot $path: ${e.message}")
                }
            }
        }
        val checkpoint = Checkpoint(
            id = UUID.randomUUID().toString(),
            name = name,
            files = files,
        )
        _checkpoints.add(checkpoint)
        return checkpoint
    }

    /**
     * Restore files from a checkpoint.
     */
    fun restoreCheckpoint(checkpointId: String): Boolean {
        val checkpoint = _checkpoints.find { it.id == checkpointId } ?: return false
        for ((path, content) in checkpoint.files) {
            try {
                val file = File(path)
                file.parentFile?.mkdirs()
                file.writeText(content, Charsets.UTF_8)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to restore $path: ${e.message}")
                return false
            }
        }
        return true
    }

    /**
     * Get all checkpoints.
     */
    fun getCheckpoints(): List<Checkpoint> = _checkpoints.toList()

    /**
     * Get a checkpoint by ID.
     */
    fun getCheckpoint(id: String): Checkpoint? = _checkpoints.find { it.id == id }

    /**
     * Delete a checkpoint.
     */
    fun deleteCheckpoint(id: String): Boolean {
        return _checkpoints.removeAll { it.id == id }
    }

    /**
     * Clear all checkpoints.
     */
    fun clearCheckpoints() = _checkpoints.clear()



    /** Reset per-turn dedup.  Call at the start of each agent iteration. */
    fun newTurn(): Unit {
        // TODO: implement newTurn
    }
    /** Take a checkpoint if enabled and not already done this turn. */
    fun ensureCheckpoint(workingDir: String, reason: String = "auto"): Boolean {
        return false
    }
    /** List available checkpoints for a directory. */
    fun listCheckpoints(workingDir: String): List<Map<String, Any?>> {
        return emptyList()
    }
    /** Parse git --shortstat output into entry dict. */
    fun _parseShortstat(statLine: String, entry: Map<String, Any?>): Unit {
        // TODO: implement _parseShortstat
    }
    /** Show diff between a checkpoint and the current working tree. */
    fun diff(workingDir: String, commitHash: String): Map<String, Any?> {
        throw NotImplementedError("diff")
    }
    /** Restore files to a checkpoint state. */
    fun restore(workingDir: String, commitHash: String, filePath: String? = null): Map<String, Any?> {
        throw NotImplementedError("restore")
    }
    /** Resolve a file path to its working directory for checkpointing. */
    fun getWorkingDirForPath(filePath: String): String {
        return ""
    }
    /** Take a snapshot.  Returns True on success. */
    fun _take(workingDir: String, reason: String): Boolean {
        return false
    }
    /** Keep only the last max_snapshots commits via orphan reset. */
    fun _prune(shadowRepo: String, workingDir: String): Unit {
        // TODO: implement _prune
    }

}
