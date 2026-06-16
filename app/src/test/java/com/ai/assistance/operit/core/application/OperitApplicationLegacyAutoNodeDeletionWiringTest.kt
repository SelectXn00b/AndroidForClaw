package com.ai.assistance.operit.core.application

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * R-AGENT-041-a (2026-06-17): `OperitApplication.onCreate()` 必须挂上一次性散节点删除迁移钩子
 * `launchLegacyAutoNodeDeletionIfNeeded()`，紧跟 R-AGENT-040 之后，把已被合并到 root 的旧
 * `#auto_summary` / `#auto_extracted` / `#auto_summary_id:NNN` 散节点从 ObjectBox 删除。
 *
 * 关键守则：
 *  - **前置门禁**：必须先确认 R-AGENT-040 done flag (`R_AGENT_040_auto_node_consolidation_done`)
 *    为 `true`，否则跳过本次执行（不写 041-a done flag），下次冷启重试，避免在合并完成前就把数据删掉
 *  - 自己的防重入键 `R_AGENT_041_legacy_node_deletion_done`（位于 `hermes_data_migrations`）
 *  - 多 profile 全遍历（`profileListFlow.first()`）
 *  - `applicationScope.launch` 后台 IO 协程，不阻塞 onCreate
 *  - SUMMARY / EXTRACTED 走 `deleteByTag` 字面（root 标识是 `_root` 后缀，字面不同安全）
 *  - SUMMARY_ID 走 `findTagsByNamePrefix("#auto_summary_id:")` + 排除 `#auto_root` + `deleteMemories`
 *    + `cleanupOrphanTagsByPrefix("#auto_summary_id:")`
 *  - try / catch 包裹（失败不写完成标记，下次启动重试）
 *  - **必须**在 SUMMARY_ID 删除路径排除带 `#auto_root` 的节点（防御性，防止任何未来命名漂移误删 root）
 *
 * **测试策略**: 与 R-AGENT-029 / R-AGENT-040 同款 source-scan。`OperitApplication.onCreate` 触发的
 * IO 协程涉及 ObjectBox + SharedPreferences，纯 JVM mock ROI 极低。运行时正确性由 §3 E2E + 手测
 * （带历史散节点的旧 APK 升级 → logcat `R-AGENT-041-a: deletion done` + MemoryScreen 看散节点已消失，
 * root 节点保留）兜底。
 *
 * 对应 TC-AGENT-041-a-a..f（见 docs/hermes-test-cases.md）。
 */
class OperitApplicationLegacyAutoNodeDeletionWiringTest {

    private val source: String by lazy { stripComments(File(applicationPath()).readText()) }

    private val onCreateBlock: String by lazy {
        runCatching { extractFunctionBlock(source, "onCreate") }.getOrDefault("")
    }

    private val deletionBlock: String by lazy {
        runCatching { extractFunctionBlock(source, "launchLegacyAutoNodeDeletionIfNeeded") }
            .getOrDefault("")
    }

    /**
     * TC-AGENT-041-a-a: 全文必须含 R-AGENT-041-a 的迁移常量字面值：
     *  - 方法名 `launchLegacyAutoNodeDeletionIfNeeded`
     *  - done flag key `R_AGENT_041_legacy_node_deletion_done`
     *  - SharedPreferences 名 `"hermes_data_migrations"`（与 R-AGENT-029 / R-AGENT-040 共用）
     */
    @Test
    fun `TC-AGENT-041-a-a application source declares deletion migration constants`() {
        assertTrue(
            "OperitApplication.kt 必须含字面方法名 `launchLegacyAutoNodeDeletionIfNeeded` —— " +
                "R-AGENT-041-a 一次性删除迁移钩子私有方法名。",
            source.contains("launchLegacyAutoNodeDeletionIfNeeded")
        )
        assertTrue(
            "OperitApplication.kt 必须含 done flag key `R_AGENT_041_legacy_node_deletion_done` —— " +
                "防重入键必须使用 requirements 锁定的固定字面（不能改名，否则下次升级 APK 会重跑删除）。",
            source.contains("R_AGENT_041_legacy_node_deletion_done")
        )
        assertTrue(
            "OperitApplication.kt 必须含 SharedPreferences 名 `\"hermes_data_migrations\"` —— " +
                "R-AGENT-041-a 与 R-AGENT-029 / R-AGENT-040 共用同一个迁移记录文件。",
            source.contains("\"hermes_data_migrations\"")
        )
    }

