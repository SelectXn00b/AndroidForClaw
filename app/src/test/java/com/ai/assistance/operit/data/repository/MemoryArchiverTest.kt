package com.ai.assistance.operit.data.repository

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * R-AGENT-038 phase 1：`MemoryArchiver` 必须把"对话压缩 / 自动抽取 / 历史编号"三大碎片源
 * 结构化合并到 3 个 root 节点 + 冷归档 jsonl，写入端（MessageCoordinationDelegate）切到本类。
 *
 * **测试策略**：与 R-AGENT-013/014/015/016/017/029 同策略走源码字符串扫描。`MemoryArchiver`
 * 重度依赖 ObjectBox `MemoryRepository` + Android `Context.filesDir`，纯 JVM 单测构造代价远高于
 * 源码扫描守住的 wiring。运行时正确性由 §3 E2E + 手测兜底（升级 → MemoryScreen 看 root 节点
 * 是否合并 + filesDir 看 jsonl 是否生成）。
 *
 * 对应 TC-AGENT-038-a..e + g（见 docs/hermes-test-cases.md）。TC-AGENT-038-f 由
 * `MessageCoordinationDelegateAutoNodeWiringTest` 守。
 */
class MemoryArchiverTest {

    private val source: String by lazy { stripComments(File(repoPath()).readText()) }

    private val appendBlock: String by lazy {
        runCatching { extractFunctionBlock(source, "appendToRoot") }.getOrDefault("")
    }

    private val ensureRootBlock: String by lazy {
        runCatching { extractFunctionBlock(source, "ensureRoot") }.getOrDefault("")
    }

    private val appendSliceBlock: String by lazy {
        runCatching { extractFunctionBlock(source, "appendSliceToArchive") }.getOrDefault("")
    }

    private val archiveDirBlock: String by lazy {
        runCatching { extractFunctionBlock(source, "archiveDir") }.getOrDefault("")
    }

