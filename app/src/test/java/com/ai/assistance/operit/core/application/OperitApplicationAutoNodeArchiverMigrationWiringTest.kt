package com.ai.assistance.operit.core.application

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * R-AGENT-040 (2026-06-17): `OperitApplication.onCreate()` 必须挂上一次性自动节点归档迁移钩子
 * `launchAutoNodeArchiverMigrationIfNeeded()`，把 phase 1 之前装的旧 APK 写下的散
 * `#auto_summary` / `#auto_extracted` / `#auto_summary_id:NNN` 节点合并到 R-AGENT-038
 * 3 个 root 节点。
 *
 * 关键守则：
 *  - SharedPreferences 防重入键 `R_AGENT_040_auto_node_consolidation_done`（位于
 *    `hermes_data_migrations` 文件，与 R-AGENT-029 共用）
 *  - 多 profile 全遍历（`profileListFlow.first()`）
 *  - `applicationScope.launch` 后台 IO 协程，不阻塞 onCreate
 *  - 三段扫描：`findMemoriesByTag("#auto_summary")` / `findMemoriesByTag("#auto_extracted")` /
 *    `findTagsByNamePrefix("#auto_summary_id:")`，对应 `ArchiveBucket.SUMMARY` /
 *    `ArchiveBucket.EXTRACTED` / `ArchiveBucket.SUMMARY_ID`
 *  - chatId 从节点 `tags` 找 `#chat:` 前缀提取（Memory 实体无 chatId 字段）
 *  - try / catch 包裹（失败不写完成标记，下次启动重试）
 *  - 不删旧节点（保险起见 phase 2 留着，R-AGENT-041 才删）
 *
 * **测试策略**: 与 R-AGENT-029 同款 source-scan。`OperitApplication.onCreate` 触发的 IO
 * 协程涉及 ObjectBox + Context.filesDir + SharedPreferences，纯 JVM mock ROI 极低。
 * 运行时正确性由 §3 E2E 全套 + 手测（带历史散节点的旧 APK 升级 → logcat
 * `R-AGENT-040: migration done` + MemoryScreen 看 root 节点 content 含历史 summary）兜底。
 *
 * 对应 TC-AGENT-040-a..f（见 docs/hermes-test-cases.md）。
 */
class OperitApplicationAutoNodeArchiverMigrationWiringTest {

    private val source: String by lazy { stripComments(File(applicationPath()).readText()) }

    private val onCreateBlock: String by lazy {
        runCatching { extractFunctionBlock(source, "onCreate") }.getOrDefault("")
    }

    private val migrationBlock: String by lazy {
        runCatching { extractFunctionBlock(source, "launchAutoNodeArchiverMigrationIfNeeded") }
            .getOrDefault("")
    }

    /**
     * TC-AGENT-040-a: 全文必须含 R-AGENT-040 的迁移常量字面值：
     *  - 方法名 `launchAutoNodeArchiverMigrationIfNeeded`
     *  - done flag key `R_AGENT_040_auto_node_consolidation_done`
     *  - SharedPreferences 名 `"hermes_data_migrations"`（与 R-AGENT-029 共用）
     */
    @Test
    fun `TC-AGENT-040-a application source declares migration constants`() {
        assertTrue(
            "OperitApplication.kt 必须含字面方法名 `launchAutoNodeArchiverMigrationIfNeeded` —— " +
                "R-AGENT-040 一次性迁移钩子私有方法名。",
            source.contains("launchAutoNodeArchiverMigrationIfNeeded")
        )
        assertTrue(
            "OperitApplication.kt 必须含 done flag key `R_AGENT_040_auto_node_consolidation_done` —— " +
                "防重入键必须使用 requirements 锁定的固定字面（不能改名，否则下次升级 APK 会重跑迁移）。",
            source.contains("R_AGENT_040_auto_node_consolidation_done")
        )
        assertTrue(
            "OperitApplication.kt 必须含 SharedPreferences 名 `\"hermes_data_migrations\"` —— " +
                "R-AGENT-040 与 R-AGENT-029 共用同一个迁移记录文件。",
            source.contains("\"hermes_data_migrations\"")
        )
    }

    /**
     * TC-AGENT-040-b: `onCreate` 函数体内必须调用 `launchAutoNodeArchiverMigrationIfNeeded()`。
     */
    @Test
    fun `TC-AGENT-040-b onCreate invokes migration hook`() {
        assertTrue(
            "找不到 onCreate 函数体 —— Application 应该重写 onCreate。",
            onCreateBlock.isNotBlank()
        )
        assertTrue(
            "OperitApplication.onCreate 函数体必须调用 `launchAutoNodeArchiverMigrationIfNeeded()` —— " +
                "否则 R-AGENT-040 一次性迁移完全没接通。\n" +
                "实际 onCreate body（前 4000 chars）:\n${onCreateBlock.take(4000)}",
            Regex("""\blaunchAutoNodeArchiverMigrationIfNeeded\s*\(\s*\)""")
                .containsMatchIn(onCreateBlock)
        )
    }

