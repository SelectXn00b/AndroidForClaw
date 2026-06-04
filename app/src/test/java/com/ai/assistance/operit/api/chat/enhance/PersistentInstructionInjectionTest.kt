package com.ai.assistance.operit.api.chat.enhance

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 守卫 R-AGENT-009 持久指令注入 在 `ConversationService` 中的接线契约。
 *
 * **背景**（2026-06-04 bugfix）：审计发现 R-AGENT-009 文档 + UI（toggle / 金色染色 /
 * MemoryRepository.findMemoriesByTag）齐了，但 `ConversationService.prepareConversationHistory`
 * 里**根本没有**拉 `#persistent_instruction` memory 拼到 system prompt 末尾——`buildPersistentInstructionsText()`
 * 只存在于需求文档里，是个不存在的方法。`MemoryInfoDialog` 上 "⭐ Persistent instruction — injected
 * into every system prompt" 那行字是误导性 UI。
 *
 * 修复：在 `ConversationService` 加 `buildPersistentInstructionsText()` 并在
 * `prepareConversationHistory` 拼 `finalSystemPrompt` 时 append。
 *
 * **测试策略**：`prepareConversationHistory` 是 suspend + 强依赖 Android `Context` /
 * preferencesManager / MemoryRepository(ObjectBox)，JVM 单测里 mock 收益低。改走
 * **源码字符串扫描**（参考 `DeepseekProviderTest` / `MemoryDedupTest` 成熟模式）：把
 * "拉 tag + 拼 [Persistent user instructions] + 在 system prompt 拼装处调用"
 * 固化进源码，防"下次顺手清理时把这条逻辑回滚"。运行时正确性由 §3 E2E 兜底。
 *
 * 对应 TC-AGENT-240-a..e、TC-AGENT-242-a（见 docs/hermes-test-cases.md）。
 */
class PersistentInstructionInjectionTest {

    // ===== TC-AGENT-240-x: ConversationService 必须有 buildPersistentInstructionsText 并接线 =====

    /**
     * TC-AGENT-240-a/b: `ConversationService` 必须定义 `buildPersistentInstructionsText` 方法
     * 且在 `prepareConversationHistory` 的 `finalSystemPrompt` 拼装处被调用一次。
     */
    @Test
    fun `TC-AGENT-240-ab ConversationService defines and invokes buildPersistentInstructionsText`() {
        val source = stripLineComments(File(conversationServicePath()).readText())

        // 1. 必须定义 buildPersistentInstructionsText 方法
        val defines = Regex("""fun\s+buildPersistentInstructionsText\s*\(""").containsMatchIn(source)
        assertTrue(
            "ConversationService 必须定义 suspend fun buildPersistentInstructionsText() —— " +
                "这是 R-AGENT-009 拉 #persistent_instruction memory 拼成 [Persistent user instructions] 段的入口。",
            defines
        )

        // 2. 必须在某处调用 buildPersistentInstructionsText()（不算定义本身）
        val callSites = Regex("""buildPersistentInstructionsText\s*\(\s*\)""").findAll(source).count()
        assertTrue(
            "ConversationService 必须有至少 1 处调用 buildPersistentInstructionsText() —— " +
                "实际 $callSites 处。光定义不调用 = 死代码 = R-AGENT-009 注入断链。",
            callSites >= 1
        )
    }

    /**
     * TC-AGENT-240-c: 拼接段头必须是 `[Persistent user instructions]`，bullet 用 `- `，
     * 按 `updatedAt desc` 排序——这三个契约由 `buildPersistentInstructionsText` 的源码体现。
     */
    @Test
    fun `TC-AGENT-240-c buildPersistentInstructionsText emits correct header bullet and sort`() {
        val source = stripLineComments(File(conversationServicePath()).readText())

        // 段头
        assertTrue(
            "ConversationService 必须使用字面量 [Persistent user instructions] 作为段头（R-AGENT-009 验收条件）",
            source.contains("[Persistent user instructions]")
        )

        // updatedAt 倒序
        assertTrue(
            "ConversationService 必须按 updatedAt 倒序排序持久指令（验收条件 TC-AGENT-240-c）—— " +
                "应出现 sortedByDescending { ...updatedAt... } 模式",
            Regex("""sortedByDescending\s*\{[^}]*updatedAt[^}]*\}""").containsMatchIn(source)
        )

        // bullet 用 "- "
        assertTrue(
            "ConversationService 拼接应使用 \"- \" 作为 bullet 前缀（R-AGENT-009 验收）",
            source.contains("\"\\n- \"") || source.contains("\"- \"") || source.contains("\"\\n- \"")
        )
    }

