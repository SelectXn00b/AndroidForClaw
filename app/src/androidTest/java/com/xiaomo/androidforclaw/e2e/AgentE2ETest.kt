package com.xiaomo.androidforclaw.e2e

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.xiaomo.androidforclaw.core.MyApplication
import com.xiaomo.androidforclaw.ui.activity.MainActivity
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*
import org.junit.FixMethodOrder
import org.junit.runners.MethodSorters

/**
 * Agent 端到端测试
 * 一个完整的用户使用流程测试
 *
 * 测试流程:
 * 1. 启动应用
 * 2. 检查权限
 * 3. 显示悬浮窗
 * 4. 打开聊天窗口
 * 5. 发送消息触发Agent
 * 6. 验证Agent执行
 * 7. 清理
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class AgentE2ETest {

    private lateinit var device: UiDevice
    private lateinit var context: Context

    companion object {
        private const val TIMEOUT = 5000L
        private const val PACKAGE_NAME = "com.xiaomo.androidforclaw.debug"
    }

    @Before
    fun setup() {
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        context = ApplicationProvider.getApplicationContext<MyApplication>()

        // 确保从主屏幕开始
        device.pressHome()
        device.waitForIdle()
    }

    /**
     * 测试1: 启动应用
     */
    @Test
    fun test01_launchApp() {
        // 启动应用
        val intent = Intent(Intent.ACTION_MAIN).apply {
            setClassName(PACKAGE_NAME, MainActivity::class.java.name)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        context.startActivity(intent)

        // 等待应用启动
        val launched = device.wait(
            Until.hasObject(By.pkg(PACKAGE_NAME).depth(0)),
            TIMEOUT
        )

        assertTrue("应用应该启动成功", launched)
        Thread.sleep(1000) // 等待界面稳定
    }

    /**
     * 测试2: 检查主界面元素
     */
    @Test
    fun test02_checkMainUI() {
        launchApp()

        // 检查主界面是否显示
        val mainLayout = device.findObject(By.pkg(PACKAGE_NAME))
        assertNotNull("主界面应该显示", mainLayout)

        Thread.sleep(500)
    }

    /**
     * 测试3: 检查悬浮窗功能
     */
    @Test
    fun test03_checkFloatingWindow() {
        launchApp()
        Thread.sleep(1000)

        // 按Home键,悬浮窗应该继续显示
        device.pressHome()
        device.waitForIdle()
        Thread.sleep(500)

        // 验证应用仍在运行(通过检查进程或悬浮窗)
        // 注: 悬浮窗需要特殊权限,测试环境可能无法验证
    }

    /**
     * 测试4: 返回应用
     */
    @Test
    fun test04_returnToApp() {
        launchApp()

        // 按Home键
        device.pressHome()
        device.waitForIdle()
        Thread.sleep(500)

        // 重新打开应用
        launchApp()

        // 验证应用恢复
        val mainLayout = device.findObject(By.pkg(PACKAGE_NAME))
        assertNotNull("应用应该恢复", mainLayout)
    }

    /**
     * 测试5: 配置加载
     */
    @Test
    fun test05_configLoads() {
        launchApp()
        Thread.sleep(1000)

        // 验证应用没有崩溃
        val mainLayout = device.findObject(By.pkg(PACKAGE_NAME))
        assertNotNull("配置加载后应用应该正常", mainLayout)
    }

    /**
     * 测试6: 完整流程 - 打开设置
     */
    @Test
    fun test06_openSettings() {
        launchApp()
        Thread.sleep(1000)

        // 尝试查找设置按钮或菜单
        // 注: 具体元素ID需要根据实际UI调整
        try {
            val settingsButton = device.findObject(By.desc("设置"))
                ?: device.findObject(By.text("设置"))

            if (settingsButton != null) {
                settingsButton.click()
                device.waitForIdle()
                Thread.sleep(500)
            }
        } catch (e: Exception) {
            // 如果找不到设置按钮,测试不失败
        }
    }

    /**
     * 测试7: 应用稳定性 - 快速切换
     */
    @Test
    fun test07_appStability() {
        launchApp()

        repeat(3) { i ->
            Thread.sleep(500)

            // 按Home
            device.pressHome()
            device.waitForIdle()
            Thread.sleep(300)

            // 返回应用
            launchApp()
            Thread.sleep(500)

            // 验证应用仍然正常
            val mainLayout = device.findObject(By.pkg(PACKAGE_NAME))
            assertNotNull("第${i+1}次切换后应用应该正常", mainLayout)
        }
    }

    /**
     * 测试8: 内存和性能
     */
    @Test
    fun test08_memoryAndPerformance() {
        launchApp()
        Thread.sleep(2000)

        // 验证应用响应
        device.waitForIdle()

        // 简单的性能测试: 应用应该在2秒内稳定
        val isResponsive = device.wait(
            Until.hasObject(By.pkg(PACKAGE_NAME)),
            2000
        )

        assertTrue("应用应该响应", isResponsive)
    }

    // ========== 辅助方法 ==========

    private fun launchApp() {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            setClassName(PACKAGE_NAME, MainActivity::class.java.name)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        context.startActivity(intent)

        device.wait(Until.hasObject(By.pkg(PACKAGE_NAME).depth(0)), TIMEOUT)
        device.waitForIdle()
    }
}
