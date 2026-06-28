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
 * 对应 TC-AGENT-240-a..e、TC-AGENT-242-a、TC-AGENT-280-a..e（见 docs/hermes-test-cases.md）。
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

        // 2. 必须在某处调用 buildPersistentInstructionsText(...)（不算定义本身）
        //    R-AGENT-046 后签名为 (userInput: String)，所以匹配任意实参；为排除定义自身，
        //    要求左括号紧贴前必须不是 `fun ` 关键字（用负向 lookbehind）。
        val callSites = Regex("""(?<!fun\s)buildPersistentInstructionsText\s*\(""").findAll(source)
            .filter { match ->
                // 再排除"定义那一行" —— 定义形如 `suspend fun buildPersistentInstructionsText(`
                val start = match.range.first
                val precedingChunk = source.substring(maxOf(0, start - 40), start)
                !precedingChunk.contains(Regex("""fun\s+$"""))
            }
            .count()
        assertTrue(
            "ConversationService 必须有至少 1 处调用 buildPersistentInstructionsText(...) —— " +
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
     * 2026-06-28（R-AGENT-046）：允许新增 `userInput: String` 形参以支持触发词预筛；
     * 但**仍不允许** chatId / sessionId 参数（per-Profile 全局，不耦合 chat / session）。
     *
     * 验证：
     *  (1) 方法存在，
     *  (2) 形参列表里不含 `chatId` / `sessionId`（regex 黑名单），
     *  (3) 方法体里仍出现 `preferencesManager.activeProfileIdFlow` 全局读法。
     */
    @Test
    fun `TC-AGENT-242-a buildPersistentInstructionsText reads active profile globally not per-chat`() {
        val source = stripLineComments(File(conversationServicePath()).readText())

        // (1) 方法存在（允许任意形参列表 —— 老的无参 / 新的 userInput 都通过）
        val defines = Regex("""fun\s+buildPersistentInstructionsText\s*\(""").containsMatchIn(source)
        assertTrue(
            "buildPersistentInstructionsText 必须存在（R-AGENT-009 入口）。",
            defines
        )

        // (2) 形参列表里不含 chatId / sessionId
        //     抓 `fun buildPersistentInstructionsText(...)` 的形参括号内容做黑名单扫描
        val sigMatch = Regex("""fun\s+buildPersistentInstructionsText\s*\(([^)]*)\)""").find(source)
        assertTrue(
            "buildPersistentInstructionsText 签名未能被解析到 —— 检查方法定义。",
            sigMatch != null
        )
        val params = sigMatch!!.groupValues[1]
        assertFalse(
            "buildPersistentInstructionsText 形参列表不得包含 chatId —— per-Profile 全局，不与 chat 耦合。" +
                "（R-AGENT-046 允许新增 userInput，但 chatId/sessionId 仍是 P1 范围之外的 per-chat 维度）。" +
                "实际形参=`$params`。",
            Regex("""\bchatId\b""").containsMatchIn(params)
        )
        assertFalse(
            "buildPersistentInstructionsText 形参列表不得包含 sessionId —— per-Profile 全局，不与 session 耦合。" +
                "实际形参=`$params`。",
            Regex("""\bsessionId\b""").containsMatchIn(params)
        )

        // (3) 必须从 preferencesManager 拉 active profile（保证三路径同 Profile 时共用）
        assertTrue(
            "buildPersistentInstructionsText 必须从 preferencesManager.activeProfileIdFlow 读 profileId，" +
                "才能保证 UI / Floating / Gateway 三路径共用同一指令池（验收 TC-AGENT-242-a）",
            Regex("""preferencesManager\.activeProfileIdFlow""").containsMatchIn(source)
        )
    }

    // ===== TC-AGENT-280-x: R-AGENT-046 触发词预筛注入（Android 扩展） =====
    //
    // 红测策略：与既有 240 系列一致——源码扫描而非行为单测，因为
    // ConversationService 强依赖 Android Context / preferencesManager /
    // MemoryRepository(ObjectBox)，JVM 单测里 mock 收益低。固化"按 trigger_keywords
    // 预筛 + 短输入 fallback + lowercase/NFC 归一化"三个契约进源码，运行时
    // 正确性由 §3 E2E 兜底。

    /**
     * TC-AGENT-280-a: 老条目（缺 trigger_keywords property）保持每轮注入 —— 向后兼容。
     *
     * 源码层契约：`buildPersistentInstructionsText` 体内必须存在
     * "缺 trigger_keywords property → 仍 emit"分支。具体表现为：对每条 memory
     * 取 trigger_keywords property 时使用 `?.` 链 / `firstOrNull` / `isNullOrBlank()`
     * 等空安全姿态，**不能**做无条件 `!!` 解引用 → 否则等价于"必须有 trigger_keywords
     * 才注入"——破坏向后兼容。
     */
    @Test
    fun `TC-AGENT-280-a legacy entries without trigger_keywords still inject every turn`() {
        val source = stripLineComments(File(conversationServicePath()).readText())

        // 必须 mention trigger_keywords key（不是字符串硬编码就是常量引用）
        val mentionsTriggerKeywords =
            source.contains("trigger_keywords") ||
                source.contains("KEY_TRIGGER_KEYWORDS") ||
                source.contains("TRIGGER_KEYWORDS")
        assertTrue(
            "TC-AGENT-280-a: buildPersistentInstructionsText 必须 mention trigger_keywords —— " +
                "字符串字面量或 MemoryProperty.KEY_TRIGGER_KEYWORDS 常量任一即可。",
            mentionsTriggerKeywords
        )

        // 必须有空安全姿态（向后兼容信号）—— `isNullOrBlank()` / `?:` / `firstOrNull`
        val hasNullSafety =
            Regex("""isNullOrBlank\s*\(""").containsMatchIn(source) ||
                Regex("""\?\:""").containsMatchIn(source) ||
                Regex("""firstOrNull\s*\(""").containsMatchIn(source)
        assertTrue(
            "TC-AGENT-280-a: 处理 trigger_keywords 必须空安全（isNullOrBlank / ?: / firstOrNull），" +
                "确保缺 property 的老条目仍然注入。",
            hasNullSafety
        )
    }

    /**
     * TC-AGENT-280-b: 触发词命中 → 注入。
     *
     * 源码层契约：方法体里必须出现"取 userInput 与某种 keyword 集合做 `contains` 匹配"
     * 的代码形态。
     */
    @Test
    fun `TC-AGENT-280-b trigger keyword hit injects entry`() {
        val source = stripLineComments(File(conversationServicePath()).readText())

        // userInput 必须是方法形参 —— 没有 userInput 就谈不上预筛
        val sigMatch = Regex("""fun\s+buildPersistentInstructionsText\s*\(([^)]*)\)""").find(source)
        assertTrue("buildPersistentInstructionsText 签名未找到。", sigMatch != null)
        val params = sigMatch!!.groupValues[1]
        assertTrue(
            "TC-AGENT-280-b: buildPersistentInstructionsText 必须接 userInput: String 形参 —— " +
                "这是触发词预筛的输入源。实际形参=`$params`。",
            Regex("""\buserInput\s*:\s*String""").containsMatchIn(params)
        )

        // 必须有子串 contains 匹配（OR 任一命中）
        assertTrue(
            "TC-AGENT-280-b: 预筛逻辑必须用 `userInput.contains(...)` 或 `keyword in userInput` 子串匹配。" +
                "未发现 contains 调用。",
            Regex("""\.contains\s*\(""").containsMatchIn(source)
        )
    }

    /**
     * TC-AGENT-280-c: 触发词未命中 → 跳过（filter 链一定要真在过滤）。
     *
     * 源码层契约：必须有 `filter` / `filterNot` 之类的过滤动作 —— 没有过滤就等于全部注入，
     * 失去 R-AGENT-046 的全部意义。
     */
    @Test
    fun `TC-AGENT-280-c trigger keyword miss skips entry`() {
        val source = stripLineComments(File(conversationServicePath()).readText())

        val hasFilter =
            Regex("""\.filter\s*\{""").containsMatchIn(source) ||
                Regex("""\.filterNot\s*\{""").containsMatchIn(source)
        assertTrue(
            "TC-AGENT-280-c: buildPersistentInstructionsText 必须包含 .filter { ... } / .filterNot { ... } " +
                "对持久指令列表做触发词过滤；否则等价于 R-AGENT-009 旧行为，本需求未落地。",
            hasFilter
        )
    }

    /**
     * TC-AGENT-280-d: 短输入 fallback —— `userInput.length < 4` 时全量注入。
     *
     * 源码层契约：必须存在阈值常量 `SHORT_INPUT_FALLBACK_THRESHOLD` 或字面量 `4`
     * 跟 `userInput.length` 比较的分支。
     */
    @Test
    fun `TC-AGENT-280-d short input fallback injects all`() {
        val source = stripLineComments(File(conversationServicePath()).readText())

        val hasThresholdConst = source.contains("SHORT_INPUT_FALLBACK_THRESHOLD")
        val hasLengthCheck =
            Regex("""userInput\.length\s*<""").containsMatchIn(source) ||
                Regex("""userInput\.length\s*<=""").containsMatchIn(source)
        assertTrue(
            "TC-AGENT-280-d: 必须存在常量 SHORT_INPUT_FALLBACK_THRESHOLD（推荐）或 `userInput.length < N` " +
                "判断 —— 短输入（如 \"嗯\" / \"ok\"）走全量注入，避免关键词不命中导致全集合静默丢失。",
            hasThresholdConst || hasLengthCheck
        )
    }

    /**
     * TC-AGENT-280-e: lowercase + NFC 归一化 —— 大小写不敏感、全角半角兼容。
     */
    @Test
    fun `TC-AGENT-280-e case insensitive nfc match`() {
        val source = stripLineComments(File(conversationServicePath()).readText())

        // 1) lowercase 归一化
        val hasLowercase =
            Regex("""\.lowercase\s*\(""").containsMatchIn(source) ||
                Regex("""\.toLowerCase\s*\(""").containsMatchIn(source)
        assertTrue(
            "TC-AGENT-280-e: 比对前必须 .lowercase() 归一化 —— 否则 `Google` ≠ `google` 会导致命中失败。",
            hasLowercase
        )

        // 2) NFC 归一化（java.text.Normalizer.normalize(... NFC)）
        val hasNfc = source.contains("Normalizer") && source.contains("NFC")
        assertTrue(
            "TC-AGENT-280-e: 比对前必须用 java.text.Normalizer 做 NFC 归一化 —— " +
                "否则不同 Unicode 形态的相同字符（如全角/半角空格）会导致命中失败。",
            hasNfc
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
