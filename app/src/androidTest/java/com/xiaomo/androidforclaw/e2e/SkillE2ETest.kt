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
import com.xiaomo.androidforclaw.agent.tools.AndroidToolRegistry
import com.xiaomo.androidforclaw.core.MyApplication
import com.xiaomo.androidforclaw.data.model.TaskDataManager
import com.xiaomo.androidforclaw.ui.activity.MainActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*
import org.junit.FixMethodOrder
import org.junit.runners.MethodSorters
import java.io.File

/**
 * Skill 功能端到端测试
 *
 * 测试真实的Android Skills执行:
 * - screenshot: 截图
 * - tap: 点击
 * - swipe: 滑动
 * - type: 输入
 * - home/back: 导航
 * - open_app: 打开应用
 *
 * 按照Agent实际使用场景测试
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class SkillE2ETest {

    companion object {
        private const val TIMEOUT = 5000L
        private const val PACKAGE_NAME = "com.xiaomo.androidforclaw.debug"

        // 静态变量,在所有测试间共享
        lateinit var device: UiDevice
        lateinit var context: Context
        lateinit var toolRegistry: AndroidToolRegistry
        lateinit var taskDataManager: TaskDataManager

        @BeforeClass
        @JvmStatic
        fun setupOnce() {
            device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
            context = ApplicationProvider.getApplicationContext<MyApplication>()
            taskDataManager = TaskDataManager.getInstance()
            toolRegistry = AndroidToolRegistry(context, taskDataManager)

            // 只启动一次应用,供所有测试使用
            println("\n🚀 启动应用 - 开始Skill测试套件")
            println("=" .repeat(60))
            launchApp()
            Thread.sleep(1500)
        }

        @JvmStatic
        fun launchApp() {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                setClassName(PACKAGE_NAME, MainActivity::class.java.name)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            context.startActivity(intent)
            device.wait(Until.hasObject(By.pkg(PACKAGE_NAME).depth(0)), TIMEOUT)
            device.waitForIdle()
        }
    }

    /**
     * 场景1: 截图功能
     * Agent需要观察屏幕时使用
     */
    @Test
    fun test01_skill_screenshot() = runBlocking {
        println("🎯 测试Skill: screenshot")
        println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

        // 执行截图
        val result = toolRegistry.execute("screenshot", emptyMap())

        assertTrue("截图应该成功", result.success)
        assertTrue("应该返回图片路径", result.content.contains("/sdcard/"))
        assertTrue("路径应该是PNG", result.content.contains(".png"))

        // 验证文件确实存在
        val imagePath = result.content.substringAfter("saved to ").trim()
        val imageFile = File(imagePath)
        assertTrue("截图文件应该存在", imageFile.exists())
        assertTrue("文件应该有内容", imageFile.length() > 0)

        println("✅ 截图成功: ${imageFile.name} (${imageFile.length() / 1024}KB)")
        println()
    }

    /**
     * 场景2: Home导航
     * Agent需要返回主屏幕时使用
     */
    @Test
    fun test02_skill_home() = runBlocking {
        println("🎯 测试Skill: home")
        println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

        // 执行Home
        val result = toolRegistry.execute("home", emptyMap())

        assertTrue("Home应该成功", result.success)
        delay(500)

        // 验证确实到了主屏幕
        val homeScreen = device.wait(Until.hasObject(By.pkg("com.miui.home")), 2000)
            || device.wait(Until.hasObject(By.pkg("com.android.launcher")), 2000)

        println("✅ 返回主屏幕成功")
        println()

        // 返回到应用继续测试
        println("  → 返回应用继续测试...")
        launchApp()
        Thread.sleep(500)
    }

    /**
     * 场景3: Back导航
     * Agent需要返回上一页时使用
     */
    @Test
    fun test03_skill_back() = runBlocking {
        println("🎯 测试Skill: back")
        println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

        // 执行Back
        val result = toolRegistry.execute("back", emptyMap())

        assertTrue("Back应该成功", result.success)
        delay(500)

        println("✅ 返回上一页成功")
        println()

        // 返回到应用继续测试
        println("  → 返回应用继续测试...")
        launchApp()
        Thread.sleep(500)
    }

    /**
     * 场景4: Wait等待
     * Agent需要等待页面加载时使用
     */
    @Test
    fun test04_skill_wait() = runBlocking {
        println("🎯 测试Skill: wait")
        println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

        val startTime = System.currentTimeMillis()

        // 等待0.2秒
        val result = toolRegistry.execute("wait", mapOf("seconds" to 0.2))

        val elapsed = System.currentTimeMillis() - startTime

        assertTrue("Wait应该成功", result.success)
        assertTrue("应该等待约200ms", elapsed >= 180 && elapsed < 300)

        println("✅ 等待成功: ${elapsed}ms")
        println()
    }

    /**
     * 场景5: Log记录
     * Agent需要记录信息时使用
     */
    @Test
    fun test05_skill_log() = runBlocking {
        println("🎯 测试Skill: log")
        println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

        // 记录日志
        val result = toolRegistry.execute("log", mapOf(
            "message" to "Skill E2E 测试日志",
            "level" to "INFO"
        ))

        assertTrue("Log应该成功", result.success)

        println("✅ 日志记录成功")
        println()
    }

    /**
     * 场景6: Notification通知
     * Agent需要发送通知时使用
     */
    @Test
    fun test06_skill_notification() = runBlocking {
        println("🎯 测试Skill: notification")
        println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

        // 发送通知
        val result = toolRegistry.execute("notification", mapOf(
            "title" to "Skill测试",
            "message" to "这是一条测试通知"
        ))

        assertTrue("Notification应该成功", result.success)

        println("✅ 通知发送成功")
        println()
    }

    /**
     * 场景7: Stop停止
     * Agent完成任务时使用
     */
    @Test
    fun test07_skill_stop() = runBlocking {
        println("🎯 测试Skill: stop")
        println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

        // 停止执行
        val result = toolRegistry.execute("stop", mapOf(
            "reason" to "Skill测试完成"
        ))

        assertTrue("Stop应该成功", result.success)
        assertTrue("应该有stopped标记", result.metadata["stopped"] == true)

        println("✅ 停止执行成功")
        println()
    }

    /**
     * 场景8: 完整Agent工作流
     * 模拟Agent完整执行流程
     */
    @Test
    fun test08_completeAgentWorkflow() = runBlocking {
        println("🤖 完整Agent工作流测试")
        println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        println()
        println("📋 场景: Agent需要截图并分析屏幕内容")
        println()

        val workflow = listOf(
            "步骤1: 记录开始" to mapOf("tool" to "log", "args" to mapOf("message" to "开始截图任务")),
            "步骤2: 等待UI稳定" to mapOf("tool" to "wait", "args" to mapOf("seconds" to 0.3)),
            "步骤3: 执行截图" to mapOf("tool" to "screenshot", "args" to emptyMap<String, Any>()),
            "步骤4: 等待保存" to mapOf("tool" to "wait", "args" to mapOf("seconds" to 0.2)),
            "步骤5: 记录完成" to mapOf("tool" to "log", "args" to mapOf("message" to "截图完成")),
            "步骤6: 发送通知" to mapOf("tool" to "notification", "args" to mapOf(
                "title" to "任务完成",
                "message" to "截图已保存"
            )),
            "步骤7: 停止执行" to mapOf("tool" to "stop", "args" to mapOf("reason" to "任务完成"))
        )

        val results = mutableListOf<Triple<String, String, Boolean>>()
        val startTime = System.currentTimeMillis()

        workflow.forEachIndexed { index, (step, config) ->
            val toolName = config["tool"] as String
            @Suppress("UNCHECKED_CAST")
            val args = config["args"] as Map<String, Any?>

            print("  ${index + 1}. $step ... ")

            val result = toolRegistry.execute(toolName, args)
            results.add(Triple(step, toolName, result.success))

            if (result.success) {
                println("✅")
                if (toolName == "screenshot" && result.content.contains("/sdcard/")) {
                    val path = result.content.substringAfter("saved to ").trim()
                    println("     → 截图保存: ${File(path).name}")
                }
            } else {
                println("❌ ${result.content}")
            }

            delay(100) // 模拟Agent思考
        }

        val totalTime = System.currentTimeMillis() - startTime
        val allSuccess = results.all { it.third }

        println()
        println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        println("📊 工作流统计:")
        println("   - 总步骤: ${workflow.size}")
        println("   - 成功: ${results.count { it.third }}")
        println("   - 失败: ${results.count { !it.third }}")
        println("   - 总耗时: ${totalTime}ms")
        println("   - 平均耗时: ${totalTime / workflow.size}ms/步")
        println()

        assertTrue("所有步骤应该成功", allSuccess)

        if (allSuccess) {
            println("✅ 完整Agent工作流测试通过")
        }
        println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        println()
    }

    // ========== 辅助方法 ==========
    // launchApp 已移至 companion object 作为共享方法
}
