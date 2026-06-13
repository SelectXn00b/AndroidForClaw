package com.ai.assistance.operit.core.application

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * R-AGENT-029 (2026-06-13): `OperitApplication.onCreate()` 必须挂上一次性孤儿 tag 迁移钩子
 * `launchOrphanTagMigrationsIfNeeded()`，使用 SharedPreferences 防重入键
 * `R_AGENT_029_auto_summary_id_orphan_cleanup_done`（位于 `hermes_data_migrations` 文件），
 * 多 profile 全遍历，try/catch 包裹（失败不写完成标记，下次启动重试）。
 *
 * **测试策略**: `OperitApplication` 重度依赖 Android Context / Hilt / 启动顺序，纯 JVM 单测构造
 * 复杂度极高，与 R-AGENT-013/014/015/016/017 同策略走源码字符串扫描守 wiring。运行时正确性由
 * 手测兜底（旧 APK 残留 → 升级 → logcat 看清理日志 + MemoryScreen 看 tag 列表）。
 *
 * 对应 TC-AGENT-029-d..f（见 docs/hermes-test-cases.md）。
 */
class OperitApplicationOrphanTagMigrationWiringTest {

    private val source: String by lazy { stripComments(File(applicationPath()).readText()) }

    private val onCreateBlock: String by lazy {
        runCatching { extractFunctionBlock(source, "onCreate") }.getOrDefault("")
    }

    private val migrationBlock: String by lazy {
        runCatching { extractFunctionBlock(source, "launchOrphanTagMigrationsIfNeeded") }
            .getOrDefault("")
    }

    /**
     * TC-AGENT-029-d: 全文必须含 R-AGENT-029 的迁移常量字面值：
     * - 方法名 `launchOrphanTagMigrationsIfNeeded`
     * - 清理 prefix `"#auto_summary_id:"`
     * - SharedPreferences 名 `"hermes_data_migrations"`
     * - 防重入键前缀 `R_AGENT_029`
     */
    @Test
    fun `TC-AGENT-029-d application source declares migration constants`() {
        assertTrue(
            "OperitApplication.kt 必须含字面方法名 `launchOrphanTagMigrationsIfNeeded` —— " +
                "迁移钩子私有方法名。",
            source.contains("launchOrphanTagMigrationsIfNeeded")
        )
        assertTrue(
            "OperitApplication.kt 必须含字面 prefix `\"#auto_summary_id:\"` —— " +
                "R-AGENT-029 一次性迁移清理目标。",
            source.contains("\"#auto_summary_id:\"")
        )
        assertTrue(
            "OperitApplication.kt 必须含字面 SharedPreferences 名 `\"hermes_data_migrations\"` —— " +
                "防重入完成标记的存放位置。",
            source.contains("\"hermes_data_migrations\"")
        )
        assertTrue(
            "OperitApplication.kt 必须含 `R_AGENT_029` 字面值 —— SharedPreferences 防重入键前缀。",
            source.contains("R_AGENT_029")
        )
    }

    /**
     * TC-AGENT-029-e: `onCreate` 函数体内必须调用 `launchOrphanTagMigrationsIfNeeded()`。
     */
    @Test
    fun `TC-AGENT-029-e onCreate invokes orphan tag migration hook`() {
        assertTrue(
            "找不到 onCreate 函数体 —— Application 应该重写 onCreate。",
            onCreateBlock.isNotBlank()
        )
        assertTrue(
            "OperitApplication.onCreate 函数体必须调用 `launchOrphanTagMigrationsIfNeeded()` —— " +
                "否则 R-AGENT-029 一次性迁移完全没接通。\n" +
                "实际 onCreate body（前 4000 chars）:\n${onCreateBlock.take(4000)}",
            Regex("""\blaunchOrphanTagMigrationsIfNeeded\s*\(\s*\)""").containsMatchIn(onCreateBlock)
        )
    }

    /**
     * TC-AGENT-029-f: `launchOrphanTagMigrationsIfNeeded` 函数体必须含：
     * - `profileListFlow.first()` 字面值（多 profile 全遍历）
     * - `cleanupOrphanTagsByPrefix(` 字面值（调仓储 API）
     * - `try {` + `catch` 包裹（失败容忍）
     * - `prefs.edit().putBoolean(` 调用（成功才写完成标记）
     */
    @Test
    fun `TC-AGENT-029-f migration iterates all profiles with try-catch and writes done flag`() {
        assertTrue(
            "找不到 launchOrphanTagMigrationsIfNeeded 函数体 —— 先满足 TC-AGENT-029-d。",
            migrationBlock.isNotBlank()
        )

        assertTrue(
            "launchOrphanTagMigrationsIfNeeded 必须用 `profileListFlow.first()` 拿到所有 profile —— " +
                "孤儿 tag 散落在每个 profile 的独立 ObjectBox 库里。",
            Regex("""\bprofileListFlow\.first\s*\(\s*\)""").containsMatchIn(migrationBlock)
        )

        assertTrue(
            "launchOrphanTagMigrationsIfNeeded 必须调 `cleanupOrphanTagsByPrefix(` —— " +
                "实际清理动作下沉到 MemoryRepository。",
            Regex("""\bcleanupOrphanTagsByPrefix\s*\(""").containsMatchIn(migrationBlock)
        )

        assertTrue(
            "launchOrphanTagMigrationsIfNeeded 必须含 `try {` 块 —— 单个 profile 失败不能拖垮其他 profile。",
            migrationBlock.contains("try {")
        )
        assertTrue(
            "launchOrphanTagMigrationsIfNeeded 必须含 `catch (` —— 失败容忍：" +
                "异常不写完成标记，下次启动重试。",
            Regex("""\bcatch\s*\(""").containsMatchIn(migrationBlock)
        )

        assertTrue(
            "launchOrphanTagMigrationsIfNeeded 必须调 `prefs.edit().putBoolean(` —— " +
                "成功完成后才写防重入标记。",
            Regex("""\bprefs\.edit\s*\(\s*\)\.putBoolean\s*\(""").containsMatchIn(migrationBlock)
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