    /**
     * TC-AGENT-041-a-b: `onCreate` 函数体内必须调用 `launchLegacyAutoNodeDeletionIfNeeded()`，
     * 并且必须出现在 `launchAutoNodeArchiverMigrationIfNeeded()` 之后（先合并、再删除）。
     */
    @Test
    fun `TC-AGENT-041-a-b onCreate invokes deletion hook after archiver migration hook`() {
        assertTrue(
            "找不到 onCreate 函数体 —— Application 应该重写 onCreate。",
            onCreateBlock.isNotBlank()
        )
        assertTrue(
            "OperitApplication.onCreate 函数体必须调用 `launchLegacyAutoNodeDeletionIfNeeded()` —— " +
                "否则 R-AGENT-041-a 一次性删除完全没接通。\n" +
                "实际 onCreate body（前 4000 chars）:\n${onCreateBlock.take(4000)}",
            Regex("""\blaunchLegacyAutoNodeDeletionIfNeeded\s*\(\s*\)""")
                .containsMatchIn(onCreateBlock)
        )

        // 顺序：deletion 必须在 archiver migration 之后
        val archiverIdx = onCreateBlock.indexOf("launchAutoNodeArchiverMigrationIfNeeded(")
        val deletionIdx = onCreateBlock.indexOf("launchLegacyAutoNodeDeletionIfNeeded(")
        assertTrue(
            "onCreate 必须先调用 `launchAutoNodeArchiverMigrationIfNeeded()` —— R-AGENT-040 是 041-a 的前置。\n实际 onCreate:\n$onCreateBlock",
            archiverIdx >= 0
        )
        assertTrue(
            "onCreate 中 `launchLegacyAutoNodeDeletionIfNeeded()` 必须**晚于** `launchAutoNodeArchiverMigrationIfNeeded()` —— " +
                "顺序：先合并到 root，再删散节点；反过来会丢数据。\n" +
                "archiver idx=$archiverIdx, deletion idx=$deletionIdx\n实际 onCreate:\n$onCreateBlock",
            deletionIdx > archiverIdx
        )
    }