    /**
     * TC-AGENT-240-d/e: `buildPersistentInstructionsText` 必须真的查询
     * `findMemoriesByTag("#persistent_instruction")` —— 不查就拿不到用户写的指令。
     */
    @Test
    fun `TC-AGENT-240-de buildPersistentInstructionsText queries findMemoriesByTag with correct tag`() {
        val source = stripLineComments(File(conversationServicePath()).readText())

        assertTrue(
            "ConversationService 必须调用 findMemoriesByTag(\"#persistent_instruction\") —— " +
                "这是从 MemoryRepository 拉用户持久指令的唯一通道",
            Regex("""findMemoriesByTag\s*\(\s*"#persistent_instruction"\s*\)""").containsMatchIn(source)
        )
    }

    // ===== TC-AGENT-242-a: gateway / UI / Floating 三路径共用全局指令池 =====

    /**
     * TC-AGENT-242-a: `buildPersistentInstructionsText` 必须读 active profile（per-Profile 全局），
     * 不依赖 chatId / sessionId —— 这保证飞书 gateway / UI chat / floating chat 三路径共用同一池。
     *
     * 验证：方法体里出现 `preferencesManager.activeProfileIdFlow` 之类的全局读法，
     * 且不接受 chatId / sessionId 参数。
     */
    @Test
    fun `TC-AGENT-242-a buildPersistentInstructionsText reads active profile globally not per-chat`() {
        val source = stripLineComments(File(conversationServicePath()).readText())

        // 签名是无参（profile 是当前 active 的）
        val noArgSignature = Regex("""fun\s+buildPersistentInstructionsText\s*\(\s*\)""").containsMatchIn(source)
        assertTrue(
            "buildPersistentInstructionsText 必须是无参方法（per-Profile 全局池），" +
                "不应接 chatId / sessionId —— per-chat 维度按 R-AGENT-009 是 P1 范围。",
            noArgSignature
        )

        // 必须从 preferencesManager 拉 active profile（保证三路径同 Profile 时共用）
        assertTrue(
            "buildPersistentInstructionsText 必须从 preferencesManager.activeProfileIdFlow 读 profileId，" +
                "才能保证 UI / Floating / Gateway 三路径共用同一指令池（验收 TC-AGENT-242-a）",
            Regex("""preferencesManager\.activeProfileIdFlow""").containsMatchIn(source)
        )
    }

    // ----- helpers -----

    /**
     * 剥掉 Kotlin 单行注释（`// ...`），避免 regex 撞上注释里的"反模式样本"。
     * 不处理 /* */ 块注释（KDoc 不包字面量代码片段）。
     */
    private fun stripLineComments(src: String): String =
        src.lines().joinToString("\n") { line ->
            val idx = findUncommentedSlashSlash(line)
            if (idx >= 0) line.substring(0, idx) else line
        }

    private fun findUncommentedSlashSlash(line: String): Int {
        var i = 0
        var inString = false
        var inChar = false
        while (i < line.length - 1) {
            val c = line[i]
            val next = line[i + 1]
            when {
                c == '\\' -> { i += 2; continue }
                inString && c == '"' -> inString = false
                inChar && c == '\'' -> inChar = false
                !inString && !inChar && c == '"' -> inString = true
                !inString && !inChar && c == '\'' -> inChar = true
                !inString && !inChar && c == '/' && next == '/' -> return i
            }
            i++
        }
        return -1
    }

    private fun appSrcMainRoot(): File {
        val candidate = File("src/main/java/com/ai/assistance/operit")
        if (candidate.exists()) return candidate
        val alt = File("app/src/main/java/com/ai/assistance/operit")
        if (alt.exists()) return alt
        error("Cannot locate app/src/main/java/com/ai/assistance/operit — cwd=${File(".").absolutePath}")
    }

    private fun conversationServicePath(): String =
        File(appSrcMainRoot(), "api/chat/enhance/ConversationService.kt").path
}
