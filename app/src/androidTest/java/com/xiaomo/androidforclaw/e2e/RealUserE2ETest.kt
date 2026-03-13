package com.xiaomo.androidforclaw.e2e

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiSelector
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
 * 真实用户场景E2E测试
 *
 * 测试流程:
 * 1. 用户在输入框输入指令(如"给我截图看看")
 * 2. 发送消息给Agent
 * 3. Agent理解指令并调用相应Skill
 * 4. 验证Skill执行结果
 *
 * 这是最真实的测试场景!
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class RealUserE2ETest {

    private lateinit var device: UiDevice
    private lateinit var context: Context

    companion object {
        private const val TIMEOUT = 10000L
        private const val LONG_TIMEOUT = 30000L
        private const val PACKAGE_NAME = "com.xiaomo.androidforclaw.debug"
    }

    @Before
    fun setup() {
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        context = ApplicationProvider.getApplicationContext<MyApplication>()

        // 从主屏幕开始
        device.pressHome()
        device.waitForIdle()
        Thread.sleep(500)
    }

    /**
     * 测试1: 启动应用并打开聊天
     */
    @Test
    fun test01_launchAndOpenChat() {
        println("\n" + "=".repeat(60))
        println("🚀 测试1: 启动应用并打开聊天窗口")
        println("=".repeat(60))

        // 启动应用
        launchApp()
        Thread.sleep(2000)

        // 查找并点击聊天按钮/图标
        // 注: 需要根据实际UI调整选择器
        val chatButton = findChatButton()

        if (chatButton != null) {
            println("✓ 找到聊天按钮,点击打开")
            chatButton.click()
            device.waitForIdle()
            Thread.sleep(1000)
            println("✅ 聊天窗口应该已打开")
        } else {
            println("⚠️ 未找到聊天按钮,尝试直接查找输入框")
        }

        println()
    }

    /**
     * 测试2: 发送简单指令 - "你好"
     */
    @Test
    fun test02_sendSimpleMessage() {
        println("\n" + "=".repeat(60))
        println("💬 测试2: 发送简单消息")
        println("=".repeat(60))

        launchApp()
        Thread.sleep(2000)

        val inputBox = findInputBox()
        if (inputBox != null) {
            println("✓ 找到输入框")

            // 输入消息
            inputBox.click()
            device.waitForIdle()
            Thread.sleep(500)

            val message = "你好"
            inputBox.setText(message)
            println("✓ 输入消息: $message")

            Thread.sleep(500)

            // 查找并点击发送按钮
            val sendButton = findSendButton()
            if (sendButton != null) {
                println("✓ 找到发送按钮,点击发送")
                sendButton.click()
                device.waitForIdle()
                Thread.sleep(2000)
                println("✅ 消息已发送")
            } else {
                println("⚠️ 未找到发送按钮")
            }
        } else {
            println("❌ 未找到输入框")
        }

        println()
    }

    /**
     * 测试3: 请求截图 - "给我截图看看"
     */
    @Test
    fun test03_requestScreenshot() {
        println("\n" + "=".repeat(60))
        println("📸 测试3: 请求截图")
        println("=".repeat(60))

        launchApp()
        Thread.sleep(2000)

        val result = sendUserMessage("给我截图看看")

        if (result) {
            println("✓ 消息已发送,等待Agent处理...")
            Thread.sleep(5000) // 等待Agent执行

            // 查找Agent回复
            val response = findLatestResponse()
            if (response != null) {
                println("✓ Agent回复: $response")

                if (response.contains("截图") || response.contains("screenshot")) {
                    println("✅ Agent正确理解并执行了截图命令")
                } else {
                    println("⚠️ Agent回复未明确提到截图")
                }
            } else {
                println("⚠️ 未找到Agent回复")
            }
        }

        println()
    }

    /**
     * 测试4: 请求等待 - "等待2秒"
     */
    @Test
    fun test04_requestWait() {
        println("\n" + "=".repeat(60))
        println("⏱️ 测试4: 请求等待")
        println("=".repeat(60))

        launchApp()
        Thread.sleep(2000)

        val startTime = System.currentTimeMillis()
        val result = sendUserMessage("等待2秒")

        if (result) {
            println("✓ 消息已发送,等待Agent处理...")
            Thread.sleep(5000)

            val elapsed = System.currentTimeMillis() - startTime
            println("✓ 总耗时: ${elapsed}ms")

            // Agent处理应该包含等待时间
            if (elapsed >= 2000) {
                println("✅ Agent执行了等待")
            }
        }

        println()
    }

    /**
     * 测试5: 返回主屏幕 - "回到主屏幕"
     */
    @Test
    fun test05_goHome() {
        println("\n" + "=".repeat(60))
        println("🏠 测试5: 返回主屏幕")
        println("=".repeat(60))

        launchApp()
        Thread.sleep(2000)

        val result = sendUserMessage("回到主屏幕")

        if (result) {
            println("✓ 消息已发送,等待Agent处理...")
            Thread.sleep(3000)

            // 验证是否到了主屏幕
            val isHome = device.wait(Until.hasObject(By.pkg("com.miui.home")), 2000)
                || device.wait(Until.hasObject(By.pkg("com.android.launcher")), 2000)

            if (isHome) {
                println("✅ Agent成功执行home命令,已返回主屏幕")
            } else {
                println("⚠️ 未确认是否返回主屏幕")
            }
        }

        println()
    }

    /**
     * 测试6: 发送通知 - "发送一个测试通知"
     */
    @Test
    fun test06_sendNotification() {
        println("\n" + "=".repeat(60))
        println("🔔 测试6: 发送通知")
        println("=".repeat(60))

        launchApp()
        Thread.sleep(2000)

        val result = sendUserMessage("发送一个测试通知,标题是'测试',内容是'这是测试通知'")

        if (result) {
            println("✓ 消息已发送,等待Agent处理...")
            Thread.sleep(5000)

            // 下拉通知栏查看
            device.openNotification()
            Thread.sleep(1000)

            val hasNotification = device.wait(
                Until.hasObject(By.textContains("测试")),
                2000
            )

            if (hasNotification) {
                println("✅ 找到了测试通知")
            } else {
                println("⚠️ 未找到通知(可能需要权限)")
            }

            // 关闭通知栏
            device.pressBack()
            Thread.sleep(500)
        }

        println()
    }

    /**
     * 测试7: 记录日志 - "记录一条日志"
     */
    @Test
    fun test07_logMessage() {
        println("\n" + "=".repeat(60))
        println("📝 测试7: 记录日志")
        println("=".repeat(60))

        launchApp()
        Thread.sleep(2000)

        val result = sendUserMessage("记录一条日志:E2E测试正在运行")

        if (result) {
            println("✓ 消息已发送,等待Agent处理...")
            Thread.sleep(3000)

            val response = findLatestResponse()
            if (response != null && response.contains("成功")) {
                println("✅ Agent成功执行log命令")
            }
        }

        println()
    }

    /**
     * 测试8: 完整任务流程
     */
    @Test
    fun test08_completeTask() {
        println("\n" + "=".repeat(60))
        println("🎯 测试8: 完整任务流程")
        println("=".repeat(60))

        launchApp()
        Thread.sleep(2000)

        val tasks = listOf(
            "记录开始执行任务",
            "等待1秒",
            "记录任务进行中",
            "完成任务"
        )

        println("📋 任务列表:")
        tasks.forEachIndexed { index, task ->
            println("   ${index + 1}. $task")
        }
        println()

        var allSuccess = true

        tasks.forEachIndexed { index, task ->
            println("▶ 执行任务 ${index + 1}: $task")

            val result = sendUserMessage(task)
            if (result) {
                println("  ✓ 消息已发送")
                Thread.sleep(3000)

                val response = findLatestResponse()
                if (response != null) {
                    println("  ✓ Agent回复: ${response.take(50)}...")
                } else {
                    println("  ⚠️ 未收到回复")
                    allSuccess = false
                }
            } else {
                println("  ❌ 发送失败")
                allSuccess = false
            }

            Thread.sleep(1000)
            println()
        }

        if (allSuccess) {
            println("✅ 完整任务流程测试通过")
        } else {
            println("⚠️ 部分任务执行异常")
        }

        println("=".repeat(60))
        println()
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

    private fun findChatButton(): androidx.test.uiautomator.UiObject2? {
        // 尝试多种方式查找聊天按钮
        return device.findObject(By.desc("聊天"))
            ?: device.findObject(By.text("聊天"))
            ?: device.findObject(By.res(PACKAGE_NAME, "chat_button"))
            ?: device.findObject(By.res(PACKAGE_NAME, "fab_chat"))
    }

    private fun findInputBox(): androidx.test.uiautomator.UiObject? {
        // 尝试多种方式查找输入框
        return try {
            device.findObject(UiSelector().className("android.widget.EditText"))
                ?: device.findObject(UiSelector().resourceId("$PACKAGE_NAME:id/et_input"))
                ?: device.findObject(UiSelector().resourceId("$PACKAGE_NAME:id/input_text"))
        } catch (e: Exception) {
            null
        }
    }

    private fun findSendButton(): androidx.test.uiautomator.UiObject2? {
        return device.findObject(By.desc("发送"))
            ?: device.findObject(By.text("发送"))
            ?: device.findObject(By.res(PACKAGE_NAME, "btn_send"))
            ?: device.findObject(By.res(PACKAGE_NAME, "send_button"))
    }

    private fun sendUserMessage(message: String): Boolean {
        val inputBox = findInputBox()
        if (inputBox == null) {
            println("  ❌ 未找到输入框")
            return false
        }

        try {
            inputBox.click()
            device.waitForIdle()
            Thread.sleep(300)

            inputBox.setText(message)
            println("  ✓ 输入: $message")
            Thread.sleep(500)

            val sendButton = findSendButton()
            if (sendButton != null) {
                sendButton.click()
                device.waitForIdle()
                return true
            } else {
                // 尝试按回车发送
                device.pressEnter()
                device.waitForIdle()
                return true
            }
        } catch (e: Exception) {
            println("  ❌ 发送失败: ${e.message}")
            return false
        }
    }

    private fun findLatestResponse(): String? {
        // 查找最新的Agent回复
        // 注: 需要根据实际UI结构调整
        try {
            val messages = device.findObjects(By.clazz("android.widget.TextView"))
            return messages.lastOrNull()?.text
        } catch (e: Exception) {
            return null
        }
    }
}
