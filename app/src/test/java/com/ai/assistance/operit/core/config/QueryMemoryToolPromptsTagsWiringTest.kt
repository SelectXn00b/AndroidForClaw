package com.ai.assistance.operit.core.config

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * R-AGENT-014 (2026-06-07)：`query_memory` 工具的 EN + CN ToolPrompt 必须新增可选 `tags`
 * 参数，让 agent 能按 tag 精准过滤（例如 `tags=#auto_summary` 只取自动摘要节点）。
 *
 * **背景**：R-AGENT-013 让 APP 内聊天的摘要强行打 `#auto_summary` tag 落库。但 query_memory
 * 现有参数只有 `query / folder_path / start_time / end_time / snapshot_id / threshold / limit`，
 * agent 即使知道这个 tag 也没法精准查 —— 只能靠语义模糊检索撞运气命中。
 *
 * R-AGENT-014 要求：
 *   - EN `memoryTools` + CN `memoryToolsCn` 的 query_memory ToolPrompt 都新增 `tags` 参数
 *   - `required = false`（向后兼容所有既有调用方）
 *   - description 含 `#auto_summary` 示例 + `|` 分隔约定（多 tag 用 `|` 分隔，与 query
 *     参数关键词分隔风格一致）
 *
 * **测试策略**：与 R-AGENT-009 `PersistentInstructionAgentHintTest` 同策略 —— 源码字符串扫描守
 * wiring。`SystemToolPrompts.kt` 是纯 const string，没法走 runtime 断言。
 *
 * 对应 TC-AGENT-014-c / d / e（见 docs/hermes-test-cases.md）。
 */
class QueryMemoryToolPromptsTagsWiringTest {

    private val source: String by lazy { File(toolPromptsPath()).readText() }

