package com.ai.assistance.operit.data.repository

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * R-AGENT-029 (2026-06-13): `MemoryRepository` 必须新增 `findTagsByNamePrefix` +
 * `cleanupOrphanTagsByPrefix(prefix)` 两个 suspend API，用于一次性迁移清理。
 *
 * 起因：R-AGENT-027 已删除 `extractAndPersistFacts` 写 `#auto_summary_id:<parentId>` 的代码路径，
 * 但**历史 APK**（含 `app-release-r026-aikeep-ba814a70.apk`）装机的 ObjectBox 里仍有该 tag 残留 +
 * R-AGENT-026 keepDecision=false 路径产生的 `#auto_summary_id:-1` 孤儿。R-AGENT-029 = 启动时
 * 一次性迁移清理。
 *
 * **测试策略**: `MemoryRepository` 重度依赖 ObjectBox / Android Context，纯 JVM 单测构造 BoxStore
 * 复杂度极高，与 R-AGENT-013/014/015/016/017 同策略走源码字符串扫描守 wiring。运行时正确性由
 * 手测兜底（旧 APK 残留 → 升级 → logcat 看清理日志 + MemoryScreen 看 tag 列表）。
 *
 * 对应 TC-AGENT-029-a..c（见 docs/hermes-test-cases.md）。
 */
class MemoryRepositoryOrphanTagCleanupTest {

    private val source: String by lazy { stripComments(File(repoPath()).readText()) }

    private val findTagsBlock: String by lazy {
        runCatching { extractFunctionBlock(source, "findTagsByNamePrefix") }.getOrDefault("")
    }

    private val cleanupBlock: String by lazy {
        runCatching { extractFunctionBlock(source, "cleanupOrphanTagsByPrefix") }.getOrDefault("")
    }

    /**
     * TC-AGENT-029-a: `MemoryRepository.kt` 必须含 `findTagsByNamePrefix` + `cleanupOrphanTagsByPrefix`
     * 两个 suspend 函数签名（字面值即可）。
     */
    @Test
    fun `TC-AGENT-029-a repository exposes prefix-based tag query and cleanup api`() {
        assertTrue(
            "MemoryRepository.kt 必须新增 `suspend fun findTagsByNamePrefix(` 函数 —— " +
                "用于按 prefix 查 tag。\nsource head:\n${source.take(200)}",
            Regex("""\bsuspend\s+fun\s+findTagsByNamePrefix\s*\(""").containsMatchIn(source)
        )
        assertTrue(
            "MemoryRepository.kt 必须新增 `suspend fun cleanupOrphanTagsByPrefix(` 函数 —— " +
                "用于按 prefix 一次性清理孤儿 tag。",
            Regex("""\bsuspend\s+fun\s+cleanupOrphanTagsByPrefix\s*\(""").containsMatchIn(source)
        )
    }

    /**
     * TC-AGENT-029-b: `cleanupOrphanTagsByPrefix` 函数体必须含 `runInTx` 调用（单事务保证）+
     * `tagBox.remove(` 调用（删 tag 实体）+ `memoryBox.put(` 调用（解 ToMany 后 put memory）+
     * `memory.tags.remove(` 调用（解 ToMany 关系）。
     */
    @Test
    fun `TC-AGENT-029-b cleanup function uses transaction and correct delete order`() {
        assertTrue(
            "找不到 cleanupOrphanTagsByPrefix 函数体 —— 先满足 TC-AGENT-029-a。",
            cleanupBlock.isNotBlank()
        )

        assertTrue(
            "cleanupOrphanTagsByPrefix 必须用 `runInTx` 包裹删除动作（单事务保证：" +
                "中途异常不留半解关系的 memory）。\n实际函数体:\n$cleanupBlock",
            Regex("""\brunInTx\s*[\{(]""").containsMatchIn(cleanupBlock)
        )

        assertTrue(
            "cleanupOrphanTagsByPrefix 必须调 `tagBox.remove(` 删 tag 实体本身。",
            Regex("""\btagBox\.remove\s*\(""").containsMatchIn(cleanupBlock)
        )

        assertTrue(
            "cleanupOrphanTagsByPrefix 必须调 `memoryBox.put(` 把解了 tag 引用后的 memory 持久化 —— " +
                "@Backlink 不会反向自动解 ToMany。",
            Regex("""\bmemoryBox\.put\s*\(""").containsMatchIn(cleanupBlock)
        )

        assertTrue(
            "cleanupOrphanTagsByPrefix 必须调 `memory.tags.remove(` 解 Memory ↔ Tag 的 ToMany 关系 —— " +
                "否则 Memory 的 tags ToMany 留下指向死 id 的幽灵关系。",
            Regex("""\bmemory\.tags\.remove\s*\(""").containsMatchIn(cleanupBlock)
        )
    }