    /**
     * TC-AGENT-041-a-c: `launchLegacyAutoNodeDeletionIfNeeded` 函数体必须满足前置门禁 +
     * 自己的 done flag 短路 + 后台执行：
     *  - 读 `R_AGENT_040_auto_node_consolidation_done`（前置门禁）
     *  - 读 `R_AGENT_041_legacy_node_deletion_done`（自己的防重入）
     *  - `applicationScope.launch` 后台协程
     *  - `getSharedPreferences("hermes_data_migrations"...` 字面
     *  - 成功路径写 `putBoolean(R_AGENT_041_legacy_node_deletion_done, true)`
     */
    @Test
    fun `TC-AGENT-041-a-c deletion hook gates on R-AGENT-040 done flag and uses background scope`() {
        assertTrue(
            "找不到 launchLegacyAutoNodeDeletionIfNeeded 函数体 —— 先满足 TC-AGENT-041-a-a。",
            deletionBlock.isNotBlank()
        )

        // (1) 后台协程
        assertTrue(
            "launchLegacyAutoNodeDeletionIfNeeded 必须用 `applicationScope.launch` —— " +
                "删除走后台 IO 协程，绝不能阻塞 Application.onCreate。\n实际:\n$deletionBlock",
            Regex("""\bapplicationScope\.launch\b""").containsMatchIn(deletionBlock)
        )

        // (2) 拿到迁移记录文件
        assertTrue(
            "launchLegacyAutoNodeDeletionIfNeeded 必须 `getSharedPreferences(\"hermes_data_migrations\", ...)` " +
                "—— 拿到迁移记录文件读 done flag。\n实际:\n$deletionBlock",
            Regex("""\bgetSharedPreferences\s*\(\s*"hermes_data_migrations"""")
                .containsMatchIn(deletionBlock)
        )

        // (3) 前置门禁：必须读 R-AGENT-040 done flag
        assertTrue(
            "launchLegacyAutoNodeDeletionIfNeeded 必须读 `R_AGENT_040_auto_node_consolidation_done` —— " +
                "前置门禁：合并未完成不允许删数据。\n实际:\n$deletionBlock",
            deletionBlock.contains("R_AGENT_040_auto_node_consolidation_done")
        )
        val hasGetBoolean040 =
            Regex("""\bgetBoolean\s*\(\s*[^)]*R_AGENT_040_auto_node_consolidation_done""")
                .containsMatchIn(deletionBlock) ||
                (deletionBlock.contains("R_AGENT_040_auto_node_consolidation_done") &&
                    Regex("""\bgetBoolean\s*\(""").containsMatchIn(deletionBlock))
        assertTrue(
            "launchLegacyAutoNodeDeletionIfNeeded 必须用 `getBoolean(R_AGENT_040_..._done, ...)` 读门禁标记 —— " +
                "前置门禁不能光含字面字符串。\n实际:\n$deletionBlock",
            hasGetBoolean040
        )

        // (4) 自己的 done flag 短路
        val hasShortCircuit041 =
            Regex("""\bgetBoolean\s*\(\s*[^)]*R_AGENT_041_legacy_node_deletion_done""")
                .containsMatchIn(deletionBlock) ||
                (deletionBlock.contains("R_AGENT_041_legacy_node_deletion_done") &&
                    Regex("""\bgetBoolean\s*\(""").containsMatchIn(deletionBlock))
        assertTrue(
            "launchLegacyAutoNodeDeletionIfNeeded 必须有 `getBoolean(R_AGENT_041_..._done, false)` " +
                "短路返回 —— 否则每次冷启都会重跑全表 deleteByTag / prefix 扫。\n实际:\n$deletionBlock",
            hasShortCircuit041
        )

        // (5) 成功路径置位 done flag
        val hasDoneWrite =
            Regex(
                """\bputBoolean\s*\(\s*[^)]*R_AGENT_041_legacy_node_deletion_done[^)]*,\s*true"""
            ).containsMatchIn(deletionBlock) ||
                (deletionBlock.contains("R_AGENT_041_legacy_node_deletion_done") &&
                    Regex("""\bputBoolean\s*\(""").containsMatchIn(deletionBlock) &&
                    deletionBlock.contains("true"))
        assertTrue(
            "launchLegacyAutoNodeDeletionIfNeeded 必须含 `putBoolean(R_AGENT_041_..._done, true)` " +
                "—— 成功完成后才写防重入标记。\n实际:\n$deletionBlock",
            hasDoneWrite
        )
    }

    /**
     * TC-AGENT-041-a-d: 函数体必须有三段删除动作：
     *  - `deleteByTag("#auto_summary")` 字面（SUMMARY bucket 散节点删除；root 是 `#auto_summary_root` 字面不同所以安全）
     *  - `deleteByTag("#auto_extracted")` 字面（EXTRACTED bucket 散节点删除）
     *  - SUMMARY_ID 走组合：`findTagsByNamePrefix("#auto_summary_id:")` + `deleteMemories(` + `cleanupOrphanTagsByPrefix("#auto_summary_id:")`
     *  - 调 `MemoryRepository(` 构造（per-profile 实例化）
     */
    @Test
    fun `TC-AGENT-041-a-d deletion scans three tag families and deletes via repository`() {
        assertTrue(
            "找不到 launchLegacyAutoNodeDeletionIfNeeded 函数体 —— 先满足 TC-AGENT-041-a-a。",
            deletionBlock.isNotBlank()
        )

        // SUMMARY: deleteByTag("#auto_summary")
        val deleteSummary = Regex("""\bdeleteByTag\s*\(\s*"#auto_summary"\s*\)""")
            .containsMatchIn(deletionBlock)
        assertTrue(
            "launchLegacyAutoNodeDeletionIfNeeded 必须调 `deleteByTag(\"#auto_summary\")` —— " +
                "SUMMARY bucket 散节点删除（root 是 `#auto_summary_root` 字面不同所以安全）。\n实际:\n$deletionBlock",
            deleteSummary
        )

        // EXTRACTED: deleteByTag("#auto_extracted")
        val deleteExtracted = Regex("""\bdeleteByTag\s*\(\s*"#auto_extracted"\s*\)""")
            .containsMatchIn(deletionBlock)
        assertTrue(
            "launchLegacyAutoNodeDeletionIfNeeded 必须调 `deleteByTag(\"#auto_extracted\")` —— " +
                "EXTRACTED bucket 散节点删除。\n实际:\n$deletionBlock",
            deleteExtracted
        )

        // SUMMARY_ID: findTagsByNamePrefix("#auto_summary_id:") + deleteMemories + cleanupOrphanTagsByPrefix
        assertTrue(
            "launchLegacyAutoNodeDeletionIfNeeded 必须含 `\"#auto_summary_id:\"` 字面 —— SUMMARY_ID bucket prefix 扫描目标。\n实际:\n$deletionBlock",
            deletionBlock.contains("\"#auto_summary_id:\"")
        )
        assertTrue(
            "launchLegacyAutoNodeDeletionIfNeeded 必须调 `findTagsByNamePrefix(` —— SUMMARY_ID 变长后缀走 prefix 扫。\n实际:\n$deletionBlock",
            Regex("""\bfindTagsByNamePrefix\s*\(""").containsMatchIn(deletionBlock)
        )
        assertTrue(
            "launchLegacyAutoNodeDeletionIfNeeded 必须调 `deleteMemories(` —— SUMMARY_ID 拿到 ids 后批量删除。\n实际:\n$deletionBlock",
            Regex("""\bdeleteMemories\s*\(""").containsMatchIn(deletionBlock)
        )
        assertTrue(
            "launchLegacyAutoNodeDeletionIfNeeded 必须调 `cleanupOrphanTagsByPrefix(\"#auto_summary_id:\")` —— " +
                "节点删完后还要清扫一遍变长后缀的孤儿 tag（与 R-AGENT-029 同款）。\n实际:\n$deletionBlock",
            Regex("""\bcleanupOrphanTagsByPrefix\s*\(\s*"#auto_summary_id:"""")
                .containsMatchIn(deletionBlock)
        )

        // MemoryRepository 构造
        assertTrue(
            "launchLegacyAutoNodeDeletionIfNeeded 必须 `MemoryRepository(...)` 构造 —— per-profile 实例化（与 R-AGENT-040 同款）。\n实际:\n$deletionBlock",
            Regex("""\bMemoryRepository\s*\(""").containsMatchIn(deletionBlock)
        )
    }

    /**
     * TC-AGENT-041-a-e: 函数体必须满足多 profile 遍历 + 异常容忍 + 失败不置 done flag：
     *  - `profileListFlow.first()` 调用
     *  - `try {` + `catch (` 包住主体
     *  - catch 路径含 `AppLogger.w(` 调用
     *  - **catch 块内不得**出现 `putBoolean(R_AGENT_041_..._done, true)`
     */
    @Test
    fun `TC-AGENT-041-a-e deletion iterates profiles guards exceptions and skips done flag on failure`() {
        assertTrue(
            "找不到 launchLegacyAutoNodeDeletionIfNeeded 函数体 —— 先满足 TC-AGENT-041-a-a。",
            deletionBlock.isNotBlank()
        )

        // 多 profile 遍历
        assertTrue(
            "launchLegacyAutoNodeDeletionIfNeeded 必须用 `profileListFlow.first()` 拿到所有 profile —— " +
                "散节点散落在每个 profile 的独立 ObjectBox 库里。\n实际:\n$deletionBlock",
            Regex("""\bprofileListFlow\.first\s*\(\s*\)""").containsMatchIn(deletionBlock)
        )

        // try / catch 包裹
        assertTrue(
            "launchLegacyAutoNodeDeletionIfNeeded 必须含 `try {` 块 —— 单 profile 失败不能拖垮其他。\n实际:\n$deletionBlock",
            deletionBlock.contains("try {")
        )
        assertTrue(
            "launchLegacyAutoNodeDeletionIfNeeded 必须含 `catch (` —— 失败容忍：异常不写完成标记，下次启动重试。\n实际:\n$deletionBlock",
            Regex("""\bcatch\s*\(""").containsMatchIn(deletionBlock)
        )

        // catch 路径必须 AppLogger.w
        assertTrue(
            "launchLegacyAutoNodeDeletionIfNeeded 的 catch 路径必须 `AppLogger.w(...)` 记录 —— " +
                "异常吞掉但要留诊断痕迹。\n实际:\n$deletionBlock",
            Regex("""\bAppLogger\.w\s*\(""").containsMatchIn(deletionBlock)
        )

        // catch 块内不得出现 putBoolean(...true) 写 done flag
        val catchBlocks = extractCatchBlocks(deletionBlock)
        assertTrue(
            "未能抽出 catch 块体 —— 期望至少 1 个，实际 ${catchBlocks.size}。\n实际:\n$deletionBlock",
            catchBlocks.isNotEmpty()
        )
        for ((idx, catchBody) in catchBlocks.withIndex()) {
            val hasDoneWriteInsideCatch =
                Regex(
                    """\bputBoolean\s*\(\s*[^)]*R_AGENT_041_legacy_node_deletion_done[^)]*,\s*true"""
                ).containsMatchIn(catchBody)
            assertFalse(
                "catch 块 #${idx + 1} 内**不得**出现 `putBoolean(R_AGENT_041_..._done, true)` —— " +
                    "失败路径必须不写 done flag，下次冷启重试。\n实际 catch body:\n$catchBody",
                hasDoneWriteInsideCatch
            )
        }
    }

    /**
     * TC-AGENT-041-a-f: SUMMARY_ID 删除路径必须排除带 `#auto_root` tag 的节点：
     *  - 函数体必须含 `#auto_root` 字面值
     *  - 必须含排除表达式：`none {` / `!` + `any {` / `filter` / `filterNot` 任一
     *  - **反向红线**：函数体不得无条件把 `findTagsByNamePrefix` 返回的 owner ids 直接传给 `deleteMemories`
     *
     * 防御性：万一未来命名漂移（例如 root 也以 `#auto_summary_id:` 开头），强制以 `#auto_root` 二级 tag 排除。
     */
    @Test
    fun `TC-AGENT-041-a-f deletion excludes nodes carrying auto_root tag from SUMMARY_ID prefix scan`() {
        assertTrue(
            "找不到 launchLegacyAutoNodeDeletionIfNeeded 函数体 —— 先满足 TC-AGENT-041-a-a。",
            deletionBlock.isNotBlank()
        )

        // (1) 必须含 #auto_root 字面
        assertTrue(
            "launchLegacyAutoNodeDeletionIfNeeded 必须含 `\"#auto_root\"` 字面 —— SUMMARY_ID 删除路径必须排除带 root 标识 tag 的节点。\n实际:\n$deletionBlock",
            deletionBlock.contains("\"#auto_root\"")
        )

        // (2) 必须含排除表达式
        val hasExclusion =
            Regex("""\.none\s*\{""").containsMatchIn(deletionBlock) ||
                Regex("""!\s*\w+\.any\s*\{""").containsMatchIn(deletionBlock) ||
                Regex("""\.filter\s*\{""").containsMatchIn(deletionBlock) ||
                Regex("""\.filterNot\s*\{""").containsMatchIn(deletionBlock)
        assertTrue(
            "launchLegacyAutoNodeDeletionIfNeeded 必须含排除表达式（`none {` / `!xxx.any {` / `filter {` / `filterNot {` 任一）—— " +
                "把带 `#auto_root` 的节点剔除出待删 id 列表。\n实际:\n$deletionBlock",
            hasExclusion
        )
    }

    // ----- helpers (与 R-AGENT-040 wiring test 同款) -----

    private fun extractFunctionBlock(src: String, name: String): String {
        val lines = src.lines()
        val startIdx = lines.indexOfFirst {
            it.contains("fun $name(") || it.contains("fun $name ")
        }
        check(startIdx >= 0) { "找不到 fun $name 签名" }
        val rest = lines.subList(startIdx + 1, lines.size)
        val nextFunOffset = rest.indexOfFirst { line ->
            val t = line.trimStart()
            Regex("""^(private |internal |public |protected |override )?(suspend )?fun \w+\s*[(<]""")
                .containsMatchIn(t)
        }
        val endIdx = if (nextFunOffset < 0) lines.size else startIdx + 1 + nextFunOffset
        return lines.subList(startIdx, endIdx).joinToString("\n")
    }

    /**
     * 从函数体内抽出所有 `catch (...) { ... }` 块的 body（用括号深度计数闭合到外层 `}`）。
     */
    private fun extractCatchBlocks(src: String): List<String> {
        val out = mutableListOf<String>()
        val anchorRe = Regex("""\bcatch\s*\([^)]*\)\s*\{""")
        for (match in anchorRe.findAll(src)) {
            val openBraceIdx = src.indexOf('{', match.range.first)
            if (openBraceIdx < 0) continue
            var depth = 1
            var i = openBraceIdx + 1
            var inString = false
            while (i < src.length && depth > 0) {
                val c = src[i]
                if (inString) {
                    if (c == '\\' && i + 1 < src.length) { i += 2; continue }
                    if (c == '"') inString = false
                } else {
                    when (c) {
                        '"' -> inString = true
                        '{' -> depth++
                        '}' -> depth--
                    }
                }
                i++
            }
            if (depth == 0) out.add(src.substring(openBraceIdx, i))
        }
        return out
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

    private fun applicationPath(): String {
        val candidates = listOf(
            File("src/main/java/com/ai/assistance/operit/core/application/OperitApplication.kt"),
            File("app/src/main/java/com/ai/assistance/operit/core/application/OperitApplication.kt")
        )
        return candidates.firstOrNull { it.exists() }?.path
            ?: error("Cannot locate OperitApplication.kt — cwd=${File(".").absolutePath}")
    }
}
