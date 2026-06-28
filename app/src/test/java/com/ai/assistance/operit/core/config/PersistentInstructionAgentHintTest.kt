package com.ai.assistance.operit.core.config

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * R-AGENT-009 agent 侧接线契约（commit 2）：
 *
 * **背景**：commit 1 完成了 ConversationService 读侧注入 + MemoryLibrary 合并保护，但 agent
 * 自己并不知道用户说\"以后用 Markdown\"这类话时应该主动 create_memory 并打 `#persistent_instruction`
 * tag。R-AGENT-009 验收要求\"agent 能自主把用户的持久规则落到带 tag 的 Memory 节点\"，靠两处
 * prompt 指引：
 *
 *   1. `SystemToolPromptsInternal` 里 `create_memory` 工具的 description 必须显式说明持久
 *      规则需要带 `#persistent_instruction` tag（EN + CN 两套都要）。
 *   2. `SystemPromptConfig` 的 `MEMORY USAGE GUIDANCE` / `记忆库使用指导` 必须有相应的
 *      触发指引，告诉 agent 什么场景下要走持久指令路径（覆盖原来\"无需手动保存\"那条全局指令
 *      留下的契约空洞）。
 *
 * **测试策略**：跟 PersistentInstructionInjectionTest 一样走源码字符串扫描——这两个文件是
 * 纯 const string，没法走 runtime 断言。
 *
 * 对应 TC-AGENT-245-a/b/c、TC-AGENT-285-a/b（见 docs/hermes-test-cases.md）。
 */
class PersistentInstructionAgentHintTest {

