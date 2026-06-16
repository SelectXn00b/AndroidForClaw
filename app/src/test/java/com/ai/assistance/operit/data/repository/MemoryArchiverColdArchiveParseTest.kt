package com.ai.assistance.operit.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R-AGENT-041-c (2026-06-17): `parseArchiveJsonl(text)` 顶层纯函数 —— 解析 jsonl 文本为
 * `List<MemoryArchiver.ArchiveEntry>`，要求容忍坏行（不抛、跳过坏行、保留好行）。
 *
 * 因为 `parseArchiveJsonl` 是纯函数（输入 String / 输出 List），ROI 极高，走真单测覆盖行为。
 *
 * 对应 TC-AGENT-041-c-a/b/c（见 docs/hermes-test-cases.md）。
 */
class MemoryArchiverColdArchiveParseTest {

    /**
     * TC-AGENT-041-c-a: 多行有效 jsonl 全部解析。
     */
    @Test
    fun `TC-AGENT-041-c-a parses well-formed jsonl into archive entries`() {
        val text = listOf(
            """{"ts":1700000000000,"chat_id":"chat-A","content":"line one","source":"auto_summary"}""",
            """{"ts":1700000010000,"chat_id":"chat-A","content":"line two","source":"auto_summary"}""",
            """{"ts":1700000020000,"chat_id":"chat-B","content":"line three","source":"auto_extracted"}"""
        ).joinToString("\n")

        val entries = parseArchiveJsonl(text)

        assertEquals("应解析 3 条 entry。", 3, entries.size)
        assertEquals(1700000000000L, entries[0].ts)
        assertEquals("chat-A", entries[0].chatId)
        assertEquals("line one", entries[0].content)
        assertEquals("auto_summary", entries[0].source)
        assertEquals("chat-B", entries[2].chatId)
        assertEquals("line three", entries[2].content)
        assertEquals("auto_extracted", entries[2].source)
    }

    /**
     * TC-AGENT-041-c-b: 含坏行（非 JSON / 缺字段 / 空白）跳过，不抛。
     */
    @Test
    fun `TC-AGENT-041-c-b skips malformed lines without throwing`() {
        val text = listOf(
            """{"ts":1700000000000,"chat_id":"chat-A","content":"good one","source":"auto_summary"}""",
            """this-is-not-json-at-all""",
            """{"ts":1700000010000,"content":"missing chat_id","source":"auto_summary"}""",
            "",
            """{"ts":1700000020000,"chat_id":"chat-B","content":"good two","source":"auto_summary"}"""
        ).joinToString("\n")

        val entries = parseArchiveJsonl(text)

        assertEquals(
            "坏行（非 JSON / 缺 chat_id / 空白）必须跳过，只剩 2 行有效。\n实际:\n$entries",
            2,
            entries.size
        )
        assertEquals("good one", entries[0].content)
        assertEquals("good two", entries[1].content)
    }

    /**
     * TC-AGENT-041-c-c: 空字符串 / 全空白 / 仅换行 -> emptyList。
     */
    @Test
    fun `TC-AGENT-041-c-c handles empty and whitespace-only input`() {
        assertTrue("空字符串 -> emptyList", parseArchiveJsonl("").isEmpty())
        assertTrue("全空白 -> emptyList", parseArchiveJsonl("   ").isEmpty())
        assertTrue("仅换行 -> emptyList", parseArchiveJsonl("\n\n\n").isEmpty())
        assertTrue(
            "tab/space/换行混合 -> emptyList",
            parseArchiveJsonl(" \t \n   \n\t").isEmpty()
        )
    }
}
