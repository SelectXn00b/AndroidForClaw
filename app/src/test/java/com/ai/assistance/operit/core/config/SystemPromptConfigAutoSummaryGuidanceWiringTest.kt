package com.ai.assistance.operit.core.config

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * R-AGENT-014 agent 侧接线契约（2026-06-07）：
 *
 * **背景**：R-AGENT-013 让 APP 内每次对话的自动摘要强行写入 `MemoryRepository` 并打
 * `#auto_summary` + `#chat:<chatId>` 两个 tag，但 agent 完全不知道这个 tag 的存在。
 * `SystemPromptConfig.GATEWAY_AWARENESS_EN/CN` 内的 `MEMORY USAGE GUIDANCE` /
 * `记忆库使用指导` 段只提到 `query_memory` / `create_memory` / `#persistent_instruction`，
 * 没有任何关于 `#auto_summary` 的提示——agent 不会想起来"我可以查最近的对话摘要"。
 *
 * R-AGENT-014 要求 MEMORY USAGE GUIDANCE 段显式告诉 agent：
 *   1. 对话摘要会自动保存到记忆库并打 `#auto_summary` tag
 *   2. 用 `query_memory` 的 `tags` 参数（`tags=#auto_summary` 或 `tags=#chat:<chatId>`）
 *      可以精准检索这些摘要
 *
 * **测试策略**：与 R-AGENT-009 `PersistentInstructionAgentHintTest` 同策略 —— 源码字符串扫描守
 * wiring。`SystemPromptConfig` 是纯 const string，没法走 runtime 断言。
 *
 * 对应 TC-AGENT-014-a / TC-AGENT-014-b（见 docs/hermes-test-cases.md）。
 */
class SystemPromptConfigAutoSummaryGuidanceWiringTest {

    private val source: String by lazy { File(systemPromptConfigPath()).readText() }

    /**
     * TC-AGENT-014-a: `GATEWAY_AWARENESS_EN` 常量内的 `MEMORY USAGE GUIDANCE` 段必须显式
     * mention `#auto_summary` tag + `query_memory` 工具 + `tags=` 用法示例。
     */
    @Test
    fun `TC-AGENT-014-a english guidance mentions auto_summary tag`() {
        val enBlock = extractBetween(source, "GATEWAY_AWARENESS_EN", "GATEWAY_AWARENESS_CN")
            ?: error("找不到 GATEWAY_AWARENESS_EN 块，SystemPromptConfig 可能被重构")

        assertTrue(
            "GATEWAY_AWARENESS_EN 的 MEMORY USAGE GUIDANCE 段必须显式提到 #auto_summary tag —— " +
                "否则 agent 不知道自动摘要存在哪里。R-AGENT-014 agent 侧接线断链。",
            enBlock.contains("#auto_summary")
        )

        assertTrue(
            "GATEWAY_AWARENESS_EN 的 MEMORY USAGE GUIDANCE 段必须 reference query_memory —— " +
                "告诉 agent 用哪个工具去查摘要。",
            enBlock.contains("query_memory")
        )

        assertTrue(
            "GATEWAY_AWARENESS_EN 的 MEMORY USAGE GUIDANCE 段必须含 tags= 用法示例 —— " +
                "agent 才知道 query_memory 有 tags 参数可用。",
            enBlock.contains("tags=") || enBlock.contains("tags =")
        )
    }

    /**
     * TC-AGENT-014-b: `GATEWAY_AWARENESS_CN` 常量内的 `记忆库使用指导` 段必须挂上对应中文指引，
     * 与英文版语义对齐（#auto_summary + query_memory + tags=）。
     */
    @Test
    fun `TC-AGENT-014-b chinese guidance mentions auto_summary tag`() {
        val cnBlockStart = source.indexOf("GATEWAY_AWARENESS_CN")
        assertTrue("找不到 GATEWAY_AWARENESS_CN 定义", cnBlockStart >= 0)
        // 取后 3000 chars 作为 inspection 窗口（CN 块包含 MEMORY GUIDANCE + WORKSPACE FILES 等子段）
        val cnBlock = source.substring(cnBlockStart, (cnBlockStart + 3000).coerceAtMost(source.length))

        assertTrue(
            "GATEWAY_AWARENESS_CN 的记忆库使用指导段必须显式提到 #auto_summary tag —— " +
                "中文版与英文版语义必须对齐。",
            cnBlock.contains("#auto_summary")
        )

        assertTrue(
            "GATEWAY_AWARENESS_CN 的记忆库使用指导段必须 reference query_memory —— " +
                "告诉 agent 用哪个工具去查摘要。",
            cnBlock.contains("query_memory")
        )

        assertTrue(
            "GATEWAY_AWARENESS_CN 的记忆库使用指导段必须含 tags= 用法示例 —— " +
                "agent 才知道 query_memory 有 tags 参数可用。",
            cnBlock.contains("tags=") || cnBlock.contains("tags =")
        )
    }

    // ----- helpers -----

    private fun extractBetween(src: String, startMarker: String, endMarker: String): String? {
        val startIdx = src.indexOf(startMarker)
        if (startIdx < 0) return null
        val endIdx = src.indexOf(endMarker, startIdx + startMarker.length)
        if (endIdx < 0) return null
        return src.substring(startIdx, endIdx)
    }

    private fun appSrcMainRoot(): File {
        val candidate = File("src/main/java/com/ai/assistance/operit")
        if (candidate.exists()) return candidate
        val alt = File("app/src/main/java/com/ai/assistance/operit")
        if (alt.exists()) return alt
        error("Cannot locate app/src/main/java/com/ai/assistance/operit — cwd=${File(".").absolutePath}")
    }

    private fun systemPromptConfigPath(): String =
        File(appSrcMainRoot(), "core/config/SystemPromptConfig.kt").path
}
