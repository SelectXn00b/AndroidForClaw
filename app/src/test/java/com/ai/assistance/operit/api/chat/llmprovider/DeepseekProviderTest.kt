package com.ai.assistance.operit.api.chat.llmprovider

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * R-AGENT-002 bugfix — DeepseekProvider 不应给 DeepSeek 官方塞空 `reasoning_content`。
 *
 * **Bug 背景**：用户报告"DeepSeek 官方 API 无响应；同一条 key 在其他 agent 应用正常"。
 * 排查锁定 `DeepseekProvider.buildMessagesWithReasoning` 5 处 `put("reasoning_content", ...)` 无条件写入：
 *  - 即便 `reasoningContent == ""` 也 put 空串
 *  - DeepSeek 官方 V3 schema 严格 → 拒/挂死
 *  - 其他 platform（OpenRouter / SiliconFlow / `OPENAI_GENERIC` 路径）走 `OpenAIProvider`
 *    （line 1011-1014 `takeIf { it.isNotEmpty() }`）不塞空，所以"其他平台没问题"
 *  - 历史 commit `024a3185` 给 MiMo 加的"无条件 put"过度防御泄漏到 DeepSeek
 *
 * **测试策略**：`buildMessagesWithReasoning` 是 `private` 且依赖 Android `Context`（多模态 `buildContentField`），
 * JVM 单测里反射 + mock 收益低。改走 **源码字符串扫描**（参考 `LaunchAppToolTest` /
 * `MemoryDedupTest` TC-260-f 的成熟模式）：把"非空才塞 + 禁塞空"两个契约固化进源码，
 * 防"下次顺手清理时把这条逻辑回滚"。运行时正确性由 §3 E2E 兜底（DeepSeek 不在脚本里，但
 * 同 provider 链路的 MiMo OpenRouter E2E 不破即认为没回归）。
 *
 * 对应 TC-AGENT-262-a..e（见 docs/hermes-test-cases.md）。
 *
 * **TC-AGENT-262-e 背景**（2026-06-03 第二次 bugfix）：上一次修守卫后，DeepSeek 官方 tool_call
 * 第二轮报 400 `The reasoning_content in the thinking mode must be passed back to the API.`
 * 根因：`buildMessagesWithReasoning` 在 ASSISTANT / TOOL_CALL 分支只信 `originalContent` 里的
 * `<think>` 标签 (`ChatUtils.extractThinkingContent`)，但上游 `OperitChatCompletionServer` /
 * `EnhancedAIService.toPromptTurnsForHistory` 早已把 reasoning 拆到 `PromptTurn.reasoningContent`
 * 带外字段并把 `<think>` 从 content 里剥光 → 解构出来必然是空串 → 被 takeIf 守卫剥掉 → DeepSeek 报 400。
 * 修复：4 处分支（useToolCall true/false × ASSISTANT/TOOL_CALL）必须优先读 `turn.reasoningContent`
 * 带外字段，inline `<think>` 提取仅作 fallback（兼容老历史）。
 */
class DeepseekProviderTest {

    // ===== TC-AGENT-262-a: assistant without thinking must omit reasoning_content =====

    /**
     * TC-AGENT-262-a: 没 `<think>` 的 ASSISTANT turn → 输出 JSON 不应含 `reasoning_content` 键。
     *
     * 用源码扫描验证：useToolCall=true 的 ASSISTANT 非 tool_call 分支 + useToolCall=false 的
     * ASSISTANT 分支，都必须用"非空才 put"模式（对齐 `OpenAIProvider.kt:1011-1014`）。
     */
    @Test
    fun `TC-AGENT-262-a assistant without thinking omits reasoning_content`() {
        val source = stripLineComments(File(deepseekProviderPath()).readText())

        // 必须存在 `takeIf { it.isNotEmpty() }?.let { put("reasoning_content", it) }` 模式
        // 至少出现 2 次（useToolCall=true 的 ASSISTANT 分支 + useToolCall=false 的 ASSISTANT 分支）
        val safeReasoningPutCount = Regex(
            """takeIf\s*\{\s*it\.isNotEmpty\(\)\s*\}\s*\??\.let\s*\{\s*put\(\s*"reasoning_content""""
        ).findAll(source).count()
        assertTrue(
            "DeepseekProvider 必须用 takeIf{it.isNotEmpty()}.let{put(\"reasoning_content\", ...)} 模式（对齐 OpenAIProvider），" +
                "至少 2 处（useToolCall true + false 的 ASSISTANT 分支），实际 $safeReasoningPutCount",
            safeReasoningPutCount >= 2
        )
    }

