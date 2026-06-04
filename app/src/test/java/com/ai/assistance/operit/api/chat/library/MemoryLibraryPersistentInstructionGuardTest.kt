package com.ai.assistance.operit.api.chat.library

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 守卫 R-AGENT-009 抗侵蚀保护 在 `MemoryLibrary` 中的接线契约。
 *
 * **背景**（2026-06-04 bugfix）：审计发现 `MemoryLibrary.saveMemory` 的自动合并 / 更新流程
 * **完全没有**跳过带 `#persistent_instruction` tag 的节点；`autoCategorizeMemories` 也无过滤。
 * 后果：用户手动标的持久指令，下一次后台自动整理就可能被合并 / 重写 / 重分类，原文丢失。
 *
 * 修复（按 R-AGENT-009 验收条件"带 tag 的节点 content 一字不动"）：
 *   1. `saveMemory` 的 `analysis.mergedEntities.forEach` 内：源 memory 中任一带 `#persistent_instruction`
 *      tag → 整组跳过合并
 *   2. `saveMemory` 的 `analysis.updatedEntities.forEach` 内：目标 memory 带 tag → 跳过更新
 *   3. `autoCategorizeMemories` 的 uncategorizedMemories 过滤：排除带 tag 的节点
 *
 * **测试策略**：依赖 ObjectBox 真机，JVM 单测里走 **源码字符串扫描**（参考 `DeepseekProviderTest`
 * 模式），把守卫契约固化进源码。运行时正确性由 §3 E2E + manual smoke 兜底。
 *
 * 对应 TC-AGENT-241-a/b（见 docs/hermes-test-cases.md）。
 */
class MemoryLibraryPersistentInstructionGuardTest {

    /**
     * TC-AGENT-241-a: merge 流程必须跳过带 #persistent_instruction tag 的 source memory。
     */
    @Test
    fun `TC-AGENT-241-a merge skips persistent_instruction sources`() {
        val source = stripLineComments(File(memoryLibraryPath()).readText())

        // 在 mergedEntities.forEach 块内必须 reference #persistent_instruction
        // （即守卫逻辑挂在合并循环里）
        val mergeBlock = extractBlockContaining(source, "mergedEntities") ?: error(
            "找不到 mergedEntities 处理块；MemoryLibrary 结构可能被改动"
        )
        assertTrue(
            "MemoryLibrary 的 mergedEntities forEach 内必须检查 #persistent_instruction tag —— " +
                "实际块内未出现该 tag 字符串。R-AGENT-009 抗侵蚀保护断链。",
            mergeBlock.contains("#persistent_instruction")
        )
    }

    /**
     * TC-AGENT-241-a': update 流程必须跳过带 tag 的 memory。
     */
    @Test
    fun `TC-AGENT-241-a' update skips persistent_instruction targets`() {
        val source = stripLineComments(File(memoryLibraryPath()).readText())

        val updateBlock = extractBlockContaining(source, "updatedEntities") ?: error(
            "找不到 updatedEntities 处理块；MemoryLibrary 结构可能被改动"
        )
        assertTrue(
            "MemoryLibrary 的 updatedEntities forEach 内必须检查 #persistent_instruction tag —— " +
                "防止用户钉的指令被自动覆盖。",
            updateBlock.contains("#persistent_instruction")
        )
    }

    /**
     * TC-AGENT-241-b: autoCategorizeMemories 必须把带 tag 的 memory 排除出待分类集合。
     */
    @Test
    fun `TC-AGENT-241-b autoCategorize skips persistent_instruction nodes`() {
        val source = stripLineComments(File(memoryLibraryPath()).readText())

        // uncategorizedMemories 这个变量名应在 autoCategorizeMemories 函数体里
        // 守卫应在它的 filter 块附近
        val uncategorizedLine = source.lines().withIndex().firstOrNull {
            it.value.contains("val uncategorizedMemories")
        } ?: error("找不到 uncategorizedMemories 定义，autoCategorizeMemories 可能被重构")

        // 取定义后 8 行作为 filter 窗口
        val window = source.lines()
            .drop(uncategorizedLine.index)
            .take(8)
            .joinToString("\n")

        assertTrue(
            "MemoryLibrary.autoCategorizeMemories 的 uncategorizedMemories filter 块必须排除 " +
                "#persistent_instruction tag 的节点 —— 否则带 tag 的节点会被分到自动文件夹，" +
                "影响 ConversationService 注入顺序与稳定性。",
            window.contains("#persistent_instruction")
        )
    }

    // ----- helpers -----

    /**
     * 抽取包含某个关键字的 forEach 块，从该关键字所在行往后取直到第一个仅含 `}` 的行（含）。
     * 简化版块抽取，足够断言"块内必有某字符串"。
     */
    private fun extractBlockContaining(source: String, keyword: String): String? {
        val lines = source.lines()
        // 找到出现 keyword 且后接 isNotEmpty / forEach 的行
        val startIdx = lines.indexOfFirst { it.contains(keyword) && (it.contains("isNotEmpty") || it.contains(".forEach")) }
        if (startIdx < 0) return null
        // 从该行开始取，遇到下一个仅含 `}` 的行（缩进 12 空格 + }）结束 —— 因为 MemoryLibrary 嵌套深，
        // 取最大窗口 60 行兜底。
        val end = (startIdx + 60).coerceAtMost(lines.size)
        return lines.subList(startIdx, end).joinToString("\n")
    }

    /** 剥掉 Kotlin 单行注释，避免 regex 撞上注释里的"反模式样本"。 */
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

    private fun memoryLibraryPath(): String =
        File(appSrcMainRoot(), "api/chat/library/MemoryLibrary.kt").path
}