    /**
     * TC-AGENT-029-c: `findTagsByNamePrefix` + `cleanupOrphanTagsByPrefix` 必须含
     * `MemoryTag_.name.startsWith(` 字面值（按 prefix 查 tag 的 ObjectBox condition）+
     * 空 prefix 守卫（`if (prefix.isEmpty())`）。
     */
    @Test
    fun `TC-AGENT-029-c prefix query uses startsWith and guards empty prefix`() {
        assertTrue(
            "找不到 findTagsByNamePrefix 函数体 —— 先满足 TC-AGENT-029-a。",
            findTagsBlock.isNotBlank()
        )
        assertTrue(
            "找不到 cleanupOrphanTagsByPrefix 函数体 —— 先满足 TC-AGENT-029-a。",
            cleanupBlock.isNotBlank()
        )

        // 两个函数都必须用 ObjectBox 的 `MemoryTag_.name.startsWith(` 做 prefix 查询
        assertTrue(
            "findTagsByNamePrefix 必须用 `MemoryTag_.name.startsWith(` 做 ObjectBox prefix 条件查询。",
            Regex("""\bMemoryTag_\.name\.startsWith\s*\(""").containsMatchIn(findTagsBlock)
        )
        assertTrue(
            "cleanupOrphanTagsByPrefix 必须用 `MemoryTag_.name.startsWith(` 做 ObjectBox prefix 条件查询。",
            Regex("""\bMemoryTag_\.name\.startsWith\s*\(""").containsMatchIn(cleanupBlock)
        )

        // 两个函数都必须有空 prefix 守卫，防误清全库（`startsWith("")` 命中所有 tag）
        val findGuards =
            Regex("""\bif\s*\(\s*prefix\.isEmpty\s*\(\s*\)\s*\)""").containsMatchIn(findTagsBlock)
        val cleanupGuards =
            Regex("""\bif\s*\(\s*prefix\.isEmpty\s*\(\s*\)\s*\)""").containsMatchIn(cleanupBlock)
        assertTrue(
            "findTagsByNamePrefix 必须含 `if (prefix.isEmpty())` 守卫 —— 否则空 prefix 会命中所有 tag。\n" +
                "实际函数体:\n$findTagsBlock",
            findGuards
        )
        assertTrue(
            "cleanupOrphanTagsByPrefix 必须含 `if (prefix.isEmpty())` 守卫 —— 否则空 prefix 会清空整个 tag 表。\n" +
                "实际函数体:\n$cleanupBlock",
            cleanupGuards
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

    private fun repoPath(): String {
        val candidates = listOf(
            File("src/main/java/com/ai/assistance/operit/data/repository/MemoryRepository.kt"),
            File("app/src/main/java/com/ai/assistance/operit/data/repository/MemoryRepository.kt")
        )
        return candidates.firstOrNull { it.exists() }?.path
            ?: error("Cannot locate MemoryRepository.kt — cwd=${File(".").absolutePath}")
    }
}
