package com.ai.assistance.operit.core.tools.system

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * R-TOOL-016 — `launch_app` 工具 wiring 契约。
 *
 * `launch_app` 通过 shell `monkey -p <pkg> -c android.intent.category.LAUNCHER 1`
 * 绕过 Android 10+ 的 background activity launch (BAL) 限制启动其他 APP，作为
 * `start_app` 的兜底。本测试类用**源码字符串扫描**把 5 个接入点固化下来，避免下次
 * "顺手清理" 把 monkey 路径砍掉（历史上有人把 start_app 从 monkey 改回 am start，
 * 见 DebuggerSystemOperationTools.kt:310 注释）。
 *
 * 对应 TC-TOOL-400-a..f（见 docs/hermes-test-cases.md）。
 *
 * 不用 JVM Robolectric 真跑 AndroidShellExecutor 的理由：它会 fork 子进程（或走
 * Shizuku binder），JVM 单测里 mock 起来收益低；这里关心的回归是 wiring 本身。
 */
class LaunchAppToolTest {

    /** TC-TOOL-400-a: StandardSystemOperationTools.launchApp 走 monkey + LAUNCHER。 */
    @Test
    fun `TC-TOOL-400-a standard tool uses monkey LAUNCHER`() {
        val source = File(standardSystemOperationToolsPath()).readText()
        assertTrue(
            "StandardSystemOperationTools 必须定义 launchApp(tool)",
            source.contains("fun launchApp(tool: AITool)")
        )
        assertTrue(
            "launchApp 必须调用 AndroidShellExecutor.executeShellCommand",
            source.contains("AndroidShellExecutor.executeShellCommand(")
        )
        assertTrue(
            "launchApp 命令必须使用 monkey -p <pkg> -c android.intent.category.LAUNCHER 1（BAL 绕开路径）",
            Regex(
                """monkey -p \$\{?packageName\}? -c android\.intent\.category\.LAUNCHER 1"""
            ).containsMatchIn(source) ||
                source.contains("monkey -p \$packageName -c android.intent.category.LAUNCHER 1")
        )
    }

    /**
     * TC-TOOL-400-b: DebuggerSystemOperationTools 通过继承复用 launchApp。
     *
     * 设计选择：DEBUGGER / ROOT / ADMIN / ACCESSIBILITY 都继承自 Standard，不重写
     * launchApp 即可——Shell 命令本身相同，权限差异由 [ShellExecutorFactory] 自动路由
     * 到 `DebuggerShellExecutor`（走 Shizuku binder，shell uid 突破 BAL）。
     *
     * 所以这条测试反向防呆：Debugger 不应**重新实现**一个偏离 monkey 路径的 launchApp。
     */
    @Test
    fun `TC-TOOL-400-b debugger tool inherits launchApp via Standard`() {
        val source = File(debuggerSystemOperationToolsPath()).readText()
        // 不要在 Debugger 层重写 launchApp 把命令改成 am start——那会让 BAL 兜底彻底失效。
        val overrideRegex = Regex("""override\s+suspend\s+fun\s+launchApp\s*\(""")
        if (overrideRegex.containsMatchIn(source)) {
            // 如果未来确实要 override，必须保留 monkey 关键字。
            assertTrue(
                "DebuggerSystemOperationTools 若重写 launchApp，必须仍走 monkey LAUNCHER 路径",
                source.contains("monkey") && source.contains("android.intent.category.LAUNCHER")
            )
        }
    }

    /** TC-TOOL-400-c: ToolRegistration 注册 launch_app 且 executor 委派 launchApp。 */
    @Test
    fun `TC-TOOL-400-c registration wires launch_app to launchApp`() {
        val source = File(toolRegistrationPath()).readText()
        assertTrue(
            "ToolRegistration 必须注册 name = \"launch_app\"",
            source.contains("name = \"launch_app\"")
        )
        assertTrue(
            "ToolRegistration 的 launch_app executor 必须委派 systemOperationTools.launchApp",
            source.contains("systemOperationTools.launchApp(tool)")
        )
        assertTrue(
            "ToolRegistration 必须引用本地化字符串 R.string.toolreg_launch_app_desc",
            source.contains("R.string.toolreg_launch_app_desc")
        )
    }