    /**
     * TC-AGENT-245-a: SystemToolPromptsInternal 的 EN + CN `create_memory` 描述都必须
     * 显式 mention `#persistent_instruction` tag 与持久规则触发场景。
     */
    @Test
    fun `TC-AGENT-245-a create_memory descriptions instruct agent to use persistent_instruction tag`() {
        val source = File(toolPromptsPath()).readText()

        // 抽 EN create_memory 块（搜 name = "create_memory" 后取 400 chars）
        val createMemoryOccurrences = Regex("""name\s*=\s*"create_memory"""").findAll(source).toList()
        assertTrue(
            "SystemToolPromptsInternal 至少要有 EN + CN 两处 create_memory ToolPrompt 定义，" +
                "实际找到 ${createMemoryOccurrences.size} 处。",
            createMemoryOccurrences.size >= 2
        )

        createMemoryOccurrences.forEachIndexed { idx, match ->
            val window = source.substring(
                match.range.first,
                (match.range.first + 1500).coerceAtMost(source.length)
            )
            assertTrue(
                "第 ${idx + 1} 处 create_memory 的 description 必须 mention `#persistent_instruction` —— " +
                    "否则 agent 没有 hint 知道持久规则要打这个 tag。R-AGENT-009 agent 侧接线断链。",
                window.contains("#persistent_instruction")
            )
        }
    }

    /**
     * TC-AGENT-245-b: SystemPromptConfig 的 MEMORY USAGE GUIDANCE (EN) 必须含
     * `#persistent_instruction` 和持久规则相关词，且不能保留原来\"you do not need to save
     * memories manually\"这条无限制的全局指令——必须挂一个例外条款。
     */
    @Test
    fun `TC-AGENT-245-b EN memory usage guidance mentions persistent_instruction with exception clause`() {
        val source = File(systemPromptConfigPath()).readText()

        // 抽 GATEWAY_AWARENESS_EN 块（包含 MEMORY USAGE GUIDANCE 子节）
        val enBlock = extractBetween(source, "GATEWAY_AWARENESS_EN", "GATEWAY_AWARENESS_CN")
            ?: error("找不到 GATEWAY_AWARENESS_EN 块，SystemPromptConfig 可能被重构")

        assertTrue(
            "GATEWAY_AWARENESS_EN 的 MEMORY USAGE GUIDANCE 段必须显式提到 #persistent_instruction tag。",
            enBlock.contains("#persistent_instruction")
        )

        // \"automatically updated / you do not need to save memories manually\" 这条全局指令
        // 必须有一个明确的例外/EXCEPTION 限定词在附近——否则与新加的\"must call create_memory\"指令相互矛盾。
        assertTrue(
            "GATEWAY_AWARENESS_EN 的 MEMORY USAGE GUIDANCE 段必须包含 EXCEPTION 或等价的限定词，" +
                "把\"无需手动保存\"这条与新的\"持久规则必须主动保存\"指令分开。",
            enBlock.contains("EXCEPTION") || enBlock.contains("Exception")
        )
    }

    /**
     * TC-AGENT-245-c: SystemPromptConfig 的 CN 记忆库使用指导必须挂上对应中文指引。
     */
    @Test
    fun `TC-AGENT-245-c CN memory usage guidance mentions persistent_instruction with exception clause`() {
        val source = File(systemPromptConfigPath()).readText()

        // 抽 GATEWAY_AWARENESS_CN 块
        val cnBlockStart = source.indexOf("GATEWAY_AWARENESS_CN")
        assertTrue("找不到 GATEWAY_AWARENESS_CN 定义", cnBlockStart >= 0)
        // 取后 2000 chars 作为 inspection 窗口
        val cnBlock = source.substring(cnBlockStart, (cnBlockStart + 2000).coerceAtMost(source.length))

        assertTrue(
            "GATEWAY_AWARENESS_CN 的记忆库使用指导段必须显式提到 #persistent_instruction tag。",
            cnBlock.contains("#persistent_instruction")
        )

        assertTrue(
            "GATEWAY_AWARENESS_CN 的记忆库使用指导段必须包含\"例外\"或\"主动\"等限定词，" +
                "把\"无需手动保存\"与\"持久规则必须主动保存\"指令分开。",
            cnBlock.contains("例外") || cnBlock.contains("主动调用")
        )
    }

    // ===== TC-AGENT-285-a/b: R-AGENT-046 agent 心智模型必须 mention trigger_keywords =====

    /**
     * TC-AGENT-285-a: GATEWAY_AWARENESS_EN 的 MEMORY USAGE GUIDANCE 必须 mention
     * `trigger_keywords` —— 让 agent 知道创建 `#persistent_instruction` 时优先填这个字段，
     * 且缺省=每轮注入（向后兼容）。
     */
    @Test
    fun `TC-AGENT-285-a EN guidance mentions trigger_keywords field`() {
        val source = File(systemPromptConfigPath()).readText()
        val enBlock = extractBetween(source, "GATEWAY_AWARENESS_EN", "GATEWAY_AWARENESS_CN")
            ?: error("找不到 GATEWAY_AWARENESS_EN 块")

        assertTrue(
            "GATEWAY_AWARENESS_EN 必须 mention `trigger_keywords` —— 否则 agent 不会知道 R-AGENT-046 " +
                "新字段的存在，新创建的 #persistent_instruction 仍会沿用旧行为（每轮全量注入）。",
            enBlock.contains("trigger_keywords")
        )
        // 既要让 agent 知道这个字段，也要让它理解\"缺省=旧行为\"——避免 agent 错以为不填会丢失规则
        assertTrue(
            "GATEWAY_AWARENESS_EN 关于 trigger_keywords 的指引必须说明\"缺省 / 省略 / legacy / every turn\" " +
                "等向后兼容含义；否则 agent 看到新字段后可能误以为不填会被丢弃。",
            enBlock.contains("Legacy") || enBlock.contains("legacy") ||
                enBlock.contains("every turn") || enBlock.contains("Omit")
        )
    }

    /**
     * TC-AGENT-285-b: GATEWAY_AWARENESS_CN 的记忆库使用指导同上。
     */
    @Test
    fun `TC-AGENT-285-b CN guidance mentions trigger_keywords field`() {
        val source = File(systemPromptConfigPath()).readText()
        val cnBlockStart = source.indexOf("GATEWAY_AWARENESS_CN")
        assertTrue("找不到 GATEWAY_AWARENESS_CN 定义", cnBlockStart >= 0)
        val cnBlock = source.substring(cnBlockStart, (cnBlockStart + 3000).coerceAtMost(source.length))

        assertTrue(
            "GATEWAY_AWARENESS_CN 必须 mention `trigger_keywords` —— 否则 agent 不会知道 R-AGENT-046 " +
                "新字段的存在。",
            cnBlock.contains("trigger_keywords")
        )
        // 中文 prompt 里同样要说明缺省语义
        assertTrue(
            "GATEWAY_AWARENESS_CN 关于 trigger_keywords 的指引必须说明\"老条目 / 缺省 / 不填 / 每轮注入\" " +
                "等向后兼容含义。",
            cnBlock.contains("老的") || cnBlock.contains("缺省") || cnBlock.contains("不填") ||
                cnBlock.contains("每轮注入") || cnBlock.contains("向后兼容")
        )
    }

    // ----- helpers -----

    /** 抽两个 marker 之间的内容（含 startMarker 那行） */
    private fun extractBetween(source: String, startMarker: String, endMarker: String): String? {
        val startIdx = source.indexOf(startMarker)
        if (startIdx < 0) return null
        val endIdx = source.indexOf(endMarker, startIdx + startMarker.length)
        if (endIdx < 0) return null
        return source.substring(startIdx, endIdx)
    }

    private fun appSrcMainRoot(): File {
        val candidate = File("src/main/java/com/ai/assistance/operit")
        if (candidate.exists()) return candidate
        val alt = File("app/src/main/java/com/ai/assistance/operit")
        if (alt.exists()) return alt
        error("Cannot locate app/src/main/java/com/ai/assistance/operit — cwd=${File(".").absolutePath}")
    }

    private fun toolPromptsPath(): String =
        File(appSrcMainRoot(), "core/config/SystemToolPromptsInternal.kt").path

    private fun systemPromptConfigPath(): String =
        File(appSrcMainRoot(), "core/config/SystemPromptConfig.kt").path
}
