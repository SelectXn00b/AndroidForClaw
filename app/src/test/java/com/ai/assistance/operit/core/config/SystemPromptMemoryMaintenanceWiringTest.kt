package com.ai.assistance.operit.core.config

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * R-AGENT-017 (2026-06-08)：让 agent 知道自己有 memory 维护职责。
 *
 * **背景**：R-AGENT-015 + 016 解决了"能存能用"，但 `SystemPromptConfig.kt` 的
 * `MEMORY USAGE GUIDANCE` / `记忆库使用指导` 段把 agent 角色定位成"查询者 + persistent_instruction
 * setter"——L61 英文 `"automatically updated by a background system ... you do not need to save
 * memories manually"` / L86 中文「无需你手动保存」**显式劝退** agent 主动维护 fact。即使
 * `update_memory` / `delete_memory` / `link_memories` 工具早已注册，agent 几乎不会主动用。
 *
 * R-AGENT-017 在两段 prompt 各加 2 条 bullet（删/替换老堵路句），告诉 agent："旧 fact 可能
 * 过时或矛盾时主动 update / link contradicts / delete；记忆库是你的长期资产，矛盾解决和一致性
 * 维护归你管"。
 *
 * **测试策略**：跟 PersistentInstructionAgentHintTest 同策略——源码字符串扫描守 prompt 关键
 * 字面值。运行时正确性靠手测兜底（agent 主动调 update_memory 的概率取决于模型）。
 *
 * **关键约束（呼应用户决策）**：告诉 agent "能力 + 责任"，不告诉 agent "机制"（不能出现
 * `auto_extracted` 字面值，防 R-AGENT-016 抽取机制泄漏 → prompt 污染）。
 *
 * 对应 TC-AGENT-017-a..g（见 docs/hermes-test-cases.md）。
 */
class SystemPromptMemoryMaintenanceWiringTest {

    private val source: String by lazy { File(systemPromptConfigPath()).readText() }

    private val enBlock: String by lazy {
        extractBetween(source, "GATEWAY_AWARENESS_EN", "GATEWAY_AWARENESS_CN")
            ?: error("找不到 GATEWAY_AWARENESS_EN 块，SystemPromptConfig 可能被重构")
    }

    private val cnBlock: String by lazy {
        // CN 块从 GATEWAY_AWARENESS_CN 开始，到下一个 TOOL_USAGE_GUIDELINES_EN 为止
        extractBetween(source, "GATEWAY_AWARENESS_CN", "TOOL_USAGE_GUIDELINES_EN")
            ?: error("找不到 GATEWAY_AWARENESS_CN 块，SystemPromptConfig 可能被重构")
    }

    /**
     * TC-AGENT-017-a: 英文 prompt 必须点名三个维护工具。
     */
    @Test
    fun `TC-AGENT-017-a english prompt names three maintenance tools`() {
        assertTrue(
            "GATEWAY_AWARENESS_EN 的 MEMORY USAGE GUIDANCE 段必须 mention `update_memory` —— " +
                "agent 不知道这个工具存在就不会主动维护。\n实际块（前 2500 chars）:\n${enBlock.take(2500)}",
            enBlock.contains("update_memory")
        )
        assertTrue(
            "GATEWAY_AWARENESS_EN 必须 mention `link_memories` —— 矛盾发现路径之一。",
            enBlock.contains("link_memories")
        )
        assertTrue(
            "GATEWAY_AWARENESS_EN 必须 mention `delete_memory` —— 过时 fact 删除路径。",
            enBlock.contains("delete_memory")
        )
    }

    /**
     * TC-AGENT-017-b: 英文 prompt 必须含 `contradicts` 字面值 + "维护责任"语义。
     */
    @Test
    fun `TC-AGENT-017-b english prompt mentions contradicts and maintenance duty`() {
        assertTrue(
            "GATEWAY_AWARENESS_EN 必须含 `contradicts` 字面值（鼓励 link_type=\"contradicts\" 的 hint）。",
            enBlock.contains("contradicts")
        )
        val hasMaintenanceDuty =
            enBlock.contains("conflict resolution") ||
                enBlock.contains("consistency maintenance")
        assertTrue(
            "GATEWAY_AWARENESS_EN 必须含 `conflict resolution` 或 `consistency maintenance` 任一字面值 —— " +
                "把\"维护责任\"语义下沉到 prompt。",
            hasMaintenanceDuty
        )
    }

