package com.xiaomo.androidforclaw.ui

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import com.xiaomo.androidforclaw.ui.activity.MainActivityCompose
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters

/**
 * ForClaw 主界面 UI 主线测试
 *
 * 关键路径:
 * 1. Connect 四卡片可见，"修改配置" 跳转正确
 * 2. Settings 显示 ForClaw 内容；模型配置可跳转
 * 3. Connect ↔ Settings 来回切换不崩溃
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class ForClawMainTabsUITest {

    companion object {
        private const val PKG     = "com.xiaomo.androidforclaw"
        private const val TIMEOUT = 8_000L
    }

    private lateinit var device: UiDevice
    private lateinit var scenario: ActivityScenario<MainActivityCompose>

    @Before
    fun setUp() {
        val instr = InstrumentationRegistry.getInstrumentation()
        device = UiDevice.getInstance(instr)
        instr.uiAutomation.executeShellCommand(
            "appops set $PKG MANAGE_EXTERNAL_STORAGE allow"
        ).close()

        val intent = Intent(ApplicationProvider.getApplicationContext(), MainActivityCompose::class.java)
        scenario = ActivityScenario.launch(intent)

        Thread.sleep(2000)
        if (device.findObject(UiSelector().textContains("欢迎使用")).exists()) {
            device.pressBack()
            Thread.sleep(1000)
        }
        device.findObject(UiSelector().text("Connect")).waitForExists(TIMEOUT)
        device.waitForIdle()
    }

    @After
    fun tearDown() {
        scenario.close()
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private fun findText(text: String, timeout: Long = TIMEOUT): UiObject {
        val obj = device.findObject(UiSelector().textContains(text))
        assertTrue("'$text' not found within ${timeout}ms", obj.waitForExists(timeout))
        return obj
    }

    private fun hasText(text: String, timeout: Long = 2_000L): Boolean =
        device.findObject(UiSelector().textContains(text)).waitForExists(timeout)

    /** Exact-match tab click — avoids hitting "Connected" when targeting "Connect" */
    private fun clickTab(label: String) {
        val obj = device.findObject(UiSelector().text(label))
        assertTrue("Tab '$label' not found within ${TIMEOUT}ms", obj.waitForExists(TIMEOUT))
        obj.click()
        device.waitForIdle()
        Thread.sleep(600)
    }

    private fun scrollDown() {
        device.swipe(device.displayWidth / 2, device.displayHeight * 3 / 4,
                     device.displayWidth / 2, device.displayHeight / 4, 15)
        device.waitForIdle()
    }

    private fun scrollUntilText(text: String, maxSwipes: Int = 5): Boolean {
        if (hasText(text, 1000)) return true
        repeat(maxSwipes) { scrollDown(); if (hasText(text, 1000)) return true }
        return false
    }

    // ── Tests ────────────────────────────────────────────────────────────

    /** Connect 四卡片可见，"修改配置" 跳转到 ModelConfigActivity 不打开引导页 */
    @Test
    fun test01_connectTab() {
        clickTab("Connect")
        findText("LLM API")
        findText("本地 Gateway")
        findText("Channels")
        findText("Skills")

        findText("修改配置").click()
        Thread.sleep(1200)
        device.waitForIdle()
        assertEquals("Should stay in app", PKG, device.currentPackageName)
        assertFalse("Should NOT open 引导页", hasText("欢迎使用", 2000))
        device.pressBack()
        device.waitForIdle()
    }

    /** Settings 显示 ForClaw 内容；模型配置可跳转 */
    @Test
    fun test02_settingsTab() {
        clickTab("Settings")
        findText("模型配置")
        findText("Channels")
        assertFalse("Should NOT show OpenClaw settings", hasText("Node Identity", 2000))
        assertTrue("Termux 配置 should be visible",  scrollUntilText("Termux 配置"))
        assertTrue("openclaw.json should be visible", scrollUntilText("openclaw.json"))
        assertTrue("检查更新 should be visible",      scrollUntilText("检查更新"))

        scrollUntilText("模型配置")
        findText("模型配置").click()
        Thread.sleep(1200)
        device.waitForIdle()
        assertEquals("Should stay in app", PKG, device.currentPackageName)
        device.pressBack()
        device.waitForIdle()
    }

}
