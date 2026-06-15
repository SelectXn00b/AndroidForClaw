package com.ai.assistance.operit.core.config

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * R-AGENT-034 (2026-06-15)：`SystemToolPrompts.getAIAllCategoriesEn/Cn` 硬编码 4 个 category
 * （basic/file/http/memory）漏了 cronjob —— agent system prompt 提到工具名但 OpenAI tools array
 * 不下发 schema → dispatch 时 LLM 根本不会调用。本测试守 cronjob category 已追加到两个语言函数。
 *
 * 对应 TC-AGENT-034-a / TC-AGENT-034-c（见 docs/hermes-test-cases.md）。
 */
class SystemToolPromptsCronjobWiringTest {

    private val source: String by lazy { File(promptsPath()).readText() }

    /**
     * TC-AGENT-034-a: cronjob 字面值 + CRONJOB_SCHEMA 引用都在两个 categories 函数里出现。
     */
    @Test
    fun `TC-AGENT-034-a cronjob category appears in EN and CN tool registries`() {
        // EN / CN 两个函数都至少 mention "cronjob" 一次
        assertTrue(
            "TC-AGENT-034-a: SystemToolPrompts.kt 必须含 `cronjob` 字面值 —— " +
                "agent OpenAI tools array 否则不下发 cronjob schema，LLM 根本不会调用。",
            source.contains("cronjob")
        )

        // 必须 reference CRONJOB_SCHEMA（来自 hermes-android tools 包）或等价 schema-list 引用
        // 既有的 4 个 category 是 ToolCategory 风格，本 R 至少要把 cronjob schema 接进去
        assertTrue(
            "TC-AGENT-034-a: SystemToolPrompts.kt 必须 reference `CRONJOB_SCHEMA` —— " +
                "证明把 hermes-android/.../tools/CronjobTools.kt 的 CRONJOB_SCHEMA 接到 prompt 注册里。\n" +
                "（或等价方式：直接构造同形 schema 字面值，但 CRONJOB_SCHEMA 复用更合规。）",
            source.contains("CRONJOB_SCHEMA")
        )
    }

    /**
     * TC-AGENT-034-c (红线): 既有 4 个 category 不被误删（追加而非替换）。
     */
    @Test
    fun `TC-AGENT-034-c existing 4 categories preserved`() {
        for (category in listOf("basicTools", "fileSystemTools", "httpTools", "memoryTools")) {
            assertTrue(
                "TC-AGENT-034-c 红线: SystemToolPrompts.kt 必须仍含 `$category` —— " +
                    "本 R 是**追加** cronjob category 而非替换既有 4 个。\n" +
                    "（若该变量名在重构中已被改，请同步更新此 TC 期望值。）",
                source.contains(category)
            )
        }
    }

    // ----- helpers -----

    private fun promptsPath(): String {
        val candidates = listOf(
            File("src/main/java/com/ai/assistance/operit/core/config/SystemToolPrompts.kt"),
            File("app/src/main/java/com/ai/assistance/operit/core/config/SystemToolPrompts.kt")
        )
        return candidates.firstOrNull { it.exists() }?.path
            ?: error("Cannot locate SystemToolPrompts.kt — cwd=${File(".").absolutePath}")
    }
}