    /**
     * TC-AGENT-017-c: 英文 prompt 不得再含老的堵路句 `you do not need to save memories manually`。
     */
    @Test
    fun `TC-AGENT-017-c english prompt removes do-not-save-manually anti-pattern`() {
        assertFalse(
            "GATEWAY_AWARENESS_EN 不得再含 `you do not need to save memories manually` —— " +
                "这句堵路老句子会直接抵消 R-AGENT-017 的维护语义。必须删/替换。",
            enBlock.contains("you do not need to save memories manually")
        )
    }

    /**
     * TC-AGENT-017-d: 中文 prompt 必须点名三个维护工具。
     */
    @Test
    fun `TC-AGENT-017-d chinese prompt names three maintenance tools`() {
        assertTrue(
            "GATEWAY_AWARENESS_CN 必须 mention `update_memory`。",
            cnBlock.contains("update_memory")
        )
        assertTrue(
            "GATEWAY_AWARENESS_CN 必须 mention `link_memories`。",
            cnBlock.contains("link_memories")
        )
        assertTrue(
            "GATEWAY_AWARENESS_CN 必须 mention `delete_memory`。",
            cnBlock.contains("delete_memory")
        )
    }

    /**
     * TC-AGENT-017-e: 中文 prompt 必须含 `contradicts` + 「矛盾」+（「维护」或「职责」）任一。
     */
    @Test
    fun `TC-AGENT-017-e chinese prompt mentions contradiction and maintenance duty`() {
        assertTrue(
            "GATEWAY_AWARENESS_CN 必须含 `contradicts` 字面值（中英 link_type 字面值保留英文）。",
            cnBlock.contains("contradicts")
        )
        assertTrue(
            "GATEWAY_AWARENESS_CN 必须含「矛盾」字面字符。",
            cnBlock.contains("矛盾")
        )
        val hasMaintenanceDuty =
            cnBlock.contains("维护") || cnBlock.contains("职责")
        assertTrue(
            "GATEWAY_AWARENESS_CN 必须含「维护」或「职责」任一字面字符 —— " +
                "把\"维护责任\"中文语义下沉到 prompt。",
            hasMaintenanceDuty
        )
    }

    /**
     * TC-AGENT-017-f: 中文 prompt 不得再含老的堵路句「无需你手动保存」。
     */
    @Test
    fun `TC-AGENT-017-f chinese prompt removes do-not-save-manually anti-pattern`() {
        assertFalse(
            "GATEWAY_AWARENESS_CN 不得再含「无需你手动保存」—— 中文堵路老句子必须删/替换。",
            cnBlock.contains("无需你手动保存")
        )
    }

    /**
     * TC-AGENT-017-g: 整个 SystemPromptConfig.kt 不得含 `auto_extracted` 字面值。
     *
     * 机制泄漏黑名单：R-AGENT-016 内部 tag 名 `#auto_extracted` 不得出现在 agent-facing prompt 里。
     * 防 prompt 污染（agent 知道 fact 来源后会回避具体表述 / 故意多输出 bullet / 把自己幻觉当 fact 递归引用）。
     */
    @Test
    fun `TC-AGENT-017-g prompt does not leak auto_extracted mechanism`() {
        assertFalse(
            "SystemPromptConfig.kt 整文件不得含 `auto_extracted` 字面值 —— " +
                "R-AGENT-016 抽取机制名属于实现细节，agent 一旦知道就会产生 prompt 污染" +
                "（回避具体表述 / 故意多 bullet / 把幻觉当 fact 递归）。",
            source.contains("auto_extracted")
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

    private fun systemPromptConfigPath(): String =
        File(appSrcMainRoot(), "core/config/SystemPromptConfig.kt").path
}
