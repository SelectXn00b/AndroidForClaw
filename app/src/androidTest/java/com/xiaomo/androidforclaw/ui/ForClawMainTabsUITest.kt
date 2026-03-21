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
 * ForClaw 主界面 UI 主线测试（精简版）
 *
 * 关键路径:
 * 1. Connect Tab 四卡片可见（LLM API / Gateway / Channels / Skills）
 * 2. Connect Tab "修改配置" 跳转到 ModelConfigActivity（非引导页）
 * 3. Settings Tab 显示 ForClaw 设置（非 OpenClaw 原版）
 * 4. Settings 中 "模型配置" 可跳转
 * 5. Connect ↔ Settings 来回切换不崩溃
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

        // 确保存储权限
        instr.uiAutomation.executeShellCommand(
            "appops set $PKG MANAGE_EXTERNAL_STORAGE allow"
        ).close()

        // 通过 ActivityScenario 启动（在 test 进程内，不会被框架 close 掉）
        val intent = Intent(ApplicationProvider.getApplicationContext(), MainActivityCompose::class.java)
        scenario = ActivityScenario.launch(intent)

        // 如果 ModelSetupActivity 拦截，按 Back 关闭
        Thread.sleep(2000)
        if (device.findObject(UiSelector().textContains("欢迎使用")).exists()) {
            device.pressBack()
            Thread.sleep(1000)
        }

        // 等待 Compose 渲染
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

    private fun hasText(text: String, timeout: Long = 2_000L): Boolean {
        return device.findObject(UiSelector().textContains(text)).waitForExists(timeout)
    }

    private fun clickTab(label: String) {
        findText(label).click()
        device.waitForIdle()
    }

    private fun scrollDown() {
        val h = device.displayHeight
        val w = device.displayWidth
        device.swipe(w / 2, h * 3 / 4, w / 2, h / 4, 15)
        device.waitForIdle()
    }

    private fun scrollUntilText(text: String, maxSwipes: Int = 5): Boolean {
        if (hasText(text, 1000)) return true
        repeat(maxSwipes) {
            scrollDown()
            if (hasText(text, 1000)) return true
        }
        return false
    }

    // ── Tests ────────────────────────────────────────────────────────────

    /**
     * Connect Tab 四个状态卡片都可见
     */
    @Test
    fun test01_connectTab_allCardsVisible() {
        clickTab("Connect")
        findText("LLM API")
        findText("本地 Gateway")
        findText("Channels")
        findText("Skills")
    }

    /**
     * "修改配置" 跳转到 ModelConfigActivity，不应打开引导页
     */
    @Test
    fun test02_connectTab_modifyConfig_opensConfigNotSetup() {
        clickTab("Connect")
        findText("修改配置").click()
        Thread.sleep(1500)
        device.waitForIdle()

        assertEquals("Should stay in app", PKG, device.currentPackageName)
        assertFalse("Should NOT open ModelSetupActivity (引导页)",
            hasText("欢迎使用", 2000))

        device.pressBack()
        device.waitForIdle()
    }

    /**
     * Settings Tab 显示 ForClaw 设置，而非 OpenClaw 原版
     */
    @Test
    fun test03_settingsTab_showsForClawSettings() {
        clickTab("Settings")
        findText("模型配置")
        findText("Channels")

        assertFalse("Should NOT show OpenClaw settings",
            hasText("Node Identity", 2000))

        assertTrue("Termux 配置 should be visible", scrollUntilText("Termux 配置"))
        assertTrue("openclaw.json should be visible", scrollUntilText("openclaw.json"))
        assertTrue("检查更新 should be visible", scrollUntilText("检查更新"))
    }

    /**
     * Settings "模型配置" 点击可跳转
     */
    @Test
    fun test04_settingsTab_modelConfig_navigates() {
        clickTab("Settings")
        findText("模型配置").click()
        Thread.sleep(1500)
        device.waitForIdle()
        assertEquals("Should stay in app", PKG, device.currentPackageName)
        device.pressBack()
        device.waitForIdle()
    }

    /**
     * Connect ↔ Settings 来回切换不崩溃
     */
    @Test
    fun test05_tabSwitch_connectAndSettings() {
        clickTab("Connect")
        findText("LLM API")

        clickTab("Settings")
        findText("模型配置")

        clickTab("Connect")
        findText("LLM API")
        findText("本地 Gateway")
    }
}
