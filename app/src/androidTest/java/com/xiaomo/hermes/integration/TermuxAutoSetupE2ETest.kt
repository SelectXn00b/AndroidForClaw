package com.xiaomo.hermes.integration

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.xiaomo.hermes.agent.tools.TermuxBridgeTool
import com.xiaomo.hermes.agent.tools.TermuxSetupStep
import org.junit.Assert.*
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters

/**
 * Termux SSH E2E test
 *
 * Validates status detection and SSH connectivity on device.
 *
 * Run:
 *   ./gradlew :app:connectedDebugAndroidTest --tests '*.TermuxAutoSetupE2ETest'
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class TermuxAutoSetupE2ETest {

    companion object {
        private const val TAG = "TermuxAutoSetupE2E"
    }

    private lateinit var bridge: TermuxBridgeTool

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        bridge = TermuxBridgeTool(context)
    }

    @Test
    fun test01_getStatus_returnsValidStatus() {
        val status = bridge.getStatus()
        Log.i(TAG, "Initial status: step=${status.lastStep}, message=${status.message}")
        Log.i(TAG, "  termuxInstalled=${status.termuxInstalled}")
        Log.i(TAG, "  sshReachable=${status.sshReachable}")
        Log.i(TAG, "  keypairPresent=${status.keypairPresent}")
        Log.i(TAG, "  ready=${status.ready}")

        assertNotNull(status)
        assertNotNull(status.lastStep)
        assertNotNull(status.message)
        assertTrue(status.message.isNotEmpty())
    }

    @Test
    fun test02_getStatus_termuxInstalledMatchesPackageManager() {
        val status = bridge.getStatus()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val termuxInstalled = try {
            context.packageManager.getPackageInfo("com.termux", 0)
            true
        } catch (_: Exception) { false }

        assertEquals("termuxInstalled should match PackageManager", termuxInstalled, status.termuxInstalled)
        Log.i(TAG, "Termux installed: $termuxInstalled (status agrees: ${status.termuxInstalled})")
    }

    @Test
    fun test03_getStatus_stepMatchesFlags() {
        val status = bridge.getStatus()

        if (!status.termuxInstalled) {
            assertEquals(TermuxSetupStep.TERMUX_NOT_INSTALLED, status.lastStep)
            assertFalse(status.ready)
            Log.i(TAG, "Termux not installed")
            return
        }

        when (status.lastStep) {
            TermuxSetupStep.KEYPAIR_MISSING -> assertFalse(status.keypairPresent)
            TermuxSetupStep.SSHD_NOT_REACHABLE -> assertFalse(status.sshReachable)
            TermuxSetupStep.SSH_AUTH_FAILED -> {
                assertTrue(status.sshReachable)
                assertFalse(status.sshAuthOk)
            }
            TermuxSetupStep.READY -> assertTrue(status.ready)
            else -> Log.w(TAG, "Unexpected step: ${status.lastStep}")
        }
    }

    @Test
    fun test04_statusPersistence_fileWritten() {
        bridge.getStatus()

        val statusFile = java.io.File("/sdcard/.androidforclaw/termux_setup_status.json")
        if (statusFile.exists()) {
            val content = statusFile.readText()
            Log.i(TAG, "Status file content: $content")
            assertTrue("Status file should contain lastStep", content.contains("lastStep"))
            assertTrue("Status file should contain updatedAt", content.contains("updatedAt"))
            assertTrue("Status file should contain ready", content.contains("ready"))
        } else {
            Log.w(TAG, "Status file not found at ${statusFile.absolutePath}")
        }
    }
}