    /** TC-TOOL-400-d: SystemToolPromptsInternal EN+CN 双侧都声明了 launch_app。 */
    @Test
    fun `TC-TOOL-400-d prompt declares launch_app with BAL hint`() {
        val source = File(systemToolPromptsInternalPath()).readText()
        val launchAppPromptCount = Regex("""name\s*=\s*"launch_app"""").findAll(source).count()
        assertTrue(
            "SystemToolPromptsInternal 必须双语（EN + CN）各声明一条 launch_app ToolPrompt，实际 $launchAppPromptCount",
            launchAppPromptCount == 2
        )
        // 描述里必须出现 BAL / monkey 关键词，告知模型这是兜底工具
        assertTrue(
            "launch_app 的 EN 描述里必须含 BAL（让模型知道何时用它）",
            source.contains("BAL")
        )
        assertTrue(
            "launch_app 的 EN 描述里必须含 monkey（让模型知道实现路径与潜在副作用）",
            source.contains("monkey")
        )
    }

    /**
     * TC-TOOL-400-e: 反向防呆——start_app 与 launch_app 必须并存。
     *
     * 失败原因（防回归）：早期版本试图直接把 start_app 改成走 monkey，结果触发屏幕方向锁
     * 等副作用被回滚（DebuggerSystemOperationTools.kt:310）。现在的策略是两个工具并存，
     * start_app 保持 intent 路径（干净），launch_app 走 monkey（兜底）。
     */
    @Test
    fun `TC-TOOL-400-e start_app still registered alongside launch_app`() {
        val toolRegSource = File(toolRegistrationPath()).readText()
        assertTrue(
            "start_app 必须仍然注册（不能被 launch_app 替换）",
            toolRegSource.contains("name = \"start_app\"")
        )
        assertTrue(
            "stop_app 必须仍然注册（防止 launch_app 编辑误删 stop_app）",
            toolRegSource.contains("name = \"stop_app\"")
        )

        // prompt 里 launch_app 的描述不能错误地告诉模型 "替代 start_app"
        val promptSource = File(systemToolPromptsInternalPath()).readText()
        assertFalse(
            "launch_app 的 EN 描述不应说 'replaces start_app'（应是兜底关系而非替换）",
            promptSource.contains("replaces start_app")
        )
        assertFalse(
            "launch_app 的 EN 描述不应说 'instead of start_app'",
            promptSource.contains("instead of start_app")
        )
    }

    /** TC-TOOL-400-f: i18n 资源完整——多语言 strings.xml 都要有 toolreg_launch_app_desc。 */
    @Test
    fun `TC-TOOL-400-f localized strings exist for all locales`() {
        val locales = listOf("values", "values-en", "values-pt-rBR", "values-ms", "values-id")
        val missing = mutableListOf<String>()
        for (locale in locales) {
            val file = File(appMainRoot(), "res/$locale/strings.xml")
            if (!file.exists()) continue
            val content = file.readText()
            if (!content.contains("toolreg_launch_app_desc")) {
                missing.add(locale)
            }
        }
        assertTrue(
            "以下 locale 缺失 toolreg_launch_app_desc: $missing",
            missing.isEmpty()
        )
    }

    // ----- helpers -----

    private fun appSrcMainRoot(): File {
        val candidate = File("src/main/java/com/ai/assistance/operit")
        if (candidate.exists()) return candidate
        val alt = File("app/src/main/java/com/ai/assistance/operit")
        if (alt.exists()) return alt
        error("Cannot locate app/src/main/java/com/ai/assistance/operit — cwd=${File(".").absolutePath}")
    }

    private fun appMainRoot(): File {
        val candidate = File("src/main")
        if (candidate.exists()) return candidate
        val alt = File("app/src/main")
        if (alt.exists()) return alt
        error("Cannot locate app/src/main — cwd=${File(".").absolutePath}")
    }

    private fun standardSystemOperationToolsPath(): String =
        File(appSrcMainRoot(), "core/tools/defaultTool/standard/StandardSystemOperationTools.kt").path

    private fun debuggerSystemOperationToolsPath(): String =
        File(appSrcMainRoot(), "core/tools/defaultTool/debugger/DebuggerSystemOperationTools.kt").path

    private fun toolRegistrationPath(): String =
        File(appSrcMainRoot(), "core/tools/ToolRegistration.kt").path

    private fun systemToolPromptsInternalPath(): String =
        File(appSrcMainRoot(), "core/config/SystemToolPromptsInternal.kt").path
}
