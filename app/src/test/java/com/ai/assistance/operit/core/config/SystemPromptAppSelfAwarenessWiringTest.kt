package com.ai.assistance.operit.core.config

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * R-AGENT-030 (2026-06-13)：让 agent 知道 HermesApp 内置了哪些用户视角的 UI 入口
 * （工具箱 / Memory hub / Settings / Skill Recorder / Terminal 等），方便用户问
 * "我去哪里 X"时给出导航式回答而非自己代劳。
 *
 * **测试策略**：仿 `SystemPromptMemoryMaintenanceWiringTest` 同范式 —— 直接对
 * `core/config/SystemPromptConfig.kt` 文本断言关键字面值 / 占位符 / replace 调用。
 * 不依赖 Android Context / Compose / LLM。运行时正确性由 §3 E2E + 手测兜底。
 *
 * 对应 TC-AGENT-030-a..e（见 docs/hermes-test-cases.md）。
 */
class SystemPromptAppSelfAwarenessWiringTest {

    private val source: String by lazy { File(systemPromptConfigPath()).readText() }

    /**
     * 抽 SYSTEM_PROMPT_TEMPLATE（英文）的常量体（带分隔节符的多行字符串）。
     * 锚点：从 "val SYSTEM_PROMPT_TEMPLATE" 这行开始，到下一个 `""".trimIndent()` 终止。
     */
    private val mainPromptEn: String by lazy {
        extractBetween(source, "val SYSTEM_PROMPT_TEMPLATE =", "\"\"\".trimIndent()")
            ?: error("找不到 SYSTEM_PROMPT_TEMPLATE 常量体，SystemPromptConfig 可能被重构")
    }

    /**
     * 抽 SYSTEM_PROMPT_TEMPLATE_CN（中文）的常量体。
     * 锚点：从 "val SYSTEM_PROMPT_TEMPLATE_CN" 这行开始，到下一个 `""".trimIndent()`。
     */
    private val mainPromptCn: String by lazy {
        extractBetween(source, "val SYSTEM_PROMPT_TEMPLATE_CN =", "\"\"\".trimIndent()")
            ?: error("找不到 SYSTEM_PROMPT_TEMPLATE_CN 常量体，SystemPromptConfig 可能被重构")
    }

    /**
     * 抽 SUBTASK_AGENT_PROMPT_TEMPLATE 的常量体。
     */
    private val subtaskPrompt: String by lazy {
        extractBetween(source, "val SUBTASK_AGENT_PROMPT_TEMPLATE =", "\"\"\".trimIndent()")
            ?: error("找不到 SUBTASK_AGENT_PROMPT_TEMPLATE 常量体，SystemPromptConfig 可能被重构")
    }

    /**
     * 抽 APP_SELF_AWARENESS_EN 常量体（中英两段都需断言关键字）。
     */
    private val selfAwarenessEn: String by lazy {
        extractBetween(source, "APP_SELF_AWARENESS_EN", "APP_SELF_AWARENESS_CN")
            ?: error("找不到 APP_SELF_AWARENESS_EN 块 —— 先满足 TC-AGENT-030-a")
    }

    /**
     * 抽 APP_SELF_AWARENESS_CN 常量体（从 APP_SELF_AWARENESS_CN 起到 TOOL_USAGE_GUIDELINES_EN 止）。
     */
    private val selfAwarenessCn: String by lazy {
        extractBetween(source, "APP_SELF_AWARENESS_CN", "TOOL_USAGE_GUIDELINES_EN")
            ?: error("找不到 APP_SELF_AWARENESS_CN 块 —— 先满足 TC-AGENT-030-a")
    }

    /**
     * TC-AGENT-030-a: 必须存在 APP_SELF_AWARENESS_EN / APP_SELF_AWARENESS_CN 两个常量声明。
     */
    @Test
    fun `TC-AGENT-030-a config exposes APP_SELF_AWARENESS constants for both languages`() {
        assertTrue(
            "SystemPromptConfig.kt 必须含 `const val APP_SELF_AWARENESS_EN` 声明 —— " +
                "R-AGENT-030 注入应用自我感知段的英文常量。",
            Regex("""\bconst\s+val\s+APP_SELF_AWARENESS_EN\b""").containsMatchIn(source)
        )
        assertTrue(
            "SystemPromptConfig.kt 必须含 `const val APP_SELF_AWARENESS_CN` 声明 —— " +
                "R-AGENT-030 注入应用自我感知段的中文常量。",
            Regex("""\bconst\s+val\s+APP_SELF_AWARENESS_CN\b""").containsMatchIn(source)
        )
    }