    /**
     * TC-AGENT-014-c: `memoryTools`（EN）的 query_memory ToolPrompt parametersStructured
     * 必须含 `name = "tags"` 的 ToolParameterSchema 条目。description 必须含 `#auto_summary`
     * 示例 + `|` 分隔约定。
     */
    @Test
    fun `TC-AGENT-014-c english tool prompt declares tags parameter`() {
        val enBlock = extractQueryMemoryBlock(source, listName = "memoryTools")
        assertTrue(
            "找不到 EN memoryTools 内的 query_memory ToolPrompt 块",
            enBlock != null
        )

        val window = enBlock!!
        assertTrue(
            "EN query_memory ToolPrompt 必须 declare `name = \"tags\"` ToolParameterSchema —— " +
                "否则 agent 即使知道 #auto_summary tag 也没法按 tag 过滤。\n实际块:\n$window",
            Regex("""name\s*=\s*"tags"""").containsMatchIn(window)
        )

        assertTrue(
            "EN query_memory tags 参数 description 必须含 #auto_summary 示例 —— " +
                "agent 才知道用什么值。",
            window.contains("#auto_summary")
        )

        assertTrue(
            "EN query_memory tags 参数 description 必须含 `|` 分隔约定（多 tag 时用）—— " +
                "与 query 参数关键词分隔风格一致。",
            window.contains("|")
        )
    }

    /**
     * TC-AGENT-014-d: `memoryToolsCn`（CN）的 query_memory ToolPrompt parametersStructured
     * 必须含 `name = "tags"` 的 ToolParameterSchema 条目；中文版与英文版语义对齐。
     */
    @Test
    fun `TC-AGENT-014-d chinese tool prompt declares tags parameter`() {
        val cnBlock = extractQueryMemoryBlock(source, listName = "memoryToolsCn")
        assertTrue(
            "找不到 CN memoryToolsCn 内的 query_memory ToolPrompt 块",
            cnBlock != null
        )

        val window = cnBlock!!
        assertTrue(
            "CN query_memory ToolPrompt 必须 declare `name = \"tags\"` ToolParameterSchema —— " +
                "中文版与英文版必须语义对齐。\n实际块:\n$window",
            Regex("""name\s*=\s*"tags"""").containsMatchIn(window)
        )

        assertTrue(
            "CN query_memory tags 参数 description 必须含 #auto_summary 示例 —— " +
                "agent 才知道用什么值。",
            window.contains("#auto_summary")
        )

        assertTrue(
            "CN query_memory tags 参数 description 必须含 `|` 分隔约定（多 tag 时用）。",
            window.contains("|")
        )
    }

    /**
     * TC-AGENT-014-e: tags 参数必须 `required = false` —— 向后兼容所有既有 query_memory
     * 调用方（agent 不传 tags 时行为完全不变）。
     */
    @Test
    fun `TC-AGENT-014-e tags parameter is optional`() {
        listOf("memoryTools" to "EN", "memoryToolsCn" to "CN").forEach { (listName, langTag) ->
            val block = extractQueryMemoryBlock(source, listName)
                ?: error("找不到 $listName 内的 query_memory ToolPrompt 块")

            // 抽 tags ToolParameterSchema 行（找 name = "tags" 之后到下一个 ToolParameterSchema 或 ) 结束）
            val tagsMatch = Regex("""name\s*=\s*"tags"[\s\S]*?required\s*=\s*(true|false)""")
                .find(block)
            assertTrue(
                "$langTag query_memory: 找不到 tags 参数的 `required = true/false` 声明 —— " +
                    "必须显式标 required = false 才能向后兼容。",
                tagsMatch != null
            )

            val requiredValue = tagsMatch!!.groupValues[1]
            assertTrue(
                "$langTag query_memory: tags 参数必须 required = false（实际 = $requiredValue）—— " +
                    "向后兼容既有调用方。",
                requiredValue == "false"
            )
        }
    }

    // ----- helpers -----

    /**
     * 从 `SystemToolPrompts.kt` 抽出指定 list（`memoryTools` 或 `memoryToolsCn`）下
     * `name = "query_memory"` 那个 ToolPrompt 的完整文本（从 `name = "query_memory"` 起
     * 到下一个 **ToolPrompt(** 出现处 —— 或 list 结束）。
     *
     * 注意：不能用泛 `name = "<...>"` 作为 ToolPrompt 边界，因为 `ToolParameterSchema(name = "query", ...)`
     * 也匹配同一模式，会把 block 在第一个参数处截断，导致看不到后面的 tags 参数声明。
     */
    private fun extractQueryMemoryBlock(src: String, listName: String): String? {
        // 1. 定位 list 起点
        val listIdx = Regex("""(val|private val|internal val)\s+$listName\b""").find(src)?.range?.first
            ?: return null
        // 2. 在 list 范围内找 name = "query_memory"
        val listChunk = src.substring(listIdx, (listIdx + 30000).coerceAtMost(src.length))
        val queryStartLocal = Regex("""name\s*=\s*"query_memory"""").find(listChunk)?.range?.first
            ?: return null
        // 3. 找下一个 ToolPrompt( 的位置（必须是 ToolPrompt 边界，不是 ToolParameterSchema 边界）
        //    搜索起点：当前 `name = "query_memory"` 匹配结束之后
        val queryMatchEnd = Regex("""name\s*=\s*"query_memory"""").find(listChunk)!!.range.last + 1
        val rest = listChunk.substring(queryMatchEnd)
        val nextToolPromptMatch = Regex("""\bToolPrompt\s*\(""").find(rest)
        val endOffsetLocal = if (nextToolPromptMatch != null) {
            queryMatchEnd + nextToolPromptMatch.range.first
        } else {
            listChunk.length
        }
        return listChunk.substring(queryStartLocal, endOffsetLocal)
    }


    private fun appSrcMainRoot(): File {
        val candidate = File("src/main/java/com/ai/assistance/operit")
        if (candidate.exists()) return candidate
        val alt = File("app/src/main/java/com/ai/assistance/operit")
        if (alt.exists()) return alt
        error("Cannot locate app/src/main/java/com/ai/assistance/operit — cwd=${File(".").absolutePath}")
    }

    private fun toolPromptsPath(): String =
        File(appSrcMainRoot(), "core/config/SystemToolPrompts.kt").path
}
