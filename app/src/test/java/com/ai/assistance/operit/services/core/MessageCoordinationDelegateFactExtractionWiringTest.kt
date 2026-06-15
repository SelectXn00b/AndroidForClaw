package com.ai.assistance.operit.services.core

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * R-AGENT-016 (2026-06-08): `MessageCoordinationDelegate.forcePersistSummaryToMemory` 落
 * `#auto_summary` 整段摘要之后，必须**追加**一步把 SUMMARY_PROMPT 已经稳定输出的
 * `【关键事实】 / [Key Facts]` bullet 段每行解析成独立 fact，逐条落 `MemoryRepository.saveMemory`
 * 并打 `#auto_extracted` + `#chat:<chatId>` 两个 tag。
 *
 * R-AGENT-027 (2026-06-13): 删除原本的 `#auto_summary_id:<parentId>` 第三个 tag。
 * 该 tag 全代码库无任何读取方，且 R-AGENT-026 的 keepDecision=false 路径会产生
 * `#auto_summary_id:-1` 孤儿污染。`#chat:<chatId>` 已足够提供会话级溯源。
 *
 * 这是 Python Hermes `MemoryProvider.sync_turn` → 远端 Mem0/Hindsight fact extraction 的
 * Android 平台 LLM-free 平替——零额外 token，复用 R-AGENT-013 已经调过的 summary LLM 输出。
 *
 * **测试策略**: `MessageCoordinationDelegate` 重度依赖 ObjectBox / Hermes / Android Context，
 * JVM mock ROI 极低；与 R-AGENT-013/014/015 同策略走源码字符串扫描守 wiring。运行时正确性由
 * 手测兜底。对应 TC-AGENT-016-a..i（见 docs/hermes-test-cases.md）。
 */
class MessageCoordinationDelegateFactExtractionWiringTest {

    private val source: String by lazy { stripComments(File(delegatePath()).readText()) }

    /** 父函数 forcePersistSummaryToMemory 的 body（用于检验 wiring 入口）*/
    private val parentBlock: String by lazy {
        extractFunctionBlock(source, "forcePersistSummaryToMemory")
    }

    /**
     * 子函数 fact extractor body —— 名称容错三选一：extractAndPersistFacts /
     * extractFactsFromSummary / persistExtractedFacts。
     */
    private val extractorBlock: String by lazy {
        listOf("extractAndPersistFacts", "extractFactsFromSummary", "persistExtractedFacts")
            .firstNotNullOfOrNull { name ->
                runCatching { extractFunctionBlock(source, name) }.getOrNull()
            } ?: ""
    }

    /**
     * TC-AGENT-016-a: `forcePersistSummaryToMemory` 函数体末尾必须含对 fact 抽取/落库新方法的调用。
     */
    @Test
    fun `TC-AGENT-016-a forcePersistSummaryToMemory invokes fact extractor at end`() {
        val invokesExtractor =
            Regex("""\bextractAndPersistFacts\s*\(""").containsMatchIn(parentBlock) ||
                Regex("""\bextractFactsFromSummary\s*\(""").containsMatchIn(parentBlock) ||
                Regex("""\bpersistExtractedFacts\s*\(""").containsMatchIn(parentBlock) ||
                Regex("""\bextractFacts\w*\s*\(""").containsMatchIn(parentBlock) ||
                Regex("""\bextract\w*Facts\w*\s*\(""").containsMatchIn(parentBlock)
        assertTrue(
            "forcePersistSummaryToMemory 函数体末尾必须含对 fact 抽取新方法的调用 —— " +
                "如 `extractAndPersistFacts(...)` / `extractFactsFromSummary(...)` / " +
                "`persistExtractedFacts(...)` 之一。否则 R-AGENT-016 整条链路完全没接通。\n" +
                "实际函数体（前 4000 chars）:\n${parentBlock.take(4000)}",
            invokesExtractor
        )
    }

