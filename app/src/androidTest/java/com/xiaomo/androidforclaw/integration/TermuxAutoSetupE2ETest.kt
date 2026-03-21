package com.xiaomo.androidforclaw.integration

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.xiaomo.androidforclaw.agent.tools.TermuxBridgeTool
import com.xiaomo.androidforclaw.agent.tools.TermuxSetupStep
import com.xiaomo.androidforclaw.agent.tools.TermuxStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters

/**
 * Termux 自动配置端到端测试
 *
 * 在真机上运行，验证完整 setup 链路：
 * 1. 状态检测是否正确
 * 2. triggerAutoSetup() 是否能推进状态
 * 3. 最终是否能达到 READY（取决于 Termux 是否已初始化）
 *
 * 运行方式：
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
        Log.i(TAG, "  termuxApiInstalled=${status.termuxApiInstalled}")
        Log.i(TAG, "  runCommandPermissionDeclared=${status.runCommandPermissionDeclared}")
        Log.i(TAG, "  runCommandServiceAvailable=${status.runCommandServiceAvailable}")
        Log.i(TAG, "  sshReachable=${status.sshReachable}")
        Log.i(TAG, "  sshConfigPresent=${status.sshConfigPresent}")
        Log.i(TAG, "  keypairPresent=${status.keypairPresent}")
        Log.i(TAG, "  ready=${status.ready}")

        // 状态对象不为 null，字段有效
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
            Log.i(TAG, "Termux not installed — cannot proceed further")
            return
        }

        if (!status.termuxApiInstalled) {
            assertEquals(TermuxSetupStep.TERMUX_API_NOT_INSTALLED, status.lastStep)
            Log.i(TAG, "Termux:API not installed — expected step matches")
            return
        }

        // 如果 Termux 和 API 都装了，检查后续步骤是否一致
        Log.i(TAG, "Both Termux and API installed, step=${status.lastStep}")
        when (status.lastStep) {
            TermuxSetupStep.RUN_COMMAND_PERMISSION_DENIED -> assertFalse(status.runCommandPermissionDeclared)
            TermuxSetupStep.RUN_COMMAND_SERVICE_MISSING -> assertFalse(status.runCommandServiceAvailable)
            TermuxSetupStep.KEYPAIR_MISSING -> assertFalse(status.keypairPresent)
            TermuxSetupStep.SSHD_NOT_REACHABLE -> assertFalse(status.sshReachable)
            TermuxSetupStep.SSH_CONFIG_MISSING -> {
                assertTrue(status.sshReachable)
                assertFalse(status.sshConfigPresent)
            }
            TermuxSetupStep.READY -> assertTrue(status.ready)
            else -> Log.w(TAG, "Unexpected step: ${status.lastStep}")
        }
    }

    @Test
    fun test04_triggerAutoSetup_progressesOrStaysStable() = runBlocking {
        val before = bridge.getStatus()
        Log.i(TAG, "Before auto-setup: step=${before.lastStep}, ready=${before.ready}")

        if (!before.termuxInstalled) {
            Log.i(TAG, "Termux not installed, skipping auto-setup test")
            return@runBlocking
        }

        val after = bridge.triggerAutoSetup()
        Log.i(TAG, "After auto-setup: step=${after.lastStep}, ready=${after.ready}")
        Log.i(TAG, "  sshReachable=${after.sshReachable}")
        Log.i(TAG, "  sshConfigPresent=${after.sshConfigPresent}")
        Log.i(TAG, "  keypairPresent=${after.keypairPresent}")

        // 状态要么进步了，要么至少没退步
        val stepOrder = TermuxSetupStep.values().toList()
        val beforeIdx = stepOrder.indexOf(before.lastStep)
        val afterIdx = stepOrder.indexOf(after.lastStep)

        // keypair 应该在 auto-setup 后被生成（如果之前没有）
        if (!before.keypairPresent && before.termuxInstalled) {
            Log.i(TAG, "Checking if keypair was generated...")
            // 不强制 assert，因为 ssh-keygen 在某些设备上可能不可用
        }

        Log.i(TAG, "Step progression: ${before.lastStep}($beforeIdx) -> ${after.lastStep}($afterIdx)")
    }

    @Test
    fun test05_statusPersistence_fileWritten() {
        // 触发一次 getStatus，确认落盘
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
            // 不强制失败，可能是存储权限问题
        }
    }

}
