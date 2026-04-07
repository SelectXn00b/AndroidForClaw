package com.xiaomo.androidforclaw.e2e

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.xiaomo.androidforclaw.agent.context.ContextBuilder
import com.xiaomo.androidforclaw.agent.context.PromptMode
import com.xiaomo.androidforclaw.agent.loop.AgentLoop
import com.xiaomo.androidforclaw.agent.tools.AndroidToolRegistry
import com.xiaomo.androidforclaw.agent.tools.ToolRegistry
import com.xiaomo.androidforclaw.config.ConfigLoader
import com.xiaomo.androidforclaw.core.MyApplication
import com.xiaomo.androidforclaw.data.model.TaskDataManager
import com.xiaomo.androidforclaw.providers.UnifiedLLMProvider
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

/**
 * Camera Skill 端到端测试
 *
 * 流程：
 * 1. 向 AgentLoop 发送"拍照看看有什么"
 * 2. Agent 调用 eye skill（action=look）
 * 3. Agent 根据拍到的照片描述内容
 * 4. 验证：使用了 camera 工具 + 最终输出有实质内容（非错误）= 通过
 *
 * 运行:
 * ./gradlew :app:connectedDebugAndroidTest \
 *   -Pandroid.testInstrumentationRunnerArguments.class=com.xiaomo.androidforclaw.e2e.CameraE2ETest
 *
 * ⚠️ 前置条件:
 * - 真机（有摄像头）
 * - 已授予 CAMERA 权限
 * - 已配置 LLM API Key
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class CameraE2ETest {

    companion object {
        private const val TAG = "CameraE2E"
        private const val TIMEOUT_MS = 120_000L // 2 分钟超时（拍照 + LLM 响应）
    }

    private lateinit var context: Context
    private lateinit var llmProvider: UnifiedLLMProvider
    private lateinit var toolRegistry: ToolRegistry
    private lateinit var androidToolRegistry: AndroidToolRegistry
    private lateinit var configLoader: ConfigLoader
    private lateinit var contextBuilder: ContextBuilder

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext<MyApplication>()
        configLoader = ConfigLoader(context)
        llmProvider = UnifiedLLMProvider(context)
        val taskDataManager = TaskDataManager.getInstance()
        toolRegistry = ToolRegistry(context, taskDataManager)
        androidToolRegistry = AndroidToolRegistry(
            context = context,
            taskDataManager = taskDataManager,
            cameraCaptureManager = MyApplication.getCameraCaptureManager(),
        )
        contextBuilder = ContextBuilder(context, toolRegistry, androidToolRegistry, configLoader)
    }

    /**
     * 核心测试：发送"拍照看看有什么"，验证 Agent 调用 camera 并描述照片内容
     */
    @Test
    fun test_cameraSnap_describeContent() {
        val agentLoop = AgentLoop(
            llmProvider = llmProvider,
            toolRegistry = toolRegistry,
            androidToolRegistry = androidToolRegistry,
            maxIterations = 10,
            configLoader = configLoader
        )

        val systemPrompt = contextBuilder.buildSystemPrompt(
            promptMode = PromptMode.FULL
        )

        val result = runBlocking {
            withTimeout(TIMEOUT_MS) {
                agentLoop.run(
                    systemPrompt = systemPrompt,
                    userMessage = "拍照看看有什么",
                    reasoningEnabled = false
                )
            }
        }

        // 打印报告
        println("═".repeat(60))
        println("📊 Camera E2E 测试报告")
        println("═".repeat(60))
        println("🔄 迭代次数: ${result.iterations}")
        println("🔧 使用工具: ${result.toolsUsed.joinToString(", ")}")
        println("📄 最终输出: ${result.finalContent.take(500)}")
        println("═".repeat(60))

        // 验证 1: 使用了 eye 工具
        assertTrue(
            "Agent 应该调用 eye 工具，实际使用: ${result.toolsUsed}",
            result.toolsUsed.any { it == "eye" }
        )

        // 验证 2: 最终输出有实质内容（不是空的，也不是纯错误信息）
        assertTrue(
            "最终输出不应为空",
            result.finalContent.isNotBlank()
        )

        // 验证 3: 输出不是权限错误（说明拍照成功了）
        assertFalse(
            "不应该是权限错误（请先在设备上授予 CAMERA 权限）",
            result.finalContent.contains("CAMERA_PERMISSION_REQUIRED")
        )

        // 验证 4: 输出不是通用错误
        val isError = result.finalContent.contains("UNAVAILABLE") &&
            !result.finalContent.contains("拍") // 排除正常描述中偶尔出现的词
        assertFalse(
            "拍照不应失败: ${result.finalContent.take(200)}",
            isError
        )

        // 验证 5: Agent 应该描述了照片内容（有实质性描述）
        // 只要输出长度 > 10 且不是纯错误，就认为 Agent 描述了内容
        assertTrue(
            "Agent 应该描述照片内容，实际输出长度: ${result.finalContent.length}",
            result.finalContent.length > 10
        )

        println("✅ Camera E2E 测试通过！Agent 成功拍照并描述了内容。")
    }
}