    /**
     * TC-AGENT-040-c: `launchAutoNodeArchiverMigrationIfNeeded` 函数体必须满足后台执行 +
     * done flag 短路：
     *  - `applicationScope.launch` / 等价后台协程（不阻塞主线程 onCreate）
     *  - `getSharedPreferences("hermes_data_migrations"...` 字面（拿到迁移记录文件）
     *  - `getBoolean(` + done flag key 的早 return 短路
     *  - `prefs.edit().putBoolean(` + done flag key + `true` 字面（成功路径置位）
     */
    @Test
    fun `TC-AGENT-040-c migration hook uses background scope and done flag short-circuit`() {
        assertTrue(
            "找不到 launchAutoNodeArchiverMigrationIfNeeded 函数体 —— 先满足 TC-AGENT-040-a。",
            migrationBlock.isNotBlank()
        )

        // (1) 后台协程
        assertTrue(
            "launchAutoNodeArchiverMigrationIfNeeded 必须用 `applicationScope.launch` —— " +
                "迁移走后台 IO 协程，绝不能阻塞 Application.onCreate。\n实际:\n$migrationBlock",
            Regex("""\bapplicationScope\.launch\b""").containsMatchIn(migrationBlock)
        )

        // (2) 拿到迁移记录文件
        assertTrue(
            "launchAutoNodeArchiverMigrationIfNeeded 必须 `getSharedPreferences(\"hermes_data_migrations\", ...)` " +
                "—— 拿到迁移记录文件读 done flag。\n实际:\n$migrationBlock",
            Regex("""\bgetSharedPreferences\s*\(\s*"hermes_data_migrations"""")
                .containsMatchIn(migrationBlock)
        )

        // (3) done flag 短路：必须含 `getBoolean(` + done flag key 的早 return
        val hasShortCircuit =
            Regex("""\bgetBoolean\s*\(\s*[^)]*R_AGENT_040_auto_node_consolidation_done""")
                .containsMatchIn(migrationBlock) ||
                (migrationBlock.contains("R_AGENT_040_auto_node_consolidation_done") &&
                    Regex("""\bgetBoolean\s*\(""").containsMatchIn(migrationBlock))
        assertTrue(
            "launchAutoNodeArchiverMigrationIfNeeded 必须有 `getBoolean(R_AGENT_040_..._done, false)` " +
                "短路返回 —— 否则每次冷启都会重跑全表扫描。\n实际:\n$migrationBlock",
            hasShortCircuit
        )

        // (4) 成功路径置位 done flag
        val hasDoneWrite =
            Regex(
                """\bputBoolean\s*\(\s*[^)]*R_AGENT_040_auto_node_consolidation_done[^)]*,\s*true"""
            ).containsMatchIn(migrationBlock) ||
                (migrationBlock.contains("R_AGENT_040_auto_node_consolidation_done") &&
                    Regex("""\bputBoolean\s*\(""").containsMatchIn(migrationBlock) &&
                    migrationBlock.contains("true"))
        assertTrue(
            "launchAutoNodeArchiverMigrationIfNeeded 必须含 `putBoolean(R_AGENT_040_..._done, true)` " +
                "—— 成功完成后才写防重入标记。\n实际:\n$migrationBlock",
            hasDoneWrite
        )
    }

    /**
     * TC-AGENT-040-d: 函数体必须扫描三段 tag 并经 archiver 落库：
     *  - `"#auto_summary"` 字面 + `findMemoriesByTag(` 调用
     *  - `"#auto_extracted"` 字面 + `findMemoriesByTag(` 调用
     *  - `"#auto_summary_id:"` 字面 + `findTagsByNamePrefix(` 调用（变长后缀走 prefix 扫）
     *  - 引用 `ArchiveBucket.SUMMARY` / `.EXTRACTED` / `.SUMMARY_ID` 三个枚举
     *  - 调 `appendToRoot(` 至少一次（迁移落库动作）
     *  - 调 `MemoryArchiver(` 构造（per-profile 实例化）
     */
    @Test
    fun `TC-AGENT-040-d migration scans three tag families and writes via archiver`() {
        assertTrue(
            "找不到 launchAutoNodeArchiverMigrationIfNeeded 函数体 —— 先满足 TC-AGENT-040-a。",
            migrationBlock.isNotBlank()
        )

        // 三段 tag 字面
        assertTrue(
            "launchAutoNodeArchiverMigrationIfNeeded 必须含 `\"#auto_summary\"` 字面 —— SUMMARY bucket 扫描目标。\n实际:\n$migrationBlock",
            migrationBlock.contains("\"#auto_summary\"")
        )
        assertTrue(
            "launchAutoNodeArchiverMigrationIfNeeded 必须含 `\"#auto_extracted\"` 字面 —— EXTRACTED bucket 扫描目标。\n实际:\n$migrationBlock",
            migrationBlock.contains("\"#auto_extracted\"")
        )
        assertTrue(
            "launchAutoNodeArchiverMigrationIfNeeded 必须含 `\"#auto_summary_id:\"` 字面 —— SUMMARY_ID bucket 走 prefix 扫描的目标前缀（变长 NNN 后缀）。\n实际:\n$migrationBlock",
            migrationBlock.contains("\"#auto_summary_id:\"")
        )

        // exact-match query 用 findMemoriesByTag
        assertTrue(
            "launchAutoNodeArchiverMigrationIfNeeded 必须调 `findMemoriesByTag(` —— SUMMARY / EXTRACTED 走 exact-match 查 tag。\n实际:\n$migrationBlock",
            Regex("""\bfindMemoriesByTag\s*\(""").containsMatchIn(migrationBlock)
        )

        // prefix scan 用 findTagsByNamePrefix
        assertTrue(
            "launchAutoNodeArchiverMigrationIfNeeded 必须调 `findTagsByNamePrefix(` —— SUMMARY_ID 变长后缀走 prefix 扫。\n实际:\n$migrationBlock",
            Regex("""\bfindTagsByNamePrefix\s*\(""").containsMatchIn(migrationBlock)
        )

        // 三个 bucket 枚举
        assertTrue(
            "launchAutoNodeArchiverMigrationIfNeeded 必须引用 `ArchiveBucket.SUMMARY` —— SUMMARY bucket 落库枚举。\n实际:\n$migrationBlock",
            migrationBlock.contains("ArchiveBucket.SUMMARY")
        )
        assertTrue(
            "launchAutoNodeArchiverMigrationIfNeeded 必须引用 `ArchiveBucket.EXTRACTED` —— EXTRACTED bucket 落库枚举。\n实际:\n$migrationBlock",
            migrationBlock.contains("ArchiveBucket.EXTRACTED")
        )
        assertTrue(
            "launchAutoNodeArchiverMigrationIfNeeded 必须引用 `ArchiveBucket.SUMMARY_ID` —— SUMMARY_ID bucket 落库枚举。\n实际:\n$migrationBlock",
            migrationBlock.contains("ArchiveBucket.SUMMARY_ID")
        )

        // archiver 落库动作
        assertTrue(
            "launchAutoNodeArchiverMigrationIfNeeded 必须调 `appendToRoot(` —— 迁移核心落库动作。\n实际:\n$migrationBlock",
            Regex("""\bappendToRoot\s*\(""").containsMatchIn(migrationBlock)
        )

        // archiver 构造
        assertTrue(
            "launchAutoNodeArchiverMigrationIfNeeded 必须 `MemoryArchiver(...)` 构造 —— per-profile 实例化。\n实际:\n$migrationBlock",
            Regex("""\bMemoryArchiver\s*\(""").containsMatchIn(migrationBlock)
        )
    }

    /**
     * TC-AGENT-040-e: 函数体必须满足多 profile 遍历 + 异常容忍 + 失败不置 done flag：
     *  - `profileListFlow.first()` 调用（遍历所有 profile）
     *  - `try {` + `catch (` 包住主体
     *  - catch 路径含 `AppLogger.w(` 调用
     *  - **catch 块内不得**出现 `putBoolean(...true)` 写 done flag —— done flag 只能在
     *    成功路径（catch 之外）置位
     */
    @Test
    fun `TC-AGENT-040-e migration iterates profiles guards exceptions and skips done flag on failure`() {
        assertTrue(
            "找不到 launchAutoNodeArchiverMigrationIfNeeded 函数体 —— 先满足 TC-AGENT-040-a。",
            migrationBlock.isNotBlank()
        )

        // 多 profile 遍历
        assertTrue(
            "launchAutoNodeArchiverMigrationIfNeeded 必须用 `profileListFlow.first()` 拿到所有 profile —— " +
                "散节点散落在每个 profile 的独立 ObjectBox 库里。\n实际:\n$migrationBlock",
            Regex("""\bprofileListFlow\.first\s*\(\s*\)""").containsMatchIn(migrationBlock)
        )

        // try / catch 包裹
        assertTrue(
            "launchAutoNodeArchiverMigrationIfNeeded 必须含 `try {` 块 —— 单 profile / 单节点失败不能拖垮整次迁移。\n实际:\n$migrationBlock",
            migrationBlock.contains("try {")
        )
        assertTrue(
            "launchAutoNodeArchiverMigrationIfNeeded 必须含 `catch (` —— 失败容忍：异常不写完成标记，下次启动重试。\n实际:\n$migrationBlock",
            Regex("""\bcatch\s*\(""").containsMatchIn(migrationBlock)
        )

        // catch 路径必须 AppLogger.w
        assertTrue(
            "launchAutoNodeArchiverMigrationIfNeeded 的 catch 路径必须 `AppLogger.w(...)` 记录 —— " +
                "异常吞掉但要留诊断痕迹。\n实际:\n$migrationBlock",
            Regex("""\bAppLogger\.w\s*\(""").containsMatchIn(migrationBlock)
        )

        // catch 块内不得出现 putBoolean(...true) —— 抽出 catch 块体单独检查
        val catchBlocks = extractCatchBlocks(migrationBlock)
        assertTrue(
            "未能抽出 catch 块体 —— 期望至少 1 个，实际 ${catchBlocks.size}。\n实际:\n$migrationBlock",
            catchBlocks.isNotEmpty()
        )
        for ((idx, catchBody) in catchBlocks.withIndex()) {
            val hasDoneWriteInsideCatch =
                Regex(
                    """\bputBoolean\s*\(\s*[^)]*R_AGENT_040_auto_node_consolidation_done[^)]*,\s*true"""
                ).containsMatchIn(catchBody)
            assertFalse(
                "catch 块 #${idx + 1} 内**不得**出现 `putBoolean(R_AGENT_040_..._done, true)` —— " +
                    "失败路径必须不写 done flag，下次冷启重试。\n实际 catch body:\n$catchBody",
                hasDoneWriteInsideCatch
            )
        }
    }

    /**
     * TC-AGENT-040-f: 函数体必须正确从 Memory 的 tags ToMany 提取 chatId（Memory 实体没有
     * `chatId` 字段，旧节点 chatId 是作为 `#chat:<id>` tag 挂在节点上的）：
     *  - 含 `"#chat:"` 字面（要找的 tag prefix）
     *  - 含 `removePrefix(` / `substringAfter(` / `drop(` 任一等价表达（取后缀）
     *
     * 反向红线：**不**得直接传 `Memory.uuid` / `memory.id` 当 chatId（语义错）。
     */
    @Test
    fun `TC-AGENT-040-f migration extracts chatId from chat-prefixed tag with empty fallback`() {
        assertTrue(
            "找不到 launchAutoNodeArchiverMigrationIfNeeded 函数体 —— 先满足 TC-AGENT-040-a。",
            migrationBlock.isNotBlank()
        )

        // 必须 reference #chat: 前缀
        assertTrue(
            "launchAutoNodeArchiverMigrationIfNeeded 必须含 `\"#chat:\"` 字面 —— Memory 实体无 chatId 字段，" +
                "旧节点的 chatId 是作为 `#chat:<id>` tag 挂在节点上的，迁移时必须从 tags ToMany 提取。\n实际:\n$migrationBlock",
            migrationBlock.contains("\"#chat:\"")
        )

        // 必须有取后缀动作
        val hasSuffixExtract =
            Regex("""\.removePrefix\s*\(""").containsMatchIn(migrationBlock) ||
                Regex("""\.substringAfter\s*\(""").containsMatchIn(migrationBlock) ||
                Regex("""\.drop\s*\(""").containsMatchIn(migrationBlock)
        assertTrue(
            "launchAutoNodeArchiverMigrationIfNeeded 必须用 `removePrefix(` / `substringAfter(` / `drop(` 任一" +
                "等价方式从 `#chat:<id>` tag 取出 `<id>` 后缀作为 chatId。\n实际:\n$migrationBlock",
            hasSuffixExtract
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
            Regex("""^(private |internal |public |protected |override )?(suspend )?fun \w+\s*[(<]""")
                .containsMatchIn(t)
        }
        val endIdx = if (nextFunOffset < 0) lines.size else startIdx + 1 + nextFunOffset
        return lines.subList(startIdx, endIdx).joinToString("\n")
    }

    /**
     * 从函数体内抽出所有 `catch (...) { ... }` 块的 body（用括号深度计数闭合到外层 `}`），
     * 用于 TC-AGENT-040-e 检查 catch 体内不得写 done flag。
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
