package com.xiaomo.hermes.agent.session

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SessionDiskBudgetTest {

    @Rule
    @JvmField
    val tempDir = TemporaryFolder()

    // ===== enforceSessionDiskBudget =====

    @Test
    fun `under budget returns null`() = runBlocking {
        val sessionsDir = tempDir.newFolder("sessions")
        File(sessionsDir, "s1.jsonl").writeText("small")
        val index = mutableMapOf(
            "k1" to SessionMetadata("s1", System.currentTimeMillis(), "s1.jsonl")
        )

        val result = SessionDiskBudget.enforceSessionDiskBudget(
            sessionsDir = sessionsDir,
            sessionIndex = index,
            activeKey = null,
            maxDiskBytes = 1_000_000
        )
        assertNull(result)
    }

    @Test
    fun `orphan files cleaned first`() = runBlocking {
        val sessionsDir = tempDir.newFolder("sessions-orphan")
        val data = "x".repeat(500)
        // Known session file
        File(sessionsDir, "known.jsonl").writeText(data)
        // Orphan file (not in index)
        File(sessionsDir, "orphan.jsonl").writeText(data)

        val index = mutableMapOf(
            "k1" to SessionMetadata("known", System.currentTimeMillis(), "known.jsonl")
        )

        val result = SessionDiskBudget.enforceSessionDiskBudget(
            sessionsDir = sessionsDir,
            sessionIndex = index,
            activeKey = null,
            maxDiskBytes = 800,
            highWaterBytes = 600
        )
        assertNotNull(result)
        assertTrue(result!!.deletedCount >= 1)
        assertFalse(File(sessionsDir, "orphan.jsonl").exists())
        assertTrue(File(sessionsDir, "known.jsonl").exists())
    }

    @Test
    fun `oldest sessions cleaned when over budget`() = runBlocking {
        val sessionsDir = tempDir.newFolder("sessions-budget")
        val now = System.currentTimeMillis()
        val data = "x".repeat(500)

        File(sessionsDir, "old.jsonl").writeText(data)
        File(sessionsDir, "mid.jsonl").writeText(data)
        File(sessionsDir, "new.jsonl").writeText(data)

        val index = mutableMapOf(
            "k-old" to SessionMetadata("old", now - 3000, "old.jsonl"),
            "k-mid" to SessionMetadata("mid", now - 2000, "mid.jsonl"),
            "k-new" to SessionMetadata("new", now - 1000, "new.jsonl")
        )

        val result = SessionDiskBudget.enforceSessionDiskBudget(
            sessionsDir = sessionsDir,
            sessionIndex = index,
            activeKey = null,
            maxDiskBytes = 1200,
            highWaterBytes = 600
        )
        assertNotNull(result)
        assertTrue(result!!.deletedCount > 0)
        assertTrue(result.freedBytes > 0)
        // Oldest should be removed first
        assertFalse(index.containsKey("k-old"))
    }

    @Test
    fun `active session preserved during cleanup`() = runBlocking {
        val sessionsDir = tempDir.newFolder("sessions-active")
        val now = System.currentTimeMillis()
        val data = "x".repeat(500)

        File(sessionsDir, "active.jsonl").writeText(data)
        File(sessionsDir, "other.jsonl").writeText(data)

        val index = mutableMapOf(
            "k-active" to SessionMetadata("active", now - 5000, "active.jsonl"),
            "k-other" to SessionMetadata("other", now - 1000, "other.jsonl")
        )

        val result = SessionDiskBudget.enforceSessionDiskBudget(
            sessionsDir = sessionsDir,
            sessionIndex = index,
            activeKey = "k-active",
            maxDiskBytes = 800,
            highWaterBytes = 400
        )
        // Active session should still be in index
        assertTrue(index.containsKey("k-active"))
        assertTrue(File(sessionsDir, "active.jsonl").exists())
    }

    @Test
    fun `result reports correct bytes`() = runBlocking {
        val sessionsDir = tempDir.newFolder("sessions-bytes")
        val data = "x".repeat(1000)

        File(sessionsDir, "s1.jsonl").writeText(data)
        File(sessionsDir, "s2.jsonl").writeText(data)

        val index = mutableMapOf(
            "k1" to SessionMetadata("s1", System.currentTimeMillis() - 2000, "s1.jsonl"),
            "k2" to SessionMetadata("s2", System.currentTimeMillis() - 1000, "s2.jsonl")
        )

        val result = SessionDiskBudget.enforceSessionDiskBudget(
            sessionsDir = sessionsDir,
            sessionIndex = index,
            activeKey = null,
            maxDiskBytes = 1500,
            highWaterBytes = 800
        )
        assertNotNull(result)
        assertTrue(result!!.totalBytesBefore > result.totalBytesAfter)
        assertTrue(result.freedBytes > 0)
    }
}
