package com.xiaomo.hermes.agent.session

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SessionStoreMaintenanceTest {

    @Rule
    @JvmField
    val tempDir = TemporaryFolder()

    // ===== pruneStaleEntries =====

    @Test
    fun `empty index returns 0`() {
        val index = mutableMapOf<String, SessionMetadata>()
        val result = SessionStoreMaintenance.pruneStaleEntries(index, tempDir.root)
        assertEquals(0, result)
    }

    @Test
    fun `stale entries are pruned`() {
        val sessionsDir = tempDir.newFolder("sessions")
        val staleTime = System.currentTimeMillis() - 31L * 24 * 60 * 60 * 1000
        val index = mutableMapOf(
            "stale" to SessionMetadata("sess-stale", staleTime, "sess-stale.jsonl"),
            "fresh" to SessionMetadata("sess-fresh", System.currentTimeMillis(), "sess-fresh.jsonl")
        )
        // Create session files
        File(sessionsDir, "sess-stale.jsonl").writeText("data")
        File(sessionsDir, "sess-fresh.jsonl").writeText("data")

        val pruned = SessionStoreMaintenance.pruneStaleEntries(index, sessionsDir)
        assertEquals(1, pruned)
        assertFalse(index.containsKey("stale"))
        assertTrue(index.containsKey("fresh"))
        assertFalse(File(sessionsDir, "sess-stale.jsonl").exists())
        assertTrue(File(sessionsDir, "sess-fresh.jsonl").exists())
    }

    @Test
    fun `active session is not pruned even if stale`() {
        val sessionsDir = tempDir.newFolder("sessions2")
        val staleTime = System.currentTimeMillis() - 31L * 24 * 60 * 60 * 1000
        val index = mutableMapOf(
            "active" to SessionMetadata("sess-active", staleTime, "sess-active.jsonl")
        )
        File(sessionsDir, "sess-active.jsonl").writeText("data")

        val pruned = SessionStoreMaintenance.pruneStaleEntries(index, sessionsDir, activeSessionKey = "active")
        assertEquals(0, pruned)
        assertTrue(index.containsKey("active"))
    }

    // ===== capEntryCount =====

    @Test
    fun `under cap does nothing`() {
        val index = mutableMapOf(
            "a" to SessionMetadata("sa", System.currentTimeMillis(), "sa.jsonl")
        )
        val removed = SessionStoreMaintenance.capEntryCount(index, tempDir.root, maxEntries = 10)
        assertEquals(0, removed)
        assertEquals(1, index.size)
    }

    @Test
    fun `over cap removes oldest entries`() {
        val sessionsDir = tempDir.newFolder("cap-sessions")
        val now = System.currentTimeMillis()
        val index = mutableMapOf(
            "oldest" to SessionMetadata("s1", now - 3000, "s1.jsonl"),
            "middle" to SessionMetadata("s2", now - 2000, "s2.jsonl"),
            "newest" to SessionMetadata("s3", now - 1000, "s3.jsonl")
        )
        for (m in index.values) {
            File(sessionsDir, "${m.sessionId}.jsonl").writeText("data")
        }

        val removed = SessionStoreMaintenance.capEntryCount(index, sessionsDir, maxEntries = 2)
        assertEquals(1, removed)
        assertEquals(2, index.size)
        assertFalse(index.containsKey("oldest"))
        assertTrue(index.containsKey("middle"))
        assertTrue(index.containsKey("newest"))
    }

    @Test
    fun `active session preserved during cap`() {
        val sessionsDir = tempDir.newFolder("cap-active")
        val now = System.currentTimeMillis()
        val index = mutableMapOf(
            "oldest-active" to SessionMetadata("s1", now - 3000, "s1.jsonl"),
            "middle" to SessionMetadata("s2", now - 2000, "s2.jsonl"),
            "newest" to SessionMetadata("s3", now - 1000, "s3.jsonl")
        )
        for (m in index.values) {
            File(sessionsDir, "${m.sessionId}.jsonl").writeText("data")
        }

        val removed = SessionStoreMaintenance.capEntryCount(
            index, sessionsDir, maxEntries = 2, activeSessionKey = "oldest-active"
        )
        // oldest-active is protected, so middle should be removed
        assertEquals(1, removed)
        assertTrue(index.containsKey("oldest-active"))
        assertTrue(index.containsKey("newest"))
    }

    // ===== rotateSessionFile =====

    @Test
    fun `file under max is not rotated`() {
        val file = tempDir.newFile("sessions.json")
        file.writeText("small")
        assertFalse(SessionStoreMaintenance.rotateSessionFile(file, maxBytes = 1000))
        assertTrue(file.exists())
    }

    @Test
    fun `file over max is rotated to backup`() {
        val file = tempDir.newFile("sessions2.json")
        file.writeText("x".repeat(200))
        assertTrue(SessionStoreMaintenance.rotateSessionFile(file, maxBytes = 100))
        // Original file should no longer exist (renamed to .bak)
        assertFalse(file.exists())
        // Backup should exist
        val backups = tempDir.root.listFiles { _, name -> name.startsWith("sessions2.json.bak.") }!!
        assertEquals(1, backups.size)
    }

    @Test
    fun `old backups beyond 3 are deleted`() {
        val dir = tempDir.newFolder("rotate-dir")
        val file = File(dir, "index.json")

        // Create 4 old backups
        for (i in 1..4) {
            File(dir, "index.json.bak.$i").writeText("backup $i")
        }

        // Create oversized file
        file.writeText("x".repeat(200))
        assertTrue(SessionStoreMaintenance.rotateSessionFile(file, maxBytes = 100))

        // Should have at most 3 backups (new one + 2 oldest kept from 4 existing)
        val backups = dir.listFiles { _, name -> name.startsWith("index.json.bak.") }!!
        assertTrue("Expected at most 3 backups, got ${backups.size}", backups.size <= 3)
    }

    @Test
    fun `nonexistent file returns false`() {
        val file = File(tempDir.root, "nonexistent.json")
        assertFalse(SessionStoreMaintenance.rotateSessionFile(file))
    }

    // ===== getActiveSessionMaintenanceWarning =====

    @Test
    fun `null active key returns null`() {
        assertNull(SessionStoreMaintenance.getActiveSessionMaintenanceWarning(
            null, emptyMap(), 30L * 86400000, 500
        ))
    }

    @Test
    fun `active session not in index returns null`() {
        assertNull(SessionStoreMaintenance.getActiveSessionMaintenanceWarning(
            "missing", emptyMap(), 30L * 86400000, 500
        ))
    }

    @Test
    fun `fresh active session returns null`() {
        val index = mapOf("active" to SessionMetadata("s1", System.currentTimeMillis(), "s1.jsonl"))
        assertNull(SessionStoreMaintenance.getActiveSessionMaintenanceWarning(
            "active", index, 30L * 86400000, 500
        ))
    }

    @Test
    fun `stale active session returns prune warning`() {
        val staleTime = System.currentTimeMillis() - 31L * 24 * 60 * 60 * 1000
        val index = mapOf("active" to SessionMetadata("s1", staleTime, "s1.jsonl"))
        val warning = SessionStoreMaintenance.getActiveSessionMaintenanceWarning(
            "active", index, 30L * 86400000, 500
        )
        assertNotNull(warning)
        assertTrue(warning!!.contains("pruned"))
    }
}