    /**
     * TC-AGENT-016-b: fact 抽取函数体内必须含【关键事实】/[Key Facts] 段头引用 + bullet 切分。
     */
    @Test
    fun `TC-AGENT-016-b fact extractor parses key facts section bullet lines`() {
        assertTrue(
            "找不到 fact 抽取函数（extractAndPersistFacts / extractFactsFromSummary / " +
                "persistExtractedFacts 任一）—— 先满足 TC-AGENT-016-a。",
            extractorBlock.isNotBlank()
        )

        // (1) 必须 reference 段头：直接字面【关键事实】/[Key Facts] 或 SUMMARY_SECTION_KEY_INFO_* 常量
        val mentionsHeader =
            extractorBlock.contains("【关键事实】") ||
                extractorBlock.contains("[Key Facts]") ||
                Regex("""\bSUMMARY_SECTION_KEY_INFO_(CN|EN)\b""").containsMatchIn(extractorBlock)
        assertTrue(
            "fact 抽取函数体必须 reference 关键事实段头 —— `【关键事实】` / `[Key Facts]` 字面值 " +
                "或 `SUMMARY_SECTION_KEY_INFO_CN/EN` 常量。\n实际函数体:\n$extractorBlock",
            mentionsHeader
        )

        // (2) 必须有 bullet 切分动作（识别 - / * / • / · 任一前缀）
        val hasBulletParse =
            Regex("""startsWith\s*\(\s*"-\s*"""").containsMatchIn(extractorBlock) ||
                Regex("""startsWith\s*\(\s*"\*\s*"""").containsMatchIn(extractorBlock) ||
                Regex("""startsWith\s*\(\s*"•\s*"""").containsMatchIn(extractorBlock) ||
                Regex("""startsWith\s*\(\s*"·\s*"""").containsMatchIn(extractorBlock) ||
                Regex("""Regex\s*\([^)]*[-*•·]""").containsMatchIn(extractorBlock) ||
                Regex("""\bremovePrefix\s*\(\s*"-\s*"""").containsMatchIn(extractorBlock) ||
                Regex("""\bremovePrefix\s*\(\s*"\*\s*"""").containsMatchIn(extractorBlock)
        assertTrue(
            "fact 抽取函数体必须含 bullet 切分代码 —— `startsWith(\"- \")` / `startsWith(\"* \")` / " +
                "`startsWith(\"• \")` / `removePrefix(\"- \")` / Regex 含 `-`/`*`/`•` 之一。\n" +
                "实际函数体:\n$extractorBlock",
            hasBulletParse
        )
    }

    /**
     * TC-AGENT-016-c: 每条 fact 必须独立落库 + 打 `#auto_extracted` tag。
     */
    @Test
    fun `TC-AGENT-016-c each fact saved separately with auto_extracted tag`() {
        assertTrue("找不到 fact 抽取函数 —— 先满足 TC-AGENT-016-a。", extractorBlock.isNotBlank())

        assertTrue(
            "fact 抽取函数体必须含 `#auto_extracted` 字面字符串。",
            extractorBlock.contains("#auto_extracted")
        )

        assertTrue(
            "fact 抽取函数体必须调 `saveMemory(...)` 落库每条 fact。",
            Regex("""\.saveMemory\s*\(""").containsMatchIn(extractorBlock)
        )

        assertTrue(
            "fact 抽取函数体必须调 `addTagToMemory(...)` 给每条 fact 打 tag。",
            Regex("""\.addTagToMemory\s*\(""").containsMatchIn(extractorBlock)
        )
    }

    /**
     * TC-AGENT-016-d: 单条 fact content 必须 800 字符截断。
     */
    @Test
    fun `TC-AGENT-016-d fact content truncated at 800 chars`() {
        assertTrue("找不到 fact 抽取函数 —— 先满足 TC-AGENT-016-a。", extractorBlock.isNotBlank())

        val hasTake =
            Regex("""\.take\s*\(\s*800\s*\)""").containsMatchIn(extractorBlock) ||
                Regex("""MAX_FACT_CONTENT_CHARS\s*=\s*800\b""").containsMatchIn(source) ||
                Regex("""FACT_CONTENT_LIMIT\s*=\s*800\b""").containsMatchIn(source) ||
                Regex("""MAX_FACT_LEN\s*=\s*800\b""").containsMatchIn(source) ||
                Regex("""\.take\s*\(\s*\w*FACT\w*\s*\)""").containsMatchIn(extractorBlock)
        assertTrue(
            "fact 抽取函数体必须对单条 fact content 做 800 字符截断 —— " +
                "源码内含 `take(800)` / `MAX_FACT_CONTENT_CHARS = 800` / `FACT_CONTENT_LIMIT = 800` 之一。",
            hasTake
        )
    }

    /**
     * TC-AGENT-016-e (2026-06-15 收紧): 单次抽取最多 **10 条** fact（原 20）。
     * `#auto_extracted` 雪球元凶之一——把单次抽取上限砍半，逼模型只挑最重要的事实。
     */
    @Test
    fun `TC-AGENT-016-e fact count capped at 10`() {
        assertTrue("找不到 fact 抽取函数 —— 先满足 TC-AGENT-016-a。", extractorBlock.isNotBlank())

        val hasCap =
            Regex("""\.take\s*\(\s*10\s*\)""").containsMatchIn(extractorBlock) ||
                Regex("""coerceAtMost\s*\(\s*10\s*\)""").containsMatchIn(extractorBlock) ||
                Regex("""minOf\s*\([^)]*\b10\b""").containsMatchIn(extractorBlock) ||
                Regex("""MAX_FACT_COUNT\s*=\s*10\b""").containsMatchIn(source) ||
                Regex("""MAX_FACTS_PER_SUMMARY\s*=\s*10\b""").containsMatchIn(source) ||
                Regex("""FACT_COUNT_LIMIT\s*=\s*10\b""").containsMatchIn(source)
        assertTrue(
            "fact 抽取函数体必须对单次抽取数量做 10 条上限（2026-06-15 从 20 收紧）—— " +
                "源码内含 `take(10)` / `coerceAtMost(10)` / `MAX_FACT_COUNT = 10` 之一。",
            hasCap
        )

        // 红线：不得再用旧的 20 上限（commit-a R-AGENT-016 收紧的核心）
        assertTrue(
            "TC-AGENT-016-e 红线：源码不得再含 `MAX_FACT_COUNT = 20` 旧常量定义（已收紧到 10）。",
            !Regex("""MAX_FACT_COUNT\s*=\s*20\b""").containsMatchIn(source)
        )
    }

    /**
     * TC-AGENT-016-f: 必须打 `#chat:` 来源 tag。
     *
     * R-AGENT-027 (2026-06-13)：删掉了 `#auto_summary_id:<parentId>` 父节点引用 tag 断言。
     * 原本意图是反查"这条 fact 来自哪段摘要"，但全代码库无任何读取方，且 R-AGENT-026 的
     * keepDecision=false 路径会产生 `#auto_summary_id:-1` 孤儿污染。`#chat:<chatId>` 已足够。
     */
    @Test
    fun `TC-AGENT-016-f facts get chat tag`() {
        assertTrue("找不到 fact 抽取函数 —— 先满足 TC-AGENT-016-a。", extractorBlock.isNotBlank())

        assertTrue(
            "fact 抽取函数体必须含 `#chat:` 来源 tag 字面字符串。",
            extractorBlock.contains("#chat:")
        )

        // R-AGENT-027 守红线：函数体不得再写 #auto_summary_id: tag（已经认定为冗余）
        assertTrue(
            "R-AGENT-027: fact 抽取函数体不得再写 `#auto_summary_id:` tag —— 已认定为设计冗余。",
            !Regex("""addTagToMemory[\s\S]{0,80}#auto_summary_id:""").containsMatchIn(extractorBlock)
        )
    }

    /**
     * TC-AGENT-016-g (2026-06-15 收紧): 去重升级到 **3-gram jaccard 0.75**。
     *
     * **背景**：原 lowercase exact 比对——同义改写（"用户喜欢 Tailwind" / "User likes Tailwind CSS"
     * / "偏好 Tailwind 框架"）就被绕开，导致 `#auto_extracted` 节点雪球式增长（用户报告
     * "记忆库数量太多了"的 #1 元凶）。复用 R-AGENT-023 已有的 `computeAutoSummaryNgrams` +
     * `computeAutoSummaryJaccard` helper（同一文件），阈值 0.75（与父 #auto_summary dedup 同
     * 阈值，行为一致）。同时把 baseline `take(200)` 提到 `take(1000)` 避免少召回。
     */
    @Test
    fun `TC-AGENT-016-g fact extractor dedupes against existing auto_extracted nodes via jaccard`() {
        assertTrue("找不到 fact 抽取函数 —— 先满足 TC-AGENT-016-a。", extractorBlock.isNotBlank())

        // 必须 reference searchMemories（dedup 前置查询）
        assertTrue(
            "fact 抽取函数体必须调 `searchMemories(...)` 做 dedup 前置查询 —— " +
                "否则每次自动摘要都会重复落库相同 fact。",
            Regex("""\bsearchMemories\s*\(""").containsMatchIn(extractorBlock)
        )

        // searchMemories 调用必须用 `#auto_extracted` tag 限定查询范围
        val hasTagsScopedDedup =
            Regex("""searchMemories\s*\([\s\S]*?#auto_extracted""").containsMatchIn(extractorBlock) ||
                Regex("""tags\s*=\s*listOf\s*\(\s*"#auto_extracted"""").containsMatchIn(extractorBlock) ||
                Regex("""tags\s*=\s*listOf\([^)]*"#auto_extracted"""").containsMatchIn(extractorBlock)
        assertTrue(
            "fact 抽取函数体的 dedup 查询必须用 `tags = listOf(\"#auto_extracted\")` 限定查询范围 —— " +
                "源码 searchMemories 调用上下文需含 `#auto_extracted` 字面值在 tags 参数里。\n" +
                "实际函数体:\n$extractorBlock",
            hasTagsScopedDedup
        )

        // (2026-06-15 收紧) 必须用 jaccard 升级版 dedup
        assertTrue(
            "TC-AGENT-016-g (2026-06-15): fact 抽取块必须调用 `computeAutoSummaryNgrams(` —— " +
                "复用 R-AGENT-023 的 3-gram helper 做相似度比对（替换 lowercase exact 雪球元凶）。\n" +
                "实际函数体:\n$extractorBlock",
            Regex("""\bcomputeAutoSummaryNgrams\s*\(""").containsMatchIn(extractorBlock)
        )
        assertTrue(
            "TC-AGENT-016-g (2026-06-15): fact 抽取块必须调用 `computeAutoSummaryJaccard(` —— " +
                "复用 R-AGENT-023 的 jaccard helper。\n实际函数体:\n$extractorBlock",
            Regex("""\bcomputeAutoSummaryJaccard\s*\(""").containsMatchIn(extractorBlock)
        )
        assertTrue(
            "TC-AGENT-016-g (2026-06-15): fact 抽取块必须含阈值 `0.75` 字面值 —— " +
                "与父 #auto_summary dedup 同阈值，保持行为一致。\n实际函数体:\n$extractorBlock",
            extractorBlock.contains("0.75")
        )

        // baseline 必须 take(1000) 或同等数量（避免少召回——原 take(200) 当用户库变大就可能漏召旧 fact）
        val hasLargerBaseline =
            Regex("""\.take\s*\(\s*1000\s*\)""").containsMatchIn(extractorBlock) ||
                Regex("""\.take\s*\(\s*\w*FACT\w*BASELINE\w*\s*\)""").containsMatchIn(extractorBlock) ||
                Regex("""DEDUP_BASELINE\s*=\s*1000\b""").containsMatchIn(source)
        assertTrue(
            "TC-AGENT-016-g (2026-06-15): fact 抽取块的 dedup baseline 必须 `take(1000)` 或更大 " +
                "—— 原 `take(200)` 当用户 #auto_extracted 库变大时会漏召旧 fact。\n" +
                "实际函数体:\n$extractorBlock",
            hasLargerBaseline
        )

        // 红线：不得再用 lowercase exact dedup（trim().lowercase() 形成的 Set<String> + factKey in set）
        // 注意：computeAutoSummaryNgrams 内部也会 lowercase()，所以仅扫 fact 抽取函数体内的 .lowercase() 链
        // 同时排除作为 search 文本输入的 lowercase 调用，重点抓"作为 dedup key 直接放进 Set"的旧路径
        val oldExactDedupPattern = Regex(
            """\.content\s*\.\s*trim\s*\(\s*\)\s*\.\s*lowercase\s*\(\s*\)"""
        )
        assertTrue(
            "TC-AGENT-016-g 红线：fact 抽取函数体不得再含 `.content.trim().lowercase()` 旧 lowercase exact dedup 链 —— " +
                "已升级到 jaccard。\n实际函数体:\n$extractorBlock",
            !oldExactDedupPattern.containsMatchIn(extractorBlock)
        )
    }

    /**
     * TC-AGENT-016-h: 失败容忍：fact 抽取整段必须 try-catch 包围。
     */
    @Test
    fun `TC-AGENT-016-h fact extractor wrapped in try catch`() {
        assertTrue("找不到 fact 抽取函数 —— 先满足 TC-AGENT-016-a。", extractorBlock.isNotBlank())

        assertTrue(
            "fact 抽取函数体必须含 `try {` 块。",
            extractorBlock.contains("try {")
        )
        assertTrue(
            "fact 抽取函数体必须含 `catch (` —— 解析异常 / 单条 saveMemory 异常都不能拖垮父 " +
                "`#auto_summary` 落库。",
            Regex("""\bcatch\s*\(""").containsMatchIn(extractorBlock)
        )
    }

    /**
     * TC-AGENT-016-i: i18n 完整性：必须根据 useEnglish 选 EN 或 CN 段头。
     */
    @Test
    fun `TC-AGENT-016-i fact extractor handles both languages`() {
        assertTrue("找不到 fact 抽取函数 —— 先满足 TC-AGENT-016-a。", extractorBlock.isNotBlank())

        // 函数签名或函数体必须含 useEnglish 形参
        val hasUseEnglish =
            Regex("""\buseEnglish\b""").containsMatchIn(extractorBlock)
        assertTrue(
            "fact 抽取函数必须接受 `useEnglish` 形参（透传自 launchAsyncSummaryForSend / " +
                "summarizeHistory），用于选 SUMMARY_PROMPT_EN / SUMMARY_PROMPT 对应的段头。",
            hasUseEnglish
        )

        // 函数体或同 source 文件必须同时 reference 中英文两个段头常量或字面值
        val hasBothLangs =
            (extractorBlock.contains("【关键事实】") || extractorBlock.contains("SUMMARY_SECTION_KEY_INFO_CN")) &&
                (extractorBlock.contains("[Key Facts]") || extractorBlock.contains("SUMMARY_SECTION_KEY_INFO_EN"))
        assertTrue(
            "fact 抽取函数体必须同时 reference 中英文两个段头 —— " +
                "中文：`【关键事实】` 字面值或 `SUMMARY_SECTION_KEY_INFO_CN` 常量；" +
                "英文：`[Key Facts]` 字面值或 `SUMMARY_SECTION_KEY_INFO_EN` 常量。\n" +
                "实际函数体:\n$extractorBlock",
            hasBothLangs
        )
    }

    // ----- helpers -----

    private fun extractFunctionBlock(src: String, name: String): String {
        val lines = src.lines()
        val startIdx = lines.indexOfFirst {
            it.contains("fun $name(") || it.contains("fun $name ")
        }
        check(startIdx >= 0) { "找不到 fun $name 签名" }
        val rest = lines.subList(startIdx + 1, lines.size)
        val nextFunOffset = rest.indexOfFirst { line ->
            val t = line.trimStart()
            Regex("""^(private |internal |public |protected )?(suspend )?fun \w+\s*[(<]""")
                .containsMatchIn(t)
        }
        val endIdx = if (nextFunOffset < 0) lines.size else startIdx + 1 + nextFunOffset
        return lines.subList(startIdx, endIdx).joinToString("\n")
    }

    private fun stripComments(src: String): String {
        val out = StringBuilder()
        var i = 0
        var inString = false
        var inChar = false
        var inBlock = false
        var inLine = false
        while (i < src.length) {
            val c = src[i]
            val next = if (i + 1 < src.length) src[i + 1] else '\u0000'
            when {
                inLine -> {
                    if (c == '\n') {
                        inLine = false
                        out.append('\n')
                    }
                    i++
                }
                inBlock -> {
                    if (c == '*' && next == '/') {
                        inBlock = false
                        i += 2
                    } else {
                        if (c == '\n') out.append('\n')
                        i++
                    }
                }
                inString -> {
                    out.append(c)
                    if (c == '\\' && i + 1 < src.length) {
                        out.append(src[i + 1])
                        i += 2
                        continue
                    }
                    if (c == '"') inString = false
                    i++
                }
                inChar -> {
                    out.append(c)
                    if (c == '\\' && i + 1 < src.length) {
                        out.append(src[i + 1])
                        i += 2
                        continue
                    }
                    if (c == '\'') inChar = false
                    i++
                }
                c == '/' && next == '/' -> { inLine = true; i += 2 }
                c == '/' && next == '*' -> { inBlock = true; i += 2 }
                c == '"' -> { inString = true; out.append(c); i++ }
                c == '\'' -> { inChar = true; out.append(c); i++ }
                else -> { out.append(c); i++ }
            }
        }
        return out.toString()
    }

    private fun delegatePath(): String {
        val candidates = listOf(
            File("src/main/java/com/ai/assistance/operit/services/core/MessageCoordinationDelegate.kt"),
            File("app/src/main/java/com/ai/assistance/operit/services/core/MessageCoordinationDelegate.kt")
        )
        return candidates.firstOrNull { it.exists() }?.path
            ?: error("Cannot locate MessageCoordinationDelegate.kt — cwd=${File(".").absolutePath}")
    }
}