    /**
     * TC-AGENT-030-b: SYSTEM_PROMPT_TEMPLATE / SYSTEM_PROMPT_TEMPLATE_CN 两个常量都必须含
     * APP_SELF_AWARENESS_SECTION 占位符；位置必须夹在 GATEWAY_AWARENESS_SECTION 与
     * TOOL_USAGE_GUIDELINES_SECTION 之间。
     */
    @Test
    fun `TC-AGENT-030-b both system prompt templates contain APP_SELF_AWARENESS placeholder between gateway and tool usage`() {
        // 英文模板
        assertTrue(
            "SYSTEM_PROMPT_TEMPLATE（英文）必须含 `APP_SELF_AWARENESS_SECTION` 占位符 —— " +
                "否则 replace 后段空缺。\n实际模板（前 2000 chars）:\n${mainPromptEn.take(2000)}",
            mainPromptEn.contains("APP_SELF_AWARENESS_SECTION")
        )
        val gwIdxEn = mainPromptEn.indexOf("GATEWAY_AWARENESS_SECTION")
        val selfIdxEn = mainPromptEn.indexOf("APP_SELF_AWARENESS_SECTION")
        val toolIdxEn = mainPromptEn.indexOf("TOOL_USAGE_GUIDELINES_SECTION")
        assertTrue(
            "SYSTEM_PROMPT_TEMPLATE（英文）必须按顺序排：GATEWAY_AWARENESS_SECTION < " +
                "APP_SELF_AWARENESS_SECTION < TOOL_USAGE_GUIDELINES_SECTION。\n" +
                "实际下标 gw=$gwIdxEn / self=$selfIdxEn / tool=$toolIdxEn",
            gwIdxEn in 0 until selfIdxEn && selfIdxEn < toolIdxEn
        )

        // 中文模板
        assertTrue(
            "SYSTEM_PROMPT_TEMPLATE_CN（中文）必须含 `APP_SELF_AWARENESS_SECTION` 占位符。\n" +
                "实际模板（前 2000 chars）:\n${mainPromptCn.take(2000)}",
            mainPromptCn.contains("APP_SELF_AWARENESS_SECTION")
        )
        val gwIdxCn = mainPromptCn.indexOf("GATEWAY_AWARENESS_SECTION")
        val selfIdxCn = mainPromptCn.indexOf("APP_SELF_AWARENESS_SECTION")
        val toolIdxCn = mainPromptCn.indexOf("TOOL_USAGE_GUIDELINES_SECTION")
        assertTrue(
            "SYSTEM_PROMPT_TEMPLATE_CN（中文）必须按顺序排：GATEWAY_AWARENESS_SECTION < " +
                "APP_SELF_AWARENESS_SECTION < TOOL_USAGE_GUIDELINES_SECTION。\n" +
                "实际下标 gw=$gwIdxCn / self=$selfIdxCn / tool=$toolIdxCn",
            gwIdxCn in 0 until selfIdxCn && selfIdxCn < toolIdxCn
        )
    }

    /**
     * TC-AGENT-030-c: getSystemPrompt(...) 函数体必须含 replace("APP_SELF_AWARENESS_SECTION", ...)
     * 调用，三元根据 useEnglish 选 EN/CN 常量。
     */
    @Test
    fun `TC-AGENT-030-c getSystemPrompt replaces APP_SELF_AWARENESS placeholder with locale-appropriate constant`() {
        // 形态：.replace("APP_SELF_AWARENESS_SECTION", if (useEnglish) APP_SELF_AWARENESS_EN else APP_SELF_AWARENESS_CN)
        // 容错：参数顺序 / 空白 / 大小写按既有 GATEWAY 风格保持一致。
        val replacePattern =
            Regex("""\.replace\s*\(\s*"APP_SELF_AWARENESS_SECTION"\s*,\s*if\s*\(\s*useEnglish\s*\)\s*APP_SELF_AWARENESS_EN\s+else\s+APP_SELF_AWARENESS_CN\s*\)""")
        assertTrue(
            "getSystemPrompt(...) 必须含 " +
                "`.replace(\"APP_SELF_AWARENESS_SECTION\", if (useEnglish) APP_SELF_AWARENESS_EN else APP_SELF_AWARENESS_CN)` 调用 —— " +
                "否则模板里的占位符会原样出现在 system prompt。",
            replacePattern.containsMatchIn(source)
        )
    }

