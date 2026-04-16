package com.xiaomo.hermes.infra

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * OpenClaw module: infra
 * Source: OpenClaw/src/infra/json-file.ts + json-files.ts
 *
 * Typed JSON file reader/writer with atomic-write support.
 * Aligned with OpenClaw's loadJsonFile / saveJsonFile / writeJsonAtomic / writeTextAtomic.
 */
object JsonFile {

    // --- Synchronous API (aligned with json-file.ts) ---

    /** Load JSON string from file, returns null if file doesn't exist or read fails. */
    fun loadJsonFile(file: File): String? {
        return try {
            if (!file.exists()) null else file.readText(Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    /** Alias for backward compat. */
    fun readJsonString(file: File): String? = loadJsonFile(file)

    /**
     * Save JSON string to file atomically:
     * write to temp file -> fsync -> rename into place -> cleanup.
     * Aligned with OpenClaw saveJsonFile().
     */
    fun saveJsonFile(file: File, json: String) {
        val targetPath = resolveWriteTarget(file)
        val tmpFile = File(targetPath.parent, "${targetPath.name}.${UUID.randomUUID()}.tmp")
        targetPath.parentFile?.mkdirs()
        try {
            writeTempFile(tmpFile, if (json.endsWith("\n")) json else "$json\n")
            renameWithFallback(tmpFile, targetPath)
        } finally {
            tmpFile.delete() // cleanup if rename didn't happen
        }
    }

    /** Alias for backward compat. */
    fun writeJsonString(file: File, json: String) = saveJsonFile(file, json)

    fun exists(file: File): Boolean = file.exists()

    fun delete(file: File): Boolean = file.delete()

    // --- Async API (aligned with json-files.ts) ---

    /** Async version of saveJsonFile. */
    suspend fun writeJsonAtomic(file: File, json: String) = withContext(Dispatchers.IO) {
        saveJsonFile(file, json)
    }

    /** Async atomic text write. */
    suspend fun writeTextAtomic(file: File, content: String) = withContext(Dispatchers.IO) {
        saveJsonFile(file, content)
    }

    /** Async read. */
    suspend fun readJsonFileAsync(file: File): String? = withContext(Dispatchers.IO) {
        loadJsonFile(file)
    }

    // --- Internal helpers ---

    /** Resolve symlinks to find the actual write target. */
    private fun resolveWriteTarget(file: File): File {
        var current = file
        val visited = mutableSetOf<String>()
        while (true) {
            val canonical = current.canonicalPath
            if (!current.exists()) return current
            if (!isSymlink(current)) return current
            if (!visited.add(canonical)) {
                throw java.io.IOException("Too many symlink levels while resolving $file")
            }
            current = current.canonicalFile
        }
    }

    private fun isSymlink(file: File): Boolean {
        return try {
            file.absolutePath != file.canonicalPath
        } catch (_: Exception) {
            false
        }
    }

    /** Write content to temp file with fsync. */
    private fun writeTempFile(tmpFile: File, content: String) {
        FileOutputStream(tmpFile).use { fos ->
            fos.write(content.toByteArray(Charsets.UTF_8))
            fos.fd.sync()
        }
    }

    /** Atomic rename with copy fallback (like TS renameJsonFileWithFallback). */
    private fun renameWithFallback(tmpFile: File, target: File) {
        if (tmpFile.renameTo(target)) return
        // Fallback: copy + delete (rename can fail across filesystems)
        tmpFile.copyTo(target, overwrite = true)
        tmpFile.delete()
    }
}

/**
 * Async lock for serializing write operations.
 * Aligned with OpenClaw createAsyncLock().
 */
class AsyncLock {
    private val mutex = Mutex()

    suspend fun <T> withLock(block: suspend () -> T): T = mutex.withLock { block() }
}
