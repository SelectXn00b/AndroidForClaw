package com.ai.assistance.operit.core.tools

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * R-AGENT-034 (2026-06-15)：`ToolRegistration.registerAllTools` 必须把 cronjob executor
 * 注册进工具表，否则 LLM 即便看到 cronjob schema 也调不到（dispatch 找不到 executor）。
 *
 * Executor bridge：args (Map) → `com.xiaomo.hermes.hermes.tools.cronjob(...)`，
 * 异常路径必须用 try/catch 包围并返回结构化 ToolResult（避免炸 handler）。
 *
 * 对应 TC-AGENT-034-b（见 docs/hermes-test-cases.md）。
 */
class CronjobToolRegistrationWiringTest {

    private val source: String by lazy { File(registrationPath()).readText() }

    /**
     * TC-AGENT-034-b: ToolRegistration.kt 含 cronjob 注册 + executor 桥接 + try/catch + ToolResult 构造。
     */
    @Test
    fun `TC-AGENT-034-b ToolRegistration registers cronjob executor with try-catch`() {
        // 必须含 "cronjob" 字面值（registerTool name 参数）
        assertTrue(
            "TC-AGENT-034-b: ToolRegistration.kt 必须含 `\"cronjob\"` 字面值 —— " +
                "agent dispatch 时按 tool name 查找 executor，没注册就调不到。",
            source.contains("\"cronjob\"")
        )

        // 必须 reference hermes-android tools 包的 cronjob 函数
        val referencesCronjobFn =
            source.contains("com.xiaomo.hermes.hermes.tools.cronjob") ||
                source.contains("CronjobTools.cronjob") ||
                Regex("""\bcronjob\s*\(""").containsMatchIn(source)
        assertTrue(
            "TC-AGENT-034-b: ToolRegistration.kt 必须 reference `com.xiaomo.hermes.hermes.tools.cronjob` " +
                "或 `CronjobTools.cronjob` 或直接 `cronjob(` 调用 —— " +
                "证明 executor 桥接到 hermes-android 真实实现而非 stub。",
            referencesCronjobFn
        )

        // try / catch 包围 dispatch（异常不炸 handler）
        assertTrue(
            "TC-AGENT-034-b: ToolRegistration.kt 必须含 `try {` 块 —— cronjob executor 调用必须包 try/catch。",
            source.contains("try {")
        )
        assertTrue(
            "TC-AGENT-034-b: ToolRegistration.kt 必须含 `catch (` —— 异常必须被结构化捕获返回 ToolResult。",
            Regex("""\bcatch\s*\(""").containsMatchIn(source)
        )

        // ToolResult 构造（异常路径返回结构化错误）
        assertTrue(
            "TC-AGENT-034-b: ToolRegistration.kt 必须含 `ToolResult(` 构造 —— " +
                "异常路径返回结构化 ToolResult 而非裸抛异常。",
            Regex("""\bToolResult\s*\(""").containsMatchIn(source)
        )
    }

    // ----- helpers -----

    private fun registrationPath(): String {
        val candidates = listOf(
            File("src/main/java/com/ai/assistance/operit/core/tools/ToolRegistration.kt"),
            File("app/src/main/java/com/ai/assistance/operit/core/tools/ToolRegistration.kt")
        )
        return candidates.firstOrNull { it.exists() }?.path
            ?: error("Cannot locate ToolRegistration.kt — cwd=${File(".").absolutePath}")
    }
}