    // ===== TC-AGENT-262-b: assistant with thinking keeps reasoning_content =====

    /**
     * TC-AGENT-262-b: 有 `<think>some reasoning</think>` 时 reasoning_content 仍然要发出。
     *
     * 等价于"必须保留 reasoning_content 的 put 路径"——只是把它放进非空守卫里。
     * 验证：源码不能"一刀切去掉 reasoning_content"——必须仍然有写入路径。
     */
    @Test
    fun `TC-AGENT-262-b assistant with thinking keeps reasoning_content`() {
        val source = stripLineComments(File(deepseekProviderPath()).readText())

        // 必须仍有 put("reasoning_content", ...) 写入（不要因为修 bug 把它整个删掉）
        val anyReasoningPut = Regex("""put\(\s*"reasoning_content"""").containsMatchIn(source)
        assertTrue(
            "DeepseekProvider 必须保留 put(\"reasoning_content\", ...) 写入路径（非空时仍要发，" +
                "否则 MiMo 的 reasoning roundtrip 会破）",
            anyReasoningPut
        )

        // emitQueuedToolCallsIfNeeded 里那处也必须用守卫（不应再用 orEmpty()）
        val queuedReasoningGuarded = Regex(
            """queuedAssistantReasoning\s*\?\.takeIf\s*\{\s*it\.isNotEmpty\(\)\s*\}"""
        ).containsMatchIn(source) ||
            Regex(
                """queuedAssistantReasoning\s*\?\.takeIf\s*\{\s*it\.isNotBlank\(\)\s*\}"""
            ).containsMatchIn(source)
        assertTrue(
            "emitQueuedToolCallsIfNeeded 处的 queuedAssistantReasoning 必须走非空守卫（不能再 .orEmpty() 塞空）",
            queuedReasoningGuarded
        )
    }

    // ===== TC-AGENT-262-c: tool_call path without thinking must omit reasoning_content =====

    /**
     * TC-AGENT-262-c: TOOL_CALL turn（无 thinking）不应输出 `reasoning_content` 键（既不空串也不 null）。
     *
     * 防呆：原代码两处 TOOL_CALL 分支硬塞空串 reasoning_content，必须消除。
     */
    @Test
    fun `TC-AGENT-262-c tool_call without thinking omits reasoning_content`() {
        val source = stripLineComments(File(deepseekProviderPath()).readText())

        // 不允许任何 put("reasoning_content", "") 字面量空串（已剥掉单行注释）
        val emptyStringPut = Regex("""put\(\s*"reasoning_content"\s*,\s*""\s*\)""").containsMatchIn(source)
        assertFalse(
            "DeepseekProvider 不允许字面量空 reasoning_content put——TOOL_CALL 分支硬塞空串会触发 DeepSeek 官方 schema 拒绝",
            emptyStringPut
        )
    }

    // ===== TC-AGENT-262-d: source contract forbids empty reasoning_content put =====

    /**
     * TC-AGENT-262-d: 防呆 wiring——禁止 5 处"塞空"模式复活。
     *
     * 覆盖两种已知反模式：
     *  - `put("reasoning_content", "")` 字面量空串
     *  - `put("reasoning_content", ...orEmpty())` 把 nullable 强制成空串
     *
     * 这一条是 §0.1 "活文档"硬约束：下次有人顺手"统一一下空字段处理"把 takeIf 拆掉，
     * 这个 test 立刻红。
     */
    @Test
    fun `TC-AGENT-262-d source contract forbids empty reasoning_content put`() {
        val source = stripLineComments(File(deepseekProviderPath()).readText())

        // 反模式 1: put("reasoning_content", "") —— 字面量空串
        assertFalse(
            "DeepseekProvider 不应硬塞空 reasoning_content（字面量空串 put）—— 触发 DeepSeek 官方 V3 schema 拒绝/挂死",
            Regex("""put\(\s*"reasoning_content"\s*,\s*""\s*\)""").containsMatchIn(source)
        )

        // 反模式 2: put("reasoning_content", xxx.orEmpty()) —— nullable 转空串塞
        assertFalse(
            "DeepseekProvider 不应用 .orEmpty() 把 nullable reasoning 塞成空串——同 hypothesis A bug",
            Regex("""put\(\s*"reasoning_content"\s*,\s*[\w.]+\.orEmpty\(\)\s*\)""").containsMatchIn(source)
        )

        // 反模式 3: put("reasoning_content", reasoningContent) 裸 put（reasoningContent 可能为空）
        // 这一条比较紧，必须走 takeIf 守卫——但 reasoningContent 这个名字在 buildMessagesWithReasoning
        // 里既是 ChatUtils.extractThinkingContent 解构出来的局部变量，也可能在其他 helper 里出现。
        // 用更严的判定：紧跟 put 后面是裸变量 reasoningContent 的（不走 takeIf 守卫）必须为 0。
        val nakedReasoningContentPut = Regex(
            """put\(\s*"reasoning_content"\s*,\s*reasoningContent\s*\)"""
        ).findAll(source).count()
        assertFalse(
            "DeepseekProvider 不应裸 put 局部变量 reasoningContent——必须走 takeIf{isNotEmpty} 守卫，" +
                "实际发现 $nakedReasoningContentPut 处",
            nakedReasoningContentPut > 0
        )
    }

    // ===== TC-AGENT-262-e: must read PromptTurn.reasoningContent out-of-band =====

    /**
     * TC-AGENT-262-e: 4 处分支必须优先读 `turn.reasoningContent` 带外字段。
     *
     * 上游 (`OperitChatCompletionServer` / `EnhancedAIService.toPromptTurnsForHistory`) 把
     * reasoning 拆到 `PromptTurn.reasoningContent`，content 里 `<think>` 已剥光。
     * `buildMessagesWithReasoning` 必须读带外字段，否则 ASSISTANT/TOOL_CALL 分支拿不到 reasoning
     * → DeepSeek 官方报 400。
     *
     * 验证：源码必须含至少 3 处 `turn.reasoningContent` 引用（4 处分支里至少 3 处
     * 直接 referenced；ASSISTANT 两个分支共享 1 处也接受，但 TOOL_CALL 两处必须各自 reference）。
     * 同时 `extractThinkingContent` 不能是唯一的 reasoning 来源 —— 必须有带外 fallback 模式。
     */
    @Test
    fun `TC-AGENT-262-e branches must read PromptTurn reasoningContent out-of-band`() {
        val source = stripLineComments(File(deepseekProviderPath()).readText())

        // 至少 3 处 turn.reasoningContent 引用（ASSISTANT useToolCall=true/false + TOOL_CALL useToolCall=true/false，
        // 实际可能合并写法所以放宽到 >=3）
        val outOfBandRefs = Regex("""turn\.reasoningContent""").findAll(source).count()
        assertTrue(
            "DeepseekProvider 必须至少 3 处引用 turn.reasoningContent 带外字段（ASSISTANT/TOOL_CALL × useToolCall true/false），" +
                "实际 $outOfBandRefs 处。上游已把 reasoning 拆到带外，content 不再含 <think> 标签。",
            outOfBandRefs >= 3
        )

        // 必须存在"非空带外则走带外，否则 fallback 到 inline 提取"的模式
        // 用宽松匹配：源码里必须同时出现 turn.reasoningContent 的非空判断 + extractThinkingContent fallback
        val hasOutOfBandPriorityPattern =
            Regex("""turn\.reasoningContent""").containsMatchIn(source) &&
                Regex("""isNullOrEmpty\(\)|isNotEmpty\(\)|isNotBlank\(\)""").containsMatchIn(source) &&
                Regex("""extractThinkingContent""").containsMatchIn(source)
        assertTrue(
            "DeepseekProvider 必须存在『带外字段优先 + inline 提取 fallback』模式：" +
                "turn.reasoningContent 非空时用带外，否则回退到 ChatUtils.extractThinkingContent",
            hasOutOfBandPriorityPattern
        )
    }

    // ----- helpers -----

    /**
     * 剥掉 Kotlin 单行注释（`// ...`），避免 regex 撞上注释里的"反模式样本"。
     * 不处理 /* */ 块注释（DeepseekProvider 里只有 KDoc，KDoc 不包字面量代码片段）。
     */
    private fun stripLineComments(src: String): String =
        src.lines().joinToString("\n") { line ->
            val idx = line.indexOf("//")
            if (idx >= 0) line.substring(0, idx) else line
        }

    private fun appSrcMainRoot(): File {
        val candidate = File("src/main/java/com/ai/assistance/operit")
        if (candidate.exists()) return candidate
        val alt = File("app/src/main/java/com/ai/assistance/operit")
        if (alt.exists()) return alt
        error("Cannot locate app/src/main/java/com/ai/assistance/operit — cwd=${File(".").absolutePath}")
    }

    private fun deepseekProviderPath(): String =
        File(appSrcMainRoot(), "api/chat/llmprovider/DeepseekProvider.kt").path
}
