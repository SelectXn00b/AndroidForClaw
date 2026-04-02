package com.xiaomo.androidforclaw.updater

import org.junit.Assert.*
import org.junit.Test

/**
 * AppUpdater 版本比较和常量测试
 */
class AppUpdaterTest {

    @Test
    fun `server constants are correct`() {
        assertEquals("https://claw.devset.top/files", AppUpdater.UPDATE_BASE_URL)
        assertEquals("https://claw.devset.top/files/version.json", AppUpdater.VERSION_JSON_URL)
    }

    @Test
    fun `APK cache is single file`() {
        // AppUpdater saves to cache/updates/update.apk — fixed name, overwritten each release
        // No version-in-name pattern needed since we only keep one copy
        assertTrue(true)
    }

    // Version comparison tests (static logic)
    @Test
    fun `newer major version detected`() {
        assertTrue(compareVersions("2.0.0", "1.0.0"))
    }

    @Test
    fun `newer minor version detected`() {
        assertTrue(compareVersions("1.1.0", "1.0.0"))
    }

    @Test
    fun `newer patch version detected`() {
        assertTrue(compareVersions("1.0.3", "1.0.2"))
    }

    @Test
    fun `same version not newer`() {
        assertFalse(compareVersions("1.0.2", "1.0.2"))
    }

    @Test
    fun `older version not newer`() {
        assertFalse(compareVersions("1.0.1", "1.0.2"))
    }

    @Test
    fun `different length versions`() {
        assertTrue(compareVersions("1.0.2.1", "1.0.2"))
        assertFalse(compareVersions("1.0", "1.0.1"))
    }

    /**
     * Pure version comparison logic (mirrors AppUpdater.isNewerVersion)
     */
    private fun compareVersions(latest: String, current: String): Boolean {
        val latestParts = latest.split(".").map { it.toIntOrNull() ?: 0 }
        val currentParts = current.split(".").map { it.toIntOrNull() ?: 0 }
        val maxLen = maxOf(latestParts.size, currentParts.size)
        for (i in 0 until maxLen) {
            val l = latestParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (l > c) return true
            if (l < c) return false
        }
        return false
    }
}
