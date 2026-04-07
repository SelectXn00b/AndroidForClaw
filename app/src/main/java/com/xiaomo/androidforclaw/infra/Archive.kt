package com.xiaomo.androidforclaw.infra

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * OpenClaw module: infra
 * Source: OpenClaw/src/infra/archive.ts
 *
 * ZIP extraction with security checks (path traversal, size budgets).
 * Aligned with TS extractZip() / extractZipEntry().
 */

data class ArchiveExtractOptions(
    /** Maximum number of entries allowed. */
    val maxEntries: Int = 10_000,
    /** Maximum total uncompressed size in bytes. */
    val maxTotalBytes: Long = 500 * 1024 * 1024, // 500 MB
    /** Strip this number of leading path components (like tar --strip-components). */
    val stripComponents: Int = 0,
    /** If set, only extract entries under this prefix. */
    val pathPrefix: String? = null
)

class ArchiveSecurityException(message: String) : SecurityException(message)

object Archive {

    /**
     * Extract a ZIP file to the destination directory with security checks.
     * Aligned with TS extractZip().
     */
    suspend fun extractZip(
        zipFile: File,
        destDir: File,
        options: ArchiveExtractOptions = ArchiveExtractOptions()
    ): List<File> = withContext(Dispatchers.IO) {
        destDir.mkdirs()
        val canonicalDest = destDir.canonicalPath
        val extracted = mutableListOf<File>()
        var entryCount = 0
        var totalBytes = 0L

        zipFile.inputStream().buffered().use { fis ->
            extractZipStream(fis, canonicalDest, destDir, options, extracted, { entryCount++ }, { totalBytes += it })
        }
        extracted
    }

    /**
     * Extract a ZIP input stream.
     */
    suspend fun extractZipStream(
        inputStream: InputStream,
        destDir: File,
        options: ArchiveExtractOptions = ArchiveExtractOptions()
    ): List<File> = withContext(Dispatchers.IO) {
        destDir.mkdirs()
        val canonicalDest = destDir.canonicalPath
        val extracted = mutableListOf<File>()
        var entryCount = 0
        var totalBytes = 0L

        extractZipStream(inputStream, canonicalDest, destDir, options, extracted, { entryCount++ }, { totalBytes += it })
        extracted
    }

    private fun extractZipStream(
        inputStream: InputStream,
        canonicalDest: String,
        destDir: File,
        options: ArchiveExtractOptions,
        extracted: MutableList<File>,
        onEntry: () -> Unit,
        onBytes: (Long) -> Unit
    ) {
        var entryCount = 0
        var totalBytes = 0L

        ZipInputStream(inputStream).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                entryCount++
                if (entryCount > options.maxEntries) {
                    throw ArchiveSecurityException(
                        "Archive exceeds maximum entry count: ${options.maxEntries}"
                    )
                }

                val entryName = normalizeArchiveEntryPath(entry.name, options.stripComponents)
                if (entryName != null) {
                    // Path prefix filter
                    if (options.pathPrefix != null && !entryName.startsWith(options.pathPrefix)) {
                        zis.closeEntry()
                        entry = zis.nextEntry
                        continue
                    }

                    val outFile = File(destDir, entryName)
                    // Path traversal check
                    if (!outFile.canonicalPath.startsWith(canonicalDest)) {
                        throw ArchiveSecurityException(
                            "Archive entry escapes destination: ${entry.name}"
                        )
                    }

                    if (entry.isDirectory) {
                        outFile.mkdirs()
                    } else {
                        outFile.parentFile?.mkdirs()
                        outFile.outputStream().buffered().use { out ->
                            val buf = ByteArray(8192)
                            var len: Int
                            while (zis.read(buf).also { len = it } != -1) {
                                totalBytes += len
                                if (totalBytes > options.maxTotalBytes) {
                                    throw ArchiveSecurityException(
                                        "Archive exceeds maximum total size: ${options.maxTotalBytes} bytes"
                                    )
                                }
                                out.write(buf, 0, len)
                            }
                        }
                        extracted.add(outFile)
                    }
                }

                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }

    /**
     * Normalize an archive entry path, stripping leading components.
     * Returns null if the entry should be skipped.
     * Aligned with TS normalizeArchiveEntryPath() + validateArchiveEntryPath().
     */
    internal fun normalizeArchiveEntryPath(rawPath: String, stripComponents: Int = 0): String? {
        // Reject absolute paths and paths with ..
        val normalized = rawPath.replace("\\", "/").trimStart('/')
        if (normalized.contains("..")) return null
        if (normalized.isBlank()) return null

        // Strip leading components
        if (stripComponents > 0) {
            val parts = normalized.split("/")
            if (parts.size <= stripComponents) return null
            return parts.drop(stripComponents).joinToString("/")
        }
        return normalized
    }
}
