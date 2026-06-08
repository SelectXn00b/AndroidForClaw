package com.ai.assistance.operit.services.core

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * R-AGENT-016 (2026-06-08): `MessageCoordinationDelegate.forcePersistSummaryToMemory` 落
 * `#auto_summary` 整段摘要之后，必须**追加**一步把 SUMMARY_PROMPT 已经稳定输出的
 * `【关键事实】 / [Key Facts]` bullet 段每行解析成独立 fact，逐条落 `MemoryRepository.saveMemory`
 * 并打 `#auto_extracted` + `#chat:<chatId>` + `#auto_summary_id:<parentId>` 三个 tag。
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
     * TC-AGENT-016-e: 单次抽取最多 20 条 fact。
     */
    @Test
    fun `TC-AGENT-016-e fact count capped at 20`() {
        assertTrue("找不到 fact 抽取函数 —— 先满足 TC-AGENT-016-a。", extractorBlock.isNotBlank())

        val hasCap =
            Regex("""\.take\s*\(\s*20\s*\)""").containsMatchIn(extractorBlock) ||
                Regex("""coerceAtMost\s*\(\s*20\s*\)""").containsMatchIn(extractorBlock) ||
                Regex("""minOf\s*\([^)]*\b20\b""").containsMatchIn(extractorBlock) ||
                Regex("""MAX_FACT_COUNT\s*=\s*20\b""").containsMatchIn(source) ||
                Regex("""MAX_FACTS_PER_SUMMARY\s*=\s*20\b""").containsMatchIn(source) ||
                Regex("""FACT_COUNT_LIMIT\s*=\s*20\b""").containsMatchIn(source)
        assertTrue(
            "fact 抽取函数体必须对单次抽取数量做 20 条上限 —— " +
                "源码内含 `take(20)` / `coerceAtMost(20)` / `MAX_FACT_COUNT = 20` 之一。",
            hasCap
        )
    }

    /**
     * TC-AGENT-016-f: 必须打 `#chat:` 来源 tag + `#auto_summary_id:` 父节点引用 tag。
     */
    @Test
    fun `TC-AGENT-016-f facts get chat tag and parent summary id tag`() {
        assertTrue("找不到 fact 抽取函数 —— 先满足 TC-AGENT-016-a。", extractorBlock.isNotBlank())

        assertTrue(
            "fact 抽取函数体必须含 `#chat:` 来源 tag 字面字符串。",
            extractorBlock.contains("#chat:")
        )

        val hasParentRef =
            extractorBlock.contains("#auto_summary_id:") ||
                extractorBlock.contains("#parent_summary:") ||
                extractorBlock.contains("#summary_id:")
        assertTrue(
            "fact 抽取函数体必须含父节点引用 tag —— `#auto_summary_id:` / `#parent_summary:` / " +
                "`#summary_id:` 任一字面字符串。让用户在 MemoryScreen 能反查这条 fact 是从哪段摘要来的。",
            hasParentRef
        )
    }

    /**
     * TC-AGENT-016-g: 去重防御：必须先查 `#auto_extracted` tag 子集做 dedup。
     */
    @Test
    fun `TC-AGENT-016-g fact extractor dedupes against existing auto_extracted nodes`() {
        assertTrue("找不到 fact 抽取函数 —— 先满足 TC-AGENT-016-a。", extractorBlock.isNotBlank())

        // 必须 reference searchMemories（dedup 前置查询）
        assertTrue(
            "fact 抽取函数体必须调 `searchMemories(...)` 做 dedup 前置查询 —— " +
                "否则每次自动摘要都会重复落库相同 fact。",
            Regex("""\bsearchMemories\s*\(""").containsMatchIn(extractorBlock)
        )

        // searchMemories 调用应该用 `#auto_extracted` tag 限定查询范围
        // 接受 listOf("#auto_extracted") 或字面 "#auto_extracted" 在 searchMemories 调用上下文里
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
