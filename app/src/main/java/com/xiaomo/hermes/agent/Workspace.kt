package com.xiaomo.hermes.agent

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/workspace.ts
 *
 * Agent workspace abstraction.
 */

import com.xiaomo.hermes.workspace.StoragePaths
import java.io.File

object Workspace {
    /** Main workspace directory */
    val root: File
        get() = StoragePaths.workspace

    /** Skills directory within workspace */
    val skillsDir: File
        get() = File(root, "skills")

    /** Memory directory within workspace */
    val memoryDir: File
        get() = File(root, "memory")

    /** Check if workspace is initialized (has at least SOUL.md or BOOTSTRAP.md) */
    fun isInitialized(): Boolean {
        return File(root, "SOUL.md").exists() || File(root, "BOOTSTRAP.md").exists()
    }

    /** Resolve a path relative to workspace */
    fun resolve(relativePath: String): File = File(root, relativePath)

    /** List workspace bootstrap files that exist */
    fun listBootstrapFiles(): List<File> {
        val names = listOf("AGENTS.md", "SOUL.md", "TOOLS.md", "IDENTITY.md",
            "USER.md", "HEARTBEAT.md", "BOOTSTRAP.md", "MEMORY.md")
        return names.map { File(root, it) }.filter { it.exists() }
    }

    /** Ensure workspace directory structure exists */
    fun ensureDirectories() {
        root.mkdirs()
        skillsDir.mkdirs()
        memoryDir.mkdirs()
    }
}