    /**
     * TC-AGENT-038-a: lazy root 创建 —— `ensureRoot` 必须在 root 不存在时新建一条 Memory，
     * source = bucket.sourceLabel；并通过 `addTagToMemory` 同时挂 `bucket.rootTag` + `#auto_root`
     * 两个 tag。
     */
    @Test
    fun `TC-AGENT-038-a ensureRoot lazily creates memory with root and auto tags`() {
        assertTrue(
            "找不到 ensureRoot 函数体 —— 检查文件签名是否还在。",
            ensureRootBlock.isNotBlank()
        )

        // 1) 必须先 findMemoriesByTag 查 root 是否已存在，存在则直接返回（避免重复建）
        assertTrue(
            "ensureRoot 必须用 `repository.findMemoriesByTag(bucket.rootTag)` 查既有 root —— " +
                "否则每次写都会新建节点。\n实际:\n$ensureRootBlock",
            Regex("""\brepository\.findMemoriesByTag\s*\(\s*bucket\.rootTag\s*\)""")
                .containsMatchIn(ensureRootBlock)
        )

        // 2) 不存在时 saveMemory 新建
        assertTrue(
            "ensureRoot 必须调 `repository.saveMemory(` 新建 root 节点。",
            Regex("""\brepository\.saveMemory\s*\(""").containsMatchIn(ensureRootBlock)
        )

        // 3) 必须同时挂 rootTag + #auto_root 两个 tag
        assertTrue(
            "ensureRoot 必须 `addTagToMemory(.., bucket.rootTag)` —— 用于按 bucket 反查。",
            Regex("""\baddTagToMemory\s*\([^)]*bucket\.rootTag""")
                .containsMatchIn(ensureRootBlock)
        )
        assertTrue(
            "ensureRoot 必须 `addTagToMemory(.., \"#auto_root\")` —— 用于全 root 横向枚举。",
            Regex("""\baddTagToMemory\s*\([^)]*"#auto_root"""")
                .containsMatchIn(ensureRootBlock)
        )

        // 4) source 必须用 bucket.sourceLabel（而不是 hardcode 字符串）
        assertTrue(
            "ensureRoot 新建 Memory 时 source 字段必须用 `bucket.sourceLabel` —— 否则归档/统计无法按桶分类。",
            Regex("""source\s*=\s*bucket\.sourceLabel""").containsMatchIn(ensureRootBlock)
        )
    }

    /**
     * TC-AGENT-038-b: prepend 顺序 —— `appendToRoot` 必须把新行放在 root content 的**最前**
     * （newest-first），确保最新内容总是在 hot 区顶部。
     */
    @Test
    fun `TC-AGENT-038-b appendToRoot prepends newest line first`() {
        assertTrue(
            "找不到 appendToRoot 函数体。",
            appendBlock.isNotBlank()
        )

        // newest-first 关键签名：updatedLines.add(newLine) 在 addAll(existingLines) 之前
        // 用源码顺序断言：`updatedLines.add(newLine)` 出现位置 < `updatedLines.addAll(existingLines)`
        val addNewIdx = appendBlock.indexOf("updatedLines.add(newLine)")
        val addAllIdx = appendBlock.indexOf("updatedLines.addAll(existingLines)")
        assertTrue(
            "appendToRoot 必须含 `updatedLines.add(newLine)` —— 把新行加到列表。",
            addNewIdx >= 0
        )
        assertTrue(
            "appendToRoot 必须含 `updatedLines.addAll(existingLines)` —— 拼接旧行。",
            addAllIdx >= 0
        )
        assertTrue(
            "newest-first 顺序错误：必须先 add(newLine) 再 addAll(existingLines)，否则新行被沉底。\n" +
                "实际:\n$appendBlock",
            addNewIdx < addAllIdx
        )
    }

    /**
     * TC-AGENT-038-c: rollover —— 当 lines 数超过 `bucket.maxHotLines` 时，必须把最旧
     * `ROLLOVER_SLICE_SIZE` 行切到 jsonl 冷归档；root content 保留剩余（前 N - SLICE）行。
     */
    @Test
    fun `TC-AGENT-038-c appendToRoot rolls over oldest slice when over limit`() {
        assertTrue("找不到 appendToRoot 函数体。", appendBlock.isNotBlank())

        // 必须有 maxHotLines 比较守卫
        assertTrue(
            "appendToRoot 必须用 `updatedLines.size > bucket.maxHotLines` 触发 rollover。\n实际:\n$appendBlock",
            Regex("""updatedLines\.size\s*>\s*bucket\.maxHotLines""").containsMatchIn(appendBlock)
        )

        // 必须把最旧 ROLLOVER_SLICE_SIZE 行切出去：takeLast(ROLLOVER_SLICE_SIZE)
        // （注：updatedLines 是 newest-first，所以"最旧"在末尾，用 takeLast）
        assertTrue(
            "rollover 必须切最旧的 ROLLOVER_SLICE_SIZE 行：`updatedLines.takeLast(ROLLOVER_SLICE_SIZE)`。\n" +
                "newest-first 列表里最旧 = 末尾，必须用 takeLast。\n实际:\n$appendBlock",
            Regex("""updatedLines\.takeLast\s*\(\s*ROLLOVER_SLICE_SIZE\s*\)""")
                .containsMatchIn(appendBlock)
        )

        // root content 保留剩余：dropLast(ROLLOVER_SLICE_SIZE)
        assertTrue(
            "rollover 后 root content 必须留剩余：`updatedLines.dropLast(ROLLOVER_SLICE_SIZE)`。",
            Regex("""updatedLines\.dropLast\s*\(\s*ROLLOVER_SLICE_SIZE\s*\)""")
                .containsMatchIn(appendBlock)
        )

        // 必须有 appendSliceToArchive(bucket, slice) 调用
        assertTrue(
            "rollover 必须调 `appendSliceToArchive(bucket, slice)` 把切片落 jsonl。",
            Regex("""\bappendSliceToArchive\s*\(\s*bucket\s*,\s*slice\s*\)""")
                .containsMatchIn(appendBlock)
        )

        // 必须返回 AppendedWithRollover(rootId, slice.size)
        assertTrue(
            "rollover 必须返回 `AppendResult.AppendedWithRollover(...)` —— 用于上层日志/审计。",
            Regex("""AppendResult\.AppendedWithRollover\s*\(""").containsMatchIn(appendBlock)
        )
    }

    /**
     * TC-AGENT-038-d: dedup —— `appendToRoot` 必须用 3-gram jaccard ≥ 0.75 与现有行比对，
     * 命中即返回 `SkippedDuplicate`，**不**写入。
     */
    @Test
    fun `TC-AGENT-038-d appendToRoot dedups by 3-gram jaccard 0_75`() {
        assertTrue("找不到 appendToRoot 函数体。", appendBlock.isNotBlank())

        // 必须调 ngrams + jaccard
        assertTrue(
            "appendToRoot 必须调 `ngrams(content)` 计算新内容的 3-gram 集合 —— dedup 输入。",
            Regex("""\bngrams\s*\(\s*content\s*\)""").containsMatchIn(appendBlock)
        )
        assertTrue(
            "appendToRoot 必须调 `jaccard(` 算相似度。",
            Regex("""\bjaccard\s*\(""").containsMatchIn(appendBlock)
        )

        // 阈值必须是 0.75（不能是 0.7 / 0.8 / 1.0）
        assertTrue(
            "dedup 阈值必须是 0.75f（与 R-AGENT-018 computeAutoSummaryNgrams 一致），不允许其他值。\n" +
                "实际:\n$appendBlock",
            Regex("""sim\s*>=\s*0\.75f""").containsMatchIn(appendBlock)
        )

        // 命中必须返回 SkippedDuplicate
        assertTrue(
            "dedup 命中必须返回 `AppendResult.SkippedDuplicate`，不写入。",
            Regex("""return\s+AppendResult\.SkippedDuplicate""").containsMatchIn(appendBlock)
        )

        // ngrams 函数本身必须用 n=3 默认参数
        val ngramsBlock = runCatching { extractFunctionBlock(source, "ngrams") }.getOrDefault("")
        assertTrue("找不到 ngrams 私有函数体。", ngramsBlock.isNotBlank())
        assertTrue(
            "ngrams 默认 n=3 必须保留 —— 与 Hermes 上游 R-AGENT-018 computeAutoSummaryNgrams 对齐。",
            Regex("""n:\s*Int\s*=\s*3""").containsMatchIn(ngramsBlock)
        )
    }

    /**
     * TC-AGENT-038-e: IO 失败保护 —— 当 `appendSliceToArchive` 抛异常时，root content
     * 必须**不**被截断（保持 rollover 前状态），返回 `AppendResult.Failed(...)`。
     */
    @Test
    fun `TC-AGENT-038-e appendToRoot returns Failed and keeps root content on io failure`() {
        assertTrue("找不到 appendToRoot 函数体。", appendBlock.isNotBlank())

        // try { appendSliceToArchive(...) } catch (t: Throwable) { ... return AppendResult.Failed(...) }
        assertTrue(
            "rollover 必须用 `try { ... } catch (t: Throwable)` 包住 appendSliceToArchive —— " +
                "否则磁盘满 / IO 错误会丢内容。",
            Regex("""try\s*\{[\s\S]*?appendSliceToArchive[\s\S]*?\}\s*catch\s*\(\s*t:\s*Throwable\s*\)""")
                .containsMatchIn(appendBlock)
        )

        // catch 分支必须返回 AppendResult.Failed
        assertTrue(
            "IO 异常时必须返回 `AppendResult.Failed(...)`，不允许吞掉。",
            Regex("""return\s+AppendResult\.Failed\s*\(""").containsMatchIn(appendBlock)
        )

        // 关键：catch 分支 return 之前**不允许** persistRootContent 被调 —— 否则等于"截了 root 但没留底"
        // 用源码顺序：persistRootContent(root, keepLines) 必须在 catch block 之外
        // 检查方式：appendSliceToArchive 调用与 catch 块在同一 try 内；
        // try-catch 块结束后才出现 persistRootContent(root, keepLines)
        val tryIdx = appendBlock.indexOf("try {")
        val catchEndPattern = Regex("""\}\s*catch\s*\(\s*t:\s*Throwable\s*\)\s*\{[^}]*\}""")
        val catchMatch = catchEndPattern.find(appendBlock, tryIdx)
        assertTrue(
            "找不到完整的 try/catch 结构。\n实际:\n$appendBlock",
            catchMatch != null
        )
        val catchEndIdx = catchMatch!!.range.last
        // persistRootContent(root, keepLines) 必须在 catch 之后才能被调（也即 rollover 成功路径）
        val persistIdx = appendBlock.indexOf("persistRootContent(root, keepLines)")
        assertTrue(
            "rollover 路径里 `persistRootContent(root, keepLines)` 必须在 try/catch **之后**调用 —— " +
                "在 catch 里截 root 等于丢内容。",
            persistIdx > catchEndIdx
        )
    }

    /**
     * TC-AGENT-038-g: 源码结构守护 —— `MemoryArchiver.kt` 文件必须含三大常量阈值 +
     * 归档目录路径前缀 `hermes/memory_archive` + jsonl 写入调用（`appendText(`）+
     * 子目录创建守卫（`mkdirs()`）。
     */
    @Test
    fun `TC-AGENT-038-g archiver has correct constants path and io guards`() {
        assertTrue(
            "MemoryArchiver.kt 必须含 `MAX_HOT_LINES_SUMMARY = 200` —— summary bucket 阈值。",
            Regex("""MAX_HOT_LINES_SUMMARY\s*=\s*200""").containsMatchIn(source)
        )
        assertTrue(
            "MemoryArchiver.kt 必须含 `MAX_HOT_LINES_EXTRACTED = 100`。",
            Regex("""MAX_HOT_LINES_EXTRACTED\s*=\s*100""").containsMatchIn(source)
        )
        assertTrue(
            "MemoryArchiver.kt 必须含 `MAX_HOT_LINES_SUMMARY_ID = 50`。",
            Regex("""MAX_HOT_LINES_SUMMARY_ID\s*=\s*50""").containsMatchIn(source)
        )
        assertTrue(
            "MemoryArchiver.kt 必须含 `ROLLOVER_SLICE_SIZE = 20` —— 每次 rollover 切 20 行。",
            Regex("""ROLLOVER_SLICE_SIZE\s*=\s*20""").containsMatchIn(source)
        )
        assertTrue(
            "MemoryArchiver.kt 必须含 `\"hermes/memory_archive\"` 路径前缀 —— " +
                "和 phase 2 R-AGENT-039 召回路径达成约定。",
            source.contains("\"hermes/memory_archive/")
        )
        assertTrue(
            "MemoryArchiver.kt 必须含 jsonl 写入调用 `appendText(` —— 冷归档落盘。",
            Regex("""\bappendText\s*\(""").containsMatchIn(source)
        )
        assertTrue(
            "appendSliceToArchive 必须含 `mkdirs()` 守卫 —— 首次 rollover 时归档子目录可能未建。",
            Regex("""\bmkdirs\s*\(\s*\)""").containsMatchIn(appendSliceBlock)
        )
        assertTrue(
            "archiveDir 必须用 `context.filesDir` 拼路径 —— 不能用 cacheDir / externalDir。",
            Regex("""context\.filesDir""").containsMatchIn(archiveDirBlock)
        )

        // 三个 enum 值必须各自带正确 dirName
        assertTrue(
            "ArchiveBucket.SUMMARY 必须带 dirName=\"auto_summary\"。",
            Regex("""SUMMARY\s*\([^)]*dirName\s*=\s*"auto_summary"""", RegexOption.DOT_MATCHES_ALL)
                .containsMatchIn(source)
        )
        assertTrue(
            "ArchiveBucket.EXTRACTED 必须带 dirName=\"auto_extracted\"。",
            Regex("""EXTRACTED\s*\([^)]*dirName\s*=\s*"auto_extracted"""", RegexOption.DOT_MATCHES_ALL)
                .containsMatchIn(source)
        )
        assertTrue(
            "ArchiveBucket.SUMMARY_ID 必须带 dirName=\"auto_summary_id\"。",
            Regex("""SUMMARY_ID\s*\([^)]*dirName\s*=\s*"auto_summary_id"""", RegexOption.DOT_MATCHES_ALL)
                .containsMatchIn(source)
        )
    }

    // ----- helpers (与 MemoryRepositoryOrphanTagCleanupTest 同款) -----

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

    private fun repoPath(): String {
        val candidates = listOf(
            File("src/main/java/com/ai/assistance/operit/data/repository/MemoryArchiver.kt"),
            File("app/src/main/java/com/ai/assistance/operit/data/repository/MemoryArchiver.kt")
        )
        return candidates.firstOrNull { it.exists() }?.path
            ?: error("Cannot locate MemoryArchiver.kt — cwd=${File(".").absolutePath}")
    }
}