    /**
     * TC-AGENT-030-d: 中英两个 APP_SELF_AWARENESS 常量都必须列出核心导航关键字
     * （守 prompt 内容不被空段或单语段意外提交）。
     */
    @Test
    fun `TC-AGENT-030-d both prompt sections list the core navigation entry points`() {
        // 中文版
        listOf("工具箱", "记忆", "设置", "技能录制", "终端").forEach { kw ->
            assertTrue(
                "APP_SELF_AWARENESS_CN 必须含中文核心导航关键字 `$kw` —— " +
                    "用户问对应入口时 agent 才能给导航式回答。\n实际块（前 1500 chars）:\n${selfAwarenessCn.take(1500)}",
                selfAwarenessCn.contains(kw)
            )
        }
        // 英文版
        listOf("Toolbox", "Memory", "Settings", "Skill", "Terminal").forEach { kw ->
            assertTrue(
                "APP_SELF_AWARENESS_EN 必须含英文核心导航关键字 `$kw` —— " +
                    "用户问对应入口时 agent 才能给导航式回答。\n实际块（前 1500 chars）:\n${selfAwarenessEn.take(1500)}",
                selfAwarenessEn.contains(kw)
            )
        }
    }

    /**
     * TC-AGENT-030-e: SUBTASK_AGENT_PROMPT_TEMPLATE 必须不含 APP_SELF_AWARENESS_SECTION 字面值。
     * 子任务 agent 不需要 UI 自我感知（与 GATEWAY_AWARENESS 同处理）。
     */
    @Test
    fun `TC-AGENT-030-e subtask agent prompt does not get app self-awareness section`() {
        assertFalse(
            "SUBTASK_AGENT_PROMPT_TEMPLATE 不得含 `APP_SELF_AWARENESS_SECTION` 占位符 —— " +
                "子任务 agent 不需要 UI 自我感知，与 GATEWAY_AWARENESS 同处理。\n" +
                "实际块（前 1500 chars）:\n${subtaskPrompt.take(1500)}",
            subtaskPrompt.contains("APP_SELF_AWARENESS_SECTION")
        )
    }

    /**
     * TC-AGENT-031-k: APP_SELF_AWARENESS 中英两段都必须 mention `cronjob` + `15` 分钟下限语义。
     * 让 agent 知道自己有定时任务工具且懂 WorkManager 平台限制。
     */
    @Test
    fun `TC-AGENT-031-k both prompt sections mention cronjob tool and 15-minute android limit`() {
        // 英文：必须 mention `cronjob` + `15`（数字字面）+ `minutes` / `minimum` 任一
        assertTrue(
            "APP_SELF_AWARENESS_EN 必须 mention `cronjob` —— " +
                "agent 不知道这个工具存在就不会主动给用户登记定时任务。\n" +
                "实际块（前 1500 chars）:\n${selfAwarenessEn.take(1500)}",
            selfAwarenessEn.contains("cronjob")
        )
        assertTrue(
            "APP_SELF_AWARENESS_EN 必须含数字 `15` —— Android WorkManager 周期下限。",
            selfAwarenessEn.contains("15")
        )
        assertTrue(
            "APP_SELF_AWARENESS_EN 必须含 `minute` 或 `minimum` 字面值 —— 表达\"下限\"语义。",
            selfAwarenessEn.contains("minute") || selfAwarenessEn.contains("minimum")
        )

        // 中文：必须 mention `cronjob` + `15` + 「分钟」+ 「计划任务」或「定时」任一
        assertTrue(
            "APP_SELF_AWARENESS_CN 必须 mention `cronjob` —— 中英文 prompt 一致。",
            selfAwarenessCn.contains("cronjob")
        )
        assertTrue(
            "APP_SELF_AWARENESS_CN 必须含数字 `15` —— Android WorkManager 周期下限。",
            selfAwarenessCn.contains("15")
        )
        assertTrue(
            "APP_SELF_AWARENESS_CN 必须含「分钟」字面字符。",
            selfAwarenessCn.contains("分钟")
        )
        assertTrue(
            "APP_SELF_AWARENESS_CN 必须含「计划任务」或「定时」字面字符 —— " +
                "中文 locale 把\"cronjob 工具\"语义下沉到 prompt。",
            selfAwarenessCn.contains("计划任务") || selfAwarenessCn.contains("定时")
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
