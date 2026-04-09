package com.xiaomo.androidforclaw.agent.session

import java.io.File
import kotlin.io.path.createTempDirectory
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionManagerDirectoryTest {
    @Test
    fun ensureSessionFileParentExists_createsMissingParentDirectory() {
        val root = createTempDirectory("session-manager-test-").toFile()
        try {
            val target = File(root, "sessions/test.jsonl")
            ensureSessionFileParentExists(target)
            assertTrue(target.parentFile?.exists() == true)
        } finally {
            root.deleteRecursively()
        }
    }
}
