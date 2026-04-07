package com.xiaomo.androidforclaw.agent.memory

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.xiaomo.androidforclaw.logging.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/memory/sqlite.ts
 *
 * SQLite storage layer for memory index.
 * Handles DB creation, file indexing, chunk storage, and file removal.
 */

// ---- SQLite Helper ----

internal class MemoryDbHelper(context: Context) : SQLiteOpenHelper(
    context, DB_NAME, null, DB_VERSION
) {
    companion object {
        private const val TAG = "MemorySqlite"
        private const val DB_NAME = "memory_index.db"
        private const val DB_VERSION = 1
    }

    var ftsCreated = true
        private set

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS files (
                path TEXT PRIMARY KEY,
                source TEXT NOT NULL,
                size INTEGER,
                mtime INTEGER,
                hash TEXT
            )
        """)
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS chunks (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                source TEXT NOT NULL,
                path TEXT NOT NULL,
                start_line INTEGER NOT NULL,
                end_line INTEGER NOT NULL,
                text TEXT NOT NULL,
                hash TEXT NOT NULL,
                embedding BLOB,
                indexed_at INTEGER NOT NULL
            )
        """)
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_chunks_path ON chunks(path)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_chunks_hash ON chunks(hash)")
        try {
            db.execSQL("CREATE VIRTUAL TABLE IF NOT EXISTS chunks_fts USING fts5(text, content='')")
        } catch (e: Exception) {
            Log.w(TAG, "FTS5 not available on this device, falling back to LIKE search", e)
            ftsCreated = false
        }
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS chunks_fts")
        db.execSQL("DROP TABLE IF EXISTS chunks")
        db.execSQL("DROP TABLE IF EXISTS files")
        onCreate(db)
    }
}

/**
 * SQLite-backed storage operations for indexing and removing files.
 *
 * Handles:
 * - Chunking files and storing chunks with embeddings
 * - Deduplicating unchanged files by hash
 * - FTS5 index maintenance
 * - File removal from index
 * - Sync: index a list of files and remove stale entries
 */
internal class MemorySqliteStore(
    internal val dbHelper: MemoryDbHelper,
    private val embeddingProvider: EmbeddingProvider?,
    internal var ftsAvailable: Boolean,
    private val mutex: Mutex
) {
    companion object {
        private const val TAG = "MemorySqlite"
    }

    /**
     * Index a single file: chunk it, compute embeddings, store in DB.
     * Skips unchanged files (by hash).
     */
    suspend fun indexFile(file: File, source: String = "memory") = mutex.withLock {
        withContext(Dispatchers.IO) {
            try {
                val path = file.absolutePath
                val content = file.readText()
                val fileHash = ChunkUtils.hashText(content)

                val db = dbHelper.writableDatabase

                // Check if file unchanged
                val cursor = db.rawQuery(
                    "SELECT hash FROM files WHERE path = ?", arrayOf(path)
                )
                val existingHash = if (cursor.moveToFirst()) cursor.getString(0) else null
                cursor.close()

                if (existingHash == fileHash) {
                    return@withContext // File unchanged
                }

                // Delete old chunks for this file
                if (ftsAvailable) {
                    db.execSQL("DELETE FROM chunks_fts WHERE rowid IN (SELECT rowid FROM chunks WHERE path = ?)", arrayOf(path))
                }
                db.delete("chunks", "path = ?", arrayOf(path))

                // Chunk the file
                val chunks = ChunkUtils.chunkMarkdown(content)
                if (chunks.isEmpty()) return@withContext

                // Get embeddings (batch)
                val embeddings = embeddingProvider?.embedBatch(chunks.map { it.text })

                // Insert chunks
                val stmt = db.compileStatement(
                    "INSERT INTO chunks (source, path, start_line, end_line, text, hash, embedding, indexed_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)"
                )

                db.beginTransaction()
                try {
                    for ((idx, chunk) in chunks.withIndex()) {
                        stmt.clearBindings()
                        stmt.bindString(1, source)
                        stmt.bindString(2, path)
                        stmt.bindLong(3, chunk.startLine.toLong())
                        stmt.bindLong(4, chunk.endLine.toLong())
                        stmt.bindString(5, chunk.text)
                        stmt.bindString(6, chunk.hash)

                        val embedding = embeddings?.getOrNull(idx)
                        if (embedding != null) {
                            stmt.bindBlob(7, floatArrayToBlob(embedding))
                        } else {
                            stmt.bindNull(7)
                        }
                        stmt.bindLong(8, System.currentTimeMillis())
                        stmt.executeInsert()
                    }

                    // Update FTS index
                    if (ftsAvailable) {
                        db.execSQL(
                            "INSERT INTO chunks_fts(rowid, text) SELECT rowid, text FROM chunks WHERE path = ?",
                            arrayOf(path)
                        )
                    }

                    // Upsert file record
                    db.execSQL(
                        "INSERT OR REPLACE INTO files (path, source, size, mtime, hash) VALUES (?, ?, ?, ?, ?)",
                        arrayOf(path, source, file.length(), file.lastModified(), fileHash)
                    )

                    db.setTransactionSuccessful()
                    Log.d(TAG, "Indexed $path: ${chunks.size} chunks")
                } finally {
                    db.endTransaction()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to index file: ${file.absolutePath}", e)
            }
        }
    }

    /**
     * Remove a file from the index.
     */
    suspend fun removeFile(path: String) = mutex.withLock {
        withContext(Dispatchers.IO) {
            val db = dbHelper.writableDatabase
            if (ftsAvailable) {
                db.execSQL("DELETE FROM chunks_fts WHERE rowid IN (SELECT rowid FROM chunks WHERE path = ?)", arrayOf(path))
            }
            db.delete("chunks", "path = ?", arrayOf(path))
            db.delete("files", "path = ?", arrayOf(path))
        }
    }

    /**
     * Sync: index all files in the given list, remove stale entries.
     */
    suspend fun sync(files: List<File>, source: String = "memory") {
        val currentPaths = files.map { it.absolutePath }.toSet()

        // Index each file
        for (file in files) {
            if (file.exists() && file.isFile) {
                indexFile(file, source)
            }
        }

        // Remove stale files from index
        withContext(Dispatchers.IO) {
            val db = dbHelper.readableDatabase
            val cursor = db.rawQuery("SELECT DISTINCT path FROM files WHERE source = ?", arrayOf(source))
            val indexedPaths = mutableListOf<String>()
            while (cursor.moveToNext()) {
                indexedPaths.add(cursor.getString(0))
            }
            cursor.close()

            for (path in indexedPaths) {
                if (path !in currentPaths) {
                    removeFile(path)
                }
            }
        }
    }
}
